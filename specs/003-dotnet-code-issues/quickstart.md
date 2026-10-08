# Quickstart: RoslynCodeAnalyzer Test Scenarios

**Feature**: 003-dotnet-code-issues | **Date**: 2026-04-07

## Cómo ejecutar los tests

```bash
cd src/test/dotnet/DotNetAnalyzer.Tests
dotnet test --filter "FullyQualifiedName~RoslynCodeAnalyzer"
```

## Escenarios de test unitarios

Cada test sigue el patrón existente de `SecretsDetectorTests.cs`:

```csharp
private static CSharpCompilation CreateCompilation(string source) { ... }

[Fact]
public void Analyze_EmptyCatchBlock_DetectsIssue()
{
    // Arrange
    var source = @"
    namespace Sample {
        public class Service {
            public void DoWork() {
                try { var x = 1; }
                catch (Exception e) { }  // vacío
            }
        }
    }";
    var compilation = CreateCompilation(source);
    var analyzer = new RoslynCodeAnalyzer();
    var components = new TypeAnalyzer().AnalyzeTypes(compilation);

    // Act
    analyzer.Analyze(compilation, components);

    // Assert
    var svc = components.First(c => c.Id.Contains("Service"));
    svc.CodeIssues.Should().HaveCount(1);
    svc.CodeIssues[0].Rule.Should().Be("EMPTY_CATCH_BLOCK");
    svc.CodeIssues[0].Severity.Should().Be("WARNING");
    svc.CodeIssues[0].Location.Should().MatchRegex(@":\d+$");
}
```

## Escenarios por regla

### EMPTY_CATCH_BLOCK

| Escenario | Input | Expected |
|-----------|-------|----------|
| Catch vacío | `catch (Exception e) { }` | 1 issue WARNING |
| Catch con comment solo | `catch (Exception e) { /* TODO */ }` | 1 issue WARNING |
| Catch con statement | `catch (Exception e) { _log.Error(e); }` | 0 issues |

### CATCH_GENERIC_EXCEPTION

| Escenario | Input | Expected |
|-----------|-------|----------|
| Catch Exception | `catch (Exception e) { ... }` | 1 issue INFO |
| Catch SystemException | `catch (SystemException e) { ... }` | 1 issue INFO |
| Catch específico | `catch (ArgumentNullException e) { ... }` | 0 issues |

### CONSOLE_LOGGING

| Escenario | Input | Expected |
|-----------|-------|----------|
| Console.WriteLine | `Console.WriteLine("debug");` | 1 issue INFO |
| Console.Write | `Console.Write("x");` | 1 issue INFO |
| ILogger usage | `_logger.LogInformation("debug");` | 0 issues |

### STRING_EQUALITY_OPERATOR

| Escenario | Input | Expected |
|-----------|-------|----------|
| ReferenceEquals string | `ReferenceEquals(s1, s2)` donde s1/s2 son strings | 1 issue WARNING |
| `==` directo sobre strings | `s1 == s2` (no issue en C# — operator sobrecargado) | 0 issues |

### HARDCODED_SECRET_IN_CODE

| Escenario | Input | Expected |
|-----------|-------|----------|
| var password = "..." | `var password = "abc123";` | 1 issue CRITICAL |
| var apiKey = "..." | `var apiKey = "sk-1234";` | 1 issue CRITICAL |
| var name = "..." | `var name = "John";` | 0 issues |

### DEPRECATED_THREAD_USAGE

| Escenario | Input | Expected |
|-----------|-------|----------|
| Thread.Abort() | `thread.Abort();` | 1 issue ERROR |
| thread.Start() | `thread.Start();` | 0 issues |

### MISSING_SWITCH_DEFAULT

| Escenario | Input | Expected |
|-----------|-------|----------|
| Switch sin default | `switch (x) { case 1: ... }` | 1 issue INFO |
| Switch con default | `switch (x) { case 1: ... default: ... }` | 0 issues |

## Escenario de integración (E2E)

```bash
# Analizar el proyecto de demo
dotnet run --project src/main/dotnet/DotNetAnalyzer -- test-for-demo/dotnet-ecommerce

# Verificar que code_issues no está vacío
cat output.json | jq '[.components[] | select(.code_issues | length > 0)] | length'
# Expected: > 0
```

## Escenario de determinismo

```bash
# Ejecutar análisis 3 veces y comparar SHA de code_issues
for i in 1 2 3; do
    dotnet run --project src/main/dotnet/DotNetAnalyzer -- test-for-demo/dotnet-ecommerce \
    | jq '[.components[].code_issues]' | sha256sum
done
# Expected: 3 líneas idénticas
```
