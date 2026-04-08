using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class TypeAnalyzer
{
    public List<Component> AnalyzeTypes(Compilation compilation)
    {
        var components = new List<Component>();

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

                var component = new Component
                {
                    Id = fullyQualifiedName,
                    Files = new List<string> { GetRelativeFilePath(syntaxTree.FilePath) },
                    Loc = CountNonBlankNonCommentLines(typeDecl),
                    IsInterface = typeSymbol != null
                        ? typeSymbol.TypeKind == TypeKind.Interface
                        : typeDecl is InterfaceDeclarationSyntax,
                    Annotations = ExtractAnnotations(typeDecl, semanticModel),
                };

                if (typeSymbol != null)
                {
                    // Base class
                    if (typeSymbol.BaseType != null
                        && typeSymbol.BaseType.SpecialType != SpecialType.System_Object
                        && typeSymbol.BaseType.Name != "Object")
                    {
                        component.Extends = GetFullyQualifiedName(typeSymbol.BaseType);
                    }

                    // Implemented interfaces
                    component.Implements = typeSymbol.Interfaces
                        .Select(i => GetFullyQualifiedName(i))
                        .ToList();
                }
                else
                {
                    // Fallback without semantic model
                    if (typeDecl is ClassDeclarationSyntax classDecl && classDecl.BaseList != null)
                    {
                        foreach (var baseType in classDecl.BaseList.Types)
                        {
                            component.Implements.Add(baseType.Type.ToString());
                        }
                    }
                }

                components.Add(component);
            }
        }

        return components;
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

        // Walk up to find namespace
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

    private static string GetRelativeFilePath(string filePath)
    {
        if (string.IsNullOrEmpty(filePath))
            return "unknown";

        // Normalize path separators
        return filePath.Replace('\\', '/');
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

            if (line.StartsWith("//"))
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

    private static List<string> ExtractAnnotations(TypeDeclarationSyntax typeDecl, SemanticModel? semanticModel)
    {
        var annotations = new List<string>();

        foreach (var attributeList in typeDecl.AttributeLists)
        {
            foreach (var attribute in attributeList.Attributes)
            {
                string name;

                if (semanticModel != null)
                {
                    var symbolInfo = semanticModel.GetSymbolInfo(attribute);
                    var attrSymbol = symbolInfo.Symbol?.ContainingType;
                    if (attrSymbol != null)
                    {
                        name = attrSymbol.Name;
                    }
                    else
                    {
                        name = attribute.Name.ToString();
                    }
                }
                else
                {
                    name = attribute.Name.ToString();
                }

                // Remove "Attribute" suffix
                if (name.EndsWith("Attribute"))
                    name = name.Substring(0, name.Length - "Attribute".Length);

                annotations.Add(name);
            }
        }

        return annotations;
    }
}
