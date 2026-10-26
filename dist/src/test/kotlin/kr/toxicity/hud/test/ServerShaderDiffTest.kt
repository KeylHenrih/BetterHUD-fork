package kr.toxicity.hud.test

import kr.toxicity.hud.pack.PackOverlay
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ServerShaderDiffTest {

    @Test
    fun testServerShaderDiffAcrossOverlays() {
        val rootDir = File("common-resources/shaders").takeIf { it.exists() } ?: File("../common-resources/shaders")
        val overlays = listOf("betterhud_1_21_4", "betterhud_1_21_6", "betterhud_26_1", "betterhud_26_2", "betterhud_26_3")

        for (overlay in overlays) {
            val vshFile = File(rootDir, "$overlay/text.vsh")
            assertTrue(vshFile.exists(), "text.vsh must exist for overlay $overlay")

            val content = vshFile.readText()
            // In server mode, #CreateLayout contains only server layouts (or empty / default)
            // The template uses explicit float literals:
            assertTrue(content.contains("float xGui = 0.0;"), "Overlay $overlay must use float literal 0.0")
            assertTrue(content.contains("float yGui = 0.0;"), "Overlay $overlay must use float literal 0.0")
            assertTrue(content.contains("float layer = 0.0;"), "Overlay $overlay must use float literal 0.0")
            assertTrue(content.contains("float opacity = 1.0;"), "Overlay $overlay must use float literal 1.0")
            assertTrue(content.contains("vertexColor *= vec4(1.0, 1.0, 1.0, opacity);"), "Overlay $overlay must use float vec4")
        }
    }
}
