# Implementation Plan: MCP Server — Instalacion Local

**Branch**: `001-mcp-server-local` | **Date**: 2026-03-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-mcp-server-local/spec.md`

## Summary

Convert the existing CLI-only static analysis tool into a dual-mode application (CLI + MCP server) by extracting a shared `AnalysisService` core, implementing the MCP protocol via the official Java SDK with stdio transport, and exposing 5 tools (`analyze_project`, `get_dependency_graph`, `get_architecture_proposal`, `get_api_contracts`, `get_component_metrics`). The tool remains a single fat JAR with mode selection via command-line argument.

## Technical Context

**Language/Version**: Java 17 (upgrade from 11; required by MCP Java SDK)
**Primary Dependencies**: Spoon 10.4.2 (AST), Jackson 2.15.2 (project JSON), MCP Java SDK 1.1.0 (protocol), SLF4J 2.0.9
**Storage**: N/A (file-based output only)
**Testing**: JUnit 5.10.0 (unit + contract tests)
**Target Platform**: Any OS with JDK 17+ (macOS, Linux, Windows)
**Project Type**: Single project (Maven JAR)
**Performance Goals**: Full analysis in <5 minutes for projects up to 200 classes (SC-001)
**Constraints**: Single fat JAR, no runtime infrastructure beyond JVM, stdio transport only
**Scale/Scope**: Local developer tool, single concurrent analysis per invocation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How Satisfied |
|-----------|--------|---------------|
| I. Backward Compatibility | PASS | CLI entry point preserved unchanged; MCP is a separate mode activated by `--mcp` flag |
| II. Single Source of Truth | PASS | New `AnalysisService` becomes shared core; CLI and MCP adapters are thin delegates |
| III. Test-First Development | PASS | TDD mandated; contract tests for MCP tool schemas, unit tests for AnalysisService |
| IV. Deterministic Output | PASS | Same core logic; MCP responses are JSON serialization of same models |
| V. Graceful Degradation | PASS | MCP tool handlers wrap errors into MCP error responses with context |
| VI. Minimal Footprint | PASS | MCP SDK is the only new dependency; single JAR preserved |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Fat JAR (dual mode)                   │
│                                                          │
│  ┌─────────────┐          ┌──────────────────────┐       │
│  │  CLI Adapter │          │   MCP Server Adapter  │       │
│  │  (Main.java) │          │  (McpServerMain.java) │       │
│  │  args → core │          │  stdio ↔ tools → core │       │
│  └──────┬──────┘          └──────────┬───────────┘       │
│         │                            │                    │
│         ▼                            ▼                    │
│  ┌──────────────────────────────────────────────┐        │
│  │            AnalysisService (core)             │        │
│  │  analyzeProject() → DependencyGraph           │        │
│  │  getArchitectureProposal() → Consolidated...  │        │
│  │  getApiContracts() → ApiContracts             │        │
│  │  getComponentMetrics() → Component            │        │
│  │  getDependencyGraph() → DependencyGraph       │        │
│  └──────────────────────┬───────────────────────┘        │
│                         │                                 │
│         ┌───────────────┼───────────────┐                │
│         ▼               ▼               ▼                │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐      │
│  │ ProjectAna- │ │  Inference   │ │  Recommend-  │      │
│  │ lyzer       │ │  Engine      │ │  ationEngine │      │
│  └─────────────┘ └──────────────┘ └──────────────┘      │
│                                                          │
└──────────────────────────────────────────────────────────┘
         ▲                              ▲
         │ stdio                        │ args
    LLM Agent                      CLI User
```

## Project Structure

### Documentation (this feature)

```text
specs/001-mcp-server-local/
  plan.md              # This file
  research.md          # Technology decisions and rationale
  data-model.md        # MCP tool schemas and entity relationships
  quickstart.md        # Setup and test scenarios
  contracts/           # MCP tool input/output contracts
```

### Source Code (repository root)

