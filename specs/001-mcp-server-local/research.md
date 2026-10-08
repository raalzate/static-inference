# Research: MCP Server — Instalacion Local

**Branch**: `001-mcp-server-local` | **Date**: 2026-03-25

## Decision 1: MCP Java SDK Selection

**Question**: Which library to use for implementing the MCP server protocol?

**Decision**: Official MCP Java SDK `io.modelcontextprotocol.sdk:mcp` v1.1.0

**Rationale**:
- Official SDK maintained by the MCP specification authors in collaboration with Spring AI
- Provides `McpSyncServer` with `StdioServerTransportProvider` — exactly what we need
- Synchronous API matches our existing blocking analysis pipeline
- Compliant with MCP specification 2025-06-18
- MIT licensed

**Alternatives Considered**:
1. **Manual JSON-RPC over stdio**: Too much protocol boilerplate; error-prone spec compliance
2. **Kotlin MCP SDK**: Adds Kotlin runtime to a pure Java project; violates Constitution VI
3. **Spring Boot MCP Starter**: Massive dependency footprint; violates Constitution VI (Minimal Footprint)

**Source**: https://github.com/modelcontextprotocol/java-sdk

## Decision 2: Java Version Upgrade (11 → 17)

**Question**: Can we keep Java 11, or must we upgrade?

**Decision**: Upgrade to Java 17

**Rationale**:
- MCP Java SDK 1.1.0 requires Java 17+ (hard requirement)
- No Java-11-compatible version of the SDK exists with stable API
- Java 17 is an LTS release (supported through 2029+)
- Spoon 10.4.2 supports Java 17 compilation and analysis
- All existing dependencies (Jackson 2.15.2, Lombok 1.18.30, SLF4J 2.0.9) support Java 17
- JUnit 5.10.0 supports Java 17

**Risk**: Users must have JDK 17+ installed. Mitigated by: JDK 17 has been available since September 2021 and is the current recommended LTS.

**Alternatives Considered**:
1. **Stay on Java 11, use SDK 0.x**: Unstable API, missing features (tool schemas), no ongoing maintenance
2. **Implement MCP protocol manually on Java 11**: High effort, maintenance burden, spec compliance risk

## Decision 3: Jackson 2 + Jackson 3 Coexistence

**Question**: MCP SDK bundles Jackson 3 (`tools.jackson.*`), but our project uses Jackson 2 (`com.fasterxml.jackson.*`). Conflict?

**Decision**: No conflict — both coexist on the classpath

**Rationale**:
- Jackson 3 uses package namespace `tools.jackson.*`, completely separate from Jackson 2's `com.fasterxml.jackson.*`
- MCP SDK's internal serialization uses Jackson 3 via `mcp-json-jackson3` module
- Our existing models, Spoon output, and all analysis code continues using Jackson 2
- The boundary is clean: MCP SDK handles protocol serialization; our code handles domain serialization
- No shared ObjectMapper instances between the two libraries

**Alternatives Considered**:
1. **Migrate entire project to Jackson 3**: High risk, touches every model class, Spoon may not support it
2. **Use `mcp-core` + `mcp-json-jackson2`**: The jackson2 binding is only available at v0.14.1 (old API); not compatible with mcp-core 1.1.0

## Decision 4: Entry Point Strategy

**Question**: How to route between CLI and MCP modes in a single JAR?

**Decision**: Flag-based routing in `MicroserviceInferenceMain`

**Rationale**:
- `MicroserviceInferenceMain` remains the JAR's `Main-Class` (backward compatible)
- If first argument is `--mcp`, delegate to `McpServerMain.start()`
- Otherwise, existing CLI behavior is unchanged
- Users can run: `java -jar tool.jar --mcp` for MCP mode
- Users can run: `java -jar tool.jar /path output.json` for CLI mode (same as before)

**Alternatives Considered**:
1. **Separate JARs for CLI and MCP**: Violates Constitution VI (single artifact)
2. **Environment variable selection**: Less discoverable than a CLI flag
3. **New Main-Class for MCP, separate JAR profile**: Complicates build, confusing for users

## Decision 5: AnalysisService Extraction Pattern

**Question**: How to create the shared core without breaking existing code?

**Decision**: Extract orchestration into `AnalysisService`, refactor `MicroserviceInferenceMain` to call it

**Rationale**:
- `MicroserviceInferenceMain.main()` currently does: analyze → infer → recommend → serialize → write files
- Extract these steps into `AnalysisService` methods that return domain objects
- `MicroserviceInferenceMain` becomes: parse args → call service → write files → print summary
- `McpServerMain` tools become: parse MCP request → call service → serialize to MCP response
- Existing `ProjectAnalyzer`, `InferenceEngine`, `MicroserviceRecommendationEngine` remain untouched

**Key Methods**:
```
AnalysisService:
  analyzeProject(Path) → AnalysisResult (graph + architecture + contracts)
  getDependencyGraph(Path) → DependencyGraph
  getArchitectureProposal(Path, String minViability) → ConsolidatedArchitecture
  getApiContracts(Path) → DependencyGraph.ApiContracts
  getComponentMetrics(Path, String componentId) → Component
```

## Decision 6: No Spring / No Framework

**Question**: Should we use Spring Boot for the MCP server?

**Decision**: No. Plain Java with MCP SDK's built-in stdio transport.

**Rationale**:
- Constitution VI mandates minimal footprint
- Spring Boot would add ~30MB+ to the fat JAR
- Our server is stateless — receives request, runs analysis, returns result
- No need for dependency injection, web server, or application context
- The MCP SDK's `StdioServerTransportProvider` + `McpSyncServer` is sufficient

## Decision 7: Logging Strategy for MCP Mode

**Question**: How to handle logging when stdio is used for MCP protocol communication?

**Decision**: Redirect SLF4J output to stderr in MCP mode

**Rationale**:
- MCP stdio transport uses stdin/stdout for protocol messages
- Any logging to stdout would corrupt the protocol stream
- SLF4J Simple logger can be configured to output to stderr via `simpleLogger.logFile=System.err`
- CLI mode continues logging to stdout as before (no change)
