# Client Animation Reference & Loop Duration Guide

Minecraft client vertex shaders interpolate animations continuously using client GPU time: `GameTime * 24000.0` ticks.

Minecraft's `GameTime` uniform runs from `0.0` to `1.0` every day cycle and wraps around every **24,000 ticks** (20 real-world minutes).

---

## The 20-Minute Phase Jump (Midnight Discontinuity)

For `type: loop` animations, the shader evaluates equation time as `mod(GameTime * 24000.0, duration)`.

- If `24000 % duration == 0`:
  The animation completes an integer number of full cycles in exactly 24,000 ticks. At midnight, when `GameTime` resets from `1.0` to `0.0`, the animation phase is continuous with **zero visual jump or glitch**.
- If `24000 % duration != 0`:
  At midnight, the animation jumps by `24000 % duration` ticks, creating a visible hitch or phase discontinuity once every 20 minutes.

The plugin automatically detects non-divisor loop durations at load time and emits a warning in the console:
```text
Client animation '<name>' has loop duration=<duration> which does not divide 24000 (remainder = <rem>). Animation will experience a phase jump once per 20 minutes (midnight). Consider using a divisor of 24000 (e.g., 20, 30, 40, 60, 80, 100, 120, 200, 240).
```

---

## Recommended Divisors of 24000

When designing `type: loop` client animations, choose a duration (period) from the table below:

| Duration (Ticks) | Period in Seconds | Cycles per Day (20 min) | Recommended Use Case |
|:----------------:|:-----------------:|:-----------------------:|:---------------------|
| **2**            | 0.10s             | 12,000                  | Rapid flicker/pulse  |
| **4**            | 0.20s             | 6,000                   | Fast shimmer         |
| **5**            | 0.25s             | 4,800                   | Fast blinking icon   |
| **6**            | 0.30s             | 4,000                   | Fast rotation        |
| **8**            | 0.40s             | 3,000                   | Subtle oscillation   |
| **10**           | 0.50s             | 2,400                   | Heartbeat / pulse    |
| **12**           | 0.60s             | 2,000                   | Smooth bobbing       |
| **15**           | 0.75s             | 1,600                   | Floating icon        |
| **16**           | 0.80s             | 1,500                   | Idle floating        |
| **20**           | 1.00s             | 1,200                   | **Standard 1-second loop** |
| **24**           | 1.20s             | 1,000                   | Gentle bounce        |
| **25**           | 1.25s             | 960                     | Smooth sway          |
| **30**           | 1.50s             | 800                     | Slow pulse / glow    |
| **40**           | 2.00s             | 600                     | **Standard 2-second loop** |
| **48**           | 2.40s             | 500                     | Breathing animation  |
| **50**           | 2.50s             | 480                     | Slow bobbing         |
| **60**           | 3.00s             | 400                     | **Standard 3-second loop** |
| **80**           | 4.00s             | 300                     | Slow floating badge  |
| **100**          | 5.00s             | 240                     | Ambient background   |
| **120**          | 6.00s             | 200                     | Very slow sweep      |
| **200**          | 10.00s            | 120                     | Idle panorama / scan |
| **240**          | 12.00s            | 100                     | Ambient slow cycle   |
| **300**          | 15.00s            | 80                      | Banner rotation      |
| **400**          | 20.00s            | 60                      | Slow color phase     |
| **600**          | 30.00s            | 40                      | Half-minute cycle    |
| **1200**         | 60.00s (1 min)    | 20                      | 1-minute cycle       |

### Invalid Durations to Avoid for `LOOP`
Durations such as **7, 9, 11, 13, 14, 17, 18, 19, 21, 22, 23, 26, 27, 28, 31, 33, 35, 37, 45, 55, 70, 90** do **NOT** divide 24000 and will experience phase jumping at midnight.
