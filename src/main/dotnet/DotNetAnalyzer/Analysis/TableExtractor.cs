using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class TableExtractor
{
    public void ExtractTables(Compilation compilation, List<Component> components)
    {
        var componentMap = components.ToDictionary(c => c.Id, c => c);
        // Also index by simple class name for matching DbSet<T> entity types
        var componentsBySimpleName = new Dictionary<string, List<Component>>();
        foreach (var component in components)
        {
            var simpleName = component.Id.Contains('.')
                ? component.Id.Substring(component.Id.LastIndexOf('.') + 1)
                : component.Id;

            if (!componentsBySimpleName.ContainsKey(simpleName))
                componentsBySimpleName[simpleName] = new List<Component>();
            componentsBySimpleName[simpleName].Add(component);
        }

        var discoveredTables = new Dictionary<string, string>(); // entity type name -> table name
        var contextOwnership = new Dictionary<string, HashSet<string>>(); // dbContext id -> entity types it exposes via DbSet

        foreach (var syntaxTree in compilation.SyntaxTrees)
        {
            SemanticModel? semanticModel = null;
            try
            {
                semanticModel = compilation.GetSemanticModel(syntaxTree);
            }
            catch
            {
                // Continue without semantic model
            }

            var root = syntaxTree.GetRoot();
            var classDeclarations = root.DescendantNodes().OfType<ClassDeclarationSyntax>();

            foreach (var classDecl in classDeclarations)
            {
                // Check for [Table("name")] attribute on any class
                var tableAttrName = ExtractTableAttribute(classDecl);
                if (tableAttrName != null)
                {
                    var className = GetClassName(classDecl, semanticModel);
                    discoveredTables[className] = tableAttrName;
                }

                // Check if this class inherits from DbContext
                if (!IsDbContext(classDecl, semanticModel))
                    continue;

                var contextId = GetClassName(classDecl, semanticModel);
                if (!contextOwnership.ContainsKey(contextId))
                    contextOwnership[contextId] = new HashSet<string>();

                // Find DbSet<T> properties
                var properties = classDecl.Members.OfType<PropertyDeclarationSyntax>();
                foreach (var property in properties)
                {
                    var entityTypeName = ExtractDbSetEntityType(property, semanticModel);
                    if (entityTypeName == null) continue;

                    contextOwnership[contextId].Add(entityTypeName);

                    if (!discoveredTables.ContainsKey(entityTypeName))
                    {
                        // Default table name is the property name (EF convention)
                        discoveredTables[entityTypeName] = property.Identifier.Text;
                    }
                }
            }
        }

        // Assign discovered tables to matching components.
        // Coverage targets: the entity component itself (when present), the owning DbContext,
        // and any repository whose declared field/parameter types reference the DbContext.
        foreach (var (entityTypeName, tableName) in discoveredTables)
        {
            AssignTable(entityTypeName, tableName, componentMap, componentsBySimpleName);
        }

        foreach (var (contextId, ownedEntities) in contextOwnership)
        {
            if (!componentMap.TryGetValue(contextId, out var contextComponent))
                continue;

            foreach (var entityType in ownedEntities)
            {
                if (!discoveredTables.TryGetValue(entityType, out var tableName))
                    continue;
                if (!contextComponent.TablesUsed.Contains(tableName))
                    contextComponent.TablesUsed.Add(tableName);
            }
        }
    }

    private static void AssignTable(
        string entityTypeName,
        string tableName,
        Dictionary<string, Component> componentMap,
        Dictionary<string, List<Component>> componentsBySimpleName)
    {
        if (componentMap.TryGetValue(entityTypeName, out var component))
        {
            if (!component.TablesUsed.Contains(tableName))
                component.TablesUsed.Add(tableName);
            return;
        }

        var simpleName = entityTypeName.Contains('.')
            ? entityTypeName.Substring(entityTypeName.LastIndexOf('.') + 1)
            : entityTypeName;

        if (componentsBySimpleName.TryGetValue(simpleName, out var matchingComponents))
        {
            foreach (var comp in matchingComponents)
            {
                if (!comp.TablesUsed.Contains(tableName))
                    comp.TablesUsed.Add(tableName);
            }
        }
    }

    private static bool IsDbContext(ClassDeclarationSyntax classDecl, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var classSymbol = semanticModel.GetDeclaredSymbol(classDecl) as INamedTypeSymbol;
            if (classSymbol != null)
            {
                var baseType = classSymbol.BaseType;
                while (baseType != null)
                {
                    if (baseType.Name == "DbContext")
                        return true;
                    baseType = baseType.BaseType;
                }
            }
        }
        else
        {
            // Fallback: check base list textually
            if (classDecl.BaseList != null)
            {
                foreach (var baseType in classDecl.BaseList.Types)
                {
                    var name = baseType.Type.ToString();
                    if (name == "DbContext" || name.EndsWith(".DbContext"))
                        return true;
                }
            }
        }

        return false;
    }

    private static string? ExtractDbSetEntityType(PropertyDeclarationSyntax property, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var propertySymbol = semanticModel.GetDeclaredSymbol(property) as IPropertySymbol;
            if (propertySymbol?.Type is INamedTypeSymbol namedType
                && namedType.Name == "DbSet"
                && namedType.TypeArguments.Length == 1)
            {
                var entityType = namedType.TypeArguments[0] as INamedTypeSymbol;
                if (entityType != null)
                    return GetFullyQualifiedName(entityType);
            }
        }
        else
        {
            // Fallback: parse syntax for DbSet<T>
            if (property.Type is GenericNameSyntax genericName
                && genericName.Identifier.Text == "DbSet"
                && genericName.TypeArgumentList.Arguments.Count == 1)
            {
                return genericName.TypeArgumentList.Arguments[0].ToString();
            }

            // Also handle qualified name like Microsoft.EntityFrameworkCore.DbSet<T>
            if (property.Type is QualifiedNameSyntax qualifiedName
                && qualifiedName.Right is GenericNameSyntax qualifiedGeneric
                && qualifiedGeneric.Identifier.Text == "DbSet"
                && qualifiedGeneric.TypeArgumentList.Arguments.Count == 1)
            {
                return qualifiedGeneric.TypeArgumentList.Arguments[0].ToString();
            }
        }

        return null;
    }

    private static string? ExtractTableAttribute(ClassDeclarationSyntax classDecl)
    {
        foreach (var attrList in classDecl.AttributeLists)
        {
            foreach (var attr in attrList.Attributes)
            {
                var name = attr.Name.ToString();
                var simpleName = name.Contains('.') ? name.Substring(name.LastIndexOf('.') + 1) : name;

                if (simpleName == "Table" || simpleName == "TableAttribute")
                {
                    if (attr.ArgumentList != null && attr.ArgumentList.Arguments.Count > 0)
                    {
                        var firstArg = attr.ArgumentList.Arguments[0];
                        if (firstArg.Expression is LiteralExpressionSyntax literal
                            && literal.IsKind(SyntaxKind.StringLiteralExpression))
                        {
                            return literal.Token.ValueText;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static string GetClassName(ClassDeclarationSyntax classDecl, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var symbol = semanticModel.GetDeclaredSymbol(classDecl) as INamedTypeSymbol;
            if (symbol != null)
                return GetFullyQualifiedName(symbol);
        }

        // Fallback
        var parts = new List<string>();
        var parent = classDecl.Parent;
        while (parent != null)
        {
            if (parent is BaseNamespaceDeclarationSyntax nsDecl)
                parts.Insert(0, nsDecl.Name.ToString());
            parent = parent.Parent;
        }
        parts.Add(classDecl.Identifier.Text);
        return string.Join(".", parts);
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
}
