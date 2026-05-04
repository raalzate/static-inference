# JSON Schema Reference

Attribute catalog for the three JSON files emitted by `static-inference`. Verified against the analyzer source (`src/main/java/com/extractor`) and the demo outputs in `test-for-demo/`.

## Output files

| File | Origin | Top-level keys |
|------|--------|----------------|
| `output.json` | full dependency graph | `components`, `edges`, `api_contracts`, `meta` |
| `output_architecture.json` | microservice proposals | `proposals`, `project_metadata`, `support_libraries`, `summary` |
| `output_entrypoints.json` | API contracts | `endpoints`, `schemas` |

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
| `cbo` | int | Coupling Between Objects |
| `lcom` | float\|null | Lack of Cohesion in Methods (0=high cohesion, 1=low) |
| `ejb_type` | string\|null | EJB stereotype if any |
| `uses_jndi` | bool | JNDI lookup detected |
| `annotations` | string[] | Source annotations |
| `interface` / `is_interface` | bool | Interface flag (both keys are emitted, same value) |

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
| `type` | string | `calls` \| `extends` \| `implements` \| `uses` |
| `weight` | int | Frequency |

### `api_contracts`

Inline subset of the entrypoints file. Use `output_entrypoints.json` as the canonical source — its shape is richer.

### `meta`

| Field | Notes |
|-------|-------|
| `source` | `spoon` (Java path) \| `dotnet` (Roslyn bridge). Use this to detect language, NOT a `language` key. |
| `collected_at` | ISO-8601 UTC timestamp |
| `microservice_candidates` | map (often empty in graph; populated when the architecture stage runs inline) |
| `dependency_accuracy` | float\|null — populated when ground truth is supplied (CLI has no flag for it; usually null) |
| `decomposition_accuracy` | float\|null — same as above |

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
| `metrics.tables_source` | string | `jpa` \| `sql-parse` \| `mixed` |
| `signals.avg_cluster_size` | float | |
| `signals.cluster_count` | int | |
| `signals.total_components` | int | |
| `rationale` | string[] | Pre-formatted rationale lines (Spanish, with emoji prefixes) |
| `recommended_actions` | string[] | Suggested next steps (Spanish) |
| `tables` | string[] | Same as `metrics.tables` (denormalized) |
| `legacy_entrypoint` | object\|null | Entry point the legacy monolith exposes for this proposal. Null when type is `"internal"`. See sub-fields below. |

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
| `components_with_secrets` | string[] of component ids — only present when at least one component has `sensitive_data == true` |
| `shared_domain` | object — cross-cutting concept aggregations |
| `secrets_locations` | map of `componentId → [file:line, ...]` — **only present when secrets exist**. Absent in the dotnet-ecommerce demo. |

### `support_libraries`

Heuristic grouping of utility classes shared across proposals — typically a list of `{ name, components[] }`.

### `summary`

String. Pre-formatted Spanish summary suitable for the MCP `analyze_project` response and for direct print.

## File: `output_entrypoints.json`

Top-level keys: `endpoints`, `schemas`.

### `endpoints[*]`

| Field | Notes |
|-------|-------|
| `id` | Endpoint identifier (`Class.method`) |
| `path` | URL path or messaging topic |
| `method` | `GET` \| `POST` \| `PUT` \| `DELETE` \| `PATCH` \| `STRUTS_ACTION` \| `LISTENER` \| `WEBSERVICE` \| ... |
| `description` | Optional human label |
| `parameters` | `[{ name, type, required, source }]` |
| `request_body_schema` | JSON Schema or null |
| `response_schema` | JSON Schema or null |
| `component_id` | Owning component (joins to `output.json#components`) |

### `schemas`

JSON Schema definitions referenced by endpoint params/bodies. Indexed by schema name.

## Cross-file joins

| Need | Join |
|------|------|
| Endpoints owned by a proposal | `endpoints[*].component_id ∈ proposals[*].components` |
| Secrets affecting a proposal | `project_metadata.secrets_locations[k] for k ∈ proposal.components` (fallback: scan `components[id].sensitive_data == true`) |
| Layer mix per proposal | `components[id].layer for id ∈ proposal.components` (treat `Datos` as `Persistencia`) |
| Code-quality weight per proposal | `components[id].code_issues[*]` weighted per [R6](./decision-rules.md#r6--code-quality-weight) |
| Detect language | `output.json#meta.source` == `spoon` (Java) or `dotnet` (Roslyn) |
