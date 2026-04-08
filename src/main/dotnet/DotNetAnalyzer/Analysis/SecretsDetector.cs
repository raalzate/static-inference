using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class SecretsDetector
{
    private static readonly HashSet<string> ConfigurationInterfaceNames = new(StringComparer.Ordinal)
    {
        "IConfiguration",
    };

    public void Detect(Compilation compilation, List<Component> components)
    {
        var componentMap = components.ToDictionary(c => c.Id, c => c);

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
                .Where(t => t is ClassDeclarationSyntax);

            foreach (var typeDecl in typeDeclarations)
            {
                var typeName = GetFullyQualifiedName(typeDecl, semanticModel);
                if (!componentMap.TryGetValue(typeName, out var component))
                    continue;

                DetectConfigurationInjection(typeDecl, semanticModel, component);
                DetectConfigurationIndexerKeys(typeDecl, semanticModel, component);
                DetectAppsettingsReferences(typeDecl, component);
            }
        }
    }

    private static void DetectConfigurationInjection(
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel,
        Component component)
    {
        // Check constructor parameters for IConfiguration
        var constructors = typeDecl.DescendantNodes().OfType<ConstructorDeclarationSyntax>();
        foreach (var ctor in constructors)
        {
            if (ctor.ParameterList == null) continue;
            foreach (var param in ctor.ParameterList.Parameters)
            {
                if (param.Type == null) continue;
                var paramTypeName = GetTypeNameFromTypeSyntax(param.Type, semanticModel);
                if (ConfigurationInterfaceNames.Contains(paramTypeName))
                {
                    if (!component.SecretsReferences.Contains("IConfiguration"))
                    {
                        component.SecretsReferences.Add("IConfiguration");
                    }
                    return;
                }
            }
        }

        // Check field declarations for IConfiguration
        var fields = typeDecl.DescendantNodes().OfType<FieldDeclarationSyntax>();
        foreach (var field in fields)
        {
            var fieldTypeName = GetTypeNameFromTypeSyntax(field.Declaration.Type, semanticModel);
            if (ConfigurationInterfaceNames.Contains(fieldTypeName))
            {
                if (!component.SecretsReferences.Contains("IConfiguration"))
                {
                    component.SecretsReferences.Add("IConfiguration");
                }
                return;
            }
        }
    }

    private static void DetectConfigurationIndexerKeys(
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel,
        Component component)
    {
        // Find indexer access expressions like _configuration["key"]
        var elementAccesses = typeDecl.DescendantNodes().OfType<ElementAccessExpressionSyntax>();
        foreach (var elementAccess in elementAccesses)
        {
            // Check if the expression is accessing a configuration-like field
            var expressionText = elementAccess.Expression.ToString();
            if (!IsConfigurationExpression(expressionText, typeDecl, semanticModel))
                continue;

            // Extract the key from the indexer argument
            if (elementAccess.ArgumentList.Arguments.Count == 1)
            {
                var argument = elementAccess.ArgumentList.Arguments[0];
                if (argument.Expression is LiteralExpressionSyntax literal &&
                    literal.IsKind(SyntaxKind.StringLiteralExpression))
                {
                    var key = literal.Token.ValueText;
                    var reference = $"config:{key}";
                    if (!component.SecretsReferences.Contains(reference))
                    {
                        component.SecretsReferences.Add(reference);
                    }
                }
            }
        }
    }

    private static bool IsConfigurationExpression(
        string expressionText,
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel)
    {
        // Check if the expression refers to a field/variable of IConfiguration type
        // Heuristic: field names containing "configuration" or "config" (case-insensitive)
        var lowerExpr = expressionText.ToLowerInvariant();
        if (lowerExpr.Contains("configuration") || lowerExpr.Contains("config"))
            return true;

        // Also check if the type has an IConfiguration field and the expression matches a field name
        var fields = typeDecl.DescendantNodes().OfType<FieldDeclarationSyntax>();
        foreach (var field in fields)
        {
            var fieldTypeName = GetTypeNameFromTypeSyntax(field.Declaration.Type, semanticModel);
            if (ConfigurationInterfaceNames.Contains(fieldTypeName))
            {
                foreach (var variable in field.Declaration.Variables)
                {
                    if (expressionText == variable.Identifier.Text)
                        return true;
                }
            }
        }

        return false;
    }

    private static void DetectAppsettingsReferences(
        TypeDeclarationSyntax typeDecl,
        Component component)
    {
        // Find string literals referencing appsettings files
        var stringLiterals = typeDecl.DescendantNodes().OfType<LiteralExpressionSyntax>()
            .Where(l => l.IsKind(SyntaxKind.StringLiteralExpression));

        foreach (var literal in stringLiterals)
        {
            var value = literal.Token.ValueText;
            if (IsAppsettingsReference(value))
            {
                if (!component.SecretsReferences.Contains(value))
                {
                    component.SecretsReferences.Add(value);
                }
            }
        }
    }

    private static bool IsAppsettingsReference(string value)
    {
        // Match patterns like "appsettings.json", "appsettings.Development.json", etc.
        return value.StartsWith("appsettings", StringComparison.OrdinalIgnoreCase) &&
               value.EndsWith(".json", StringComparison.OrdinalIgnoreCase);
    }

    private static string GetTypeNameFromTypeSyntax(TypeSyntax typeSyntax, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var typeInfo = semanticModel.GetTypeInfo(typeSyntax);
            if (typeInfo.Type is INamedTypeSymbol namedType)
            {
                return namedType.Name;
            }
        }

        // Fallback: extract simple name from syntax
        var fullName = typeSyntax.ToString();
        var lastDot = fullName.LastIndexOf('.');
        return lastDot >= 0 ? fullName.Substring(lastDot + 1) : fullName;
    }

    private static string GetFullyQualifiedName(TypeDeclarationSyntax typeDecl, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var symbol = semanticModel.GetDeclaredSymbol(typeDecl) as INamedTypeSymbol;
            if (symbol != null)
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
        }

        // Fallback
        var fallbackParts = new List<string>();
        var parent = typeDecl.Parent;
        while (parent != null)
        {
            if (parent is BaseNamespaceDeclarationSyntax nsDecl)
            {
                fallbackParts.Insert(0, nsDecl.Name.ToString());
            }
            parent = parent.Parent;
        }
        fallbackParts.Add(typeDecl.Identifier.Text);
        return string.Join(".", fallbackParts);
    }
}
