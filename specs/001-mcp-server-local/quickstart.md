# Quickstart: MCP Server — Instalacion Local

**Branch**: `001-mcp-server-local` | **Date**: 2026-03-25

## Prerequisites

- JDK 17+ installed
- Maven 3.8+
- .NET 8.0 SDK (only if analyzing .NET/C# projects)
- A sample project to analyze — Java or .NET (the tool itself can be used as a test subject)

## Build

### Java fat JAR

```bash
mvn clean package -DskipTests
```

Produces: `target/java-dependency-extractor.jar`

### .NET analyzer (required for .NET projects)

```bash
# macOS Apple Silicon
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target

# macOS Intel / Linux / Windows: replace osx-arm64 with osx-x64, linux-x64, or win-x64
```

Publishes `dotnet-analyzer` binary directly to `target/` alongside the JAR.

## CLI Mode

```bash
# Java project (auto-detected via pom.xml / build.gradle)
java -jar target/java-dependency-extractor.jar /path/to/java-project output.json

# .NET project (auto-detected via .sln / .csproj)
java -jar target/java-dependency-extractor.jar /path/to/dotnet-project output.json
```

Output files:
- `output.json` — dependency graph (`meta.source`: `"spoon"` for Java, `"roslyn"` for .NET)
- `output_architecture.json` — architecture proposals
- `output_entrypoints.json` — API contracts

## MCP Mode

```bash
java -jar target/java-dependency-extractor.jar --mcp
```

The server starts and communicates via stdio (stdin/stdout). Logs go to stderr.
Project type is auto-detected per invocation — the same server handles both Java and .NET projects.

## Agent Configuration

### Claude Code / Claude Desktop

Add to `.mcp.json` in the project root:

```json
{
  "mcpServers": {
    "static-inference": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/java-dependency-extractor.jar", "--mcp"]
    }
  }
}
```

The `dotnet-analyzer` binary must be in the same `target/` directory as the JAR.

### Generic MCP Client

Any MCP-compatible agent can connect via stdio transport by launching the JAR with `--mcp`.

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `analyze_project` | Full pipeline: graph + architecture + API contracts. Generates files on disk. |
| `get_dependency_graph` | Components and edges only. |
| `get_architecture_proposal` | Microservice proposals with viability scores. Filterable by `minViability`. |
| `get_api_contracts` | REST endpoints and schemas. Java: `@RestController`. .NET: `[ApiController]`. |
| `get_component_metrics` | CBO, LCOM, LOC, layer, tables, messaging role for a specific type. |

## Test Scenarios

### Scenario 1: Verify Java CLI still works

```bash
java -jar target/java-dependency-extractor.jar . output.json

python3 -c "import json; json.load(open('output.json'))"
python3 -c "import json; json.load(open('output_architecture.json'))"
python3 -c "import json; json.load(open('output_entrypoints.json'))"
```

**Expected**: Same output as before the MCP integration. `meta.source = "spoon"`.

### Scenario 2: Verify MCP server starts and lists tools

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | \
java -jar target/java-dependency-extractor.jar --mcp 2>/dev/null
```

**Expected**: JSON response listing 5 tools.

### Scenario 3: Analyze Java project via MCP

Configure the MCP server in Claude Code, then ask:

> "Analyze the Java project at /path/to/project and tell me about the architecture"

**Expected**: Agent invokes `analyze_project`, receives structured results, generates output files.

### Scenario 4: Analyze .NET project via MCP

> "Analyze the .NET project at test-for-demo/dotnet-ecommerce/src/ECommerceApp"

**Expected**: Agent invokes `analyze_project`, output contains `meta.source = "roslyn"`, lists ASP.NET Core controllers and services.

### Scenario 5: Query API contracts for .NET project

> "Show me the REST endpoints for the .NET project at test-for-demo/dotnet-ecommerce/src/ECommerceApp"

**Expected**: Agent invokes `get_api_contracts`, returns endpoints from `ProductsController` and `OrdersController`.

### Scenario 6: Component metrics lookup — .NET type

> "What are the metrics for ECommerceApp.Services.ProductService?"

**Expected**: Agent invokes `get_component_metrics`, receives CBO, LCOM, layer=`"Negocio"`, messaging_role=`"publisher"`.

### Scenario 7: Filtered architecture proposals

> "Show me only the high-viability microservice proposals for the project"

**Expected**: Agent invokes `get_architecture_proposal` with `minViability: "Alta"`.

### Scenario 8: Error handling

> "Analyze the project at /nonexistent/path"

**Expected**: Clear error: path does not exist.

> "Analyze a project with both pom.xml and .csproj"

**Expected**: Clear error: `Ambiguous project type`.