```text
src/
  main/java/com/extractor/
    MicroserviceInferenceMain.java    # CLI adapter (existing, minimal changes)
    mcp/
      McpServerMain.java             # MCP server entry point and tool registration
      tools/
        AnalyzeProjectTool.java      # analyze_project tool handler
        GetDependencyGraphTool.java  # get_dependency_graph tool handler
        GetArchitectureTool.java     # get_architecture_proposal tool handler
        GetApiContractsTool.java     # get_api_contracts tool handler
        GetComponentMetricsTool.java # get_component_metrics tool handler
      ToolSchemas.java               # JSON schema definitions for tool inputs
    service/
      AnalysisService.java           # Shared core: orchestrates analysis pipeline
      AnalysisResult.java            # Unified result wrapper with metadata
    analyzer/                        # (existing, unchanged)
    inference/                       # (existing, unchanged)
    model/                           # (existing, unchanged)
    constants/                       # (existing, unchanged)
    utils/                           # (existing, unchanged)

  test/java/com/extractor/
    service/
      AnalysisServiceTest.java       # Unit tests for shared core
    mcp/
      McpServerContractTest.java     # Contract tests for MCP tool schemas
      tools/
        AnalyzeProjectToolTest.java
        GetDependencyGraphToolTest.java
        GetArchitectureToolTest.java
        GetApiContractsToolTest.java
        GetComponentMetricsToolTest.java
    integration/
      CliMcpParityTest.java          # CLI vs MCP output parity (SC-003, SC-005)
```

**Structure Decision**: Single project with new `mcp/` and `service/` packages under the existing `com.extractor` namespace. The MCP layer is isolated from the analysis core. No monorepo or multi-module needed — constitution principle VI (Minimal Footprint) favors simplicity.

## Key Technical Decisions

### 1. Java 17 Upgrade

The MCP Java SDK 1.1.0 requires Java 17+. This is the only viable option — older SDK versions (0.x) lack stability and API completeness. Java 17 is an LTS release; Spoon 10.4.2 supports it. See `research.md` for full analysis.

### 2. MCP Java SDK with Jackson Coexistence

The `mcp` convenience artifact (1.1.0) bundles Jackson 3 (`tools.jackson.*` namespace). The existing project uses Jackson 2 (`com.fasterxml.jackson.*`). These coexist on the classpath without conflict because Jackson 3 uses a different package namespace. The MCP SDK handles its own serialization internally; our models continue using Jackson 2.

### 3. Entry Point Routing

The existing `MicroserviceInferenceMain` becomes the CLI adapter (unchanged behavior). A new `McpServerMain` handles MCP mode. The fat JAR's manifest `Main-Class` remains `MicroserviceInferenceMain`, which detects `--mcp` flag and delegates to `McpServerMain`. This preserves backward compatibility (Constitution I).

### 4. AnalysisService as Shared Core

Extract the orchestration logic currently in `MicroserviceInferenceMain.main()` into `AnalysisService`. Both CLI and MCP adapters delegate to it. This satisfies Constitution II (Single Source of Truth) without touching existing analyzer/inference internals.

### 5. MCP Tool Granularity

Five tools matching spec requirements FR-003 through FR-007:

| Tool | Input | Output | Maps to |
|------|-------|--------|---------|
| `analyze_project` | projectPath, outputDir? | DependencyGraph + Architecture + Contracts + files on disk | FR-003 |
| `get_dependency_graph` | projectPath | DependencyGraph (components + edges) | FR-004 |
| `get_architecture_proposal` | projectPath, minViability? | ConsolidatedArchitecture (filtered) | FR-005 |
| `get_api_contracts` | projectPath | ApiContracts (endpoints + schemas) | FR-006 |
| `get_component_metrics` | projectPath, componentId | Component metrics (CBO, LCOM, layer, tables) | FR-007 |

## Dependency Changes (pom.xml)

```xml
<!-- ADD: MCP Java SDK (includes Jackson 3 internally) -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- CHANGE: Java version 11 → 17 -->
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

No other dependency changes. Existing Jackson 2, Spoon, Lombok, SLF4J remain as-is.

## Implementation Strategy

### Phase 1: Core Extraction
1. Create `AnalysisService` extracting logic from `MicroserviceInferenceMain`
2. Refactor `MicroserviceInferenceMain` to delegate to `AnalysisService`
3. Verify CLI behavior is identical (Constitution I)

### Phase 2: MCP Server Shell
4. Upgrade Java to 17 in pom.xml
5. Add MCP SDK dependency
6. Create `McpServerMain` with stdio transport and empty tool registrations
7. Add `--mcp` flag routing in `MicroserviceInferenceMain`

### Phase 3: Tool Implementation
8. Implement `analyze_project` tool (full pipeline + file output)
9. Implement `get_dependency_graph` tool
10. Implement `get_architecture_proposal` tool (with viability filter)
11. Implement `get_api_contracts` tool
12. Implement `get_component_metrics` tool

### Phase 4: Error Handling & Polish
13. MCP error responses with actionable messages (FR-008)
14. Example `.mcp.json` configuration (FR-013)
15. Help/usage text for both modes (US-4 scenario 2)

## Complexity Tracking

No violations detected. The architecture adds one new dependency (MCP SDK) and one new package (`mcp/`) without violating any constitutional principle.
