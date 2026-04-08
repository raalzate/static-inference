using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Pass 3: Analyzes dependency injection patterns in .NET projects.
/// Detects constructor injection, interface implementations, and IServiceCollection registrations.
/// </summary>
public class DependencyInjectionAnalyzer
{
    private static readonly HashSet<string> RegistrationMethods = new(StringComparer.Ordinal)
    {
        "AddScoped",
        "AddTransient",
        "AddSingleton",
    };

    public List<Edge> Analyze(Compilation compilation, List<Component> components)
    {
        var knownComponents = new HashSet<string>(components.Select(c => c.Id));
        var interfaceComponents = new HashSet<string>(
            components.Where(c => c.IsInterface).Select(c => c.Id));
        var edgeMap = new Dictionary<string, Edge>();

        foreach (var syntaxTree in compilation.SyntaxTrees)
        {
            SemanticModel? semanticModel = null;
            try
            {
                semanticModel = compilation.GetSemanticModel(syntaxTree);
            }
            catch
            {
                continue;
            }

            if (semanticModel == null)
                continue;

            var root = syntaxTree.GetRoot();

            // Pass 3a: Constructor injection — interface-typed constructor params
            AnalyzeConstructorInjection(root, semanticModel, knownComponents, interfaceComponents, edgeMap);

            // Pass 3b: Interface implementations — class : IInterface pattern
            AnalyzeInterfaceImplementations(root, semanticModel, knownComponents, edgeMap);

            // Pass 3c: IServiceCollection registrations — AddScoped/AddTransient/AddSingleton<I, T>
            AnalyzeServiceRegistrations(root, semanticModel, knownComponents, edgeMap);
        }

        return edgeMap.Values.ToList();
    }

    private static void AnalyzeConstructorInjection(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        HashSet<string> interfaceComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var constructors = root.DescendantNodes().OfType<ConstructorDeclarationSyntax>();

        foreach (var constructor in constructors)
        {
            var containingType = constructor.Ancestors()
                .OfType<TypeDeclarationSyntax>()
                .FirstOrDefault();

            if (containingType == null)
                continue;

            var containingSymbol = semanticModel.GetDeclaredSymbol(containingType) as INamedTypeSymbol;
            if (containingSymbol == null)
                continue;

            var containerId = GetFullyQualifiedName(containingSymbol);
            if (!knownComponents.Contains(containerId))
                continue;

            foreach (var parameter in constructor.ParameterList.Parameters)
            {
                if (parameter.Type == null)
                    continue;

                var typeInfo = semanticModel.GetTypeInfo(parameter.Type);
                var paramType = typeInfo.Type as INamedTypeSymbol;
                if (paramType == null)
                    continue;

                // Only create injection edges for interface-typed parameters
                if (paramType.TypeKind != TypeKind.Interface)
                    continue;

                var paramTypeId = GetFullyQualifiedName(paramType);
                if (!knownComponents.Contains(paramTypeId))
                    continue;

                if (containerId == paramTypeId)
                    continue;

                AddOrIncrementEdge(edgeMap, containerId, paramTypeId, "injection");
            }
        }
    }

    private static void AnalyzeInterfaceImplementations(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var classDeclarations = root.DescendantNodes().OfType<ClassDeclarationSyntax>();

        foreach (var classDecl in classDeclarations)
        {
            var classSymbol = semanticModel.GetDeclaredSymbol(classDecl) as INamedTypeSymbol;
            if (classSymbol == null)
                continue;

            var classId = GetFullyQualifiedName(classSymbol);
            if (!knownComponents.Contains(classId))
                continue;

            foreach (var iface in classSymbol.Interfaces)
            {
                var ifaceId = GetFullyQualifiedName(iface);

                // Only create edge if the interface is a known component
                if (!knownComponents.Contains(ifaceId))
                    continue;

                AddOrIncrementEdge(edgeMap, classId, ifaceId, "implements");
            }
        }
    }

    private static void AnalyzeServiceRegistrations(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var invocations = root.DescendantNodes().OfType<InvocationExpressionSyntax>();

        foreach (var invocation in invocations)
        {
            // Match pattern: services.AddScoped<IService, Implementation>()
            // or services.AddTransient<IService, Implementation>()
            // or services.AddSingleton<IService, Implementation>()

            string? methodName = null;

            if (invocation.Expression is MemberAccessExpressionSyntax memberAccess)
            {
                methodName = memberAccess.Name is GenericNameSyntax genericName
                    ? genericName.Identifier.Text
                    : memberAccess.Name.Identifier.Text;
            }

            if (methodName == null || !RegistrationMethods.Contains(methodName))
                continue;

            // Try to resolve via semantic model for accurate type resolution
            var symbolInfo = semanticModel.GetSymbolInfo(invocation);
            var methodSymbol = symbolInfo.Symbol as IMethodSymbol
                ?? symbolInfo.CandidateSymbols.OfType<IMethodSymbol>().FirstOrDefault();

            if (methodSymbol != null && methodSymbol.TypeArguments.Length == 2)
            {
                var serviceType = methodSymbol.TypeArguments[0] as INamedTypeSymbol;
                var implType = methodSymbol.TypeArguments[1] as INamedTypeSymbol;

                if (serviceType != null && implType != null)
                {
                    var serviceId = GetFullyQualifiedName(serviceType);
                    var implId = GetFullyQualifiedName(implType);

                    if (knownComponents.Contains(serviceId) && knownComponents.Contains(implId))
                    {
                        // Edge from implementation to interface (implementation registers as service)
                        AddOrIncrementEdge(edgeMap, implId, serviceId, "registration");
                    }

                    continue;
                }
            }

            // Fallback: parse type arguments from syntax when semantic model cannot resolve
            if (invocation.Expression is MemberAccessExpressionSyntax ma
                && ma.Name is GenericNameSyntax gns
                && gns.TypeArgumentList.Arguments.Count == 2)
            {
                var serviceTypeSyntax = gns.TypeArgumentList.Arguments[0];
                var implTypeSyntax = gns.TypeArgumentList.Arguments[1];

                var serviceTypeInfo = semanticModel.GetTypeInfo(serviceTypeSyntax);
                var implTypeInfo = semanticModel.GetTypeInfo(implTypeSyntax);

                var serviceSymbol = serviceTypeInfo.Type as INamedTypeSymbol;
                var implSymbol = implTypeInfo.Type as INamedTypeSymbol;

                if (serviceSymbol != null && implSymbol != null)
                {
                    var serviceId = GetFullyQualifiedName(serviceSymbol);
                    var implId = GetFullyQualifiedName(implSymbol);

                    if (knownComponents.Contains(serviceId) && knownComponents.Contains(implId))
                    {
                        AddOrIncrementEdge(edgeMap, implId, serviceId, "registration");
                    }
                }
            }
        }
    }

    private static void AddOrIncrementEdge(
        Dictionary<string, Edge> edgeMap,
        string from,
        string to,
        string type)
    {
        var key = $"{from}|{to}|{type}";

        if (edgeMap.TryGetValue(key, out var existing))
        {
            existing.Weight++;
        }
        else
        {
            edgeMap[key] = new Edge
            {
                From = from,
                To = to,
                Weight = 1,
                Type = type
            };
        }
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
