#!/usr/bin/env bash

JAR_FILE="$(dirname "$0")/target/java-dependency-extractor.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please run 'mvn clean package' first to build the executable JAR"
    exit 1
fi

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <project-path> <output-file>"
    echo ""
    echo "Example:"
    echo "  $0 /path/to/my-project output.json"
    echo ""
    echo "This will generate:"
    echo "  - output.json (complete dependency graph)"
    echo "  - output_architecture.json (microservice proposals)"
    exit 1
fi

PROJECT_PATH="$1"
OUTPUT_FILE="$2"

if [ ! -d "$PROJECT_PATH" ]; then
    echo "Error: Project path not found: $PROJECT_PATH"
    exit 1
fi

echo "Running Java Dependency Extractor..."
echo "Project: $PROJECT_PATH"
echo "Output: $OUTPUT_FILE"
echo ""

# Try to use user-provided Java command first (via JAVA_CMD env var)
if [ -n "$JAVA_CMD" ]; then
    echo "Using Java from JAVA_CMD: $JAVA_CMD"
    "$JAVA_CMD" -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    EXIT_CODE=$?
# Check if nix-shell is available (NixOS/Replit)
elif command -v nix-shell >/dev/null 2>&1; then
    echo "Detected NixOS environment, using nix-shell..."
    nix-shell -p jdk11 --run "java -jar \"$JAR_FILE\" \"$PROJECT_PATH\" \"$OUTPUT_FILE\""
    EXIT_CODE=$?
# Try JAVA_HOME if set
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "Using Java from JAVA_HOME: $JAVA_HOME"
    "$JAVA_HOME/bin/java" -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    EXIT_CODE=$?
# Try java from PATH
elif command -v java >/dev/null 2>&1; then
    echo "Using Java from PATH"
    java -jar "$JAR_FILE" "$PROJECT_PATH" "$OUTPUT_FILE"
    EXIT_CODE=$?
else
    echo "Error: No Java installation found!"
    echo ""
    echo "Please install Java 11+ or set one of the following:"
    echo "  - JAVA_CMD: Path to java executable (e.g., export JAVA_CMD=/usr/bin/java)"
    echo "  - JAVA_HOME: Path to JDK root directory (e.g., export JAVA_HOME=/usr/lib/jvm/java-11)"
    echo "  - Or add java to your PATH"
    echo ""
    exit 1
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Analysis complete!"
    echo ""
    echo "Generated files:"
    ls -lh "$OUTPUT_FILE" output_architecture.json output.json 2>/dev/null
    if [ -f "output_web.json" ]; then
        echo "Web components detected:"
        ls -lh output_web.json
    fi
else
    echo ""
    echo "❌ Analysis failed with exit code $EXIT_CODE"
    exit $EXIT_CODE
fi
