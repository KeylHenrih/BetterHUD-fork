# Changelog

All notable changes to BetterHud (Client-Side Animation Fork) will be documented in this file.

## [2.2.0-fork.3] - 2026-10-26

### Fixed
- **A (GLSL Floored Modulo Hardening):** Stabilized GLSL `jmod` against possible float division precision discrepancies across the 24,000-tick day boundary (`m = x - y * floor(x / y); return m >= y ? m - y : (m < 0.0 ? m + y : m);`); hardware GPU verified across 3,312 time boundary combinations (verified: Mesa Intel, glslangValidator; unverified on NVIDIA/AMD/Apple).
- **B (Slot Priority and Server Fallback):** Prioritized client animation shaders for slots 1..1023; overflown layouts safely fall back to server variant without emitting protocol quads (alpha in 1..240) into empty case 0.
- **C (World Change & Respawn Server Parity):** Restored 100% upstream parity for server mode (no bossbar flicker, no popup wiping, no reloads); in client mode, re-anchors listener trackers and updates active popup spawn times without full reload.
- **D (Popup Lifecycle Parity & Hold Freezing):** Fixed `/hud debug-popup ... hold` cyclic re-triggering via `keepAlive()` in `PopupUpdater`; preserved byte-for-byte component constancy from `animDuration` to safe window ($M - L - 1$) for non-held popups.
- **E (Thread-Safe Bounded Text LRU Cache):** Replaced external dependency with standard library `Collections.synchronizedMap(LinkedHashMap(accessOrder = true))` LRU cache bounded to 2048 elements; stress-tested with 100,000 distinct items; verified Folia multi-threading concurrency across 16 parallel threads; benchmarked 12.7x speedup (1,169,728 ops/sec).
- **G (Text Decoration Support & Safe Fallback):** Strips unsupported text decorations (`<u>`, `<st>`, `<obf>`) from dynamic placeholders with rate-limited warning, preventing font renderer line artifacts or quad disappearance.
- **I (GLSL Portability):** Fixed implicit integer casts (`float x = 0;` -> `0.0`, `vec4(1, 1, 1, ...)` -> `vec4(1.0, 1.0, 1.0, ...)`), added explicit `default: { break; }` to switch statements across all overlays (verified: Mesa Intel, glslangValidator; unverified on NVIDIA/AMD/Apple).

### Added
- **F (Configuration Parity Analyzer):** Standalone configuration parity tool in `tools/config-parity-tool/` analyzing real server deployments (`BETTERHUD_CONFIG_DIR`).
- **J (Cross-Vendor Testing Guide):** Published `docs/TESTING.md` with step-by-step instructions for `debug_probe_popup`, live health/food bar mode switching, and F3 diagnostic logging.

## [2.2.0-fork.2] - 2026-10-25

### Fixed
- **H1 (Italic Compensation Across Y):** Fixed vertex shear compensation formula in core shaders (`text.vsh`) to account for arbitrary layout Y positions (`0.25 * (pos.y - DEFAULT_OFFSET - isBottom * 8.0)`), eliminating horizontal position discrepancy (hardware GPU verified: 0.000000 px discrepancy).
- **H3 (Debug Leak Prevention):** Fixed leaks in debug popups: tasks are cancelled and held popups are cleared on `/hud reload`, player quit, and plugin disable; debug JSON output in chat is bounded to 500 characters.
- **H4 (Bedrock & Mode Safety):** Enforced unconditional server mode for Bedrock Edition players regardless of player mode overrides; `/hud animation-mode` immediately reloads player HUD; added reflection-safe Floodgate detection on Velocity and Fabric.
- **H6 (Animation Wrap-Around Safety Latch):** Added safety latch in layout runner returning empty component when elapsed exceeds safe window (`elapsed >= M - lookahead`), eliminating un-animated ghost frame flash after animation completion; added `resetSpawnTime` on popup updater life extension.
- **H8 (World Change & Respawn Resync):** Cleared active popups and listener animation trackers on player world change and respawn events.
- **H9 & C3 (Text Transformation Caching):** Added bounded caches for `applyClientExact` and `applyClientExactDual` (with component width included in cache keys) to eliminate redundant component allocations on unchanged text ticks.
- **H10 (Config Validation & Safe Fallbacks):** Added safe fallbacks for unrecognized config enums, clamping warnings for out-of-range numerical values, and deprecation warnings for keys left in `config.yml`.
- **C1 (Equation Modulo Parity):** Switched `%` operator in `GlslTranspiler` to truncated remainder (`trunc()`), matching exp4j's Java `%` behavior for negative numbers.
- **C2 (Layout Slot Overflow Protection):** Added guard against allocating beyond 1023 unique shader layouts, safely falling back to static rendering with clear warning.
- **C4 (Lazy Bootstrap Access):** Converted `VOLATILE_CODE` to a lazy property getter in `Plugins.kt` to prevent static class initialization NPEs before BetterHudAPI is mocked.

