#!/usr/bin/env bash
# PostToolUse hook for static-inference MCP analyze_project.
# Auto-generates triage CSV + risk report next to the produced JSONs.
# Wire-up in .claude/settings.json:
#  hooks.PostToolUse[matcher=mcp__static-inference__analyze_project]
set -euo pipefail

# Hook receives JSON via stdin: { tool_name, tool_input, tool_response, ... }
# We only act when output files exist next to projectPath / outputDir.
INPUT="$(cat)"

PROJECT="$(echo "${INPUT}" | jq -r '.tool_input.projectPath // empty')"
OUT_DIR="$(echo "${INPUT}" | jq -r '.tool_input.outputDir // .tool_input.projectPath // empty')"

if [[ -z "${OUT_DIR}" || ! -d "${OUT_DIR}" ]]; then
  echo "[post-analysis] skip: outputDir not resolved" >&2
  exit 0
fi

GRAPH="${OUT_DIR}/output.json"
ARCH="$(ls -1 "${OUT_DIR}"/output*architecture*.json 2>/dev/null | head -1)"

if [[ ! -f "${GRAPH}" || -z "${ARCH}" ]]; then
  echo "[post-analysis] skip: graph or architecture json not found in ${OUT_DIR}" >&2
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TRIAGE="${SCRIPT_DIR}/../scripts/triage-proposals.sh"
RISK="${SCRIPT_DIR}/../scripts/risk-report.sh"

bash "${TRIAGE}" "${ARCH}" --md > "${OUT_DIR}/triage.md"
bash "${RISK}" "${GRAPH}" "${ARCH}" > "${OUT_DIR}/risk-report.md"

echo "[post-analysis] wrote ${OUT_DIR}/triage.md and risk-report.md" >&2
