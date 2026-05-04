# Install — `static-inference-expert-kiro` skill

Drop this skill into a downstream project that wants to consume the `static-inference` MCP from **Kiro**.

## 1. Copy the skill folder

```bash
# from your consumer project root
mkdir -p .kiro/skills
cp -R /path/to/static-inference/skills/static-inference-expert-kiro .kiro/skills/
```

Layout afterwards:

```
<your-project>/
├── .kiro/
│   ├── skills/
│   │   └── static-inference-expert-kiro/
│   │       ├── SKILL.md
│   │       ├── INSTALL.md
│   │       ├── references/
│   │       └── scripts/
│   └── settings/
│       └── mcp.json   ← step 2
└── ...
```

## 2. Obtain the analyzer artifacts

Build once inside the `static-inference` repo (use the Maven wrapper if `mvn` is not on PATH):

```bash
# inside the static-inference repo
./mvnw clean package -DskipTests          # produces target/java-dependency-extractor.jar
# or: mvn clean package -DskipTests

# .NET support (only needed to analyze .NET/C# projects)
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target
# replace -r with: osx-x64 | linux-x64 | win-x64
```

Distribute the `target/` folder to consumers (or copy just the binaries to a shared path):

```
target/
├── java-dependency-extractor.jar        # main analyzer JAR
├── java-dependency-extractor-1.0.0.jar  # versioned copy (same content)
└── dotnet-analyzer                      # .NET bridge binary (platform-specific)
```

Both JARs are identical — use `java-dependency-extractor.jar` for stable references.

## 3. Register the MCP server

Create or edit `.kiro/settings/mcp.json` at the project root:

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

The JAR path must be absolute. Ship `dotnet-analyzer` in the same directory as the JAR so it is found automatically. The `autoApprove` list skips the per-call confirmation prompt — remove entries you want to confirm manually.

> **Workspace vs user scope**: `.kiro/settings/mcp.json` applies to this project only; `~/.kiro/settings/mcp.json` applies globally across all workspaces.

## 4. Enable MCP in Kiro

Open Kiro Settings (`Cmd+,` on macOS, `Ctrl+,` on Windows/Linux) and ensure **MCP** support is enabled in the MCP Servers tab.

## 5. Verify

Open the project in Kiro and check the MCP Servers tab in the Kiro panel. You should see `static-inference` listed as active with these tools:

```
analyze_project
get_dependency_graph
get_architecture_proposal
get_api_contracts
get_component_metrics
```

If the server appears as failed: reproduce manually to see stderr output:

```bash
java -jar /abs/path/to/java-dependency-extractor.jar --mcp < /dev/null
```

## 6. Run an analysis

Just ask: *"analiza este proyecto y dime qué microservicios extraer"*. The skill activates, runs `analyze_project`, applies the decision rules in `references/decision-rules.md`, and produces an evidence-backed report.

## CLI fallback

If the MCP server is unavailable, scripts work standalone:

```bash
# locate JAR via STATIC_INFERENCE_JAR env, .kiro/settings/mcp.json, or STATIC_INFERENCE_HOME
export STATIC_INFERENCE_JAR=/abs/path/java-dependency-extractor.jar

bash .kiro/skills/static-inference-expert-kiro/scripts/run-analysis.sh \
  /abs/path/to/your/project ./analysis-output

bash .kiro/skills/static-inference-expert-kiro/scripts/triage-proposals.sh \
  ./analysis-output/output_architecture.json --md

bash .kiro/skills/static-inference-expert-kiro/scripts/risk-report.sh \
  ./analysis-output/output.json \
  ./analysis-output/output_architecture.json
```

## Requirements

| Component | Where | Version |
|-----------|-------|---------|
| Java | consumer project | 17+ |
| `dotnet-analyzer` binary | only if analyzing .NET | platform-specific (osx-arm64, linux-x64, ...) |
| `jq` | for batch scripts | any recent |

The `dotnet-analyzer` binary is located by `DotNetToolLocator` in this order: (1) sibling of the JAR, (2) `DOTNET_ANALYZER_PATH` env var, (3) system `PATH`. Recommended layout: ship the JAR and the platform-matching binary in the same directory.

See `references/build-setup.md` for full prerequisites and analyzer artifact distribution patterns.
