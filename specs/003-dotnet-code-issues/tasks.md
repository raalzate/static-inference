# Tasks: 003-dotnet-code-issues — RoslynCodeAnalyzer

**Branch**: `003-dotnet-code-issues` | **Generated**: 2026-04-07
**Feature Stage**: testified | **TDD**: MANDATORY (constitución Principio III)

## Implementation Strategy

El orden de implementación sigue estrictamente el ciclo **RED → GREEN** de TDD:
1. Crear fixtures y skeleton para que el código compile
2. Escribir tests fallando (RED)
3. Implementar regla por regla hasta que pasen (GREEN)
4. Integrar en el pipeline y verificar E2E

**Entidad fundacional**: `RoslynCodeAnalyzer.cs` — es prerequisito para US1, US2 y US3.

---

## Phase 1 — Setup

- [x] T001 Create C# fixture files with bug patterns in `src/test/dotnet/DotNetAnalyzer.Tests/Fixtures/CodeIssuesSample/` (EmptyCatch.cs, GenericException.cs, ConsoleLogging.cs, StringEquality.cs, HardcodedSecret.cs, DeprecatedThread.cs, MissingSwitchDefault.cs, CleanCode.cs)
- [x] T002 [P] Create `RoslynCodeAnalyzer.cs` skeleton with empty `Analyze(Compilation compilation, List<Component> components)` method in `src/main/dotnet/DotNetAnalyzer/Analysis/RoslynCodeAnalyzer.cs`

---

## Phase 2 — Foundational (TDD: escribir tests RED)

> Depends on: T001, T002

- [x] T003 Create `RoslynCodeAnalyzerTests.cs` with all 17 unit test stubs in `src/test/dotnet/DotNetAnalyzer.Tests/Analysis/RoslynCodeAnalyzerTests.cs` — tests deben compilar y fallar (RED) [TS-001, TS-002, TS-003, TS-004, TS-005, TS-006, TS-007, TS-008, TS-009, TS-010, TS-011, TS-012, TS-013, TS-014, TS-015, TS-016, TS-017]

---

## Phase 3 — US1: Bug Pattern Detection (P1)

> Depends on: T003 | All T004–T010 are [P] — implementar en paralelo

- [x] T004 [P] [US1] Implement `EMPTY_CATCH_BLOCK` detection in `RoslynCodeAnalyzer.cs` via `VisitCatchClause` — check `node.Block.Statements.Count == 0` [TS-001, TS-002]
- [x] T005 [P] [US1] Implement `CATCH_GENERIC_EXCEPTION` detection in `RoslynCodeAnalyzer.cs` via `VisitCatchClause` — check type name is "Exception" or "SystemException" [TS-003, TS-004]
- [x] T006 [P] [US1] Implement `CONSOLE_LOGGING` detection in `RoslynCodeAnalyzer.cs` via `VisitInvocationExpression` — check `Console.Write` or `Console.WriteLine` [TS-005, TS-006, TS-007]
- [x] T007 [P] [US1] Implement `STRING_EQUALITY_OPERATOR` detection in `RoslynCodeAnalyzer.cs` via `VisitInvocationExpression` — check call to `ReferenceEquals` where arguments involve string literals or string-typed names [TS-008, TS-009]
- [x] T008 [P] [US1] Implement `HARDCODED_SECRET_IN_CODE` detection in `RoslynCodeAnalyzer.cs` via `VisitVariableDeclarator` — check variable name contains "password", "apikey", "secret", "token" (case-insensitive) and value is a non-empty string literal [TS-010, TS-011, TS-012]
- [x] T009 [P] [US1] Implement `DEPRECATED_THREAD_USAGE` detection in `RoslynCodeAnalyzer.cs` via `VisitInvocationExpression` — check member access `Abort` on `Thread` type (syntactic match) [TS-013, TS-014]
- [x] T010 [P] [US1] Implement `MISSING_SWITCH_DEFAULT` detection in `RoslynCodeAnalyzer.cs` via `VisitSwitchStatement` — check no section contains `DefaultSwitchLabel` [TS-015, TS-016]
- [x] T011 [US1] Verify clean code fixture produces zero issues — run `dotnet test --filter "CleanComponent"` and confirm TS-017 passes [TS-017]

---

## Phase 4 — US2: Schema & Pipeline Integration (P2)

