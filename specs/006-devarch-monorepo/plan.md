# Implementation Plan: Unificación del repositorio dev-arch

**Branch**: `006-devarch-monorepo` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/006-devarch-monorepo/spec.md`

## Summary

Importar Static Inference con historia (`git subtree add`, sin squash) en `apps/dev-arch/engine` del monorepo `maestria-ia` (worktree `/Users/raul.alzate/Documents/maestria/maestria-devarch`, rama `feature/dev-arch-monorepo`), reubicar la web en `apps/dev-arch/web`, promover `schema/`, `skills/`, `specs/`, `CONSTITUTION.md`, `PREMISE.md` y `docs/SCHEMA.md` al nivel de producto, eliminar el jar vendorizado y el `output_entrypoints.json` obsoleto, y orquestar todo con un `Makefile` + scripts bash en la raíz del producto y workspaces npm (`web`, `schema`). La web resuelve el motor construido localmente (`web/engine-dist/engine.jar`, sobrescribible con `ENGINE_JAR_PATH`). Versión única en `VERSION` (1.2.0) propagada por script. CI monorepo en un único workflow con filtros por área. Las pruebas existentes del motor se adaptan solo en **localización** de archivos (research D3).

## Technical Context

**Language/Version**: Java 17 (motor, Maven 3.9 / `mvnw`), C# .NET 8 (`dotnet-analyzer`, compilable con SDK ≥ 8), TypeScript 5 / Node ≥ 20 (web Next.js 15.3.8, paquete `schema`, pruebas de aceptación), Bash + GNU Make (orquestación), YAML GitHub Actions
**Primary Dependencies**: existentes (Spoon, Jackson, MCP SDK Java, Roslyn, Next.js, Genkit); **nuevas solo dev/CI**: `@cucumber/cucumber` 11.x, `yaml` 2.x y `picomatch` 4.x (raíz del producto, solo para step definitions de aceptación: leer el workflow y evaluar filtros de rutas), `tsx` 4.x (ya usado en `schema/`, se iza a la raíz), `dorny/paths-filter@v3` (CI)
**Storage**: archivos en git; artefactos generados en `engine/target/`, `web/.next/`, `web/engine-dist/` (ignorados)
**Testing**: JUnit 5 + Cucumber-JVM (motor, existentes), xUnit (`Category!=Corpus`), `node:test` + `tsx` (schema y web unitarias), Cucumber-JS (aceptación 006; escenarios `@slow` con `DEVARCH_E2E=true`)
**Target Platform**: macOS / Linux para desarrollo y CI (`ubuntu-latest`); release multiplataforma del motor sin cambios de matriz
**Project Type**: monorepo de producto (web + motor Java/.NET + paquete de contrato)
**Performance Goals**: análisis web del ZIP `java-standar` < 2 min (SC-002); jobs CI sin regresión frente al CI actual del motor
**Constraints**: JDK **17** vía `JAVA_HOME` (por defecto la máquina tiene 25); `DOTNET_ROOT` para .NET de Homebrew; no tocar `target/`/`src/` del repo origen durante el bucle Maven; no modificar el `.gitignore` global del monorepo; ninguna aserción ni `.feature` de 001–005 cambia; `project/dev-arch` y `project/static-inference` intactos; sin binarios versionados
**Scale/Scope**: ~700 archivos del motor (72 MB de `test-for-demo/`, 48 commits), ~60 archivos de la web, 7 historias, 29 FR

## Constitution Check

*GATE: evaluado antes de research y re-evaluado tras el diseño.*

```
CONSTITUTION GATE ACTIVE — 17 reglas extraídas (CONSTITUTION.md v1.0.0)
```

| Principio / regla | Evaluación | Estado |
|-------------------|------------|--------|
| I. CLI operativo; JSON = contrato público; paridad CLI/MCP | No cambia código de análisis ni formatos; la única modificación del motor es leer `SERVER_VERSION` de un recurso filtrado (mismo valor 1.2.0) | PASS |
| II. Lógica en el núcleo; adaptadores delgados | La web sigue invocando el CLI sin duplicar lógica; el cliente MCP es 007 | PASS |
| III. TDD obligatorio; no modificar aserciones | Fase testify antes de tasks; cada historia arranca con escenarios/pruebas en rojo. Las pruebas existentes solo cambian la **localización** de archivos (research D3), con aserciones intactas | PASS (con vigilancia — ver Complexity Tracking) |
| III. Pruebas existentes siguen pasando | SC-005: 100 % de las pruebas previas (sin corpus) en verde en el producto | PASS |
| IV. Determinismo | Sin cambios en la salida | PASS |
| V. Errores con contexto | `check-prereqs.sh` nombra prerrequisito y versión; la web devuelve `ENGINE_NOT_BUILT` con comando a ejecutar | PASS |
| VI. Artefacto único (fat JAR), sin infraestructura de runtime, dependencias justificadas | El motor sigue siendo un fat JAR + analizador .NET; la web es un artefacto separado que lo invoca (alcance de la Constitución: el motor). Dependencias nuevas solo dev/CI, justificadas en research D5, D8, D9 | PASS (justificado) |
| Quality: análisis estático resuelto antes de merge | SpotBugs, Checkstyle (ratchet 4763), `dotnet format` se conservan con igual severidad (FR-025) | PASS |
| Workflow: rama de feature + PR, conventional commits | Rama `feature/dev-arch-monorepo` del monorepo; commits convencionales | PASS |
| Governance: no enmendar la Constitución | Se **traslada** sin cambios (FR-005); la ampliación de alcance es la enmienda 2.0.0 de la Fase 7 | PASS |

**Post-design re-check**: PASS — ningún artefacto de diseño altera la salida del motor, duplica lógica ni añade dependencias de runtime. Riesgo vigilado: la adaptación de localización en pruebas (Complexity Tracking).

## Architecture

```text
 maestria-ia (monorepo)
 ├── .github/workflows/dev-arch.yml ──── filtros por área ────────────────────────────┐
 └── apps/dev-arch  (producto, VERSION=1.2.0, Makefile, package.json workspaces)      │
      │                                                                              │
      │  make build / test / dev / schema-check / clean / version-sync               │
      ▼                                                                              ▼
  ┌──────────────────┐   mvn package    ┌──────────────────────┐   dotnet publish  ┌──────────────────┐
  │ Make + scripts   │─────────────────▶│ Java engine (CLI/MCP)│◀── locator ──────│ dotnet-analyzer  │
  │ (orquestación)   │                  │ engine/target/*.jar  │                   │ framework-dep.   │
  └───────┬──────────┘                  └──────────┬───────────┘                   └────────┬─────────┘
          │ stage                                   │ copia                                  │ copia
          ▼                                         ▼                                        ▼
  ┌──────────────────┐   execFile java -jar   ┌──────────────────────────────────────────────────────┐
  │ Next.js web      │──────────────────────▶ │ web/engine-dist/ (engine.jar + dotnet-analyzer)      │
  │ /api/analyze     │                        └──────────────────────────────────────────────────────┘
  └───────┬──────────┘
          │ workspace dep (tipos, 007)
          ▼
  ┌──────────────────┐   valida (test)   ┌──────────────────┐
  │ schema/1.0.0     │◀──────────────────│ Contract tests   │  (Java, .NET, TS; make schema-check)
  └──────────────────┘                   └──────────────────┘
```

## Design Notes

1. **Secuencia de migración** (en el worktree del monorepo; este repo solo se lee):
   1. Mergear la rama `006-devarch-monorepo` del motor a su `main` (specs 006 viajan con la importación).
   2. `git subtree add --prefix apps/dev-arch/engine /Users/raul.alzate/Documents/maestria/maestria/project/static-inference main` (sin `--squash`).
   3. Commit de reorganización con `git mv` (research D2) y movimiento de la web a `web/` (research E1); `git rm` del jar y de `output_entrypoints.json`.
   4. Configuración unificada (research D10), workspaces (D5), `VERSION` (D6), `Makefile` + scripts (D4), cambio de la web (D7), adaptación de localización en pruebas (D3), CI (D8).
2. **Rutas del producto** (fuente única para scripts y pruebas): `PRODUCT_ROOT=apps/dev-arch`, `ENGINE_DIR=$PRODUCT_ROOT/engine`, `SCHEMA_DIR=$PRODUCT_ROOT/schema`, `WEB_DIR=$PRODUCT_ROOT/web`, `ENGINE_DIST=$WEB_DIR/engine-dist`. Ver [data-model.md](./data-model.md).
3. **Prerrequisitos** (`scripts/check-prereqs.sh`): Java = `$JAVA_HOME/bin/java` si `JAVA_HOME` está definido, si no `java` del `PATH`; exige major **17** (mensaje: `JDK 17 required (found 25): set JAVA_HOME to a JDK 17`); .NET: `dotnet --list-sdks` con alguna versión ≥ 8 **y** `dotnet --list-runtimes` con `Microsoft.NETCore.App 8.`; Node ≥ 20 y npm. Salida `[prereqs] <tool>: OK|FAIL — detalle`, exit ≠ 0 al primer FAIL antes de compilar. `build`, `test`, `dev` dependen de él; `schema-check` conserva su propia detección (NOT VERIFIED) de 005.
4. **Pruebas del motor tras la promoción de `schema/`** (research D3): `ProductLayout` + propiedades surefire; `RunSchemaContractFeaturesTest` lee `features.dir`; `CiWorkflowSteps` lee `ProductLayout.ciWorkflow()`; `GovernanceSteps`/`GeneratedTypesSteps`/`SchemaCheckSteps` resuelven `docs/`, `specs/`, `schema/` con `productRoot()` y `pom.xml`, `target/`, `src/test/dotnet`, cwd de `mvn`/`dotnet` con `engineRoot()`; instalación npm en `productRoot()` con `npm ci`. En .NET `ContractSchemas.EngineRoot`. **Ningún `assert*`, `.feature` ni `context.json` cambia** (verificado en analyze y en implement con `git diff` filtrado).
5. **`make test`** = `check-prereqs` → `version-check` → `test-engine` (`mvn -B verify` en `engine/` con `JAVA_HOME`; `dotnet test ... --filter "Category!=Corpus"` previo `engine-stage-analyzer` para `target/dotnet-analyzer`) → `schema-check` → `test-web` (`npm test -w web`) → `test-acceptance` (Cucumber-JS sin `@slow` salvo `DEVARCH_E2E=true`). `e2e` y `doctor` imprimen `[e2e] not implemented yet (feature 011)` / `[doctor] not implemented yet (feature 012)` y salen con 0.
6. **`make dev`** = `check-prereqs` → `build-engine` si `web/engine-dist/engine.jar` falta o es más antiguo que `engine/target/java-dependency-extractor.jar` → exporta `JAVA_HOME`, `DOTNET_ROOT` → `npm run dev -w web` (puerto 9002). `npm run dev` directo en `web/` también funciona tras `make build` (criterio de salida).
7. **Flujo de análisis de la web**: solo cambia la resolución del motor y la invocación (`execFile`); la lectura de `output*.json` desde `cwd` y la falta de aislamiento entre análisis se mantienen (deuda de 007, spec Edge Cases).
8. **Versión**: `scripts/version.sh sync|check` (contrato en [contracts/version.md](./contracts/version.md)); `version-check` forma parte de `make test` y del job CI `engine-java`.
9. **CI** (contrato en [contracts/ci-workflow.md](./contracts/ci-workflow.md)): `dev-arch.yml` en la raíz del monorepo; `defaults.run.working-directory: apps/dev-arch`; el job `schema` mantiene literal `make schema-check` con JDK 17/.NET 8/Node 20 y sin tolerancia (TS-039 de 005).
10. **Tablero iikit**: tras mover `specs/`, `.specify/` y `.tessl/` a la raíz del producto, `generate-dashboard-safe.sh` se ejecuta desde `apps/dev-arch`.

## Project Structure

### Documentation (this feature)

```text
specs/006-devarch-monorepo/
  plan.md              # Este archivo
  research.md          # Evidencia y decisiones D1–D10
  data-model.md        # Layout, versión, objetivos Make, resolución del motor
  quickstart.md        # Escenarios de validación
  contracts/
    make-targets.md    # Interfaz de los comandos del producto
    engine-resolution.md  # Contrato web ↔ motor local (env, errores)
    ci-workflow.md     # Jobs, disparadores y filtros
    version.md         # Fuente única y propagación
  checklists/          # requirements.md (+ /iikit-03-checklist)
  tests/features/      # /iikit-04-testify
  tasks.md             # /iikit-05-tasks
```

### Source Code (monorepo `maestria-ia`)

```text
.github/workflows/dev-arch.yml          # NUEVO: CI del producto con filtros por área
apps/dev-arch/
  VERSION                               # NUEVO: 1.2.0
  Makefile                              # NUEVO: build, test, test-engine, test-web, test-acceptance, e2e, schema-check, dev, clean, doctor, version-sync, version-check
  package.json  package-lock.json       # NUEVO: workspaces [web, schema]; devDeps @cucumber/cucumber, tsx
  cucumber.mjs                          # NUEVO: features specs/006-*/tests/features, steps tests/acceptance
  .gitignore  .editorconfig  .mcp.json  # NUEVO/unificados
  AGENTS.md  CLAUDE.md  tessl.json  .tessl/RULES.md  .specify/context.json   # desde el motor
  CONSTITUTION.md  PREMISE.md  CHANGELOG.md                                 # desde el motor (sin cambios)
  specs/001..006-*/                     # desde el motor
  scripts/
    check-prereqs.sh  engine-build.sh  engine-stage.sh  dev.sh  version.sh   # NUEVOS
    schema-check.sh                     # desde engine/scripts, con ENGINE_DIR/SCHEMA_DIR
  tests/acceptance/
    steps/*.steps.ts  support/{world,layout,process}.ts                     # NUEVOS
  schema/                               # desde engine/schema (sin package-lock propio)
  skills/                               # desde engine/skills
  docs/SCHEMA.md                        # desde engine/docs
  docker/README.md                      # NUEVO: marcador (Dockerfile en feature 012)
  engine/                               # subtree de static-inference (historia completa)
    pom.xml  mvnw  .mvn/  spotbugs-exclude.xml  README.md  ALGORITHMS.md  CONFIGURATION.md  MCP.md
    src/main/java/com/extractor/mcp/McpServerMain.java          # versión desde recurso filtrado
    src/main/resources/static-inference-version.properties     # NUEVO (filtrado)
    src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj        # + <Version>
    src/test/java/com/extractor/testsupport/ProductLayout.java  # NUEVO
    src/test/java/com/extractor/{schema,bdd}/*                  # solo localización de rutas
    src/test/dotnet/DotNetAnalyzer.Tests/SchemaContract/*       # EngineRoot
    test-for-demo/
  web/                                  # desde apps/dev-arch (src, docs, configs)
    package.json                        # version = VERSION; dep @static-inference/schema; script test
    src/server/engine/engine-path.ts    # NUEVO: resolveEngine + buildEngineCommand (puras)
    src/server/engine/engine-path.test.ts  src/app/api/analyze/route.test.ts   # NUEVOS
    src/app/api/analyze/route.ts        # usa engine-path + execFile
    engine-dist/                        # generado (ignorado)
```

**Structure Decision**: Monorepo de producto según D1 del plan de integración; el motor conserva su layout Maven intacto bajo `engine/` para no romper historia ni herramientas, y todo lo transversal (contrato, gobernanza, orquestación, CI) sube al producto.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Cambios en ~9 archivos de prueba existentes (solo localización de rutas) | Promover `schema/`, `specs/`, `docs/` al producto cambia la raíz que las pruebas asumían | No promover `schema/` (workspace `engine/schema`) evita el cambio pero contradice D1 del plan; queda como reserva si el usuario lo prefiere (research D3) |
| Runner BDD Node (`@cucumber/cucumber`) además de Cucumber-JVM | Las verificaciones de 006 son de producto (Make, npm, git, HTTP de la web) | Meterlas en la suite Maven acopla el motor a la web y al monorepo |
| Acción de terceros `dorny/paths-filter` | Filtros por área dentro de un único workflow con `needs:` | Workflows separados con `on.paths` no pueden encadenar release tras las pruebas |
