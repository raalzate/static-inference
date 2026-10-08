# Implementation Plan: RoslynCodeAnalyzer — .NET Code Issues Detection

**Branch**: `003-dotnet-code-issues` | **Date**: 2026-04-07 | **Spec**: [spec.md](./spec.md)

## Summary

Crear `RoslynCodeAnalyzer.cs` usando Roslyn `CSharpSyntaxWalker` para detectar 7 patrones de bugs en C#, integrado como Pass 11 en `ProjectAnalyzer.cs`. El campo `code_issues` actualmente siempre retorna `[]` en proyectos .NET porque no existe un analizador equivalente al Java `StaticCodeAnalyzer`. Este plan usa la infraestructura Roslyn ya presente (`CSharpCompilation`, `SyntaxTree`, `SemanticModel`) y sigue el mismo patrón de los 9 analyzers existentes.

## Technical Context

**Language/Version**: C# 12 / .NET 8
**Primary Dependencies**: Microsoft.CodeAnalysis.CSharp (ya en el proyecto), Microsoft.CodeAnalysis.Workspaces.MSBuild (ya en el proyecto)
**Storage**: N/A — análisis en memoria, output JSON
**Testing**: xUnit + FluentAssertions (ya en el proyecto), mismo patrón que `SecretsDetectorTests.cs`
**Target Platform**: CLI + MCP server (sin cambios en entry points)
**Project Type**: Single library — `src/main/dotnet/DotNetAnalyzer/`
**Performance Goals**: Overhead < 5s sobre análisis base para proyectos típicos de ~50 clases
**Constraints**: No nuevas dependencias NuGet — usar únicamente APIs de Roslyn ya referenciadas
**Scale/Scope**: ~7 reglas de detección, 1 clase nueva, 1 clase modificada

## Constitution Check

*Hard gate — re-checked post-design.*

| Principio | Estado | Detalle |
|-----------|--------|---------|
| I. Backward Compatibility | PASS | `code_issues: []` ya existe en el contrato; poblar la lista no rompe nada |
| II. Single Source of Truth | PASS | La lógica vive solo en `RoslynCodeAnalyzer.cs`; `ProjectAnalyzer.cs` solo orquesta |
| III. Test-First (TDD) | GATE ACTIVO | Todos los patrones deben tener tests fallando antes de la implementación |
| IV. Determinismo | PASS | `SyntaxTree` ordering es determinístico por archivo; la lista de issues se ordena por `location` |
| V. Graceful Degradation | PASS | Errores por archivo son capturados con try/catch, análisis continúa |
| VI. Minimal Footprint | PASS | Cero dependencias nuevas — Roslyn APIs ya disponibles |

## Project Structure

### Documentation (this feature)

```text
specs/003-dotnet-code-issues/
  plan.md                           # Este archivo
  spec.md                           # Feature specification
  research.md                       # Decisiones técnicas documentadas
  data-model.md                     # Entidades y campos
  quickstart.md                     # Escenarios de test
  contracts/
    code-issues-schema.md           # Schema canónico de CodeIssue
  checklists/
    requirements.md                 # Quality checklist
  tasks.md                          # (generado por /iikit-05-tasks)
```

### Source Code (cambios en repositorio)

```text
src/main/dotnet/DotNetAnalyzer/
  Analysis/
    RoslynCodeAnalyzer.cs           # NUEVO — detector de patrones (Pass 11)
    ProjectAnalyzer.cs              # MODIFICADO — añadir Pass 11 y campo _codeAnalyzer

src/test/dotnet/DotNetAnalyzer.Tests/
  Analysis/
    RoslynCodeAnalyzerTests.cs      # NUEVO — 1 test por patrón + casos negativos
  Fixtures/
    CodeIssuesSample/               # NUEVO — archivos C# con patrones detectables
      EmptyCatch.cs
      GenericException.cs
      ConsoleLogging.cs
      StringEquality.cs
      HardcodedSecret.cs
      DeprecatedThread.cs
      MissingSwitchDefault.cs
      CleanCode.cs                  # Sin patrones — para casos negativos

specs/002-dotnet-roslyn-analyzer/contracts/
  dependency-graph-schema.md        # MODIFICADO — añadir definición completa de CodeIssue
```

