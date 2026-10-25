# Changelog

All notable changes to BetterHud (Client-Side Animation Fork) will be documented in this file.

## [2.2.0-fork.1] - 2026-10-25

### Added
- **GPU-Accelerated Client Core Shader Animations:**
  - Client-side execution of layout movements (`loop`, `play_once`), progress bars (`listener`), and PNG sequences (`sequence`).
  - Seamless 144+ FPS refresh-rate rendering using client `GameTime` and modulo day-cycle time synchronization.
  - Over 95% reduction in HUD networking traffic and server tick overhead.
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
