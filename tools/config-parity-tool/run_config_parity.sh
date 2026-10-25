#!/usr/bin/env bash
# BetterHUD Configuration Parity Analyzer
# Compares server vs client component trees, colors, shadows, advances, and decorations.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

CONFIG_DIR="${1:-${BETTERHUD_CONFIG_DIR:-/home/keyl/Servers/RevakeMain/plugins/BetterHud}}"

export JAVA_HOME="${JAVA_HOME:-/home/keyl/java/jdk-26.0.2.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "================================================================================"
echo "Starting BetterHUD Configuration Parity Tool"
echo "Config Target: $CONFIG_DIR"
echo "================================================================================"

cd "$ROOT_DIR"
./gradlew :dist:test --tests "kr.toxicity.hud.tools.ConfigParityToolTest" -Dbetterhud.config.dir="$CONFIG_DIR" --info --no-daemon
