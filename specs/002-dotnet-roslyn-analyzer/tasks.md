# Tasks: dotnet-roslyn-analyzer

**Input**: Design documents from `/specs/002-dotnet-roslyn-analyzer/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓
**Feature files**: 6 .feature files with test spec IDs TS-001–TS-007, TS-011–TS-015, TS-021–TS-023, TS-031–TS-032, TS-041–TS-043, TS-051–TS-052

**TDD mandate**: CONSTITUTION §III (NON-NEGOTIABLE) — tests MUST fail before implementation code is written.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to
- Exact file paths are included in all implementation tasks

---

## Phase 1: Setup (Project Scaffolding)

**Purpose**: Initialize the .NET analyzer project and test infrastructure before any analysis code is written.

- [x] T001 Create dotnet-analyzer project scaffold: `dotnet-analyzer/DotNetAnalyzer.sln`, `dotnet-analyzer/src/DotNetAnalyzer/DotNetAnalyzer.csproj` with Roslyn + System.Text.Json dependencies (Roslyn 4.9.2, MSBuild.Locator 1.7.8)
- [x] T002 Create xUnit test project scaffold: `dotnet-analyzer/tests/DotNetAnalyzer.Tests/DotNetAnalyzer.Tests.csproj` with xUnit 2.7.0 and FluentAssertions 6.12.0
- [x] T003 [P] Create minimal .NET fixture project for testing: `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Fixtures/SampleProject/` with controllers, services, repositories, DbContext, and DI registration

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core abstractions used across all user stories. No story implementation can begin until this phase completes.

**CRITICAL**: All Phase 3–8 user story tasks depend on this phase.

- [x] T004 [P] Create `LanguageAnalyzer` interface in `src/main/java/com/extractor/analyzer/LanguageAnalyzer.java` — defines `analyzeProject(Path)` and `getDependencyResolver()`
- [x] T005 [P] Create `ProjectType` enum in `src/main/java/com/extractor/analyzer/ProjectType.java` — values: JAVA, DOTNET, AMBIGUOUS, UNKNOWN
- [x] T006 [P] Create .NET model classes mirroring Java models (same JSON property names): `dotnet-analyzer/src/DotNetAnalyzer/Model/DependencyGraph.cs`, `Component.cs`, `Edge.cs`, `ApiEndpoint.cs`, `ApiSchema.cs`

**Checkpoint**: Foundational abstractions ready — story phases can begin.

---

## Phase 3: User Story 5 — Java Backward Compatibility (Priority: P1) MVP

**Goal**: Wrap existing analysis behind `LanguageAnalyzer` interface with zero behavior change for Java projects.

**Independent Test**: Run existing Java test suite — all tests must pass unchanged.

> **NOTE: Write failing tests FIRST before any production code changes**

### Tests for User Story 5

- [x] T007 [P] [US5] Write `LanguageAnalyzerTest.java` in `src/test/java/com/extractor/analyzer/LanguageAnalyzerTest.java` — interface contract tests to pass [TS-041, TS-042, TS-043]
- [x] T008 [P] [US5] Write `JavaSpoonAnalyzerTest.java` in `src/test/java/com/extractor/analyzer/JavaSpoonAnalyzerTest.java` — adapter delegation tests to pass [TS-041, TS-042]

### Implementation for User Story 5

- [x] T009 [US5] Create `JavaSpoonAnalyzer.java` in `src/main/java/com/extractor/analyzer/JavaSpoonAnalyzer.java` — wraps existing `ProjectAnalyzer`, implements `LanguageAnalyzer`, zero logic duplication (depends on T007, T008)
- [x] T010 [US5] Write `AnalysisServiceDotNetTest.java` in `src/test/java/com/extractor/service/AnalysisServiceDotNetTest.java` — verifies Java routing unchanged; tests must fail before T011 [TS-041, TS-043]
- [x] T011 [US5] Modify `AnalysisService.java` in `src/main/java/com/extractor/service/AnalysisService.java` — inject `LanguageAnalyzer`, default to `JavaSpoonAnalyzer`, preserve all existing behavior (depends on T009, T010)

**Checkpoint**: All existing Java tests pass. US5 acceptance criteria met.

---

## Phase 4: User Story 2 — Automatic Project Type Detection (Priority: P1) MVP

**Goal**: Detect project type (Java/.NET/AMBIGUOUS/UNKNOWN) from directory marker files.

**Independent Test**: Point tool at directories with known markers and verify correct analyzer selection.

**Depends on**: Phase 2 (T005) and Phase 3 (T011) complete.

> **NOTE: Write failing tests FIRST before any production code changes**

### Tests for User Story 2

- [x] T012 [P] [US2] Write `ProjectTypeDetectorTest.java` in `src/test/java/com/extractor/analyzer/ProjectTypeDetectorTest.java` — tests for all detection cases including .NET Framework to pass [TS-011, TS-012, TS-013, TS-014, TS-015]
- [x] T013 [P] [US2] Write `DotNetToolLocatorTest.java` in `src/test/java/com/extractor/analyzer/DotNetToolLocatorTest.java` — tests for executable discovery to pass [TS-013, TS-014]

### Implementation for User Story 2

- [x] T014 [US2] Create `ProjectTypeDetector.java` in `src/main/java/com/extractor/analyzer/ProjectTypeDetector.java` — scans for `.sln`/`.csproj` → DOTNET, `pom.xml`/`build.gradle` → JAVA, both → AMBIGUOUS, neither → UNKNOWN; handles subdirectory `.sln` ambiguity (depends on T012)
- [x] T015 [US2] Create `DotNetToolLocator.java` in `src/main/java/com/extractor/analyzer/DotNetToolLocator.java` — search order: JAR sibling → PATH → DOTNET_ANALYZER_PATH env var; throws with installation instructions if not found (depends on T013)
- [x] T016 [US2] Integrate `ProjectTypeDetector` into `AnalysisService.java` routing — wire detection and clear error messages for AMBIGUOUS/UNKNOWN cases (depends on T014, T015, T011)

**Checkpoint**: Auto-detection works for Java, .NET, mixed, and unknown projects. US2 acceptance criteria met.

---

## Phase 5: User Story 1 — Analyze .NET Monolithic Project (Priority: P1) MVP

**Goal**: Full .NET analysis pipeline — Roslyn AST → DependencyGraph JSON via subprocess bridge.

**Independent Test**: Analyze `Fixtures/SampleProject/` and verify DependencyGraph schema, components, edges, and API contracts match expectations.

**Depends on**: Phase 2 (T006) and Phase 4 (T016) complete.

> **NOTE: Write failing tests FIRST before any .NET production code**

### Tests for User Story 1 (.NET side — xUnit)

- [x] T017 [P] [US1] Write `TypeAnalyzerTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/TypeAnalyzerTests.cs` — Pass 1 type extraction tests to pass [TS-001, TS-004]
- [x] T018 [P] [US1] Write `InvocationAnalyzerTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/InvocationAnalyzerTests.cs` — Pass 2 method call dependency tests to pass [TS-001, TS-004]
- [x] T019 [P] [US1] Write `EndpointExtractorTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/EndpointExtractorTests.cs` — ASP.NET Core attribute routing tests to pass [TS-002]
- [x] T020 [P] [US1] Write `TableExtractorTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/TableExtractorTests.cs` — Entity Framework DbSet and `[Table]` detection tests to pass [TS-003]
- [x] T021 [US1] Write `FullPipelineTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Integration/FullPipelineTests.cs` — end-to-end integration test using SampleProject fixture to pass [TS-001, TS-004, TS-005, TS-006, TS-007]

### Tests for User Story 1 (Java bridge side — JUnit)

- [x] T022 [US1] Write `DotNetBridgeAnalyzerTest.java` in `src/test/java/com/extractor/analyzer/DotNetBridgeAnalyzerTest.java` — subprocess mocking and JSON parsing tests to pass [TS-001, TS-004, TS-005, TS-006, TS-007]

### Implementation for User Story 1 (.NET analyzer)

- [x] T023 [P] [US1] Implement `TypeAnalyzer.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/TypeAnalyzer.cs` — Pass 1: extract `INamedTypeSymbol` from Roslyn semantic model into `Component` objects with qualified name, file path, LOC, annotations/attributes (depends on T017)
- [x] T024 [P] [US1] Implement `InvocationAnalyzer.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/InvocationAnalyzer.cs` — Pass 2: walk `InvocationExpressionSyntax` nodes to build `Edge` list for method call dependencies (depends on T018)
- [x] T025 [P] [US1] Implement `EndpointExtractor.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/EndpointExtractor.cs` — detect `[ApiController]`, `[HttpGet]`, `[HttpPost]`, `[HttpPut]`, `[HttpDelete]`, `[Route]` attributes; populate `api_contracts.endpoints` (depends on T019)
- [x] T026 [P] [US1] Implement `TableExtractor.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/TableExtractor.cs` — detect `DbSet<T>` properties in `DbContext` and `[Table]` attributes; populate `tables_used` (depends on T020)
- [x] T027 [US1] Implement `ProjectAnalyzer.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/ProjectAnalyzer.cs` — 5-pass orchestrator: opens workspace via `MSBuildWorkspace` (with `AdhocWorkspace` fallback for no-build mode), runs T023–T026 passes, assembles `DependencyGraph` (depends on T023, T024, T025, T026)
- [x] T028 [US1] Implement `Program.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Program.cs` — CLI entry point: parse project path arg, call `ProjectAnalyzer`, serialize `DependencyGraph` to stdout as JSON, write errors to stderr (depends on T027)

### Implementation for User Story 1 (Java bridge)

- [x] T029 [US1] Create `DotNetBridgeAnalyzer.java` in `src/main/java/com/extractor/analyzer/DotNetBridgeAnalyzer.java` — runs `dotnet-analyzer <path>` via `ProcessBuilder`, reads stdout JSON, deserializes with Jackson into existing `DependencyGraph` model; captures stderr for diagnostics; configurable timeout (depends on T022, T028, T015)
- [x] T030 [US1] Create `DotNetDependencyResolver.java` in `src/main/java/com/extractor/analyzer/DotNetDependencyResolver.java` — reads NuGet package references from `.csproj` files; implements `DependencyResolver` for the bridge
- [x] T031 [US1] Wire `DotNetBridgeAnalyzer` into `AnalysisService.java` routing — DOTNET type → `DotNetBridgeAnalyzer`; validate with integration test against SampleProject fixture (depends on T029, T030, T016)

**Checkpoint**: Full .NET analysis pipeline works end-to-end. US1 acceptance criteria met.

---

## Phase 6: User Story 3 — .NET Dependency Injection Analysis (Priority: P2)

**Goal**: Graph reflects constructor injection, interface-implementation relationships, and IServiceCollection registrations.

**Independent Test**: Analyze a project with known DI patterns and verify edges in graph.

**Depends on**: Phase 5 (T027) complete.

> **NOTE: Write failing tests FIRST**

### Tests for User Story 3

- [x] T032 [P] [US3] Write `DependencyInjectionAnalyzerTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/DependencyInjectionAnalyzerTests.cs` — tests for constructor injection, interface-implementation, and IServiceCollection registration detection to pass [TS-021, TS-022, TS-023]

### Implementation for User Story 3

- [x] T033 [US3] Implement `DependencyInjectionAnalyzer.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/DependencyInjectionAnalyzer.cs` — Pass 3: detect constructor parameter injection → edges; detect `I` prefix interface implementations → edges; parse `services.AddScoped/AddTransient/AddSingleton<I,T>()` calls → edges (depends on T032)
- [x] T034 [US3] Integrate `DependencyInjectionAnalyzer` into `ProjectAnalyzer.cs` as Pass 3 — merge DI edges into the `DependencyGraph` produced by Pass 1+2 (depends on T033)

**Checkpoint**: DI edges appear in graph. US3 acceptance criteria met.

---

## Phase 7: User Story 4 — Quality Metrics for .NET Components (Priority: P2)

**Goal**: CBO, LCOM, LOC, and layer classification for all .NET components.

**Independent Test**: Analyze a known component and verify metric values match expected computation.

**Depends on**: Phase 5 (T027) complete. Can run **in parallel with Phase 6**.

> **NOTE: Write failing tests FIRST**

### Tests for User Story 4

- [x] T035 [P] [US4] Write `MetricsCalculatorTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/MetricsCalculatorTests.cs` — CBO, LCOM, LOC calculation tests to pass [TS-031]
- [x] T036 [P] [US4] Write `LayerClassifierTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/LayerClassifierTests.cs` — layer assignment tests for Controller, Service, Repository, Shared to pass [TS-032]

### Implementation for User Story 4

- [x] T037 [P] [US4] Implement `MetricsCalculator.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/MetricsCalculator.cs` — CBO: count unique outgoing type dependencies; LCOM: ratio of methods sharing fields; LOC: count non-blank non-comment lines (depends on T035)
- [x] T038 [P] [US4] Implement `LayerClassifier.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/LayerClassifier.cs` — classify using `[ApiController]`/`*Controller` → Controlador; `*Service`/`*Manager`/`*Handler` → Negocio; `DbContext`/`*Repository`/`*Data` → Datos; `*Helper`/`*Extensions`/`*Middleware` → Compartida (depends on T036)
- [x] T039 [US4] Integrate `MetricsCalculator` and `LayerClassifier` into `ProjectAnalyzer.cs` — compute and assign metrics for each `Component` after Pass 1 (depends on T037, T038)

**Checkpoint**: Metrics and layer assignments present on all components. US4 acceptance criteria met.

---

## Phase 8: User Story 6 — Messaging & Configuration Detection (Priority: P3)

**Goal**: Detect MassTransit/Azure Service Bus patterns and IConfiguration/appsettings references.

**Independent Test**: Analyze a project with known messaging/config patterns and verify field population.

**Depends on**: Phase 5 (T027) complete. Can run **in parallel with Phases 6 and 7**.

> **NOTE: Write failing tests FIRST**

### Tests for User Story 6

- [x] T040 [P] [US6] Write `MessagingDetectorTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/MessagingDetectorTests.cs` — tests for MassTransit and Azure Service Bus detection to pass [TS-051]
- [x] T041 [P] [US6] Write `SecretsDetectorTests.cs` in `dotnet-analyzer/tests/DotNetAnalyzer.Tests/Analysis/SecretsDetectorTests.cs` — tests for `IConfiguration` and appsettings reference detection to pass [TS-052]

### Implementation for User Story 6

- [x] T042 [P] [US6] Implement `MessagingDetector.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/MessagingDetector.cs` — detect MassTransit consumer/publisher patterns; detect Azure Service Bus `ServiceBusClient`/`ServiceBusSender`/`ServiceBusProcessor`; populate `messaging_type` and `messaging_role` (depends on T040)
- [x] T043 [P] [US6] Implement `SecretsDetector.cs` in `dotnet-analyzer/src/DotNetAnalyzer/Analysis/SecretsDetector.cs` — detect `IConfiguration` injections and `_configuration["key"]` indexer calls; detect `appsettings.json` key references; populate `secrets_references` (depends on T041)
- [x] T044 [US6] Integrate `MessagingDetector` and `SecretsDetector` into `ProjectAnalyzer.cs` — run as supplementary passes after Pass 3, merge results into `Component` fields (depends on T042, T043)

**Checkpoint**: Messaging and config patterns detected. US6 acceptance criteria met.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Integration validation, .NET annotation support in existing Java components, CLI polish.

**Depends on**: All user story phases complete.

- [x] T045 Add .NET annotations (`ApiController`, `Authorize`, `FromServices`) to Java `LayerClassifier` lists in `src/main/java/com/extractor/inference/LayerClassifier.java` — extend annotation arrays without changing logic
- [x] T046 Add .NET Framework project support validation to `ProjectTypeDetector.java` — ensure `.csproj` files targeting `net48` or earlier are correctly detected as DOTNET [TS-015]
- [x] T047 Update CLI help text in `src/main/java/com/extractor/MicroserviceInferenceMain.java` — add `.NET support` note to usage string and supported project types
- [x] T048 [P] End-to-end integration test (Java CLI → .NET project): verify all 5 MCP tools return valid responses for `.NET` project with real `DependencyGraph`, proposals, and metrics [TS-005, TS-006]
- [x] T049 [P] Verify all existing Java tests still pass — run `mvn test` and confirm `CliMcpParityTest.java` passes unchanged [TS-041, TS-042, TS-043]
- [x] T050 Run quickstart.md validation scenarios end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)         → No dependencies. Start immediately.
Phase 2 (Foundational)  → Depends on Phase 1 (T001, T002).
Phase 3 (US5 — P1)      → Depends on Phase 2 (T004 for interface).
Phase 4 (US2 — P1)      → Depends on Phase 2 (T005) + Phase 3 (T011 AnalysisService).
Phase 5 (US1 — P1)      → Depends on Phase 2 (T006) + Phase 4 (T016 routing).
Phase 6 (US3 — P2)      → Depends on Phase 5 (T027 ProjectAnalyzer scaffold).
Phase 7 (US4 — P2)      → Depends on Phase 5 (T027). PARALLEL with Phase 6.
Phase 8 (US6 — P3)      → Depends on Phase 5 (T027). PARALLEL with Phases 6+7.
Phase 9 (Polish)        → Depends on Phases 6+7+8 complete.
```

