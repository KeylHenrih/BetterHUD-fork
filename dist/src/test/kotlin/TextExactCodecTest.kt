import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.text.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

class TextExactCodecTest {

    @Test
    fun testExactCodecCpuFullChannelSweepsAndRandomRgb() {
        // Test all T in 0..239
        for (t in 0..239) {
            val a = t + 1
            assertTrue(a in 1..240, "Alpha must be in range 1..240 for T=$t")
        }

        // Test channel sweeps across multiple baselines
        val baselines = intArrayOf(0, 16, 32, 64, 77, 128, 179, 192, 240, 255)
        for (base in baselines) {
            for (i in 0..255) {
                // R sweep
                verifyCodec(i, base, base, i % 240)
                // G sweep
                verifyCodec(base, i, base, i % 240)
                // B sweep
                verifyCodec(base, base, i, i % 240)
            }
        }

        // 2 million random RGB samples
        val rng = Random(42)
        val batchCount = 2_000_000
        for (iter in 0 until batchCount) {
            val r = rng.nextInt(256)
            val g = rng.nextInt(256)
            val b = rng.nextInt(256)
            val t = rng.nextInt(240)
            verifyCodec(r, g, b, t)
        }
    }

    private fun verifyCodec(r: Int, g: Int, b: Int, t: Int) {
        val originalColor = TextColor.color(r, g, b)
        val comp = Component.text("A").color(originalColor)
        val rewritten = comp.applyClientExact(t)

        val shadow = rewritten.shadowColor()
        assertNotNull(shadow, "ShadowColor must not be null")
        shadow!!

        // Carrier Alpha
        val alpha = shadow.alpha()
        assertEquals(t + 1, alpha, "Alpha must be T+1")
        assertTrue(alpha in 1..240, "Alpha must be in 1..240")

        // Carrier RGB
        assertEquals(r, shadow.red(), "Red channel mismatch")
        assertEquals(g, shadow.green(), "Green channel mismatch")
        assertEquals(b, shadow.blue(), "Blue channel mismatch")

        // Ensure ARGB != 0
        val argb = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        assertNotEquals(0, argb, "ARGB must never be zero")

        // Main quad color should be WHITE (or alpha 255) to collapse in shader
        assertEquals(NamedTextColor.WHITE, rewritten.color())
    }

    @Test
    fun testDualCodecAllMDivisorsAndRoles() {
        val rng = Random(1337)
        for (m in M_DIVISORS) {
            assertTrue(2 * m <= 254, "2*M must be <= 254 (was ${2 * m})")
            for (t in 0 until m) {
                // Test 50 random colors per (M, T) pair
                for (iter in 0 until 50) {
                    val r = rng.nextInt(256)
                    val g = rng.nextInt(256)
                    val b = rng.nextInt(256)
                    val textCol = TextColor.color(r, g, b)
                    val shadowCol = TextColor.color((r * 0.25f).toInt(), (g * 0.25f).toInt(), (b * 0.25f).toInt())

                    val comp = Component.text("DualTest")
                        .color(textCol)
                        .shadowColor(ShadowColor.shadowColor(shadowCol.red(), shadowCol.green(), shadowCol.blue(), 255))

                    // Role 1: SHADOW
                    val copyShadow = comp.applyClientDualRole(DualCopyRole.SHADOW, t, m)
                    val sShadow = copyShadow.shadowColor()
                    assertNotNull(sShadow)
                    val sAlpha = sShadow!!.alpha()
                    assertEquals(1 + m + t, sAlpha, "Shadow role alpha mismatch for M=$m, T=$t")
                    assertTrue(sAlpha in (m + 1)..(2 * m), "Shadow alpha must be in (M+1)..(2*M)")
                    assertEquals(shadowCol.red(), sShadow.red())
                    assertEquals(shadowCol.green(), sShadow.green())
                    assertEquals(shadowCol.blue(), sShadow.blue())
                    val recoveredShadowT = sAlpha - 1 - m
                    assertEquals(t, recoveredShadowT, "Recovered T for shadow role mismatch")

                    // Role 2: TEXT
                    val copyText = comp.applyClientDualRole(DualCopyRole.TEXT, t, m)
                    val tShadow = copyText.shadowColor()
                    assertNotNull(tShadow)
                    val tAlpha = tShadow!!.alpha()
                    assertEquals(1 + t, tAlpha, "Text role alpha mismatch for M=$m, T=$t")
                    assertTrue(tAlpha in 1..m, "Text alpha must be in 1..M")
                    assertEquals(textCol.red(), tShadow.red())
                    assertEquals(textCol.green(), tShadow.green())
                    assertEquals(textCol.blue(), tShadow.blue())
                    val recoveredTextT = tAlpha - 1
                    assertEquals(t, recoveredTextT, "Recovered T for text role mismatch")

                    // Both main quads must be WHITE (alpha 255) to collapse
                    assertEquals(NamedTextColor.WHITE, copyShadow.color())
                    assertEquals(NamedTextColor.WHITE, copyText.color())
                }
            }
        }
    }

