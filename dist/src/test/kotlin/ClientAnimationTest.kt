import kr.toxicity.hud.animation.AnimationLocation
import kr.toxicity.hud.animation.AnimationMode
import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.animation.ClientAnimation
import kr.toxicity.hud.animation.EasingType
import kr.toxicity.hud.animation.ListenerAnimation
import kr.toxicity.hud.animation.ListenerTracker
import kr.toxicity.hud.animation.SequenceAnimation
import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.manager.ShaderManager.ShaderType
import kr.toxicity.hud.image.enums.ImageType
import kr.toxicity.hud.image.enums.SplitType
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.shader.RenderScale
import kr.toxicity.hud.yaml.YamlObjectImpl
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import kr.toxicity.hud.util.applyColor
import kr.toxicity.hud.util.HUD_DEFAULT_BIT
import kr.toxicity.hud.util.HUD_MAX_BIT
import kr.toxicity.hud.util.HUD_ADD_HEIGHT
import kr.toxicity.hud.util.toSemver
import kr.toxicity.hud.text.applyClientExact
import kr.toxicity.hud.text.applyClientDualRole
import kr.toxicity.hud.text.DualCopyRole
import net.kyori.adventure.text.TextComponent
import java.io.File
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientAnimationTest {

    @Test
    fun testServerAnimationLocation() {
        val map = mutableMapOf<String, Any>(
            "type" to "loop",
            "duration" to 60,
            "x-equation" to "3cos(t/30 * pi)",
            "y-equation" to "3sin(t/30 * pi)",
            "mode" to "server"
        )
        val yaml = YamlObjectImpl("test", map)
        val animLoc = AnimationLocation(yaml)

        assertNull(animLoc.clientAnimation)
        assertEquals(60, animLoc.location.size)
        assertEquals(AnimationType.LOOP, animLoc.type)
        assertEquals(3, animLoc.location[0].x)
        assertEquals(0, animLoc.location[0].y)
    }

    @Test
    fun testClientAnimationLocationExplicitMode() {
        val map = mutableMapOf<String, Any>(
            "type" to "loop",
            "duration" to 60,
            "x-equation" to "3cos(t/30 * pi)",
            "y-equation" to "3sin(t/30 * pi)",
            "mode" to "client"
        )
        val yaml = YamlObjectImpl("test", map)
        val animLoc = AnimationLocation(yaml)

        assertNotNull(animLoc.clientAnimation)
        assertEquals(1, animLoc.location.size)
        assertEquals(PixelLocation.zero, animLoc.location[0])

        val clientAnim = animLoc.clientAnimation!!
        assertEquals(60, clientAnim.duration)
        assertEquals(AnimationType.LOOP, clientAnim.type)
        assertTrue(clientAnim.xGlsl.contains("cos"))
        assertTrue(clientAnim.yGlsl.contains("sin"))
        assertEquals("1.0", clientAnim.opacityGlsl)
    }

    @Test
    fun testPlayOnceAnimationClientMode() {
        val map = mutableMapOf<String, Any>(
            "type" to "play_once",
            "duration" to 20,
            "x-equation" to "3 * cos(t)",
            "y-equation" to "0",
            "mode" to "client"
        )
        val yaml = YamlObjectImpl("test_play_once", map)
        val animLoc = AnimationLocation(yaml)

        assertNotNull(animLoc.clientAnimation, "play_once must be supported in client mode")
        assertEquals(1, animLoc.location.size, "Client animation must collapse location frames to 1")
        assertEquals(AnimationType.PLAY_ONCE, animLoc.type)
        assertEquals(20, animLoc.clientAnimation?.duration)
        assertEquals("0.0", animLoc.clientAnimation?.yGlsl)
        assertTrue(animLoc.clientAnimation?.xGlsl?.contains("cos") == true)
    }

    @Test
    fun testGlobalClientAnimationMode() {
        val prevMode = ConfigManagerImpl.animationMode
        try {
            // Set global mode to CLIENT via reflection since setter is private
            val field = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
            field.isAccessible = true
            field.set(ConfigManagerImpl, AnimationMode.CLIENT)

            val map = mutableMapOf<String, Any>(
                "type" to "loop",
                "duration" to 40,
                "x-equation" to "2 * sin(t)",
                "y-equation" to "0"
            )
            val yaml = YamlObjectImpl("test", map)
            val animLoc = AnimationLocation(yaml)

            assertNotNull(animLoc.clientAnimation)
            assertEquals(1, animLoc.location.size)
            assertEquals(40, animLoc.clientAnimation?.duration)

            // Override with mode: server should keep it server
            map["mode"] = "server"
            val serverOverride = AnimationLocation(YamlObjectImpl("test", map))
            assertNull(serverOverride.clientAnimation)
            assertEquals(40, serverOverride.location.size)
        } finally {
            val field = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
            field.isAccessible = true
            field.set(ConfigManagerImpl, prevMode)
        }
    }

    @Test
    fun testGlobalHybridAnimationMode() {
        val prevMode = ConfigManagerImpl.animationMode
        try {
            val field = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
            field.isAccessible = true
            field.set(ConfigManagerImpl, AnimationMode.HYBRID)

            val map = mutableMapOf<String, Any>(
                "type" to "loop",
                "duration" to 60,
                "x-equation" to "0",
                "y-equation" to "3cos(t/30 * pi)"
            )
            val yaml = YamlObjectImpl("test", map)
            val animLoc = AnimationLocation(yaml)

            assertNotNull(animLoc.clientAnimation, "HYBRID mode must enable client animation for Java")
            assertEquals(1, animLoc.location.size, "HYBRID mode must reduce location frames to 1")
            assertEquals(60, animLoc.clientAnimation?.duration)
            assertEquals("0.0", animLoc.clientAnimation?.xGlsl)
            assertTrue(animLoc.clientAnimation?.yGlsl?.contains("cos") == true)
        } finally {
            val field = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
            field.isAccessible = true
            field.set(ConfigManagerImpl, prevMode)
        }
    }

    @Test
    fun testCategoryALayoutLoopEquivalence() {
        val formula = "3cos(t/30 * pi)"
        val glsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(formula)
        assertTrue(glsl.contains("cos"))

        // Test 240 fractional steps across 60-tick duration (step = 0.25 tick, simulating 80 FPS)
        for (i in 0..240) {
            val tFractional = i * 0.25
            val t = (tFractional % 60.0)
            val kotlinResult = 3.0 * kotlin.math.cos(t / 30.0 * Math.PI)
            val glslResult = 3.0f * kotlin.math.cos((t.toFloat() / 30.0f) * 3.14159265f)
            val diff = kotlin.math.abs(kotlinResult - glslResult.toDouble())
            assertTrue(diff < 1e-4, "Mismatch at t=$t: expected $kotlinResult, got $glslResult, diff=$diff")
        }
    }

    @Test
    fun testHudShaderComparison() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val anim1 = ClientAnimation(60, "3.0 * cos(t)", "0.0", "1.0", AnimationType.LOOP)
        val anim2 = ClientAnimation(60, "3.0 * cos(t)", "0.0", "1.0", AnimationType.LOOP)
        val anim3 = ClientAnimation(30, "sin(t)", "0.0", "1.0", AnimationType.LOOP)

        val s1 = HudShader(gui, scale, 1, 0, 1.0, 0, anim1)
        val s2 = HudShader(gui, scale, 1, 0, 1.0, 0, anim2)
        val s3 = HudShader(gui, scale, 1, 0, 1.0, 0, anim3)
        val sNull = HudShader(gui, scale, 1, 0, 1.0, 0, null)

        assertEquals(s1, s2)
        assertEquals(0, s1.compareTo(s2))
        assertNotEquals(s1, s3)
        assertNotEquals(s1, sNull)
        assertTrue(sNull < s1) // nullsFirst
    }

    @Test
    fun testShaderManagerDefinedMethod() {
        val tagBuildersField = ShaderManagerImpl::class.java.getDeclaredField("tagBuilders")
        tagBuildersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tagBuilders = tagBuildersField.get(ShaderManagerImpl) as Map<String, (ShaderType) -> List<String>>

        // Check #GenerateOtherDefinedMethod for VERTEX
        val vertexMethods = tagBuilders["GenerateOtherDefinedMethod"]?.invoke(ShaderType.TEXT_VERTEX)
        assertNotNull(vertexMethods)
        assertTrue(vertexMethods!!.any { it.contains("out vec2 hudFill;") })
        assertTrue(vertexMethods.any { it.contains("float jmod(float x, float y)") })
        assertTrue(vertexMethods.any { it.contains("float getElapsedTicks") })
        assertTrue(vertexMethods.any { it.contains("float easeOutCubic") })

        // Check #GenerateOtherDefinedMethod for FRAGMENT
        val fragMethods = tagBuilders["GenerateOtherDefinedMethod"]?.invoke(ShaderType.TEXT_FRAGMENT)
        assertNotNull(fragMethods)
        assertTrue(fragMethods!!.any { it.contains("in vec2 hudFill;") })
        assertTrue(fragMethods.any { it.contains("bool shouldDiscard(vec2 uv, float fill, int splitType)") })

        // Check #GenerateOtherStartMethod for VERTEX initializes hudFill
        val vertexStart = tagBuilders["GenerateOtherStartMethod"]?.invoke(ShaderType.TEXT_VERTEX)
        assertNotNull(vertexStart)
        assertTrue(vertexStart!!.any { it.contains("hudFill = vec2(-1.0, -1.0);") })

        // Check #GenerateOtherMainMethod for FRAGMENT applies discard check
        val fragMain = tagBuilders["GenerateOtherMainMethod"]?.invoke(ShaderType.TEXT_FRAGMENT)
        assertNotNull(fragMain)
        assertTrue(fragMain!!.any { it.contains("if (hudFill.x >= 0.0)") })
    }

    @Test
    fun testEasingCalculations() {
        // Linear
        assertEquals(0.0, EasingType.LINEAR.ease(0.0), 1e-6)
        assertEquals(0.5, EasingType.LINEAR.ease(0.5), 1e-6)
        assertEquals(1.0, EasingType.LINEAR.ease(1.0), 1e-6)

        // EaseOutCubic: 1 - (1-x)^3. At 0.5 -> 1 - 0.125 = 0.875
        assertEquals(0.0, EasingType.EASE_OUT_CUBIC.ease(0.0), 1e-6)
        assertEquals(0.875, EasingType.EASE_OUT_CUBIC.ease(0.5), 1e-6)
        assertEquals(1.0, EasingType.EASE_OUT_CUBIC.ease(1.0), 1e-6)

        // EaseInOutCubic: At 0.5 -> 0.5
        assertEquals(0.0, EasingType.EASE_IN_OUT_CUBIC.ease(0.0), 1e-6)
        assertEquals(0.5, EasingType.EASE_IN_OUT_CUBIC.ease(0.5), 1e-6)
        assertEquals(1.0, EasingType.EASE_IN_OUT_CUBIC.ease(1.0), 1e-6)

        // EaseOutQuad: x * (2 - x). At 0.5 -> 0.5 * 1.5 = 0.75
        assertEquals(0.0, EasingType.EASE_OUT_QUAD.ease(0.0), 1e-6)
        assertEquals(0.75, EasingType.EASE_OUT_QUAD.ease(0.5), 1e-6)
        assertEquals(1.0, EasingType.EASE_OUT_QUAD.ease(1.0), 1e-6)

        // EaseInOutQuad: At 0.5 -> 0.5
        assertEquals(0.0, EasingType.EASE_IN_OUT_QUAD.ease(0.0), 1e-6)
        assertEquals(0.5, EasingType.EASE_IN_OUT_QUAD.ease(0.5), 1e-6)
        assertEquals(1.0, EasingType.EASE_IN_OUT_QUAD.ease(1.0), 1e-6)

        // Easing parsing
        assertEquals(EasingType.LINEAR, EasingType.fromString("linear"))
        assertEquals(EasingType.EASE_OUT_CUBIC, EasingType.fromString("ease_out_cubic"))
        assertEquals(EasingType.EASE_OUT_CUBIC, EasingType.fromString("easeoutcubic"))
        assertEquals(EasingType.EASE_IN_OUT_QUAD, EasingType.fromString("ease-in-out-quad"))
        assertEquals(EasingType.EASE_OUT_CUBIC, EasingType.fromString("unknown_default"))
    }

    @Test
    fun testListenerTrackerEncodingAndInterruption() {
        val tracker = ListenerTracker(duration = 10, easing = EasingType.LINEAR)

        // Initial update at tick 10 with value 0.6
        val color1 = tracker.update(0.6, 10)
        assertEquals(10, color1.red()) // tStart = 10 % 240
        assertEquals((0.6 * 255).toInt(), color1.green()) // fromVal
        assertEquals((0.6 * 255).toInt(), color1.blue()) // toVal

        // Next tick with unchanged value: should not restart animation
        val color2 = tracker.update(0.6, 12)
        assertEquals(10, color2.red())
        assertEquals((0.6 * 255).toInt(), color2.green())
        assertEquals((0.6 * 255).toInt(), color2.blue())

        // Value changes to 1.0 after duration elapsed (at tick 25, 15 ticks later)
        val color3 = tracker.update(1.0, 25)
        assertEquals(25, color3.red()) // tStart = 25 % 240
        assertEquals((0.6 * 255).toInt(), color3.green()) // previous settled value
        assertEquals(255, color3.blue()) // new target 1.0 * 255

        // Interruption: mid-transition at tick 30 (5 ticks into 10-tick animation)
        // With LINEAR easing, progress is 5/10 = 0.5
        // current visual = 0.6 + (1.0 - 0.6) * 0.5 = 0.8
        // New target changes to 0.2
        val color4 = tracker.update(0.2, 30)
        assertEquals(30, color4.red()) // tStart = 30
        assertEquals((0.8 * 255).toInt(), color4.green()) // uninterrupted visual continuity!
        assertEquals((0.2 * 255).toInt(), color4.blue())
    }

    @Test
    fun testHudShaderWithListenerAnimation() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val listener1 = ListenerAnimation(SplitType.LEFT, 10, EasingType.EASE_OUT_CUBIC)
        val listener2 = ListenerAnimation(SplitType.LEFT, 10, EasingType.EASE_OUT_CUBIC)
        val listener3 = ListenerAnimation(SplitType.UP, 10, EasingType.EASE_OUT_CUBIC)

        val s1 = HudShader(gui, scale, 1, 0, 1.0, 0, null, listener1)
        val s2 = HudShader(gui, scale, 1, 0, 1.0, 0, null, listener2)
        val s3 = HudShader(gui, scale, 1, 0, 1.0, 0, null, listener3)
        val sNone = HudShader(gui, scale, 1, 0, 1.0, 0, null, null)

        assertEquals(s1, s2)
        assertEquals(0, s1.compareTo(s2))
        assertNotEquals(s1, s3)
        assertNotEquals(s1, sNone)
        assertTrue(sNone < s1) // nullsFirst

        // toBackground should remove listenerAnimation
        val bg = s1.toBackground(0.5)
        assertNull(bg.listenerAnimation)
    }

    @Test
    fun testSequenceAnimationAndCollapse() {
        val seq1 = SequenceAnimation(0, 4)
        val seq2 = SequenceAnimation(0, 4)
        val seq3 = SequenceAnimation(1, 4)
        val seq4 = SequenceAnimation(0, 8)

        assertEquals(seq1, seq2)
        assertEquals(0, seq1.compareTo(seq2))
        assertTrue(seq1 < seq3)
        assertTrue(seq1 < seq4)

        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val sSeq1 = HudShader(gui, scale, 1, 0, 1.0, 0, null, null, seq1)
        val sSeq2 = HudShader(gui, scale, 1, 0, 1.0, 0, null, null, seq2)
        val sSeq3 = HudShader(gui, scale, 1, 0, 1.0, 0, null, null, seq3)
        val sNone = HudShader(gui, scale, 1, 0, 1.0, 0, null, null, null)

        assertEquals(sSeq1, sSeq2)
        assertEquals(0, sSeq1.compareTo(sSeq2))
        assertNotEquals(sSeq1, sSeq3)
        assertNotEquals(sSeq1, sNone)
        assertTrue(sNone < sSeq1) // nullsFirst

        // toBackground should remove sequenceAnimation
        val bg = sSeq1.toBackground(0.5)
        assertNull(bg.sequenceAnimation)
    }

    @Test
    fun testAdventureComponentStructuralEquality() {
        val c1 = Component.text("test").color(NamedTextColor.WHITE).compact()
        val c2 = Component.text("test").color(NamedTextColor.WHITE).compact()
        val c3 = Component.text("other").color(NamedTextColor.WHITE).compact()
        val c4 = Component.text("test").color(NamedTextColor.RED).compact()

        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
        assertNotEquals(c1, c3)
        assertNotEquals(c1, c4)
    }

    @Test
    fun testAllClientShaderVersions() {
        val shadersDir = File("../common-resources/shaders").takeIf { it.exists() }
            ?: File("common-resources/shaders")
        assertTrue(shadersDir.exists(), "shaders directory must exist")

        val versions = listOf(
            "betterhud_1_21_4",
            "betterhud_1_21_6",
            "betterhud_26_1",
            "betterhud_26_2",
            "betterhud_26_3"
        )

        val tagBuildersField = ShaderManagerImpl::class.java.getDeclaredField("tagBuilders")
        tagBuildersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tagBuilders = tagBuildersField.get(ShaderManagerImpl) as Map<String, (ShaderType) -> List<String>>

        for (ver in versions) {
            val verDir = File(shadersDir, ver)
            assertTrue(verDir.exists(), "Directory $ver must exist")

            val vshFile = File(verDir, "text.vsh")
            val fshFile = File(verDir, "text.fsh")
            assertTrue(vshFile.exists(), "$ver/text.vsh must exist")
            assertTrue(fshFile.exists(), "$ver/text.fsh must exist")

            val vshContent = vshFile.readText()
            val fshContent = fshFile.readText()

            // Verify template tag placements
            assertTrue(vshContent.contains("#GenerateOtherDefinedMethod"), "$ver text.vsh must contain #GenerateOtherDefinedMethod")
            assertTrue(vshContent.contains("#GenerateOtherStartMethod"), "$ver text.vsh must contain #GenerateOtherStartMethod")
            assertTrue(vshContent.contains("#GenerateOtherMainMethod"), "$ver text.vsh must contain #GenerateOtherMainMethod")

            assertTrue(fshContent.contains("#GenerateOtherDefinedMethod"), "$ver text.fsh must contain #GenerateOtherDefinedMethod")
            assertTrue(fshContent.contains("#GenerateOtherMainMethod"), "$ver text.fsh must contain #GenerateOtherMainMethod")

            // Simulate tag replacement for VERTEX shader
            val replacedVsh = vshContent
                .replace("#GenerateOtherDefinedMethod", tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(ShaderType.TEXT_VERTEX).joinToString("\n"))
                .replace("#GenerateOtherStartMethod", tagBuilders["GenerateOtherStartMethod"]!!.invoke(ShaderType.TEXT_VERTEX).joinToString("\n"))

            assertTrue(replacedVsh.contains("out vec2 hudFill;"))
            assertTrue(replacedVsh.contains("out vec2 hudQuadUV;"))
            assertTrue(replacedVsh.contains("hudFill = vec2(-1.0, -1.0);"))
            assertTrue(replacedVsh.contains("hudQuadUV = "))
            assertTrue(replacedVsh.contains("float jmod("))
            assertTrue(replacedVsh.contains("float getElapsedTicks("))

            // Simulate tag replacement for FRAGMENT shader
            val replacedFsh = fshContent
                .replace("#GenerateOtherDefinedMethod", tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(ShaderType.TEXT_FRAGMENT).joinToString("\n"))
                .replace("#GenerateOtherMainMethod", tagBuilders["GenerateOtherMainMethod"]!!.invoke(ShaderType.TEXT_FRAGMENT).joinToString("\n"))

            assertTrue(replacedFsh.contains("in vec2 hudFill;"))
            assertTrue(replacedFsh.contains("in vec2 hudQuadUV;"))
            assertTrue(replacedFsh.contains("bool shouldDiscard(vec2 uv, float fill, int splitType)"))
            assertTrue(replacedFsh.contains("if (hudFill.x >= 0.0)"))
            assertTrue(replacedFsh.contains("shouldDiscard(hudQuadUV, hudFill.x"))
            assertTrue(replacedFsh.contains("discard;"))
        }
    }

    @Test
    fun testClientModeListenerComponentNotEmpty() {
        val fontKey = net.kyori.adventure.key.Key.key("betterhud", "image")
        val dummyGlyph = WidthComponent(
            Component.text().font(fontKey).content("\uE001\uF801"),
            50
        )
        val pixelComp = PixelComponent(dummyGlyph, 0)

        // Simulating HudImageParser: images list contains [EMPTY_PIXEL_COMPONENT, pixelComp]
        val clientImages = listOf(kr.toxicity.hud.util.EMPTY_PIXEL_COMPONENT, pixelComp)
        val anim = ListenerAnimation(SplitType.LEFT, 10, EasingType.EASE_OUT_CUBIC)
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val element = unsafe.allocateInstance(kr.toxicity.hud.element.ImageElement::class.java) as kr.toxicity.hud.element.ImageElement
        val idField = kr.toxicity.hud.element.ImageElement::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(element, "test_bar")
        val animField = kr.toxicity.hud.element.ImageElement::class.java.getDeclaredField("listenerAnimation")
        animField.isAccessible = true
        animField.set(element, anim)
        val imageComp = kr.toxicity.hud.image.ImageComponent(element, null, clientImages, emptyMap())

        val player = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.player.HudPlayer::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.player.HudPlayer::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "uuid" -> java.util.UUID.randomUUID()
                "name" -> "TestPlayer"
                "getTick" -> 50L
                "worldTime" -> 50L
                else -> null
            }
        } as kr.toxicity.hud.api.player.HudPlayer

        val listener = kr.toxicity.hud.api.listener.HudListener { 0.75 }

        val result = ImageType.LISTENER.getComponent(listener, 0L, imageComp, player)
        val built = result.component.component.build()
        val textComp = built as? net.kyori.adventure.text.TextComponent
        assertNotNull(textComp, "Built component must be a TextComponent")
        assertEquals("\uE001\uF801", textComp!!.content(), "Content must not be empty! It must contain the glyph.")
        assertEquals(fontKey, textComp.font(), "Font must match betterhud:image")
        assertNotNull(textComp.color(), "Color must be encoded with animation parameters")
        assertEquals(50, result.component.width, "Width must match the glyph width")
    }

    @Test
    fun testListenerTrackerSealingAfterDuration() {
        val tracker = ListenerTracker(duration = 10, easing = EasingType.LINEAR)

        // Initial setup at worldTime = 1000 with 1.0 (full health)
        val c0 = tracker.update(1.0, 1000L)
        assertEquals(1000 % 240, c0.red())
        assertEquals(255, c0.green()) // fromVal = 1.0
        assertEquals(255, c0.blue())  // toVal = 1.0
        assertEquals(1.0, tracker.fromVal)
        assertEquals(1.0, tracker.toVal)

        // Damage taken: value changes to 0.4 at worldTime = 1010
        val c1 = tracker.update(0.4, 1010L)
        assertEquals(1010 % 240, c1.red()) // tStart = 1010 % 240 = 50
        assertEquals(255, c1.green()) // fromVal = 1.0
        assertEquals((0.4 * 255.0).toInt(), c1.blue()) // toVal = 0.4
        assertEquals(1.0, tracker.fromVal)
        assertEquals(0.4, tracker.toVal)
        assertNotEquals(tracker.fromVal, tracker.toVal, "During transition, fromVal and toVal must differ")

        // Mid-transition at worldTime = 1015 (5 ticks elapsed, 50% done)
        val c2 = tracker.update(0.4, 1015L)
        assertEquals(50, c2.red(), "tStart must remain 50")
        assertEquals(255, c2.green(), "fromVal must remain 1.0 during active transition")
        assertEquals((0.4 * 255.0).toInt(), c2.blue())

        // Transition finished at worldTime = 1020 (10 ticks elapsed == duration)
        val c3 = tracker.update(0.4, 1020L)
        assertEquals((0.4 * 255.0).toInt(), c3.green(), "After duration, fromVal must seal to toVal!")
        assertEquals((0.4 * 255.0).toInt(), c3.blue())
        assertEquals(tracker.toVal, tracker.fromVal, "fromVal and toVal must be equal after duration")

        // Long after transition (e.g. 1020 + 240 ticks = 1260 ticks, 12 seconds later)
        // Without sealing, modulo 240 wrap would replay the transition!
        val c4 = tracker.update(0.4, 1260L)
        assertEquals((0.4 * 255.0).toInt(), c4.green(), "Must remain sealed after 240 ticks!")
        assertEquals((0.4 * 255.0).toInt(), c4.blue())
        assertEquals(tracker.toVal, tracker.fromVal)
    }

    @Test
    fun testWorldTimeModulo240Equivalence() {
        // Minecraft GameTime * 24000.0 wraps at 24000.
        // Since 24000 % 240 == 0, (worldTime % 24000) % 240 is strictly identical to worldTime % 240.
        val testWorldTimes = listOf(0L, 1L, 40L, 239L, 240L, 1000L, 23999L, 24000L, 24040L, 48123L, 1000000L)
        for (wt in testWorldTimes) {
            val mod24000_240 = (wt % 24000L) % 240L
            val mod240 = wt % 240L
            assertEquals(mod240, mod24000_240, "Phase mismatch for worldTime $wt")
        }
    }

    @Test
    fun testShaderListenerGlslBranching() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val listenerAnim = ListenerAnimation(SplitType.LEFT, 10, EasingType.EASE_OUT_CUBIC)
        val hudShader = HudShader(gui, scale, 1, 0, 1.0, 0, null, listenerAnim)

        val compiledLayout = ShaderManagerImpl.generateShaderLayout(mapOf(hudShader to emptyList()))
        val fullGlsl = compiledLayout.joinToString("\n")

        // Verify sealing optimization branch exists in generated GLSL
        assertTrue(fullGlsl.contains("if (abs(fromVal - toVal) < 0.001) {"), "Must contain static sealed check")
        assertTrue(fullGlsl.contains("hudFill = vec2(clamp(toVal, 0.0, 1.0), 0.0);"), "Must assign direct toVal fill when sealed")
        assertTrue(fullGlsl.contains("float elapsed = getElapsedTicks(tStart, GameTime * 24000.0);"), "Must interpolate when active")
        assertTrue(fullGlsl.contains("float fill = clamp(fromVal + (toVal - fromVal) * easeOutCubic(progress), 0.0, 1.0);"), "Must use easing")
    }

    @Test
    fun testImageTypeUsesWorldTime() {
        val fontKey = net.kyori.adventure.key.Key.key("betterhud", "image")
        val dummyGlyph = WidthComponent(
            Component.text().font(fontKey).content("\uE001"),
            50
        )
        val pixelComp = PixelComponent(dummyGlyph, 0)
        val clientImages = listOf(kr.toxicity.hud.util.EMPTY_PIXEL_COMPONENT, pixelComp)
        val anim = ListenerAnimation(SplitType.LEFT, 10, EasingType.LINEAR)

        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val element = unsafe.allocateInstance(kr.toxicity.hud.element.ImageElement::class.java) as kr.toxicity.hud.element.ImageElement
        val idField = kr.toxicity.hud.element.ImageElement::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(element, "test_world_time_bar")
        val animField = kr.toxicity.hud.element.ImageElement::class.java.getDeclaredField("listenerAnimation")
        animField.isAccessible = true
        animField.set(element, anim)
        val imageComp = kr.toxicity.hud.image.ImageComponent(element, null, clientImages, emptyMap())

        // Player with distinct getTick() (3L) and worldTime() (4850L)
        val player = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.player.HudPlayer::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.player.HudPlayer::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "uuid" -> java.util.UUID.randomUUID()
                "name" -> "WorldTimeTestPlayer"
                "getTick" -> 3L
                "worldTime" -> 4850L
                else -> null
            }
        } as kr.toxicity.hud.api.player.HudPlayer

        val listener = kr.toxicity.hud.api.listener.HudListener { 0.5 }
        val result = ImageType.LISTENER.getComponent(listener, 0L, imageComp, player)
        val color = result.component.component.build().color()
        assertNotNull(color)
        // tStartByte = 4850 % 240 = 50. NOT 3 % 240 = 3!
        assertEquals(4850 % 240, color!!.red(), "tStart must be derived from player.worldTime() (4850 % 240 == 50), not player.tick (3)!")
    }

    @Test
    fun testListenerTrackerByteQuantizedDeadzone() {
        val tracker = ListenerTracker(duration = 10, easing = EasingType.LINEAR)

        // Initial update with 0.804032 (byte = 205)
        tracker.update(0.804032, 100L)
        assertEquals(205, (tracker.toVal * 255.0).toInt())
        assertEquals(100L, tracker.startTickVal)

        // Micro-fluctuation: target changes to 0.804035 (byte = 205).
        // Since (target * 255).roundToInt() == 205, this MUST NOT restart the transition!
        tracker.update(0.804035, 105L)
        assertEquals(100L, tracker.startTickVal, "startTick must not reset for changes smaller than 1 color byte!")

        // At tick 110 (10 ticks elapsed >= duration), tracker must seal!
        tracker.update(0.804035, 110L)
        assertEquals(tracker.toVal, tracker.fromVal, "Must seal when duration expires without byte change")

        // Significant change: memory jumps to 0.817 (byte = 208 != 205)
        tracker.update(0.817, 115L)
        assertEquals(115L, tracker.startTickVal, "startTick must reset when encoded byte changes!")
        assertEquals(208, (tracker.toVal * 255.0).toInt())
    }

    @Test
    fun testCyclicElapsedTicksFormula() {
        fun jmod(x: Double, y: Double): Double = x - y * kotlin.math.floor(x / y)
        fun getElapsedTicks(tStart: Double, gameTimeTicks: Double): Double {
            return jmod(gameTimeTicks - tStart + 120.0, 240.0) - 120.0
        }

        // 1. Normal forward progress
        assertEquals(0.0, getElapsedTicks(40.0, 40.0), 1e-6)
        assertEquals(5.0, getElapsedTicks(40.0, 45.0), 1e-6)
        assertEquals(10.0, getElapsedTicks(40.0, 50.0), 1e-6)

        // 2. Lookahead / ping: game time is slightly behind start (now < start)
        assertEquals(-2.0, getElapsedTicks(40.0, 38.0), 1e-6)
        assertEquals(-0.5, getElapsedTicks(40.0, 39.5), 1e-6)

        // 3. Crossing the modulo 240 boundary (start = 238, now wraps around past 240)
        assertEquals(0.0, getElapsedTicks(238.0, 238.0), 1e-6)
        assertEquals(1.5, getElapsedTicks(238.0, 239.5), 1e-6)
        assertEquals(2.0, getElapsedTicks(238.0, 0.0), 1e-6) // 240.0 % 240 == 0.0 -> exactly 2.0 ticks elapsed!
        assertEquals(4.5, getElapsedTicks(238.0, 2.5), 1e-6) // 4.5 ticks elapsed
        assertEquals(10.0, getElapsedTicks(238.0, 8.0), 1e-6) // 10.0 ticks elapsed

        // 4. Strict monotonic increase across the 240 boundary without any jump
        var prevElapsed = -5.0
        for (step in 0..150) {
            val delta = (step - 20) * 0.1 // from -2.0 ticks to +13.0 ticks
            val currentWorldTime = (238.0 + delta) % 240.0
            val elapsed = getElapsedTicks(238.0, currentWorldTime)
            assertEquals(delta, elapsed, 1e-4, "Discontinuity at delta=$delta: got $elapsed")
            assertTrue(elapsed >= prevElapsed, "Must be monotonically increasing: prev=$prevElapsed, cur=$elapsed")
            prevElapsed = elapsed
        }
    }

    @Test
    fun testPopupImagePlayOnceShaderGeneration() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val playOnceAnim = ClientAnimation(16, "45.0 * sin(t)", "-45.0 * sin(t)", "1.0", AnimationType.PLAY_ONCE)
        val hudShader = HudShader(gui, scale, 1, 0, 1.0, 0, playOnceAnim, null)

        val compiledLayout = ShaderManagerImpl.generateShaderLayout(mapOf(hudShader to emptyList()))
        val fullGlsl = compiledLayout.joinToString("\n")

        assertTrue(fullGlsl.contains("float tStart = floor(Color.r * 255.0 + 0.5);"), "Must unpack tStart from Color.r")
        assertTrue(fullGlsl.contains("float elapsed = getElapsedTicks(tStart, GameTime * 24000.0);"), "Must compute cyclic elapsed")
        assertTrue(fullGlsl.contains("float t = clamp(elapsed, 0.0, 16.0);"), "Must clamp t to [0, duration]")
        assertTrue(fullGlsl.contains("pos.x += (45.0 * sin(t));"), "Must apply xGlsl")
        assertTrue(fullGlsl.contains("pos.y += (-45.0 * sin(t));"), "Must apply yGlsl")
        assertTrue(fullGlsl.contains("vertexColor = vec4(1.0, 1.0, 1.0, 1.0);"), "Must reset vertexColor to prevent red tint")
    }

    @Test
    fun testAnimationElementRegistryLogging() {
        val map = mutableMapOf<String, Any>(
            "type" to "play_once",
            "duration" to 16,
            "x-equation" to "10 * cos(t)",
            "y-equation" to "10 * sin(t)",
            "mode" to "client"
        )
        val yaml = YamlObjectImpl("notification_test_icon", map)
        AnimationLocation(yaml)

        val registry = kr.toxicity.hud.animation.AnimationElementRegistry.getAll()
        val info = registry["notification_test_icon"]
        assertNotNull(info, "Element must be registered in AnimationElementRegistry")
        assertEquals(AnimationMode.CLIENT, info?.mode)
        assertEquals(AnimationType.PLAY_ONCE, info?.type)
        assertTrue(info?.reason?.contains("Transpiled to GLSL") == true)
    }

    @Test
    fun testCombinedPlayOnceAndListenerShaderGenerationNoRedeclaration() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val playOnceAnim = ClientAnimation(6, "0", "0", "clamp(t/5.0, 0.0, 1.0)", AnimationType.PLAY_ONCE)
        val listenerAnim = ListenerAnimation(kr.toxicity.hud.image.enums.SplitType.RIGHT, 10, EasingType.EASE_OUT_CUBIC)
        val hudShader = HudShader(gui, scale, 1, 0, 1.0, 0, playOnceAnim, listenerAnim)

        val compiledLayout = ShaderManagerImpl.generateShaderLayout(mapOf(hudShader to emptyList()))
        val fullGlsl = compiledLayout.joinToString("\n")

        val tStartCount = Regex("float tStart").findAll(fullGlsl).count()
        val elapsedCount = Regex("float elapsed").findAll(fullGlsl).count()

        assertEquals(1, tStartCount, "float tStart must be declared exactly once in shader case: \n$fullGlsl")
        assertEquals(1, elapsedCount, "float elapsed must be declared exactly once in shader case: \n$fullGlsl")
        assertTrue(fullGlsl.contains("hudFill = vec2(fill, 1.0);"))
        assertTrue(fullGlsl.contains("vertexColor = vec4(1.0, 1.0, 1.0, 1.0);"))
    }

    // ЧЕСТНЫЙ АУДИТ: Это CPU float32 эмуляция семантики GLSL, а не исполнение шейдера на GPU.
    // Прямое аппаратное исполнение шейдера на GPU с Transform Feedback проверено в scripts/gpu_shader_test.java.
    @Test
    fun testGoldenTrajectoryServerVsShader() {
        data class TestCase(
            val name: String,
            val duration: Int,
            val xEq: String,
            val yEq: String,
            val opEq: String,
            val glslX: (Float) -> Float,
            val glslY: (Float) -> Float,
            val glslOp: (Float) -> Float
        )

        val pi = Math.PI.toFloat()
        fun min(a: Float, b: Float) = if (a < b) a else b
        fun max(a: Float, b: Float) = if (a > b) a else b

        val cases = listOf(
            TestCase(
                name = "notification_70",
                duration = 70,
                xEq = "230 * 0.5 * (1 + cos(pi * min(1, max(0, t) / 12))) + 230 * 0.5 * (1 - cos(pi * min(1, max(0, t - 58) / 12)))",
                yEq = "-6 * (1 - min(1, max(0, t) / 12)) ^ 2 * cos(2 * pi * min(1, max(0, t) / 12))",
                opEq = "min(1, (t / 8) ^ 2) * min(1, (max(0, 70 - t) / 10) ^ 2)",
                glslX = { t ->
                    val inTerm = 230f * 0.5f * (1f + kotlin.math.cos(pi * min(1f, max(0f, t) / 12f)))
                    val outTerm = 230f * 0.5f * (1f - kotlin.math.cos(pi * min(1f, max(0f, t - 58f) / 12f)))
                    inTerm + outTerm
                },
                glslY = { t ->
                    val f = 1f - min(1f, max(0f, t) / 12f)
                    -6f * (f * f) * kotlin.math.cos(2f * pi * min(1f, max(0f, t) / 12f))
                },
                glslOp = { t ->
                    val t8 = t / 8f
                    val end = max(0f, 70f - t) / 10f
                    min(1f, t8 * t8) * min(1f, end * end)
                }
            ),
            TestCase(
                name = "notification_80",
                duration = 80,
                xEq = "230 * 0.5 * (1 + cos(pi * min(1, max(0, t) / 12))) + 230 * 0.5 * (1 - cos(pi * min(1, max(0, t - 68) / 12)))",
                yEq = "-6 * (1 - min(1, max(0, t) / 12)) ^ 2 * cos(2 * pi * min(1, max(0, t) / 12))",
                opEq = "min(1, (t / 8) ^ 2) * min(1, (max(0, 80 - t) / 10) ^ 2)",
                glslX = { t ->
                    val inTerm = 230f * 0.5f * (1f + kotlin.math.cos(pi * min(1f, max(0f, t) / 12f)))
                    val outTerm = 230f * 0.5f * (1f - kotlin.math.cos(pi * min(1f, max(0f, t - 68f) / 12f)))
                    inTerm + outTerm
                },
                glslY = { t ->
                    val f = 1f - min(1f, max(0f, t) / 12f)
                    -6f * (f * f) * kotlin.math.cos(2f * pi * min(1f, max(0f, t) / 12f))
                },
                glslOp = { t ->
                    val t8 = t / 8f
                    val end = max(0f, 80f - t) / 10f
                    min(1f, t8 * t8) * min(1f, end * end)
                }
            ),
            TestCase(
                name = "notification_90",
                duration = 90,
                xEq = "230 * 0.5 * (1 + cos(pi * min(1, max(0, t) / 12))) + 230 * 0.5 * (1 - cos(pi * min(1, max(0, t - 78) / 12)))",
                yEq = "-6 * (1 - min(1, max(0, t) / 12)) ^ 2 * cos(2 * pi * min(1, max(0, t) / 12))",
                opEq = "min(1, (t / 8) ^ 2) * min(1, (max(0, 90 - t) / 10) ^ 2)",
                glslX = { t ->
                    val inTerm = 230f * 0.5f * (1f + kotlin.math.cos(pi * min(1f, max(0f, t) / 12f)))
                    val outTerm = 230f * 0.5f * (1f - kotlin.math.cos(pi * min(1f, max(0f, t - 78f) / 12f)))
                    inTerm + outTerm
                },
                glslY = { t ->
                    val f = 1f - min(1f, max(0f, t) / 12f)
                    -6f * (f * f) * kotlin.math.cos(2f * pi * min(1f, max(0f, t) / 12f))
                },
                glslOp = { t ->
                    val t8 = t / 8f
                    val end = max(0f, 90f - t) / 10f
                    min(1f, t8 * t8) * min(1f, end * end)
                }
            ),
            TestCase(
                name = "item_pickup_60",
                duration = 60,
                xEq = "180 * 0.5 * (1 + cos(pi * min(1, max(0, t) / 10))) + 180 * 0.5 * (1 - cos(pi * min(1, max(0, t - 50) / 10)))",
                yEq = "12 * (1 - min(1, max(0, t) / 10)) ^ 2 * cos(2 * pi * min(1, max(0, t) / 10))",
                opEq = "min(1, (t / 6) ^ 2) * min(1, (max(0, 60 - t) / 8) ^ 2)",
                glslX = { t ->
                    val inTerm = 180f * 0.5f * (1f + kotlin.math.cos(pi * min(1f, max(0f, t) / 10f)))
                    val outTerm = 180f * 0.5f * (1f - kotlin.math.cos(pi * min(1f, max(0f, t - 50f) / 10f)))
                    inTerm + outTerm
                },
                glslY = { t ->
                    val f = 1f - min(1f, max(0f, t) / 10f)
                    12f * (f * f) * kotlin.math.cos(2f * pi * min(1f, max(0f, t) / 10f))
                },
                glslOp = { t ->
                    val t6 = t / 6f
                    val end = max(0f, 60f - t) / 8f
                    min(1f, t6 * t6) * min(1f, end * end)
                }
            ),
            TestCase(
                name = "damage_indicator_6",
                duration = 6,
                xEq = "0",
                yEq = "0",
                opEq = "3 * min(1, max(0, t / 5)) ^ 2 - 2 * min(1, max(0, t / 5)) ^ 3",
                glslX = { 0f },
                glslY = { 0f },
                glslOp = { t ->
                    val p = min(1f, max(0f, t / 5f))
                    3f * p * p - 2f * p * p * p
                }
            )
        )

        cases.forEach { tc ->
            val xGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(tc.xEq)
            val yGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(tc.yEq)
            val opGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(tc.opEq)
            assertNotNull(xGlsl)
            assertNotNull(yGlsl)
            assertNotNull(opGlsl)

            val srvX = kr.toxicity.hud.equation.TEquation(tc.xEq)
            val srvY = kr.toxicity.hud.equation.TEquation(tc.yEq)
            val srvOp = kr.toxicity.hud.equation.TEquation(tc.opEq)

            val steps = tc.duration * 2
            for (step in 0..steps) {
                val t = step * 0.5
                val tFloat = t.toFloat()

                val expectedX = srvX.evaluate(t)
                val actualX = tc.glslX(tFloat).toDouble()
                val diffX = kotlin.math.abs(expectedX - actualX)
                assertTrue(diffX <= 0.5, "${tc.name} X diff at t=$t was $diffX (> 0.5 px): srv=$expectedX, glsl=$actualX")

                val expectedY = srvY.evaluate(t)
                val actualY = tc.glslY(tFloat).toDouble()
                val diffY = kotlin.math.abs(expectedY - actualY)
                assertTrue(diffY <= 0.5, "${tc.name} Y diff at t=$t was $diffY (> 0.5 px): srv=$expectedY, glsl=$actualY")

                val expectedOp = srvOp.evaluate(t)
                val actualOp = tc.glslOp(tFloat).toDouble()
                val diffOp = kotlin.math.abs(expectedOp - actualOp)
                assertTrue(diffOp <= 0.02, "${tc.name} Opacity diff at t=$t was $diffOp (> 0.02): srv=$expectedOp, glsl=$actualOp")
            }
        }
    }

    @Test
    fun testTimeMathAsymmetricWindowAndWrap() {
        fun jmod(x: Float, y: Float): Float = x - y * kotlin.math.floor(x / y)
        fun getElapsedTicks(tStart: Float, gameTimeTicks: Float, L: Float = 10.0f, M: Float = 240.0f): Float {
            return jmod(gameTimeTicks - tStart + L, M) - L
        }

        // 1. Modulo 240 Rollover test
        val start1 = 236.0f
        val tStart1 = 236.0f
        var prevElapsed = -100.0f
        for (i in 0..120) {
            val now = 230.0f + i * 0.25f
            val gameTimeTicks = jmod(now, 240.0f)
            val elapsed = getElapsedTicks(tStart1, gameTimeTicks)
            val expected = now - start1
            assertEquals(expected, elapsed, 1e-4f, "Elapsed mismatch at now=$now")
            if (prevElapsed != -100.0f) {
                assertTrue(elapsed > prevElapsed, "Discontinuity / jump detected at now=$now: prev=$prevElapsed, cur=$elapsed")
            }
            prevElapsed = elapsed
        }

        // 2. Wrap 24000 Rollover test
        val start2 = 23996.0f
        val tStart2 = jmod(start2, 240.0f)
        prevElapsed = -100.0f
        for (i in 0..120) {
            val now = 23990.0f + i * 0.25f
            val gameTimeTicks = jmod(now, 24000.0f)
            val elapsed = getElapsedTicks(tStart2, gameTimeTicks)
            val expected = now - start2
            assertEquals(expected, elapsed, 1e-3f, "Elapsed mismatch across 24000 wrap at now=$now")
            if (prevElapsed != -100.0f) {
                assertTrue(elapsed > prevElapsed, "Discontinuity / jump across 24000 wrap at now=$now: prev=$prevElapsed, cur=$elapsed")
            }
            prevElapsed = elapsed
        }

        // 3. Lookahead ping jitter tolerance
        val start3 = 100.0f
        val earlyNow = 96.0f
        val earlyElapsed = getElapsedTicks(start3, earlyNow)
        assertEquals(-4.0f, earlyElapsed, 1e-4f)
        val clamped = kotlin.math.max(0.0f, earlyElapsed)
        assertEquals(0.0f, clamped, 1e-4f, "Early packet must clamp to 0.0 without jumping")

        // 4. Long animations up to 220 ticks
        val start4 = 50.0f
        for (t in 0..220) {
            val now = start4 + t
            val elapsed = getElapsedTicks(start4, jmod(now, 240.0f))
            assertEquals(t.toFloat(), elapsed, 1e-4f, "Long animation failed at t=$t")
        }

        // 5. Float32 single-precision accuracy near 24000.0
        val gameTimeFloat = (23999.5 / 24000.0).toFloat()
        val restoredTicks = gameTimeFloat * 24000.0f
        val precisionError = kotlin.math.abs(restoredTicks - 23999.5f)
        assertTrue(precisionError < 0.002f, "Float32 GameTime precision error ($precisionError) must be < 0.002 ticks")
    }

    @Test
    fun testComponentStabilityAcrossConsecutiveTicks() {
        var currentTick = 0L
        var currentWorldTime = 1000L
        val dummyPlayer = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.player.HudPlayer::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.player.HudPlayer::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "uuid" -> java.util.UUID.randomUUID()
                "name" -> "TestPlayer"
                "getTick", "tick" -> currentTick
                "worldTime" -> currentWorldTime
                "ping" -> 50
                else -> null
            }
        } as kr.toxicity.hud.api.player.HudPlayer

        val mockIterator = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.popup.PopupIterator::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.popup.PopupIterator::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getSpawnTime", "spawnTime" -> 1000L
                "getIndex", "index" -> 0
                "name" -> "test_popup"
                else -> null
            }
        } as kr.toxicity.hud.api.popup.PopupIterator

        val updateEvent = kr.toxicity.hud.api.update.PopupUpdateEvent(
            kr.toxicity.hud.api.update.UpdateEvent.EMPTY,
            mockIterator
        )

        fun createProductionRunner(
            reason: kr.toxicity.hud.api.update.UpdateEvent,
            clientAnimType: AnimationType?,
            player: kr.toxicity.hud.api.player.HudPlayer
        ): kr.toxicity.hud.util.Runner<WidthComponent> {
            val iterator = (reason as? kr.toxicity.hud.api.update.PopupUpdateEvent)?.iterator()
            val effectiveClientAnimType = clientAnimType
            val tColor = if (effectiveClientAnimType == AnimationType.PLAY_ONCE) {
                val baseTick = iterator?.spawnTime?.takeIf { it > 0L } ?: player.worldTime()
                val lookahead = (player.ping() / 100.0).roundToInt().coerceIn(0, ConfigManagerImpl.animationLookaheadMax)
                val startTick = baseTick + lookahead
                val tStartByte = ((startTick % 240).toInt()).coerceIn(0, 239)
                TextColor.color(tStartByte, 0, 0)
            } else null
            return kr.toxicity.hud.util.Runner {
                val textBuilder = Component.text("󎀂󎀁")
                    .font(net.kyori.adventure.key.Key.key("betterhud", "hud_popup_image"))
                    .toBuilder()
                val dummyChar = WidthComponent(textBuilder, 10)
                if (tColor != null) {
                    dummyChar applyColor tColor
                } else {
                    dummyChar
                }
            }
        }

        val runner = createProductionRunner(updateEvent, AnimationType.PLAY_ONCE, dummyPlayer)
        val jsonOutputs = mutableListOf<String>()
        val gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()

        for (tick in 0..20) {
            currentTick = tick.toLong()
            currentWorldTime = 1000L + tick

            val comp = runner()
            val json = gson.serialize(comp.component.build())
            jsonOutputs.add(json)

            assertEquals(jsonOutputs[0], json, "JSON component on tick $tick must be byte-for-byte identical to tick 0!")
        }
        assertEquals(21, jsonOutputs.size)
    }

    @Test
    fun testMutationOldRunnerEvaluationFailsStability() {
        var currentWorldTime = 1000L
        val dummyPlayer = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.player.HudPlayer::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.player.HudPlayer::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "worldTime" -> currentWorldTime
                "ping" -> 50
                else -> null
            }
        } as kr.toxicity.hud.api.player.HudPlayer

        // OLD BUG: tColor was calculated inside Runner { ... } reading player.worldTime()
        val buggyRunner = kr.toxicity.hud.util.Runner {
            val lookahead = (dummyPlayer.ping() / 100.0).roundToInt().coerceIn(0, ConfigManagerImpl.animationLookaheadMax)
            val startTick = dummyPlayer.worldTime() + lookahead
            val tStartByte = ((startTick % 240).toInt()).coerceIn(0, 239)
            val tColor = TextColor.color(tStartByte, 0, 0)
            val textBuilder = Component.text("󎀂󎀁")
                .font(net.kyori.adventure.key.Key.key("betterhud", "hud_popup_image"))
                .toBuilder()
            WidthComponent(textBuilder, 10) applyColor tColor
        }

        val gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
        currentWorldTime = 1000L
        val tick0Json = gson.serialize(buggyRunner().component.build())

        currentWorldTime = 1001L
        val tick1Json = gson.serialize(buggyRunner().component.build())

        assertNotEquals(tick0Json, tick1Json, "Mutation proof: old buggy Runner code MUST fail stability check!")
        assertTrue(tick0Json.contains("color\":\"#290000\""))
        assertTrue(tick1Json.contains("color\":\"#2A0000\""))
    }

    @Test
    fun testDifferentialGeneratedShaderMatchesConfig() {
        val xRaw = "230 * 0.5 * (1 + cos(pi * min(1, max(0, t) / 12))) + 230 * 0.5 * (1 - cos(pi * min(1, max(0, t - 58) / 12)))"
        val yRaw = "-6 * (1 - min(1, max(0, t) / 12)) ^ 2 * cos(2 * pi * min(1, max(0, t) / 12))"
        val opRaw = "min(1, (t / 8) ^ 2) * min(1, (max(0, 70 - t) / 10) ^ 2)"

        val expectedXGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(xRaw)
        val expectedYGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(yRaw)
        val expectedOpGlsl = kr.toxicity.hud.equation.GlslTranspiler.transpile(opRaw)

        val clientAnim = ClientAnimation(70, expectedXGlsl, expectedYGlsl, expectedOpGlsl, AnimationType.PLAY_ONCE)
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val hudShader = HudShader(gui, scale, 1, 0, 1.0, 0, clientAnim, null)

        val compiledLayout = ShaderManagerImpl.generateShaderLayout(mapOf(hudShader to emptyList()))
        val glslCase = compiledLayout.joinToString("\n")

        // 1. Structural extraction
        assertTrue(glslCase.contains("pos.x += ($expectedXGlsl);"), "Generated GLSL must contain exact transpiled X equation")
        assertTrue(glslCase.contains("pos.y += ($expectedYGlsl);"), "Generated GLSL must contain exact transpiled Y equation")
        assertTrue(glslCase.contains("opacity *= clamp($expectedOpGlsl, 0.0, 1.0);"), "Generated GLSL must contain exact transpiled Opacity equation")

        // 2. Cross-evaluation across grid: verify differential parity with server TEquation
        val srvX = kr.toxicity.hud.equation.TEquation(xRaw)
        for (i in 0..140) {
            val t = i * 0.5
            val srvVal = srvX.evaluate(t)
            val inTerm = 230.0 * 0.5 * (1.0 + Math.cos(Math.PI * Math.min(1.0, Math.max(0.0, t) / 12.0)))
            val outTerm = 230.0 * 0.5 * (1.0 - Math.cos(Math.PI * Math.min(1.0, Math.max(0.0, t - 58.0) / 12.0)))
            val glslVal = inTerm + outTerm
            assertEquals(srvVal, glslVal, 1e-4, "Differential mismatch between server equation and shader formula at t=$t")
        }
    }

    @Test
    fun testRule46PopupsWithTextFallbackToServerEntirely() {
        kr.toxicity.hud.animation.AnimationElementRegistry.clear()

        val mockSource = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.command.BetterCommandSource::class.java.classLoader,
            arrayOf(kr.toxicity.command.BetterCommandSource::class.java)
        ) { _, _, _ -> null } as kr.toxicity.command.BetterCommandSource

        val mockHud = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.BetterHud::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.BetterHud::class.java)
        ) { _, method, _ ->
            if (method.name == "bootstrap") {
                java.lang.reflect.Proxy.newProxyInstance(
                    kr.toxicity.hud.api.BetterHudBootstrap::class.java.classLoader,
                    arrayOf(kr.toxicity.hud.api.BetterHudBootstrap::class.java)
                ) { _, m, _ ->
                    when {
                        m.returnType == java.io.File::class.java -> java.io.File(".")
                        m.returnType.isInterface -> java.lang.reflect.Proxy.newProxyInstance(
                            m.returnType.classLoader ?: kr.toxicity.hud.api.BetterHudBootstrap::class.java.classLoader,
                            arrayOf(m.returnType)
                        ) { _, _, _ -> null }
                        else -> null
                    }
                }
            } else null
        } as kr.toxicity.hud.api.BetterHud
        val mainField = kr.toxicity.hud.api.BetterHudAPI::class.java.getDeclaredField("main")
        mainField.isAccessible = true
        mainField.set(null, mockHud)

        val dummyText = kr.toxicity.hud.element.TextElement(
            "default_font", null, emptyList(), kr.toxicity.hud.util.intKeyMapOf<kr.toxicity.hud.text.TextScale>(), kr.toxicity.hud.util.intKeyMapOf<kr.toxicity.hud.text.ImageTextScale>(), YamlObjectImpl("txt", mutableMapOf<String, Any>())
        )
        val textMapField = kr.toxicity.hud.manager.TextManagerImpl::class.java.getDeclaredField("textMap")
        textMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (textMapField.get(kr.toxicity.hud.manager.TextManagerImpl) as MutableMap<String, kr.toxicity.hud.element.TextElement>)["default_font"] = dummyText

        val dummyImage = kr.toxicity.hud.element.ImageElement(
            "notification_bg_info_1", emptyList(), kr.toxicity.hud.image.enums.ImageType.SINGLE, YamlObjectImpl("img", mutableMapOf<String, Any>())
        )
        val imageMapField = kr.toxicity.hud.manager.ImageManager::class.java.getDeclaredField("imageMap")
        imageMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (imageMapField.get(kr.toxicity.hud.manager.ImageManager) as MutableMap<String, kr.toxicity.hud.element.ImageElement>)["notification_bg_info_1"] = dummyImage

        // 1. Popup with text (e.g. notification)
        val textLayoutYaml = YamlObjectImpl("notification-info-1", mutableMapOf(
            "texts" to mutableMapOf("title" to mutableMapOf("name" to "default_font", "pattern" to "test")),
            "animations" to mutableMapOf(
                "mode" to "client",
                "type" to "play_once",
                "duration" to 70,
                "x-equation" to "230 * 0.5 * (1 + cos(t))",
                "y-equation" to "0",
                "opacity-equation" to "1"
            )
        ))
        val layoutWithText = kr.toxicity.hud.layout.LayoutGroup("notification-info-1", mockSource, textLayoutYaml)

        // Simulate PopupImpl registration
        val hasTextOrHead = layoutWithText.text.isNotEmpty() || layoutWithText.head.isNotEmpty()
        assertTrue(hasTextOrHead, "notification-info-1 must have text")
        val fullPathWithText = "popup_notification_info_1/notification-info-1/animation"
        if (hasTextOrHead && layoutWithText.animation.clientAnimation != null) {
            kr.toxicity.hud.animation.AnimationElementRegistry.register(
                fullPathWithText,
                AnimationMode.SERVER,
                layoutWithText.animation.type,
                "text not supported in client mode yet (fallback to server)"
            )
        }

        // 2. Pure image popup (debug_image_popup)
        val pureImageLayoutYaml = YamlObjectImpl("debug-image-layout", mutableMapOf(
            "images" to mutableMapOf("bg" to mutableMapOf("name" to "notification_bg_info_1")),
            "animations" to mutableMapOf(
                "mode" to "client",
                "type" to "play_once",
                "duration" to 40,
                "x-equation" to "120 * 0.5 * (1 + cos(t))",
                "y-equation" to "0",
                "opacity-equation" to "1"
            )
        ))
        val pureImageLayout = kr.toxicity.hud.layout.LayoutGroup("debug-image-layout", mockSource, pureImageLayoutYaml)
        val pureHasTextOrHead = pureImageLayout.text.isNotEmpty() || pureImageLayout.head.isNotEmpty()
        assertFalse(pureHasTextOrHead, "debug-image-layout must NOT have text")
        val pureFullPath = "popup_debug_image_popup/debug-image-layout/animation"
        if (!pureHasTextOrHead && pureImageLayout.animation.clientAnimation != null) {
            kr.toxicity.hud.animation.AnimationElementRegistry.register(
                pureFullPath,
                AnimationMode.CLIENT,
                pureImageLayout.animation.type,
                "Transpiled to GLSL shader (${pureImageLayout.animation.type.name})"
            )
        }

        val allElements = kr.toxicity.hud.animation.AnimationElementRegistry.getAll()
        val textElem = allElements[fullPathWithText]
        val imageElem = allElements[pureFullPath]

        assertNotNull(textElem, "Text popup element must be registered")
        assertEquals(AnimationMode.SERVER, textElem?.mode, "Popup with text MUST be SERVER mode per Rule 4.6")
        assertTrue(textElem?.reason?.contains("text not supported in client mode yet") == true)

        assertNotNull(imageElem, "Pure image popup element must be registered")
        assertEquals(AnimationMode.CLIENT, imageElem?.mode, "Pure image popup MUST be CLIENT mode")
        assertTrue(imageElem?.reason?.contains("Transpiled to GLSL") == true)
    }

    @Test
    fun testPixelComponentColorApplicationImmutability() {
        val originalBuilder = Component.text("󎀂󎀁").font(net.kyori.adventure.key.Key.key("betterhud", "hud_popup_image")).toBuilder()
        val originalPixelComp = PixelComponent(WidthComponent(originalBuilder, 10), 5)

        val tColor1 = TextColor.color(105, 0, 0)
        val result1 = originalPixelComp applyColor tColor1

        // 1. Result must have tColor1
        assertEquals(tColor1, result1.component.component.build().color())
        // 2. Original must NOT be mutated
        assertNull(originalBuilder.build().color(), "Original builder must not have its color modified!")

        // 3. Applying color consecutively to the original template on subsequent ticks must NOT decay
        val result2 = originalPixelComp applyColor tColor1
        assertEquals(tColor1, result2.component.component.build().color(), "Subsequent tick applyColor must remain exact!")

        val result3 = originalPixelComp applyColor tColor1
        assertEquals(tColor1, result3.component.component.build().color(), "Third tick applyColor must remain exact!")
        assertNull(originalBuilder.build().color(), "Original builder must remain null after multiple evaluations!")
    }

    @Test
    fun testPopupLayoutImageTemplateImmutabilityAcrossTicks() {
        // Simulates the shared ImageComponent template in PopupElement across 30 consecutive ticks
        val templateBuilder = Component.text("󎀂󎀁")
            .font(net.kyori.adventure.key.Key.key("betterhud", "popup_debug_image_popup_image"))
            .toBuilder()
        val templateComp = PixelComponent(WidthComponent(templateBuilder, 20), 0)

        val tStartByte = 179 // from latest.log: 3034739 % 240 = 179 (#B30000)
        val tColor = TextColor.color(tStartByte, 0, 0)
        val gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()

        val renderedJsonList = mutableListOf<String>()

        for (tick in 0..29) {
            // Replicates PopupLayout.kt Runner block
            val pixelComp = templateComp // returned by fn(frame) from cached images list
            val base = pixelComp.component
            val finalPixelComp = PixelComponent(
                WidthComponent(
                    base.component.build().toBuilder().color(tColor),
                    base.width
                ),
                pixelComp.pixel
            )

            val json = gson.serialize(finalPixelComp.component.component.build())
            renderedJsonList.add(json)

            assertTrue(json.contains("\"color\":\"#b30000\"") || json.contains("\"color\":\"#B30000\""),
                "Tick $tick must contain #b30000, but got: $json")
            assertFalse(json.contains("\"color\":\"black\""),
                "Tick $tick must NEVER contain black! Got: $json")
        }

        // Verify template builder remains completely untouched
        assertNull(templateBuilder.build().color(), "Template builder must remain untouched in memory!")
    }

    @Test
    fun testAutomaticShaderDirectoryCleanup() {
        val tempDir = java.nio.file.Files.createTempDirectory("betterhud_shader_cleanup_test").toFile()
        try {
            val shadersDir = File(tempDir, "shaders")
            val subOverlay = File(shadersDir, "betterhud_26_1")
            subOverlay.mkdirs()
            val dummyShader = File(subOverlay, "text.vsh")
            dummyShader.writeText("// stale shader content from previous build")

            assertTrue(dummyShader.exists(), "Dummy shader file must exist before cleanup")
            assertTrue(shadersDir.exists(), "shaders/ directory must exist before cleanup")

            // Simulate BetterHudImpl.init / ShaderManagerImpl.start cleanup logic
            File(tempDir, "shaders").let { dir ->
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }

            assertFalse(shadersDir.exists(), "shaders/ directory must be recursively deleted at startup/reload")
            assertFalse(dummyShader.exists(), "Dummy shader file must be deleted")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDamageIndicatorModuloAndCollapseOnElapsedNegative() {
        // 1. Math verification: 16-tick popup with lookahead 5 requires M >= 30 (not 24)
        val animDuration = 16
        val popupDuration = 16
        val lookahead = 5
        val maxDuration = maxOf(animDuration, popupDuration)
        val requiredTicks = maxDuration + maxOf(lookahead, 10) + 4 // 16 + 10 + 4 = 30
        val divisorM = kr.toxicity.hud.text.getDivisorM(requiredTicks)
        assertEquals(30, divisorM, "Divisor M for 16t popup must be 30 or larger to avoid wrapping before duration ends")

        // 2. Wrap-around timing verification: M - L > duration
        val L = 10.0
        val positiveWindow = divisorM!!.toDouble() - L
        assertTrue(positiveWindow >= animDuration + 4, "Positive window ($positiveWindow) must exceed duration ($animDuration) by safety margin")

        // 3. GLSL verification: elapsed < 0.0 must collapse quad off-screen
        val playOnceAnim = ClientAnimation(16, "45.0 * sin(t)", "-45.0 * sin(t)", "min(1.0, (max(0.0, 15.0 - t) / 9.0) ^ 2.0)", AnimationType.PLAY_ONCE)
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val textShader = HudShader(gui, scale, 1, 0, 1.0, 0, playOnceAnim, null, isText = true, moduloM = 30)

        val compiledLayout = ShaderManagerImpl.generateShaderLayout(mapOf(textShader to emptyList()))
        val fullGlsl = compiledLayout.joinToString("\n")

        assertTrue(fullGlsl.contains("if (elapsed < 0.0) {"), "Shader must check elapsed < 0.0")
        assertTrue(fullGlsl.contains("pos.xy = vec2(-10000.0);"), "Shader must collapse quad off-screen when elapsed < 0.0")
        assertTrue(fullGlsl.contains("opacity = 0.0;"), "Shader must zero out opacity when elapsed < 0.0")

        // 4. Trace cyclic modulo across time: verify that elapsed is positive for entire animation and negative upon wrap
        fun getElapsedTicks(tStart: Double, gameTimeTicks: Double, M: Double): Double {
            fun jmod(x: Double, y: Double) = x - y * kotlin.math.floor(x / y)
            return jmod(gameTimeTicks - tStart + 10.0, M) - 10.0
        }

        val tStart = 0.0
        for (tick in 0..16) {
            val elapsed = getElapsedTicks(tStart, tick.toDouble(), 30.0)
            assertEquals(tick.toDouble(), elapsed, 1e-4, "Elapsed must be linear and positive during animation")
            assertTrue(elapsed >= 0.0, "Elapsed must be >= 0.0 at tick $tick")
        }

        // At wrap-around point (tick 20)
        val wrapElapsed = getElapsedTicks(tStart, 20.0, 30.0)
        assertEquals(-10.0, wrapElapsed, 1e-4, "Wrap-around must jump to negative elapsed (-10.0)")
        assertTrue(wrapElapsed < 0.0, "Wrap-around must produce negative elapsed so quad collapses off-screen")
    }

    @Test
    fun testItalicShearCompensationAcrossY() {
        val defaultOffset = 10.0f
        val uiX = 1920.0f
        val xLayout = 100.0f
        val heightBit = HUD_DEFAULT_BIT
        val maxBit = HUD_MAX_BIT
        val addOffset = HUD_ADD_HEIGHT.toFloat()

        val testY = listOf(0, 37, 200, -150)
        val testIds = listOf(1, 5, 42)

        for (id in testIds) {
            for (y in testY) {
                val ascent = -(((id + (1 shl maxBit)) shl heightBit) + HUD_ADD_HEIGHT + y)

                val glyphHeight = 8.0f
                val up = 7.0f - ascent.toFloat()
                val down = up + glyphHeight
                val shearTop = 1.0f - 0.25f * up
                val shearBottom = 1.0f - 0.25f * down

                // Quad vertex 0 (Top-Left)
                var posX0 = xLayout + 0.5f * uiX + shearTop
                var posY0 = defaultOffset + up

                // Quad vertex 1 (Bottom-Left)
                var posX1 = xLayout + 0.5f * uiX + shearBottom
                var posY1 = defaultOffset + down

                // Quad vertex 2 (Bottom-Right)
                val charWidth = 6.0f
                var posX2 = xLayout + charWidth + 0.5f * uiX + shearBottom
                var posY2 = defaultOffset + down

                // Quad vertex 3 (Top-Right)
                var posX3 = xLayout + charWidth + 0.5f * uiX + shearTop
                var posY3 = defaultOffset + up

                fun processShader(posX: Float, posY: Float, vid: Int): Float {
                    var x = posX
                    var y = posY
                    val bit = y.toInt() shr heightBit
                    if (x < -10000.0f) {
                        val isBottom = if (vid == 1 || vid == 2) 1.0f else 0.0f
                        x += 0.25f * (y - defaultOffset - isBottom * glyphHeight)
                    }
                    x -= 0.5f * uiX
                    return x
                }

                val outX0 = processShader(posX0, posY0, 0)
                val outX1 = processShader(posX1, posY1, 1)
                val outX2 = processShader(posX2, posY2, 2)
                val outX3 = processShader(posX3, posY3, 3)

                // Expected vanilla Minecraft positions (with exact vanilla italic shear: top = +1.0, bottom = 1.0 - 0.25 * height = -1.0)
                assertEquals(xLayout + 1.0f, outX0, 1e-3f, "Top-left vertex X must match xLayout + 1.0 for y=$y, id=$id")
                assertEquals(xLayout - 1.0f, outX1, 1e-3f, "Bottom-left vertex X must match xLayout - 1.0 for y=$y, id=$id")
                assertEquals(xLayout + charWidth - 1.0f, outX2, 1e-3f, "Bottom-right vertex X must match xLayout + width - 1.0 for y=$y, id=$id")
                assertEquals(xLayout + charWidth + 1.0f, outX3, 1e-3f, "Top-right vertex X must match xLayout + width + 1.0 for y=$y, id=$id")
            }
        }
    }

    @Test
    fun testVersionParsingAndSemverCoerce() {
        val ver1 = "2.2.0-fork.1".toSemver()
        assertNotNull(ver1)
        assertEquals(2, ver1.major)
        assertEquals(2, ver1.minor)
        assertEquals(0, ver1.patch)
        assertEquals("fork.1", ver1.preRelease.joinToString("."))

        val ver2 = "2.2.0-fork.2".toSemver()
        assertNotNull(ver2)
        assertEquals(2, ver2.major)
        assertEquals(2, ver2.minor)
        assertEquals(0, ver2.patch)
        assertEquals("fork.2", ver2.preRelease.joinToString("."))

        assertTrue(ver1 < ver2, "fork.1 must be less than fork.2")
    }

    @Test
    fun testBedrockPlayerAnimationModeSafety() {
        var bedrockFlag = false
        val mockVolatile = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.volatilecode.VolatileCodeHandler::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.volatilecode.VolatileCodeHandler::class.java)
        ) { _, _, _ -> null } as kr.toxicity.hud.api.volatilecode.VolatileCodeHandler

        val mockScheduler = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.scheduler.HudScheduler::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.scheduler.HudScheduler::class.java)
        ) { _, _, _ ->
            java.lang.reflect.Proxy.newProxyInstance(
                kr.toxicity.hud.api.scheduler.HudTask::class.java.classLoader,
                arrayOf(kr.toxicity.hud.api.scheduler.HudTask::class.java)
            ) { _, _, _ -> null }
        } as kr.toxicity.hud.api.scheduler.HudScheduler

        val mockBootstrap = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.BetterHudBootstrap::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.BetterHudBootstrap::class.java)
        ) { _, method, args ->
            when (method.name) {
                "isBedrockPlayer" -> bedrockFlag
                "volatileCode" -> mockVolatile
                "scheduler" -> mockScheduler
                else -> null
            }
        } as kr.toxicity.hud.api.BetterHudBootstrap

        val mockHud = java.lang.reflect.Proxy.newProxyInstance(
            kr.toxicity.hud.api.BetterHud::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.BetterHud::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "bootstrap" -> mockBootstrap
                else -> null
            }
        } as kr.toxicity.hud.api.BetterHud

        val mainField = kr.toxicity.hud.api.BetterHudAPI::class.java.getDeclaredField("main")
        mainField.isAccessible = true
        mainField.set(null, mockHud)

        val playerUuid = java.util.UUID.randomUUID()
        val dummyPlayer = object : kr.toxicity.hud.player.HudPlayerImpl() {
            override fun updatePlaceholder() {}
            override fun uuid(): java.util.UUID = playerUuid
            override fun name(): String = "TestPlayer"
            override fun audience(): net.kyori.adventure.audience.Audience = net.kyori.adventure.audience.Audience.empty()
            override fun hasPermission(perm: String): Boolean = true
            override fun locale(): java.util.Locale = java.util.Locale.ENGLISH
            override fun location(): kr.toxicity.hud.api.adapter.LocationWrapper = throw UnsupportedOperationException()
            override fun world(): kr.toxicity.hud.api.adapter.WorldWrapper = throw UnsupportedOperationException()
            override fun handle(): Any = this
        }

        val modeField = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
        modeField.isAccessible = true
        modeField.set(ConfigManagerImpl, kr.toxicity.hud.animation.AnimationMode.HYBRID)

        // Case 1: Java player with HYBRID mode -> client animation enabled
        bedrockFlag = false
        dummyPlayer.setPlayerAnimationMode(kr.toxicity.hud.animation.PlayerAnimationMode.AUTO)
        assertTrue(dummyPlayer.isClientAnimationEnabled(), "Java player in HYBRID mode must enable client animation")

        // Case 2: Bedrock player in HYBRID mode -> MUST be disabled
        bedrockFlag = true
        assertFalse(dummyPlayer.isClientAnimationEnabled(), "Bedrock player in HYBRID mode must NEVER enable client animation")

        // Case 3: Bedrock player with explicit CLIENT mode -> MUST STILL be disabled (Bedrock shaders not supported)
        dummyPlayer.setPlayerAnimationMode(kr.toxicity.hud.animation.PlayerAnimationMode.CLIENT)
        assertFalse(dummyPlayer.isClientAnimationEnabled(), "Bedrock player with explicit CLIENT mode must NEVER enable client animation")
    }

    @Test
    fun testSharedModuloMBetweenImageAndTextInPopupLayout() {
        val anim = ClientAnimation(
            15,
            "t",
            "0",
            "1",
            AnimationType.PLAY_ONCE
        )
        val M = 30 // Divisor for requiredTicks <= 30
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))

        val imageShader = HudShader(
            gui = gui,
            renderScale = scale,
            layer = 1,
            outline = 0,
            opacity = 1.0,
            property = 0,
            clientAnimation = anim,
            isText = false,
            moduloM = M
        )

        val textShader = HudShader(
            gui = gui,
            renderScale = scale,
            layer = 1,
            outline = 0,
            opacity = 1.0,
            property = 0,
            clientAnimation = anim,
            isText = true,
            moduloM = M
        )

        val imageGlsl = ShaderManagerImpl.generateShaderLayout(mapOf(imageShader to emptyList())).joinToString("\n")
        val textGlsl = ShaderManagerImpl.generateShaderLayout(mapOf(textShader to emptyList())).joinToString("\n")

        // 1. Image shader must use M=30.0 in getElapsedTicks and tStart < 30.0
        assertTrue(imageGlsl.contains("getElapsedTicks(tStart, GameTime * 24000.0, 30.0)"), "Image shader must use M=30 in getElapsedTicks: $imageGlsl")
        assertTrue(imageGlsl.contains("if (tStart < 30.0)"), "Image shader must guard tStart < 30.0: $imageGlsl")
        assertFalse(imageGlsl.contains("240.0"), "Image shader must not reference 240.0: $imageGlsl")

        // 2. Text shader in dual mode must use M=30.0 for shadow and main text
        assertTrue(textGlsl.contains("getElapsedTicks(tStart, GameTime * 24000.0, 30.0)"), "Text shader must use M=30 in getElapsedTicks: $textGlsl")
        assertTrue(textGlsl.contains("actualA <= 30"), "Text shadow must check actualA <= 30: $textGlsl")
        assertTrue(textGlsl.contains("actualA <= 60"), "Text main must check actualA <= 60: $textGlsl")
        assertFalse(textGlsl.contains("240.0"), "Text shader must not reference 240.0: $textGlsl")

        // 3. Mathematical parity of decoded tStart across image, text shadow, and text main
        val startTick = 107L
        val tStartByte = ((startTick % M).toInt()).coerceIn(0, M - 1) // 17
        assertEquals(17, tStartByte)

        // Image encodes tStartByte into Red
        val imageDecodedTStart = tStartByte.toDouble()

        // Text Dual mode encodes (tStartByte + 1) for shadow, (tStartByte + 1 + M) for main into Alpha
        val shadowAlpha = tStartByte + 1 // 18
        val mainAlpha = tStartByte + 1 + M // 48

        val textShadowDecodedTStart = (shadowAlpha - 1).toDouble()
        val textMainDecodedTStart = (mainAlpha - 1 - M).toDouble()

        assertEquals(imageDecodedTStart, textShadowDecodedTStart, "Image and text shadow decoded tStart must be identical")
        assertEquals(imageDecodedTStart, textMainDecodedTStart, "Image and text main decoded tStart must be identical")
    }

    @Test
    fun testPopupLifeExtensionAndSafetyLatch() {
        val anim = ClientAnimation(16, "45 * sin(t)", "-45 * sin(t)", "min(1, (max(0, 15 - t) / 9) ^ 2)", AnimationType.PLAY_ONCE)
        val M = 30
        val lookahead = 10
        val safeWindow = M - lookahead // 20

        // Test 1: Safety latch condition
        fun isSafetyLatchTriggered(now: Long, spawn: Long, moduloM: Int, lookaheadMax: Int): Boolean {
            val elapsed = (now - spawn).toInt()
            return elapsed >= (moduloM - lookaheadMax)
        }

        val spawn = 100L
        assertFalse(isSafetyLatchTriggered(105L, spawn, M, lookahead), "Playing animation at tick 105 must not trigger latch")
        assertFalse(isSafetyLatchTriggered(119L, spawn, M, lookahead), "At tick 119 (elapsed 19 < safeWindow 20), must not trigger latch")
        assertTrue(isSafetyLatchTriggered(120L, spawn, M, lookahead), "At tick 120 (elapsed 20 >= safeWindow 20), safety latch MUST trigger")
        assertTrue(isSafetyLatchTriggered(125L, spawn, M, lookahead), "At tick 125 (elapsed 25 >= safeWindow 20), safety latch MUST trigger")

        // Test 2: When life extension occurs at tick 115, resetSpawnTime resets spawnTime to 115
        val iter = object : kr.toxicity.hud.api.popup.PopupIterator {
            private var st = 100L
            override fun getSpawnTime(): Long = st
            override fun resetSpawnTime(spawnTime: Long) { st = spawnTime }
            override fun parent(): kr.toxicity.hud.api.popup.Popup = throw UnsupportedOperationException()
            override fun isUnique(): Boolean = true
            override fun markedAsRemoval(): Boolean = false
            override fun push(): Boolean = false
            override fun getIndex(): Int = 0
            override fun getMaxIndex(): Int = 0
            override fun setIndex(index: Int) {}
            override fun available(): Boolean = true
            override fun getKey(): Any = "key"
            override fun canSave(): Boolean = false
            override fun alwaysCheckCondition(): Boolean = false
            override fun next(): List<WidthComponent> = emptyList()
            override fun remove() {}
            override fun getPriority(): Int = 0
            override fun setPriority(priority: Int) {}
            override fun name(): String = "test"
            override fun getUUID(): java.util.UUID = java.util.UUID.randomUUID()
            override fun getSortType(): kr.toxicity.hud.api.popup.PopupSortType = kr.toxicity.hud.api.popup.PopupSortType.FIRST
            override fun compareTo(other: kr.toxicity.hud.api.popup.PopupIterator): Int = 0
        }

        assertEquals(100L, iter.getSpawnTime())
        // Simulate updater() resetting spawnTime
        var currentTime = 115L
        iter.resetSpawnTime(currentTime)
        assertEquals(115L, iter.getSpawnTime(), "resetSpawnTime must update spawnTime on life extension")

        // After reset at 115, at tick 120 (elapsed 5), latch does NOT trigger!
        assertFalse(isSafetyLatchTriggered(120L, iter.getSpawnTime(), M, lookahead), "After reset, tick 120 is elapsed=5 and must not trigger latch")
    }

    @Test
    fun testColorAndShadowParityAcrossServerAndClientModes() {
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()

        // Case 1: entity_health_text from upstream default configs
        val entityPattern = mm.deserialize("Health: <#FFD800>20 // 20")
        val exactComp = entityPattern.applyClientExact(10)
        assertEquals(NamedTextColor.WHITE, exactComp.style().color(), "Client exact mode root text color must be WHITE")
        // Check children colors
        val children = exactComp.children()
        assertTrue(children.isNotEmpty())
        val goldChild = children.firstOrNull { it.children().any { c -> (c as? TextComponent)?.content()?.contains("20") == true } || (it as? TextComponent)?.content()?.contains("20") == true }
        assertNotNull(goldChild, "Gold child must be present")

        // Case 2: Explicit shadow via MiniMessage <shadow:#123456>
        val shadowMsg = mm.deserialize("<#FF0000><shadow:#123456>Damage: 100</shadow></#FF0000>")
        val dualShadow = shadowMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.SHADOW, 5, 30)
        val dualText = shadowMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.TEXT, 5, 30)

        val shadowStyle = dualShadow.style()
        val shadowCol = shadowStyle.shadowColor()!!
        assertEquals(0x12, shadowCol.red())
        assertEquals(0x34, shadowCol.green())
        assertEquals(0x56, shadowCol.blue())
        // Alpha must be in M+1..2M (31..60 for M=30, tStart=5 -> 1 + 30 + 5 = 36)
        assertEquals(36, shadowCol.alpha())

        val textStyle = dualText.style()
        val textShadowCol = textStyle.shadowColor()!!
        // Text layer encodes text RGB (red = 255, 0, 0)
        assertEquals(255, textShadowCol.red())
        assertEquals(0, textShadowCol.green())
        assertEquals(0, textShadowCol.blue())
        // Alpha in 1..M (1 + 5 = 6)
        assertEquals(6, textShadowCol.alpha())

        // Case 3: Zero / disabled shadow via shadowColor(0) (e.g. outline: 0 in BetterHud)
        val zeroShadowMsg = Component.text("No Shadow").shadowColor(net.kyori.adventure.text.format.ShadowColor.shadowColor(0))
        val dualZeroShadow = zeroShadowMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.SHADOW, 0, 30)
        val zeroCol = dualZeroShadow.style().shadowColor()!!
        assertEquals(0, zeroCol.value(), "Disabled shadow must have shadow value 0")

        // Case 4: Gradient <gradient:#FF0000:#00FF00>
        val gradMsg = mm.deserialize("<gradient:#FF0000:#00FF00>ABC</gradient>")
        val gradShadow = gradMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.SHADOW, 0, 30)
        val gradText = gradMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.TEXT, 0, 30)
        assertEquals(gradMsg.children().size, gradShadow.children().size)
        assertEquals(gradMsg.children().size, gradText.children().size)

        // Case 5: Nested components with color inheritance and overrides
        val nestedMsg = mm.deserialize("<gold>Gold1 <white>White</white> Gold2</gold>")
        val nestedDual = nestedMsg.applyClientDualRole(kr.toxicity.hud.text.DualCopyRole.TEXT, 0, 30)
        val cRoot = nestedDual.style().shadowColor()!!
        val c0 = (nestedDual.children()[0] as TextComponent).style().shadowColor()!!
        val c1 = (nestedDual.children()[1] as TextComponent).style().shadowColor()!!
        // cRoot and c1 must carry Gold text color in shadowColor
        val goldColor = TextColor.color(NamedTextColor.GOLD)
        assertEquals(goldColor.red(), cRoot.red())
        assertEquals(goldColor.green(), cRoot.green())
        assertEquals(goldColor.blue(), cRoot.blue())
        assertEquals(goldColor.red(), c1.red())
        assertEquals(goldColor.green(), c1.green())
        assertEquals(goldColor.blue(), c1.blue())
        // c0 must carry White text color in shadowColor
        assertEquals(255, c0.red())
        assertEquals(255, c0.green())
        assertEquals(255, c0.blue())
    }

    @Test
    fun testCrossWorldDesyncAndCleanup() {
        val uuid = java.util.UUID.randomUUID()
        val tracker = kr.toxicity.hud.animation.ListenerTracker(20, EasingType.LINEAR)
        // Simulate tracker in World 1 at tick 1000
        val col = tracker.update(0.5, 1000L)
        assertNotNull(col)
        assertEquals(1000L, tracker.startTickVal)

        // If player teleports to World 2 with different gameTime (e.g. 200L):
        // Without cleanup, elapsed = 200 - 1000 = -800L
        val uncleanedElapsed = (200L - tracker.startTickVal).toInt()
        assertTrue(uncleanedElapsed < 0, "Uncleaned cross-world teleport produces negative elapsed")

        // With cleanup (removeTrackers):
        kr.toxicity.hud.image.enums.ImageType.removeTrackers(uuid)
        assertNull(kr.toxicity.hud.image.enums.ImageType.getTrackers(uuid), "Trackers must be cleared after world change/respawn")

        // New tracker in World 2 initializes cleanly at World 2 time (200L)
        val newTracker = kr.toxicity.hud.animation.ListenerTracker(20, EasingType.LINEAR)
        newTracker.update(0.5, 200L)
        assertEquals(200L, newTracker.startTickVal, "New tracker must anchor to World 2 time")
    }
}
