package kr.toxicity.hud.test

import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.text.applyClientExact
import kr.toxicity.hud.text.applyClientExactDual
import kr.toxicity.hud.text.clearExactTextCache
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TextExactCacheMutationTest {

    @BeforeEach
    fun setUp() {
        clearExactTextCache()
    }

    @Test
    fun testExactSingleCacheNotPollutedByPlusOrMutation() {
        val base = WidthComponent(Component.text("Health: ").toBuilder(), 40)

        // 1. First retrieval from cache
        val first = base.applyClientExact(10)
        val initialChildrenCount = first.component.children().size
        val initialBuild = first.component.build()

        // 2. Perform `plus` operation on the returned component
        val appendage = WidthComponent(Component.text("100/100").toBuilder(), 35)
        val combined = first + appendage

        // Verify combined has the appendage
        assertEquals(initialChildrenCount + 1, combined.component.children().size)

        // 3. Retrieve second time by the same key from cache
        val second = base.applyClientExact(10)

        // The cached component must remain pristine and unpolluted!
        assertEquals(initialChildrenCount, second.component.children().size,
            "Cached WidthComponent must not be mutated by subsequent '+' operations!")
        assertEquals(initialBuild, second.component.build(),
            "Cached component build must match initial build")
    }

    @Test
    fun testExactDualCacheNotPollutedByPlusOrMutation() {
        // Dual mode requires text with shadow
        val textWithShadow = Component.text("Mana: ", NamedTextColor.BLUE)
            .shadowColor(ShadowColor.shadowColor(0, 0, 255, 255))
        val base = WidthComponent(Component.text().append(textWithShadow), 30)

        // 1. First retrieval from dual cache
        val first = base.applyClientExactDual(5, 40)
        val initialChildrenCount = first.component.children().size
        val initialBuild = first.component.build()

        // 2. Perform `plus` operation on the returned component
        val appendage = WidthComponent(Component.text("50/50").toBuilder(), 25)
        val combined = first + appendage

        // 3. Retrieve second time from dual cache
        val second = base.applyClientExactDual(5, 40)

        // The cached component must remain pristine and unpolluted!
        assertEquals(initialChildrenCount, second.component.children().size,
            "Cached dual WidthComponent must not be mutated by subsequent '+' operations!")
        assertEquals(initialBuild, second.component.build(),
            "Cached dual component build must match initial build")
    }
}
