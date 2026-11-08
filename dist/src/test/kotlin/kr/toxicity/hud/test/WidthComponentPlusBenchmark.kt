package kr.toxicity.hud.test

import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.util.EMPTY_WIDTH_COMPONENT
import kr.toxicity.hud.util.toSpaceComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Test

class WidthComponentPlusBenchmark {

    private fun simulateHudAssembly(): WidthComponent {
        var root = EMPTY_WIDTH_COMPONENT

        // 3 Bossbars (background, fill, frame)
        for (b in 1..3) {
            val bg = WidthComponent(Component.text("bar_bg_$b").toBuilder(), 100)
            val fill = WidthComponent(Component.text("bar_fill_$b").color(NamedTextColor.GREEN).toBuilder(), 75)
            val frame = WidthComponent(Component.text("bar_frame_$b").toBuilder(), 100)

            val bar = 10.toSpaceComponent() + bg + (-100).toSpaceComponent() + fill + (-75).toSpaceComponent() + frame
            root += bar
        }

        // 5 Texts (name, health, mana, coords, ping)
        val texts = listOf(
            WidthComponent(Component.text("Player123").toBuilder(), 50),
            WidthComponent(Component.text("HP: 20/20").color(NamedTextColor.RED).toBuilder(), 60),
            WidthComponent(Component.text("MP: 100/100").color(NamedTextColor.BLUE).toBuilder(), 70),
            WidthComponent(Component.text("X: 100, Y: 64, Z: -200").toBuilder(), 120),
            WidthComponent(Component.text("Ping: 25ms").toBuilder(), 45)
        )
        for (txt in texts) {
            root += 5.toSpaceComponent() + txt + 5.toSpaceComponent()
        }

        // 1 Popup (shadow + backstep + text)
        val shadow = WidthComponent(Component.text("POPUP!").color(NamedTextColor.DARK_GRAY).toBuilder(), 40)
        val backstep = (-40).toSpaceComponent()
        val popupText = WidthComponent(Component.text("POPUP!").color(NamedTextColor.GOLD).toBuilder(), 40)
        root += shadow + backstep + popupText

        return root
    }

    @Test
    fun runBenchmark() {
        // Warmup JIT
        for (i in 1..50_000) {
            simulateHudAssembly()
        }

        // 1 Player: 1,000 ticks
        System.gc()
        Thread.sleep(50)
        val startMem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val start1 = System.nanoTime()
        for (i in 1..1_000) {
            simulateHudAssembly()
        }
        val elapsed1 = System.nanoTime() - start1
        val endMem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // 100 Players: 100 ticks (10,000 player-ticks)
        System.gc()
        Thread.sleep(50)
        val startMem100 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val start100 = System.nanoTime()
        for (t in 1..100) {
            for (p in 1..100) {
                simulateHudAssembly()
            }
        }
        val elapsed100 = System.nanoTime() - start100
        val endMem100 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        println("=== BENCHMARK RESULT ===")
        println("1 Player (1,000 ticks): total time = ${elapsed1 / 1_000_000.0} ms, per tick = ${elapsed1 / 1_000.0 / 1_000.0} us")
        println("100 Players (100 ticks = 10,000 assemblies): total time = ${elapsed100 / 1_000_000.0} ms, per tick (100 players) = ${elapsed100 / 100.0 / 1_000_000.0} ms")
    }
}
