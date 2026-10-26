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
2. **Lossless 24-bit RGB Text Popups (`text-client-mode: exact`):**
   - Transmits true 24-bit RGB colors (16,777,216 colors) to client vertex shaders with zero palette quantization error.
   - Full support for MiniMessage hex colors, dynamic gradients, and animated placeholders.
3. **Dual-Copy Exact Text Shadow Mode (`text-shadow-mode: dual`):**
   - Emits dual copies (shadow carrier layer + text carrier layer) to display both exact text color and exact shadow color.
   - Independent of OpenGL Z-buffer or depth testing; guarantees proper layering via 2D buffer submission order.
4. **Dispatcher ID Separation:**
   - Independent dispatcher IDs for client and server layouts (capacity 1024, under 6% utilized).
   - Generates independent client font definitions (`_client.json`), allowing seamless per-player switching.
5. **Per-Player Runtime Switching:**
   - `/hud animation-mode <player|self> <client|server|auto>` allows players or admins to toggle animation modes instantly without restarting the server (implemented, not verified in live client with multiple concurrent players).
   - Bedrock Edition players (via GeyserMC / Floodgate) automatically receive server-side rendering with zero setup.
6. **Diagnostic & Debug Suite:**
   - `/hud debug-anim`: Live element inspect tree showing real-time client/server registration and modulus divisors.
   - `/hud debug-popup <popup> [hold|stop]`: Freezes popups on screen for visual inspection and alignment.

---

## HUD Elements and Progress Bars (Listener Bars)

### Which elements run in client mode (144+ FPS):
Client shader mode supports smooth hardware progress fill for images configured with `type: listener`:
- **Health Bar:** Continuous smooth interpolation when taking damage or regenerating health.
- **Food Bar:** Smooth filling of the hunger level bar.
- **Experience Bar:** Continuous smooth progression of player level and exp ratio.
- **Air / Oxygen Bar:** Smooth depletion of oxygen bar while submerged underwater.
- **Custom RPG Stats (Mana, Stamina, Energy, Shield):** Any progress bar bound to numeric placeholders (`(number)string:player-mana`).

### How the mode is determined:
1. In the image configuration file (`images/<name>.yml`), `type: listener` is defined.
2. If `mode: client` is set in the `setting:` block or top-level (or globally via `animation-mode: client` / `hybrid`), the image is not split into 25–200 individual frame sprites; instead, it is generated as a unified texture (`_client.png`).
3. The server transmits vertex color channels: `Color.g` (start value `from`), `Color.b` (target value `to`), and `Color.r` (start tick `tStart`). The vertex shader interpolates the clipping coordinate (`hudFill.x`) based on in-game `GameTime` using the configured easing function (`linear`, `ease_out_cubic`, `ease_out_quad`, etc.).

### What remains in server mode (SERVER):
- **Discrete Icon Sequences (Sequence):** Sprites with discrete frame steps or separated heart icons without smooth clipping.
- **Player Heads (Head):** Rendered on the server due to dynamic Mojang skin fetching, which does not support shader UV modification.
- **Numeric Text Counters (HP/Mana Numbers):** Numeric text placeholders (`HP: 18/20`) update at server tick rate (20 TPS), while the graphical bar beneath them moves at monitor refresh rate (144+ FPS).
- **Static Backgrounds (Single):** Background backing sprites (`empty_bar.png`) that do not require dynamic fill animation.

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
# - hybrid: client animations for Java players and server fallback for Bedrock players (Floodgate/Geyser). Implemented, not verified with live Bedrock clients.
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
   - The dual-copy scheme utilizes alpha ranges from 1 to M (text) and from M+1 to 2M (shadow), requiring: 2M no greater than 240 (strictly below 255).
   - The maximum duration for dual text shadow is approximately 115 ticks (M no greater than 120).
   - Popups exceeding 115 ticks automatically fall back according to `text-shadow-fallback` (`exact` smooth client animation without shadow, or `server` 20 Hz rendering with shadow).
2. **Text Formatting & Decorations:**
   - `bold` and `italic` are fully supported (italic shear displacement is compensated in vertex shaders).
   - `underlined`, `strikethrough`, and `obfuscated` decorations: layouts containing these styles (`<u>`, `<st>`, `<obf>`) automatically fall back to server-side rendering with a console warning, preventing font renderer line geometry artifacts.
3. **Shadow Alpha Channel:**
   - Minecraft's font renderer strips the alpha channel from text component colors (alpha is always 255). Only RGB of shadow colors is rendered; custom shadow alpha is ignored.
4. **Vertex Count Overhead:**
   - Dual text shadow mode emits two string copies per shadowed line, doubling vertex count for shadowed text popups.
5. **Player Heads & Dynamic Skins:**
   - Player heads remain rendered server-side (`[SERVER]`) due to dynamic Mojang skin fetching.
6. **Bedrock Edition (Geyser / Floodgate):**
   - Bedrock clients do not execute Java Edition core shaders and automatically receive server-side rendering.

---

## Administration Commands

- `/hud popup show <player|all> <popup>`: Displays the specified popup to a player or all online players (e.g. `/hud popup show all damage_indicator_1` or `/hud popup show Steve item_pickup`).
- `/hud debug-popup <popup> [hold|stop]`: Displays or freezes a popup on screen for visual inspection and alignment (`hold` to freeze indefinitely, `stop` to remove).
- `/hud animation-mode <self|player> [client|server|auto]`: Queries or changes animation mode for yourself or a target player without server reload (implemented, not verified in live client with multiple concurrent players).
  - `auto`: Uses client animations on Java Edition, falls back to server mode on Bedrock Edition.
  - `server`: Forces classic server-side rendering (20 TPS).
  - `client`: Forces GPU client-side rendering (144+ FPS).
- `/hud debug-anim [player|all]`: Displays real-time inspection tree of all active HUD elements, their rendering mode (`[CLIENT]` vs `[SERVER]`), registered modulus $M$, and lookahead alpha values.
- `/hud reload`: Safely reloads BetterHud configuration and rebuilds shader resource packs.

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

# Output jar: build/libs/BetterHud-bukkit-2.2.0-fork.4.jar
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
