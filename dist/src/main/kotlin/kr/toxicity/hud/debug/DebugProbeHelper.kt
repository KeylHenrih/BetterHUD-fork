package kr.toxicity.hud.debug

import kr.toxicity.hud.api.placeholder.HudPlaceholder
import kr.toxicity.hud.manager.PlaceholderManagerImpl
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.yaml.YamlObjectImpl
import org.yaml.snakeyaml.Yaml
import java.util.function.Function

object DebugProbeHelper {

    // Main glyph collapse marker prefix (#0102XX)
    // #010203: Strip 1
    // #010204: Strip 2
    // #010205: Strip 3
    // #010206: Strip 4
    // #010207: Strip 4 Reverse
    // #010208: Matrix Strips

    // Strip 1: Shadow Alpha Test (0..255)
    // Main glyph: #010203 (collapsed in shader)
    // Pos 0: ARGB = 0 (<shadow:#00000000>) -> Minecraft skips quad, leaves 1-pixel gap
    // Pos 1: A = 0, R = 0, G = 137, B = 73 -> quad emitted with A = 0
    // Pos 2..256: A = i, R = i (expected), G = 137, B = 73 -> quad emitted with A = i
    val shadowAlphaStrip: String by lazy {
        val sb = StringBuilder()
        sb.append("<color:#010203><shadow:#00000000>|</shadow></color>")
        sb.append("<color:#010203><shadow:#00894900>|</shadow></color>")
        for (i in 1..255) {
            sb.append(String.format("<color:#010203><shadow:#%02X8949%02X>|</shadow></color>", i, i))
        }
        sb.toString()
    }

    // Strip 2: Shadow Red Test (256 values: R = 0..255)
    // Main glyph: #010204 (collapsed in shader)
    // Shadow: R = i (tested), G = i (expected), B = 10, A = 241
    val shadowRedStrip: String by lazy {
        val sb = StringBuilder()
        for (i in 0..255) {
            sb.append(String.format("<color:#010204><shadow:#%02X%02X0AF1>|</shadow></color>", i, i))
        }
        sb.toString()
    }

    // Strip 3: Shadow Green Test (256 values: G = 0..255)
    // Main glyph: #010205 (collapsed in shader)
    // Shadow: R = i (expected), G = i (tested), B = 10, A = 242
    val shadowGreenStrip: String by lazy {
        val sb = StringBuilder()
        for (i in 0..255) {
            sb.append(String.format("<color:#010205><shadow:#%02X%02X0AF2>|</shadow></color>", i, i))
        }
        sb.toString()
    }

    // Strip 4: Shadow Blue Test (256 values: B = 0..255)
    // Main glyph: #010206 (collapsed in shader)
    // Shadow: R = i (expected), G = 10, B = i (tested), A = 243
    val shadowBlueStrip: String by lazy {
        val sb = StringBuilder()
        for (i in 0..255) {
            sb.append(String.format("<color:#010206><shadow:#%02X0A%02XF3>|</shadow></color>", i, i))
        }
        sb.toString()
    }

    // Strip 4 Reverse: Shadow Blue in reverse order (255 downTo 0)
    // Main glyph: #010207 (collapsed in shader)
    // Shadow: R = i (expected), G = 10, B = i (tested), A = 244
    val shadowBlueReverseStrip: String by lazy {
        val sb = StringBuilder()
        for (i in 255 downTo 0) {
            sb.append(String.format("<color:#010207><shadow:#%02X0A%02XF4>|</shadow></color>", i, i))
        }
        sb.toString()
    }

    // Strip 5: Main Glyph Alpha Invariant Test (256 values: R = 0..255, G = 255 - i, B = 222)
    // No shadow quad
    val mainAlphaStrip: String by lazy {
        val sb = StringBuilder()
        for (i in 0..255) {
            sb.append(String.format("<color:#%02X%02XDE>|</color>", i, 255 - i))
        }
        sb.toString()
    }

    // Channel Matrix Strips (Step 3b):
    // Tests sweeping 0..255 on R, G, B with baselines 0, 77, 128, 255.
    fun buildMatrixRed(base: Int, alpha: Int): String {
        val sb = StringBuilder()
        for (i in 0..255) {
            // R = i (tested), G = i (expected), B = base, A = alpha
            sb.append(String.format("<color:#010208><shadow:#%02X%02X%02X%02X>|</shadow></color>", i, i, base, alpha))
        }
        return sb.toString()
    }