### Added
- Property-based fuzz test running 10,000 random component trees through exact and dual text pipelines.
- Comprehensive time boundary test with 5,000 runs around Minecraft day-cycle boundaries (0, 24000, 48000).
- Expanded hardware GPU transform feedback tests covering regular, italic, bold, and bold+italic glyph quads across Y coordinates.

## [2.2.0-fork.1] - 2026-10-25

### Added
- **GPU-Accelerated Client Core Shader Animations:**
  - Client-side execution of layout movements (`loop`, `play_once`), progress bars (`listener`), and PNG sequences (`sequence`).
  - Seamless 144+ FPS refresh-rate rendering using client `GameTime` and modulo day-cycle time synchronization.
- **Lossless 24-Bit RGB Exact Text Mode (`text-client-mode: exact`):**
  - Full 24-bit RGB color gamut ($16,777,216$ colors) for client-side play-once text popups.
  - Zero palette quantization or nearest-color approximation error ($\Delta = 0$).
  - Supports MiniMessage hex colors, dynamic gradients, and animated placeholders.
- **Dual-Copy Exact Text Shadow Mode (`text-shadow-mode: dual`):**
  - Dual-copy text emission (`[Copy 1: Shadow Layer] + [Backstep: -W] + [Copy 2: Text Layer]`).
  - Renders true text color and true shadow color simultaneously without requiring OpenGL Z-buffering.
  - Selects optimal day-cycle divisor $M \in \{1..120\}$ to prevent time wrapping discontinuities.
  - Single-copy optimization when text leaves have no shadow defined.
  - Configurable fallback policy (`text-shadow-fallback: exact|server`) for popups exceeding duration window.
- **Dual Layout Dispatcher Architecture:**
  - Completely separated client layout IDs from server layout IDs ($2^{10} = 1024$ capacity, $< 6\%$ utilized).
  - Independent font file compilation (`_client.json`).
- **Per-Player Animation Mode Switching:**
  - Added command `/hud animation-mode <player|self> <client|server|auto>`.
  - Automatic platform detection for Bedrock Edition players (GeyserMC / Floodgate) falling back cleanly to server rendering.
- **Configuration & Maintenance:**
  - Configuration keys consolidated in `shader.yml`: `animation-mode`, `text-client-mode`, `text-shadow-mode`, `text-shadow-fallback`, `animation-lookahead-max`, `delete-shaders-on-start`.
  - Automatic cleanup and template regeneration via `delete-shaders-on-start`.
  - Debug assets and probe symbols gated behind `animation-debug-probe` and `debug` flags.
- **Offline Test Suite:**
  - `TextExactCodecTest`: CPU codec test covering 2,000,000 random RGB samples and channel sweeps.
  - `ShaderLintTest`: GLSL compilation verification across all 5 Minecraft overlays (`betterhud_1_21_4` to `betterhud_26_3`) via `glslangValidator`.
  - `CommandTreeTest`: Brigadier command hierarchy and parameter annotation regression test.
  - `ClientAnimationTest` & `ClientAnimationBenchmarkTest`: Time mathematics and traffic reduction verification.

### Changed
- Decoupled bStats metrics and update checker from upstream project.
- Relocated shader and animation configuration keys to `shader.yml`.