### Critical Path (longest chain)

T001 → T002 → T006 → T017–T020 → T023–T026 → T027 → T021/T022 → T029 → T031 → T048

**Depth**: 10 sequential steps from setup to end-to-end validation.

### Parallel Batches

**Batch A (Phase 2 — run together)**:
`T004` + `T005` + `T006` (all Foundational, different files)

**Batch B (Phase 3 — tests run together)**:
`T007` + `T008`

**Batch C (Phase 4 — tests run together)**:
`T012` + `T013`

**Batch D (Phase 5 — xUnit tests run together)**:
`T017` + `T018` + `T019` + `T020`

**Batch E (Phase 5 — .NET implementation run together)**:
`T023` + `T024` + `T025` + `T026`

**Batch F (Phases 6+7+8 — all story tests run together)**:
`T032` + `T035` + `T036` + `T040` + `T041`

**Batch G (Phases 6+7+8 — all story implementations run together)**:
`T033` + `T037` + `T038` + `T042` + `T043`

**Batch H (Phase 9 — polish run together)**:
`T048` + `T049`

---

## MVP Scope Suggestion

**MVP = Phases 1–5** (Tasks T001–T031) delivers:
- US5: Zero regression for existing Java users
- US2: Auto-detection of project type
- US1: Full .NET analysis (ASP.NET Core, EF, basic metrics)

