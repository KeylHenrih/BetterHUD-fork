package kr.toxicity.hud.test

import kr.toxicity.hud.util.HUD_ADD_HEIGHT
import kr.toxicity.hud.util.HUD_DEFAULT_BIT
import kr.toxicity.hud.util.HUD_MAX_BIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ItalicSlopeEmulationTest {

    @Test
    fun testItalicSlopeFloat32Emulation() {
        val uiX = 1920.0f
        val xLayout = 100.0f
        val heightBit = HUD_DEFAULT_BIT
        val maxBit = HUD_MAX_BIT
        val addOffset = HUD_ADD_HEIGHT.toFloat()

        val lines = listOf(1, 2, 3)
        val scales = listOf(0.5f, 1.0f, 2.0f)
        val glyphHeights = listOf(8.0f, 12.0f, 16.0f)
        val testY = listOf(-100, 0, 50, 200)
        val testIds = listOf(1, 42, 512, 1023)

        var maxSlopeDiscrepancy = 0.0f
        var maxSlantDiscrepancyPx = 0.0f

        for (line in lines) {
            // Vanilla BossHealthOverlay text position for given line:
            val textY = 3.0f + 19.0f * (line - 1)
            // DEFAULT_OFFSET defined in shader:
            val defaultOffset = 10.0f + 17.0f * (line - 1)

            for (scale in scales) {
                for (glyphHeight in glyphHeights) {
                    for (id in testIds) {
                        for (y in testY) {
                            val ascent = -(((id + (1 shl maxBit)) shl heightBit) + HUD_ADD_HEIGHT + y)
                            val up = 7.0f - ascent.toFloat()
                            val down = up + glyphHeight
                            val shearTop = 1.0f - 0.25f * up
                            val shearBottom = 1.0f - 0.25f * down

                            // Vertices entering vertex shader:
                            val posX0 = xLayout - 10000.0f + 0.5f * uiX + shearTop
                            val posY0 = textY + up

                            val posX1 = xLayout - 10000.0f + 0.5f * uiX + shearBottom
                            val posY1 = textY + down

                            fun runVertexShader(posX: Float, posY: Float, vid: Int): Pair<Float, Float> {
                                var x = posX
                                var yCoord = posY
                                if (x < -10000.0f) {
                                    val isBottom = if (vid == 1 || vid == 2) 1.0f else 0.0f
                                    x += 0.25f * (yCoord - defaultOffset - isBottom * glyphHeight) + 1.75f
                                }
                                x -= 0.5f * uiX
                                yCoord -= ((id + (1 shl maxBit)) shl heightBit) + addOffset + defaultOffset

                                // apply scale
                                x *= scale
                                yCoord *= scale
                                return x to yCoord
                            }

                            val (outX0, outY0) = runVertexShader(posX0, posY0, 0)
                            val (outX1, outY1) = runVertexShader(posX1, posY1, 1)

                            val actualSlantX = outX0 - outX1
                            val actualHeightY = outY1 - outY0
                            val actualSlope = actualSlantX / actualHeightY

                            val expectedSlantX = 0.25f * glyphHeight * scale
                            val expectedHeightY = glyphHeight * scale
                            val expectedSlope = 0.25f

                            val slantDiff = abs(actualSlantX - expectedSlantX)
                            val slopeDiff = abs(actualSlope - expectedSlope)

                            if (slantDiff > maxSlantDiscrepancyPx) maxSlantDiscrepancyPx = slantDiff
                            if (slopeDiff > maxSlopeDiscrepancy) maxSlopeDiscrepancy = slopeDiff

                            // Assertions:
                            // The slope between top and bottom vertices must not diverge (> 0.05 px):
                            assertTrue(slantDiff <= 0.05f,
                                "Slant difference $slantDiff must be <= 0.05 px (line=$line, scale=$scale, height=$glyphHeight)")
                            assertEquals(expectedSlope, actualSlope, 1e-4f,
                                "Slope must equal exactly 0.25 (line=$line, scale=$scale, height=$glyphHeight)")
                        }
                    }
                }
            }
        }

        println("=== Italic Slope Float32 Emulation Results ===")
        println("Max slope discrepancy: $maxSlopeDiscrepancy")
        println("Max slant discrepancy: $maxSlantDiscrepancyPx px")
        assertTrue(maxSlantDiscrepancyPx < 1e-5f, "Slope discrepancy is essentially 0.000000 px invariant!")
    }
}
