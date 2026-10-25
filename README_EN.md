<div align="center">  

![-0001-export](https://github.com/toxicity188/BetterHud/assets/114675706/ccbf4bd3-9133-44ee-b277-985eae4349ae)

# BetterHud (Client-Side Animation Fork)

[🇷🇺 Русская версия](README.md) | [🇬🇧 English version](README_EN.md)

[Fork Repository](https://github.com/KeylHenrih/BetterHUD-fork) | [Upstream Repository](https://github.com/toxicity188/BetterHud)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/github/actions/workflow/status/KeylHenrih/BetterHUD-fork/gradle.yml?branch=client-animations&style=for-the-badge&logo=github)](https://github.com/KeylHenrih/BetterHUD-fork/actions)

</div>

---

## Fork Notice & Upstream Compatibility

> [!IMPORTANT]
> This repository is a specialized fork of [toxicity188/BetterHud](https://github.com/toxicity188/BetterHud) (MIT License, Copyright © 2024–2025 toxicity188).
>
> - **Replacement Plugin:** This fork completely replaces upstream BetterHud on the server. **Do NOT run both jars concurrently.**
> - **100% Server Mode Parity:** When `animation-mode: server` is configured or when handling Bedrock Edition players, the plugin executes 100% identically to upstream BetterHud.
> - **Notice of Modifications:** This fork introduces client-side GPU vertex shader animation pipelines, exact 24-bit text color encoding, dual-copy text shadow compensation, independent dispatcher IDs, and dedicated animation administration commands.

---

## Features Added in this Fork

1. **GPU-Accelerated Client Animations (144+ FPS):**
   - Offloads element translation, alpha fading, and progress transitions to Minecraft Core Shaders (`text.vsh`, `text.fsh`).
   - Animations update on every display refresh frame using `GameTime`, bypassing the 20 Hz server tick limit.
   - Eliminates per-tick packet spam, reducing HUD network traffic by over 95%.
2. **Lossless 24-bit RGB Text Popups (`text-client-mode: exact`):**
   - Transmits true 24-bit RGB colors ($16,777,216$ colors) to client vertex shaders with zero palette quantization error ($\Delta = 0$).
   - Full support for MiniMessage hex colors, dynamic gradients, and animated placeholders.
3. **Dual-Copy Exact Text Shadow Mode (`text-shadow-mode: dual`):**
   - Emits dual copies (shadow carrier layer + text carrier layer) to display both exact text color and exact shadow color.
   - Independent of OpenGL Z-buffer or depth testing; guarantees proper layering via 2D buffer submission order.
4. **Dispatcher ID Separation:**
   - Independent dispatcher IDs for client and server layouts ($2^{10} = 1024$ capacity, $< 6\%$ utilized).
   - Generates independent client font definitions (`_client.json`), allowing seamless per-player switching.
5. **Per-Player Runtime Switching:**
   - `/hud animation-mode <player|self> <client|server|auto>` allows players or admins to toggle animation modes instantly without restarting the server.
   - Bedrock Edition players (via GeyserMC / Floodgate) automatically receive server-side rendering with zero setup.
6. **Diagnostic & Debug Suite:**
   - `/hud debug-anim`: Live element inspect tree showing real-time client/server registration and modulus divisors.
   - `/hud debug-popup <popup> [hold|stop]`: Freezes popups on screen for visual inspection and alignment.

---

## Testing & Verification Status

> [!NOTE]
> **Honest Verification Status:**
> - **Live Client Verified:** Tested and confirmed in live game clients **only on Minecraft 26.1.2** (Purpur 26.1.2 server, Vanilla client, and Fabulously Optimized client, verified specifically at **GUI scale 2 and 3**).
> - **Fabulously Optimized (Sodium, Iris):** Fabulously Optimized (Sodium, Iris) with standard resource packs works properly. Behavior with custom third-party shaderpacks (Iris/OptiFine shaderpacks that override core text shaders) has not been tested; in case of visual issues, switch the player to server rendering via `/hud animation-mode self server`.
> - **Bedrock Edition (Geyser / Floodgate):** The automatic selection logic for the server-side fallback is implemented, but has not been tested with a real Bedrock client.
> - **Other Minecraft Overlays:** Overlays `betterhud_1_21_4`, `betterhud_1_21_6`, `betterhud_26_2`, and `betterhud_26_3` are verified via offline GLSL compilation tests (`ShaderLintTest` / `glslangValidator`) only; they have not been verified in live clients of those versions.

---

## Configuration (`shader.yml`)

Client shader parameters are configured in `plugins/BetterHud/shader.yml`:

```yaml
# Animation execution mode:
# - client: offloads animations to client vertex shaders where supported.
# - server: classic server-tick rendering (100% vanilla compatibility).
# - hybrid: listener bars in client mode, remaining elements in server mode.
animation-mode: client

# Text rendering mode in client popups:
# - exact: lossless 24-bit RGB colors without quantization.
# - off: server-rendered text fallback.
text-client-mode: exact

# Text shadow rendering mode in client popups:
# - dual: emits dual copies (shadow layer + text layer) with exact shadow and text colors.
# - off: single-copy text without shadow (saves buffer vertices).
text-shadow-mode: dual

# Fallback policy when text shadow is unavailable (e.g. duration exceeds max window M=120):
# - exact: smooth client animation with exact text color, without shadow.
# - server: server-side rendering with vanilla shadow, at 20 Hz server tick rate.
text-shadow-fallback: exact

# Automatically backup and refresh plugins/BetterHud/shaders directory on startup and reload.
# When enabled, old shaders are safely renamed to shaders.bak-<timestamp> (retaining up to 3 backups).
# Default: false
delete-shaders-on-start: false

# Maximum animation lookahead ticks compensation for player network latency.
animation-lookahead-max: 5

# Debug slow-motion multiplier (1.0 = normal speed).
animation-debug-slowmo: 1.0

# Enable debug probe popups and test matrix (development only).
animation-debug-probe: false
```

> [!TIP]
> **Shader Refresh & Backups (`delete-shaders-on-start`):**
> When set to `true`, BetterHud automatically renames existing `plugins/BetterHud/shaders/` to `shaders.bak-<timestamp>` and extracts fresh shader templates on server start or reload (keeping at most 3 historical backups). Default is `false` to avoid unintentional overwriting of customized shaders.

---

## Known Limitations

1. **Duration Limit for Text Shadow (`text-shadow-mode: dual`):**
   - The dual-copy scheme utilizes alpha ranges $[1..M]$ (text) and $[M+1..2M]$ (shadow), requiring $2M \le 240 < 255$.
   - The maximum duration for dual text shadow is $\sim 115$ ticks ($M \le 120$).
   - Popups exceeding 115 ticks automatically fall back according to `text-shadow-fallback` (`exact` smooth client animation without shadow, or `server` 20 Hz rendering with shadow).
2. **Text Formatting & Decorations:**
   - `bold` and `italic` are fully supported (italic shear displacement is compensated in vertex shaders).
   - `underlined`, `strikethrough`, and `obfuscated` decorations create auxiliary Minecraft quads that are not collapsed by the carrier quad shader logic.
3. **Shadow Alpha Channel:**
   - Minecraft's font renderer strips the alpha channel from text component colors ($A=255$ always). Only RGB of shadow colors is rendered; custom shadow alpha is ignored.
4. **Vertex Count Overhead:**
   - Dual text shadow mode emits two string copies per shadowed line, doubling vertex count for shadowed text popups.
5. **Player Heads & Dynamic Skins:**
   - Player heads remain rendered server-side (`[SERVER]`) due to dynamic Mojang skin fetching.
6. **Bedrock Edition (Geyser / Floodgate):**
   - Bedrock clients do not execute Java Edition core shaders and automatically receive server-side rendering.

---

## Administration Commands

- `/hud animation-mode <player|self> <client|server|auto>`: Changes animation mode for a specific player without server reload.
  - `auto`: Uses client animations on Java Edition, falls back to server mode on Bedrock Edition.
  - `server`: Forces classic server-side rendering.
  - `client`: Enables GPU client-side rendering.
- `/hud debug-anim`: Displays the real-time registry of all active HUD elements, their rendering mode (`[CLIENT]` vs `[SERVER]`), and dual mode $M$ divisors.
- `/hud debug-popup <popup> [hold|stop]`: Displays or freezes a popup for visual inspection.

---

## Building from Source

### Prerequisites
- JDK 25 or higher (e.g. Eclipse Adoptium Temurin 25 or OpenJDK 26)
- Git

### Build Commands
```bash
# Clone the repository
git clone https://github.com/KeylHenrih/BetterHUD-fork.git
cd BetterHUD-fork

# Build all modules and run test suite
./gradlew build --no-daemon

# Build Bukkit/Paper shadow jar
./gradlew :bootstrap:bukkit:shadowJar

# Output jar: build/libs/BetterHud-bukkit-2.2.0-fork.1.jar
```

---

## Technical Architecture & Specification

For detailed mathematical specifications of time synchronization ($M$, $L$, lookahead), vertex quad encoding tables, GLSL shader generation, and dispatcher ID bit manipulation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## License & Copyright

- Upstream Project: [BetterHud](https://github.com/toxicity188/BetterHud) by toxicity188.
- Licensed under the [MIT License](LICENSE).
- Copyright © 2024–2025 toxicity188.
- Fork modifications copyright © 2026 KeylHenrih.
