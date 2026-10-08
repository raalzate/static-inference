# Quickstart: .NET/C# Analyzer

## Prerequisites

- JDK 17+ (for the main tool)
- Maven 3.8+
- .NET 8.0 SDK (only for building the .NET analyzer from source)

## Building

### 1. Java fat JAR

```bash
mvn clean package -DskipTests
# Output: target/java-dependency-extractor.jar
```

### 2. .NET analyzer (single-file binary)

```bash
# macOS Apple Silicon
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target

# macOS Intel
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-x64 --self-contained -p:PublishSingleFile=true -o target

# Linux
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r linux-x64 --self-contained -p:PublishSingleFile=true -o target

# Windows
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r win-x64 --self-contained -p:PublishSingleFile=true -o target
```

The binary is published directly to `target/` alongside the JAR — no extra copy needed.

## Usage

### Analyze a .NET project (auto-detected)

```bash
java -jar target/java-dependency-extractor.jar /path/to/dotnet-project output.json
```

The tool detects `.sln` or `.csproj` in the directory and routes to the Roslyn bridge automatically.

### Analyze a Java project (unchanged)

```bash
java -jar target/java-dependency-extractor.jar /path/to/java-project output.json
```

### MCP server mode (auto-detects project type per invocation)

```bash
java -jar target/java-dependency-extractor.jar --mcp
```

### Demo project

A sample .NET e-commerce project is included:

```bash
java -jar target/java-dependency-extractor.jar \
  test-for-demo/dotnet-ecommerce/src/ECommerceApp \
  output-demo-dotnet.json
```

Make sure to run `dotnet restore` in the demo project first:

```bash
dotnet restore test-for-demo/dotnet-ecommerce/src/ECommerceApp
```

## Test Scenarios

### Scenario 1: Auto-detection selects .NET analyzer

```bash
java -jar target/java-dependency-extractor.jar \
  test-for-demo/dotnet-ecommerce/src/ECommerceApp output.json
```

**Expected**: log shows `Detected .NET project`, output contains `"source": "roslyn"` in `meta`.

### Scenario 2: Auto-detection selects Java analyzer (backward compat)

```bash
java -jar target/java-dependency-extractor.jar \
  test-for-demo/java-standar output-java.json
```

**Expected**: output identical to pre-.NET version, `"source": "spoon"`.

### Scenario 3: .NET project produces valid DependencyGraph

Analyze `test-for-demo/dotnet-ecommerce/src/ECommerceApp` and verify:

```bash
python3 -c "
import json
g = json.load(open('output.json'))
assert g['meta']['source'] == 'roslyn'
assert len(g['components']) > 0
assert len(g['edges']) > 0
assert len(g['api_contracts']['endpoints']) > 0
print(f'OK: {len(g[\"components\"])} components, {len(g[\"edges\"])} edges')
"
```

### Scenario 4: .NET project through MCP tools

1. Start MCP server: `java -jar target/java-dependency-extractor.jar --mcp`
2. Invoke `analyze_project` with .NET project path
3. Invoke `get_dependency_graph` — verify components and edges
4. Invoke `get_architecture_proposal` — verify microservice proposals
5. Invoke `get_api_contracts` — verify ASP.NET Core endpoints
6. Invoke `get_component_metrics` with `componentId: "ECommerceApp.Services.ProductService"` — verify CBO/LCOM/LOC

### Scenario 5: Ambiguous project type

```bash
mkdir /tmp/mixed && touch /tmp/mixed/pom.xml /tmp/mixed/App.csproj
java -jar target/java-dependency-extractor.jar /tmp/mixed output.json
```

**Expected**: error `Ambiguous project type: found both Java and .NET markers`.

### Scenario 6: dotnet-analyzer not found

Remove the binary and run on a .NET project:

**Expected**: `Could not find 'dotnet-analyzer' executable` with installation instructions.

### Scenario 7: Non-building .NET project (fallback mode)

Create a `.csproj` without running `dotnet restore` and with missing package references.

**Expected**: analysis completes with `[WARN] Using AdhocWorkspace fallback`, components extracted from syntax only.
