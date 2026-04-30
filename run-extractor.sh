#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
    cat <<EOF
Usage: $0 [--java|--dotnet] <project-path> <output-file>

Runs the appropriate static-inference analyzer (Java or .NET) against
<project-path> and writes the dependency graph JSON to <output-file>.

If no flag is given, the project type is auto-detected:
  - .NET     ← *.sln, *.csproj, *.fsproj or *.vbproj found in the path
  - Java     ← otherwise (pom.xml, build.gradle, src/, etc.)

Override the JAR / DLL location with:
  JAR_FILE=/path/to/java-dependency-extractor.jar
  DOTNET_DLL=/path/to/dotnet-analyzer.dll

Example:
  $0 /path/to/spring-boot-app graph.json
  $0 --dotnet /path/to/MySolution.sln graph.json
EOF
}

# ── Parse args ────────────────────────────────────────────────
MODE=""
case "${1:-}" in
    --java)   MODE="java";   shift ;;
    --dotnet) MODE="dotnet"; shift ;;
    -h|--help) usage; exit 0 ;;
esac

if [ "$#" -lt 2 ]; then
    usage
    exit 1
fi

PROJECT_PATH="$1"
OUTPUT_FILE="$2"

if [ ! -e "$PROJECT_PATH" ]; then
    echo "Error: Project path not found: $PROJECT_PATH"
    exit 1
fi

# ── Auto-detect project type if not specified ────────────────
if [ -z "$MODE" ]; then
    if [ -f "$PROJECT_PATH" ]; then
        case "$PROJECT_PATH" in
            *.sln|*.csproj|*.fsproj|*.vbproj) MODE="dotnet" ;;
            *) MODE="java" ;;
        esac
    elif find "$PROJECT_PATH" -maxdepth 4 \
            \( -name "*.csproj" -o -name "*.sln" -o -name "*.fsproj" -o -name "*.vbproj" \) \
            -print -quit 2>/dev/null | grep -q .; then
        MODE="dotnet"
    else
        MODE="java"
    fi
    echo "Auto-detected project type: $MODE"
fi

# ── Locate analyzer artifact ─────────────────────────────────
locate_first() {
    for c in "$@"; do
        [ -n "$c" ] && [ -f "$c" ] && { echo "$c"; return 0; }
    done
    return 1
}

run_java() {
    JAR_FILE="$(locate_first \
        "${JAR_FILE:-}" \
        "$SCRIPT_DIR/java/java-dependency-extractor.jar" \
        "$SCRIPT_DIR/target/java-dependency-extractor.jar")" || {
        echo "Error: Java JAR not found. Set JAR_FILE or build with 'mvn clean package'."
        exit 1
    }

    echo "Running Java Dependency Extractor..."
    echo "  JAR:     $JAR_FILE"
    echo "  Project: $PROJECT_PATH"
    echo "  Output:  $OUTPUT_FILE"
    echo ""

    if [ -n "${JAVA_CMD:-}" ]; then
        "$JAVA_CMD" -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    elif command -v nix-shell >/dev/null 2>&1; then
        echo "Detected NixOS environment, using nix-shell..."
        nix-shell -p jdk11 --run "java -jar \"$JAR_FILE\" \"$PROJECT_PATH\" \"$OUTPUT_FILE\""
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        "$JAVA_HOME/bin/java" -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    elif command -v java >/dev/null 2>&1; then
        java -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    else
        echo "Error: No Java installation found. Set JAVA_CMD, JAVA_HOME or add java to PATH."
        exit 1
    fi
}

run_dotnet() {
    DOTNET_DLL="$(locate_first \
        "${DOTNET_DLL:-}" \
        "$SCRIPT_DIR/dotnet/dotnet-analyzer.dll")" || {
        echo "Error: .NET analyzer DLL not found. Set DOTNET_DLL or rebuild distribution."
        exit 1
    }

    if ! command -v dotnet >/dev/null 2>&1; then
        echo "Error: 'dotnet' runtime not found. Install .NET 8 SDK/runtime."
        exit 1
    fi

    echo "Running .NET Dependency Extractor..."
    echo "  DLL:     $DOTNET_DLL"
    echo "  Project: $PROJECT_PATH"
    echo "  Output:  $OUTPUT_FILE"
    echo ""

    dotnet "$DOTNET_DLL" "$PROJECT_PATH" > "$OUTPUT_FILE"
}

# ── Dispatch ─────────────────────────────────────────────────
case "$MODE" in
    java)   run_java ;;
    dotnet) run_dotnet ;;
    *)      echo "Error: unknown mode '$MODE'"; exit 1 ;;
esac
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Analysis complete!"
    echo ""
    echo "Generated files:"
    ls -lh "$OUTPUT_FILE" output_architecture.json output.json 2>/dev/null || true
    if [ -f "output_web.json" ]; then
        echo "Web components detected:"
        ls -lh output_web.json
    fi
else
    echo ""
    echo "❌ Analysis failed with exit code $EXIT_CODE"
    exit $EXIT_CODE
fi
