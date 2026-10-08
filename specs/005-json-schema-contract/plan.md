# Implementation Plan: Contrato de datos único y versionado

**Branch**: `005-json-schema-contract` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/005-json-schema-contract/spec.md`

## Summary

Formalizar el contrato de salida de Static Inference como JSON Schema 2020-12 en un paquete `schema/` versionado (1.0.0), describiendo el output real v1.1.0 de Java y .NET (research E1–E11). Añadir `meta.schema_version` a los tres artefactos desde el modelo de dominio (sin tocar adaptadores CLI/MCP), validar las salidas en tests de contrato Java (networknt, scope test) y .NET (JsonSchema.Net, proyecto de tests), extender `CliMcpParityTest`, generar tipos TS + validadores zod en `schema/generated/`, documentar la política en `docs/SCHEMA.md` y unificar todo en `make schema-check` (bloqueante en CI).

## Technical Context

**Language/Version**: Java 17 (motor), C# / .NET 8 (dotnet-analyzer), TypeScript 5 sobre Node 20 (solo herramientas de `schema/`), Bash (script `schema-check`)
**Primary Dependencies**: Jackson 2.x (existente); **nuevas, solo test/dev**: `com.networknt:json-schema-validator` 1.5.x (Maven, scope `test`), `JsonSchema.Net` 7.x (NuGet, solo `DotNetAnalyzer.Tests`), `json-schema-to-typescript` 15.x, `json-schema-to-zod` 2.x, `zod` 3.x, `tsx` 4.x (npm, `schema/` privado)
**Storage**: archivos (esquemas JSON, fixtures y código generado versionados en git)
**Testing**: JUnit 5 (`@Tag("schema-contract")`), Cucumber JVM 7.x (`cucumber-java`, `cucumber-junit-platform-engine`, scope `test`) como runner BDD de los `.feature` (research D11), xUnit (`[Trait("Category","SchemaContract")]`), `node:test` vía `tsx`
**Target Platform**: macOS/Linux para desarrollo y CI (ubuntu-latest); el artefacto desplegable no cambia
**Project Type**: single (motor Java con sub-proyecto .NET) + paquete auxiliar `schema/`
**Performance Goals**: `make schema-check` < 5 min en CI (dominado por `mvn test` acotado al tag y `dotnet test` filtrado)
**Constraints**: sin dependencias de runtime nuevas en el fat JAR ni en `dotnet-analyzer` (principio VI); salidas byte-idénticas entre ejecuciones (principio IV); ningún campo existente cambia de nombre, tipo, valor u orden (principio I)
**Scale/Scope**: 3 documentos raíz + 5 respuestas MCP, ~22 `$defs`; fixtures de 2 proyectos demo (java-standar, dotnet-ecommerce) más los demás de `test-for-demo/` en los tests Java

## Constitution Check

*GATE: evaluado antes de research y re-evaluado tras el diseño.*

```
CONSTITUTION GATE ACTIVE — 17 reglas extraídas (CONSTITUTION.md v1.0.0)
```

| Principio / regla | Evaluación | Estado |
|-------------------|------------|--------|
| I. CLI existente operativo; JSON = contrato público; cambios con versionado explícito | Solo se añade `meta.schema_version` (aditivo) + `meta` en arquitectura/entrypoints; contrato versionado 1.0.0; nada se renombra ni elimina (FR-011) | PASS |
| I. Paridad CLI vs MCP | Campo añadido en el modelo (`DependencyGraph.Meta`, `ConsolidatedArchitecture`, `ApiContracts`) que ambos adaptadores serializan; `CliMcpParityTest` extendido | PASS |
| II. Lógica en el núcleo, adaptadores delgados | Sin cambios en `MicroserviceInferenceMain` ni tools MCP salvo lo que ya serializan | PASS |
| III. TDD obligatorio; tests antes del código | Fase testify antes de tasks; tasks ordenadas test-first (cada test de contrato falla antes de añadir `schema_version`) | PASS |
| III. No modificar aserciones para pasar | Regla trasladada a tasks | PASS |
| IV. Determinismo; respuestas MCP válidas contra esquemas documentados | `schema_version` es constante; esta feature *materializa* "matching documented schemas" | PASS |
| V. Errores con contexto | `schema-check` reporta frente + ruta del campo; puente .NET emite warning si las versiones difieren | PASS |
| VI. Dependencias mínimas y justificadas; fat JAR único; sin infraestructura de runtime | Todas las dependencias nuevas son test/dev (research D2–D4). Node solo para el paquete `schema/`, no para ejecutar el motor | PASS (justificado en Complexity Tracking) |
| Quality: contract tests de handlers MCP | Nuevos tests de contrato de las 5 respuestas | PASS |
| Quality: static analysis resuelto antes de merge | Checkstyle/SpotBugs/dotnet format siguen bloqueantes | PASS |
| Workflow: breaking change ⇒ bump + migración | Política en `docs/SCHEMA.md` | PASS |
| Workflow: conventional commits | Aplica en implement | PASS |

**Post-design re-check**: PASS — ningún artefacto de diseño introduce dependencias de runtime, cambios de comportamiento ni lógica duplicada.

## Architecture

```text
                 ┌──────────────────────────────┐
                 │ schema/1.0.0/*.schema.json   │  fuente de verdad (+ schema/VERSION)
                 └──────┬──────────┬────────────┘
         valida (test)  │          │ genera (dev)          valida (test)
  ┌─────────────────────▼──┐   ┌───▼─────────────────┐   ┌──────────────────────────┐
  │ Java contract tests    │   │ schema/gen (Node)   │   │ .NET contract tests      │
  │ networknt validator    │   │ types.ts+validators │   │ JsonSchema.Net           │
  └─────────▲──────────────┘   └───▲─────────────────┘   └──────────▲───────────────┘
            │ output*.json / MCP   │ fixtures + samples             │ graph JSON (stdout)
  ┌─────────┴──────────────┐       │                      ┌─────────┴───────────────┐
  │ Java engine (CLI/MCP)  │───────┘                      │ dotnet-analyzer         │
  └────────────────────────┘                              └─────────────────────────┘
                       ▲  orquesta los tres frentes + versión
                 ┌─────┴──────────────────────┐
                 │ make schema-check (script) │ ◀── CI job
                 └────────────────────────────┘
```

## Design Notes

1. **Versión**: `com.extractor.model.SchemaVersion.CURRENT = "1.0.0"`; `DotNetAnalyzer.Model.SchemaVersion.Current = "1.0.0"`; `schema/VERSION`. `DependencyGraph.Meta` gana `@JsonProperty("schema_version")` declarado después de `source` (`@JsonPropertyOrder` si hace falta para fijar el orden). Nueva clase `DocumentMeta { schema_version }` usada por `ConsolidatedArchitecture.meta` (primer campo) y `DependencyGraph.ApiContracts.meta` (primer campo). En .NET, `Meta.SchemaVersion` con `[JsonPropertyName("schema_version")]` y `JsonPropertyOrder` tras `source`.
2. **Puente .NET**: `DotNetBridgeAnalyzer` lee `meta.schema_version` del JSON .NET; si falta o difiere de `SchemaVersion.CURRENT`, añade warning `"dotnet-analyzer schema_version X != engine Y"` a `AnalysisResult.warnings` y fija la constante Java.
3. **`get_api_contracts` y entrypoints**: ambos serializan `ApiContracts` con `meta`; en el grafo, `api_contracts.meta` también aparece (Java) y es opcional en el esquema (.NET crudo no lo emite).
4. **Carga de esquemas en tests**: `SchemaRegistry` (test util Java) y `SchemaRegistry` (.NET tests) mapean `https://static-inference.dev/schema/1.0.0/` → `<repo>/schema/1.0.0/`. Java localiza la raíz vía `${project.basedir}` (system property de surefire `schema.dir`); .NET vía búsqueda ascendente de `schema/VERSION`.
5. **Muestras para TS**: los tests de contrato Java/.NET escriben cada documento validado en `target/schema-samples/<frente>/<proyecto>/<archivo>.json`, que `npm run check` valida con zod (cadena productor→consumidor).
6. **Generación determinista**: `generate.mjs` ordena `$defs` alfabéticamente, fija `bannerComment` sin fecha, formatea con el printer interno y termina con `\n`.
7. **Documentación**: `docs/SCHEMA.md` (política, catálogo de cambios, formato de nota de migración, cómo regenerar). Se actualizan `skills/static-inference-expert-{claude,kiro}/references/json-schema.md`, `specs/00{1,2,3}-*/contracts/*.md` (locales, gitignored), `MCP.md`, `README.md` y `CHANGELOG.md` (`[Unreleased]` → Añadido `meta.schema_version`).

## Project Structure

### Documentation (this feature)

```text
specs/005-json-schema-contract/
  plan.md  research.md  data-model.md  quickstart.md
  contracts/schema-package.md  contracts/schema-check.md
  checklists/requirements.md
  tests/features/*.feature      # /iikit-04-testify
  tasks.md                      # /iikit-05-tasks
```

### Source Code (repository root)

```text
schema/                                   # NUEVO (ver contracts/schema-package.md)
  VERSION  1.0.0/  gen/  generated/  fixtures/  test/  package.json  README.md
scripts/schema-check.sh                   # NUEVO
Makefile                                  # NUEVO (target schema-check)
docs/SCHEMA.md                            # NUEVO
pom.xml                                   # + networknt (test), surefire systemPropertyVariables schema.dir
src/main/java/com/extractor/
  model/SchemaVersion.java                # NUEVO
  model/DocumentMeta.java                 # NUEVO
  model/DependencyGraph.java              # Meta.schema_version, ApiContracts.meta
  inference/ConsolidatedArchitecture.java # meta
  analyzer/DotNetBridgeAnalyzer.java      # propagación/aviso de versión
src/test/java/com/extractor/
  schema/SchemaRegistry.java              # NUEVO test util
  schema/SchemaDocumentsContractTest.java # NUEVO artefactos Java (demos Java)
  schema/SchemaNegativeContractTest.java  # NUEVO mutaciones rechazadas
  schema/McpResponsesContractTest.java    # NUEVO 5 tools
  schema/SchemaVersionContractTest.java   # NUEVO versión en 3 artefactos + determinismo
  integration/CliMcpParityTest.java       # EXTENDIDO con validación
  analyzer/DotNetBridgeAnalyzerTest.java  # EXTENDIDO versión propagada / warning
  bdd/RunSchemaContractFeaturesTest.java  # NUEVO runner Cucumber (@Suite, features en specs/005-*/tests/features)
  bdd/*Steps.java                         # NUEVO step definitions (documentos, versión, MCP, tipos, schema-check, gobernanza)
src/main/dotnet/DotNetAnalyzer/Model/
  SchemaVersion.cs                        # NUEVO
  DependencyGraph.cs                      # Meta.SchemaVersion
src/test/dotnet/DotNetAnalyzer.Tests/
  DotNetAnalyzer.Tests.csproj             # + JsonSchema.Net
  SchemaContract/SchemaRegistry.cs        # NUEVO
  SchemaContract/GraphSchemaContractTests.cs  # NUEVO (SampleProject, CodeIssuesSample, VbLegacy fixture)
  Model/MetaDeterminismTests.cs           # EXTENDIDO schema_version
.github/workflows/ci.yml                  # + job schema-check
.gitignore                                # + excepción !specs/005-json-schema-contract/ (research D11)
skills/static-inference-expert-{claude,kiro}/references/json-schema.md   # alineados
```

**Structure Decision**: se mantiene la estructura single-project actual; `schema/` es un paquete hermano autocontenido que en la Fase 2 se moverá tal cual a `apps/dev-arch/schema/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Toolchain Node en el repo (principio VI: dependencias mínimas) | FR-017 exige tipos y validadores generados para el consumidor web; la generación TS/zod solo existe en el ecosistema Node | Tipos a mano (prohibido por FR-017/SC-005); generar desde Java (no hay generador zod mantenido) |
| Cucumber JVM (2 dependencias de test) | Regla phase-discipline: `.feature` requieren step definitions y runner BDD | Pruebas nativas con IDs TS solamente (incumple la regla) |
| Validador JSON Schema en Java y en .NET (2 dependencias de test) | FR-012/FR-013 exigen que cada productor se valide en su propia suite | Validar solo desde Node (no prueba el JSON .NET crudo ni corre en `mvn verify`) |
