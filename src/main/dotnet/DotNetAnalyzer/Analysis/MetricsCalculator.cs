using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Calculates quality metrics (CBO, LCOM, LOC) for .NET components.
/// </summary>
public class MetricsCalculator
{
    /// <summary>
    /// Computes CBO, LCOM, and LOC for each component in the list, mutating them in place.
    /// </summary>
    public void Calculate(Compilation compilation, List<Component> components)
    {
        var componentIds = new HashSet<string>(components.Select(c => c.Id));

        foreach (var syntaxTree in compilation.SyntaxTrees)
        {
            SemanticModel? semanticModel = null;
            try
            {
                semanticModel = compilation.GetSemanticModel(syntaxTree);
            }
            catch
            {
                // Gracefully handle cases where semantic model is unavailable
            }

            var root = syntaxTree.GetRoot();
            var typeDeclarations = root.DescendantNodes()
                .OfType<TypeDeclarationSyntax>()
                .Where(t => t is ClassDeclarationSyntax or InterfaceDeclarationSyntax);

            foreach (var typeDecl in typeDeclarations)
            {
                INamedTypeSymbol? typeSymbol = null;
                if (semanticModel != null)
                {
                    typeSymbol = semanticModel.GetDeclaredSymbol(typeDecl) as INamedTypeSymbol;
                }

                var fullyQualifiedName = typeSymbol != null
                    ? GetFullyQualifiedName(typeSymbol)
                    : GetFallbackName(typeDecl);

                var component = components.FirstOrDefault(c => c.Id == fullyQualifiedName);
                if (component == null)
                    continue;

                // CBO: count unique outgoing type dependencies
                component.Cbo = CalculateCbo(typeDecl, semanticModel, fullyQualifiedName);

                // LCOM: ratio of methods sharing fields (0.0 = cohesive, 1.0 = not cohesive)
                component.Lcom = CalculateLcom(typeDecl, semanticModel);

                // LOC: count non-blank non-comment lines (re-calculate for accuracy)
                component.Loc = CountNonBlankNonCommentLines(typeDecl);
            }
        }
    }

    private static int CalculateCbo(TypeDeclarationSyntax typeDecl, SemanticModel? semanticModel, string ownName)
    {
        if (semanticModel == null)
            return 0;

        var referencedTypes = new HashSet<string>();

        // Walk all identifier names and member accesses to find type references
        foreach (var node in typeDecl.DescendantNodes())
        {
            ITypeSymbol? typeSymbol = null;

            switch (node)
            {
                case ObjectCreationExpressionSyntax creation:
                    typeSymbol = semanticModel.GetTypeInfo(creation).Type;
                    break;

                case VariableDeclarationSyntax varDecl:
                    typeSymbol = semanticModel.GetTypeInfo(varDecl.Type).Type;
                    break;

                case ParameterSyntax param when param.Type != null:
                    typeSymbol = semanticModel.GetTypeInfo(param.Type).Type;
                    break;

                case PropertyDeclarationSyntax prop:
                    typeSymbol = semanticModel.GetTypeInfo(prop.Type).Type;
                    break;

                case FieldDeclarationSyntax field:
                    typeSymbol = semanticModel.GetTypeInfo(field.Declaration.Type).Type;
                    break;

                case MethodDeclarationSyntax method:
                    typeSymbol = semanticModel.GetTypeInfo(method.ReturnType).Type;
                    break;

                case InvocationExpressionSyntax invocation:
                    var symbolInfo = semanticModel.GetSymbolInfo(invocation);
                    if (symbolInfo.Symbol is IMethodSymbol methodSymbol)
                    {
                        var containingType = methodSymbol.ContainingType;
                        if (containingType != null)
                        {
                            var typeName = GetFullyQualifiedName(containingType);
                            if (!IsSystemType(typeName) && typeName != ownName)
                            {
                                referencedTypes.Add(typeName);
                            }
                        }
                    }
                    continue;
            }

            if (typeSymbol is INamedTypeSymbol namedType)
            {
                var typeName = GetFullyQualifiedName(namedType);
                if (!IsSystemType(typeName) && typeName != ownName)
                {
                    referencedTypes.Add(typeName);
                }
            }
        }

        // Also count base types and implemented interfaces
        if (typeDecl is ClassDeclarationSyntax classDecl && classDecl.BaseList != null)
        {
            foreach (var baseType in classDecl.BaseList.Types)
            {
                var baseTypeSymbol = semanticModel.GetTypeInfo(baseType.Type).Type as INamedTypeSymbol;
                if (baseTypeSymbol != null)
                {
                    var typeName = GetFullyQualifiedName(baseTypeSymbol);
                    if (!IsSystemType(typeName) && typeName != ownName)
                    {
                        referencedTypes.Add(typeName);
                    }
                }
            }
        }

        return referencedTypes.Count;
    }

