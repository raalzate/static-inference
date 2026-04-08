using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class MessagingDetector
{
    // MassTransit interface names that indicate consumer role
    private static readonly HashSet<string> MassTransitConsumerInterfaces = new(StringComparer.Ordinal)
    {
        "IConsumer",
    };

    // MassTransit types that indicate publisher role (used as constructor parameters or fields)
    private static readonly HashSet<string> MassTransitPublisherTypes = new(StringComparer.Ordinal)
    {
        "IPublishEndpoint",
        "IBus",
    };

    // Azure Service Bus types that indicate publisher role
    private static readonly HashSet<string> AzureServiceBusSenderTypes = new(StringComparer.Ordinal)
    {
        "ServiceBusClient",
        "ServiceBusSender",
    };

    // Azure Service Bus types that indicate consumer role
    private static readonly HashSet<string> AzureServiceBusProcessorTypes = new(StringComparer.Ordinal)
    {
        "ServiceBusProcessor",
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

                DetectMassTransitPatterns(typeDecl, semanticModel, component);
                DetectAzureServiceBusPatterns(typeDecl, semanticModel, component);
            }
        }
    }

    private static void DetectMassTransitPatterns(
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel,
        Component component)
    {
        // Check implemented interfaces for IConsumer<T>
        if (typeDecl is ClassDeclarationSyntax classDecl && classDecl.BaseList != null)
        {
            foreach (var baseType in classDecl.BaseList.Types)
            {
                var typeName = GetBaseTypeName(baseType, semanticModel);
                if (MassTransitConsumerInterfaces.Contains(typeName))
                {
                    component.MessagingType = "MassTransit";
                    component.MessagingRole = "consumer";
                    return;
                }
            }
        }

        // Check constructor parameters and fields for IPublishEndpoint / IBus
        if (HasFieldOrParameterOfType(typeDecl, semanticModel, MassTransitPublisherTypes))
        {
            component.MessagingType = "MassTransit";
            component.MessagingRole = "publisher";
        }
    }

    private static void DetectAzureServiceBusPatterns(
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel,
        Component component)
    {
        // Skip if already detected as a messaging type
        if (component.MessagingType != null)
            return;

        bool hasSenderType = HasFieldOrParameterOfType(typeDecl, semanticModel, AzureServiceBusSenderTypes);
        bool hasProcessorType = HasFieldOrParameterOfType(typeDecl, semanticModel, AzureServiceBusProcessorTypes);

        if (hasProcessorType)
        {
            component.MessagingType = "AzureServiceBus";
            component.MessagingRole = "consumer";
        }
        else if (hasSenderType)
        {
            component.MessagingType = "AzureServiceBus";
            component.MessagingRole = "publisher";
        }
    }

    private static bool HasFieldOrParameterOfType(
        TypeDeclarationSyntax typeDecl,
        SemanticModel? semanticModel,
        HashSet<string> targetTypeNames)
    {
        // Check field declarations
        var fields = typeDecl.DescendantNodes().OfType<FieldDeclarationSyntax>();
        foreach (var field in fields)
        {
            var fieldTypeName = GetTypeNameFromTypeSyntax(field.Declaration.Type, semanticModel);
            if (targetTypeNames.Contains(fieldTypeName))
                return true;
        }

        // Check constructor parameters
        var constructors = typeDecl.DescendantNodes().OfType<ConstructorDeclarationSyntax>();
        foreach (var ctor in constructors)
        {
            if (ctor.ParameterList == null) continue;
            foreach (var param in ctor.ParameterList.Parameters)
            {
                if (param.Type == null) continue;
                var paramTypeName = GetTypeNameFromTypeSyntax(param.Type, semanticModel);
                if (targetTypeNames.Contains(paramTypeName))
                    return true;
            }
        }

        return false;
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

    private static string GetBaseTypeName(BaseTypeSyntax baseType, SemanticModel? semanticModel)
    {
        if (semanticModel != null)
        {
            var typeInfo = semanticModel.GetTypeInfo(baseType.Type);
            if (typeInfo.Type is INamedTypeSymbol namedType)
            {
                return namedType.Name;
            }
        }

        // Fallback: extract the simple name (strip namespace and generic args)
        var fullName = baseType.Type.ToString();
        // Remove generic arguments
        var genericIdx = fullName.IndexOf('<');
        if (genericIdx >= 0)
            fullName = fullName.Substring(0, genericIdx);
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
