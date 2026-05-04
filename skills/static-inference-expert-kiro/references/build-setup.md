# Build Setup (Consumer Side — Kiro)

Pre-flight requirements when a project wants to invoke the `static-inference` MCP from Kiro.

The skill is consumed by *downstream* projects — projects whose source is being analyzed. The analyzer artifacts (JAR + optional .NET binary) are produced once by the `static-inference` repo and reused everywhere.

## MCP registration

Create or edit `.kiro/settings/mcp.json` at the consumer project root (or use `~/.kiro/settings/mcp.json` for user-global scope):

```json
{
  "mcpServers": {
    "static-inference": {
      "command": "java",
      "args": [
        "-jar",
        "/abs/path/to/java-dependency-extractor.jar",
        "--mcp"
      ],
      "disabled": false,
      "autoApprove": [
        "analyze_project",
        "get_dependency_graph",
        "get_architecture_proposal",
        "get_api_contracts",
        "get_component_metrics"
      ]
    }
  }
}
```

The JAR path must be absolute. Ship `dotnet-analyzer` in the same directory as the JAR so it is found automatically. The `autoApprove` list skips per-call confirmation — use `"*"` to approve all tools at once, or list them individually.

> **Kiro-specific fields:**
> - `disabled` — set `true` to deactivate without removing the entry.
> - `autoApprove` — tool names to auto-approve, or `"*"` for all.
> - `disabledTools` — tool names to exclude from the session.

## Where does the JAR come from?

It is built once in the `static-inference` analyzer repo (use `mvnw` if `mvn` is not on PATH):

```bash
# in the static-inference repo, not in the consumer project
./mvnw clean package -DskipTests
# or: mvn clean package -DskipTests
# produces target/java-dependency-extractor.jar
#     and  target/java-dependency-extractor-1.0.0.jar (versioned copy, same content)
```

Consumer projects do NOT build the analyzer themselves. They consume the JAR. Common distribution patterns:

- Copy `target/java-dependency-extractor.jar` (and `target/dotnet-analyzer` if needed) to a shared location (`/opt/static-inference/`, `~/.local/share/static-inference/`).
- Pin to a Git release artifact or internal Nexus/Artifactory.
- Symlink from a developer workstation's checkout (`ln -s /path/to/repo/target/java-dependency-extractor.jar`).

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

In Kiro, after registering the server, open the MCP Servers tab in the Kiro panel. The `static-inference` server should appear as active with these tools visible:

```
analyze_project
get_dependency_graph
get_architecture_proposal
get_api_contracts
get_component_metrics
```

If the server appears failed, reproduce manually (logging goes to stderr in MCP mode):

```bash
java -jar /abs/path/to/java-dependency-extractor.jar --mcp < /dev/null
```

## CLI fallback (when no MCP session)

`scripts/run-analysis.sh` drives the JAR directly. JAR resolution order:

1. `STATIC_INFERENCE_JAR=/abs/path/to/java-dependency-extractor.jar`
2. The path inside the project's `.kiro/settings/mcp.json` `static-inference` server args
3. `STATIC_INFERENCE_HOME/target/java-dependency-extractor.jar`
4. `./target/java-dependency-extractor.jar` (only useful inside the analyzer repo)

If none resolve, the script errors with the resolution list.
