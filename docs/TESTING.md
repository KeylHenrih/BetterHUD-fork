# BetterHUD Verification & Testing Guide

This guide provides a comprehensive checklist for testing BetterHUD client-side shader animations.

> [!NOTE]
> **Текущий статус проверки драйверов / Driver Verification Status:**
> - Проверено на оборудовании: **Mesa Intel** (RPL-P, OpenGL 4.6 Core Profile), **glslangValidator** (0 ошибок валидации GLSL).
> - На **NVIDIA**, **AMD Radeon**, **Apple Silicon / Metal** работа на реальном железе **не проверена**.
> - Тестирование по данной инструкции на видеокартах NVIDIA/AMD/Apple приветствуется.

---

## 1. Prerequisites & Installation

1. Copy the built `BetterHud-bukkit-2.2.0-fork.3.jar` into your server's `plugins/` directory.
2. Ensure you are running Minecraft Java Edition 1.21.4 through 1.21.6 / 26.1+.
3. Join the server and verify that the plugin loaded cleanly:
   ```
   /hud reload
   ```

---

## 2. Test 1: GPU Shader Self-Test Matrix (`debug_probe_popup`)

This test directly probes hardware vertex shader calculations (alpha decoding, channel precision, and vertex displacement).

### Steps:
1. Open `plugins/BetterHud/config.yml` on the server and set:
   ```yaml
   animation-debug-probe: true
   ```
2. Reload BetterHUD:
   ```
   /hud reload
   ```
3. Spawn and hold the test matrix:
   ```
   /hud debug-popup debug_probe_popup hold
   ```

### Expected Visual Result:
You should see 4 horizontal rows of colored indicator bars across the center of the screen:
- **Row 1 (Alpha Probe, 0..255):** Position 0 is blank (collapsed off-screen as expected). Positions 1 through 255 are **solid bright green**.
- **Row 2 (Red Probe, 0..255):** All 256 positions are **solid bright green**.
- **Row 3 (Green Probe, 0..255):** All 256 positions are **solid bright green**.
- **Row 4 (Blue Probe, 0..255):** All 256 positions are **solid bright green**.

> [!NOTE]
> If any square appears red, magenta, black, or scattered across the screen, your GPU compiler has an alpha clamp or arithmetic discrepancy. Please capture an F3 screenshot and report it.

4. Dismiss the probe:
   ```
   /hud debug-popup debug_probe_popup clear
   ```
5. Disable `animation-debug-probe: false` in `config.yml` when testing is complete.

---

## 3. Test 2: Live Animation Mode Switching (`health` and `food` bars)

To clearly see the difference between server-side 20 Hz discrete updates and client-side 144+ FPS hardware interpolation, use **live dynamic bars** rather than static text:

### Steps:
1. Ensure your player's animation mode is set to server mode:
   ```
   /hud animation-mode self server
   ```
2. Take damage (e.g. `/damage 4` or jump from a height).
   - **Observation (Server Mode):** The health bar updates in discrete steps matching server ticks (20 Hz, stepped animation).
3. Switch to client-side GPU animation:
   ```
   /hud animation-mode self client
   ```
4. Take damage again or heal up.
   - **Observation (Client Mode):** The health bar interpolates smoothly with 144+ FPS refresh rate without stepped lag.
5. Restore default auto mode:
   ```
   /hud animation-mode self auto
   ```

---

## 4. Test 3: Popup Freeze Parity (`hold` test)

Verify that popups hold on their final frame without cyclic re-triggering or premature vanishing:

```
/hud debug-popup damage-indicator-1 hold
```
- **Expected:** The popup animates along its trajectory once, reaches its final resting position, and remains frozen in place.
- **Verification:** Confirm that it does NOT restart every 10 ticks and does NOT disappear after 60–120 ticks.
- **Dismiss:**
  ```
  /hud debug-popup damage-indicator-1 clear
  ```

---

## 5. Capturing Debug Information & Logs

When reporting test results across different hardware:
1. Press `F3` in-game.
2. Note the top-right information block:
   - **Display resolution & refresh rate:** e.g. `2560x1440@165Hz`
   - **GPU Vendor & Model:** e.g. `NVIDIA GeForce RTX 4080/PCIe/SSE2` or `Apple M3 Max` or `AMD Radeon RX 7900 XTX`
   - **OpenGL Driver Version:** e.g. `4.6.0 NVIDIA 550.54.14` or `4.1 Metal - 88`
3. Press `F2` to take a screenshot.
4. Check client logs (`.minecraft/logs/latest.log`) for any lines matching `[BetterHud]` or `OpenGL debug message`.