US3 (DI analysis), US4 (quality metrics), and US6 (messaging) add depth incrementally post-MVP.

---

## Summary

| Story | Priority | Tasks | Parallel Opportunities |
|-------|----------|-------|----------------------|
| US5 — Java Backward Compat | P1 | T007–T011 | T007, T008 |
| US2 — Auto Project Detection | P1 | T012–T016 | T012, T013 |
| US1 — .NET Analysis | P1 | T017–T031 | T017–T020, T023–T026 |
| US3 — DI Analysis | P2 | T032–T034 | T032 |
| US4 — Quality Metrics | P2 | T035–T039 | T035, T036, T037, T038 |
| US6 — Messaging/Config | P3 | T040–T044 | T040, T041, T042, T043 |
| Setup + Foundational | — | T001–T006 | T004, T005, T006 |
| Polish | — | T045–T050 | T048, T049 |
| **Total** | | **50 tasks** | **~20 parallel opportunities** |

## Bug Fix Tasks

- [x] T-B001 [BUG-001] Implement fix for BUG-001 referencing test spec bugfix_BUG-001.feature: omit `meta.collected_at` by default in Java and .NET; opt-in via `STATIC_INFERENCE_COLLECTED_AT=true`
- [x] T-B002 [BUG-001] Verify fix passes test bugfix_BUG-001.feature for BUG-001: two CLI runs produce byte-identical output
- [x] T-B003 [BUG-002] Implement fix for BUG-002 referencing test spec bugfix_BUG-002.feature: drain stderr concurrently, enforce real timeout
- [x] T-B004 [BUG-002] Verify fix passes test bugfix_BUG-002.feature for BUG-002
- [x] T-B005 [BUG-003] Implement fix for BUG-003 referencing test spec bugfix_BUG-003.feature: resolve `dotnet-analyzer.exe` on Windows
- [x] T-B006 [BUG-003] Verify fix passes test bugfix_BUG-003.feature for BUG-003
- [x] T-B007 [BUG-004] Implement fix for BUG-004 referencing test spec bugfix_BUG-004.feature: iterate role keywords in a fixed declared order
- [x] T-B008 [BUG-004] Verify fix passes test bugfix_BUG-004.feature for BUG-004
