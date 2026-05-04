#!/usr/bin/env bash
# Batch triage. Emits CSV: id,name,viability,size,cohesion,coupling,density,jaccard,sensitive,decision
# Decision applies decision-rules R1..R4, R8, R9 without R5/R6/R7 (those need cross-file joins).
# Usage: triage-proposals.sh <output_architecture.json> [--csv|--md]
set -euo pipefail

ARCH_JSON="${1:-}"
FORMAT="${2:---csv}"

if [[ -z "${ARCH_JSON}" || ! -f "${ARCH_JSON}" ]]; then
  echo "ERROR: missing or invalid architecture JSON: ${ARCH_JSON}" >&2
  echo "Usage: $0 <output_architecture.json> [--csv|--md]" >&2
  exit 2
fi

if ! command -v jq >/dev/null; then
  echo "ERROR: jq required" >&2
  exit 3
fi

JQ_FILTER='
  .proposals[]
  | {
      id,
      name,
      viability,
      size: .metrics.size,
      cohesion: .metrics.cohesion_avg,
      coupling: .metrics.external_coupling,
      density: .metrics.internal_edge_density,
      jaccard: .metrics.data_jaccard,
      sensitive: .metrics.sensitive
    }
  | . + {
      decision: (
        if .viability == "Baja" then "KEEP_MONOLITH"
        elif .coupling > 0.6 then "BLOCK_FRAGILE_BOUNDARY"
        elif (.cohesion < 0.5 and .jaccard < 0.7) then "BLOCK_LOW_COHESION"
        elif .size < 3 then "TOO_SMALL_MERGE"
        elif .size > 25 then "TOO_LARGE_SPLIT"
        elif (.density < 0.1 and .size >= 5) then "DEMOTE_WEAK_INTERNAL"
        elif .viability == "Alta" then "EXTRACT_CANDIDATE"
        else "REVIEW_CONDITIONAL"
        end
      )
    }
'

case "${FORMAT}" in
  --csv)
    echo "id,name,viability,size,cohesion,coupling,density,jaccard,sensitive,decision"
    jq -r "${JQ_FILTER} | [.id,.name,.viability,.size,.cohesion,.coupling,.density,.jaccard,.sensitive,.decision] | @csv" "${ARCH_JSON}"
    ;;
  --md)
    echo "| id | name | viability | size | cohesion | coupling | density | jaccard | sensitive | decision |"
    echo "|----|------|-----------|------|----------|----------|---------|---------|-----------|----------|"
    jq -r "${JQ_FILTER} | \"| \(.id) | \(.name) | \(.viability) | \(.size) | \(.cohesion) | \(.coupling) | \(.density) | \(.jaccard) | \(.sensitive) | \(.decision) |\"" "${ARCH_JSON}"
    ;;
  *)
    echo "ERROR: unknown format ${FORMAT} (use --csv or --md)" >&2
    exit 2
    ;;
esac
