---
description: "Task list for 005-json-schema-contract"
---

# Tasks: Contrato de datos único y versionado

**Input**: Design documents from `specs/005-json-schema-contract/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md, tests/features/ (44 escenarios TS-001..TS-044, hash LOCKED)

**Tests**: TDD obligatorio (CONSTITUTION.md principio III). Cada tarea de test DEBE fallar antes de su tarea de implementación. Prohibido modificar `.feature` o aserciones para pasar.

**Organization**: por historia de usuario. US2 (versión) se ejecuta antes que US1 porque los esquemas fijan `meta.schema_version` como requerido y la validación de salidas reales de US1 depende de que el productor ya lo emita; ambas son P1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizable (archivos distintos, sin dependencias pendientes)
- **[USn]**: historia de usuario de spec.md
- Trazabilidad: listas explícitas `[TS-001, TS-002]`

---

## Phase 1: Setup

**Purpose**: dependencias de test/dev y estructura de `schema/`

- [x] T001 Añadir excepción `!specs/005-json-schema-contract/` en `.gitignore` para que los `.feature` viajen con el código (research D11) — _N/A (moot): specs/ is fully versioned since 3fb9958; instead .gitignore gained exceptions for schema fixtures/v1.1.0 baselines (output*.json) and ignores schema/node_modules_
- [x] T002 [P] Añadir dependencias de test en `pom.xml`: `com.networknt:json-schema-validator` 1.5.x, `io.cucumber:cucumber-java`, `io.cucumber:cucumber-junit-platform-engine`, `org.junit.platform:junit-platform-suite` (todas `<scope>test</scope>`); configurar surefire `systemPropertyVariables` `schema.dir=${project.basedir}/schema` y `features.dir=${project.basedir}/specs/005-json-schema-contract/tests/features` — _networknt NOT declared: 3.0.0 already arrives transitively (compile) via MCP SDK; a test-scoped declaration would strip it from the fat JAR (research D2 addendum). Cucumber 7.14.0 + junit-platform-suite 1.10.0 (aligned with JUnit 5.10.0)_
- [x] T003 [P] Añadir `JsonSchema.Net` a `src/test/dotnet/DotNetAnalyzer.Tests/DotNetAnalyzer.Tests.csproj` (solo proyecto de tests) — _JsonSchema.Net 7.4.0_
- [x] T004 [P] Crear paquete `schema/package.json` (`"private": true`, `@static-inference/schema`, devDependencies `json-schema-to-typescript`, `json-schema-to-zod`, `zod`, `tsx`, `typescript`; scripts `generate`, `check`, `test`), `schema/tsconfig.json`, `schema/README.md` y `schema/VERSION` con `1.0.0` — _+ @apidevtools/json-schema-ref-parser (dereference for json-schema-to-zod); versions pinned_
- [x] T005 Registrar las dependencias en `mvn dependency:tree` y `dotnet list package` como línea base (guardar salida en `specs/005-json-schema-contract/baseline-deps.txt`) antes de cualquier cambio de producción [TS-043]

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: infraestructura de validación y línea base v1.1.0 que usan todas las historias

- [x] T006 Capturar artefactos de línea base v1.1.0 (JAR actual, sin cambios) para `java-standar` y `dotnet-ecommerce` en `src/test/resources/baseline-v1.1.0/<proyecto>/` con la raíz del repo normalizada a `${REPO}` [TS-020] — _captured with the v1.1.0 JAR + self-contained dotnet-analyzer (absolute project path)_
- [x] T007 [P] Crear utilidad de test `src/test/java/com/extractor/schema/SchemaRegistry.java`: mapea `https://static-inference.dev/schema/1.0.0/` → `schema/1.0.0/`, expone `validate(kind, JsonNode)` con errores que incluyen JSON Pointer y nombre del campo — _uses networknt 3.0.0 (transitive), format assertions on, JSON Pointer paths_
- [x] T008 [P] Crear utilidad de test `src/test/java/com/extractor/schema/DemoOutputs.java`: ejecuta `AnalysisService` sobre `test-for-demo/<proyecto>`, serializa los 3 artefactos con el mismo `ObjectMapper` del CLI y los escribe también en `target/schema-samples/java/<proyecto>/`
- [x] T009 [P] Crear utilidad de test `src/test/dotnet/DotNetAnalyzer.Tests/SchemaContract/SchemaRegistry.cs` (búsqueda ascendente de `schema/VERSION`, mismo mapeo de `$id`) — _class ContractSchemas (avoids clash with Json.Schema.SchemaRegistry)_
- [x] T010 Crear runner Cucumber `src/test/java/com/extractor/bdd/RunSchemaContractFeaturesTest.java` (`@Suite`, `@IncludeEngines("cucumber")`, features desde `features.dir`, glue `com.extractor.bdd`, tag `schema-contract`) y `src/test/java/com/extractor/bdd/ToolAvailability.java` (detección de `dotnet`/`node` para `@requires-dotnet`/`@requires-node`) — _runner not tagged schema-contract (avoids recursion with schema-check); schema-check e2e scenarios gated by SCHEMA_CHECK_E2E_
- [x] T011 Crear esqueleto `schema/1.0.0/common.schema.json` con `$schema` 2020-12, `$id` y `$defs` vacíos tipados por nombre (sin reglas aún) para que los registros resuelvan referencias

