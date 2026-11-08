import kr.toxicity.hud.api.manager.ShaderManager.ShaderType
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.ShaderManagerImpl
import kr.toxicity.hud.pack.PackOverlay
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.shader.RenderScale
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

class BaselineInsuranceTest {

    private val baselineDir = (if (File("tools").isDirectory) File("tools/baseline") else File("../tools/baseline")).apply {
        mkdirs()
    }

    private fun getOverlayTemplate(overlay: PackOverlay, fileName: String): File {
        return File("../common-resources/shaders/${overlay.overlayName}/$fileName").let {
            if (it.exists()) it else File("common-resources/shaders/${overlay.overlayName}/$fileName")
        }
    }

    private fun buildServerVsh(overlay: PackOverlay, layoutLines: List<String>): String {
        val templateFile = getOverlayTemplate(overlay, "text.vsh")
        assertTrue(templateFile.exists(), "Shader template not found: ${templateFile.absolutePath}")
        var content = templateFile.readText()

        val constants = """
            #define HEIGHT_BIT 13
            #define MAX_BIT 10
            #define ADD_OFFSET 4095
            #define DEFAULT_OFFSET 10
        """.trimIndent()

        val definedMethods = ShaderManagerImpl.tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(ShaderType.TEXT_VERTEX).joinToString("\n")
        val startMethods = ShaderManagerImpl.tagBuilders["GenerateOtherStartMethod"]!!.invoke(ShaderType.TEXT_VERTEX).joinToString("\n")

        content = content.replace("#CreateConstant", constants)
        content = content.replace("#CreateLayout", layoutLines.joinToString("\n"))
        content = content.replace("#GenerateOtherDefinedMethod", definedMethods)
        content = content.replace("#GenerateOtherStartMethod", startMethods)
        content = content.replace("#GenerateOtherMainMethod", "")

        return content
    }

    private fun buildServerFsh(overlay: PackOverlay): String {
        val templateFile = getOverlayTemplate(overlay, "text.fsh")
        assertTrue(templateFile.exists(), "Fragment template not found: ${templateFile.absolutePath}")
        var content = templateFile.readText()

        val definedMethods = ShaderManagerImpl.tagBuilders["GenerateOtherDefinedMethod"]!!.invoke(ShaderType.TEXT_FRAGMENT).joinToString("\n")
        val mainMethods = ShaderManagerImpl.tagBuilders["GenerateOtherMainMethod"]!!.invoke(ShaderType.TEXT_FRAGMENT).joinToString("\n")

        content = content.replace("#CreateConstant", "")
        content = content.replace("#GenerateOtherDefinedMethod", definedMethods)
        content = content.replace("#GenerateOtherMainMethod", mainMethods)

        return content
    }

    @Test
    fun testServerShadersBaseline() {
        val gui = GuiLocation(0.0, 0.0)
        val scale = RenderScale(PixelLocation.zero, RenderScale.Scale(1.0, 1.0, false))
        val staticShader = HudShader(gui, scale, 1, 0, 1.0, 0, null, null)
        val serverLayout = ShaderManagerImpl.generateShaderLayout(mapOf(staticShader to emptyList()))

        val shadersDir = File(baselineDir, "shaders")
        shadersDir.mkdirs()

        for (overlay in PackOverlay.entries) {
            val vsh = buildServerVsh(overlay, serverLayout)
            val fsh = buildServerFsh(overlay)

            val vshFile = File(shadersDir, "${overlay.overlayName}_text.vsh")
            val fshFile = File(shadersDir, "${overlay.overlayName}_text.fsh")

            if (!vshFile.exists()) {
                vshFile.writeText(vsh)
            } else {
                assertEquals(vshFile.readText(), vsh, "Server vertex shader for ${overlay.overlayName} changed against baseline!")
            }

            if (!fshFile.exists()) {
                fshFile.writeText(fsh)
            } else {
                assertEquals(fshFile.readText(), fsh, "Server fragment shader for ${overlay.overlayName} changed against baseline!")
            }
        }
    }

    @Test
    fun testServerComponentsBaseline() {
        val componentsDir = File(baselineDir, "components")
        componentsDir.mkdirs()

        // 1. Text Component serialization in server mode (clean text without dual client wrapping)
        val sampleText = net.kyori.adventure.text.Component.text("Revake Health: 100/100")
            .color(net.kyori.adventure.text.format.NamedTextColor.RED)
        val sampleWidthComp = kr.toxicity.hud.api.component.WidthComponent(sampleText.toBuilder(), 120)

        val gson = GsonComponentSerializer.gson()
        val textJson = gson.serialize(sampleWidthComp.component.build())

        val textJsonFile = File(componentsDir, "sample_server_text.json")
        if (!textJsonFile.exists()) {
            textJsonFile.writeText(textJson)
        } else {
            assertEquals(textJsonFile.readText(), textJson, "Sample server text component JSON changed against baseline!")
        }

        // 2. Server Image Component serialization
        val imgText = net.kyori.adventure.text.Component.text("󎀂󎀁")
            .font(net.kyori.adventure.key.Key.key("betterhud", "image_default-image"))
        val imgWidthComp = kr.toxicity.hud.api.component.WidthComponent(imgText.toBuilder(), 18)
        val imgJson = gson.serialize(imgWidthComp.component.build())

        val imgJsonFile = File(componentsDir, "sample_server_image.json")
        if (!imgJsonFile.exists()) {
            imgJsonFile.writeText(imgJson)
        } else {
            assertEquals(imgJsonFile.readText(), imgJson, "Sample server image component JSON changed against baseline!")
        }
    }
}
