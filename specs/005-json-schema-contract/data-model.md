# Data Model: 005-json-schema-contract

**Date**: 2026-10-07 | **Contract version**: 1.0.0

Modelo derivado del output real v1.1.0 (ver research.md, E1–E9). Convenciones:

- **R** = requerido; **O** = opcional; `T?` = anulable (`["T","null"]`).
- Objetos marcados **[cerrado]** rechazan propiedades no declaradas (FR-004). Mapas marcados **[mapa]** aceptan claves arbitrarias.
- Nombres de propiedades en snake_case salvo la respuesta de `analyze_project` (camelCase, como hoy).

## SchemaVersion

| Campo | Tipo | Regla |
|-------|------|-------|
| `schema_version` | string | R; patrón semver `^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$`; en el esquema 1.0.0 `const: "1.0.0"` |

Fuente de verdad: `schema/VERSION`. Réplicas que `schema-check` compara: `$id` de cada esquema, `SchemaVersion.CURRENT` (Java), `SchemaVersion.Current` (.NET).

## DependencyGraph (`output.json`, respuesta `get_dependency_graph`) [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `components` | Component[] | R |
| `edges` | Edge[] | R |
| `api_contracts` | ApiContracts (meta opcional) | R |
| `meta` | GraphMeta | R |

### GraphMeta [cerrado]

| Campo | Tipo | R/O | Notas |
|-------|------|-----|-------|
| `source` | enum `spoon` \| `roslyn` \| `roslyn-fallback` | R | FR-005 |
| `schema_version` | SchemaVersion | R | **nuevo** (FR-008/009), serializado tras `source` |
| `collected_at` | string (`format: date-time`) | O | opt-in `STATIC_INFERENCE_COLLECTED_AT` (FR-010) |
| `microservice_candidates` | [mapa] string→string | R | |
| `dependency_accuracy` | object? | O | .NET omite nulos |
| `decomposition_accuracy` | object? | O | |

### Component [cerrado] (también respuesta `get_component_metrics`)

| Campo | Tipo | R/O | Notas |
|-------|------|-----|-------|
| `id` | string (minLength 1) | R | FQN |
| `files` | string[] | R | |
| `loc` | integer ≥ 0 | R | |
| `tables_used` | string[] | R | |
| `sensitive_data` | boolean | R | |
| `secrets_references` | string[] | R | |
| `layer` | enum `Controlador`\|`Negocio`\|`Datos`\|`Persistencia`\|`Dominio`\|`Transferencia`\|`Web`\|`Compartida` | R | FR-005; `Datos` alias histórico de `Persistencia` |
| `calls_out` | string[] | R | |
| `calls_in` | string[] | R | |
| `annotations` | string[] | R | |
| `implements` | string[] | R | |
| `external_dependencies` | string[] | R | |
| `package_dependencies` | PackageGroup[] | R | |
| `code_issues` | CodeIssue[] | R | |
| `uses_jndi` | boolean | R | |
| `is_interface` | boolean | R | |
| `interface` | boolean | O | `deprecated: true` (FR-007); .NET crudo no lo emite |
| `domain` | string? | O | |
| `ejb_type` | string? | O | |
| `extends` | string? | O | |
| `messaging_type` | string? | O | valores abiertos |
| `messaging_role` | string? | O | valores abiertos |
| `web_type` | string? | O | |
| `web_role` | string? | O | |
| `cbo` | integer? ≥ 0 | O | |
| `lcom` | number? [0,1] | O | |
| `complexity_avg` | number? ≥ 0 | O | no documentado antes de 005 |
| `complexity_max` | integer? ≥ 0 | O | ídem |

### PackageGroup [cerrado]

`package_name` string R · `components` string[] R · `count` integer ≥ 0 R

### CodeIssue [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `type` | enum `BUG_PATTERN`\|`CODE_STYLE`\|`SECURITY`\|`PERFORMANCE` | R |
| `severity` | enum `INFO`\|`WARNING`\|`ERROR`\|`CRITICAL` | R |
| `message` | string (minLength 1) | R |
| `line` | integer ≥ 0 | R |
| `pattern` | string (minLength 1, valores abiertos) | R |

### Edge [cerrado]

`from` string R · `to` string R · `weight` integer ≥ 0 R · `type` string R, patrón `^[a-z][a-z-]*(,[a-z][a-z-]*)*$` (valores abiertos, compuestos permitidos)

## ApiContracts (`output_entrypoints.json`, respuesta `get_api_contracts`, embebido en `api_contracts`) [cerrado]

| Campo | Tipo | R/O | Notas |
|-------|------|-----|-------|
| `endpoints` | ApiEndpoint[] | R | |
| `schemas` | [mapa] string→ApiSchema | R | |
| `meta` | DocumentMeta | R en `EntrypointsDocument`; O en `DependencyGraph.api_contracts` | **nuevo** |

