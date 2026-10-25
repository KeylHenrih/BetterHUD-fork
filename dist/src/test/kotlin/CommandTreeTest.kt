import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.BetterHudLogger
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.api.popup.PopupUpdater
import kr.toxicity.hud.api.scheduler.HudScheduler
import kr.toxicity.hud.api.scheduler.HudTask
import kr.toxicity.hud.api.volatilecode.VolatileCodeHandler
import kr.toxicity.hud.manager.CommandManager
import kr.toxicity.hud.resource.GlobalResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

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

    @Test
    fun testHeldPopupsLifecycleAndCleanupOnReloadAndEnd() {
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

        var taskCancelled = false
        val mockTask = Proxy.newProxyInstance(
            HudTask::class.java.classLoader,
            arrayOf(HudTask::class.java)
        ) { _, method, _ ->
            if (method.name == "cancel") taskCancelled = true
            null
        } as HudTask

        val mockScheduler = Proxy.newProxyInstance(
            HudScheduler::class.java.classLoader,
            arrayOf(HudScheduler::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "asyncTaskTimer" -> mockTask
                else -> null
            }
        } as HudScheduler

        val tempDir = File("build/tmp/test-held-popups").apply { mkdirs() }

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "consoleSource" -> mockSource
                "logger" -> mockLogger
                "volatileCode" -> mockVolatile
                "scheduler" -> mockScheduler
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
                else -> null
            }
        } as BetterHud

        val mainField = BetterHudAPI::class.java.getDeclaredField("main")
        mainField.isAccessible = true
        mainField.set(null, mockHud)

        CommandManager.start()

        // Inject a held popup
        val heldField = CommandManager::class.java.getDeclaredField("heldPopups")
        heldField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = heldField.get(CommandManager) as java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.ConcurrentHashMap<String, PopupUpdater>>

        var removed = false
        val dummyUpdater = object : PopupUpdater {
            override fun update(): Boolean = true
            override fun getIndex(): Int = 0
            override fun setIndex(index: Int) {}
            override fun remove() { removed = true }
        }
        val uuid = UUID.randomUUID()
        map.computeIfAbsent(uuid) { java.util.concurrent.ConcurrentHashMap() }["test"] = dummyUpdater

        assertEquals(1, map.size)

        // Test 1: Reload must clear heldPopups and remove updaters!
        val reloadInfo = ReloadInfo(mockSource, emptySet())
        val globalResource = GlobalResource(reloadInfo)
        CommandManager.reload(tempDir, reloadInfo, globalResource)

        assertTrue(removed, "PopupUpdater.remove() must be called on reload")
        assertTrue(map.isEmpty(), "heldPopups map must be empty after reload")

        // Test 2: Add another updater, then test CommandManager.end()
        var removedOnEnd = false
        val dummyUpdater2 = object : PopupUpdater {
            override fun update(): Boolean = true
            override fun getIndex(): Int = 0
            override fun setIndex(index: Int) {}
            override fun remove() { removedOnEnd = true }
        }
        map.computeIfAbsent(uuid) { java.util.concurrent.ConcurrentHashMap() }["test2"] = dummyUpdater2

        CommandManager.end()
        assertTrue(removedOnEnd, "PopupUpdater.remove() must be called on end")
        assertTrue(map.isEmpty(), "heldPopups must be empty on end")
        assertTrue(taskCancelled, "heldPopups asyncTaskTimer must be cancelled on end")

        // Test 3: Player quit cleanup
        var removedOnQuit = false
        val dummyUpdater3 = object : PopupUpdater {
            override fun update(): Boolean = true
            override fun getIndex(): Int = 0
            override fun setIndex(index: Int) {}
            override fun remove() { removedOnQuit = true }
        }
        val quitUuid = UUID.randomUUID()
        map.computeIfAbsent(quitUuid) { java.util.concurrent.ConcurrentHashMap() }["test3"] = dummyUpdater3
        CommandManager.clearHeldPopups(quitUuid)
        assertTrue(removedOnQuit, "PopupUpdater.remove() must be called on player clear")
        assertTrue(map[quitUuid] == null || map[quitUuid]!!.isEmpty(), "Player popups must be cleared")
    }
}
