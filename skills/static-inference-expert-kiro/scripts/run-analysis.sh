#!/usr/bin/env bash
# CLI fallback runner for the static-inference analyzer (Kiro variant).
# Use when the MCP server is not available in the current Kiro session.
#
# JAR resolution order:
#   1. STATIC_INFERENCE_JAR env var
#   2. Parse .kiro/settings/mcp.json (project root or any ancestor) for
#      mcpServers.static-inference.args containing the JAR path
#   3. STATIC_INFERENCE_HOME/target/java-dependency-extractor.jar
#   4. ./target/java-dependency-extractor.jar (only useful when running inside the analyzer repo)
#
# Usage: run-analysis.sh <projectPath> [outputDir]
set -euo pipefail

PROJECT_PATH="${1:-}"
OUTPUT_DIR="${2:-${PROJECT_PATH}}"

if [[ -z "${PROJECT_PATH}" ]]; then
  echo "ERROR: missing projectPath" >&2
  echo "Usage: $0 <absolute-project-path> [outputDir]" >&2
  exit 2
fi

if [[ ! -d "${PROJECT_PATH}" ]]; then
  echo "ERROR: not a directory: ${PROJECT_PATH}" >&2
  exit 2
fi

# --- locate JAR -------------------------------------------------------------

find_jar_in_kiro_mcp() {
  local dir="$1"
  while [[ "${dir}" != "/" ]]; do
    local cfg="${dir}/.kiro/settings/mcp.json"
    if [[ -f "${cfg}" ]] && command -v jq >/dev/null; then
      local jar
      jar="$(jq -r '.mcpServers["static-inference"].args[]? | select(test("\\.jar$"))' "${cfg}" 2>/dev/null | head -1)"
      if [[ -n "${jar}" && -f "${jar}" ]]; then
        echo "${jar}"
        return 0
      fi
    fi
    dir="$(dirname "${dir}")"
  done
  return 1
}

JAR=""
if [[ -n "${STATIC_INFERENCE_JAR:-}" && -f "${STATIC_INFERENCE_JAR}" ]]; then
  JAR="${STATIC_INFERENCE_JAR}"
elif found_jar="$(find_jar_in_kiro_mcp "$(pwd)")"; then
  JAR="${found_jar}"
elif [[ -n "${STATIC_INFERENCE_HOME:-}" && -f "${STATIC_INFERENCE_HOME}/target/java-dependency-extractor.jar" ]]; then
  JAR="${STATIC_INFERENCE_HOME}/target/java-dependency-extractor.jar"
elif [[ -f "./target/java-dependency-extractor.jar" ]]; then
  JAR="$(pwd)/target/java-dependency-extractor.jar"
fi

if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  cat >&2 <<EOF
ERROR: could not locate java-dependency-extractor.jar.

Options to resolve:
  - Export STATIC_INFERENCE_JAR=/abs/path/to/java-dependency-extractor.jar
  - Add .kiro/settings/mcp.json at the project root with the server's JAR path
    (see references/build-setup.md)
  - Export STATIC_INFERENCE_HOME pointing to the analyzer repo

EOF
  exit 3
fi

mkdir -p "${OUTPUT_DIR}"
OUT_JSON="${OUTPUT_DIR}/output.json"

echo "[run-analysis] jar=${JAR}" >&2
echo "[run-analysis] project=${PROJECT_PATH}" >&2
echo "[run-analysis] out=${OUT_JSON}" >&2

java -jar "${JAR}" "${PROJECT_PATH}" "${OUT_JSON}"

echo "[run-analysis] generated:" >&2
ls -1 "${OUTPUT_DIR}"/output*.json 2>/dev/null >&2 || true
