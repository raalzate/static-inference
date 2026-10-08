# Tasks: MCP Server — Instalación Local

**Input**: Design documents from `/specs/001-mcp-server-local/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, tests/features/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[USn]**: Which user story this task belongs to
- Traceability: test spec IDs listed as comma-separated (e.g., `[TS-001, TS-002]`)

---

## Phase 1: Setup

**Purpose**: Project configuration changes required before any implementation

- [x] T001 Upgrade Java version from 11 to 17 in pom.xml (maven.compiler.source, maven.compiler.target, compiler plugin config)
- [x] T002 Add MCP Java SDK dependency `io.modelcontextprotocol.sdk:mcp:1.1.0` to pom.xml
- [x] T003 Verify project compiles and existing tests pass after Java 17 upgrade (`mvn clean test`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared core that ALL user stories depend on — MUST complete before story work

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Create `src/main/java/com/extractor/service/AnalysisResult.java` — value object wrapping DependencyGraph + ConsolidatedArchitecture + ApiContracts + metadata (componentCount, edgeCount, warnings)
- [x] T005 Create `src/main/java/com/extractor/service/AnalysisService.java` — extract orchestration logic from MicroserviceInferenceMain into shared core with methods: analyzeProject(Path), getDependencyGraph(Path), getArchitectureProposal(Path, String), getApiContracts(Path), getComponentMetrics(Path, String)
- [x] T006 Write unit tests for AnalysisService in `src/test/java/com/extractor/service/AnalysisServiceTest.java` — test each method with valid input, invalid path, empty project [TS-027]
- [x] T007 Refactor `src/main/java/com/extractor/MicroserviceInferenceMain.java` to delegate to AnalysisService instead of inline orchestration (preserve identical CLI behavior)

**Checkpoint**: Shared core extracted — CLI produces identical output as before (Constitution I)

---

## Phase 3: User Story 3 — Instalar y configurar el servidor localmente (Priority: P1)

**Goal**: MCP server starts via `--mcp` flag, communicates over stdio, lists 5 tools

**Independent Test**: Execute JAR with `--mcp`, send initialize + tools/list, verify 5 tools returned

### Tests for US3 (TDD — write FIRST, verify they FAIL)

- [x] T008 [P] [US3] Write contract test for MCP server initialization and tool listing in `src/test/java/com/extractor/mcp/McpServerContractTest.java` [TS-015, TS-016, TS-018, TS-019]

### Implementation for US3

- [x] T009 [US3] Create `src/main/java/com/extractor/mcp/ToolSchemas.java` — JSON schema definitions for all 5 tool inputs (per contracts/mcp-tools.md)
- [x] T010 [US3] Create `src/main/java/com/extractor/mcp/McpServerMain.java` — MCP server entry point with StdioServerTransportProvider, registers 5 tools with schemas, delegates to AnalysisService [TS-015, TS-016, TS-019]
- [x] T011 [US3] Add `--mcp` flag routing in `src/main/java/com/extractor/MicroserviceInferenceMain.java` — detect flag, delegate to McpServerMain.start() [TS-018]
- [x] T012 [US3] Configure SLF4J to output to stderr in MCP mode (research.md Decision 7)
- [x] T013 [P] [US3] Create example `.mcp.json` configuration file at project root for agent integration [TS-017]

**Checkpoint**: `java -jar tool.jar --mcp` starts server, agent discovers 5 tools via stdio

---

## Phase 4: User Story 1 — Invocar análisis completo desde un agente LLM (Priority: P1)

**Goal**: Agent invokes `analyze_project`, receives full structured results, files generated on disk

**Independent Test**: Configure MCP server, invoke analyze_project on sample Java project, verify response + output files

### Tests for US1 (TDD — write FIRST, verify they FAIL)

- [x] T014 [P] [US1] Write test for AnalyzeProjectTool in `src/test/java/com/extractor/mcp/tools/AnalyzeProjectToolTest.java` — success response structure, file generation, error cases [TS-001, TS-002, TS-003, TS-005, TS-006]

### Implementation for US1

- [x] T015 [US1] Create `src/main/java/com/extractor/mcp/tools/AnalyzeProjectTool.java` — handle analyze_project invocation, call AnalysisService.analyzeProject(), serialize to MCP response, write output files [TS-001, TS-005, TS-006]
- [x] T016 [US1] Implement path validation and error handling in AnalyzeProjectTool — invalid path, no Java sources, return MCP error with isError=true [TS-002, TS-003]
- [x] T017 [US1] Register AnalyzeProjectTool in McpServerMain tool registration

**Checkpoint**: Agent can invoke analyze_project and receive full results + disk files

---

## Phase 5: User Story 2 — Consultar un tipo específico de análisis (Priority: P2)

**Goal**: Agent queries individual tools (dependency graph, architecture, API contracts, component metrics)

**Independent Test**: Invoke each granular tool individually, verify response contains only requested data

### Tests for US2 (TDD — write FIRST, verify they FAIL)

- [x] T018 [P] [US2] Write test for GetDependencyGraphTool in `src/test/java/com/extractor/mcp/tools/GetDependencyGraphToolTest.java` [TS-010]
- [x] T019 [P] [US2] Write test for GetArchitectureTool in `src/test/java/com/extractor/mcp/tools/GetArchitectureToolTest.java` [TS-008, TS-011]
- [x] T020 [P] [US2] Write test for GetApiContractsTool in `src/test/java/com/extractor/mcp/tools/GetApiContractsToolTest.java` [TS-007, TS-012]
- [x] T021 [P] [US2] Write test for GetComponentMetricsTool in `src/test/java/com/extractor/mcp/tools/GetComponentMetricsToolTest.java` [TS-009, TS-013, TS-014]

### Implementation for US2

- [x] T022 [P] [US2] Create `src/main/java/com/extractor/mcp/tools/GetDependencyGraphTool.java` — call AnalysisService.getDependencyGraph(), return DependencyGraph JSON [TS-010]
- [x] T023 [P] [US2] Create `src/main/java/com/extractor/mcp/tools/GetArchitectureTool.java` — call AnalysisService.getArchitectureProposal() with optional minViability filter [TS-008, TS-011]
- [x] T024 [P] [US2] Create `src/main/java/com/extractor/mcp/tools/GetApiContractsTool.java` — call AnalysisService.getApiContracts(), return only endpoints + schemas [TS-007, TS-012]
- [x] T025 [P] [US2] Create `src/main/java/com/extractor/mcp/tools/GetComponentMetricsTool.java` — call AnalysisService.getComponentMetrics(), handle unknown component error [TS-009, TS-013, TS-014]
- [x] T026 [US2] Register all 4 granular tools in McpServerMain

**Checkpoint**: All 5 MCP tools functional — agent can query specific analysis types independently

---

## Phase 6: User Story 4 — Modo CLI preservado tras la actualización (Priority: P2)

**Goal**: CLI mode behavior identical to pre-MCP version

**Independent Test**: Run updated JAR with original CLI args, compare output with baseline

### Tests for US4 (TDD — write FIRST, verify they FAIL)

- [x] T027 [P] [US4] Write CLI/MCP parity test in `src/test/java/com/extractor/integration/CliMcpParityTest.java` — same project produces identical output via CLI and MCP [TS-020, TS-027]
- [x] T028 [P] [US4] Write help text test verifying both CLI and MCP usage are shown [TS-021]

### Implementation for US4

- [x] T029 [US4] Update help/usage text in MicroserviceInferenceMain to show both CLI and MCP modes [TS-021]
- [x] T030 [US4] Verify CLI output parity — run baseline comparison against pre-refactor output [TS-020]

**Checkpoint**: CLI users experience zero behavioral change after upgrade

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, edge cases, and hardening across all tools

- [x] T031 [P] Implement MCP error response sanitization — ensure no stack traces leak in any tool error response [TS-022]
- [x] T032 [P] Handle partial analysis failures — continue with warnings when individual files fail to parse [TS-023]
- [x] T033 [P] Handle paths with spaces and special characters across all tools [TS-024]
- [x] T034 [P] Handle non-existent output directory — create or report clear error [TS-025]
- [x] T035 Verify concurrent invocation independence — stateless AnalysisService, no shared mutable state [TS-026]
- [x] T036 Long-running analysis — verify server maintains stdio connection for analyses >30s [TS-004]
- [x] T037 Run quickstart.md validation scenarios end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup) ─────────────► Phase 2 (Foundational)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
              Phase 3 (US3)   Phase 4 (US1)    Phase 6 (US4)
              MCP Server      analyze_project   CLI compat
                    │               │
                    ▼               ▼
              Phase 5 (US2) ◄──────┘
              Granular tools
                    │
                    ▼
              Phase 7 (Polish)
```

