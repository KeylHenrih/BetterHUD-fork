package kr.toxicity.hud.test

import kr.toxicity.hud.animation.AnimationMode
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.BetterHudLogger
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.configuration.PluginConfiguration
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.util.toYaml
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Proxy

class DefaultsAndHybridTest {

    private val loggedWarnings = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        loggedWarnings.clear()
    }

    private fun setupMockBootstrap(tempDir: File, resourceProvider: (String) -> java.io.InputStream?) {
        val mockLogger = Proxy.newProxyInstance(
            BetterHudLogger::class.java.classLoader,
            arrayOf(BetterHudLogger::class.java)
        ) { _, method, args ->
            if (method.name == "warn" && args != null && args.isNotEmpty()) {
                val str = when (val first = args[0]) {
                    is Array<*> -> first.joinToString(" ")
                    else -> first.toString()
                }
                loggedWarnings.add(str)
            }
            null
        } as BetterHudLogger

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, args ->
            when (method.name) {
                "dataFolder" -> tempDir
                "logger" -> mockLogger
                "resource" -> resourceProvider(args[0] as String)
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
    }

    @Test
    fun testCleanInstallationCreatesServerModeAndSafeShaderDefaults() {
        val tempDir = File("build/tmp/test-clean-install").apply {
            deleteRecursively()
            mkdirs()
        }

        val commonResourceFile = File("common-resources/shader.yml").let {
            if (it.exists()) it else File("../common-resources/shader.yml")
        }

        setupMockBootstrap(tempDir) { name ->
            if (name == "shader.yml") commonResourceFile.inputStream() else null
        }

        // Clean installation
        val shaderYaml = PluginConfiguration.SHADER.create()

        assertEquals(
            "server",
            shaderYaml.get("animation-mode")?.asString(),
            "Default animation-mode for clean installations must be 'server'"
        )
        assertFalse(
            shaderYaml.get("delete-shaders-on-start")?.asBoolean() ?: true,
            "Default delete-shaders-on-start for clean installations must be false"
        )
    }

    @Test
    fun testExistingClientConfigurationNotOverwritten() {
        val tempDir = File("build/tmp/test-existing-install").apply {
            deleteRecursively()
            mkdirs()
        }

        // Existing shader.yml with client mode
        val existingFile = File(tempDir, "shader.yml")
        existingFile.writeText("animation-mode: client\ndelete-shaders-on-start: true\n")

        val commonResourceFile = File("common-resources/shader.yml").let {
            if (it.exists()) it else File("../common-resources/shader.yml")
        }

        setupMockBootstrap(tempDir) { name ->
            if (name == "shader.yml") commonResourceFile.inputStream() else null
        }

        val shaderYaml = PluginConfiguration.SHADER.create()

        assertEquals(
            "client",
            shaderYaml.get("animation-mode")?.asString(),
            "Existing animation-mode: client must NOT be overwritten on upgrade"
        )
        assertTrue(
            shaderYaml.get("delete-shaders-on-start")?.asBoolean() ?: false,
            "Existing delete-shaders-on-start: true must NOT be overwritten"
        )
    }

    @Test
    fun testKotlinDefaultsForDeleteShadersOnStartIsFalse() {
        val tempDir = File("build/tmp/test-code-defaults").apply {
            deleteRecursively()
            mkdirs()
        }

        // Config file without shader.yml
        File(tempDir, "config.yml").writeText("bar-color: yellow\n")

        setupMockBootstrap(tempDir) { null }

        ConfigManagerImpl.preReload()
        assertFalse(
            ConfigManagerImpl.deleteShadersOnStart,
            "Kotlin fallback default for delete-shaders-on-start must be false"
        )
    }

    @Test
    fun testHybridModeParsesAndLogsWarning() {
        val tempDir = File("build/tmp/test-hybrid-mode").apply {
            deleteRecursively()
            mkdirs()
        }

        File(tempDir, "shader.yml").writeText("animation-mode: hybrid\n")
        File(tempDir, "config.yml").writeText("bar-color: yellow\n")

        val commonResourceFile = File("common-resources/shader.yml").let {
            if (it.exists()) it else File("../common-resources/shader.yml")
        }

        setupMockBootstrap(tempDir) { name ->
            if (name == "shader.yml") commonResourceFile.inputStream()
            else if (name == "config.yml") File(tempDir, "config.yml").inputStream()
            else null
        }

        ConfigManagerImpl.start()

        assertEquals(AnimationMode.HYBRID, ConfigManagerImpl.animationMode)
        assertTrue(
            loggedWarnings.any { it.contains("hybrid") && it.contains("client") },
            "Using hybrid mode must log an explicit notice that hybrid operates as client. Got warnings: $loggedWarnings"
        )
    }
}
