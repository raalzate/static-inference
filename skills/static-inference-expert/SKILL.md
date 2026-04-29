---
name: static-inference-expert
description: >-
  Expert in the `static-inference` MCP server. Analyzes Java/.NET projects through the MCP tools (`analyze_project`, `get_dependency_graph`, `get_architecture_proposal`, `get_api_contracts`, `get_component_metrics`) and produces responsible, evidence-backed microservice extraction decisions grounded in the emitted JSON attributes (viability, cohesion, coupling, sensitive_data, code_issues, layer, tables_used, messaging_role, web_role).
  Use when user asks to "analyze this project", "evaluar microservicios", "qué extraer del monolito", "viabilidad de extracción", "decidir qué módulo migrar", or wants automated triage / batch reports from static-inference outputs.
license: MIT
metadata:
  version: "1.0.0"
  mcp_server: "static-inference"
  mcp_sdk: "1.1.0"
---

# Static-Inference Expert

Expert agent for the `static-inference` MCP. Drives end-to-end analysis of Java/.NET projects (Spring, Struts, Hibernate, EJB, .NET Core, etc.) and converts raw JSON output into responsible architectural decisions.

> **Distribution**: this skill is shipped *with the static-inference MCP*. End users copy this folder into `.claude/skills/static-inference-expert/` of the project they want analyzed. The skill itself does not need to live next to the analyzer source — it locates the JAR via `.mcp.json`, env vars, or explicit args. See [INSTALL.md](./INSTALL.md).

## When skill activates

User asks for any of:
- Project analysis run (Java / .NET / Spring / Struts / Hibernate / EJB / .NET Core).
- Microservice extraction recommendation.
- Viability ranking, refactor priority, risk surface.
- API contract / endpoint inventory.
- Component-level metrics (CBO, LCOM, layer, calls).
- Risk report (sensitive data, secrets, bug patterns).

## Pre-flight checks

Before invoking MCP tools, verify in the user's project:

1. **MCP server registered** — `.mcp.json` lists a `static-inference` server entry pointing to the analyzer JAR. If absent, guide the user through [references/build-setup.md](./references/build-setup.md) §"MCP registration".
2. **MCP tools visible** — in this Claude Code session look for `mcp__static-inference__analyze_project`. If missing, the server failed to start: surface the diagnostic in `references/build-setup.md` §"Verification" and STOP.
3. **Java 17+** on PATH — `java -version`.
4. **.NET binary** (only when target is a .NET project) — see `build-setup.md` §".NET analyzer".

Never run `mvn` / `dotnet publish` inside the user's project — those are analyzer-side builds. If the JAR is missing, ask the user to point `STATIC_INFERENCE_JAR` at an existing artifact or follow the analyzer's own build docs.

If any check fails, surface the fix and STOP. No analysis on a broken setup.

## Execution flow

### Step 1 — Resolve project target

