# Quickstart: VB.NET Legacy Analyzer

**Feature**: 004-vb-legacy-analyzer | **Date**: 2026-05-08 (reconstruido 2026-10-07)

## Ejecutar los tests

```bash
# Suite completa sin el corpus externo (CI / cualquier SO)
dotnet test src/test/dotnet/DotNetAnalyzer.Tests --filter "Category!=Corpus"     # 144 tests

# Solo VB
dotnet test src/test/dotnet/DotNetAnalyzer.Tests --filter "Category=VbLegacy"

# Corpus de referencia (requiere test-for-demo/vb-demo-legacy/ local, no redistribuible)
dotnet test src/test/dotnet/DotNetAnalyzer.Tests --filter "Category=Corpus"      # 2 tests

# Detector de tipo de proyecto (Java)
./mvnw test -Dtest=ProjectTypeDetectorTest
```

## Analizar un proyecto VB

```bash
dotnet run --project src/main/dotnet/DotNetAnalyzer -- path/to/App.vbproj > graph.json
dotnet run --project src/main/dotnet/DotNetAnalyzer -- path/to/dir-con-vbproj  > graph.json
```

Fuera de Windows se espera en stderr:

```
[WARN] OS is not Windows; .vbproj loading will use Adhoc fallback with reduced fidelity. ...
```

y `jq .meta.source graph.json` → `"roslyn-fallback"`.

## Escenarios de verificación

| Escenario | Entrada | Esperado | Req |
|-----------|---------|----------|-----|
| Carga `.vbproj` | `Fixtures/VbLegacy/MinimalVbProject/Minimal.vbproj` | componente `MinimalVb.Greeter` | FR-001, SC-001 |
| Autodescubrimiento | directorio del fixture | mismo resultado | FR-002 |
| Autogenerados | fixture con `MainForm.Designer.vb` | ningún `files[]` lo contiene | FR-010 |
| Fallback | macOS/Linux | `meta.source = roslyn-fallback` + `[WARN]` | FR-005, FR-032, SC-006 |
| Determinismo | 2 ejecuciones | ids y aristas idénticos | FR-029, SC-008 |
| Capas corpus | `AmigosPlus.Accounting.Controller` | 0 "Controlador", mayoría `loc > 1` | SC-003, SC-004 |
| Capas corpus | `AmigosPlus.Model.Common.Adapters` | ≥ 80% `*Adapter` en "Datos" | SC-002 |

## Resultados observados al cierre

- 2026-05-08: 146/146 (incluye corpus). Accounting.Controller 22 componentes, 100% Negocio; Common.Adapters 130 componentes, 127 en Datos (98%).
- 2026-10-07: 144/144 sin corpus (corpus no presente en el checkout); `ProjectTypeDetectorTest` verde.