    private static double CalculateLcom(TypeDeclarationSyntax typeDecl, SemanticModel? semanticModel)
    {
        // LCOM4-style: ratio of methods NOT sharing fields
        // Collect fields declared in the type
        var fields = typeDecl.Members
            .OfType<FieldDeclarationSyntax>()
            .SelectMany(f => f.Declaration.Variables.Select(v => v.Identifier.Text))
            .ToList();

        // Collect methods declared in the type
        var methods = typeDecl.Members
            .OfType<MethodDeclarationSyntax>()
            .Where(m => !m.Modifiers.Any(SyntaxKind.StaticKeyword))
            .ToList();

        if (fields.Count == 0 || methods.Count == 0)
            return 0.0;

        // For each method, determine which fields it accesses
        var methodFieldSets = new List<HashSet<string>>();

        foreach (var method in methods)
        {
            var accessedFields = new HashSet<string>();

            foreach (var identifier in method.DescendantNodes().OfType<IdentifierNameSyntax>())
            {
                var name = identifier.Identifier.Text;
                if (fields.Contains(name))
                {
                    accessedFields.Add(name);
                }
            }

            // Also check for _field access patterns via MemberAccessExpression (this._field)
            foreach (var memberAccess in method.DescendantNodes().OfType<MemberAccessExpressionSyntax>())
            {
                if (memberAccess.Expression is ThisExpressionSyntax)
                {
                    var name = memberAccess.Name.Identifier.Text;
                    if (fields.Contains(name))
                    {
                        accessedFields.Add(name);
                    }
                }
            }

            methodFieldSets.Add(accessedFields);
        }

        // LCOM = 1 - (sum of fields accessed by each method) / (methods * fields)
        // Henderson-Sellers LCOM formula:
        // LCOM = (M - (1/F) * sum(MF)) / (M - 1)
        // where M = number of methods, F = number of fields, MF = number of methods accessing field f
        int m = methods.Count;
        int f = fields.Count;

        if (m <= 1)
            return 0.0;

        double sumMf = 0;
        foreach (var field in fields)
        {
            int methodsAccessingField = methodFieldSets.Count(set => set.Contains(field));
            sumMf += methodsAccessingField;
        }

        double averageMethodsPerField = sumMf / f;
        double lcom = (m - averageMethodsPerField) / (m - 1);

        // Clamp to [0.0, 1.0]
        return Math.Max(0.0, Math.Min(1.0, lcom));
    }

    private static int CountNonBlankNonCommentLines(TypeDeclarationSyntax typeDecl)
    {
        var text = typeDecl.GetText();
        var count = 0;
        var inBlockComment = false;

        foreach (var textLine in text.Lines)
        {
            var line = textLine.ToString().Trim();

            if (inBlockComment)
            {
                if (line.Contains("*/"))
                    inBlockComment = false;
                continue;
            }

            if (string.IsNullOrWhiteSpace(line))
                continue;

            if (line.StartsWith("//") || line.StartsWith("///"))
                continue;

            if (line.StartsWith("/*"))
            {
                inBlockComment = !line.Contains("*/");
                continue;
            }

            count++;
        }

        return count;
    }

    private static bool IsSystemType(string typeName)
    {
        return typeName.StartsWith("System.") ||
               typeName.StartsWith("Microsoft.") ||
               typeName == "object" ||
               typeName == "string" ||
               typeName == "int" ||
               typeName == "bool" ||
               typeName == "void" ||
               typeName == "double" ||
               typeName == "float" ||
               typeName == "decimal" ||
               typeName == "long" ||
               typeName == "short" ||
               typeName == "byte" ||
               typeName == "char";
    }

    private static string GetFullyQualifiedName(INamedTypeSymbol symbol)
    {
        var parts = new List<string>();
        var current = symbol.ContainingNamespace;
        while (current != null && !current.IsGlobalNamespace)
        {
            parts.Insert(0, current.Name);
            current = current.ContainingNamespace;
        }
        parts.Add(symbol.Name);
        return string.Join(".", parts);
    }

    private static string GetFallbackName(TypeDeclarationSyntax typeDecl)
    {
        var parts = new List<string>();
        var parent = typeDecl.Parent;
        while (parent != null)
        {
            if (parent is BaseNamespaceDeclarationSyntax nsDecl)
            {
                parts.Insert(0, nsDecl.Name.ToString());
            }
            parent = parent.Parent;
        }
        parts.Add(typeDecl.Identifier.Text);
        return string.Join(".", parts);
    }
}
