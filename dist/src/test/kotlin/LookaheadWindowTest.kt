package kr.toxicity.hud.test

import kr.toxicity.hud.api.manager.ShaderManager.ShaderType
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.manager.ShaderManagerImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class LookaheadWindowTest {

    private fun jmod(x: Double, y: Double): Double {
        val m = x - y * Math.floor(x / y)
        return if (m >= y) m - y else if (m < 0.0) m + y else m
    }

    private fun extractGlslLookaheadL(): Double? {
        val constants = ShaderManagerImpl.tagBuilders["CreateConstant"]!!.invoke(ShaderType.TEXT_VERTEX)
        val lDefine = constants.firstOrNull { it.trim().startsWith("#define L ") } ?: return null
        return lDefine.trim().removePrefix("#define L ").trim().toDoubleOrNull()
    }

    @Test
    fun testGlslLookaheadWindowSingleSourceOfTruth() {
        // 1. Verify Kotlin single source of truth
        val kotlinL = ConfigManagerImpl.animationLookaheadWindow.toDouble()
        assertEquals(maxOf(10.0, ConfigManagerImpl.animationLookaheadMax.toDouble()), kotlinL)

        // 2. Verify GLSL shader exports `#define L` matching Kotlin L
        val glslL = extractGlslLookaheadL()
        assertEquals(kotlinL, glslL, "GLSL shader must define macro L matching Kotlin ConfigManagerImpl.animationLookaheadWindow")

        // 3. Verify getElapsedTicks GLSL source uses L rather than hardcoded 10.0
        val definedMethods = ShaderManagerImpl.tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(ShaderType.TEXT_VERTEX).joinToString("\n")
        assertTrue(definedMethods.contains("jmod(gameTimeTicks - tStart + L, 240.0) - L"),
            "getElapsedTicks(tStart, gameTimeTicks) must use macro L instead of hardcoded 10.0")
        assertTrue(definedMethods.contains("jmod(gameTimeTicks - tStart + L, M) - L"),
            "getElapsedTicks(tStart, gameTimeTicks, M) must use macro L instead of hardcoded 10.0")
    }

    @Test
    fun testDivergenceBetweenGlslAndKotlinCausesFailure() {
        // If GLSL was stuck at hardcoded L_glsl = 10.0, but Kotlin configured animationLookaheadMax = 15 (L_kotlin = 15):
        val lGlslOld = 10.0
        val lookaheadMax = 15
        val ping = 1500 // lookahead = 15
        val lookahead = 15.0
        val tickOffset = -lookahead // -15.0
        val M = 240.0

        // Old buggy behavior: wraps into positive 225.0 elapsed, rendering late animation frame instead of collapsing!
        val oldGlslElapsed = jmod(tickOffset + lGlslOld, M) - lGlslOld // jmod(-15 + 10, 240) - 10 = jmod(-5, 240) - 10 = 235 - 10 = 225.0
        assertEquals(225.0, oldGlslElapsed, "Hardcoded 10.0 in GLSL causes 225.0 elapsed glitch when lookahead=15")

        // Unified behavior: L = max(10, 15) = 15:
        val unifiedL = maxOf(10.0, lookaheadMax.toDouble())
        val unifiedGlslElapsed = jmod(tickOffset + unifiedL, M) - unifiedL // jmod(-15 + 15, 240) - 15 = 0 - 15 = -15.0
        assertEquals(-15.0, unifiedGlslElapsed, "Unified L preserves negative elapsed (-15.0) which safely hides the quad")
    }

    @Test
    fun testLookaheadWindowMathAcrossPingAndLookaheadRange() {
        // Test lookahead settings from 0 to 20
        for (lookaheadMax in 0..20) {
            val L = maxOf(10, lookaheadMax)
            val M = 240.0

            // Test ping range 0..2000 ms (step 25 ms)
            for (pingMs in 0..2000 step 25) {
                val lookahead = (pingMs / 100.0).roundToInt().coerceIn(0, lookaheadMax)
                assertTrue(lookahead <= L, "Lookahead $lookahead must not exceed window L $L")

                // At popup spawn (gameTime == spawnTime):
                // Server sends tStart = spawnTime + lookahead
                // At client, gameTime - tStart = -lookahead
                val tickOffset = -lookahead.toDouble()
                assertTrue(tickOffset >= -L && tickOffset <= 0.0, "tickOffset $tickOffset must be in [-L, 0]")

                // Shader computation:
                val glslElapsed = jmod(tickOffset + L, M) - L

                // GLSL elapsed must equal tickOffset exactly (no modulo wrap-around into [0, M))
                assertEquals(tickOffset, glslElapsed, 1e-6,
                    "At spawn time with ping=$pingMs ms and lookaheadMax=$lookaheadMax, elapsed must be $tickOffset but got $glslElapsed")

                // When elapsed < 0, shader safely collapses quad
                if (lookahead > 0) {
                    assertTrue(glslElapsed < 0.0, "Elapsed must be negative before animation begins")
                } else {
                    assertEquals(0.0, glslElapsed, 1e-6)
                }

                // Verify safe window before modulo wraparound:
                // Safe play window is [0, M - L)
                val endOfSafeWindow = M - L
                val safeElapsed = jmod(endOfSafeWindow - 1.0 + L, M) - L
                assertEquals(endOfSafeWindow - 1.0, safeElapsed, 1e-6, "safe window before M-L must not wrap")

                // At M - L, wraparound starts (safety latch in Kotlin catches this)
                val wrapElapsed = jmod(endOfSafeWindow + L, M) - L
                assertEquals(-L.toDouble(), wrapElapsed, 1e-6, "wraparound must cleanly jump to -L (collapsing quad)")
            }
        }
    }
}
