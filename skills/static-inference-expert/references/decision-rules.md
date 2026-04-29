# Decision Rules

Authoritative thresholds for converting `static-inference` JSON output into extraction decisions. Every rule cites the JSON path it reads. The analyzer's own viability formula is documented at the bottom — the gates here run *on top of* that score.

## Viability classes

Drives the proposal triage. Source: `proposals[*].viability` (string: `Alta` | `Media` | `Baja`).

`ViabilityScorer` (`src/main/java/com/extractor/inference/ViabilityScorer.java`) computes:

```
score = 0.50 * cohesion_adj
      + 0.35 * (1 - external_coupling)
      + 0.15 * data_cohesion
size penalties:
  totalSize < 3   → score *= 0.6
  totalSize > 50 AND internal_density < 0.5 → score *= 0.7

cohesion_adj = 0.7 * weighted_avg_cohesion + 0.3 * internal_edge_density

viability = score >= 0.7 ? "Alta"
          : score >= 0.5 ? "Media"
          : "Baja"
```

So `Alta`/`Media`/`Baja` already encode cohesion + coupling + data + size. The rules below are *additional safeguards* — they enforce constraints the score does not weight (sensitive data, layer mixing, entrypoint coverage, code-quality issues).

| Class | Default action | Override conditions |
|-------|----------------|---------------------|
| `Alta` | Promote to extraction candidate | Block if rule R3, R4, or R6 fires |
| `Media` | Conditional — list gaps to reach `Alta` | Promote only if R7 satisfied AND user approves |
| `Baja` | Keep in monolith / merge with related domain | Never auto-promote |

## R1 — Viability gate

```
candidate := proposals[*] where viability == "Alta"
```

## R2 — Sensitive-data inspection

```
if proposal.metrics.sensitive == true:
    require remediation block in report
    cross_ref project_metadata.secrets_locations[component_id]   # may be absent if no secrets
    list affected files in components[*].secrets_references
```

`secrets_locations` only appears in `output_architecture.json#project_metadata` when at least one component carries secret references. Don't fail when absent — fall back to scanning `components[*].sensitive_data == true` in `output.json`.

Never strip `sensitive == true` from output. It must appear in the user-facing report.

## R3 — Cohesion floor

```
if proposal.metrics.cohesion_avg < 0.5
   AND proposal.metrics.data_jaccard < 0.7:
    block extraction
    reason := "low cohesion without data-sharing justification"
```

`data_jaccard >= 0.7` rescues low cohesion because shared data implies a shared bounded context.

## R4 — External coupling ceiling

```
if proposal.metrics.external_coupling > 0.6:
    flag as fragile_boundary
    require manual review
    do not auto-promote even if viability == "Alta"
```

## R5 — Layer-mixing detector

`LayerClassifier` emits one of: `Controlador`, `Negocio`, `Persistencia`, `Dominio`, `Transferencia`, `Web`, `Compartida`, `Datos`. Note: `Datos` is a deprecated alias kept for backward compatibility — treat it as `Persistencia` when counting.

```
core_layers := {Controlador, Web, Negocio, Persistencia}    # Datos folds into Persistencia
cross_cutting := {Compartida, Dominio, Transferencia}       # excluded from spread

layers := { components[c].layer for c in proposal.components } - cross_cutting
if |layers ∩ core_layers| > 2:
    mark needs_split
    suggest: "split by layer before extraction"
```

`Compartida`, `Dominio`, `Transferencia` are excluded because they are cross-cutting by design (shared utilities, value objects, DTOs); their presence does not signal a multi-tier proposal.

## R6 — Code-quality weight

`code_issues[*].severity` values emitted by the analyzers:

- Java (Spoon): `INFO`, `WARNING`
- .NET (Roslyn): `INFO`, `WARNING`, `ERROR` (only patterns like `DEPRECATED_THREAD_USAGE`, `ASYNC_VOID_METHOD`)

So an `ERROR`-only rule is too tight for Java targets. Use a weighted count:

```
weight := sum(
    issue in components[c].code_issues for c in proposal.components
    case issue.severity:
        ERROR   → 3
        WARNING → 1
        INFO    → 0
)
if weight > 10:
    block extraction
    output: tech-debt task with top 10 file:line entries (severity desc)
```

Reasoning: 10 warnings ≈ 3 errors of damage, both indicate stabilization is needed before service split. Keep INFO out of the score (mostly stylistic).

## R7 — Entrypoint coverage

```
endpoints := { e for e in output_entrypoints.endpoints
              if e.component_id in proposal.components }
listeners := { c for c in proposal.components
              if components[c].messaging_role == "consumer" }
if |endpoints| == 0 AND |listeners| == 0:
    reclassify as shared_library
    do not propose as microservice
```

## R8 — Size sanity

```
if proposal.metrics.size < 3:
    flag too_small — likely belongs to another proposal
if proposal.metrics.size > 25:
    flag too_large — recommend further clustering
```

Note: the analyzer's own scorer already penalizes `totalSize < 3` (×0.6) and `totalSize > 50` with low internal density (×0.7). R8 surfaces the *qualitative* recommendation; the score reflects the quantitative penalty.

## R9 — Internal density check

```
if proposal.metrics.internal_edge_density < 0.1
   AND proposal.metrics.size >= 5:
    weak_internal_cohesion → demote one viability class
```

## R10 — Secrets-location override

```
if any component in proposal appears in project_metadata.secrets_locations:
    add prereq task: "extract secrets to vault before service split"
    block release until prereq completed
```

## Composite ranking (within a viability class)

The analyzer already publishes `proposals[*].viability` based on the formula above. To rank candidates *within* an `Alta` (or `Media`) bucket, prefer:

```
rank_score = proposal.metrics.cohesion_avg * 0.30
           + (1 - proposal.metrics.external_coupling) * 0.25
           + proposal.metrics.data_jaccard * 0.20
           + proposal.metrics.internal_edge_density * 0.15
           + min(proposal.metrics.size, 12) / 12 * 0.10
           - (R6.weight) * 0.01
           - (proposal.metrics.sensitive ? 0.10 : 0)
```

Tie-breaker only. Do not override the analyzer's `viability` with this score.

## Output contract

Every recommendation in the user-facing report MUST cite at least one JSON path. Examples:

- "Extract `Order` service — `proposals[2].viability=Alta`, `metrics.cohesion_avg=0.78`, `metrics.external_coupling=0.32`."
- "Block `Notification`+`Inventory` extraction — `metrics.cohesion_avg=0.41`, no `data_jaccard` rescue (=0.0)."
- "Sensitive data flag on `Customer` — `components[com.acme.erp.model.Customer].sensitive_data=true`."

If a recommendation cannot cite a path, do not emit it.
