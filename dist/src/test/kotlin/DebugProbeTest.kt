package kr.toxicity.hud.test

import kr.toxicity.hud.debug.DebugProbeHelper
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.PlaceholderManagerImpl
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.util.split
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.shader.RenderScale
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DebugProbeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            DebugProbeHelper.init()
        }
    }

    @Test
    fun testMiniMessageColorAndShadow() {
        val input = "<color:#010203><shadow:#11223344>|</shadow></color>"
        val comp = MiniMessage.miniMessage().deserialize(input)
        assertEquals(TextColor.color(1, 2, 3), comp.color())
        assertEquals(ShadowColor.shadowColor(0x11, 0x22, 0x33, 0x44), comp.shadowColor())
    }

    @Test
    fun testShadowAlphaStripStructure() {
        val strip = DebugProbeHelper.shadowAlphaStrip
        val comp = MiniMessage.miniMessage().deserialize(strip)
        assertEquals(257, comp.children().size)
        // Check pos 0: ARGB = 0
        val c0 = comp.children()[0]
        assertEquals(ShadowColor.shadowColor(0, 0, 0, 0), c0.shadowColor())
        // Check pos 1: A = 0, R = 0, G = 137, B = 73
        val c1 = comp.children()[1]
        assertEquals(ShadowColor.shadowColor(0, 137, 73, 0), c1.shadowColor())
        // Check pos 256: A = 255, R = 255, G = 137, B = 73
        val c256 = comp.children()[256]
        assertEquals(ShadowColor.shadowColor(255, 137, 73, 255), c256.shadowColor())
    }

    @Test
    fun testOffsetItalicStructure() {
        val italicComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.offsetItalic)
        println("italicComp: $italicComp")
        val boldComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.offsetBold)
        println("boldComp: $boldComp")
        val normalComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.offsetNormal)
        println("normalComp: $normalComp")

        val splitOption = kr.toxicity.hud.util.SplitOption(1000, 0, false)
        val splitItalic = italicComp.split(splitOption) { 8 }
        println("splitItalic size: ${splitItalic.size}")
        splitItalic.forEach {
            println("splitItalic item: width=${it.width()}, comp=${it.component().build()}")
        }
    }

    @Test
    fun testShadowRgbStrips() {
        val redComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.shadowRedStrip)
        assertEquals(256, redComp.children().size)
        val greenComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.shadowGreenStrip)
        assertEquals(256, greenComp.children().size)
        val blueComp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.shadowBlueStrip)
        assertEquals(256, blueComp.children().size)
    }

    @Test
    fun testMainAlphaStrip() {
        val comp = MiniMessage.miniMessage().deserialize(DebugProbeHelper.mainAlphaStrip)
        assertEquals(256, comp.children().size)
        // Verify no shadow is attached and colors match
        val first = comp.children()[0]
        assertNull(first.shadowColor())
        assertEquals(TextColor.color(0, 255, 222), first.color())
    }

    @Test
    fun testProbeShaderCaseGeneration() {
        val lines = DebugProbeHelper.generateProbeShaderCase()
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("actualG == 137 && actualB == 73") })
        assertTrue(lines.any { it.contains("actualA == 241 && actualB == 10") })
        assertTrue(lines.any { it.contains("actualA == 242 && actualB == 10") })
        assertTrue(lines.any { it.contains("actualA == 243 && actualG == 10") })
        assertTrue(lines.any { it.contains("actualB == 222 && (actualR + actualG == 255)") })
        assertTrue(lines.any { it.contains("gl_VertexID") })
    }

    @Test
    fun testHudShaderProbeDistinction() {
        val dummyRender = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val normalShader = HudShader(GuiLocation(0.0, 0.0), dummyRender, 0, 0, 1.0, 0, isText = true, isProbe = false)
        val probeShader = HudShader(GuiLocation(0.0, 0.0), dummyRender, 0, 0, 1.0, 0, isText = true, isProbe = true)

        assertNotEquals(normalShader, probeShader)
        assertTrue(normalShader.compareTo(probeShader) != 0)
    }

    @Test
    fun testShaderManagerProbeLayoutEmission() {
        val dummyRender = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val probeShader = HudShader(GuiLocation(0.0, 0.0), dummyRender, 0, 0, 1.0, 0, isText = true, isProbe = true)
        val map = mapOf(probeShader to listOf<(Int) -> Unit>())

        val layoutLines = ShaderManagerImpl.generateShaderLayout(map)
        assertTrue(layoutLines.any { it.contains("actualG == 137 && actualB == 73") })
        assertTrue(layoutLines.any { it.contains("gl_VertexID") })
    }
}
