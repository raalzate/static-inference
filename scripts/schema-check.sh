#!/usr/bin/env bash
# schema-check — single verification entry point of the output contract (005-json-schema-contract, FR-015/FR-016).
#
# Fronts (all run, the result aggregates):
#   version  schema/VERSION == $id segment == SchemaVersion const == Java SchemaVersion.CURRENT == .NET SchemaVersion.Current
#   java     mvn test -Dgroups=schema-contract          (Java artifacts, MCP responses, CLI/MCP parity)
#   dotnet   dotnet test --filter Category=SchemaContract (raw .NET analyzer graph)
#   ts       npm ci + npm run check + npm test in schema/ (generated types up to date, fixtures, producer samples)
#
# Output: one "[schema-check] <front>: OK|FAIL|NOT VERIFIED[ — detail]" line per front and a final
# "[schema-check] RESULT: OK|FAIL (...)" line. Exit 0 only when the four fronts are OK; a front that cannot run
# (missing JDK 17 / .NET SDK / Node 20) is NOT VERIFIED and fails the check.
#
# Environment: JAVA_HOME (JDK 17), MVN_ARGS (extra Maven args, e.g. -o), SCHEMA_CHECK_SKIP_BUILD=1 (reuse
# schema/node_modules and the published dotnet-analyzer), SCHEMA_CHECK_VERBOSE=1 (dump tool logs on failure).
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2
LOG_DIR="$ROOT/target/schema-check"
mkdir -p "$LOG_DIR"
export SCHEMA_CHECK_RUNNING=1

FAILED=0
NOT_VERIFIED=0

report() { # front status [detail]
  local line="[schema-check] $1: $2"
  [ -n "${3:-}" ] && line="$line — $3"
  echo "$line"
  case "$2" in
    FAIL) FAILED=$((FAILED + 1)) ;;
    "NOT VERIFIED") NOT_VERIFIED=$((NOT_VERIFIED + 1)) ;;
  esac
}

dump_log() { # log file
  if [ "${SCHEMA_CHECK_VERBOSE:-0}" = "1" ]; then
    echo "----- $1 -----" >&2
    tail -n 80 "$1" >&2
  else
    echo "[schema-check]   (log: ${1#"$ROOT"/})" >&2
  fi
}

# First contract violation (JSON Pointer or dotted path + message) found in a tool log.
first_violation() {
  grep -oE "(/[A-Za-z0-9_.-]+)+(: |\]: )[^]>\"]{1,160}" "$1" | grep -vE '^/(Users|home|tmp|private|var|opt)/' | head -n 1
}

# ---------------------------------------------------------------- version
SCHEMA_VERSION="$(tr -d '[:space:]' < schema/VERSION 2>/dev/null)"
check_version() {
  local problems=() v id_versions const java_v dotnet_v
  if [ -z "$SCHEMA_VERSION" ] || [ ! -d "schema/$SCHEMA_VERSION" ]; then
    report version FAIL "schema/VERSION '$SCHEMA_VERSION' has no schema/<version>/ directory"
    return
  fi
  id_versions="$(grep -rhoE '"\$id": *"https://static-inference\.dev/schema/[^/]+/' "schema/$SCHEMA_VERSION" \
    | sed -E 's#.*/schema/([^/]+)/#\1#' | sort -u | tr '\n' ' ')"
  for v in $id_versions; do
    [ "$v" != "$SCHEMA_VERSION" ] && problems+=("\$id=$v schema=$SCHEMA_VERSION")
  done
  const="$(grep -oE '"const": *"[^"]+"' "schema/$SCHEMA_VERSION/common.schema.json" | head -n 1 | sed -E 's/.*"([^"]+)"$/\1/')"
  [ "$const" != "$SCHEMA_VERSION" ] && problems+=("const=$const schema=$SCHEMA_VERSION")
  java_v="$(grep -oE 'CURRENT = "[^"]+"' src/main/java/com/extractor/model/SchemaVersion.java | sed -E 's/.*"([^"]+)"/\1/')"
  [ "$java_v" != "$SCHEMA_VERSION" ] && problems+=("java=$java_v schema=$SCHEMA_VERSION")
  dotnet_v="$(grep -oE 'Current = "[^"]+"' src/main/dotnet/DotNetAnalyzer/Model/SchemaVersion.cs | sed -E 's/.*"([^"]+)"/\1/')"
  [ "$dotnet_v" != "$SCHEMA_VERSION" ] && problems+=("dotnet=$dotnet_v schema=$SCHEMA_VERSION")
  if [ "${#problems[@]}" -eq 0 ]; then
    report version OK "$SCHEMA_VERSION"
  else
    report version FAIL "${problems[*]}"
  fi
}

# ---------------------------------------------------------------- tool detection
java_bin() {
  if [ -n "${JAVA_HOME:-}" ]; then
    [ -x "$JAVA_HOME/bin/java" ] && echo "$JAVA_HOME/bin/java"
  else
    command -v java 2>/dev/null
  fi
}
java_major() {
  "$1" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/' | awk '{ if ($1 == 1) print $2; else print $1 }'
}
mvn_cmd() {
  if command -v mvn >/dev/null 2>&1; then echo mvn; elif [ -x "$ROOT/mvnw" ]; then echo "$ROOT/mvnw"; fi
}
node_major() {
  node --version 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/'
}