**Structure Decision**: Se mantiene la estructura existente de un solo proyecto. `RoslynCodeAnalyzer.cs` sigue el patrón de `SecretsDetector.cs` y `MessagingDetector.cs` (clase standalone, método público `Analyze(compilation, components)`).

## Architecture

```
ProjectAnalyzer.AnalyzeProjectAsync()
    │
    ├── Pass 1-10: existentes (sin cambios)
    │
    └── Pass 11: RoslynCodeAnalyzer.Analyze(compilation, components)
                  │
                  ├── foreach component
                  │     └── foreach SyntaxTree in component.Files
                  │           └── new BugPatternWalker(semanticModel).Visit(tree.root)
                  │                 └── OnPatternDetected → component.CodeIssues.Add(issue)
                  │
                  └── issues.Sort(by location)   // determinismo
```

### Roslyn API Usage

| Necesidad | API Roslyn |
|-----------|-----------|
| Recorrer AST | `CSharpSyntaxWalker` |
| Detectar catch vacío | `VisitCatchClause` → `node.Block.Statements.Count == 0` |
| Detectar catch genérico | `VisitCatchClause` → type name == "Exception" o "SystemException" |
| Detectar Console.Write | `VisitInvocationExpression` → `Console.Write/WriteLine` |
| Detectar == en strings tipadas como object | `VisitBinaryExpression` + `SemanticModel.GetTypeInfo()` |
| Detectar hardcoded secrets | `VisitVariableDeclarator` → nombre contiene password/secret/apikey, valor es string literal |
| Detectar Thread.Abort | `VisitInvocationExpression` → `Thread.Abort()` |
| Detectar switch sin default | `VisitSwitchStatement` → sections sin `DefaultSwitchLabel` |
| Obtener número de línea | `node.GetLocation().GetLineSpan().StartLinePosition.Line + 1` |
| Obtener ruta del archivo | `node.GetLocation().SourceTree?.FilePath` |

### Mapeo Java → C# de patrones

| Regla Java | Regla C# | Notas |
|-----------|----------|-------|
| `empty_catch_block` | `EMPTY_CATCH_BLOCK` | Mapeo directo |
| `generic_exception_catch` | `CATCH_GENERIC_EXCEPTION` | Mapeo directo |
| `system_out_println` | `CONSOLE_LOGGING` | `Console.Write*` |
| `string_comparison_operator` | `STRING_EQUALITY_OPERATOR` | En C#: `==` sobre variables de tipo `object` que son strings. Detectar via `ReferenceEquals(string, string)` |
| N/A (nuevo) | `HARDCODED_SECRET_IN_CODE` | Variable asignada con literal, nombre sugiere secreto |
| `thread_unsafe_method` | `DEPRECATED_THREAD_USAGE` | `Thread.Abort()` removido en .NET 6 |
| `missing_switch_default` | `MISSING_SWITCH_DEFAULT` | Mapeo directo |

> **Nota**: `STRING_EQUALITY_OPERATOR` en C# no equivale exactamente al Java porque `string ==` está sobrecargado. El patrón C# detecta `ReferenceEquals(a, b)` donde a o b es string, que siempre es un bug.

## Complexity Tracking

No hay violaciones constitucionales que justificar.

## Dependencies

- `Microsoft.CodeAnalysis.CSharp` — ya en `DotNetAnalyzer.csproj` (Roslyn)
- `xunit`, `FluentAssertions` — ya en `DotNetAnalyzer.Tests.csproj`
- Cero NuGet nuevos

## Implementation Order (TDD)

Orden dictado por la constitución (III — TDD obligatorio):

1. Crear fixtures C# con patrones problemáticos
2. Crear `RoslynCodeAnalyzerTests.cs` con tests fallando (RED)
3. Implementar `RoslynCodeAnalyzer.cs` hasta que todos pasen (GREEN)
4. Añadir Pass 11 en `ProjectAnalyzer.cs` con test de integración
5. Actualizar schema contract en `specs/002-dotnet-roslyn-analyzer/contracts/dependency-graph-schema.md`
6. Verificar test E2E con `test-for-demo/dotnet-ecommerce`
