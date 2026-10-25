package kr.toxicity.hud.tools

import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.text.DualCopyRole
import kr.toxicity.hud.text.applyClientDualRole
import kr.toxicity.hud.text.calculateClientAdvance
import kr.toxicity.hud.text.getDivisorM
import kr.toxicity.hud.text.hasAnyShadow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.assertTrue

class ConfigParityToolTest {

    data class LayoutAnalysis(
        val popupName: String,
        val layoutName: String,
        val filePath: String,
        val mode: String, // CLIENT or SERVER
        val reason: String,
        val moduloM: Int? = null,
        val parityIssues: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf()
    )

    @Test
    fun testAnalyzeConfigurations() {
        val configDirPath = System.getProperty("betterhud.config.dir")
            ?: System.getenv("BETTERHUD_CONFIG_DIR")
            ?: "/home/keyl/Servers/RevakeMain/plugins/BetterHud"

        val configDir = File(configDirPath).let {
            if (it.exists() && it.isDirectory) it
            else File("src/main/resources")
        }

        println("================================================================================")
        println("                BETTERHUD CONFIGURATION PARITY ANALYZER")
        println("================================================================================")
        println("Target Directory: ${configDir.absolutePath}")

        val yaml = Yaml()
        val layoutsDir = File(configDir, "layouts")
        val popupsDir = File(configDir, "popups")

        val analyses = mutableListOf<LayoutAnalysis>()
        val mm = MiniMessage.miniMessage()

        // 1. Scan layouts
        val layoutFiles = if (layoutsDir.exists()) {
            layoutsDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".yml") || it.name.endsWith(".yaml")) }.toList()
        } else emptyList()

        for (file in layoutFiles) {
            val relativePath = file.relativeTo(configDir).path
            val rawData = runCatching { yaml.load<Map<String, Any>>(file.readText()) }.getOrNull() ?: continue

            for ((layoutKey, layoutVal) in rawData) {
                if (layoutVal !is Map<*, *>) continue
                @Suppress("UNCHECKED_CAST")
                val layoutMap = layoutVal as Map<String, Any>

                val animMap = layoutMap["animation"] as? Map<*, *> ?: layoutMap["animations"] as? Map<*, *>
                val hasHead = layoutMap.containsKey("head") || layoutMap.containsKey("heads")
                val textMap = layoutMap["text"] as? Map<*, *> ?: layoutMap["texts"] as? Map<*, *>

                val analysis = if (animMap == null) {
                    LayoutAnalysis("", layoutKey, relativePath, "SERVER", "No animation section defined")
                } else {
                    val typeStr = (animMap["type"] as? String)?.uppercase() ?: "PLAY_ONCE"
                    val duration = (animMap["duration"] as? Number)?.toInt() ?: 0
                    val xRaw = animMap["x-equation"] as? String
                    val yRaw = animMap["y-equation"] as? String
                    val lookahead = 10

                    if (hasHead) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Contains player/custom heads")
                    } else if (duration <= 0) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Unbounded lifetime (duration <= 0)")
                    } else if (xRaw == null || yRaw == null) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Missing x-equation or y-equation")
                    } else if (duration + lookahead > (240 - lookahead)) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Duration ($duration) exceeds safe window (220 ticks)")
                    } else {
                        // Check GLSL transpilation
                        val transpileResult = runCatching {
                            GlslTranspiler.transpile(xRaw)
                            GlslTranspiler.transpile(yRaw)
                        }
                        if (transpileResult.isFailure) {
                            LayoutAnalysis("", layoutKey, relativePath, "SERVER", "GLSL transpilation error: ${transpileResult.exceptionOrNull()?.message}")
                        } else {
                            val req = duration + lookahead + 4
                            val m = getDivisorM(req) ?: 240
                            LayoutAnalysis("", layoutKey, relativePath, "CLIENT", "Transpiled to GLSL shader (M=$m)", moduloM = m)
                        }
                    }
                }

                // Check text decorations & component parity
                if (textMap != null) {
                    for ((_, textVal) in textMap) {
                        val pattern = when (textVal) {
                            is String -> textVal
                            is Map<*, *> -> textVal["pattern"] as? String ?: textVal["text"] as? String ?: ""
                            else -> ""
                        }
                        if (pattern.isNotEmpty()) {
                            checkTextParity(pattern, mm, analysis)
                        }
                    }
                }

                analyses.add(analysis)
            }
        }

        // Summary counts
        val clientCount = analyses.count { it.mode == "CLIENT" }
        val serverCount = analyses.count { it.mode == "SERVER" }
        val totalIssues = analyses.sumOf { it.parityIssues.size }
        val totalWarnings = analyses.sumOf { it.warnings.size }

        println("\n--- SUMMARY ---")
        println("Total Layouts Analyzed: ${analyses.size}")
        println("Client Shader Animated: $clientCount")
        println("Server Side Fallback:   $serverCount")
        println("Parity Errors:          $totalIssues")
        println("Compatibility Warnings: $totalWarnings")

        println("\n--- SERVER FALLBACK LAYOUTS (with reasons) ---")
        val fallbacks = analyses.filter { it.mode == "SERVER" }
        if (fallbacks.isEmpty()) {
            println("None (all layouts are client-side animated).")
        } else {
            fallbacks.forEach {
                println("• [${it.filePath}] -> ${it.layoutName}:")
                println("    Reason: ${it.reason}")
            }
        }

        println("\n--- CLIENT-ANIMATED LAYOUTS (with Modulo M) ---")
        analyses.filter { it.mode == "CLIENT" }.forEach {
            println("• [${it.filePath}] -> ${it.layoutName} (M=${it.moduloM})")
            it.warnings.forEach { w -> println("    [WARN] $w") }
            it.parityIssues.forEach { err -> println("    [ERROR] $err") }
        }

        println("\n================================================================================")
        println("Parity Status: ${if (totalIssues == 0) "PASS" else "FAIL"}")
        println("================================================================================")

        assertTrue(totalIssues == 0, "Configuration parity tool found $totalIssues discrepancies between server and client!")
    }

    private fun checkTextParity(pattern: String, mm: MiniMessage, analysis: LayoutAnalysis) {
        val parsed = runCatching { mm.deserialize(pattern) }.getOrNull() ?: return

        // Check unsupported decorations
        fun checkDecorations(comp: Component) {
            val style = comp.style()
            if (style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE) {
                analysis.warnings.add("Text '$pattern' contains <u>: Minecraft text core shader drops underline")
            }
            if (style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE) {
                analysis.warnings.add("Text '$pattern' contains <st>: Minecraft text core shader drops strikethrough")
            }
            if (style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) {
                analysis.warnings.add("Text '$pattern' contains <obf>: Minecraft text core shader drops obfuscated")
            }
            comp.children().forEach { checkDecorations(it) }
        }
        checkDecorations(parsed)

        // If client animated, verify exact dual rewrite parity
        val m = analysis.moduloM ?: 30
        val widthComp = WidthComponent(Component.text().append(parsed), 100)
        val advance = calculateClientAdvance(widthComp)

        if (parsed.hasAnyShadow()) {
            val copyShadow = parsed.applyClientDualRole(DualCopyRole.SHADOW, 0, m)
            val copyText = parsed.applyClientDualRole(DualCopyRole.TEXT, 0, m)

            // Copy 1 shadow alpha must be in (M+1)..(2*M)
            val shadowColor = copyShadow.style().shadowColor()
            if (shadowColor != null && shadowColor.value() != 0) {
                val a = shadowColor.alpha()
                if (a < m + 1 || a > 2 * m) {
                    analysis.parityIssues.add("Shadow alpha ($a) out of bounds for M=$m (expected ${m + 1}..${2 * m})")
                }
            }

            // Copy 2 text alpha must be in 1..M
            val textColor = copyText.style().shadowColor()
            if (textColor != null && textColor.value() != 0) {
                val a = textColor.alpha()
                if (a < 1 || a > m) {
                    analysis.parityIssues.add("Text alpha ($a) out of bounds for M=$m (expected 1..$m)")
                }
            }
        }
    }
}