> Depends on: T011

- [x] T012 [P] [US2] Write schema validation tests in `RoslynCodeAnalyzerTests.cs` — verify all fields (rule, severity, message, location) are non-null and severity is in allowed enum [TS-018, TS-019]
- [x] T013 [US2] Add `Pass 11` to `ProjectAnalyzer.cs`: instantiate `_codeAnalyzer = new RoslynCodeAnalyzer()` and call `_codeAnalyzer.Analyze(compilation, components)` after Pass 10 in `src/main/dotnet/DotNetAnalyzer/Analysis/ProjectAnalyzer.cs`
- [x] T014 [US2] Write pipeline integration test in `src/test/dotnet/DotNetAnalyzer.Tests/Integration/FullPipelineTests.cs` — add test that verifies `code_issues` is non-empty after full analysis of a project with known patterns [TS-020, TS-021]
- [x] T015 [P] [US2] Implement deterministic sort in `RoslynCodeAnalyzer.Analyze()` — sort `component.CodeIssues` by `location` string, then by `rule` as tiebreaker, before returning [TS-022, TS-023]
- [x] T016 [US2] Update `specs/002-dotnet-roslyn-analyzer/contracts/dependency-graph-schema.md` — add CodeIssue field definitions (rule, severity, message, location) with example JSON

---

## Phase 5 — US3: Graceful Degradation (P3)

> Depends on: T011

- [x] T017 [P] [US3] Write graceful degradation tests in `RoslynCodeAnalyzerTests.cs` — empty component, empty source file, comments-only file; write pipeline test for unparseable file scenario [TS-024, TS-025, TS-026, TS-027]
- [x] T018 [US3] Implement per-file try/catch in `RoslynCodeAnalyzer.Analyze()` — wrap each file analysis in try/catch, emit `Console.Error.WriteLine("[WARN] ...")` on failure and continue, ensuring component.CodeIssues remains valid list [TS-024, TS-025]

---

## Phase 6 — Polish & Verification

> Depends on: T014, T015, T018

- [x] T019 Run `dotnet test` for all `RoslynCodeAnalyzerTests` — verify all 27 TS-XXX scenarios pass with 0 failures
- [x] T020 Run E2E analysis on `test-for-demo/dotnet-ecommerce` and verify SC-001 — `code_issues` contains at least 1 real finding across all components
- [x] T021 Verify determinism SC-003 — run analysis on `test-for-demo/dotnet-ecommerce` 3 times, compare `jq '[.components[].code_issues]' | sha256sum` output is identical

---

## Dependency Graph

```
T001 ──┐
       ├── T003 ──┬── T004 [P]
T002 ──┘          ├── T005 [P]
                  ├── T006 [P]
                  ├── T007 [P]
                  ├── T008 [P]
                  ├── T009 [P]
                  └── T010 [P]
                        │
                        └── T011
                              ├── T012 [P] ──────────────────┐
                              ├── T013 ── T014               │
                              ├── T015 [P]                   ├── T019 ── T020 ── T021
                              ├── T016                       │
                              ├── T017 [P]                   │
                              └── T018 ─────────────────────┘
```

**Critical path**: T001 → T003 → T004…T010 → T011 → T013 → T014 → T019 → T020 → T021

**Parallel batches**:
- Batch A (Phase 1): T001, T002 en paralelo
- Batch B (Phase 3): T004, T005, T006, T007, T008, T009, T010 en paralelo
- Batch C (Phase 4/5): T012, T015 en paralelo; T017 en paralelo con T012, T015

## MVP Scope

Para un MVP funcional (SC-001 + SC-002): completar T001 → T011 (Phases 1–3).
Esto produce un analizador con los 7 patrones pero sin integración en el pipeline.

Para producción (todos los SC): completar todas las fases.

## Summary

| Fase | Tareas | Paralelas | Historia |
|------|--------|-----------|---------|
| Setup | 2 | 1 | — |
| Foundational | 1 | 0 | — |
| US1 Bug Patterns | 8 | 7 | US1 P1 |
| US2 Schema+Pipeline | 5 | 2 | US2 P2 |
| US3 Graceful Degradation | 2 | 1 | US3 P3 |
| Polish | 3 | 0 | — |
| **Total** | **21** | **11** | |
