# Build Setup (Consumer Side)

Pre-flight requirements when a project wants to invoke the `static-inference` MCP.

The skill is consumed by *downstream* projects — projects whose source is being analyzed. The analyzer artifacts (JAR + optional .NET binary) are produced once by the `static-inference` repo and reused everywhere.

## MCP registration

Add a `.mcp.json` at the consumer project root:

```json
{
  "mcpServers": {
    "static-inference": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/abs/path/to/java-dependency-extractor.jar",
        "--mcp"
      ]
    }
  }
}
```

The JAR path must be absolute. The analyzer repo ships a `mcp-config.example.json` template.

## Where does the JAR come from?

It is built once in the `static-inference` analyzer repo:

```bash
# in the static-inference repo, not in the consumer project
mvn clean package -DskipTests
# produces target/java-dependency-extractor.jar
```

Consumer projects do NOT build the analyzer themselves. They consume the JAR. Common distribution patterns:

- Copy the JAR into a shared location (`/opt/static-inference/`, `~/.local/share/static-inference/`).
- Pin to a Git release artifact or internal Nexus/Artifactory.
- Symlink from a developer workstation's checkout (`ln -s /path/to/repo/target/...`).

## Java prerequisites

```bash
java -version   # 17+
```

## .NET analyzer (only for .NET / mixed targets)

The .NET binary `dotnet-analyzer` is also produced in the `static-inference` repo, per platform:

```bash
# macOS Apple Silicon
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target

# also: osx-x64, linux-x64, win-x64
```

`DotNetToolLocator.java` searches for the executable in this order:

1. **Sibling of the JAR** — file `dotnet-analyzer` next to `java-dependency-extractor.jar`. This is the recommended layout for distribution: ship the JAR + the platform-matching binary in the same directory.
2. **`DOTNET_ANALYZER_PATH`** environment variable (or system property). Can point to either the directory containing the executable or the executable itself.
3. **System `PATH`** — the binary named `dotnet-analyzer` resolved from `$PATH`.

If none resolves, the analyzer throws `IllegalStateException` with the search list.

The consumer must restore the target .NET project before analysis:

```bash
dotnet restore /path/to/consumer-dotnet-project
```

## Mixed projects

When a directory contains both `pom.xml` *and* `*.csproj`, the analyzer cannot disambiguate. Run analysis once per subdirectory.

## Verification

In Claude Code, after registering the server, run `/mcp` (or check the tools list). Expect:

```
mcp__static-inference__analyze_project
mcp__static-inference__get_dependency_graph
mcp__static-inference__get_architecture_proposal
mcp__static-inference__get_api_contracts
mcp__static-inference__get_component_metrics
```

If absent: the server failed to start. Reproduce manually (logging goes to stderr in MCP mode):

```bash
java -jar /abs/path/to/java-dependency-extractor.jar --mcp < /dev/null
```

## CLI fallback (when no MCP session)

`scripts/run-analysis.sh` drives the JAR directly. JAR resolution order:

1. `STATIC_INFERENCE_JAR=/abs/path/to/java-dependency-extractor.jar`
2. The path inside the project's `.mcp.json` `static-inference` server args
3. `STATIC_INFERENCE_HOME/target/java-dependency-extractor.jar`
4. `./target/java-dependency-extractor.jar` (only useful inside the analyzer repo)

If none resolve, the script errors with the resolution list.