### Critical Path

T001 → T002 → T003 → T005 → T006 → T007 → T010 → T011 → T015 → T016 → T017 → T031

### Parallel Opportunities

| After completion of... | These can run in parallel |
|------------------------|--------------------------|
| Phase 2 (Foundational) | T008, T014, T027, T028 (tests for US3, US1, US4) |
| Phase 3 (US3 server) | Phase 4 (US1) and Phase 6 (US4) |
| Phase 4 (US1 done) | T018, T019, T020, T021 (all US2 tests in parallel) |
| US2 tests written | T022, T023, T024, T025 (all US2 tools in parallel) |
| All stories complete | T031, T032, T033, T034 (all polish tasks in parallel) |

### Task-Level Dependencies

| Task | Depends on |
|------|-----------|
| T003 | T001, T002 |
| T005 | T004 |
| T006 | T005 |
| T007 | T005, T006 |
| T009 | T003 |
| T010 | T005, T009 |
| T011 | T010 |
| T015 | T010, T014 |
| T016 | T015 |
| T017 | T015 |
| T022, T023, T024, T025 | T010, T018-T021 |
| T026 | T022, T023, T024, T025 |
| T030 | T007 |
| T031-T036 | T026 |

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 37 |
| US1 tasks | 4 |
| US2 tasks | 9 |
| US3 tasks | 6 |
| US4 tasks | 4 |
| Setup/Foundational | 7 |
| Polish | 7 |
| Parallel opportunities | 5 batches |
| MVP scope | Phase 1-4 (Setup + Foundation + US3 + US1 = 17 tasks) |

## Notes

- [P] tasks = different files, no dependencies
- Constitution III mandates TDD: write tests FIRST (Red), implement (Green), refactor
- .feature files exist — task descriptions reference specific TS-IDs for traceability
- AnalysisService is the critical shared dependency — Phase 2 must complete before any story
- MVP = US3 (server starts) + US1 (analyze_project works) — delivers core value
