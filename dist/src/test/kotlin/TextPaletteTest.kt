package kr.toxicity.hud.text

import kr.toxicity.hud.animation.ClientAnimation
import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.shader.RenderScale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TextPaletteTest {

    @BeforeEach
    fun setUp() {
        TextPalette.reset()
    }

    @Test
    fun `test initial palette contains white and named colors`() {
        assertEquals(16, TextPalette.size())
        assertEquals(NamedTextColor.WHITE, TextPalette.getColor(0))

        assertEquals(0, TextPalette.indexOf(NamedTextColor.WHITE))
        assertEquals(1, TextPalette.indexOf(NamedTextColor.BLACK))
        assertEquals(7, TextPalette.indexOf(NamedTextColor.GOLD))
        assertEquals(13, TextPalette.indexOf(NamedTextColor.RED))
    }

    @Test
    fun `test custom color registration`() {
        val yellowGold = TextColor.fromHexString("#FFD800")!!
        val idx = TextPalette.register(yellowGold)
        assertEquals(16, idx)
        assertEquals(17, TextPalette.size())
        assertEquals(idx, TextPalette.indexOf(yellowGold))
        assertEquals(yellowGold, TextPalette.getColor(idx))
    }

    @Test
    fun `test nearest color matching for unknown color`() {
        // Almost red #FF5556 vs NamedTextColor.RED #FF5555
        val slightlyOffRed = TextColor.color(255, 85, 86)
        val idx = TextPalette.indexOf(slightlyOffRed)
        assertEquals(13, idx) // matches NamedTextColor.RED
    }

    @Test
    fun `test applyClientPalette on single component`() {
        val tStartByte = 42
        val comp = Component.text("-14", NamedTextColor.RED)

        val paletted = comp.applyClientPalette(tStartByte, defaultOutline = 0xFF000000.toInt())

        assertNotNull(paletted.color())
        assertEquals(tStartByte, paletted.color()!!.red())
        assertEquals(13, paletted.color()!!.green()) // RED index
        assertEquals(0, paletted.color()!!.blue())

        // Shadow check: outline is black so shadowColor must be set with b=1 and same tStart
        val shadow = paletted.shadowColor()
        assertNotNull(shadow)
        assertEquals(tStartByte, shadow!!.red())
        assertEquals(13, shadow.green())
        assertEquals(1, shadow.blue())
        assertEquals(255, shadow.alpha())
    }

    @Test
    fun `test applyClientPalette without shadow`() {
        val tStartByte = 88
        val comp = Component.text("No Shadow", NamedTextColor.GREEN)

        val paletted = comp.applyClientPalette(tStartByte, defaultOutline = 0)

        assertEquals(tStartByte, paletted.color()!!.red())
        assertEquals(11, paletted.color()!!.green()) // GREEN index
        assertEquals(0, paletted.color()!!.blue())

        // Shadow should be none
        val shadow = paletted.shadowColor()
        assertTrue(shadow == null || shadow == ShadowColor.none() || shadow.alpha() == 0)
    }

    @Test
    fun `test applyClientPalette on nested children with different colors`() {
        val tStartByte = 15
        val yellowGold = TextColor.fromHexString("#FFD800")!!
        TextPalette.register(yellowGold)
        val goldIdx = TextPalette.indexOf(yellowGold)

        val parent = Component.text("")
            .append(Component.text("CRIT ", NamedTextColor.RED).decorate(TextDecoration.BOLD))
            .append(Component.text("42", yellowGold))

        val paletted = parent.applyClientPalette(tStartByte, defaultOutline = 0xFF000000.toInt())
        val children = paletted.children()
        assertEquals(2, children.size)

        val child1 = children[0]
        assertEquals(tStartByte, child1.color()!!.red())
        assertEquals(13, child1.color()!!.green()) // RED
        assertEquals(0, child1.color()!!.blue())
        assertTrue(child1.hasDecoration(TextDecoration.BOLD))
        assertEquals(tStartByte, child1.shadowColor()!!.red())
        assertEquals(13, child1.shadowColor()!!.green())
        assertEquals(1, child1.shadowColor()!!.blue())

        val child2 = children[1]
        assertEquals(tStartByte, child2.color()!!.red())
        assertEquals(goldIdx, child2.color()!!.green()) // #FFD800
        assertEquals(0, child2.color()!!.blue())
        assertEquals(tStartByte, child2.shadowColor()!!.red())
        assertEquals(goldIdx, child2.shadowColor()!!.green())
        assertEquals(1, child2.shadowColor()!!.blue())
    }

    @Test
    fun `test width and pixel preservation`() {
        val tStartByte = 77
        val rawComp = Component.text("Test", NamedTextColor.AQUA)
        val widthComp = WidthComponent(rawComp.toBuilder(), 38)
        val pixelComp = PixelComponent(widthComp, 15)

        val transformed = pixelComp.applyClientPalette(tStartByte, defaultOutline = 0)

        assertEquals(38, transformed.component.width)
        assertEquals(15, transformed.pixel)
        assertEquals(tStartByte, transformed.component.component.build().color()!!.red())
    }

    @Test
    fun `test generateGlsl contains valid palette mapping`() {
        val glslLines = TextPalette.generateGlsl()
        val glsl = glslLines.joinToString("\n")

        assertTrue(glsl.contains("vec3 getTextPaletteColor(int idx)"))
        assertTrue(glsl.contains("case 0: return vec3(1.000000, 1.000000, 1.000000);")) // white
        assertTrue(glsl.contains("case 1: return vec3(0.000000, 0.000000, 0.000000);")) // black
        assertTrue(glsl.contains("default: return vec3(1.0, 1.0, 1.0);"))
    }

    @Test
    fun `test shader layout GLSL generation for text vs image`() {
        val anim = ClientAnimation(20, "10.0", "0.0", "1.0", AnimationType.PLAY_ONCE)
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))

        val textShader = HudShader(gui, scale, 1, 0xFF000000.toInt(), 1.0, 0, anim, null, null, isText = true)
        val imgShader = HudShader(gui, scale, 1, 0, 1.0, 0, anim, null, null, isText = false)

        val layoutLines = ShaderManagerImpl.generateShaderLayout(
            mapOf(textShader to listOf(), imgShader to listOf())
        )
        val layoutGlsl = layoutLines.joinToString("\n")

        // Text shader case should have exact mode decoding, main quad collapse, and shadow shift
        assertTrue(layoutGlsl.contains("int actualA = int(round(Color.a * 255.0));"))
        assertTrue(layoutGlsl.contains("pos.xy = vec2(-10000.0);"))
        assertTrue(layoutGlsl.contains("pos.xy -= vec2(1.0, 1.0);"))
        assertTrue(layoutGlsl.contains("vertexColor = vec4(float(actualR) / 255.0, float(actualG) / 255.0, float(actualB) / 255.0, 1.0);"))

        // Image shader case should have vertexColor = vec4(1.0, 1.0, 1.0, 1.0)
        assertTrue(layoutGlsl.contains("vertexColor = vec4(1.0, 1.0, 1.0, 1.0);"))

        // Image should have the tStart < 240.0 check
        assertTrue(layoutGlsl.contains("if (tStart < 240.0)"))
    }
}