**Checkpoint**: validadores y runner listos; línea base capturada.

---

## Phase 3: User Story 2 — Versión del contrato en cada salida (P1)

**Goal**: los tres artefactos declaran `meta.schema_version = 1.0.0` desde Java y .NET sin alterar nada más.

**Independent Test**: analizar java-standar y dotnet-ecommerce, leer `meta.schema_version` en los 3 artefactos y comparar dos ejecuciones byte a byte.

### Tests for User Story 2 (escribir primero, deben FALLAR)

- [x] T012 [P] [US2] Step definitions de versión en `src/test/java/com/extractor/bdd/SchemaVersionSteps.java` y test JUnit `src/test/java/com/extractor/schema/SchemaVersionContractTest.java` (`@Tag("schema-contract")`), incluida validación de `schema_version` ausente/mal formada y de `collected_at` [TS-013, TS-014, TS-016, TS-017, TS-018, TS-019]
- [x] T013 [P] [US2] Test de no regresión contra línea base en `src/test/java/com/extractor/schema/BaselineParityTest.java` (elimina `meta.schema_version` y los `meta` nuevos y compara byte a byte) [TS-020]
- [x] T014 [P] [US2] Extender `src/test/java/com/extractor/analyzer/DotNetBridgeAnalyzerTest.java` con versión ausente/distinta → warning con ambas versiones y salida con `1.0.0` [TS-021]
- [x] T015 [P] [US2] Test xUnit `src/test/dotnet/DotNetAnalyzer.Tests/Model/SchemaVersionTests.cs` (`[Trait("Category","SchemaContract")]`): grafo crudo de `SampleProject`, `CodeIssuesSample` y `VbLegacy` contiene `schema_version` justo tras `source`; extender `Model/MetaDeterminismTests.cs` [TS-015, TS-017]

### Implementation for User Story 2

- [x] T016 [P] [US2] Crear `src/main/java/com/extractor/model/SchemaVersion.java` (`CURRENT = "1.0.0"`) y `src/main/java/com/extractor/model/DocumentMeta.java` (`schema_version`)
- [x] T017 [US2] Añadir `schema_version` a `DependencyGraph.Meta` tras `source` (`@JsonPropertyOrder`) y `meta` (primer campo) a `DependencyGraph.ApiContracts` en `src/main/java/com/extractor/model/DependencyGraph.java` (depende de T016)
- [x] T018 [US2] Añadir `meta` (primer campo) a `src/main/java/com/extractor/inference/ConsolidatedArchitecture.java` (depende de T016)
- [x] T019 [US2] Propagar/verificar versión en `src/main/java/com/extractor/analyzer/DotNetBridgeAnalyzer.java`: warning `dotnet-analyzer schema_version X != engine Y` en `AnalysisResult.warnings` y fijar `SchemaVersion.CURRENT` (depende de T017) [TS-021] — _bridge exposes warnings via LanguageAnalyzer#getWarnings (default empty); AnalysisService merges them into AnalysisResult.warnings_
- [x] T020 [P] [US2] Crear `src/main/dotnet/DotNetAnalyzer/Model/SchemaVersion.cs` (`Current = "1.0.0"`) y añadir `Meta.SchemaVersion` con `[JsonPropertyName("schema_version")]` y `[JsonPropertyOrder]` tras `source` en `src/main/dotnet/DotNetAnalyzer/Model/DependencyGraph.cs`
- [x] T021 [US2] Verificar que T012–T015 pasan y que la suite existente Java y .NET sigue verde [TS-044] — _Java 171/171 (excluding not-yet-implemented US3+ scenarios), .NET 156/156_

