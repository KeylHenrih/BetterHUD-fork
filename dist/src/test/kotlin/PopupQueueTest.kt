package kr.toxicity.hud.test

import kr.toxicity.hud.animation.PlayerAnimationMode
import kr.toxicity.hud.api.BetterHud
import kr.toxicity.hud.api.BetterHudAPI
import kr.toxicity.hud.api.BetterHudBootstrap
import kr.toxicity.hud.api.adapter.LocationWrapper
import kr.toxicity.hud.api.adapter.WorldWrapper
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.api.popup.PopupIterator
import kr.toxicity.hud.api.popup.PopupSortType
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.player.HudPlayerImpl
import kr.toxicity.hud.popup.PopupIteratorGroupImpl
import kr.toxicity.hud.popup.PopupIteratorImpl
import net.kyori.adventure.audience.Audience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID

class PopupQueueTest {

    private var currentWorldTime = 100L

    private inner class TestPlayer : HudPlayerImpl() {
        init {
            setPlayerAnimationMode(PlayerAnimationMode.CLIENT)
        }
        override fun uuid(): UUID = UUID.randomUUID()
        override fun name(): String = "QueuePlayer"
        override fun ping(): Int = 50
        override fun worldTime(): Long = currentWorldTime
        override fun audience(): Audience = Audience.empty()
        override fun hasPermission(perm: String): Boolean = true
        override fun locale(): Locale = Locale.ENGLISH
        override fun location(): LocationWrapper = throw UnsupportedOperationException()
        override fun world(): WorldWrapper = throw UnsupportedOperationException()
        override fun handle(): Any = this
        override fun updatePlaceholder() {}
    }

    private fun createMockPopup(maxStack: Int = 1): Popup {
        return Proxy.newProxyInstance(
            Popup::class.java.classLoader,
            arrayOf(Popup::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getMaxStack" -> maxStack
                "tick" -> 1
                "frameType" -> Popup.FrameType.LOCAL
                else -> null
            }
        } as Popup
    }

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

    @Test
    fun testQueuedPopupSpawnTimeDeferredUntilFirstDisplay() {
        currentWorldTime = 100L
        val player = TestPlayer()
        val mockParent = createMockPopup(maxStack = 1)
        val group = PopupIteratorGroupImpl()

        // Popup A: starts at tick 100
        val iterA = PopupIteratorImpl(
            reason = UpdateEvent.EMPTY,
            player = player,
            layouts = emptyList(),
            parent = mockParent,
            unique = false,
            maxIndex = 0,
            key = "A",
            sortType = PopupSortType.LAST,
            name = "popup_a",
            save = true,
            push = false,
            alwaysCheckCondition = false,
            value = -1,
            condition = { true },
            removeTask = {}
        )

        // Popup B: enters queue at tick 100
        val iterB = PopupIteratorImpl(
            reason = UpdateEvent.EMPTY,
            player = player,
            layouts = emptyList(),
            parent = mockParent,
            unique = false,
            maxIndex = 0,
            key = "B",
            sortType = PopupSortType.LAST,
            name = "popup_b",
            save = true,
            push = false,
            alwaysCheckCondition = false,
            value = -1,
            condition = { true },
            removeTask = {}
        )

        group.addIterator(iterA)
        group.addIterator(iterB)

        assertEquals(0, iterA.index, "Popup A must be at index 0")
        assertEquals(1, iterB.index, "Popup B must be at index 1 (queued)")

        // At tick 100, Popup A is active, Popup B is waiting in queue
        group.next() // iterA displayed for tick 100

        // Advance 300 ticks while iterA plays
        currentWorldTime = 400L

        // A finishes and is removed
        iterA.remove()

        // At tick 400, group.next() removes A and shifts B to index 0, calling B.next() for the first time
        group.next()

        assertEquals(0, iterB.index, "Popup B must have shifted to index 0")

        // In the new code, B's spawnTime MUST be set to currentWorldTime (400L), NOT creation time (100L)
        assertEquals(400L, iterB.getSpawnTime(), "Popup B spawnTime must anchor to the tick it first displays (400L)")
        val elapsed = player.worldTime() - iterB.getSpawnTime()
        assertEquals(0L, elapsed, "Popup B elapsed must start at 0 when first displayed")
    }

    @Test
    fun testDisplacedPopupWithoutQueue() {
        currentWorldTime = 100L
        val player = TestPlayer()
        val mockParent = createMockPopup(maxStack = 1)
        val group = PopupIteratorGroupImpl()

        // Popup with push: true (displacement)
        val iter1 = PopupIteratorImpl(
            reason = UpdateEvent.EMPTY,
            player = player,
            layouts = emptyList(),
            parent = mockParent,
            unique = false,
            maxIndex = 0,
            key = "P1",
            sortType = PopupSortType.FIRST,
            name = "popup_1",
            save = false,
            push = true,
            alwaysCheckCondition = false,
            value = -1,
            condition = { true },
            removeTask = {}
        )
        group.addIterator(iter1)
        group.next()
        assertEquals(100L, iter1.getSpawnTime())

        currentWorldTime = 250L
        val iter2 = PopupIteratorImpl(
            reason = UpdateEvent.EMPTY,
            player = player,
            layouts = emptyList(),
            parent = mockParent,
            unique = false,
            maxIndex = 0,
            key = "P2",
            sortType = PopupSortType.FIRST,
            name = "popup_2",
            save = false,
            push = true,
            alwaysCheckCondition = false,
            value = -1,
            condition = { true },
            removeTask = {}
        )
        group.addIterator(iter2)
        group.next()

        assertEquals(250L, iter2.getSpawnTime(), "Pushed popup must anchor to worldTime at first display (250L)")
    }
}