User input rules:
- Empty → default to `pwd` (the user's project root) and confirm with the user before proceeding.
- Relative path → resolve to absolute via `pwd`.
- Ambiguous (Java + .NET in same dir) → ask the user which subdir per `references/build-setup.md` §"Mixed projects".

### Step 2 — Run analysis

Prefer **MCP tool path** when available in current session:

| Goal | MCP tool | Notes |
|------|----------|-------|
| Full pipeline + JSON files | `analyze_project` | Default. Use `outputDir` to keep workspace clean. |
| Graph only, no disk | `get_dependency_graph` | Read-only exploration. |
| Filtered proposals | `get_architecture_proposal` | Pass `minViability` to skip noise. |
| Endpoints / messaging | `get_api_contracts` | Confirms entrypoint surface. |
| Single component drill-down | `get_component_metrics` | Pass exact `componentId`. |

Fallback CLI path (no MCP session). Requires either `STATIC_INFERENCE_JAR` env var, a `.mcp.json` with the analyzer's JAR path, or `STATIC_INFERENCE_HOME` pointing to the analyzer repo:

```bash
bash .claude/skills/static-inference-expert/scripts/run-analysis.sh <projectPath> [outputDir]
```

### Step 3 — Load decision artifacts

Three JSON files drive every decision (canonical names emitted by the MCP `analyze_project` tool):

| File | Top keys | Used for |
|------|----------|----------|
| `output.json` | `components`, `edges`, `api_contracts`, `meta` | Component inventory, layer map, dependency edges, language detection (`meta.source`) |
| `output_architecture.json` | `proposals`, `project_metadata`, `support_libraries`, `summary` | Microservice proposals + viability |
| `output_entrypoints.json` | `endpoints`, `schemas` | API + messaging surface |

CLI mode derives the architecture/entrypoints filenames from the `<outputFile>` argument (replacing `.json` with `_architecture.json` / `_entrypoints.json`). If the user invoked the CLI with `output_architecture.json` as `<outputFile>`, you may see `output_architecture_architecture.json` / `output_architecture_entrypoints.json` siblings — read those instead. See `references/json-schema.md#filename-canonicality`.

See [references/json-schema.md](./references/json-schema.md) for the full attribute catalog.

### Step 4 — Apply decision rules

Use [references/decision-rules.md](./references/decision-rules.md). Hard rules (non-negotiable):

1. **Viability gate** — only `viability == "Alta"` proposals are extraction candidates without further work. `Media` → conditional. `Baja` → keep in monolith or merge.
2. **Sensitive data flag** — any proposal with `metrics.sensitive == true` MUST surface secrets remediation in the recommendation. Cross-reference `project_metadata.secrets_locations`.
3. **Cohesion floor** — `metrics.cohesion_avg < 0.5` blocks extraction unless `data_jaccard >= 0.7` (shared-data justification).
4. **Coupling ceiling** — `metrics.external_coupling > 0.6` → flag as fragile-boundary, do not promote even if viability says Alta. Require manual review.
5. **Layer mixing** — proposal whose components span > 2 of the *core* layers {Controlador, Web, Negocio, Persistencia} (excluding cross-cutting `Compartida`/`Dominio`/`Transferencia`; `Datos` folds into `Persistencia`) → mark as needs-split.
6. **Code-quality weight** — for each component sum `code_issues[*]` by severity (`ERROR`=3, `WARNING`=1, `INFO`=0). If total > 10 → block extraction; document tech-debt task first. (Java emits only `INFO`/`WARNING`; `ERROR` only appears for .NET targets.)
7. **Entrypoint coverage** — every Alta-viability proposal must own ≥1 endpoint from `output_entrypoints.json` OR ≥1 messaging listener (`messaging_role == "consumer"`). Otherwise it is a library, not a service — recommend as shared module instead.

### Step 5 — Produce decision report

Output structure (markdown to user):

```
# Análisis: <project name>

## Resumen
- Componentes: <n> | LOC: <n> | Edges: <n> | Lenguaje: <meta.source>
- Propuestas: Alta=<a> Media=<m> Baja=<b>
- Endpoints: <e> | Listeners: <l>
- Riesgos críticos: <componentes con sensitive_data + R6 weight excedido>

## Candidatos a extracción (Alta viabilidad)
| # | Nombre | Componentes | Cohesión | Acopl. ext. | Tablas | Endpoints | Decisión |

## Conditional (Media viabilidad)
... incluye qué falta para promover a Alta ...

## Mantener en monolito (Baja)
... razón en una línea por propuesta ...

## Riesgos transversales
- Secrets: archivos + propuestas afectadas (de `secrets_locations` o fallback `sensitive_data`)
- Code issues con peso alto: top 5 con file:line (severity desc)
- Layer mixing: propuestas afectadas (capas core mezcladas)

## Próximos pasos accionables
1. ...
2. ...
```

NEVER hide a Baja proposal silently — list it with reason. NEVER recommend extraction without citing the JSON attribute that justifies it.

### Step 6 — Persist evidence

Save the report to `<outputDir>/static-inference-report.md` so future runs can diff. Do not overwrite if user passed `--no-persist`.

## Batch / hooks

For repeated runs (CI, watching multiple repos):

- **Batch script** — `scripts/triage-proposals.sh <archJson>` emits a CSV of `(id,name,viability,size,cohesion,coupling,recommendation)`.
- **Risk extractor** — `scripts/risk-report.sh <graphJson> <archJson>` emits sensitive components + ERROR-severity issues + sensitive proposals (NB: argument order is graph first).
- **Stop hook** — drop `hooks/post-analysis.sh` into `.claude/settings.json` `hooks.Stop` if user wants auto-report after every analysis.

Hook example for `.claude/settings.json`:
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "mcp__static-inference__analyze_project",
        "hooks": [
          { "type": "command", "command": "bash .claude/skills/static-inference-expert/hooks/post-analysis.sh" }
        ]
      }
    ]
  }
}
```

## Responsible decision principles

1. **No extraction without evidence** — every recommendation cites a JSON attribute path.
2. **Surface trade-offs explicitly** — if cohesion is low but data sharing is high, say both.
3. **Never silence Baja** — list with one-line reason; user decides if rules apply.
4. **Sensitive data is a stop sign** — extraction proposals touching secrets get an explicit remediation step before promotion.
5. **Defer to user on ambiguity** — when rules conflict (e.g., Alta viability + high coupling), ask before recommending.
6. **Outputs are claims, not truth** — note that static analysis misses runtime behavior; recommend dynamic validation for any Alta candidate before cutover.

## References

- [decision-rules.md](./references/decision-rules.md) — full thresholds + scoring
- [json-schema.md](./references/json-schema.md) — JSON attribute catalog
- [mcp-tools.md](./references/mcp-tools.md) — MCP tool reference + examples
- [build-setup.md](./references/build-setup.md) — environment prep

## Scripts

- [run-analysis.sh](./scripts/run-analysis.sh) — CLI fallback runner
- [triage-proposals.sh](./scripts/triage-proposals.sh) — batch CSV triage
- [risk-report.sh](./scripts/risk-report.sh) — secrets + bug pattern surface

## Hooks

- [post-analysis.sh](./hooks/post-analysis.sh) — auto report generator
