using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class EndpointExtractor
{
    private static readonly Dictionary<string, string> HttpMethodAttributes = new(StringComparer.OrdinalIgnoreCase)
    {
        { "HttpGet", "GET" },
        { "HttpGetAttribute", "GET" },
        { "HttpPost", "POST" },
        { "HttpPostAttribute", "POST" },
        { "HttpPut", "PUT" },
        { "HttpPutAttribute", "PUT" },
        { "HttpDelete", "DELETE" },
        { "HttpDeleteAttribute", "DELETE" },
        { "HttpPatch", "PATCH" },
        { "HttpPatchAttribute", "PATCH" },
    };

    private static readonly HashSet<string> ParameterSourceAttributes = new(StringComparer.OrdinalIgnoreCase)
    {
        "FromBody", "FromBodyAttribute",
        "FromQuery", "FromQueryAttribute",
        "FromRoute", "FromRouteAttribute",
        "FromHeader", "FromHeaderAttribute",
        "FromForm", "FromFormAttribute",
    };

    public List<ApiEndpoint> ExtractEndpoints(Compilation compilation, List<Component> components)
    {
        var endpoints = new List<ApiEndpoint>();
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
                // Continue without semantic model
            }

            var root = syntaxTree.GetRoot();
            var classDeclarations = root.DescendantNodes().OfType<ClassDeclarationSyntax>();

            foreach (var classDecl in classDeclarations)
            {
                if (!IsApiController(classDecl, semanticModel))
                    continue;

                INamedTypeSymbol? classSymbol = null;
                if (semanticModel != null)
                {
                    classSymbol = semanticModel.GetDeclaredSymbol(classDecl) as INamedTypeSymbol;
                }

                var componentId = classSymbol != null
                    ? GetFullyQualifiedName(classSymbol)
                    : GetFallbackName(classDecl);

                var classRoute = ExtractRouteTemplate(classDecl.AttributeLists, classDecl, semanticModel);

                var methods = classDecl.Members.OfType<MethodDeclarationSyntax>();

                foreach (var method in methods)
                {
                    if (!method.Modifiers.Any(SyntaxKind.PublicKeyword))
                        continue;

                    var httpMethods = ExtractHttpMethods(method);
                    if (httpMethods.Count == 0)
                        continue;

                    foreach (var (httpMethod, methodRoute) in httpMethods)
                    {
                        var fullPath = CombineRoutes(classRoute, methodRoute);
                        var parameters = ExtractParameters(method, semanticModel);

                        var endpoint = new ApiEndpoint
                        {
                            Id = $"{httpMethod} {fullPath}",
                            Method = httpMethod,
                            Path = fullPath,
                            ComponentId = componentId,
                            Parameters = parameters,
                        };

                        // Extract return type as response schema hint
                        if (method.ReturnType != null)
                        {
                            endpoint.ResponseSchema = method.ReturnType.ToString();
                        }

                        endpoints.Add(endpoint);
                    }
                }
            }
        }

        return endpoints;
    }

    private static bool IsApiController(ClassDeclarationSyntax classDecl, SemanticModel? semanticModel)
    {
        // Check for [ApiController] attribute
        if (HasAttribute(classDecl.AttributeLists, "ApiController"))
            return true;

        // Check if inheriting from ControllerBase or Controller
        if (semanticModel != null)
        {
            var classSymbol = semanticModel.GetDeclaredSymbol(classDecl) as INamedTypeSymbol;
            if (classSymbol != null)
            {
                var baseType = classSymbol.BaseType;
                while (baseType != null)
                {
                    var baseName = baseType.Name;
                    if (baseName == "ControllerBase" || baseName == "Controller")
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
                    if (name == "ControllerBase" || name == "Controller"
                        || name.EndsWith(".ControllerBase") || name.EndsWith(".Controller"))
                        return true;
                }
            }
        }

        return false;
    }

    private static bool HasAttribute(SyntaxList<AttributeListSyntax> attributeLists, string attributeName)
    {
        foreach (var attrList in attributeLists)
        {
            foreach (var attr in attrList.Attributes)
            {
                var name = attr.Name.ToString();
                if (name == attributeName || name == attributeName + "Attribute"
                    || name.EndsWith("." + attributeName) || name.EndsWith("." + attributeName + "Attribute"))
                    return true;
            }
        }
        return false;
    }

    private static string ExtractRouteTemplate(
        SyntaxList<AttributeListSyntax> attributeLists,
        ClassDeclarationSyntax classDecl,
        SemanticModel? semanticModel)
    {
        foreach (var attrList in attributeLists)
        {
            foreach (var attr in attrList.Attributes)
            {
                var name = attr.Name.ToString();
                if (name == "Route" || name == "RouteAttribute"
                    || name.EndsWith(".Route") || name.EndsWith(".RouteAttribute"))
                {
                    return ExtractFirstArgumentValue(attr);
                }
            }
        }
        return string.Empty;
    }

    private static List<(string HttpMethod, string Route)> ExtractHttpMethods(MethodDeclarationSyntax method)
    {
        var results = new List<(string, string)>();

        foreach (var attrList in method.AttributeLists)
        {
            foreach (var attr in attrList.Attributes)
            {
                var name = attr.Name.ToString();

                // Strip namespace prefix if present
                var simpleName = name.Contains('.') ? name.Substring(name.LastIndexOf('.') + 1) : name;

                if (HttpMethodAttributes.TryGetValue(simpleName, out var httpMethod))
                {
                    var route = ExtractFirstArgumentValue(attr);
                    results.Add((httpMethod, route));
                }
            }
        }

        return results;
    }

    private static string ExtractFirstArgumentValue(AttributeSyntax attr)
    {
        if (attr.ArgumentList == null || attr.ArgumentList.Arguments.Count == 0)
            return string.Empty;

        var firstArg = attr.ArgumentList.Arguments[0];
        var expression = firstArg.Expression;

        // Handle string literal
        if (expression is LiteralExpressionSyntax literal && literal.IsKind(SyntaxKind.StringLiteralExpression))
        {
            return literal.Token.ValueText;
        }

        // Fallback: use the raw text without quotes
        var text = expression.ToString();
        if (text.StartsWith("\"") && text.EndsWith("\""))
            text = text.Substring(1, text.Length - 2);

        return text;
    }

    private static string CombineRoutes(string classRoute, string methodRoute)
    {
        var combined = classRoute.TrimEnd('/');

        if (!string.IsNullOrEmpty(methodRoute))
        {
            if (methodRoute.StartsWith("/"))
            {
                // Absolute route on method overrides class route
                combined = methodRoute;
            }
            else
            {
                combined = string.IsNullOrEmpty(combined)
                    ? methodRoute
                    : $"{combined}/{methodRoute}";
            }
        }

        // Ensure leading slash
        if (!string.IsNullOrEmpty(combined) && !combined.StartsWith("/"))
            combined = "/" + combined;

        return string.IsNullOrEmpty(combined) ? "/" : combined;
    }

    private static List<Parameter> ExtractParameters(MethodDeclarationSyntax method, SemanticModel? semanticModel)
    {
        var parameters = new List<Parameter>();

        foreach (var param in method.ParameterList.Parameters)
        {
            string? source = null;

            foreach (var attrList in param.AttributeLists)
            {
                foreach (var attr in attrList.Attributes)
                {
                    var name = attr.Name.ToString();
                    var simpleName = name.Contains('.') ? name.Substring(name.LastIndexOf('.') + 1) : name;

                    if (ParameterSourceAttributes.Contains(simpleName))
                    {
                        // Normalize to lowercase source name
                        source = simpleName.Replace("Attribute", "")
                            .Replace("From", "")
                            .ToLowerInvariant();
                        break;
                    }
                }
                if (source != null) break;
            }

            if (source != null)
            {
                var typeName = param.Type?.ToString() ?? "object";

                parameters.Add(new Parameter
                {
                    Name = param.Identifier.Text,
                    In = source,
                    Type = typeName,
                    Required = param.Default == null
                });
            }
        }

        return parameters;
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

    private static string GetFallbackName(ClassDeclarationSyntax classDecl)
    {
        var parts = new List<string>();
        var parent = classDecl.Parent;
        while (parent != null)
        {
            if (parent is BaseNamespaceDeclarationSyntax nsDecl)
            {
                parts.Insert(0, nsDecl.Name.ToString());
            }
            parent = parent.Parent;
        }
        parts.Add(classDecl.Identifier.Text);
        return string.Join(".", parts);
    }
}
