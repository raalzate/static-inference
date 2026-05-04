---
name: static-inference-expert-claude
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

> **Distribution**: this skill is shipped *with the static-inference MCP*. End users copy this folder into `.claude/skills/static-inference-expert-claude/` of the project they want analyzed. The skill itself does not need to live next to the analyzer source — it locates the JAR via `.mcp.json`, env vars, or explicit args. See [INSTALL.md](./INSTALL.md).

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
bash .claude/skills/static-inference-expert-claude/scripts/run-analysis.sh <projectPath> [outputDir]
```

### Step 2b — Partial scan detection and tiered auto-remediation

Immediately after receiving the `analyze_project` result, inspect the response **before** loading artifacts.

#### Language detection
```bash
# .NET present?
find <projectPath> -name "*.csproj" | grep -v /obj/ | grep -v /bin/ | head -1

# Java present?
find <projectPath> -name "pom.xml" -o -name "build.gradle" -o -name "build.gradle.kts" | grep -v /target/ | head -1
```

#### Detection signals

Trigger remediation when ANY of the following is true:

| Signal | Language | Meaning |
|--------|----------|---------|
| `meta.source == "roslyn-fallback"` | .NET | Binary not found; Roslyn parsed only project-scope files |
| `componentCount == 0` | any | No entry point found in given path |
| `componentCount < source_file_count / 3` | .NET | Scan missed sibling directories |
| `componentCount < source_file_count / 5` | Java | Scan missed source directories |

**Source file count:**
```bash
# .NET
find <projectPath>/.. -name "*.cs" | grep -v /obj/ | grep -v /bin/ | wc -l

