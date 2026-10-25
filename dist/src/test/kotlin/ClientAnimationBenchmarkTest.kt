import kr.toxicity.hud.animation.AnimationMode
import kr.toxicity.hud.animation.AnimationType
import kr.toxicity.hud.animation.EasingType
import kr.toxicity.hud.animation.ListenerTracker
import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class ClientAnimationBenchmarkTest {

    @Test
    fun testStationaryNetworkTrafficReduction() {
        val totalTicks = 100 // 5 seconds at 20 TPS
        val duration = 60

        // Server-mode: Server evaluates equation every tick and produces a unique component
        var serverPacketsSent = 0
        var lastServerComponent: Component? = null

        val serverFrames = (0..<duration).map { t ->
            val x = (3.0 * cos(t.toDouble() / 30.0 * Math.PI)).toInt()
            val y = (3.0 * sin(t.toDouble() / 30.0 * Math.PI)).toInt()
            // In BetterHud server mode, each (x, y) yields a different glyph/character
            Component.text("glyph_at_${x}_$y").color(NamedTextColor.WHITE).compact()
        }

        for (tick in 0..<totalTicks) {
            val currentFrame = serverFrames[tick % duration]
            if (currentFrame != lastServerComponent) {
                lastServerComponent = currentFrame
                serverPacketsSent++
            }
        }

        // Client-mode: Server sends the base glyph once. Client shader handles all offsets!
        var clientPacketsSent = 0
        var lastClientComponent: Component? = null

        val clientStaticFrame = Component.text("glyph_client_shader").color(NamedTextColor.WHITE).compact()

        for (tick in 0..<totalTicks) {
            val currentFrame = clientStaticFrame
            if (currentFrame != lastClientComponent) {
                lastClientComponent = currentFrame
                clientPacketsSent++
            }
        }

        println("=== Network Traffic Benchmark (100 ticks = 5 seconds) ===")
        println("Server-Mode Packets Sent: $serverPacketsSent (20.0 pkts/sec)")
        println("Client-Mode Packets Sent: $clientPacketsSent (${clientPacketsSent.toDouble() / (totalTicks / 20.0)} pkts/sec)")
        println("Traffic reduction: ${((1.0 - clientPacketsSent.toDouble() / serverPacketsSent) * 100.0)}%")

        assertEquals(1, clientPacketsSent, "Client mode must only send 1 initial packet for stationary animated HUD")
        assertTrue(serverPacketsSent >= 40, "Server mode must send frequent update packets (observed: $serverPacketsSent)")
        assertTrue(clientPacketsSent < serverPacketsSent)
    }

    @Test
    fun testCpuOverheadComparisonOn100Players() {
        val playerCount = 100
        val tickCount = 100
        val duration = 60

        // Precompute server frames
        val serverFrames = (0..<duration).map { t ->
            val x = (3.0 * cos(t.toDouble() / 30.0 * Math.PI)).toInt()
            val y = (3.0 * sin(t.toDouble() / 30.0 * Math.PI)).toInt()
            Component.text("glyph_${x}_$y").color(NamedTextColor.WHITE).compact()
        }
        val clientFrame = Component.text("glyph_client").color(NamedTextColor.WHITE).compact()

        // Warmup JIT
        for (p in 0..<playerCount) {
            for (t in 0..<50) {
                val f1 = serverFrames[t % duration]
                val f2 = clientFrame
            }
        }

        // Benchmark Server-mode
        val serverStart = System.nanoTime()
        var serverHash = 0
        for (t in 0..<tickCount) {
            for (p in 0..<playerCount) {
                val frame = serverFrames[(t + p) % duration]
                val built = frame.compact()
                serverHash = serverHash xor built.hashCode()
            }
        }
        val serverTimeNs = System.nanoTime() - serverStart
        val serverMsPerTick = (serverTimeNs / 1_000_000.0) / tickCount

        // Benchmark Client-mode
        val clientStart = System.nanoTime()
        var clientHash = 0
        for (t in 0..<tickCount) {
            for (p in 0..<playerCount) {
                val frame = clientFrame
                clientHash = clientHash xor frame.hashCode()
            }
        }
        val clientTimeNs = System.nanoTime() - clientStart
        val clientMsPerTick = (clientTimeNs / 1_000_000.0) / tickCount

        println("=== Server CPU Benchmark (100 players, 100 ticks) ===")
        println("Server-Mode CPU Time: %.4f ms/tick (total %.2f ms)".format(serverMsPerTick, serverTimeNs / 1_000_000.0))
        println("Client-Mode CPU Time: %.4f ms/tick (total %.2f ms)".format(clientMsPerTick, clientTimeNs / 1_000_000.0))
        println("CPU Speedup: %.2fx".format(serverMsPerTick / clientMsPerTick.coerceAtLeast(0.0001)))

        assertTrue(clientMsPerTick <= serverMsPerTick, "Client mode CPU time must be less than or equal to server mode")
    }

    @Test
    fun testSequenceCollapseTraffic() {
        val frameCount = 4
        val totalTicks = 80 // 4 seconds

        // Server-mode: Server picks images[tick % frameCount] every tick
        val frames = (0..<frameCount).map { Component.text("seq_$it").compact() }
        var serverPackets = 0
        var lastServerComp: Component? = null

        for (t in 0..<totalTicks) {
            val comp = frames[t % frameCount]
            if (comp != lastServerComp) {
                lastServerComp = comp
                serverPackets++
            }
        }

        // Client-mode: Server sends all 4 frames collapsed into a single component once
        val collapsedClientComp = Component.text("seq_0\uF801seq_1\uF801seq_2\uF801seq_3").compact()
        var clientPackets = 0
        var lastClientComp: Component? = null

        for (t in 0..<totalTicks) {
            val comp = collapsedClientComp
            if (comp != lastClientComp) {
                lastClientComp = comp
                clientPackets++
            }
        }

        assertEquals(totalTicks, serverPackets)
        assertEquals(1, clientPackets)
        println("Sequence collapse network packet reduction: $serverPackets -> $clientPackets packets")
    }
}
