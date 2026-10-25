import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.animation.ClientAnimation
import kr.toxicity.hud.animation.EasingType
import kr.toxicity.hud.animation.ListenerAnimation
import kr.toxicity.hud.animation.SequenceAnimation
import kr.toxicity.hud.image.enums.SplitType
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.pack.PackOverlay
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.shader.RenderScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class ShaderLintTest {

    private val incDir = File("src/test/resources/shaders/include").absoluteFile

    private fun isGlslangAvailable(): Boolean {
        return try {
            val p = ProcessBuilder("glslangValidator", "--version").start()
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getIncludes(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (incDir.exists()) {
            incDir.listFiles()?.forEach { file ->
                map[file.name] = file.readText()
            }
        }
        return map
    }

    private fun lintShader(rawCode: String, overlay: PackOverlay, stage: String = "vert") {
        val glslangAvailable = isGlslangAvailable()
        val isCi = System.getenv("CI")?.toBoolean() == true || System.getenv("REQUIRE_GLSLANG")?.toBoolean() == true
        if (isCi) {
            assertTrue(glslangAvailable, "glslangValidator is required in CI (CI=true or REQUIRE_GLSLANG=true) but was not found. Please install glslang-tools.")
        } else {
            assumeTrue(glslangAvailable, "glslangValidator is required for shader linting. Skipping test because glslangValidator is not installed.")
        }

        var processed = rawCode
        val includes = getIncludes()

        // 1.21.4 legacy fog_distance and linear_fog stub
        val legacyFog = """
            float fog_distance(vec3 pos, int shape) {
                if (shape == 0) return length(pos);
                else return max(length(pos.xz), abs(pos.y));
            }
            vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
                if (vertexDistance <= fogStart) return inColor;
                float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
                return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
            }
        """.trimIndent()

        if (overlay == PackOverlay.V1_21_4) {
            processed = processed.replace("#moj_import <fog.glsl>", legacyFog)
        }

        var changed = true
        var loops = 0
        while (changed && loops < 5) {
            val prev = processed
            includes.forEach { (name, code) ->
                processed = processed.replace("#moj_import <minecraft:$name>", code)
                processed = processed.replace("#moj_import <$name>", code)
                processed = processed.replace("#include <minecraft:$name>", code)
                processed = processed.replace("#include <$name>", code)
            }
            changed = (prev != processed)
            loops++
        }

        val suffix = if (stage == "vert") ".vert" else ".frag"
        val tempFile = File.createTempFile("shader_lint_${overlay.overlayName}_", suffix)
        try {
            tempFile.writeText(processed)
            val process = ProcessBuilder("glslangValidator", "-S", stage, tempFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            assertEquals(0, exitCode, "glslangValidator compilation failed for overlay ${overlay.overlayName} ($stage):\n$output\n\nSource:\n$processed")
        } finally {
            tempFile.delete()
        }
    }

    private fun buildTestShader(overlay: PackOverlay, layoutLines: List<String>): String {
        val templateFile = File("../common-resources/shaders/${overlay.overlayName}/text.vsh").let {
            if (it.exists()) it else File("common-resources/shaders/${overlay.overlayName}/text.vsh")
        }
        assertTrue(templateFile.exists(), "Shader template not found: ${templateFile.absolutePath}")
        var content = templateFile.readText()

        val constants = """
            #define HEIGHT_BIT 13
            #define MAX_BIT 10
            #define ADD_OFFSET 4095
            #define DEFAULT_OFFSET 10
        """.trimIndent()

        val definedMethods = ShaderManagerImpl.tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(kr.toxicity.hud.api.manager.ShaderManager.ShaderType.TEXT_VERTEX).joinToString("\n")
        val startMethods = ShaderManagerImpl.tagBuilders["GenerateOtherStartMethod"]!!.invoke(kr.toxicity.hud.api.manager.ShaderManager.ShaderType.TEXT_VERTEX).joinToString("\n")

        content = content.replace("#CreateConstant", constants)
        content = content.replace("#CreateLayout", layoutLines.joinToString("\n"))
        content = content.replace("#GenerateOtherDefinedMethod", definedMethods)
        content = content.replace("#GenerateOtherStartMethod", startMethods)
        content = content.replace("#GenerateOtherMainMethod", "")

        return content
    }

    @Test
    fun testShaderLintAllOverlaysServerMode() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val staticShader = HudShader(gui, scale, 1, 0, 1.0, 0, null, null)

        val layout = ShaderManagerImpl.generateShaderLayout(mapOf(staticShader to emptyList()))

        PackOverlay.entries.forEach { overlay ->
            val vsh = buildTestShader(overlay, layout)
            lintShader(vsh, overlay)
        }
    }

    @Test
    fun testShaderLintAllOverlaysClientMode() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))

        val seqAnim = SequenceAnimation(0, 4)
        val loopAnim = ClientAnimation(20, "10.0 * sin(t)", "10.0 * cos(t)", "1.0", AnimationType.LOOP)
        val playOnceAnim = ClientAnimation(70, "230.0 * 0.5 * (1.0 + cos(3.14159 * t / 12.0))", "0.0", "clamp(t / 8.0, 0.0, 1.0)", AnimationType.PLAY_ONCE)
        val listenerAnim = ListenerAnimation(SplitType.RIGHT, 10, EasingType.EASE_OUT_CUBIC)

        val shaders = mapOf(
            HudShader(gui, scale, 1, 0, 1.0, 0, loopAnim, null, null) to emptyList<(Int) -> Unit>(),
            HudShader(gui, scale, 2, 0, 1.0, 0, null, listenerAnim, null) to emptyList(),
            HudShader(gui, scale, 3, 0, 1.0, 0, playOnceAnim, null, null) to emptyList(),
            HudShader(gui, scale, 4, 0, 1.0, 0, playOnceAnim, listenerAnim, null) to emptyList(),
            HudShader(gui, scale, 5, 0, 1.0, 0, null, null, seqAnim) to emptyList()
        )

        val layout = ShaderManagerImpl.generateShaderLayout(shaders)

        PackOverlay.entries.forEach { overlay ->
            val vsh = buildTestShader(overlay, layout)
            lintShader(vsh, overlay)
        }
    }

    @Test
    fun testShaderLintAllOverlaysHybridMode() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))

        val staticShader = HudShader(gui, scale, 1, 0, 1.0, 0, null, null)
        val loopAnim = ClientAnimation(20, "5.0 * sin(t)", "0.0", "1.0", AnimationType.LOOP)
        val listenerAnim = ListenerAnimation(SplitType.LEFT, 15, EasingType.EASE_OUT_QUAD)

        val shaders = mapOf(
            staticShader to emptyList<(Int) -> Unit>(),
            HudShader(gui, scale, 2, 0, 1.0, 0, loopAnim, null, null) to emptyList(),
            HudShader(gui, scale, 3, 0, 1.0, 0, null, listenerAnim, null) to emptyList()
        )

        val layout = ShaderManagerImpl.generateShaderLayout(shaders)

        PackOverlay.entries.forEach { overlay ->
            val vsh = buildTestShader(overlay, layout)
            lintShader(vsh, overlay)
        }
    }

    @Test
    fun testShaderLintAllOverlaysFragment() {
        PackOverlay.entries.forEach { overlay ->
            val templateFile = File("../common-resources/shaders/${overlay.overlayName}/text.fsh").let {
                if (it.exists()) it else File("common-resources/shaders/${overlay.overlayName}/text.fsh")
            }
            assertTrue(templateFile.exists(), "Fragment shader template not found: ${templateFile.absolutePath}")
            var content = templateFile.readText()

            val definedMethods = ShaderManagerImpl.tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(kr.toxicity.hud.api.manager.ShaderManager.ShaderType.TEXT_FRAGMENT).joinToString("\n")
            val mainMethods = ShaderManagerImpl.tagBuilders["GenerateOtherMainMethod"]!!.invoke(kr.toxicity.hud.api.manager.ShaderManager.ShaderType.TEXT_FRAGMENT).joinToString("\n")

            content = content.replace("#CreateConstant", "")
            content = content.replace("#GenerateOtherDefinedMethod", definedMethods)
            content = content.replace("#GenerateOtherMainMethod", mainMethods)

            lintShader(content, overlay, stage = "frag")
        }
    }
}
