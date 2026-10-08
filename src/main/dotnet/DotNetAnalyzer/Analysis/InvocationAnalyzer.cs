using Microsoft.CodeAnalysis;
using DotNetAnalyzer.Model;
using CSharpSyntax = Microsoft.CodeAnalysis.CSharp.Syntax;
using VbSyntax = Microsoft.CodeAnalysis.VisualBasic.Syntax;

namespace DotNetAnalyzer.Analysis;

public class InvocationAnalyzer
{
    public List<Edge> AnalyzeInvocations(Compilation compilation, List<Component> components)
    {
        var knownComponents = new HashSet<string>(components.Select(c => c.Id));
        var edgeMap = new Dictionary<string, Edge>();

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
                AnalyzeMethodCallsCSharp(root, semanticModel, knownComponents, edgeMap);
                AnalyzeConstructorInjectionCSharp(root, semanticModel, knownComponents, edgeMap);
            }
            else if (compilation.Language == LanguageNames.VisualBasic)
            {
                AnalyzeMethodCallsVb(root, semanticModel, knownComponents, edgeMap);
                AnalyzeConstructorInjectionVb(root, semanticModel, knownComponents, edgeMap);
            }
        }

        return edgeMap.Values.ToList();
    }

    // ─── C# walkers (preserved verbatim) ──────────────────────────────────────

    private static void AnalyzeMethodCallsCSharp(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var invocations = root.DescendantNodes().OfType<CSharpSyntax.InvocationExpressionSyntax>();

        foreach (var invocation in invocations)
        {
            var callerType = invocation.Ancestors()
                .OfType<CSharpSyntax.TypeDeclarationSyntax>()
                .FirstOrDefault();

            if (callerType == null)
                continue;

            var callerSymbol = semanticModel.GetDeclaredSymbol(callerType) as INamedTypeSymbol;
            if (callerSymbol == null)
                continue;

            var callerId = GetFullyQualifiedName(callerSymbol);
            if (!knownComponents.Contains(callerId))
                continue;

            ResolveCallAndAddEdge(invocation, semanticModel, callerId, knownComponents, edgeMap);
        }
    }

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
                AddInjectionIfKnown(paramType, containerId, knownComponents, edgeMap);
            }
        }
    }

    // ─── VB walkers (parallel structure, VB syntax nodes) ─────────────────────

    private static void AnalyzeMethodCallsVb(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var invocations = root.DescendantNodes().OfType<VbSyntax.InvocationExpressionSyntax>();

        foreach (var invocation in invocations)
        {
            // VB types are declared with ClassBlock, ModuleBlock, StructureBlock,
            // InterfaceBlock — all derive from TypeBlockSyntax in the VB API.
            var callerType = invocation.Ancestors()
                .OfType<VbSyntax.TypeBlockSyntax>()
                .FirstOrDefault();

            if (callerType == null)
                continue;

            var callerSymbol = semanticModel.GetDeclaredSymbol(callerType) as INamedTypeSymbol;
            if (callerSymbol == null)
                continue;

            var callerId = GetFullyQualifiedName(callerSymbol);
            if (!knownComponents.Contains(callerId))
                continue;

            ResolveCallAndAddEdge(invocation, semanticModel, callerId, knownComponents, edgeMap);
        }
    }

    private static void AnalyzeConstructorInjectionVb(
        SyntaxNode root,
        SemanticModel semanticModel,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        // VB declares constructors as `Sub New(...)` inside a ConstructorBlockSyntax.
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

            // Sub New parameters live on the ConstructorBlock's SubNewStatement.
            var parameterList = constructor.SubNewStatement?.ParameterList;
            if (parameterList == null)
                continue;

            foreach (var parameter in parameterList.Parameters)
            {
                var typeSyntax = parameter.AsClause?.Type;
                if (typeSyntax == null)
                    continue;

                var paramType = semanticModel.GetTypeInfo(typeSyntax).Type as INamedTypeSymbol;
                AddInjectionIfKnown(paramType, containerId, knownComponents, edgeMap);
            }
        }
    }

    // ─── Shared symbol-level logic ───────────────────────────────────────────

    private static void ResolveCallAndAddEdge(
        SyntaxNode invocation,
        SemanticModel semanticModel,
        string callerId,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        var symbolInfo = semanticModel.GetSymbolInfo(invocation);
        var methodSymbol = symbolInfo.Symbol as IMethodSymbol
            ?? symbolInfo.CandidateSymbols.OfType<IMethodSymbol>().FirstOrDefault();

        if (methodSymbol?.ContainingType == null)
            return;

        var calleeId = GetFullyQualifiedName(methodSymbol.ContainingType);
        if (!knownComponents.Contains(calleeId))
            return;

        if (callerId == calleeId)
            return;

        AddOrIncrementEdge(edgeMap, callerId, calleeId, "call");
    }

    private static void AddInjectionIfKnown(
        INamedTypeSymbol? paramType,
        string containerId,
        HashSet<string> knownComponents,
        Dictionary<string, Edge> edgeMap)
    {
        if (paramType == null)
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
