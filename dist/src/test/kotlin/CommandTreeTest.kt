import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.BetterHudLogger
import kr.toxicity.hud.api.volatilecode.VolatileCodeHandler
import kr.toxicity.hud.manager.CommandManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.lang.reflect.Proxy

class CommandTreeTest {

    @Test
    fun testCommandTreeBuildIntegrity() {
        // Initialize mock BetterHud environment for CommandManager
        val mockSource = Proxy.newProxyInstance(
            BetterCommandSource::class.java.classLoader,
            arrayOf(BetterCommandSource::class.java)
        ) { _, _, _ -> null } as BetterCommandSource

        val mockLogger = Proxy.newProxyInstance(
            BetterHudLogger::class.java.classLoader,
            arrayOf(BetterHudLogger::class.java)
        ) { _, _, _ -> null } as BetterHudLogger

        val mockVolatile = Proxy.newProxyInstance(
            VolatileCodeHandler::class.java.classLoader,
            arrayOf(VolatileCodeHandler::class.java)
        ) { _, _, _ -> null } as VolatileCodeHandler

        val tempDir = File("build/tmp/test-command-tree").apply { mkdirs() }

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "consoleSource" -> mockSource
                "logger" -> mockLogger
                "volatileCode" -> mockVolatile
                "dataFolder" -> tempDir
                "worlds" -> emptyList<Any>()
                else -> null
            }
        } as BetterHudBootstrap

        val mockHud = Proxy.newProxyInstance(
            BetterHud::class.java.classLoader,
            arrayOf(BetterHud::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "bootstrap" -> mockBootstrap
                "loadAssets" -> null
                else -> null
            }
        } as BetterHud

        val mainField = BetterHudAPI::class.java.getDeclaredField("main")
        mainField.isAccessible = true
        mainField.set(null, mockHud)

        // CommandModule.build() MUST build the full tree of Brigadier command nodes without exception.
        // It validates all method parameters, annotations (@Option, @Vararg, @Source), and serializers.
        assertDoesNotThrow("Command tree building must succeed without NotLastParameterException or any other command validation error") {
            CommandManager.module.build(java.util.function.Function<Any, BetterCommandSource?> { null })
        }
    }
}