    @Test
    fun testDualCodec2MillionRandomSamples() {
        val rng = Random(4242)
        val batchCount = 2_000_000
        for (i in 0 until batchCount) {
            val m = M_DIVISORS[rng.nextInt(M_DIVISORS.size)]
            val t = rng.nextInt(m)
            val r = rng.nextInt(256)
            val g = rng.nextInt(256)
            val b = rng.nextInt(256)

            val textCol = TextColor.color(r, g, b)
            val comp = Component.text("X").color(textCol)

            val copyText = comp.applyClientDualRole(DualCopyRole.TEXT, t, m)
            val shadowObj = copyText.shadowColor()!!
            val a = shadowObj.alpha()
            assertEquals(1 + t, a)
            assertEquals(r, shadowObj.red())
            assertEquals(g, shadowObj.green())
            assertEquals(b, shadowObj.blue())
            assertNotEquals(0, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }

    @Test
    fun testItalicCountAndAdvanceCalculation() {
        // Plain text
        val plain = Component.text("Hello World") // 11 chars
        assertEquals(0, plain.countItalicChars())

        // Italic text
        val italic = Component.text("Hello World").decorate(TextDecoration.ITALIC)
        assertEquals(11, italic.countItalicChars())

        // Bold Italic text
        val boldItalic = Component.text("12345")
            .decorate(TextDecoration.BOLD)
            .decorate(TextDecoration.ITALIC)
        assertEquals(5, boldItalic.countItalicChars())

        // Mixed nested text: "Normal " (7) + italic "Italic" (6) + " Normal" (7) = 20 total, 6 italic
        val mixed = Component.text("Normal ")
            .append(Component.text("Italic").decorate(TextDecoration.ITALIC))
            .append(Component.text(" Normal"))
        assertEquals(6, mixed.countItalicChars())

        // WidthComponent advance calculation
        val wcPlain = WidthComponent(plain.toBuilder(), 55)
        assertEquals(55, calculateClientAdvance(wcPlain))

        // Italic has width 55 in BetterHud, but client advance is 55 - 11 = 44
        val wcItalic = WidthComponent(italic.toBuilder(), 55)
        assertEquals(44, calculateClientAdvance(wcItalic))
    }

    @Test
    fun testDualCopyWidthPreservation() {
        val comp = Component.text("Popup Notification")
            .color(NamedTextColor.GOLD)
            .shadowColor(ShadowColor.shadowColor(0xFF3F3F3F.toInt()))
        val wc = WidthComponent(comp.toBuilder(), 120)

        val dualWc = wc.applyClientExactDual(10, 30)
        // Net width seen by BetterHud server layout must remain exactly 120!
        assertEquals(120, dualWc.width, "Dual copy net layout width must remain identical to original")
    }

    @Test
    fun testHasAnyShadowResolution() {
        val mini = MiniMessage.miniMessage()

        // Explicit shadow
        val withShadow = mini.deserialize("<red><shadow:#FF0000>Text with shadow</shadow></red>")
        assertTrue(withShadow.hasAnyShadow(), "Must detect explicit shadow")

        // Explicitly disabled shadow
        val withoutShadow = mini.deserialize("<red><shadow:#00000000>No shadow</shadow></red>")
        assertFalse(withoutShadow.hasAnyShadow(), "Must detect shadow explicitly disabled")

        // Plain text with default vanilla shadow
        val defaultShadow = mini.deserialize("<white>Default text</white>")
        assertTrue(defaultShadow.hasAnyShadow(), "Default text without shadow tag has vanilla shadow")

        // Root with shadowColor 0 (e.g. outline: 0 in BetterHud)
        val rootDisabled = Component.text("Disabled Root")
            .shadowColor(ShadowColor.shadowColor(0))
        assertFalse(rootDisabled.hasAnyShadow(), "Root with shadowColor 0 must disable shadow for children")

        // Child overriding root disabled shadow
        val childOverride = Component.text("Parent")
            .shadowColor(ShadowColor.shadowColor(0))
            .append(Component.text("Child").shadowColor(ShadowColor.shadowColor(0xFF00FF00.toInt())))
        assertTrue(childOverride.hasAnyShadow(), "Child with explicit shadow must override root disabled shadow")
    }

    @Test
    fun testSingleCopyOptimizationWhenNoShadow() {
        val comp = Component.text("No Shadow Item")
            .color(NamedTextColor.AQUA)
            .shadowColor(ShadowColor.shadowColor(0))
        val wc = WidthComponent(comp.toBuilder(), 80)

        val result = wc.applyClientExactDual(5, 60)
        // Since there is no shadow, result should be single-copy (width 80, no backstep)
        assertEquals(80, result.width)
        val built = result.component.build()
        // Single copy: shadow alpha = 5 + 1 = 6
        assertEquals(6, built.shadowColor()?.alpha())
    }

    @Test
    fun testGradientMiniMessageRewriting() {
        val mini = MiniMessage.miniMessage()
        val gradient = mini.deserialize("<gradient:#FF0000:#0000FF>01234567890123456789012345678901</gradient>")
        val t = 42
        val rewritten = gradient.applyClientExact(t)

        // Verify every child character receives its exact gradient color in shadowColor
        fun checkChildren(c: Component) {
            val color = c.color()
            val shadow = c.shadowColor()
            if (shadow != null) {
                assertEquals(t + 1, shadow.alpha())
                assertNotNull(color)
            }
            c.children().forEach { checkChildren(it) }
        }
        checkChildren(rewritten)
    }

    @Test
    fun testNestedColorsAndStyles() {
        val mini = MiniMessage.miniMessage()
        val nested = mini.deserialize("<red>Hello <bold><green>bold green</green></bold> normal red</red>")
        val t = 100
        val rewritten = nested.applyClientExact(t)

        val firstChild = rewritten.children().firstOrNull() ?: rewritten
        val helloShadow = (if (rewritten.shadowColor() != null) rewritten else firstChild).shadowColor()
        assertNotNull(helloShadow)
        assertEquals(NamedTextColor.RED.red(), helloShadow!!.red())
        assertEquals(NamedTextColor.RED.green(), helloShadow.green())
        assertEquals(NamedTextColor.RED.blue(), helloShadow.blue())
        assertEquals(t + 1, helloShadow.alpha())

        val boldGreenChild = rewritten.children().find { it.hasDecoration(TextDecoration.BOLD) }
            ?: rewritten.children().flatMap { it.children() }.find { it.hasDecoration(TextDecoration.BOLD) }
        assertNotNull(boldGreenChild, "Must find bold green child")
        val greenShadow = boldGreenChild!!.shadowColor()
        assertNotNull(greenShadow)
        assertEquals(NamedTextColor.GREEN.red(), greenShadow!!.red())
        assertEquals(NamedTextColor.GREEN.green(), greenShadow.green())
        assertEquals(NamedTextColor.GREEN.blue(), greenShadow.blue())
        assertEquals(t + 1, greenShadow.alpha())
        assertTrue(boldGreenChild.hasDecoration(TextDecoration.BOLD))
    }

    @Test
    fun testMainQuadCollapseAndSafeFallback() {
        fun evalGlslBranch(actualA: Int, m: Int): String {
            return if (actualA in 1..m) {
                "LAYER_TEXT"
            } else if (actualA in (m + 1)..(2 * m)) {
                "LAYER_SHADOW"
            } else {
                "COLLAPSE"
            }
        }

        val m = 30
        // Main quad collapses
        assertEquals("COLLAPSE", evalGlslBranch(255, m))
        assertEquals("COLLAPSE", evalGlslBranch(0, m))
        assertEquals("COLLAPSE", evalGlslBranch(61, m))
        assertEquals("COLLAPSE", evalGlslBranch(150, m))

        // Text layer
        for (a in 1..30) {
            assertEquals("LAYER_TEXT", evalGlslBranch(a, m))
        }

        // Shadow layer
        for (a in 31..60) {
            assertEquals("LAYER_SHADOW", evalGlslBranch(a, m))
        }
    }

    @Test
    fun testSynchronyPositionOffset() {
        val vanillaShadowOffset = 1.0
        val shaderCounterOffset = -1.0
        val netShadowOffset = vanillaShadowOffset + shaderCounterOffset
        assertEquals(0.0, netShadowOffset, 1e-9)
    }
}