    fun buildMatrixGreen(base: Int, alpha: Int): String {
        val sb = StringBuilder()
        for (i in 0..255) {
            // R = i (expected), G = i (tested), B = base, A = alpha
            sb.append(String.format("<color:#010208><shadow:#%02X%02X%02X%02X>|</shadow></color>", i, i, base, alpha))
        }
        return sb.toString()
    }

    fun buildMatrixBlue(base: Int, alpha: Int): String {
        val sb = StringBuilder()
        for (i in 0..255) {
            // R = i (expected), G = base, B = i (tested), A = alpha
            sb.append(String.format("<color:#010208><shadow:#%02X%02X%02X%02X>|</shadow></color>", i, base, i, alpha))
        }
        return sb.toString()
    }

    // Step 4: Shadow Offset Tests (Normal, Bold, Italic, Bold+Italic, Colored Shadow)
    val offsetNormal: String = "<color:#FF0000><shadow:#0066FFFE>OFFSET (NORMAL): The quick brown fox jumps 12345</shadow></color>"
    val offsetBold: String = "<bold><color:#FF0000><shadow:#0066FFFE>OFFSET (BOLD): The quick brown fox jumps 12345</shadow></color></bold>"
    val offsetItalic: String = "<italic><color:#FF0000><shadow:#0066FFFE>OFFSET (ITALIC): The quick brown fox jumps 12345</shadow></color></italic>"
    val offsetBoldItalic: String = "<bold><italic><color:#FF0000><shadow:#0066FFFE>OFFSET (BOLD+ITALIC): The quick brown fox jumps 12345</shadow></color></italic></bold>"
    val offsetItalicColored: String = "<italic><color:#FF0000><shadow:#00FFFFFE>OFFSET (ITALIC+CYAN): The quick brown fox jumps 12345</shadow></color></italic>"

    // Step 5: Z-Order Depth Tests (-100 to +100 Z)
    // Main text: Red (#FF0000)
    // Shadow text: Blue (#0066FF), A indicates Z shift
    fun buildZTest(label: String, alpha: Int): String {
        return String.format("<color:#FF0000><shadow:#0066FF%02X>%s</shadow></color>", alpha, label)
    }

    // Step 6: Vertex Corner Ordering Test (Solid quad glyphs)
    val vertexNormal: String = "<color:#12FE34>████████████████</color>"
    val vertexBold: String = "<bold><color:#12FE34>████████████████</color></bold>"
    val vertexItalic: String = "<italic><color:#12FE34>████████████████</color></italic>"

    fun init() {
        val stringContainer = PlaceholderManagerImpl.stringContainer
        val probes = mutableMapOf(
            "alpha" to shadowAlphaStrip,
            "red" to shadowRedStrip,
            "green" to shadowGreenStrip,
            "blue" to shadowBlueStrip,
            "blue_reverse" to shadowBlueReverseStrip,
            "main_alpha" to mainAlphaStrip,

            // Matrix Red (baselines 0, 77, 128, 255)
            "mat_r_0" to buildMatrixRed(0, 231),
            "mat_r_77" to buildMatrixRed(77, 232),
            "mat_r_128" to buildMatrixRed(128, 233),
            "mat_r_255" to buildMatrixRed(255, 234),

            // Matrix Green (baselines 0, 77, 128, 255)
            "mat_g_0" to buildMatrixGreen(0, 235),
            "mat_g_77" to buildMatrixGreen(77, 236),
            "mat_g_128" to buildMatrixGreen(128, 237),
            "mat_g_255" to buildMatrixGreen(255, 238),

            // Matrix Blue (baselines 0, 77, 128, 255)
            "mat_b_0" to buildMatrixBlue(0, 239),
            "mat_b_77" to buildMatrixBlue(77, 245),
            "mat_b_128" to buildMatrixBlue(128, 246),
            "mat_b_255" to buildMatrixBlue(255, 247),

            // Offset tests
            "offset_normal" to offsetNormal,
            "offset_bold" to offsetBold,
            "offset_italic" to offsetItalic,
            "offset_bold_italic" to offsetBoldItalic,
            "offset_italic_cyan" to offsetItalicColored,

            // Z-Order tests
            "z_m100" to buildZTest("Z-ORDER (-100 Z): BLUE SHADOW", 210),
            "z_m10" to buildZTest("Z-ORDER (-10 Z): BLUE SHADOW", 211),
            "z_m1" to buildZTest("Z-ORDER (-1 Z): BLUE SHADOW", 212),
            "z_m01" to buildZTest("Z-ORDER (-0.1 Z): BLUE SHADOW", 213),
            "z_m001" to buildZTest("Z-ORDER (-0.01 Z): BLUE SHADOW", 214),
            "z_p001" to buildZTest("Z-ORDER (+0.01 Z): BLUE SHADOW", 215),
            "z_p01" to buildZTest("Z-ORDER (+0.1 Z): BLUE SHADOW", 216),
            "z_p1" to buildZTest("Z-ORDER (+1 Z): BLUE SHADOW", 217),
            "z_p10" to buildZTest("Z-ORDER (+10 Z): BLUE SHADOW", 218),
            "z_p100" to buildZTest("Z-ORDER (+100 Z): BLUE SHADOW", 219),

            // Vertex order
            "vertex_normal" to vertexNormal,
            "vertex_bold" to vertexBold,
            "vertex_italic" to vertexItalic
        )

        probes.forEach { (key, value) ->
            stringContainer.addPlaceholder("debug_probe_$key", HudPlaceholder.of { _, _ ->
                Function { value }
            })
        }
        stringContainer.addPlaceholder("debug_probe", HudPlaceholder.builder<String>()
            .requiredArgsLength(1)
            .function { args, _ ->
                val probeKey = args[0]
                val v = probes[probeKey] ?: "<none>"
                Function { v }
            }
            .build()
        )
    }