`EntrypointsDocument` = `ApiContracts` con `meta` requerido.

### DocumentMeta [cerrado]

`schema_version` SchemaVersion R

### ApiEndpoint [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `id` | string | R |
| `path` | string | R |
| `method` | string (valores abiertos: verbos HTTP, `STRUTS_ACTION`, `LISTENER`, `WEBSERVICE`, `KAFKA_LISTEN`…) | R |
| `parameters` | Parameter[] | R |
| `response_schema` | string? | R en output actual; anulable |
| `component_id` | string | R |
| `description` | string? | O |
| `request_body_schema` | string? | O |

### Parameter [cerrado]

`name` string R · `in` string R (`path`/`query`/`header`/`body`, abiertos) · `type` string R · `required` boolean R

### ApiSchema [cerrado]

`type` string R · `properties` [mapa] string→ApiSchemaProperty R

ApiSchemaProperty [cerrado]: `type` string R

## ConsolidatedArchitecture (`output_architecture.json`, respuesta `get_architecture_proposal`) [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `meta` | DocumentMeta | R (**nuevo**) |
| `project_metadata` | ProjectMetadata | R |
| `proposals` | Proposal[] | R |
| `support_libraries` | SupportLibrary[] | R |
| `summary` | string | R |

### ProjectMetadata [cerrado] (Java usa `NON_NULL`)

| Campo | Tipo | R/O |
|-------|------|-----|
| `external_dependencies` | [mapa] string→string | O |
| `package_dependencies` | [mapa] string→PackageDependencyInfo | O |
| `total_components` | integer ≥ 0 | O |
| `total_loc` | integer ≥ 0 | O |
| `components_with_secrets` | integer ≥ 0 | O |
| `shared_domain` | string | O |
| `secrets_locations` | SecretLocation[] | O |

> Todos opcionales porque la clase usa `NON_NULL` y la referencia admite `{}`; en el output actual siempre aparecen los seis primeros.

PackageDependencyInfo [cerrado]: `components_count` integer R · `total_dependencies_out` integer R · `depends_on_packages` string[] R

SecretLocation [cerrado]: `path` string R · `line` integer ≥ 0 R · `kind` string R · `redacted` boolean R

### Proposal [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `id` | integer | R |
| `name` | string | R |
| `viability` | enum `Alta`\|`Media`\|`Baja` | R |
| `clusters` | integer[] | R |
| `components` | string[] | R |
| `metrics` | ProposalMetrics | R |
| `signals` | ProposalSignals | R |
| `rationale` | string[] | R |
| `recommended_actions` | string[] | R |
| `tables` | ProposalTable[] | O |
| `legacy_entrypoint` | LegacyEntrypoint? | O |

ProposalMetrics [cerrado]: `size` integer R · `cohesion_avg` number R · `external_coupling` number R · `internal_edge_density` number R · `data_jaccard` number R · `tables` string[] R · `sensitive` boolean R · `tables_source` string O (valores observados `jpa`, `orm`, `sql-parse`, `mixed`, `none`; abiertos)

ProposalSignals [cerrado]: `avg_cluster_size` number R · `cluster_count` integer R · `total_components` integer R

ProposalTable [cerrado]: `name` string R · `component` string R · `source` string R

### LegacyEntrypoint [cerrado] (`NON_NULL`)

| Campo | Tipo | R/O |
|-------|------|-----|
| `type` | enum `rest`\|`messaging`\|`service`\|`internal` | R |
| `primary_entry_class` | string | O |
| `exposed_operations` | string[] | O |
| `messaging_channels` | string[] | O |
| `endpoints` | ApiEndpoint[] | O |
| `description` | string | O |

SupportLibrary [cerrado]: `id` integer R · `name` string R · `clusters` integer[] R · `components` string[] R

## AnalyzeProjectResponse (respuesta `analyze_project`) [cerrado]

| Campo | Tipo | R/O |
|-------|------|-----|
| `componentCount` | integer ≥ 0 | R |
| `edgeCount` | integer ≥ 0 | R |
| `proposalCount` | integer ≥ 0 | R |
| `endpointCount` | integer ≥ 0 | R |
| `filesGenerated` | string[] (3 elementos) | R |
| `summary` | string | R |

## Relaciones e invariantes (no expresables en JSON Schema; verificados por tests de contrato)

- `edges[*].from/to`, `calls_in/out[*]` y `endpoints[*].component_id` referencian `components[*].id` — **no** se valida en esta feature (integridad referencial fuera de alcance; la validación es estructural).
- `interface == is_interface` cuando ambos están presentes — aserción en tests Java (no en el esquema).

## Versionado (estado)

```
1.0.0 (esta feature) ──breaking──▶ 2.0.0 (+ nota de migración en docs/SCHEMA.md)
        └──campo opcional nuevo──▶ 1.1.0
        └──aclaración sin efecto──▶ 1.0.1
```
