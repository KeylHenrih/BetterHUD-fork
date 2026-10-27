package kr.toxicity.hud.tools

import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.text.DualCopyRole
import kr.toxicity.hud.text.applyClientDualRole
import kr.toxicity.hud.text.calculateClientAdvance
import kr.toxicity.hud.text.getDivisorM
import kr.toxicity.hud.text.hasAnyShadow
import kr.toxicity.hud.text.hasUnsupportedClientDecorations
import kr.toxicity.hud.text.stripUnsupportedClientDecorations
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
        val lifetime: Int? = null,
        val lifetimeSource: String? = null,
        val animDuration: Int? = null,
        val lookaheadL: Int? = null,
        val moduloM: Int? = null,
        val margin: Int? = null,
        val parityIssues: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf()
    )

    private val testPlaceholderSets = listOf(
        "Set 1 (Short Text)" to mapOf(
            "custom_variable:text" to "+15",
            "custom_variable:title" to "Notice",
            "custom_variable:msg_1" to "OK",
            "custom_variable:msg_2" to "Done",
            "custom_variable:msg_3" to "Info"
        ),
        "Set 2 (Long Cyrillic)" to mapOf(
            "custom_variable:text" to "Критический урон! +1,450 HP",
            "custom_variable:title" to "Достижение получено!",
            "custom_variable:msg_1" to "Вы успешно завершили сложное испытание в подземелье!",
            "custom_variable:msg_2" to "Награда перенесена в ваш инвентарь.",
            "custom_variable:msg_3" to "Продолжайте исследовать мир Revake."
        ),
        "Set 3 (MiniMessage with Styles)" to mapOf(
            "custom_variable:text" to "<gradient:#FF5555:#FFFF55><b>КРИТ!</b></gradient> <shadow:#000000><u><st>+999</st></u></shadow>",
            "custom_variable:title" to "<gradient:#55FF55:#55FFFF><b>НОВЫЙ УРОВЕНЬ!</b></gradient>",
            "custom_variable:msg_1" to "<color:#FFAA00>Поздравляем!</color> <shadow:#330000>Вы достигли <yellow><b>50 уровня</b></yellow></shadow>",
            "custom_variable:msg_2" to "Доступны новые <u>навыки</u> и <i>способности</i>",
            "custom_variable:msg_3" to "<gradient:#FF55FF:#5555FF>Нажмите [J] для открытия меню</gradient>"
        )
    )

    @Test
    fun testAnalyzeConfigurations() {
        val configDirPath = System.getProperty("betterhud.config.dir")
            ?: System.getenv("BETTERHUD_CONFIG_DIR")
            ?: "common-resources/default"

        val configDir = sequenceOf(
            File(configDirPath),
            File("common-resources/default"),
            File("../common-resources/default")
        ).firstOrNull { it.exists() && it.isDirectory } ?: File(".")

        println("================================================================================")
        println("                BETTERHUD CONFIGURATION PARITY ANALYZER")
        println("================================================================================")
        println("Target Directory: ${configDir.absolutePath}")

        val yaml = Yaml()
        val layoutsDir = File(configDir, "layouts")
        val popupsDir = File(configDir, "popups")

        val analyses = mutableListOf<LayoutAnalysis>()
        val mm = MiniMessage.miniMessage()

        // 0. Pre-scan popups to associate layout names with parent popup lifetime
        val layoutToPopup = mutableMapOf<String, Pair<String, Int>>()
        val popupFiles = if (popupsDir.exists()) {
            popupsDir.walkTopDown().filter { it.isFile && (it.name.endsWith(".yml") || it.name.endsWith(".yaml")) }.toList()
        } else emptyList()

        for (file in popupFiles) {
            val rawData = runCatching { yaml.load<Map<String, Any>>(file.readText()) }.getOrNull() ?: continue
            for ((popupKey, popupVal) in rawData) {
                if (popupVal !is Map<*, *>) continue
                val pDuration = (popupVal["duration"] as? Number)?.toInt() ?: 0
                val layoutsObj = popupVal["layouts"] as? Map<*, *> ?: continue
                for ((_, entry) in layoutsObj) {
                    val layoutName = when (entry) {
                        is String -> entry
                        is Map<*, *> -> (entry["name"] as? String) ?: (entry["layout"] as? String)
                        else -> null
                    }
                    if (layoutName != null) {
                        val existing = layoutToPopup[layoutName]
                        if (existing == null || pDuration > existing.second) {
                            layoutToPopup[layoutName] = Pair(popupKey, pDuration)
                        }
                    }
                }
            }
        }

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
                    val animDuration = (animMap["duration"] as? Number)?.toInt() ?: 0
                    val xRaw = animMap["x-equation"] as? String
                    val yRaw = animMap["y-equation"] as? String
                    val lookahead = 10

                    val popupRef = layoutToPopup[layoutKey]
                    val popupDuration = popupRef?.second ?: 0
                    val lifetime = maxOf(animDuration, popupDuration)
                    val lifetimeSource = if (popupRef != null && popupRef.second > 0) "popup '${popupRef.first}' (${popupRef.second}t)" else "layout (${animDuration}t)"
                    val requiredTicks = lifetime + lookahead + 2
                    val chosenM = getDivisorM(requiredTicks) ?: 240
                    val margin = (chosenM - lookahead) - lifetime

                    if (hasHead) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Contains player/custom heads")
                    } else if (animDuration <= 0) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Unbounded lifetime (duration <= 0)")
                    } else if (xRaw == null || yRaw == null) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Missing x-equation or y-equation")
                    } else if (lifetime + lookahead > (240 - lookahead)) {
                        LayoutAnalysis("", layoutKey, relativePath, "SERVER", "Lifetime ($lifetime) exceeds safe window (220 ticks)")
                    } else {
                        val transpileResult = runCatching {
                            GlslTranspiler.transpile(xRaw)
                            GlslTranspiler.transpile(yRaw)
                        }
                        if (transpileResult.isFailure) {
                            LayoutAnalysis("", layoutKey, relativePath, "SERVER", "GLSL transpilation error: ${transpileResult.exceptionOrNull()?.message}")
                        } else {
                            val a = LayoutAnalysis(
                                popupName = popupRef?.first ?: "",
                                layoutName = layoutKey,
                                filePath = relativePath,
                                mode = "CLIENT",
                                reason = "Transpiled to GLSL shader",
                                lifetime = lifetime,
                                lifetimeSource = lifetimeSource,
                                animDuration = animDuration,
                                lookaheadL = lookahead,
                                moduloM = chosenM,
                                margin = margin
                            )
                            if (margin < 0) {
                                a.parityIssues.add("Safe window exceeded: margin = $margin ticks < 0 (lifetime=$lifetime, L=$lookahead, M=$chosenM)")
                            } else if (margin < 2) {
                                a.warnings.add("Low safe window margin: margin = $margin ticks < 2 (lifetime=$lifetime, L=$lookahead, M=$chosenM)")
                            }
                            a
                        }
                    }
                }

                // Check text decorations & component parity across all 3 test placeholder sets
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

        println("\n--- CLIENT-ANIMATED LAYOUTS (with Modulo M, Lifetime & Margin) ---")
        analyses.filter { it.mode == "CLIENT" }.forEach {
            val marginStr = if (it.margin != null) "margin=${it.margin}t" else "margin=N/A"
            val srcStr = if (it.lifetimeSource != null) "from ${it.lifetimeSource}" else ""
            println("• [${it.filePath}] -> ${it.layoutName} (M=${it.moduloM}, lifetime=${it.lifetime}t $srcStr, anim=${it.animDuration}t, L=${it.lookaheadL}, $marginStr)")
            it.warnings.forEach { w -> println("    [WARN] $w") }
            it.parityIssues.forEach { err -> println("    [ERROR] $err") }
        }

        println("\n================================================================================")
        println("Parity Status: ${if (totalIssues == 0) "PASS" else "FAIL"}")
        println("================================================================================")

        assertTrue(totalIssues == 0, "Configuration parity tool found $totalIssues discrepancies between server and client!")
    }

    private fun checkTextParity(pattern: String, mm: MiniMessage, analysis: LayoutAnalysis) {
        val isStaticWithUnsupportedDecorations = pattern.contains("<u>", ignoreCase = true) ||
            pattern.contains("<st>", ignoreCase = true) ||
            pattern.contains("<obf>", ignoreCase = true)

        if (isStaticWithUnsupportedDecorations) {
            analysis.warnings.add("Static pattern '$pattern' contains <u>/<st>/<obf>: text core shader drops line decorations")
        }

        // Test with all 3 test placeholder sets
        for ((setName, placeholderMap) in testPlaceholderSets) {
            var resolvedPattern = pattern
            for ((key, value) in placeholderMap) {
                resolvedPattern = resolvedPattern.replace("[$key]", value)
            }
            // Strip any remaining unmapped placeholders to a default value
            resolvedPattern = resolvedPattern.replace(Regex("\\[[^\\]]+\\]"), "Sample")

            val parsed = runCatching { mm.deserialize(resolvedPattern) }.getOrNull()
            if (parsed == null) {
                analysis.parityIssues.add("Failed to parse MiniMessage pattern '$resolvedPattern' in $setName")
                continue
            }

            // Test dynamic decoration stripping
            val cleanComp = if (parsed.hasUnsupportedClientDecorations()) {
                parsed.stripUnsupportedClientDecorations()
            } else parsed

            if (cleanComp.hasUnsupportedClientDecorations()) {
                analysis.parityIssues.add("Component in $setName still contains unsupported decorations after stripping: $resolvedPattern")
            }

            // If client animated, verify exact dual rewrite parity
            if (analysis.mode == "CLIENT") {
                val m = analysis.moduloM ?: 30
                val widthComp = WidthComponent(Component.text().append(cleanComp), 100)
                val advance = calculateClientAdvance(widthComp)

                if (cleanComp.hasAnyShadow()) {
                    val copyShadow = cleanComp.applyClientDualRole(DualCopyRole.SHADOW, 0, m)
                    val copyText = cleanComp.applyClientDualRole(DualCopyRole.TEXT, 0, m)

                    val shadowColor = copyShadow.style().shadowColor()
                    if (shadowColor != null && shadowColor.value() != 0) {
                        val a = shadowColor.alpha()
                        if (a < m + 1 || a > 2 * m) {
                            analysis.parityIssues.add("Shadow alpha ($a) out of bounds for M=$m (expected ${m + 1}..${2 * m}) in $setName")
                        }
                    }

                    val textColor = copyText.style().shadowColor()
                    if (textColor != null && textColor.value() != 0) {
                        val a = textColor.alpha()
                        if (a < 1 || a > m) {
                            analysis.parityIssues.add("Text alpha ($a) out of bounds for M=$m (expected 1..$m) in $setName")
                        }
                    }
                }
            }
        }
    }
}