    fun generateProbeShaderCase(): List<String> {
        return listOf(
            "int actualA = int(round(Color.a * 255.0));",
            "int actualR = int(round(Color.r * 255.0));",
            "int actualG = int(round(Color.g * 255.0));",
            "int actualB = int(round(Color.b * 255.0));",

            // Collapsed main glyphs for strips
            "if (actualR == 1 && actualG == 2 && (actualB >= 3 && actualB <= 8)) {",
            "    pos.xy = vec2(-10000.0);",
            "    vertexColor = vec4(0.0);",
            "}",

            // Strip 1: Shadow Alpha Test (G=137, B=73) -> check actualA == actualR
            "else if (actualG == 137 && actualB == 73) {",
            "    if (actualA == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Strip 2: Shadow Red Test (A=241, B=10) -> check actualR == actualG
            "else if (actualA == 241 && actualB == 10) {",
            "    if (actualR == actualG) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Strip 3: Shadow Green Test (A=242, B=10) -> check actualG == actualR
            "else if (actualA == 242 && actualB == 10) {",
            "    if (actualG == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Strip 4: Shadow Blue Test (A=243, G=10) -> check actualB == actualR
            "else if (actualA == 243 && actualG == 10) {",
            "    if (actualB == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Strip 4 Reverse: Shadow Blue Reversed (A=244, G=10) -> check actualB == actualR
            "else if (actualA == 244 && actualG == 10) {",
            "    if (actualB == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Strip 5: Main Glyph Alpha Invariant (B=222, R+G=255) -> check actualA == 255
            "else if (actualB == 222 && (actualR + actualG == 255)) {",
            "    if (actualA == 255) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Channel Matrix: Red (A=231..234) -> check actualR == actualG
            "else if (actualA >= 231 && actualA <= 234) {",
            "    if (actualR == actualG) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Channel Matrix: Green (A=235..238) -> check actualG == actualR
            "else if (actualA >= 235 && actualA <= 238) {",
            "    if (actualG == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Channel Matrix: Blue (A=239 or A in 245..247) -> check actualB == actualR
            "else if (actualA == 239 || (actualA >= 245 && actualA <= 247)) {",
            "    if (actualB == actualR) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Offset & Z-Order Tests:
            // Main text: Red (#FF0000)
            "else if (actualR == 255 && actualG == 0 && actualB == 0) {",
            "    vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "}",

            // Shadow Blue (offset text: A=254)
            "else if (actualR == 0 && actualG == 102 && actualB == 255 && actualA == 254) {",
            "    vertexColor = vec4(0.0, 0.4, 1.0, 1.0);",
            "}",

            // Shadow Cyan (offset text: A=254)
            "else if (actualR == 0 && actualG == 255 && actualB == 255 && actualA == 254) {",
            "    vertexColor = vec4(0.0, 1.0, 1.0, 1.0);",
            "}",

            // Shadow Blue with Z shifts (A in 210..219)
            "else if (actualR == 0 && actualG == 102 && actualB == 255 && actualA >= 210 && actualA <= 219) {",
            "    if (actualA == 210) pos.z -= 100.0;",
            "    else if (actualA == 211) pos.z -= 10.0;",
            "    else if (actualA == 212) pos.z -= 1.0;",
            "    else if (actualA == 213) pos.z -= 0.1;",
            "    else if (actualA == 214) pos.z -= 0.01;",
            "    else if (actualA == 215) pos.z += 0.01;",
            "    else if (actualA == 216) pos.z += 0.1;",
            "    else if (actualA == 217) pos.z += 1.0;",
            "    else if (actualA == 218) pos.z += 10.0;",
            "    else if (actualA == 219) pos.z += 100.0;",
            "    vertexColor = vec4(0.0, 0.4, 1.0, 1.0);",
            "}",

            // Vertex Corners (Solid block #12FE34)
            "else if (actualR == 18 && actualG == 254 && actualB == 52) {",
            "    int corner = int(gl_VertexID) & 3;",
            "    if (corner == 0) vertexColor = vec4(1.0, 0.0, 0.0, 1.0);",
            "    else if (corner == 1) vertexColor = vec4(0.0, 1.0, 0.0, 1.0);",
            "    else if (corner == 2) vertexColor = vec4(0.0, 0.0, 1.0, 1.0);",
            "    else vertexColor = vec4(1.0, 1.0, 0.0, 1.0);",
            "}",

            "else {",
            "    vertexColor = Color;",
            "}"
        )
    }

    private fun generateItalicLayoutYaml(isClient: Boolean): String {
        val xPos = if (isClient) 40 else -140
        val animSection = if (isClient) """
animations:
  type: play_once
  duration: 60
  x-equation: "0"
  y-equation: "0"
""" else ""

        val heights = listOf(-100, 0, 50, 200)
        val styles = listOf(
            "norm" to "<color:#FFFF00>█</color> <shadow:#FF5555><color:#FFFFFF>Normal",
            "ital" to "<color:#FFFF00>█</color> <shadow:#FF5555><color:#FFFFFF><i>Italic</i>",
            "bold" to "<color:#FFFF00>█</color> <shadow:#FF5555><color:#FFFFFF><b>Bold</b>",
            "bita" to "<color:#FFFF00>█</color> <shadow:#FF5555><color:#FFFFFF><b><i>BoldItalic</i></b>"
        )

        val sb = StringBuilder()
        sb.append("texts:\n")
        for (y in heights) {
            val yKey = if (y < 0) "m${-y}" else "$y"
            for ((idx, s) in styles.withIndex()) {
                val lineY = y + idx * 10
                sb.append("  ${s.first}_$yKey:\n")
                sb.append("    name: default_font\n")
                sb.append("    pattern: \"${s.second} Y=$y</color></shadow>\"\n")
                sb.append("    align: left\n")
                sb.append("    x: $xPos\n")
                sb.append("    y: $lineY\n")
            }
        }
        sb.append(animSection)
        return sb.toString()
    }

    private val debugItalicPopupYaml = """
duration: 100
queue: false
push: false
layouts:
  1:
    name: debug-italic-server
    gui:
      x: 50
      y: 50
  2:
    name: debug-italic-client
    gui:
      x: 50
      y: 50
"""

    fun injectDebugItalicLayouts(consumer: (String, YamlObject) -> Unit) {
        val yamlParser = Yaml()
        val serverYaml = generateItalicLayoutYaml(false)
        val clientYaml = generateItalicLayoutYaml(true)

        val serverMap = (yamlParser.load<Map<*, *>>(serverYaml) ?: emptyMap<Any, Any>()).toMutableMap()
        val clientMap = (yamlParser.load<Map<*, *>>(clientYaml) ?: emptyMap<Any, Any>()).toMutableMap()

        consumer("debug-italic-server", YamlObjectImpl("debug-italic-server", serverMap))
        consumer("debug-italic-client", YamlObjectImpl("debug-italic-client", clientMap))
    }

    fun injectDebugItalicPopup(consumer: (String, YamlObject) -> Unit) {
        val yamlParser = Yaml()
        val popupMap = (yamlParser.load<Map<*, *>>(debugItalicPopupYaml) ?: emptyMap<Any, Any>()).toMutableMap()
        consumer("debug_italic_popup", YamlObjectImpl("debug_italic_popup", popupMap))
    }
}
