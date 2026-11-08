package kr.toxicity.hud.test

import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.manager.CommandManager
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.player.HudPlayerImpl
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID

class CleanupAndCompletenessTest {

    private val receivedMessages = mutableListOf<String>()

    private val recordingAudience = object : Audience {
        override fun sendMessage(message: Component) {
            val json = GsonComponentSerializer.gson().serialize(message)
            receivedMessages.add(json)
        }
    }

    private inner class TestCommandPlayer(var admin: Boolean = true) : HudPlayerImpl(), BetterCommandSource {
        override fun uuid(): UUID = UUID.randomUUID()
        override fun name(): String = "TestAdmin"
        override fun ping(): Int = 20
        override fun worldTime(): Long = 1000L
        override fun audience(): Audience = recordingAudience
        override fun hasPermission(perm: String): Boolean = if (perm == "betterhud.admin") admin else false
        override fun locale(): Locale = Locale.ENGLISH
        override fun location(): LocationWrapper = throw UnsupportedOperationException()
        override fun world(): WorldWrapper = throw UnsupportedOperationException()
        override fun handle(): Any = this
        override fun updatePlaceholder() {}
        override fun reload() {}
    }

    @BeforeEach
    fun setup() {
        receivedMessages.clear()

        val mockScheduler = Proxy.newProxyInstance(
            kr.toxicity.hud.api.scheduler.HudScheduler::class.java.classLoader,
            arrayOf(kr.toxicity.hud.api.scheduler.HudScheduler::class.java)
        ) { _, _, _ ->
            Proxy.newProxyInstance(
                kr.toxicity.hud.api.scheduler.HudTask::class.java.classLoader,
                arrayOf(kr.toxicity.hud.api.scheduler.HudTask::class.java)
            ) { _, _, _ -> null }
        } as kr.toxicity.hud.api.scheduler.HudScheduler

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "isBedrockPlayer" -> false
                "scheduler" -> mockScheduler
                "dataFolder" -> File("build/tmp/test-cleanup").apply { mkdirs() }
                "logger" -> Proxy.newProxyInstance(
                    kr.toxicity.hud.api.BetterHudLogger::class.java.classLoader,
                    arrayOf(kr.toxicity.hud.api.BetterHudLogger::class.java)
                ) { _, _, _ -> null }
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

    private fun findCommandExecutor(commandName: String): Pair<Any, Method>? {
        val argsField = CommandManager.module.javaClass.getDeclaredField("arguments").apply { isAccessible = true }
        val argsList = argsField.get(CommandManager.module) as List<*>
        val executor = argsList.firstOrNull { arg ->
            val nameMethod = arg!!.javaClass.getMethod("name").apply { isAccessible = true }
            val aliasesMethod = arg.javaClass.getMethod("aliases").apply { isAccessible = true }
            val name = nameMethod.invoke(arg) as String
            val aliases = aliasesMethod.invoke(arg) as Array<*>
            name == commandName || aliases.contains(commandName)
        } ?: return null

        val objField = executor.javaClass.getDeclaredField("obj").apply { isAccessible = true }
        val listener = objField.get(executor)
        val methodField = executor.javaClass.getDeclaredField("method").apply { isAccessible = true }
        val method = methodField.get(executor) as Method
        method.isAccessible = true
        return listener to method
    }

    @Test
    fun testNewMathFunctionsTranspilation() {
        val expressions = mapOf(
            "cot(t)" to listOf("tan(t)", "1.0"),
            "sec(t)" to listOf("cos(t)", "1.0"),
            "csc(t)" to listOf("sin(t)", "1.0"),
            "log1p(t)" to listOf("log(1.0 + (t))"),
            "expm1(t)" to listOf("exp(t)", "1.0"),
            "toradian(t)" to listOf("radians(t)"),
            "todegree(t)" to listOf("degrees(t)"),
            "round(t)" to listOf("round(t)")
        )

        for ((expr, expectedSubstrings) in expressions) {
            val transpiled = GlslTranspiler.transpile(expr)
            for (sub in expectedSubstrings) {
                assertTrue(transpiled.contains(sub), "Transpiled '$expr' -> '$transpiled' must contain '$sub'")
            }
        }
    }

    @Test
    fun testDebugCommandsRegisteredAndGated() {
        val debugCommands = listOf("debug-anim", "debug-popup", "debug-color", "debug-italic")

        for (cmd in debugCommands) {
            val pair = findCommandExecutor(cmd)
            assertNotNull(pair, "Command executor for '$cmd' must be registered")
        }

        // Test permission gate and debug: false gate
        val debugField = ConfigManagerImpl::class.java.getDeclaredField("debug").apply { isAccessible = true }

        val nonAdminPlayer = TestCommandPlayer(admin = false)
        val adminPlayer = TestCommandPlayer(admin = true)

        // 1. debugColor without admin permission
        val (colorObj, colorMethod) = findCommandExecutor("debug-color")!!
        receivedMessages.clear()
        colorMethod.invoke(colorObj, nonAdminPlayer, "#FF0000")
        assertTrue(receivedMessages.any { it.contains("betterhud.admin") },
            "debugColor must reject execution for non-admin player")

        // 2. debugColor with admin, but debug: false
        debugField.set(ConfigManagerImpl, false)
        receivedMessages.clear()
        colorMethod.invoke(colorObj, adminPlayer, "#FF0000")
        assertTrue(receivedMessages.any { it.contains("Debug mode is disabled in config.yml") },
            "debugColor must reject execution when debug: false")

        // 3. debugColor with admin and debug: true
        debugField.set(ConfigManagerImpl, true)
        receivedMessages.clear()
        colorMethod.invoke(colorObj, adminPlayer, "#FF0000")
        assertTrue(receivedMessages.any { it.contains("Exact Color Test") },
            "debugColor must allow execution when admin=true and debug=true")

        // 4. debugItalic without admin permission
        val (italicObj, italicMethod) = findCommandExecutor("debug-italic")!!
        receivedMessages.clear()
        italicMethod.invoke(italicObj, nonAdminPlayer)
        assertTrue(receivedMessages.any { it.contains("betterhud.admin") },
            "debugItalic must reject execution for non-admin player")

        // 5. debugItalic with admin, but debug: false
        debugField.set(ConfigManagerImpl, false)
        receivedMessages.clear()
        italicMethod.invoke(italicObj, adminPlayer)
        assertTrue(receivedMessages.any { it.contains("Debug mode is disabled in config.yml") },
            "debugItalic must reject execution when debug: false")

        // 6. debugAnim without admin permission
        val (animObj, animMethod) = findCommandExecutor("debug-anim")!!
        receivedMessages.clear()
        animMethod.invoke(animObj, nonAdminPlayer, null)
        assertTrue(receivedMessages.any { it.contains("betterhud.admin") },
            "debugAnim must reject execution for non-admin player")

        // 7. debugAnim with admin, but debug: false
        debugField.set(ConfigManagerImpl, false)
        receivedMessages.clear()
        animMethod.invoke(animObj, adminPlayer, null)
        assertTrue(receivedMessages.any { it.contains("Debug mode is disabled in config.yml") },
            "debugAnim must reject execution when debug: false")
    }
}
