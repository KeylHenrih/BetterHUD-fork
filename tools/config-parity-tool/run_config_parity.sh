#!/usr/bin/env bash
# BetterHUD Configuration Parity Analyzer
# Compares server vs client component trees, colors, shadows, advances, and decorations.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

CONFIG_DIR="${1:-${BETTERHUD_CONFIG_DIR:-common-resources/default}}"

if [ -z "$JAVA_HOME" ]; then
    if command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
    fi
fi
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "================================================================================"
echo "Starting BetterHUD Configuration Parity Tool"
echo "Config Target: $CONFIG_DIR"
echo "================================================================================"

cd "$ROOT_DIR"
./gradlew :dist:test --tests "kr.toxicity.hud.tools.ConfigParityToolTest" -Dbetterhud.config.dir="$CONFIG_DIR" --info --no-daemon
