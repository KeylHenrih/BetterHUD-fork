# BetterHUD Configuration Parity Tool

A standalone verification tool that analyzes real BetterHUD configuration folders (popups, layouts, and texts) and verifies mathematical and visual parity between server-mode and client-mode pipelines.

## Purpose
- Identifies which layouts are client-side shader animated vs server-side fallback, along with the precise technical reasons.
- Verifies exact color and shadow alpha encoding for GLSL transpiled layouts across valid modulo windows ($M \in \{20..120\}$).
- Verifies text advance calculations (stripping italic shears to match client rendering).
- Detects unsupported decorations (`<u>`, `<st>`, `<obf>`) dropped by vanilla core shaders.

## Usage

### Run on Default / Target Server:
```bash
./tools/config-parity-tool/run_config_parity.sh /path/to/plugins/BetterHud
```

### Run with Environment Variable:
```bash
export BETTERHUD_CONFIG_DIR=/path/to/plugins/BetterHud
./tools/config-parity-tool/run_config_parity.sh
```

### Run via Gradle directly:
```bash
./gradlew :dist:test --tests "kr.toxicity.hud.tools.ConfigParityToolTest" -Dbetterhud.config.dir=/path/to/plugins/BetterHud --info
```
