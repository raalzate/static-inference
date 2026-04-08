using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class RoslynCodeAnalyzer
{
    private static readonly HashSet<string> SecretKeywords = new(StringComparer.OrdinalIgnoreCase)
    {
        "password", "passwd", "secret", "apikey", "api_key",
        "token", "accesstoken", "access_token", "connectionstring"
    };

    public void Analyze(Compilation compilation, List<Component> components)
    {
        var componentMap = components.ToDictionary(c => c.Id, c => c);

        foreach (var syntaxTree in compilation.SyntaxTrees)
        {
            var root = syntaxTree.GetRoot();
            var relativePath = GetRelativePath(syntaxTree.FilePath);

            // Find type declarations and map to components by fully-qualified name
            SemanticModel? semanticModel = null;
            try
            {
                semanticModel = compilation.GetSemanticModel(syntaxTree);
            }
            catch
            {
                // Gracefully handle unavailable semantic model
            }

            var typeDeclarations = root.DescendantNodes()
                .OfType<TypeDeclarationSyntax>()
                .Where(t => t is ClassDeclarationSyntax or InterfaceDeclarationSyntax);

            foreach (var typeDecl in typeDeclarations)
            {
                var typeName = GetFullyQualifiedName(typeDecl, semanticModel);
                if (!componentMap.TryGetValue(typeName, out var component))
                    continue;

                try
                {
                    var walker = new BugPatternWalker(component, relativePath);
                    walker.Visit(typeDecl);
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"[WARN] RoslynCodeAnalyzer: Error analyzing type '{typeName}': {ex.Message}");
                }
            }
        }

        // Sort issues by location for determinism (FR-007, SC-003)
        foreach (var component in components)
        {
            component.CodeIssues.Sort((a, b) =>
            {
                var locCompare = string.Compare(a.Location ?? "", b.Location ?? "", StringComparison.Ordinal);
                return locCompare != 0 ? locCompare : string.Compare(a.Rule, b.Rule, StringComparison.Ordinal);
            });
        }
    }

    private static string GetRelativePath(string fullPath)
    {
        if (string.IsNullOrEmpty(fullPath))
            return "unknown";
        return Path.GetFileName(fullPath);
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

        // Fallback without semantic model
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

    private class BugPatternWalker : CSharpSyntaxWalker
    {
        private readonly Component _component;
        private readonly string _relativePath;

        public BugPatternWalker(Component component, string relativePath)
        {
            _component = component;
            _relativePath = relativePath;
        }

        // RULE 1: EMPTY_CATCH_BLOCK
        // RULE 2: CATCH_GENERIC_EXCEPTION
        public override void VisitCatchClause(CatchClauseSyntax node)
        {
            base.VisitCatchClause(node);

            // Empty catch block
            if (node.Block.Statements.Count == 0)
            {
                AddIssue("EMPTY_CATCH_BLOCK", "WARNING",
                    "Empty catch block detected. Exceptions should not be swallowed.",
                    node);
            }

            // Generic exception catch
            if (node.Declaration != null)
            {
                var typeName = node.Declaration.Type.ToString();
                var simpleTypeName = typeName.Split('.').Last();

                if (simpleTypeName is "Exception" or "SystemException")
                {
                    AddIssue("CATCH_GENERIC_EXCEPTION", "INFO",
                        $"Catching generic {simpleTypeName} is generally discouraged. Use a more specific exception type.",
                        node);
                }
            }
        }

        // RULE 3: CONSOLE_LOGGING
        // RULE 4: STRING_EQUALITY_OPERATOR (ReferenceEquals)
        // RULE 6: DEPRECATED_THREAD_USAGE (Thread.Abort)
        public override void VisitInvocationExpression(InvocationExpressionSyntax node)
        {
            base.VisitInvocationExpression(node);

            if (node.Expression is MemberAccessExpressionSyntax memberAccess)
            {
                var memberName = memberAccess.Name.Identifier.Text;
                var expressionText = memberAccess.Expression.ToString();
                var simpleExprName = expressionText.Split('.').Last();

                // Console.Write / Console.WriteLine
                if (simpleExprName == "Console" && memberName is "Write" or "WriteLine")
                {
                    AddIssue("CONSOLE_LOGGING", "INFO",
                        "Direct use of Console.Write/WriteLine. Use a logger (ILogger) instead.",
                        node);
                }

                // Thread.Abort()
                if (memberName == "Abort")
                {
                    AddIssue("DEPRECATED_THREAD_USAGE", "ERROR",
                        "Usage of Thread.Abort() which is deprecated and throws PlatformNotSupportedException in .NET 6+.",
                        node);
                }

                // object.ReferenceEquals(a, b) or Object.ReferenceEquals(a, b)
                if (memberName == "ReferenceEquals")
                {
                    var expr = memberAccess.Expression.ToString();
                    if (expr is "object" or "Object" or "System.Object")
                    {
                        AddIssue("STRING_EQUALITY_OPERATOR", "WARNING",
                            "Use of ReferenceEquals for string comparison. Use string.Equals() or == operator instead.",
                            node);
                    }
                }
            }

            // ReferenceEquals(a, b) — unqualified invocation
            if (node.Expression is IdentifierNameSyntax identifierName &&
                identifierName.Identifier.Text == "ReferenceEquals")
            {
                AddIssue("STRING_EQUALITY_OPERATOR", "WARNING",
                    "Use of ReferenceEquals for string comparison. Use string.Equals() or == operator instead.",
                    node);
            }
        }

        // RULE 5: HARDCODED_SECRET_IN_CODE
        public override void VisitVariableDeclarator(VariableDeclaratorSyntax node)
        {
            base.VisitVariableDeclarator(node);

            if (node.Initializer?.Value is LiteralExpressionSyntax literal &&
                literal.IsKind(SyntaxKind.StringLiteralExpression))
            {
                var varName = node.Identifier.Text;
                var literalValue = literal.Token.ValueText;

                if (!string.IsNullOrWhiteSpace(literalValue) &&
                    SecretKeywords.Any(keyword => varName.IndexOf(keyword, StringComparison.OrdinalIgnoreCase) >= 0))
                {
                    AddIssue("HARDCODED_SECRET_IN_CODE", "CRITICAL",
                        $"Hardcoded secret detected in variable '{varName}'. Use configuration or secret management instead.",
                        node);
                }
            }
        }

        // RULE 7: MISSING_SWITCH_DEFAULT
        public override void VisitSwitchStatement(SwitchStatementSyntax node)
        {
            base.VisitSwitchStatement(node);

            var hasDefault = node.Sections
                .SelectMany(s => s.Labels)
                .Any(label => label is DefaultSwitchLabelSyntax);

            if (!hasDefault)
            {
                AddIssue("MISSING_SWITCH_DEFAULT", "INFO",
                    "Switch statement is missing a 'default' case.",
                    node);
            }
        }

        // RULE 8: NULL_FORGIVING_OPERATOR
        public override void VisitPropertyDeclaration(PropertyDeclarationSyntax node)
        {
            base.VisitPropertyDeclaration(node);

            if (node.Initializer?.Value is PostfixUnaryExpressionSyntax postfix
                && postfix.IsKind(SyntaxKind.SuppressNullableWarningExpression)
                && postfix.Operand is LiteralExpressionSyntax literal
                && literal.IsKind(SyntaxKind.NullLiteralExpression))
            {
                AddIssue("NULL_FORGIVING_OPERATOR", "WARNING",
                    $"Property '{node.Identifier.Text}' uses null-forgiving operator (= null!). Consider using 'required' modifier, a default value, or making the property nullable.",
                    node);
            }
        }

        // RULE 9: UNUSED_ASSIGNED_VARIABLE
        // RULE 10: ASYNC_VOID_METHOD
        public override void VisitMethodDeclaration(MethodDeclarationSyntax node)
        {
            base.VisitMethodDeclaration(node);

            // RULE 10: ASYNC_VOID_METHOD
            if (node.Modifiers.Any(SyntaxKind.AsyncKeyword)
                && node.ReturnType is PredefinedTypeSyntax predefined
                && predefined.Keyword.IsKind(SyntaxKind.VoidKeyword))
            {
                AddIssue("ASYNC_VOID_METHOD", "ERROR",
                    $"Method '{node.Identifier.Text}' is declared as 'async void'. Use 'async Task' instead to allow proper exception handling and awaiting.",
                    node);
            }

            // RULE 9: UNUSED_ASSIGNED_VARIABLE
            if (node.Body == null)
                return;

            var localDeclarations = node.Body.DescendantNodes()
                .OfType<LocalDeclarationStatementSyntax>();

            foreach (var localDecl in localDeclarations)
            {
                foreach (var variable in localDecl.Declaration.Variables)
                {
                    if (variable.Initializer == null)
                        continue;

                    var varName = variable.Identifier.Text;

                    // Skip discard variables
                    if (varName == "_")
                        continue;

                    // Count IdentifierNameSyntax references in the method body
                    // (the declaration's identifier is a SyntaxToken, not IdentifierNameSyntax,
                    // so it is naturally excluded from this search)
                    var refsOutsideDeclaration = node.Body.DescendantNodes()
                        .OfType<IdentifierNameSyntax>()
                        .Where(id => id.Identifier.Text == varName
                                     && !variable.Span.Contains(id.Span))
                        .Any();

                    if (!refsOutsideDeclaration)
                    {
                        AddIssue("UNUSED_ASSIGNED_VARIABLE", "WARNING",
                            $"Variable '{varName}' is assigned but never used.",
                            localDecl);
                    }
                }
            }
        }

        private void AddIssue(string rule, string severity, string message, SyntaxNode node)
        {
            var lineSpan = node.GetLocation().GetLineSpan();
            var line = lineSpan.StartLinePosition.Line + 1; // 1-based
            var filePath = _relativePath;

            _component.CodeIssues.Add(new CodeIssue
            {
                Rule = rule,
                Severity = severity,
                Message = message,
                Line = line,
                Location = $"{filePath}:{line}"
            });
        }
    }
}
