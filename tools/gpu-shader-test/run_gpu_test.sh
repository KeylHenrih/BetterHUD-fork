#!/bin/bash
set -e

# Detect JAVA_HOME or java in PATH
if [ -z "$JAVA_HOME" ]; then
    if ! command -v java >/dev/null 2>&1; then
        echo "ERROR: JAVA_HOME is not set and 'java' was not found in PATH." >&2
        echo "Please export JAVA_HOME=/path/to/jdk-25+" >&2
        exit 1
    fi
else
    export PATH="$JAVA_HOME/bin:$PATH"
fi

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$DIR"

# Gradle module cache directory
GRADLE_CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
if [ ! -d "$GRADLE_CACHE_DIR" ]; then
    echo "ERROR: Gradle cache directory not found at $GRADLE_CACHE_DIR" >&2
    echo "Please build the project first with './gradlew build' or set GRADLE_USER_HOME." >&2
    exit 1
fi

echo "[GPU Test] Executing hardware GPU Transform Feedback test with real generated GLSL..."
CLASSPATH="api/build/libs/*:dist/build/libs/*:bootstrap/bukkit/build/libs/*:build/libs/*:$(find "$GRADLE_CACHE_DIR" -name "*.jar" | tr '\n' ':')"
java -cp "$CLASSPATH" tools/gpu-shader-test/GpuShaderTest.java
