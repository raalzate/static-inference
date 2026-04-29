# MCP Tools Reference

The `static-inference` MCP server (Java MCP SDK v1.1.0, stdio transport) exposes five tools. All take a `projectPath` (absolute). The analyzer repo's `MCP.md` is the source of truth — this page mirrors the contract for offline use of the skill.

## `analyze_project`

> Description (from `ToolSchemas.java`): "Runs the full static analysis pipeline on a Java or .NET/C# project (auto-detected). Returns dependency graph, architecture proposals, and API contracts. Generates output files on disk."

```json
{
  "projectPath": "/abs/path",
  "outputDir": "/abs/path"   // optional; defaults to projectPath
}
```

Required: `projectPath`. Java projects must contain `pom.xml` or `build.gradle`; .NET projects must contain a `.sln` or `.csproj`. Type is auto-detected.

Response (JSON text content): `{ componentCount, edgeCount, proposalCount, endpointCount, filesGenerated[], summary }`.

Files emitted into `outputDir` (or `projectPath` when omitted):
- `output.json` — dependency graph
- `output_architecture.json` — proposals + project metadata
- `output_entrypoints.json` — API + messaging contracts

## `get_dependency_graph`

Read-only. No disk write.

```json
{ "projectPath": "/abs/path" }
```

Returns full `DependencyGraph` (`components`, `edges`, `api_contracts`, `meta`).

## `get_architecture_proposal`

Filtered proposals.

```json
{
  "projectPath": "/abs/path",
  "minViability": "Alta"   // "Alta" | "Media" | "Baja", optional
}
```

Returns `ConsolidatedArchitecture` (= `output_architecture.json` shape).

## `get_api_contracts`

```json
{ "projectPath": "/abs/path" }
```

Returns endpoints + messaging contracts (= `output_entrypoints.json` shape).

Detects:
- Java: `@RestController`, `@RequestMapping`, `@Path` (JAX-RS), Struts actions, servlets.
- .NET: `[ApiController]`, `[HttpGet|Post|Put|Delete]`.
- Messaging: Kafka, RabbitMQ, JMS, MassTransit, Azure Service Bus.

## `get_component_metrics`

```json
{
  "projectPath": "/abs/path",
  "componentId": "com.acme.OrderService"
}
```

Required: both fields. `componentId` format from `ToolSchemas.java`:
- Java: `com.example.service.UserService`
- .NET: `ECommerceApp.Services.ProductService`

Returns the full component record (CBO, LCOM, layer, tables, messaging role, call graph, code issues, etc.) — same shape as `output.json#components[*]`.

If `componentId` is unknown the tool returns the list of available component ids; re-call with an exact match.

## Tool selection cheat-sheet

| User intent | Tool |
|-------------|------|
| First analysis | `analyze_project` |
| "What can be extracted?" | `get_architecture_proposal` with `minViability="Alta"` |
| "What APIs does this expose?" | `get_api_contracts` |
| "Tell me about class X" | `get_component_metrics` |
| Read-only exploration | `get_dependency_graph` |

## Calling from Claude Code

When this MCP is registered in `.mcp.json`, tools appear as:
```
mcp__static-inference__analyze_project
mcp__static-inference__get_dependency_graph
mcp__static-inference__get_architecture_proposal
mcp__static-inference__get_api_contracts
mcp__static-inference__get_component_metrics
```

If tools are not visible, the server is not registered or not running — fall back to CLI via `scripts/run-analysis.sh`.

## Constraints

- `projectPath` must be absolute. Resolve relatives before calling.
- Java + .NET in same dir → ambiguous; call once per subdir.
- .NET projects require the `dotnet-analyzer` binary. Located by `DotNetToolLocator` in this order: sibling-of-JAR → `DOTNET_ANALYZER_PATH` → system `PATH`. See `build-setup.md`.
- The MCP server logs to stderr; stdout is reserved for the JSON-RPC channel.