**Checkpoint**: versión presente; salidas byte-idénticas a v1.1.0 salvo los campos nuevos.

---

## Phase 4: User Story 1 — Contrato formal de los tres artefactos (P1) 🎯 MVP

**Goal**: esquemas 2020-12 de grafo, arquitectura y entrypoints que aceptan el output real y rechazan desviaciones.

**Independent Test**: validar las salidas de los 6 proyectos demo (aceptadas) y las mutaciones de TS-004..TS-012 (rechazadas/aceptadas según el caso).

### Tests for User Story 1 (escribir primero, deben FALLAR)

- [x] T022 [P] [US1] Step definitions de documentos en `src/test/java/com/extractor/bdd/ContractDocumentsSteps.java` (mutaciones por JSON Pointer, aserción de ruta/campo en el error) [TS-001, TS-002, TS-004, TS-005, TS-006, TS-007, TS-008, TS-009, TS-010, TS-011, TS-012]
- [x] T023 [P] [US1] Test JUnit `src/test/java/com/extractor/schema/SchemaDocumentsContractTest.java` (parametrizado por los 6 proyectos demo; escribe muestras en `target/schema-samples/`) [TS-001, TS-002]
- [x] T024 [P] [US1] Test JUnit `src/test/java/com/extractor/schema/SchemaNegativeContractTest.java` (campo requerido eliminado, catálogo cerrado, propiedad desconocida, mapas dinámicos, valores abiertos, `roslyn-fallback`, grafo vacío, `legacy_entrypoint` nulo/ausente/`internal`) [TS-004, TS-005, TS-006, TS-007, TS-008, TS-009, TS-011, TS-012]
- [x] T025 [P] [US1] Test xUnit `src/test/dotnet/DotNetAnalyzer.Tests/SchemaContract/GraphSchemaContractTests.cs`: grafo crudo de `SampleProject`, `CodeIssuesSample` y `VbLegacy` (omite nulos) válido; escribe muestras en `target/schema-samples/dotnet/` [TS-003]
- [x] T026 [P] [US1] Step definition `@requires-dotnet` que ejecuta `dotnet-analyzer` sobre el fixture `SampleProject` y valida stdout en `src/test/java/com/extractor/bdd/DotNetRawSteps.java` [TS-003, TS-015] — _file landed with the US2 commit (shared by TS-015)_

### Implementation for User Story 1

- [x] T027 [US1] Completar `$defs` en `schema/1.0.0/common.schema.json` según data-model.md (incluye [TS-016, TS-018]): `SchemaVersion` (`const 1.0.0`), `DocumentMeta`, `GraphMeta`, `Component` (`interface` con `deprecated: true`), `PackageGroup`, `CodeIssue`, `Edge` (patrón compuesto), `ApiContracts`, `ApiEndpoint`, `Parameter`, `ApiSchema`, `ApiSchemaProperty` — objetos cerrados, anulables opcionales, catálogos de FR-005 [TS-004, TS-005, TS-006, TS-007, TS-008, TS-009, TS-010]
- [x] T028 [US1] Añadir a `schema/1.0.0/common.schema.json` los `$defs` de arquitectura: `ProjectMetadata`, `PackageDependencyInfo`, `SecretLocation`, `Proposal`, `ProposalMetrics`, `ProposalSignals`, `ProposalTable`, `LegacyEntrypoint`, `SupportLibrary` (depende de T027) [TS-005, TS-012]
- [x] T029 [P] [US1] Crear `schema/1.0.0/dependency-graph.schema.json` (raíz cerrada; `api_contracts.meta` opcional) (depende de T027) [TS-001, TS-002, TS-003, TS-011]
- [x] T030 [P] [US1] Crear `schema/1.0.0/architecture.schema.json` (`meta` requerido) (depende de T028) [TS-001, TS-002, TS-012]
- [x] T031 [P] [US1] Crear `schema/1.0.0/entrypoints.schema.json` (`EntrypointsDocument` = `allOf` + `meta` requerido + `unevaluatedProperties: false`) (depende de T027) [TS-001, TS-002]
- [x] T032 [US1] Ejecutar T022–T026 hasta verde; ajustar **esquemas** (nunca las aserciones) ante falsos negativos y documentar en research.md cualquier campo real no previsto [TS-001, TS-002, TS-003, TS-004, TS-005, TS-006, TS-007, TS-008, TS-009, TS-010, TS-011, TS-012] — _schema-only adjustments; findings E13-E17 documented in research.md (response_schema optional, nullable numeric ranges via anyOf for networknt 3.0.0)_

