---
description: "Task list for 006-devarch-monorepo"
---

# Tasks: Unificación del repositorio dev-arch

**Input**: Design documents from `specs/006-devarch-monorepo/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md, tests/features/ (42 escenarios TS-001..TS-042, hash LOCKED)

**Tests**: TDD obligatorio (CONSTITUTION.md principio III). Cada tarea de step definitions / pruebas DEBE ejecutarse y fallar antes de su tarea de implementación. Prohibido modificar `.feature`, aserciones existentes o `context.json` para pasar.

**Dónde se trabaja**: worktree del monorepo `/Users/raul.alzate/Documents/maestria/maestria-devarch` (rama `feature/dev-arch-monorepo`). Las rutas son relativas a la raíz del monorepo; `P/` = `apps/dev-arch/`. El repo de Static Inference solo se lee (origen del subtree); no se ejecuta nada en su `target/`.

**Regla de adaptación de pruebas existentes (research D3)**: en archivos de prueba del motor solo se permite cambiar **cómo se localizan** archivos y directorios (raíz, propiedades, cwd de procesos, comando de instalación npm). Ningún `assert*`, `.feature`, `Examples`, lista de valores esperados ni `context.json` cambia. T014 verifica esto con un diff filtrado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizable (archivos distintos, sin dependencias pendientes)
- **[USn]**: historia de usuario de spec.md
- Trazabilidad: listas explícitas `[TS-001, TS-002]`

---

## Phase 1: Setup (importación y estructura)

**Purpose**: traer el motor con historia, reubicar la web y crear el esqueleto del producto

- [ ] T001 Integrar la rama `006-devarch-monorepo` en `main` del repo Static Inference (merge local, sin push) para que `specs/006-*` viaje con la importación; confirmar con el usuario antes de ejecutarlo
- [ ] T002 En el worktree del monorepo: `git subtree add --prefix apps/dev-arch/engine /Users/raul.alzate/Documents/maestria/maestria/project/static-inference main` (sin `--squash`) [TS-002]
- [ ] T003 Reorganizar con `git mv` (research D2): `P/engine/{specs,skills,schema}` → `P/{specs,skills,schema}`; `P/engine/{CONSTITUTION.md,PREMISE.md,CHANGELOG.md}` → `P/`; `P/engine/docs/SCHEMA.md` → `P/docs/SCHEMA.md`; `P/engine/scripts/schema-check.sh` → `P/scripts/schema-check.sh`; `git rm` de `P/engine/Makefile` y `P/engine/.github/`
- [ ] T004 Reubicar la web con `git mv`: `P/{src,docs,components.json,next.config.ts,package.json,postcss.config.mjs,tailwind.config.ts,tsconfig.json,README.md}` → `P/web/`; `git rm P/java-dependency-extractor.jar P/output_entrypoints.json` [TS-003, TS-042]
- [ ] T005 Crear `P/.gitignore` único fusionando `P/engine/.gitignore` y la `.gitignore` de la web, añadiendo `web/engine-dist/`, `.tessl/tiles/`, `.specify/dashboard.html`, `.specify/score-history.json`, `.specify/active-feature`, `!package-lock.json` y las excepciones de 005 con rutas nuevas (`!schema/fixtures/**/output*.json`, `!engine/src/test/resources/baseline-v1.1.0/**/output*.json`); `git rm` de `P/engine/.gitignore` y `P/web/.gitignore` (research D10)
- [ ] T006 Copiar a `P/` los archivos de gobernanza no versionados del motor: `AGENTS.md`, `CLAUDE.md`, `tessl.json`, `.tessl/RULES.md`, `.specify/context.json` (research E6, D10)
- [ ] T007 Crear `P/package.json` (`dev-arch`, privado, `workspaces: ["web","schema"]`, `engines.node >=20`, devDeps `@cucumber/cucumber`, `tsx`, `yaml`, `picomatch`, script `test:acceptance`), `P/cucumber.mjs` (features `specs/006-devarch-monorepo/tests/features/**/*.feature`, import `tests/acceptance/**/*.ts`, `tags: DEVARCH_E2E=true ? '' : 'not @slow'`); `git rm P/schema/package-lock.json`; `npm install` en `P/` → `P/package-lock.json` (research D5, D9)
- [ ] T008 Crear `P/docker/README.md` (marcador: Dockerfile y compose en feature 012) y verificar que `P/docs/` y `P/skills/` existen (FR-001, FR-006)

**Checkpoint**: estructura importada; las pruebas del motor que asumen la raíz antigua están en rojo (esperado).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: soporte de pruebas y localización de rutas del motor; bloquea todas las historias

- [ ] T009 [P] Crear soporte de aceptación en `P/tests/acceptance/support/`: `layout.ts` (monorepoRoot, productRoot, engineRoot, webDir, engineDist), `process.ts` (ejecución con env, captura de exit/stdout, timeout, `JAVA_HOME`), `git.ts` (clon limpio en directorio temporal, `ls-files`, `rev-list`), `gates.ts` (`@slow` solo con `DEVARCH_E2E=true`; guarda anti-recursión `DEVARCH_TEST_RUNNING=1`), `world.ts`
- [ ] T010 [P] Escribir primero `P/engine/src/test/java/com/extractor/testsupport/ProductLayoutTest.java` (propiedades explícitas > búsqueda ascendente > error que nombra la propiedad; `find()` producto→motor; `ciWorkflow()` monorepo→producto) y verla fallar
- [ ] T011 Implementar `P/engine/src/test/java/com/extractor/testsupport/ProductLayout.java` hasta pasar T010 (research D3)
- [ ] T012 Adaptar **solo localización** en `P/engine/src/test/java/com/extractor/schema/{SchemaRegistry,DemoOutputs}.java` y `P/engine/src/test/java/com/extractor/bdd/{BaselineArtifacts,GovernanceSteps,GeneratedTypesSteps,SchemaCheckSteps,DotNetRawSteps,CiWorkflowSteps,RunSchemaContractFeaturesTest}.java` para usar `ProductLayout` (productRoot para `schema/`, `specs/`, `docs/`, `skills/`, `MCP.md` vía `find()`; engineRoot para `pom.xml`, `target/`, `test-for-demo/`, `src/test/dotnet`, cwd de `mvn`/`dotnet`; `ciWorkflow()` para el workflow; `npm ci` en productRoot); en `P/engine/pom.xml` surefire: `engine.root`, `product.root`, `schema.dir=${project.basedir}/../schema`, `features.dir=${project.basedir}/../specs/005-json-schema-contract/tests/features`, `devarch.ci.workflow=${project.basedir}/../../../.github/workflows/dev-arch.yml`
- [ ] T013 [P] Añadir `EngineRoot` en `P/engine/src/test/dotnet/DotNetAnalyzer.Tests/SchemaContract/SchemaRegistry.cs` y usarlo en `GraphJson.cs` y `GraphSchemaContractTests.cs` (solo localización)
- [ ] T014 Verificar la regla de adaptación: `git diff <commit de T003>.. -- P/engine/src/test` filtrado no muestra líneas `assert`/`Assert.`/`@Then`/`@Given`/`@When` cambiadas ni cambios en `P/specs/00[1-5]-*/tests/` ni `P/specs/*/context.json`; `testify-tdd.sh verify-hash` OK para 001–005 [TS-024]
- [ ] T015 Mover la lógica de `P/scripts/schema-check.sh` a `PRODUCT_ROOT`/`ENGINE_DIR`/`SCHEMA_DIR` (mvn y dotnet en `engine/`, `npm ci` en `P/`, `target/` del motor), conservando literal el formato de salida de 005
- [ ] T016 Añadir a `P/web/package.json` el script `"test": "node --import tsx --test \"src/**/*.test.ts\""` y crear `P/scripts/lib.sh` (rutas del producto, `java_bin` con `JAVA_HOME`, `mvn_cmd` con `mvnw`, `dotnet_root`, formato `[<etapa>] <x>: OK|FAIL — detalle`)
- [ ] T017 Línea base: con `JAVA_HOME` = JDK 17, `mvn -B verify` en `P/engine` y `dotnet test ... --filter "Category!=Corpus"` en verde; `SCHEMA_CHECK_SKIP_BUILD=0 bash P/scripts/schema-check.sh` → `RESULT: OK`

**Checkpoint**: el motor pasa todas sus pruebas en su nueva ubicación; soporte de aceptación listo.

---

## Phase 3: User Story 1 — Un solo producto con la historia del motor (P1) 🎯 MVP

**Goal**: estructura del producto verificable, historia preservada, sin binarios

**Independent Test**: `npx cucumber-js --tags @US-001` en `P/`

- [ ] T018 [US1] Escribir `P/tests/acceptance/steps/repository-structure.steps.ts` [TS-001, TS-002, TS-003, TS-004, TS-005, TS-042] y ejecutarlos en rojo contra el commit `a32d5f5` (antes de T002–T008)
- [ ] T019 [US1] Completar lo que falte de Phase 1 hasta que pasen [TS-001, TS-002, TS-003, TS-004, TS-005, TS-042] (lista de entradas de raíz permitidas en `layout.ts`, según FR-001 y plan *Project Structure*)

**Checkpoint**: US1 verde.

---

## Phase 4: User Story 2 — Construcción y pruebas desde clon limpio (P1)

**Goal**: `make build && make test` verdes desde clon limpio con JDK 17 vía `JAVA_HOME`

**Independent Test**: `DEVARCH_E2E=true npx cucumber-js --tags @US-002`

- [ ] T020 [US2] Escribir `P/tests/acceptance/steps/build-and-test.steps.ts` [TS-006, TS-007, TS-008, TS-009, TS-010, TS-011, TS-012, TS-013] (prerrequisitos simulados con `PATH`/`JAVA_HOME` temporales y binarios falsos) y verlos fallar
- [ ] T021 [US2] Implementar `P/scripts/check-prereqs.sh` según contracts/make-targets.md (JDK major == 17; .NET SDK ≥ 8 + runtime 8; Node ≥ 20; exit 2 al primer FAIL) [TS-008, TS-009]
- [ ] T022 [US2] Implementar `P/scripts/engine-build.sh` (`mvn -B -DskipTests package` en `engine/` con `JAVA_HOME`; `dotnet publish` framework-dependent a `engine/target/dotnet-analyzer-publish/` + lanzador `engine/target/dotnet-analyzer` con `DOTNET_ROOT`, como 005) y `P/scripts/engine-stage.sh` (copia a `web/engine-dist/engine.jar` y `web/engine-dist/dotnet-analyzer*`) [TS-006]
- [ ] T023 [US2] Crear `P/Makefile` con `build`, `build-engine`, `test`, `test-engine`, `test-web`, `test-acceptance`, `schema-check`, `clean`, `e2e`, `doctor` (marcadores con los mensajes del contrato), `prereqs` [TS-006, TS-007, TS-010, TS-011, TS-012]
- [ ] T024 [US2] Verificar workspace y lockfile único (sin locks en `web/` ni `schema/`) [TS-013]
- [ ] T025 [US2] Ejecutar `DEVARCH_E2E=true` [TS-006, TS-007, TS-011] en clon limpio y corregir hasta verde; `git status --porcelain` vacío tras build+test (SC-003)

**Checkpoint**: criterio de salida 1/2 cumplido.

---

## Phase 5: User Story 3 — La web analiza un ZIP con el motor local (P1)

**Goal**: `npm run dev` en `web/` analiza un ZIP con `web/engine-dist/engine.jar`

**Independent Test**: `DEVARCH_E2E=true npx cucumber-js --tags @US-003`

- [ ] T026 [P] [US3] Escribir `P/web/src/server/engine/engine-path.test.ts` (`node:test`) para `resolveEngine` y `buildEngineCommand` según contracts/engine-resolution.md [TS-015, TS-016, TS-017, TS-018] y verla fallar
- [ ] T027 [P] [US3] Escribir `P/tests/acceptance/steps/web-local-engine.steps.ts` (levanta `next dev` en puerto libre, genera ZIP de `engine/test-for-demo/<proyecto>` en temporal, `POST /api/analyze`) [TS-014, TS-015, TS-016, TS-017, TS-018, TS-019] y verlos fallar
- [ ] T028 [US3] Implementar `P/web/src/server/engine/engine-path.ts` (`resolveEngine`, `buildEngineCommand`, mensaje `Engine not built ... make build`) [TS-015, TS-016, TS-017, TS-018]
- [ ] T029 [US3] Modificar `P/web/src/app/api/analyze/route.ts`: usar `resolveEngine(process.env, process.cwd(), existsSync)` y `execFile(file, args)`; devolver 500 con el mensaje `not-built`; sin otros cambios de flujo [TS-014, TS-015, TS-019]
- [ ] T030 [US3] Implementar `P/scripts/dev.sh` y el objetivo `dev` en `P/Makefile` (build del motor si falta u obsoleto; exporta `JAVA_HOME`, `DOTNET_ROOT`; `npm run dev -w web`) [TS-014]
- [ ] T031 [US3] Ejecutar `DEVARCH_E2E=true` [TS-014, TS-019] y comprobar que la web ignora campos nuevos sin fallar (research E3)

**Checkpoint**: criterio de salida 2/2 cumplido — MVP de la fase.

---

## Phase 6: User Story 4 — Contrato a nivel de producto (P2)

**Goal**: contrato consumible por la web como workspace; `schema-check` idéntico a 005

**Independent Test**: `DEVARCH_E2E=true npx cucumber-js --tags @US-004`

- [ ] T032 [US4] Escribir `P/tests/acceptance/steps/product-contract.steps.ts` [TS-020, TS-021, TS-022, TS-023, TS-024] (TS-021 sobre copia temporal del producto) y verlos fallar
- [ ] T033 [US4] Declarar `"@static-inference/schema": "*"` en `P/web/package.json` y reinstalar (`npm install` en `P/`) [TS-022]
- [ ] T034 [US4] Ajustar `P/Makefile` `schema-check` → `bash scripts/schema-check.sh` y validar [TS-020, TS-021, TS-023]; reutilizar T014 para [TS-024]

**Checkpoint**: US4 verde; 005 sigue verde desde el producto.

---

## Phase 7: User Story 5 — Integración continua por áreas (P2)

**Goal**: workflow del monorepo con filtros, gates heredados y release `dev-arch-v*`

**Independent Test**: `npx cucumber-js --tags @US-005` + TS-039 de 005 (`CiWorkflowSteps`) en verde

- [ ] T035 [US5] Escribir `P/tests/acceptance/steps/continuous-integration.steps.ts` (carga YAML, evalúa filtros `dorny/paths-filter` y `on.*.paths` con `picomatch`) [TS-025, TS-026, TS-027, TS-028, TS-029, TS-030] y verlos fallar
- [ ] T036 [US5] Crear `.github/workflows/dev-arch.yml` en la raíz del monorepo según contracts/ci-workflow.md (jobs `changes`, `engine-java`, `engine-dotnet`, `web`, `schema`, `acceptance`, `release`; `working-directory: apps/dev-arch`; `timeout-minutes: 20`; Checkstyle 4763; SpotBugs exclude; `Category!=Corpus`; release framework-dependent con artefacto `dev-arch-engine-<tag>-<rid>`) [TS-025, TS-026, TS-027, TS-028, TS-029, TS-030]
- [ ] T037 [US5] Ejecutar `mvn -B test -Dtest=RunSchemaContractFeaturesTest` en `P/engine` y confirmar TS-039 de 005 en verde contra el nuevo workflow

**Checkpoint**: US5 verde.

---

## Phase 8: User Story 6 — Una sola versión del producto (P2)

**Goal**: `VERSION` única propagada; MCP reporta la versión de producto

**Independent Test**: `npx cucumber-js --tags @US-006` + `McpServerVersionTest`

- [ ] T038 [P] [US6] Escribir `P/tests/acceptance/steps/product-version.steps.ts` [TS-031, TS-032, TS-033, TS-035] (sobre copia temporal) y verlos fallar
- [ ] T039 [P] [US6] Escribir `P/engine/src/test/java/com/extractor/mcp/McpServerVersionTest.java` (serverInfo.version == `project.version` pasado por surefire) [TS-034] y verlo fallar (hoy literal)
- [ ] T040 [US6] Crear `P/VERSION` (`1.2.0`) e implementar `P/scripts/version.sh sync|check` + objetivos `version-sync`/`version-check` en `P/Makefile` según contracts/version.md; añadir `version-check` a `make test` [TS-031, TS-032, TS-033, TS-035]
- [ ] T041 [US6] Añadir `P/engine/src/main/resources/static-inference-version.properties` (`version=${project.version}`), filtrado de recursos en `P/engine/pom.xml` y lectura en `P/engine/src/main/java/com/extractor/mcp/McpServerMain.java` (sin literal) [TS-034]
- [ ] T042 [US6] Añadir `<Version>` a `P/engine/src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj` y ejecutar `make version-sync` (web `0.1.0` → `1.2.0`; `package.json` de producto) [TS-031]

**Checkpoint**: US6 verde; `schema/VERSION` sigue en 1.0.0.

---

## Phase 9: User Story 7 — Configuración y gobernanza unificadas (P3)

**Goal**: un solo `.gitignore`/`.editorconfig`/instrucciones de agente; `.mcp.json` relativo; tablero desde el producto

**Independent Test**: `DEVARCH_E2E=true npx cucumber-js --tags @US-007`

- [ ] T043 [US7] Escribir `P/tests/acceptance/steps/unified-configuration.steps.ts` (TS-038 con `git check-ignore`; TS-037 arranca el servidor MCP por stdio y lista herramientas) [TS-036, TS-037, TS-038, TS-039, TS-040, TS-041] y verlos fallar
- [ ] T044 [P] [US7] Crear `P/.editorconfig` (research D10) [TS-040]
- [ ] T045 [P] [US7] Crear `P/.mcp.json` con `tessl` y `dev-arch-engine` (`java -jar engine/target/java-dependency-extractor.jar --mcp`) [TS-036, TS-037]
- [ ] T046 [US7] Revisar `P/AGENTS.md`/`P/CLAUDE.md` (referencian `.tessl/RULES.md`; sin copias en `web/`, `engine/`, `schema/`) y ajustar `P/.gitignore` hasta pasar [TS-038, TS-039, TS-040]
- [ ] T047 [US7] Generar el tablero iikit desde `P/` (`bash .tessl/tiles/tessl-labs/intent-integrity-kit/skills/iikit-core/scripts/bash/generate-dashboard-safe.sh`, previo `tessl install`) [TS-041]

**Checkpoint**: todas las historias verdes.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T048 Validar `quickstart.md` completo en un clon limpio (`DEVARCH_E2E=true make test-acceptance`), registrando tiempos para SC-002 y SC-009
- [ ] T049 [P] Actualizar `P/engine/README.md` y `P/web/README.md` con una sección breve "Cómo construir y ejecutar desde el producto" (`make build`, `make dev`); sin fusionar documentación (Fase 7)
- [ ] T050 [P] Verificar que `project/dev-arch` y `project/static-inference` del monorepo no tienen cambios en la rama (`git diff main --stat -- project/`) [TS-004]
- [ ] T051 Re-ejecutar T014 y `testify-tdd.sh verify-hash` para 001–006 antes de cada commit final; commits convencionales; sin push

---


## Phase 11: Decisiones de usuario 2026-10-08 (A2, A3, A4)

- [ ] T052 [A2] Añadir en `apps/dev-arch/AGENTS.md` una nota NO normativa: "CONSTITUTION.md v1.x gobierna `engine/`; alcance producto (web) se define en la enmienda 2.0.0 de la Fase 7". No modificar CONSTITUTION.md.
- [ ] T053 [A3] En `.github/workflows/dev-arch.yml`, ejecutar TS-006/TS-007 (clon limpio + `make build && make test`) en un job propio `acceptance-clean-clone` con `timeout-minutes: 45`, solo en push a main y tags `dev-arch-v*`; el resto del job `acceptance` respeta el límite de SC-009.
- [ ] T054 [A4] Tras T002, congelar el repo origen `project/static-inference`: README con aviso "Movido a maestria-ia/apps/dev-arch/engine; este repo queda de solo lectura" y commit en su `main`. No borrar nada.

## Dependencies & Execution Order

- **Setup (T001–T008)**: T001 → T002 → T003, T004 (paralelas entre sí) → T005 → T006, T007, T008.
- **Foundational (T009–T017)**: depende de Setup. T009 ∥ T010 ∥ T013; T010 → T011 → T012 → T014; T012 → T015; T016 independiente; T017 al final. Bloquea todas las historias.
- **US1 (T018–T019)**: T018 puede escribirse antes de Setup (rojo contra `a32d5f5`); verde tras Foundational.
- **US2 (T020–T025)**: depende de Foundational. T020 → T021 → T022 → T023 → T024 → T025.
- **US3 (T026–T031)**: depende de US2 (T022 staging, T023 Makefile). T026 ∥ T027 → T028 → T029 → T030 → T031.
- **US4 (T032–T034)**: depende de Foundational (T015) y de T023.
- **US5 (T035–T037)**: depende de T023 (objetivos usados en CI) y T012 (`ciWorkflow()`).
- **US6 (T038–T042)**: depende de T023. T038 ∥ T039 → T040 → T041 → T042.
- **US7 (T043–T047)**: depende de Setup (T005, T006) y T022 (TS-037).
- **Polish (T048–T051)**: tras todas las historias.

**Critical path**: T001 → T002 → T003 → T005 → T010 → T011 → T012 → T014 → T017 → T020 → T021 → T022 → T023 → T025 → T026 → T028 → T029 → T031 → T048.

### Parallel batches

- Setup: [T003, T004]; [T006, T007, T008]
- Foundational: [T009, T010, T013, T016]
- Tras T023: US4 (T032–T034), US5 (T035–T037), US6 (T038–T042) y US7 (T043–T047) en paralelo con US3
- US3: [T026, T027]; US6: [T038, T039]; US7: [T044, T045]
- Polish: [T049, T050]

## Implementation Strategy

1. **MVP** = Setup + Foundational + US1 + US2 + US3 → criterio de salida de la Fase 2 (SC-001, SC-002).
2. Incremento 2: US4 + US6 (contrato y versión, sin CI).
3. Incremento 3: US5 (CI) — requiere push del monorepo para validarse en GitHub; localmente se valida por inspección (TS-025..TS-030).
4. Incremento 4: US7 + Polish.

## Notes

- No se modifican `.feature` ni aserciones; si un escenario parece incorrecto, re-ejecutar `/iikit-04-testify`.
- El bucle Maven en el repo origen: T001 solo toca `specs/`; T002 lee el repo origen por git, sin tocar su `target/`.
- La eliminación de `project/dev-arch` y la enmienda constitucional quedan fuera (Fase 7).