# Java
find <projectPath> -name "*.java" | grep -v /target/ | grep -v /build/ | wc -l
```

If no signal fires → scan is complete, proceed to Step 3.

---

#### Tiered remediation protocol

Execute **silently** — all changes are temporary and reversible. Track every created/modified file in `pending_restore = []` from the start. Run tiers in order; stop at first success. On failure at any tier, restore all pending files before escalating.

---

**Tier 1 — Descriptor patch** (least invasive — extend existing build descriptor)

**.NET**

1. Locate the `.csproj`:
   ```bash
   find <projectPath> -maxdepth 3 -name "*.csproj" | grep -v /obj/ | grep -v /bin/ | head -1
   ```
2. Find orphan source dirs (have `.cs` files but are outside the `.csproj` directory):
   ```bash
   find <root> -name "*.cs" | grep -v /obj/ | grep -v /bin/ | grep -v <csproj_dir> \
     | sed 's|/[^/]*\.cs$||' | sort -u
   ```
   If none → scan was complete, skip remediation.
3. Read `.csproj`, save original to `pending_restore`. Append before `</Project>`:
   ```xml
   <!-- static-inference-expert: Tier 1 temporary compile scope expansion -->
   <ItemGroup>
     <Compile Include="../<orphan_dir_1>/**/*.cs" />
     <Compile Include="../<orphan_dir_2>/**/*.cs" />
   </ItemGroup>
   ```
4. Re-run `mcp__static-inference__analyze_project` with the same `projectPath`.
5. Evaluate:
   - `componentCount > original` → **Tier 1 succeeded**. Proceed to Step 3; restore `.csproj` after artifact load (Step F).
   - Unchanged or scan fails → restore `.csproj` from `pending_restore`, escalate to **Tier 2**.

**Java**

1. Check for build descriptor at `projectPath`:
   ```bash
   find <projectPath> -maxdepth 1 -name "pom.xml" -o -name "build.gradle" -o -name "build.gradle.kts"
   ```
2. If **no descriptor** → search upward for topmost `pom.xml`:
   ```bash
   d=<projectPath>; while [ "$d" != "/" ]; do [ -f "$d/pom.xml" ] && echo "$d" && break; d=$(dirname "$d"); done
   ```
   Re-run with found path. Improved → **Tier 1 succeeded**. Not found → escalate to **Tier 2**.
3. If **descriptor present** but `componentCount` low:
   a. Check multi-module Maven:
      ```bash
      grep -oP '(?<=<module>)[^<]+' <projectPath>/pom.xml
      ```
      If modules found → run `analyze_project` on each subdir with its own `pom.xml`:
      ```bash
      find <projectPath> -mindepth 2 -maxdepth 2 -name "pom.xml" | grep -v /target/
      ```
      Collect per-module results. Any subdir yields components → **Tier 1 succeeded** (report as multi-module).
   b. Check Gradle subprojects:
      ```bash
      find <projectPath> -name "settings.gradle" -o -name "settings.gradle.kts" | head -3
      ```
      If `settings.gradle` includes subprojects → run `analyze_project` on each. Any yield → **Tier 1 succeeded**.
   c. Check non-standard source layout:
      ```bash
      find <projectPath> -name "*.java" | grep -v /target/ | grep -v src/main/java | head -10
      ```
      If files found outside `src/main/java` → escalate to **Tier 2** (Spoon expects standard layout).
   d. If descriptor present + 0 `.java` files → **STOP**: empty project.
4. All sub-checks fail → escalate to **Tier 2**.

---

**Tier 2 — Synthetic aggregator** (when Tier 1 fails — create temporary project structure)

**.NET**

1. Enumerate all `.csproj` in the source tree:
   ```bash
   find <root> -name "*.csproj" | grep -v /obj/ | grep -v /bin/
   ```
2. Find orphan dirs (contain `.cs` files but have no `.csproj`):
   ```bash
   # All dirs with .cs files
   find <root> -name "*.cs" | grep -v /obj/ | grep -v /bin/ | xargs -I{} dirname {} | sort -u
   # Subtract dirs already owned by a .csproj
   ```
3. For each orphan dir, create a minimal stub `.csproj` (suffix `_si_stub` for cleanup):
   ```xml
   <Project Sdk="Microsoft.NET.Sdk">
     <PropertyGroup><TargetFramework>net8.0</TargetFramework></PropertyGroup>
   </Project>
   ```
   Save as `<orphan_dir>/<DirName>_si_stub.csproj`. Add to `pending_restore`.
4. Create `_si_aggregate.sln` at `<root>` referencing all `.csproj` (originals + stubs):
   ```
   Microsoft Visual Studio Solution File, Format Version 12.00
   Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "<Name>", "<rel_path>", "{<GUID>}"
   EndProject
   ```
   Use `uuidgen` for GUIDs. Add `_si_aggregate.sln` to `pending_restore`.
5. Re-run `analyze_project` with `projectPath = <root>`.
6. Evaluate:
   - `componentCount > original` → **Tier 2 succeeded**. Proceed to Step 3; cleanup after artifact load.
   - Unchanged or fails → restore all from `pending_restore`, escalate to **Tier 3**.
7. **Cleanup** (after Step 3): delete `_si_aggregate.sln` and all `*_si_stub.csproj`.

**Java**

1. Find all unique dirs that directly contain `.java` files:
   ```bash
   find <root> -name "*.java" | grep -v /target/ | grep -v /build/ \
     | xargs -I{} dirname {} | sort -u
   ```
2. Run `analyze_project` on each dir independently. Collect all `components` + `edges` arrays.
3. Merge results: union of components, union of edges. Emit a merged report.
4. Any dir yields components → **Tier 2 succeeded** (note: "Análisis multi-directorio — resultados fusionados").
5. All fail → escalate to **Tier 3**.

---

**Tier 3 — Unrecoverable partial scan**

Restore all files in `pending_restore`. Do **not** produce extraction recommendations. Output:

```
❌ Scan parcial irrecuperable en <path>.

Tiers intentados: Tier 1 (descriptor patch) → Tier 2 (synthetic aggregator)
Componentes detectados: <n> / archivos fuente estimados: <m>