**Checkpoint**: MVP — contrato formal validando salidas reales Java y .NET.

---

## Phase 5: User Story 3 — Contrato de las respuestas MCP (P1)

**Goal**: las 5 respuestas exitosas validan contra esquemas que reutilizan las definiciones de artefactos; paridad CLI/MCP validada.

**Independent Test**: invocar las 5 tools sobre java-standar y validar cada respuesta.

### Tests for User Story 3 (escribir primero, deben FALLAR)

- [x] T033 [P] [US3] Step definitions MCP en `src/test/java/com/extractor/bdd/McpResponsesSteps.java` (invoca handlers `*Tool.handle` como `McpServerContractTest`) [TS-022, TS-023, TS-024, TS-025, TS-026, TS-027] — _handlers invoked through McpServerMain.createToolSpecs via test helper com.extractor.mcp.McpCalls_
- [x] T034 [P] [US3] Test JUnit `src/test/java/com/extractor/schema/McpResponsesContractTest.java` (5 tools, `minViability` Alta/Media/Baja, forma de `analyze_project` y `get_component_metrics` sin `meta`) [TS-022, TS-023, TS-026, TS-027]
- [x] T035 [US3] Extender `src/test/java/com/extractor/integration/CliMcpParityTest.java`: validar cada artefacto CLI y cada respuesta MCP contra su contrato además de la equivalencia existente (`@Tag("schema-contract")`) [TS-024]

### Implementation for User Story 3

- [x] T036 [P] [US3] Crear `schema/1.0.0/mcp/get_dependency_graph.response.schema.json`, `get_architecture_proposal.response.schema.json`, `get_api_contracts.response.schema.json`, `get_component_metrics.response.schema.json` como `$ref` puros a documentos/definición `Component` [TS-022, TS-025]
- [x] T037 [P] [US3] Crear `schema/1.0.0/mcp/analyze_project.response.schema.json` (camelCase, cerrado, `filesGenerated` con 3 elementos) [TS-022, TS-026]
- [x] T038 [US3] Ejecutar T033–T035 hasta verde sin tocar adaptadores MCP (principio II) [TS-022, TS-023, TS-024, TS-025, TS-026, TS-027] — _no MCP adapter changed_

**Checkpoint**: CLI y MCP verificablemente equivalentes contra el contrato.

---

## Phase 6: User Story 4 — Tipos y validadores generados (P2)

**Goal**: `schema/generated/` con tipos TS y validadores zod deterministas.

**Independent Test**: `npm --prefix schema test` acepta fixtures válidas y muestras, rechaza inválidas; regenerar no produce diff.

### Tests for User Story 4 (escribir primero, deben FALLAR)