# ---------------------------------------------------------------- dotnet-analyzer for the engine tests
# Publishes the current .NET analyzer (framework-dependent: single-file builds cannot load MSBuild and always
# degrade to roslyn-fallback) and installs target/dotnet-analyzer, the location the engine looks up first
# (sibling of target/classes), as a small launcher. Any previous target/dotnet-analyzer is replaced.
prepare_dotnet_analyzer() {
  command -v dotnet >/dev/null 2>&1 || return 0
  local out="$LOG_DIR/dotnet-analyzer" sdk_dir
  if [ -z "${DOTNET_ROOT:-}" ]; then
    sdk_dir="$(dotnet --list-sdks 2>/dev/null | tail -n 1 | sed -E 's/.*\[(.*)\]$/\1/')"
    [ -n "$sdk_dir" ] && [ -d "$sdk_dir/.." ] && export DOTNET_ROOT="$(cd "$sdk_dir/.." && pwd)"
  fi
  if [ "${SCHEMA_CHECK_SKIP_BUILD:-0}" != "1" ] || [ ! -x "$out/dotnet-analyzer" ]; then
    dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj -c Release -o "$out" \
      > "$LOG_DIR/dotnet-publish.log" 2>&1 || return 0
  fi
  [ -x "$out/dotnet-analyzer" ] || return 0
  rm -f "$ROOT/target/dotnet-analyzer"
  {
    echo '#!/usr/bin/env bash'
    echo "# Generated by scripts/schema-check.sh: launches the framework-dependent dotnet-analyzer."
    [ -n "${DOTNET_ROOT:-}" ] && echo "export DOTNET_ROOT=\"\${DOTNET_ROOT:-$DOTNET_ROOT}\""
    echo "exec \"$out/dotnet-analyzer\" \"\$@\""
  } > "$ROOT/target/dotnet-analyzer"
  chmod +x "$ROOT/target/dotnet-analyzer"
  return 0
}

# ---------------------------------------------------------------- java
check_java() {
  local jb major mvn log="$LOG_DIR/java.log" detail
  jb="$(java_bin)"
  if [ -z "$jb" ]; then report java "NOT VERIFIED" "java not found (JDK 17 required)"; return; fi
  major="$(java_major "$jb")"
  if [ -z "$major" ] || [ "$major" -lt 17 ] 2>/dev/null; then
    report java "NOT VERIFIED" "JDK 17+ required (found ${major:-unknown})"; return
  fi
  mvn="$(mvn_cmd)"
  if [ -z "$mvn" ]; then report java "NOT VERIFIED" "mvn not found"; return; fi
  # shellcheck disable=SC2086
  if "$mvn" -B -q ${MVN_ARGS:-} test -Dgroups=schema-contract > "$log" 2>&1; then
    report java OK
  else
    detail="$(first_violation "$log")"
    report java FAIL "${detail:-schema-contract tests failed}"
    dump_log "$log"
  fi
}

# ---------------------------------------------------------------- dotnet
check_dotnet() {
  local log="$LOG_DIR/dotnet.log" detail
  if ! command -v dotnet >/dev/null 2>&1 || ! dotnet --version > /dev/null 2>&1; then
    report dotnet "NOT VERIFIED" "dotnet SDK not found"; return
  fi
  if dotnet test src/test/dotnet/DotNetAnalyzer.Tests/DotNetAnalyzer.Tests.csproj \
       --filter "Category=SchemaContract" > "$log" 2>&1; then
    report dotnet OK
  else
    detail="$(first_violation "$log")"
    report dotnet FAIL "${detail:-SchemaContract tests failed}"
    dump_log "$log"
  fi
}

# ---------------------------------------------------------------- ts
check_ts() {
  local major log="$LOG_DIR/ts.log" detail
  major="$(node_major)"
  if [ -z "$major" ]; then report ts "NOT VERIFIED" "node not found (Node 20+ required)"; return; fi
  if [ "$major" -lt 20 ] 2>/dev/null; then report ts "NOT VERIFIED" "Node 20+ required (found $major)"; return; fi
  if ! command -v npm >/dev/null 2>&1; then report ts "NOT VERIFIED" "npm not found"; return; fi
  : > "$log"
  if [ "${SCHEMA_CHECK_SKIP_BUILD:-0}" != "1" ] || [ ! -d schema/node_modules ]; then
    if ! npm --prefix schema ci --no-audit --no-fund >> "$log" 2>&1; then
      report ts FAIL "npm ci failed"; dump_log "$log"; return
    fi
  fi
  if npm --prefix schema run --silent check >> "$log" 2>&1 && npm --prefix schema test >> "$log" 2>&1; then
    report ts OK
  else
    detail="$(grep -m 1 -E '\[schema\] FAIL|not ok|✖' "$log" | sed -E 's/^\[schema\] FAIL //')"
    report ts FAIL "${detail:-schema package checks failed}"
    dump_log "$log"
  fi
}

check_version
prepare_dotnet_analyzer
check_java
check_dotnet
check_ts

if [ "$FAILED" -eq 0 ] && [ "$NOT_VERIFIED" -eq 0 ]; then
  echo "[schema-check] RESULT: OK"
  exit 0
fi
echo "[schema-check] RESULT: FAIL ($FAILED failed, $NOT_VERIFIED not verified)"
exit 1
