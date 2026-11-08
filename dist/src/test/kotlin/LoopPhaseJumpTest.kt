package kr.toxicity.hud.test

import kr.toxicity.hud.animation.AnimationLocation
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.BetterHudLogger
import kr.toxicity.hud.yaml.YamlObjectImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Proxy

class LoopPhaseJumpTest {

    private val capturedWarnings = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        capturedWarnings.clear()
        setupMockBootstrap()
    }

    private fun setupMockBootstrap() {
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

        val mockBootstrap = Proxy.newProxyInstance(
            BetterHudBootstrap::class.java.classLoader,
            arrayOf(BetterHudBootstrap::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "logger" -> mockLogger
                "dataFolder" -> File("build/tmp/test-phase-jump")
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
    fun testLoopDuration7LogsPhaseJumpWarning() {
        val yaml = YamlObjectImpl("test_anim_7", mutableMapOf(
            "mode" to "client",
            "type" to "loop",
            "duration" to 7,
            "x-equation" to "0",
            "y-equation" to "0"
        ))

        val loc = AnimationLocation(yaml, "test_anim_7")
        org.junit.jupiter.api.Assertions.assertNotNull(loc.clientAnimation, "Client animation must be created")

        assertTrue(
            capturedWarnings.any {
                it.contains("test_anim_7") &&
                it.contains("loop duration=7 which does not divide 24000") &&
                it.contains("remainder = ${24000 % 7}") &&
                it.contains("phase jump once per 20 minutes")
            },
            "Phase jump warning must be logged for duration=7, but got: $capturedWarnings"
        )
    }

    @Test
    fun testLoopDuration20DoesNotLogWarning() {
        val yaml = YamlObjectImpl("test_anim_20", mutableMapOf(
            "mode" to "client",
            "type" to "loop",
            "duration" to 20,
            "x-equation" to "0",
            "y-equation" to "0"
        ))

        val loc = AnimationLocation(yaml, "test_anim_20")
        org.junit.jupiter.api.Assertions.assertNotNull(loc.clientAnimation, "Client animation must be created")

        assertFalse(
            capturedWarnings.any { it.contains("phase jump once per 20 minutes") || it.contains("does not divide 24000") },
            "Phase jump warning must NOT be logged for duration=20 (exact divisor), but got: $capturedWarnings"
        )
    }
}