- [ ] T039 [P] [US4] Copiar salidas reales normalizadas a `schema/fixtures/valid/{java-standar,dotnet-ecommerce}/` y `schema/fixtures/valid/mcp/analyze_project.json`; crear `schema/fixtures/invalid/{graph-missing-component-id,graph-layer-out-of-catalogue,graph-unknown-property,graph-missing-schema-version,architecture-bad-viability}.json` [TS-029, TS-030]
- [ ] T040 [P] [US4] Escribir `schema/test/validators.test.ts` (`node:test`): cobertura de tipos/validadores, fixtures válidas/ inválidas con ruta, muestras de `target/schema-samples/`, determinismo de regeneración, banner "generated — do not edit" [TS-028, TS-029, TS-030, TS-031, TS-032, TS-033]
- [ ] T041 [P] [US4] Step definitions `@requires-node` en `src/test/java/com/extractor/bdd/GeneratedTypesSteps.java` que delegan en `npm --prefix schema test`/`run check` [TS-028, TS-029, TS-030, TS-031, TS-032, TS-033]

### Implementation for User Story 4

- [ ] T042 [US4] Implementar `schema/gen/generate.mjs`: `json-schema-to-typescript` → `schema/generated/types.ts`, `json-schema-to-zod` → `schema/generated/validators.ts` con `parseDependencyGraph`, `parseConsolidatedArchitecture`, `parseEntrypointsDocument`, `parseAnalyzeProjectResponse`, `parseComponent`; `schema/generated/index.ts`; orden estable, banner sin fecha, `\n` final [TS-028, TS-032, TS-033]
- [ ] T043 [US4] Implementar `schema/gen/check.mjs`: regenera a tmp y compara con `generated/`, valida `fixtures/valid` (aceptar), `fixtures/invalid` (rechazar), `../target/schema-samples/**`, coherencia `VERSION`↔`$id`, modo `--file/--kind` (contracts/schema-package.md) [TS-029, TS-030, TS-031, TS-032]
- [ ] T044 [US4] Ejecutar `npm --prefix schema run generate` y commitear `schema/generated/` + `schema/package-lock.json`; T039–T041 en verde [TS-028, TS-029, TS-030, TS-031, TS-032, TS-033]

**Checkpoint**: consumidor tipado listo para Fase 2.

---

## Phase 7: User Story 5 — Verificación única `schema-check` (P2)

**Goal**: un comando que falla si Java, .NET, TS o la versión divergen.

**Independent Test**: estado alineado → exit 0; cada divergencia de quickstart.md §4 → FAIL en el frente correcto.

### Tests for User Story 5 (escribir primero, deben FALLAR)

- [ ] T045 [P] [US5] Step definitions en `src/test/java/com/extractor/bdd/SchemaCheckSteps.java`: copian el repo a un directorio temporal (worktree), introducen cada divergencia, ocultan herramientas vía `PATH` y ejecutan `scripts/schema-check.sh`; aserción de formato de líneas, exit code y tiempo < 10 min [TS-034, TS-035, TS-036, TS-037, TS-038]
- [ ] T046 [P] [US5] Step definition de CI en `src/test/java/com/extractor/bdd/CiWorkflowSteps.java` (parsea `.github/workflows/ci.yml`: job `schema-check` con JDK 17/.NET 8/Node 20, sin `continue-on-error`) [TS-039]

### Implementation for User Story 5

- [ ] T047 [US5] Implementar `scripts/schema-check.sh` según contracts/schema-check.md (4 frentes, agregación, `NOT VERIFIED` ⇒ exit 1, mensajes con frente y ruta) [TS-034, TS-035, TS-036, TS-037, TS-038]
- [ ] T048 [US5] Crear `Makefile` raíz con target `schema-check` (y `schema-generate`) que invoca el script [TS-034]
- [ ] T049 [US5] Añadir job `schema-check` bloqueante en `.github/workflows/ci.yml` (setup-java 17, setup-dotnet 8.0.x, setup-node 20, `make schema-check`) [TS-039]
- [ ] T050 [US5] Ejecutar T045–T046 hasta verde [TS-034, TS-035, TS-036, TS-037, TS-038, TS-039]

**Checkpoint**: criterio de salida de la Fase 1 cumplido.

---

## Phase 8: User Story 6 — Política de versionado y documentación (P3)

**Goal**: `docs/SCHEMA.md` y referencias alineadas; huella mínima demostrada.

**Independent Test**: inspección guiada por TS-040..TS-043.

### Tests for User Story 6 (escribir primero, deben FALLAR)

