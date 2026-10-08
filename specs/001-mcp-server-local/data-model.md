# Data Model: MCP Server — Instalacion Local

**Branch**: `001-mcp-server-local` | **Date**: 2026-03-25

## Existing Domain Entities (unchanged)

These entities are the project's public JSON contracts (Constitution I — no changes allowed):

### DependencyGraph
- `components`: List&lt;Component&gt; — all Java types analyzed
- `edges`: List&lt;Edge&gt; — dependency relationships with weights
- `api_contracts`: ApiContracts — REST endpoints and schemas
- `meta`: Meta — analysis metadata

### Component
- `id`: String — fully qualified class name
- `files`: List&lt;String&gt; — source file paths
- `loc`: int — lines of code
- `tables_used`: List&lt;String&gt; — database tables accessed
- `sensitive_data`: List&lt;String&gt; — sensitive data references
- `domain`: String — business domain
- `layer`: String — architectural layer
- `calls_out` / `calls_in`: List&lt;String&gt; — call graph
- `cbo`: double — Coupling Between Objects
- `lcom`: double — Lack of Cohesion of Methods
- `annotations`: List&lt;String&gt;
- `ejb_type` / `web_type` / `messaging_type`: String

### ConsolidatedArchitecture
- `project_metadata`: ProjectMetadata
- `proposals`: List&lt;MicroserviceProposal&gt;
- `support_libraries`: List&lt;SupportLibrary&gt;
- `summary`: String

### ApiContracts
- `endpoints`: List&lt;ApiEndpoint&gt;
- `schemas`: Map&lt;String, ApiSchema&gt;

## New Entities

### AnalysisService (service layer)

Shared core that both CLI and MCP adapters delegate to.

**State**: Stateless — creates fresh analyzers per invocation.

**Methods**:
| Method | Input | Output |
|--------|-------|--------|
| `analyzeProject(Path)` | project root path | `AnalysisResult` |
| `getDependencyGraph(Path)` | project root path | `DependencyGraph` |
| `getArchitectureProposal(Path, String)` | project root, min viability | `ConsolidatedArchitecture` |
| `getApiContracts(Path)` | project root path | `DependencyGraph.ApiContracts` |
| `getComponentMetrics(Path, String)` | project root, component ID | `Component` (or null) |

### AnalysisResult (value object)

Wraps the full analysis pipeline output for `analyze_project`:

```
AnalysisResult
  ├── dependencyGraph: DependencyGraph
  ├── architecture: ConsolidatedArchitecture
  ├── apiContracts: DependencyGraph.ApiContracts
  ├── componentCount: int
  ├── edgeCount: int
  └── warnings: List<String>
```

## MCP Tool Input Schemas

### analyze_project

```json
{
  "type": "object",
  "properties": {
    "projectPath": {
      "type": "string",
      "description": "Absolute path to the Java project root directory"
    },
    "outputDir": {
      "type": "string",
      "description": "Directory to write output files (optional, defaults to project root)"
    }
  },
  "required": ["projectPath"]
}
```

### get_dependency_graph

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

### get_architecture_proposal

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
      "description": "Minimum viability threshold to filter proposals (optional)"
    }
  },
  "required": ["projectPath"]
}
```

### get_api_contracts

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

### get_component_metrics

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
      "description": "Fully qualified class name (e.g., 'com.example.UserService')"
    }
  },
  "required": ["projectPath", "componentId"]
}
```

## MCP Tool Response Format

All tools return `CallToolResult` with `TextContent` containing JSON. Example for `analyze_project`:

```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"componentCount\":42,\"edgeCount\":87,\"warnings\":[],\"filesGenerated\":[\"output.json\",\"output_architecture.json\",\"output_entrypoints.json\"],\"summary\":\"Analysis complete...\"}"
    }
  ]
}
```

Error responses follow MCP error format:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Project path '/invalid/path' does not exist or does not contain Java source files."
    }
  ],
  "isError": true
}
```

## Entity Relationships

```
MicroserviceInferenceMain ──delegates──▶ AnalysisService
McpServerMain ──registers──▶ Tool Handlers ──delegates──▶ AnalysisService
AnalysisService ──uses──▶ ProjectAnalyzer ──produces──▶ DependencyGraph
AnalysisService ──uses──▶ InferenceEngine ──produces──▶ MicroserviceCandidates
AnalysisService ──uses──▶ MicroserviceRecommendationEngine ──produces──▶ ConsolidatedArchitecture
AnalysisResult ──contains──▶ DependencyGraph + ConsolidatedArchitecture + ApiContracts
```
