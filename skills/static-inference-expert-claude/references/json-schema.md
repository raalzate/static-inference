# JSON Schema Reference

Attribute catalog for the three JSON files emitted by `static-inference`. Verified against the analyzer source (`src/main/java/com/extractor`) and the demo outputs in `test-for-demo/`.

> **Source of truth**: the formal contract is the JSON Schema 2020-12 package in `schema/1.0.0/` (version in `schema/VERSION`, policy in `docs/SCHEMA.md`). This page is a reading guide; if it ever disagrees with `schema/1.0.0/`, the schema wins. Every artifact declares the contract version in `meta.schema_version` (`"1.0.0"`).

## Output files

| File | Origin | Top-level keys |
|------|--------|----------------|
| `output.json` | full dependency graph | `components`, `edges`, `api_contracts`, `meta` |
| `output_architecture.json` | microservice proposals | `meta`, `project_metadata`, `proposals`, `support_libraries`, `summary` |
| `output_entrypoints.json` | API contracts | `meta`, `endpoints`, `schemas` |

### Filename canonicality

The MCP tool `analyze_project` always writes the three filenames above, into `outputDir` (or `projectPath` if not provided) — see `AnalyzeProjectTool.java:68-78`.

The CLI entry point (`MicroserviceInferenceMain.java:68-75`) uses the user-supplied `<outputFile>` as the *graph* file, then derives the other two by replacing `.json` with `_architecture.json` / `_entrypoints.json`. Quirk: invoking the CLI with `output_architecture.json` as `<outputFile>` produces `output_architecture_architecture.json` and `output_architecture_entrypoints.json` — that is what you see in `test-for-demo/legacy-erp/`. Always treat the MCP filenames as canonical.

## File: `output.json`

### `components[*]`

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | Fully-qualified type name. Used as `componentId` for `get_component_metrics`. |
| `files` | string[] | Absolute paths |
| `loc` | int | Lines of code |
| `tables_used` | string[] | DB tables read/written (lowercased) |
| `sensitive_data` | bool | Touches secrets / PII |
| `secrets_references` | string[] | Free-form: `System.getProperty()`, env vars, `@Value("${...}")`, `@Resource`, etc. |
| `domain` | string\|null | Inferred bounded context (often null) |
| `layer` | string | `Controlador` \| `Negocio` \| `Persistencia` \| `Dominio` \| `Transferencia` \| `Web` \| `Compartida` \| `Datos` (deprecated alias for `Persistencia`). Source: `LayerClassifier.java`. |
| `calls_out` | string[] | Outgoing call targets |
| `calls_in` | string[] | Incoming callers |
| `external_dependencies` | string[] | Java: Maven coordinates `group:artifact:version`. .NET: NuGet ids. |
| `package_dependencies` | array | `[{ package_name, components[], count }]` |
| `code_issues` | array | `[{ type, severity, message, line, pattern }]` — see severities below |
| `extends` | string\|null | Superclass |
| `implements` | string[] | Interfaces |
| `messaging_type` | string\|null | `kafka` \| `rabbitmq` \| `jms` \| `masstransit` \| `azure-service-bus` |
| `messaging_role` | string\|null | `producer` \| `consumer` |
| `web_type` | string\|null | `spring-rest` \| `jaxrs` \| `struts` \| `servlet` \| `aspnet-mvc` \| `aspnet-core` |
| `web_role` | string\|null | `controller` \| `action` \| `endpoint` |
| `cbo` | int\|null | Coupling Between Objects |
| `lcom` | float\|null | Lack of Cohesion in Methods (0=high cohesion, 1=low) |
| `complexity_avg` | float\|null | Average cyclomatic complexity of the component's methods |
| `complexity_max` | int\|null | Maximum cyclomatic complexity of the component's methods |
| `ejb_type` | string\|null | EJB stereotype if any |
| `uses_jndi` | bool | JNDI lookup detected |
| `annotations` | string[] | Source annotations |
| `is_interface` | bool | Interface flag |
| `interface` | bool | **Deprecated** duplicate of `is_interface` (Java engine only; the raw .NET analyzer omits it). Read `is_interface`. |

The raw .NET analyzer omits properties whose value is null; the Java engine (and the .NET bridge) emit them as `null`. Treat absent and `null` the same.

### `code_issues[*]`