- [ ] T051 [P] [US6] Step definitions en `src/test/java/com/extractor/bdd/GovernanceSteps.java`: tabla de clasificación y formato de nota de migración en `docs/SCHEMA.md`; los 7 temas en `skills/static-inference-expert-{claude,kiro}/references/json-schema.md`, `MCP.md` y contratos de specs 001/002/003 (si existen localmente); comparación de dependencias con `specs/005-json-schema-contract/baseline-deps.txt` [TS-040, TS-041, TS-042, TS-043]

### Implementation for User Story 6

- [ ] T052 [P] [US6] Escribir `docs/SCHEMA.md`: semver, tabla mayor/menor/parche (FR-019), formato de nota de migración, procedimiento de regeneración, referencia a principio I [TS-040, TS-041]
- [ ] T053 [P] [US6] Alinear `skills/static-inference-expert-claude/references/json-schema.md` y `skills/static-inference-expert-kiro/references/json-schema.md` con el contrato (7 divergencias) y remitir a `schema/1.0.0/` como fuente de verdad [TS-042]
- [ ] T054 [P] [US6] Marcar como obsoletos y remitir a `schema/1.0.0/` los contratos `specs/002-dotnet-roslyn-analyzer/contracts/dependency-graph-schema.md`, `specs/003-dotnet-code-issues/contracts/code-issues-schema.md` y `specs/001-mcp-server-local/contracts/mcp-tools.md`; actualizar `MCP.md` y `README.md` [TS-042]
- [ ] T055 [US6] Ejecutar T051 hasta verde [TS-040, TS-041, TS-042, TS-043]

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T056 [P] Añadir entrada `[Unreleased]` en `CHANGELOG.md`: `meta.schema_version` (aditivo), `meta` en arquitectura/entrypoints, paquete `schema/`, `make schema-check`
- [ ] T057 Ejecutar `mvn -B clean verify`, `dotnet test`, `npm --prefix schema test` y `make schema-check`; Checkstyle/SpotBugs/`dotnet format` sin nuevos avisos [TS-034, TS-043, TS-044]
- [ ] T058 Validar quickstart.md de punta a punta (incluida la tabla de divergencias §4) [TS-035, TS-036]

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → **US2 (3)** → **US1 (4)** → **US3 (5)** → **US4 (6)** → **US5 (7)** → **US6 (8)** → **Polish (9)**
- US3 depende de los esquemas de US1 (`$ref`). US4 depende de US1+US3 (genera desde todos los esquemas). US5 orquesta US1–US4. US6 solo depende de US1 (contenido del contrato) y T005 (línea base de dependencias); puede adelantarse en paralelo con US4/US5.

### Critical Path

T002 → T007 → T012 → T016 → T017 → T022 → T027 → T028 → T030 → T032 → T036 → T038 → T042 → T043 → T044 → T047 → T050 → T057 (18 tareas)

### Parallel Batches

- Fase 1: T002, T003, T004 en paralelo (tras T001)
- Fase 2: T007, T008, T009 en paralelo; T010 y T011 tras T002
- US2: tests T012–T015 en paralelo; T016 ∥ T020
- US1: tests T022–T026 en paralelo; T029 ∥ T031 (tras T027), T030 tras T028
- US3: T033 ∥ T034; T036 ∥ T037
- US4: T039 ∥ T040 ∥ T041
- US6: T052 ∥ T053 ∥ T054 (puede correr junto a Fase 6/7)

### Story Independence

- Sin inversiones de prioridad: P1 (US2, US1, US3) no depende de P2/P3.
- US1 depende de US2 (ambas P1) — documentado arriba.

## Implementation Strategy

1. **MVP** = Fases 1–4 (versión + contrato formal validado en Java y .NET).
2. Incremento 2 = US3 (paridad MCP).
3. Incremento 3 = US4 + US5 (criterio de salida de la fase).
4. Cierre = US6 + Polish.

## Notes

- Nunca editar `.feature` ni aserciones; si un requisito cambia, re-ejecutar `/iikit-04-testify`.
- Las pruebas nativas llevan el ID `TS-XXX` en `@DisplayName`/nombre del test.
- Confirmar con el usuario los defaults marcados "pendiente confirmar" en spec.md y research.md D11 antes de `/iikit-07-implement`.
