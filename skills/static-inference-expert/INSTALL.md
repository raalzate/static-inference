# Install — `static-inference-expert` skill

Drop this skill into a downstream project that wants to consume the `static-inference` MCP.

## 1. Copy the skill folder

```bash
# from your consumer project root
mkdir -p .claude/skills
cp -R /path/to/static-inference/skills/static-inference-expert .claude/skills/
```

Layout afterwards:

```
<your-project>/
├── .claude/
│   └── skills/
│       └── static-inference-expert/
│           ├── SKILL.md
│           ├── INSTALL.md
│           ├── references/
│           ├── scripts/
│           └── hooks/
└── .mcp.json   ← step 2
```

## 2. Register the MCP server

Create `.mcp.json` at the project root (or merge into an existing one):

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

Replace the JAR path with the artifact you got from the analyzer team / release.

## 3. Verify

Open the project in Claude Code, start a session, then check the tools list. You should see:

```
mcp__static-inference__analyze_project
mcp__static-inference__get_dependency_graph
mcp__static-inference__get_architecture_proposal
mcp__static-inference__get_api_contracts
mcp__static-inference__get_component_metrics
```

Plus a skill named `static-inference-expert` in the available skills.

## 4. (Optional) Auto-report hook

Add to `.claude/settings.json` so every analysis writes `triage.md` + `risk-report.md` next to the JSON outputs:

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "mcp__static-inference__analyze_project",
        "hooks": [
          {
            "type": "command",
            "command": "bash .claude/skills/static-inference-expert/hooks/post-analysis.sh"
          }
        ]
      }
    ]
  }
}
```

## 5. Run an analysis

Just ask: *"analiza este proyecto y dime qué microservicios extraer"*. The skill activates, runs `analyze_project`, applies the decision rules in `references/decision-rules.md`, and produces an evidence-backed report.

## CLI fallback

If the MCP server is unavailable, scripts work standalone:

```bash
# locate JAR via STATIC_INFERENCE_JAR env, .mcp.json, or STATIC_INFERENCE_HOME
export STATIC_INFERENCE_JAR=/abs/path/java-dependency-extractor.jar

bash .claude/skills/static-inference-expert/scripts/run-analysis.sh \
  /abs/path/to/your/project ./analysis-output

bash .claude/skills/static-inference-expert/scripts/triage-proposals.sh \
  ./analysis-output/output_architecture.json --md

bash .claude/skills/static-inference-expert/scripts/risk-report.sh \
  ./analysis-output/output.json \
  ./analysis-output/output_architecture.json
```

## Requirements

| Component | Where | Version |
|-----------|-------|---------|
| Java | consumer project | 17+ |
| `dotnet-analyzer` binary | only if analyzing .NET | platform-specific (osx-arm64, linux-x64, ...) |
| `jq` | for batch scripts + hook | any recent |

The `dotnet-analyzer` binary is located by `DotNetToolLocator` in this order: (1) sibling of the JAR, (2) `DOTNET_ANALYZER_PATH` env var, (3) system `PATH`. Recommended layout: ship the JAR and the platform-matching binary in the same directory.

See `references/build-setup.md` for full prerequisites and analyzer artifact distribution patterns.
