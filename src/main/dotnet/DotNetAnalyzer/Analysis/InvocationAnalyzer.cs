using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class InvocationAnalyzer
{
    public List<Edge> AnalyzeInvocations(Compilation compilation, List<Component> components)
    {
        var knownComponents = new HashSet<string>(components.Select(c => c.Id));
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

            // Pass 2a: Method call dependencies
            AnalyzeMethodCalls(root, semanticModel, knownComponents, edgeMap);

            // Pass 2b: Constructor injection dependencies
            AnalyzeConstructorInjection(root, semanticModel, knownComponents, edgeMap);
        }

        return edgeMap.Values.ToList();
    }

    private static void AnalyzeMethodCalls(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var invocations = root.DescendantNodes().OfType<InvocationExpressionSyntax>();

        foreach (var invocation in invocations)
        {
            // Find the caller type (the enclosing class/interface)
            var callerType = invocation.Ancestors()
                .OfType<TypeDeclarationSyntax>()
                .FirstOrDefault();

            if (callerType == null)
                continue;

            var callerSymbol = semanticModel.GetDeclaredSymbol(callerType) as INamedTypeSymbol;
            if (callerSymbol == null)
                continue;

            var callerId = GetFullyQualifiedName(callerSymbol);
            if (!knownComponents.Contains(callerId))
                continue;

            // Resolve the invoked method
            var symbolInfo = semanticModel.GetSymbolInfo(invocation);
            var methodSymbol = symbolInfo.Symbol as IMethodSymbol
                ?? symbolInfo.CandidateSymbols.OfType<IMethodSymbol>().FirstOrDefault();

            if (methodSymbol?.ContainingType == null)
                continue;

            var calleeId = GetFullyQualifiedName(methodSymbol.ContainingType);

            if (!knownComponents.Contains(calleeId))
                continue;

            // Skip self-calls
            if (callerId == calleeId)
                continue;

            AddOrIncrementEdge(edgeMap, callerId, calleeId, "call");
        }
    }

    private static void AnalyzeConstructorInjection(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
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

                var paramTypeId = GetFullyQualifiedName(paramType);
                if (!knownComponents.Contains(paramTypeId))
                    continue;

                if (containerId == paramTypeId)
                    continue;

                AddOrIncrementEdge(edgeMap, containerId, paramTypeId, "injection");
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
