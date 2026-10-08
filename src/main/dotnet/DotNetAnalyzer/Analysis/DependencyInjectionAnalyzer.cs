using Microsoft.CodeAnalysis;
using DotNetAnalyzer.Model;
using CSharpSyntax = Microsoft.CodeAnalysis.CSharp.Syntax;
using VbSyntax = Microsoft.CodeAnalysis.VisualBasic.Syntax;

namespace DotNetAnalyzer.Analysis;

/// <summary>
/// Pass 3: Analyzes dependency injection patterns in .NET projects.
/// Detects:
///   - constructor injection through interface-typed parameters (C# and VB),
///   - interface implementations via INamedTypeSymbol.Interfaces (language-agnostic),
///   - IServiceCollection service registrations (C# generic syntax only — VB
///     ASP.NET Core projects are out of the legacy desktop scope and do not
///     typically use this pattern).
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
        var edgeMap = new Dictionary<string, Edge>();

        // Pass 3b — language-agnostic. Walk all named types via GlobalNamespace
        // and inspect INamedTypeSymbol.Interfaces. Done once for the compilation,
        // independent of syntax trees.
        AnalyzeInterfaceImplementationsViaSymbols(compilation, knownComponents, edgeMap);

        // Passes 3a (constructor injection) and 3c (service registrations) are
        // syntax-driven and dispatched per-language.
        foreach (var syntaxTree in compilation.SyntaxTrees)
        {
            SemanticModel? semanticModel;
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

            if (compilation.Language == LanguageNames.CSharp)
            {
                AnalyzeConstructorInjectionCSharp(root, semanticModel, knownComponents, edgeMap);
                AnalyzeServiceRegistrationsCSharp(root, semanticModel, knownComponents, edgeMap);
            }
            else if (compilation.Language == LanguageNames.VisualBasic)
            {
                AnalyzeConstructorInjectionVb(root, semanticModel, knownComponents, edgeMap);
                // Service registrations (AddScoped<,>) is C#-only in this scope.
            }
        }

        return edgeMap.Values.ToList();
    }

    // ─── Pass 3b — language-agnostic via GlobalNamespace ─────────────────────

    private static void AnalyzeInterfaceImplementationsViaSymbols(
        Compilation compilation,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        foreach (var typeSymbol in EnumerateNamedTypes(compilation.Assembly.GlobalNamespace))
        {
            if (typeSymbol.TypeKind != TypeKind.Class && typeSymbol.TypeKind != TypeKind.Struct)
                continue;

            if (typeSymbol.DeclaringSyntaxReferences.IsDefaultOrEmpty)
                continue;

            var classId = GetFullyQualifiedName(typeSymbol);
            if (!knownComponents.Contains(classId))
                continue;

            foreach (var iface in typeSymbol.Interfaces)
            {
                var ifaceId = GetFullyQualifiedName(iface);
                if (!knownComponents.Contains(ifaceId))
                    continue;

                AddOrIncrementEdge(edgeMap, classId, ifaceId, "implements");
            }
        }
    }

    private static IEnumerable<INamedTypeSymbol> EnumerateNamedTypes(INamespaceSymbol ns)
    {
        foreach (var type in ns.GetTypeMembers())
        {
            yield return type;
            foreach (var nested in type.GetTypeMembers())
                yield return nested;
        }
        foreach (var sub in ns.GetNamespaceMembers())
            foreach (var t in EnumerateNamedTypes(sub))
                yield return t;
    }

    // ─── Pass 3a — constructor injection (C# walker) ──────────────────────────

    private static void AnalyzeConstructorInjectionCSharp(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var constructors = root.DescendantNodes().OfType<CSharpSyntax.ConstructorDeclarationSyntax>();

        foreach (var constructor in constructors)
        {
            var containingType = constructor.Ancestors()
                .OfType<CSharpSyntax.TypeDeclarationSyntax>()
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

                var paramType = semanticModel.GetTypeInfo(parameter.Type).Type as INamedTypeSymbol;
                AddInjectionIfInterfaceKnown(paramType, containerId, knownComponents, edgeMap);
            }
        }
    }

    // ─── Pass 3a — constructor injection (VB walker) ──────────────────────────

    private static void AnalyzeConstructorInjectionVb(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var constructors = root.DescendantNodes().OfType<VbSyntax.ConstructorBlockSyntax>();

        foreach (var constructor in constructors)
        {
            var containingType = constructor.Ancestors()
                .OfType<VbSyntax.TypeBlockSyntax>()
                .FirstOrDefault();

            if (containingType == null)
                continue;

            var containingSymbol = semanticModel.GetDeclaredSymbol(containingType) as INamedTypeSymbol;
            if (containingSymbol == null)
                continue;

            var containerId = GetFullyQualifiedName(containingSymbol);
            if (!knownComponents.Contains(containerId))
                continue;

            var parameterList = constructor.SubNewStatement?.ParameterList;
            if (parameterList == null)
                continue;

            foreach (var parameter in parameterList.Parameters)
            {
                var typeSyntax = parameter.AsClause?.Type;
                if (typeSyntax == null)
                    continue;

                var paramType = semanticModel.GetTypeInfo(typeSyntax).Type as INamedTypeSymbol;
                AddInjectionIfInterfaceKnown(paramType, containerId, knownComponents, edgeMap);
            }
        }
    }

    // ─── Pass 3c — IServiceCollection registrations (C# only) ─────────────────

    private static void AnalyzeServiceRegistrationsCSharp(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var invocations = root.DescendantNodes().OfType<CSharpSyntax.InvocationExpressionSyntax>();

        foreach (var invocation in invocations)
        {
            string? methodName = null;

            if (invocation.Expression is CSharpSyntax.MemberAccessExpressionSyntax memberAccess)
            {
                methodName = memberAccess.Name is CSharpSyntax.GenericNameSyntax genericName
                    ? genericName.Identifier.Text
                    : memberAccess.Name.Identifier.Text;
            }

            if (methodName == null || !RegistrationMethods.Contains(methodName))
                continue;

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
                        AddOrIncrementEdge(edgeMap, implId, serviceId, "registration");
                    }

                    continue;
                }
            }

            if (invocation.Expression is CSharpSyntax.MemberAccessExpressionSyntax ma
                && ma.Name is CSharpSyntax.GenericNameSyntax gns
                && gns.TypeArgumentList.Arguments.Count == 2)
            {
                var serviceTypeSyntax = gns.TypeArgumentList.Arguments[0];
                var implTypeSyntax = gns.TypeArgumentList.Arguments[1];

                var serviceSymbol = semanticModel.GetTypeInfo(serviceTypeSyntax).Type as INamedTypeSymbol;
                var implSymbol = semanticModel.GetTypeInfo(implTypeSyntax).Type as INamedTypeSymbol;

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

    // ─── Shared helpers ───────────────────────────────────────────────────────

    private static void AddInjectionIfInterfaceKnown(
        INamedTypeSymbol? paramType,
        string containerId,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        if (paramType == null)
            return;

        // Only create injection edges for interface-typed parameters
        if (paramType.TypeKind != TypeKind.Interface)
            return;

        var paramTypeId = GetFullyQualifiedName(paramType);
        if (!knownComponents.Contains(paramTypeId))
            return;

        if (containerId == paramTypeId)
            return;

        AddOrIncrementEdge(edgeMap, containerId, paramTypeId, "injection");
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
