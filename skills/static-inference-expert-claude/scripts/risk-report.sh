#!/usr/bin/env bash
# Risk surface report. Joins graph + architecture to expose:
#  - Components touching secrets and which proposals own them
#  - Top ERROR-severity code_issues
#  - Components in shared layers used across multiple proposals
# Usage: risk-report.sh <output.json> <output_architecture.json>
set -euo pipefail

GRAPH="${1:-}"
ARCH="${2:-}"

if [[ -z "${GRAPH}" || ! -f "${GRAPH}" ]]; then
  echo "ERROR: missing graph json" >&2
  exit 2
fi
if [[ -z "${ARCH}" || ! -f "${ARCH}" ]]; then
  echo "ERROR: missing architecture json" >&2
  exit 2
fi

if ! command -v jq >/dev/null; then
  echo "ERROR: jq required" >&2
  exit 3
fi

echo "## Sensitive components"
echo
echo "| component | layer | files | secret refs |"
echo "|-----------|-------|-------|-------------|"
jq -r '.components[] | select(.sensitive_data == true) | "| \(.id) | \(.layer) | \(.files | join("; ")) | \((.secrets_references // []) | join(", ")) |"' "${GRAPH}"

echo
echo "## Secrets locations (project_metadata)"
jq -r '.project_metadata.secrets_locations // {} | to_entries[] | "- \(.key): \(.value | join(", "))"' "${ARCH}" || echo "- none"

echo
echo "## Top code issues (ERROR + WARNING)"
echo
echo "Java (Spoon) emits only INFO/WARNING; ERROR only appears for .NET (Roslyn) targets."
echo
echo "| component | severity | line | message |"
echo "|-----------|----------|------|---------|"
jq -r '
  .components[]
  | . as $c
  | (.code_issues // [])[]
  | select(.severity == "ERROR" or .severity == "WARNING")
  | "| \($c.id) | \(.severity) | \(.line) | \(.message) |"
' "${GRAPH}" | head -20

echo
echo "## Proposals touching sensitive components"
echo
jq -r '
  .proposals[]
  | select(.metrics.sensitive == true)
  | "- [\(.viability)] \(.name) (id=\(.id)) — components: \(.components | join(", "))"
' "${ARCH}"