`{ type, severity, message, line, pattern }` — `type` ∈ `BUG_PATTERN` \| `CODE_STYLE` \| `SECURITY` \| `PERFORMANCE`; `severity` ∈ `INFO` \| `WARNING` \| `ERROR` \| `CRITICAL`; `pattern` is the rule id (open set); `line` is an int. There is no `rule` nor `location` field.

### `code_issues[*].severity`

| Severity | Emitter | Examples |
|----------|---------|----------|
| `ERROR` | .NET (`RoslynCodeAnalyzer.cs`) only | `DEPRECATED_THREAD_USAGE`, `ASYNC_VOID_METHOD` |
| `WARNING` | both | `string_comparison_operator`, code-smell patterns |
| `INFO` | both | stylistic / naming |

Java (Spoon) currently never emits `ERROR`. Apply [decision-rules R6](./decision-rules.md#r6--code-quality-weight) which weights both severities.

### `edges[*]`

| Field | Type | Notes |
|-------|------|-------|
| `from` | string | Component id |
| `to` | string | Component id |
| `type` | string | Open set, may be **composite** (comma separated, e.g. `call,use,injection-constructor`). Seen: `call`, `use`, `injection`, `injection-constructor`, `injection-field`, `implements`, `interface-impl`, `registration`, `repository`, `relation` |
| `weight` | int | Frequency |

### `api_contracts`

Inline subset of the entrypoints file. Use `output_entrypoints.json` as the canonical source — its shape is richer.

### `meta`

| Field | Notes |
|-------|-------|
| `source` | `spoon` (Java) \| `roslyn` (.NET) \| `roslyn-fallback` (.NET, Adhoc workspace with reduced fidelity). Use this to detect language, NOT a `language` key. |
| `schema_version` | Contract version (`"1.0.0"`), serialized right after `source` |
| `collected_at` | ISO-8601 UTC timestamp. **Opt-in**: only present when `STATIC_INFERENCE_COLLECTED_AT=true`; omitted by default so output is deterministic |
| `microservice_candidates` | map (often empty in graph; populated when the architecture stage runs inline) |
| `dependency_accuracy` | object\|null — populated when ground truth is supplied (CLI has no flag for it; usually null) |
| `decomposition_accuracy` | object\|null — same as above |

`output_architecture.json` and `output_entrypoints.json` carry their own `meta: { schema_version }` as first key.

## File: `output_architecture.json`

### `proposals[*]`

| Field | Type | Notes |
|-------|------|-------|
| `id` | int | Stable ordinal |
| `name` | string | Human label (Spanish) |
| `viability` | string | `Alta` \| `Media` \| `Baja` — primary gate. Computed by `ViabilityScorer`. |
| `clusters` | int[] | Cluster ids merged into this proposal |
| `components` | string[] | Component ids included |
| `metrics.size` | int | # components |
| `metrics.cohesion_avg` | float | 0–1 (higher = better) |
| `metrics.external_coupling` | float | 0–1 (higher = worse) |
| `metrics.internal_edge_density` | float | 0–1 |
| `metrics.data_jaccard` | float | 0–1 (shared data ratio) |
| `metrics.tables` | string[] | Aggregated tables |
| `metrics.sensitive` | bool | Any component sensitive |
| `metrics.tables_source` | string | Open set: `jpa` \| `orm` \| `sql-parse` \| `mixed` \| `none` |
| `signals.avg_cluster_size` | float | |
| `signals.cluster_count` | int | |
| `signals.total_components` | int | |
| `rationale` | string[] | Pre-formatted rationale lines (Spanish, with emoji prefixes) |
| `recommended_actions` | string[] | Suggested next steps (Spanish) |
| `tables` | object[] | `[{ name, component, source }]` — table name, owning component id and detection source (optional key) |
| `legacy_entrypoint` | object\|null | Entry point the legacy monolith exposes for this proposal (optional key; may be an object with only `type: "internal"`). See sub-fields below. |

### `legacy_entrypoint` sub-fields

Source: `LegacyEntrypoint.java`. Built by `MicroserviceRecommendationEngine.buildLegacyEntrypoint()`.

| Field | Type | Notes |
|-------|------|-------|
| `type` | string | `"rest"` \| `"messaging"` \| `"service"` \| `"internal"` — see type semantics below |
| `primary_entry_class` | string\|null | FQCN of the main controller / listener / service. Null for `"internal"` |
| `exposed_operations` | string[] | Human-readable operation summaries, e.g. `"POST /cuentas/crearCuenta"`. Empty for non-REST types |
| `messaging_channels` | string[]\|null | Queue / topic names. Only populated for `"messaging"` type |
| `endpoints` | object[] | Full `ApiEndpoint` descriptors — same shape as `output_entrypoints.json#endpoints[*]`. Only populated for `"rest"` type |
| `description` | string\|null | Free-text explanation |

**`type` semantics:**

| Value | Meaning | Populated fields |
|-------|---------|-----------------|
| `rest` | Entry via HTTP controllers | `primary_entry_class`, `exposed_operations`, `endpoints` |
| `messaging` | Entry via message listeners | `primary_entry_class`, `messaging_channels` |
| `service` | Entry via service interface, no REST/messaging | `primary_entry_class` |
| `internal` | No external entry point detected | none (all null/empty) |

**Cross-file join:** `legacy_entrypoint.endpoints[*].component_id` joins to `output.json#components[*].id` and to `output_entrypoints.json#endpoints[*].component_id`. Use `output_entrypoints.json` as the canonical source for full endpoint detail; `legacy_entrypoint.endpoints` is a pre-filtered subset scoped to this proposal's components.

### Viability scoring

```
score = 0.50 * cohesion_adj + 0.35 * (1 - external_coupling) + 0.15 * data_cohesion
cohesion_adj = 0.7 * weighted_avg_cohesion + 0.3 * internal_edge_density
size penalties:
  totalSize < 3   → score *= 0.6
  totalSize > 50 AND internal_density < 0.5 → score *= 0.7
viability = score >= 0.7 ? Alta : score >= 0.5 ? Media : Baja
```

Source: `ViabilityScorer.java:41-52`.

### `project_metadata`

May be `{}` or omit fields when there is nothing to report. Verified keys:

| Field | Notes |
|-------|-------|
| `external_dependencies` | Java: map of `groupId:artifactId` → resolved GAV. .NET: NuGet id → version. |
| `package_dependencies` | map of `package` → `{ components_count, total_dependencies_out, depends_on_packages[] }` |
| `total_components` | int |
| `total_loc` | int |
| `components_with_secrets` | int — number of components referencing secrets |
| `shared_domain` | string — name of the shared/cross-cutting domain (`"unknown"` when none) |
| `secrets_locations` | list of `{ path, line, kind, redacted }` (value always redacted; `kind` e.g. `API_KEY`, `JDBC_PASSWORD`). Empty list when no secrets. |

### `support_libraries`

Heuristic grouping of utility classes shared across proposals — a list of `{ id, name, clusters[], components[] }`.

### `summary`

String. Pre-formatted Spanish summary suitable for the MCP `analyze_project` response and for direct print.

## File: `output_entrypoints.json`

Top-level keys: `meta`, `endpoints`, `schemas`.

### `endpoints[*]`

| Field | Notes |
|-------|-------|
| `id` | Endpoint identifier (`Class.method`) |
| `path` | URL path or messaging topic |
| `method` | `GET` \| `POST` \| `PUT` \| `DELETE` \| `PATCH` \| `STRUTS_ACTION` \| `LISTENER` \| `WEBSERVICE` \| ... |
| `description` | Optional human label |
| `parameters` | `[{ name, in, type, required }]` (`in`: `path` \| `query` \| `header` \| `body`) |
| `request_body_schema` | Schema name (key of `schemas`) or null |
| `response_schema` | Schema name or null (may be absent in raw .NET output) |
| `component_id` | Owning component (joins to `output.json#components`) |

### `schemas`

Data schemas referenced by endpoint params/bodies, indexed by schema name: `{ type, properties: { <name>: { type } } }`.

## Cross-file joins

| Need | Join |
|------|------|
| Endpoints owned by a proposal | `endpoints[*].component_id ∈ proposals[*].components` |
| Secrets affecting a proposal | `components[id].secrets_references` / `sensitive_data` for `id ∈ proposal.components`; `project_metadata.secrets_locations[*].path` gives file locations |
| Layer mix per proposal | `components[id].layer for id ∈ proposal.components` (treat `Datos` as `Persistencia`) |
| Code-quality weight per proposal | `components[id].code_issues[*]` weighted per [R6](./decision-rules.md#r6--code-quality-weight) |
| Detect language | `output.json#meta.source` == `spoon` (Java) or `roslyn` / `roslyn-fallback` (.NET) |
