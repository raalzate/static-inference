# MCP Tool Contracts

**Branch**: `001-mcp-server-local` | **Date**: 2026-03-25

## Tool Registration

The MCP server registers 5 tools following the MCP specification. Each tool has a name, description, and JSON schema for input parameters.

---

## Tool 1: analyze_project (FR-003)

**Name**: `analyze_project`
**Description**: Runs the full static analysis pipeline on a Java project. Returns dependency graph, architecture proposals, and API contracts. Generates output files on disk.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory containing pom.xml or build.gradle"
    },
    "outputDir": {
      "type": "string",
      "description": "Directory to write output JSON files. Defaults to project root if omitted."
    }
  },
  "required": ["projectPath"]
}
```

**Success Response** (CallToolResult, isError=false):
```json
{
  "componentCount": 42,
  "edgeCount": 87,
  "proposalCount": 3,
  "endpointCount": 12,
  "filesGenerated": [
    "/path/output.json",
    "/path/output_architecture.json",
    "/path/output_entrypoints.json"
  ],
  "summary": "Architecture analysis summary text..."
}
```

**Error Response** (CallToolResult, isError=true):
- `"Project path '/x' does not exist"`
- `"No Java source files found in '/x'"`
- `"Analysis failed for component X: reason. Partial results available."`

---

## Tool 2: get_dependency_graph (FR-004)

**Name**: `get_dependency_graph`
**Description**: Returns the dependency graph of a Java project, including all components and their relationships. Does not generate files on disk.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory"
    }
  },
  "required": ["projectPath"]
}
```

**Success Response**: Full `DependencyGraph` JSON (components + edges + api_contracts + meta).

---

## Tool 3: get_architecture_proposal (FR-005)

**Name**: `get_architecture_proposal`
**Description**: Returns microservice decomposition proposals with viability scores, metrics, and recommended actions. Optionally filtered by minimum viability level.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory"
    },
    "minViability": {
      "type": "string",
      "enum": ["Alta", "Media", "Baja"],
      "description": "Minimum viability threshold. Only proposals meeting or exceeding this level are returned."
    }
  },
  "required": ["projectPath"]
}
```

**Success Response**: `ConsolidatedArchitecture` JSON, with `proposals` filtered by viability if `minViability` is provided.

**Viability ordering**: Alta > Media > Baja.

---

## Tool 4: get_api_contracts (FR-006)

**Name**: `get_api_contracts`
**Description**: Returns REST endpoints and messaging contracts discovered in the Java project. Does not include dependency graph or architecture data.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory"
    }
  },
  "required": ["projectPath"]
}
```

**Success Response**: `ApiContracts` JSON (endpoints + schemas only).

---

## Tool 5: get_component_metrics (FR-007)

**Name**: `get_component_metrics`
**Description**: Returns detailed metrics for a specific component (Java class) including coupling (CBO), cohesion (LCOM), architectural layer, database tables, and call graph.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory"
    },
    "componentId": {
      "type": "string",
      "description": "Fully qualified class name, e.g., 'com.example.service.UserService'"
    }
  },
  "required": ["projectPath", "componentId"]
}
```

**Success Response**: Single `Component` JSON with all fields.

**Error Response**:
- `"Component 'com.example.Foo' not found in project. Available components: [list]"`

---

## Cross-Cutting Contracts

### Error Format (FR-008)

All errors return `CallToolResult` with `isError=true` and a single `TextContent` with a human-readable message. No stack traces are exposed.

### Tool Listing (FR-011)

The MCP server responds to `tools/list` with all 5 tools, each including name, description, and complete input schema as defined above.
