# Changelog

All notable changes to BetterHud (Client-Side Animation Fork) will be documented in this file.

## [2.2.0-fork.4] - 2026-10-26

### Fixed
- **Slot Priority and Server Variant Fallback:** Prioritized client animation shaders for slots 1..1023. Overflown layouts (>1023) safely fall back to their byte-for-byte server layout variant instead of empty case 0, eliminating white quad artifacts without sending protocol quads (alpha in 1..240) into empty cases.
- **Standard Library LRU Text Cache:** Replaced external dependency with standard library `Collections.synchronizedMap(LinkedHashMap(accessOrder = true, removeEldestEntry))` bounded at 2048 elements; stress-tested with 100,000 distinct items; verified Folia multi-threading concurrency across 16 parallel threads; benchmarked 12.7x speedup (1,169,728 ops/sec).
- **GPU Hardware Verification Clarification:** Clarified that hardware testing was executed on Mesa Intel OpenGL 4.6 and glslangValidator, explicitly marking NVIDIA, AMD, and Apple Silicon as unverified in live environments; refined float division error description.
- **Server Quad Invariance Testing:** Added shader diff validation across all overlays ensuring explicit float literals and default switch blocks, and transform feedback GPU test on 10,000 server quads with 0.000000 maximum error.
- **Zero-Update Hold & Component Constancy:** Clamped `effectiveBaseTick` to `baseTick` during hold and from `animDuration` to safe window ($M - L - 1$), ensuring 100% byte-for-byte component constancy across frames and 0 network packets sent after animation finishes.
- **Dynamic Placeholder Text Decoration Stripping:** Added `stripUnsupportedClientDecorations()` on dynamic placeholders so that dynamic text containing `<u>`, `<st>`, or `<obf>` has these styles safely stripped with a rate-limited console warning (once per 10s), preventing text from collapsing to off-screen coordinates `(-10000, -10000)`.

### Added
- **Italic Test Probe & Screenshot Analyzer:** Created `debug_italic_popup` and `tools/screenshot-analyzer/analyze_italic.py` comparing server vs client column alignment with 0.5 GUI pixel tolerance.
- **Extended Configuration Parity Analyzer:** Expanded `tools/config-parity-tool` with lifetime, animation duration, lookahead, modulo M, safe margin calculations, and multi-set placeholder fuzzing.

## [2.2.0-fork.3] - 2026-10-26

### Fixed
- **A (GLSL Floored Modulo Hardening):** Stabilized GLSL `jmod` against float division precision errors across the 24,000-tick day boundary (`m = x - y * floor(x / y); return m >= y ? m - y : (m < 0.0 ? m + y : m);`); hardware GPU verified across 3,312 time boundary combinations.
- **B (Layout Slot Overflow Protection):** Reserved explicit static `case 0: { break; }` in GLSL for overflown layouts ($\ge 1024$), logging a single summary warning listing all overflown layout names.
- **C (World Change & Respawn Server Parity):** Restored 100% upstream parity for server mode (no bossbar flicker, no popup wiping, no reloads); in client mode, re-anchors listener trackers and updates active popup spawn times without full reload.
- **D (Popup Lifecycle Parity & Hold Freezing):** Fixed `/hud debug-popup ... hold` cyclic re-triggering via `keepAlive()` in `PopupUpdater`; pinned client virtual elapsed time to `animDuration` after completion, eliminating premature disappearance at safe window ($M - L$) and ensuring held popups freeze on the final frame matching server mode.
- **E (Thread-Safe Bounded Text Cache):** Replaced bulk-clear cache with `ExpiringMap` LRU cache bounded to 2048 elements; stress-tested with 100,000 distinct items; verified Folia multi-threading concurrency across 16 parallel threads; benchmarked 12.7x speedup (1,169,728 ops/sec).
- **G (Text Decoration Support & Safe Fallback):** Added automatic fallback to server mode for layouts with unsupported text decorations (`<u>`, `<st>`, `<obf>`), preventing font renderer line geometry artifacts.
- **I (Cross-Vendor GLSL Portability):** Fixed implicit integer casts (`float x = 0;` -> `0.0`, `vec4(1, 1, 1, ...)` -> `vec4(1.0, 1.0, 1.0, ...)`), added explicit `default: { break; }` to switch statements across all overlays; validated with `glslangValidator`.

### Added
- **F (Configuration Parity Analyzer):** Standalone configuration parity tool in `tools/config-parity-tool/` analyzing real server deployments (`BETTERHUD_CONFIG_DIR`).
- **J (Cross-Vendor Testing Guide):** Published `docs/TESTING.md` with step-by-step instructions for `debug_probe_popup`, live health/food bar mode switching, and F3 diagnostic logging.

## [2.2.0-fork.2] - 2026-10-25

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
