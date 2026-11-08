# Proposal: Dual-Variant HUD Layout Architecture for BetterHud

## 1. Problem Statement & Motivation
Currently in BetterHud (fork `client-animations`):
- Popups support per-player mode switching (`/hud animation-mode <self|player> <client|server|auto>`) and automatic fallback for Bedrock players (via GeyserMC / Floodgate), because popups instantiate independent `PopupIterator` and `PopupLayout` instances per player.
- In contrast, **HUD elements** (`ImageType.LISTENER` health/progress bars and `LOOP` animations) are compiled globally at pack generation time based on `animation-mode` in `shader.yml`.
- If `animation-mode: client` is configured globally:
  - Java Edition clients receive smooth 144+ FPS hardware interpolation in core vertex shaders (`text.vsh`).
  - However, Bedrock Edition players (RenderDragon engine cannot parse Java core shaders) and Java players on incompatible Iris/OptiFine third-party shaderpacks receive shader time-tags and carrier glyphs, resulting in visual artifacts or broken progress bars.
  - Calling `/hud animation-mode self server` changes the player's preference, but HUD elements continue emitting client shader carrier components.

---

## 2. Proposed Architectural Solution

To achieve true per-player parity for persistent HUD elements, the plugin needs to compile **two parallel layout representations** for each HUD element at pack generation time and route them conditionally per player at runtime:

```
                      +-----------------------------+
                      |   Hud Layout Registration   |
                      +--------------+--------------+
                                     |
               +---------------------+---------------------+
               |                                           |
    [Variant A: Server/Static]                   [Variant B: Client Shader]
    - Upstream frame-sprite array                - Single unified texture (_client.png)
    - Discrete 0..100% sprites                   - Vertex shader hardware fill & interpolation
    - Font: betterhud:image_...                  - Font: betterhud:image_..._client
    - Dispatcher ID: Standard pool               - Dispatcher ID: Client pool (4096+)
```

---

## 3. Implementation Approaches

### Option 1: Dual Font & Component Generation (Recommended)
1. **Compilation Phase (`ImageManager`, `LayoutManager`):**
   - When compiling a HUD layout, generate both:
     - `variant_server`: Standard sequence of sliced sprites (`_0.png`, `_1.png`...) rendered tick-by-tick by the server task.
     - `variant_client`: Single quad with vertex shader hardware fill (`_client.png`) with `getElapsedTicks()` or listener tracking.
2. **Runtime Routing (`HudPlayerImpl.kt`):**
   - In `HudPlayerImpl`, the HUD update loop checks `isClientAnimationEnabled()` for each player:
     ```kotlin
     val component = if (isClientAnimationEnabled()) {
         hud.clientLayout.getComponent(player)
     } else {
         hud.serverLayout.getComponent(player)
     }
     ```
   - For Bedrock players (`isBedrockPlayer(uuid) == true`) and players in `SERVER` mode, `serverLayout` emits vanilla-compatible standard unicode glyphs.
   - For Java players with shaders, `clientLayout` emits client font carrier glyphs.

### Option 2: Display-Offset / Negative Space Dual Multiplexing
- Emits both variants into the bossbar/actionbar packet simultaneously, but uses negative space glyphs and client vertex shader depth/discard to selectively hide one variant:
  - Java core shader discards the server variant if client mode is active.
  - Vanilla/Bedrock renders only the server variant because client variant glyphs are unmapped in vanilla resource pack font files.
- **Trade-off:** Increases packet size slightly, but requires zero runtime branching in packet assembly.

---

## 4. Migration Plan & Backward Compatibility
1. **Default Safety:**
   - Keep `animation-mode: server` by default in `shader.yml` until dual-variant HUD is fully implemented and tested.
2. **Configuration Toggle:**
   - `hud-dual-variant: true | false` in `shader.yml`.
   - When enabled, pack generator creates both `image_<name>` and `image_<name>_client` fonts.
3. **Dispatcher ID Allocation:**
   - Client layouts already use a reserved high-bit ID range (`0x1000..0x13FF`), leaving standard IDs (`0x0000..0x03FF`) untouched for 100% upstream parity.

---

## 5. Summary
Dual-variant HUD layout architecture eliminates the last architectural limitation of client-side shader animations in BetterHud, allowing Bedrock players and Java shader players to play seamlessly together on the same server with zero compromises.
