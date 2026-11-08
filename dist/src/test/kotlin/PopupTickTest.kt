package kr.toxicity.hud.test

import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.animation.AnimationElementRegistry
import kr.toxicity.hud.animation.AnimationMode
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.BetterHudLogger
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.element.ImageElement
import kr.toxicity.hud.image.enums.ImageType
import kr.toxicity.hud.layout.LayoutGroup
import kr.toxicity.hud.manager.ImageManager
import kr.toxicity.hud.manager.LayoutManager
import kr.toxicity.hud.popup.PopupImpl
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.yaml.YamlObjectImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Proxy

class PopupTickTest {

    private val capturedWarnings = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        AnimationElementRegistry.clear()
        capturedWarnings.clear()
    }

    private fun createMockSource(): BetterCommandSource {
        return Proxy.newProxyInstance(
            BetterCommandSource::class.java.classLoader,
            arrayOf(BetterCommandSource::class.java)
        ) { _, _, _ -> null } as BetterCommandSource
    }

    @Test
    fun testPopupWithTickGreaterThanOneForcesServerModeAndLogsWarning() {
        val tempDir = File("build/tmp/test-popup-tick").apply {
            deleteRecursively()
            mkdirs()
        }

        val mockLogger = Proxy.newProxyInstance(
            BetterHudLogger::class.java.classLoader,
            arrayOf(BetterHudLogger::class.java)
        ) { _, method, args ->
            if (method.name == "warn" && args != null && args.isNotEmpty()) {
                val str = when (val first = args[0]) {
                    is Array<*> -> first.joinToString(" ")
                    else -> first.toString()
                }
                capturedWarnings.add(str)
            }
            null
        } as BetterHudLogger

        val mockSource = createMockSource()

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, args ->
            when (method.name) {
                "dataFolder" -> tempDir
                "logger" -> mockLogger
                "consoleSource" -> mockSource
                "version" -> "2.2.0-fork.5"
                else -> null
            }
        } as BetterHudBootstrap

        val mockHud = Proxy.newProxyInstance(
            BetterHud::class.java.classLoader,
            arrayOf(BetterHud::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "bootstrap" -> mockBootstrap
                else -> null
            }
        } as BetterHud

        val mainField = BetterHudAPI::class.java.getDeclaredField("main")
        mainField.isAccessible = true
        mainField.set(null, mockHud)

        // Register dummy image
        val bi = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val loadedImage = kr.toxicity.hud.image.LoadedImage(bi, 0, 0)
        val namedImage = kr.toxicity.hud.image.NamedLoadedImage("test.png", loadedImage)
        val dummyImage = ImageElement(
            "test_img", listOf(namedImage), ImageType.SINGLE, YamlObjectImpl("img", mutableMapOf<String, Any>())
        )
        val imageMapField = ImageManager::class.java.getDeclaredField("imageMap")
        imageMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (imageMapField.get(ImageManager) as MutableMap<String, ImageElement>)["test_img"] = dummyImage

        // Register client animated layout
        val layoutYaml = YamlObjectImpl("test_layout", mutableMapOf(
            "images" to mutableMapOf("bg" to mutableMapOf("name" to "test_img")),
            "animations" to mutableMapOf(
                "mode" to "client",
                "type" to "play_once",
                "duration" to 40,
                "x-equation" to "10",
                "y-equation" to "0",
                "opacity-equation" to "1"
            )
        ))
        val layoutGroup = LayoutGroup("test_layout", mockSource, layoutYaml)
        val layoutMapField = LayoutManager::class.java.getDeclaredField("layoutMap")
        layoutMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (layoutMapField.get(LayoutManager) as MutableMap<String, LayoutGroup>)["test_layout"] = layoutGroup

        // Create popup with tick: 2
        val reloadInfo = ReloadInfo(mockSource, emptySet())
        val globalResource = GlobalResource(reloadInfo)
        val popupYaml = YamlObjectImpl("test_tick_popup", mutableMapOf(
            "duration" to 40,
            "tick" to 2L,
            "layouts" to mutableMapOf(
                "layout1" to mutableMapOf("name" to "test_layout")
            )
        ))

        val popup = PopupImpl("test_tick_popup", globalResource, popupYaml)

        // Verification:
        // 1. Element registry must register SERVER mode because tick != 1
        val entry = AnimationElementRegistry.getAll()["popup_test_tick_popup/test_layout/animation"]
        assertNotNull(entry, "Layout must be registered in AnimationElementRegistry")
        assertEquals(AnimationMode.SERVER, entry?.mode,
            "Popup with tick=2 must fallback to SERVER animation mode!")

        // 2. Warning must be emitted
        assertTrue(
            capturedWarnings.any { it.contains("test_tick_popup") && it.contains("tick=2 != 1") && it.contains("forcing SERVER mode") },
            "Warning must be logged for popup with tick=2 != 1, but got: $capturedWarnings"
        )
    }
}
