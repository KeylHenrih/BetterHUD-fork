package kr.toxicity.hud.test

import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.animation.PlayerAnimationMode
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.api.popup.PopupIterator
import kr.toxicity.hud.api.popup.PopupSortType
import kr.toxicity.hud.player.HudPlayerImpl
import kr.toxicity.hud.popup.PopupIteratorGroupImpl
import net.kyori.adventure.audience.Audience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID

class WorldChangeRespawnTest {

    @BeforeEach
    fun setup() {
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

    private class FakeWorld(
        var gameTime: Long,
        var dayTime: Long
    )

    private class FakeHudPlayer(
        private val uuid: UUID = UUID.randomUUID(),
        var currentWorld: FakeWorld
    ) : HudPlayerImpl() {
        init {
            setPlayerAnimationMode(PlayerAnimationMode.CLIENT)
        }
        override fun uuid(): UUID = uuid
        override fun name(): String = "TestPlayer"
        override fun ping(): Int = 50
        override fun worldTime(): Long = currentWorld.gameTime
        override fun audience(): Audience = Audience.empty()
        override fun hasPermission(perm: String): Boolean = true
        override fun locale(): Locale = Locale.ENGLISH
        override fun location(): LocationWrapper = throw UnsupportedOperationException()
        override fun world(): WorldWrapper = throw UnsupportedOperationException()
        override fun handle(): Any = this
        override fun updatePlaceholder() {}
    }

    private class SimpleMockIterator(
        private var spawnTime: Long,
        val duration: Int = 70
    ) : PopupIterator {
        override fun getSpawnTime(): Long = spawnTime
        override fun resetSpawnTime(spawnTime: Long) {
            this.spawnTime = spawnTime
        }
        override fun parent(): Popup = Proxy.newProxyInstance(
            Popup::class.java.classLoader,
            arrayOf(Popup::class.java)
        ) { _, m, _ -> if (m.name == "getMaxStack") 10 else null } as Popup

        override fun isUnique(): Boolean = false
        override fun markedAsRemoval(): Boolean = false
        override fun push(): Boolean = false
        override fun getIndex(): Int = 0
        override fun getMaxIndex(): Int = 0
        override fun setIndex(index: Int) {}
        override fun available(): Boolean = true
        override fun getKey(): Any = "key"
        override fun canSave(): Boolean = false
        override fun alwaysCheckCondition(): Boolean = false
        override fun next(): List<WidthComponent> = emptyList()
        override fun remove() {}
        override fun getPriority(): Int = 0
        override fun setPriority(priority: Int) {}
        override fun name(): String = "test_popup"
        override fun getUUID(): UUID = UUID.randomUUID()
        override fun getSortType(): PopupSortType = PopupSortType.FIRST
        override fun compareTo(other: PopupIterator): Int = 0
    }

    private fun isPopupVisible(player: HudPlayer, iter: PopupIterator, duration: Int = 70, lookahead: Int = 10): Boolean {
        val safeWindow = duration - lookahead
        val currentElapsed = (player.worldTime() - iter.getSpawnTime()).toInt()
        val isExceeding = currentElapsed >= safeWindow
        val effectiveElapsed = if (isExceeding) duration else currentElapsed
        return effectiveElapsed < safeWindow && currentElapsed >= 0
    }

    @Test
    fun testWorldChangeUsesGameTimeAndPopupRemainsVisible() {
        val world = FakeWorld(gameTime = 5_000_000L, dayTime = 6000L)
        val player = FakeHudPlayer(currentWorld = world)

        val iterator = SimpleMockIterator(spawnTime = 5_000_000L, duration = 70)
        val group = PopupIteratorGroupImpl()
        group.addIterator(iterator)
        player.popupGroupIteratorMap["test"] = group

        // Call handleWorldChange() without passing dayTime
        // In the old code, this method does not exist without arguments, or if dayTime (6000L) was passed:
        player.handleWorldChange()

        val elapsed = player.worldTime() - iterator.getSpawnTime()
        assertTrue(elapsed in 0..10, "Elapsed after world change must be small and non-negative, got: $elapsed")
        assertTrue(isPopupVisible(player, iterator), "Popup must remain visible after world change")
    }

    @Test
    fun testRespawnUsesGameTimeAndPopupRemainsVisible() {
        val worldA = FakeWorld(gameTime = 5_000_000L, dayTime = 6000L)
        val worldB = FakeWorld(gameTime = 7_000_000L, dayTime = 12000L)
        val player = FakeHudPlayer(currentWorld = worldA)

        val iterator = SimpleMockIterator(spawnTime = 5_000_000L, duration = 70)
        val group = PopupIteratorGroupImpl()
        group.addIterator(iterator)
        player.popupGroupIteratorMap["test"] = group

        // Player respawns to world B
        player.handleRespawn(worldB.gameTime)
        player.currentWorld = worldB

        val elapsed = player.worldTime() - iterator.getSpawnTime()
        assertEquals(0L, elapsed, "Elapsed after respawn to world B must be 0")
        assertTrue(isPopupVisible(player, iterator), "Popup must remain visible after respawn to new world")
    }

    @Test
    fun testOldBugSimulationFailsPopupVisibility() {
        val world = FakeWorld(gameTime = 5_000_000L, dayTime = 6000L)
        val player = FakeHudPlayer(currentWorld = world)

        val iterator = SimpleMockIterator(spawnTime = 5_000_000L, duration = 70)
        val group = PopupIteratorGroupImpl()
        group.addIterator(iterator)
        player.popupGroupIteratorMap["test"] = group

        // Simulate old bug: passing dayTime (6000L) instead of gameTime
        player.handleWorldChange(world.dayTime)

        val elapsed = player.worldTime() - iterator.getSpawnTime()
        assertEquals(4_994_000L, elapsed, "Simulated bug should produce enormous elapsed")
        assertFalse(isPopupVisible(player, iterator), "Old bug must cause safety latch to hide popup")
    }
}
