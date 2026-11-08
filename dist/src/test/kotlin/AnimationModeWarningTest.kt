package kr.toxicity.hud.test

import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.animation.PlayerAnimationMode
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.player.HudPlayerImpl
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID

class AnimationModeWarningTest {

    private val receivedMessages = mutableListOf<String>()

    private val recordingAudience = object : Audience {
        override fun sendMessage(message: Component) {
            val json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(message)
            receivedMessages.add(json)
        }
    }

    private inner class MockPlayer : HudPlayerImpl(), BetterCommandSource {
        override fun uuid(): UUID = UUID.randomUUID()
        override fun name(): String = "TestPlayer"
        override fun ping(): Int = 20
        override fun worldTime(): Long = 1000L
        override fun audience(): Audience = recordingAudience
        override fun hasPermission(perm: String): Boolean = true
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

        val modeField = ConfigManagerImpl::class.java.getDeclaredField("animationMode")
        modeField.isAccessible = true
        modeField.set(ConfigManagerImpl, kr.toxicity.hud.animation.AnimationMode.CLIENT)

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
                "dataFolder" -> java.io.File("build/tmp/test-anim-mode").apply { mkdirs() }
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

    private fun invokeAnimationMode(source: BetterCommandSource, target: String, mode: String?) {
        val argsField = kr.toxicity.hud.manager.CommandManager.module.javaClass.getDeclaredField("arguments").apply { isAccessible = true }
        val argsList = argsField.get(kr.toxicity.hud.manager.CommandManager.module) as List<*>
        val executor = argsList.first { arg ->
            val nameMethod = arg!!.javaClass.getMethod("name").apply { isAccessible = true }
            val aliasesMethod = arg.javaClass.getMethod("aliases").apply { isAccessible = true }
            val name = nameMethod.invoke(arg) as String
            val aliases = aliasesMethod.invoke(arg) as Array<*>
            name == "animationMode" || name == "animation-mode" || aliases.contains("animation-mode") || aliases.contains("am")
        }!!
        val objField = executor.javaClass.getDeclaredField("obj").apply { isAccessible = true }
        val listener = objField.get(executor)
        val methodField = executor.javaClass.getDeclaredField("method").apply { isAccessible = true }
        val method = methodField.get(executor) as java.lang.reflect.Method
        method.isAccessible = true
        method.invoke(listener, source, target, mode)
    }

    @Test
    fun testAnimationModeCommandEmitsWarningOnQueryAndSet() {
        val player = MockPlayer()

        // 1. Query mode
        invokeAnimationMode(player, "self", null)
        val expectedWarning = "Note: per-player animation mode applies to popups only; HUD elements follow global shader.yml animation-mode"
        assertTrue(
            receivedMessages.any { it.contains(expectedWarning) },
            "Querying animation mode must warn about HUD scope. Got messages: $receivedMessages"
        )

        receivedMessages.clear()

        // 2. Set mode
        invokeAnimationMode(player, "self", "server")
        assertTrue(
            receivedMessages.any { it.contains(expectedWarning) },
            "Setting animation mode must warn about HUD scope. Got messages: $receivedMessages"
        )
    }
}