Estructura requerida:
- .NET: crear <nombre>.sln con un .csproj por módulo; referenciar proyectos entre sí
- Java: agregar pom.xml en cada módulo; definir padre con <modules> o settings.gradle con include()
```

---

#### Restore protocol

Keep `pending_restore = []` throughout remediation. Append path + original content for every modified file and path for every synthetic file created. After Step 3 artifact loading (or on any tier failure): restore originals, delete synthetics.

> **Why restore?** Tier 1 and Tier 2 artifacts are diagnostic tools, not build fixes. The user decides whether to adopt them permanently.

#### Report header when remediation ran

Always include in the Step 5 output:

```
## ⚠️ Cobertura del análisis
- Tier ejecutado: <Tier 1 / Tier 2 / irrecuperable>
- Componentes antes: <n> → después: <m>
- Archivos temporales: <lista> → restaurados ✅
- Recomendación: <formalizar estructura según Tier 3 message>
```

#### Zero-component guard

If after all tiers `componentCount` is still 0, **STOP**. Do not produce a decision report. Output Tier 3 message above.

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
7. **Entrypoint coverage** — every Alta-viability proposal must own ≥1 endpoint from `output_entrypoints.json` OR ≥1 messaging listener (`messaging_role == "consumer"`). Shortcut: if `proposal.legacy_entrypoint` is present, read `legacy_entrypoint.type` directly — any value other than `"internal"` satisfies coverage without the manual join. `"internal"` → reclassify as shared module.
8. **Entrypoint-type alignment** — use `legacy_entrypoint.type` to tailor the migration contract: `"rest"` → list `legacy_entrypoint.exposed_operations` + flag null schemas; `"messaging"` → list `legacy_entrypoint.messaging_channels` + flag channel ownership; `"service"` → document service interface; `"internal"` → no external contract. See [R11 in decision-rules.md](./references/decision-rules.md#r11--entrypoint-type-alignment).
9. **Cyclomatic complexity gate** — R12 never blocks extraction. If any component has `complexity_max > 15` → recommend extraction AND add a mandatory prereq refactor task (CRITICAL). If `10 < complexity_max ≤ 15` → recommend extraction AND flag refactor as recommended before cutover (WARNING). Always cite the specific method from `code_issues[pattern=HIGH_CYCLOMATIC_COMPLEXITY]`. Skip if `complexity_max` is `null` (output predates analyzer v2.1). See [R12 in decision-rules.md](./references/decision-rules.md#r12--cyclomatic-complexity-gate).

### Step 5 — Produce decision report

Output structure (markdown to user):

```
# Análisis: <project name>

<!-- SOLO incluir si se ejecutó remediación de scan parcial (Step 2b) -->
## ⚠️ Cobertura del análisis
- Scan inicial: <componentCount_original> componentes detectados (roslyn-fallback)
- Remediación aplicada: se expandió `<csproj_path>` con <N> directorios adicionales
- Scan final: <componentCount_final> componentes — cobertura ampliada
- .csproj restaurado al estado original ✅
- Recomendación: formalizar estructura con .sln + .csproj por módulo (ver Próximos pasos)
<!-- FIN bloque remediación -->

## Resumen
- Componentes: <n> | LOC: <n> | Edges: <n> | Lenguaje: <meta.source>
- Propuestas: Alta=<a> Media=<m> Baja=<b>
- Endpoints: <e> | Listeners: <l>
- Riesgos críticos: <componentes con sensitive_data + R6 weight excedido + R12 CC bloqueante>

## Candidatos a extracción (Alta viabilidad)
| # | Nombre | Componentes | Cohesión | Acopl. ext. | Tablas | CC max | Tipo entrada | Operaciones expuestas | Decisión |

## Conditional (Media viabilidad)
... incluye qué falta para promover a Alta ...

## Mantener en monolito (Baja)
... razón en una línea por propuesta ...

## Riesgos transversales
- Secrets: archivos + propuestas afectadas (de `secrets_locations` o fallback `sensitive_data`)
- Code issues con peso alto: top 5 con file:line (severity desc)
- Complejidad ciclomática alta: componentes con `complexity_max > 10`, método + CC + línea (de `code_issues[pattern=HIGH_CYCLOMATIC_COMPLEXITY]`)
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
          { "type": "command", "command": "bash .claude/skills/static-inference-expert-claude/hooks/post-analysis.sh" }
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
