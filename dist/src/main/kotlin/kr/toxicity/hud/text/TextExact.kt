package kr.toxicity.hud.text

import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.util.finalizeFont
import kr.toxicity.hud.util.toSpaceComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Divisors of 24000 (Minecraft day cycle in ticks) that are <= 120.
 * Allows safe dual-copy payloads with 2 * M <= 240 < 255.
 */
val M_DIVISORS = intArrayOf(
    1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 16, 20, 24, 25, 30, 32, 40, 48, 50, 60, 64, 75, 80, 96, 100, 120
)

/**
 * Finds the smallest divisor M of 24000 such that M >= requiredTicks.
 * Returns null if requiredTicks > 120.
 */
fun getDivisorM(requiredTicks: Int): Int? {
    return M_DIVISORS.firstOrNull { it >= requiredTicks }
}

/**
 * Counts codepoints with italic decoration in an Adventure Component tree.
 * In Minecraft client 26.1.2, italic is purely a vertex shear; it does NOT increase cursor advance.
 */
fun Component.countItalicChars(parentItalic: Boolean = false): Int {
    val style = style()
    val isItalic = when (style.decoration(TextDecoration.ITALIC)) {
        TextDecoration.State.TRUE -> true
        TextDecoration.State.FALSE -> false
        TextDecoration.State.NOT_SET -> parentItalic
    }
    var count = 0
    if (this is TextComponent) {
        if (isItalic) {
            count += content().codePoints().count().toInt()
        }
    }
    for (child in children()) {
        count += child.countItalicChars(isItalic)
    }
    return count
}

/**
 * Calculates the exact horizontal cursor advance of a WidthComponent in the Minecraft client.
 * Subtracts italic padding which BetterHud adds for server bounding-box width but client ignores for cursor advance.
 */
fun calculateClientAdvance(widthComp: WidthComponent): Int {
    val italicCount = widthComp.component.build().countItalicChars()
    return (widthComp.width - italicCount).coerceAtLeast(0)
}

/**
 * Resolves the effective shadow color for a text node.
 * - Explicit shadow color (e.g. from MiniMessage <shadow:...>): used if alpha != 0 and value != 0.
 * - Explicit shadow 0 / alpha 0: shadow disabled.
 * - Inherited from ancestor if present.
 * - Default: vanilla ARGB.scaleRGB(effectiveColor, 0.25f).
 */
fun resolveShadow(
    style: Style,
    inheritedShadow: TextColor?,
    inheritedShadowDisabled: Boolean,
    effectiveColor: TextColor
): Pair<TextColor?, Boolean> {
    val rawShadow = style.shadowColor()
    return if (rawShadow != null) {
        if (rawShadow.value() == 0 || rawShadow.alpha() == 0) {
            Pair(null, true)
        } else {
            Pair(TextColor.color(rawShadow.red(), rawShadow.green(), rawShadow.blue()), false)
        }
    } else {
        if (inheritedShadowDisabled) {
            Pair(null, true)
        } else if (inheritedShadow != null) {
            Pair(inheritedShadow, false)
        } else {
            Pair(
                TextColor.color(
                    (effectiveColor.red() * 0.25f).toInt(),
                    (effectiveColor.green() * 0.25f).toInt(),
                    (effectiveColor.blue() * 0.25f).toInt()
                ),
                false
            )
        }
    }
}

/**
 * Checks whether any leaf node in the component tree produces a visible shadow.
 */
fun Component.hasAnyShadow(
    inheritedShadow: TextColor? = null,
    inheritedShadowDisabled: Boolean = false,
    inheritedColor: TextColor = NamedTextColor.WHITE
): Boolean {
    val style = style()
    val rawColor = style.color()
    val effectiveColor = rawColor ?: inheritedColor
    val (shadow, disabled) = resolveShadow(style, inheritedShadow, inheritedShadowDisabled, effectiveColor)
    if (this is TextComponent && content().isNotEmpty()) {
        if (shadow != null) return true
    }
    for (child in children()) {
        if (child.hasAnyShadow(shadow, disabled, effectiveColor)) return true
    }
    return false
}

enum class DualCopyRole {
    SHADOW,
    TEXT
}

/**
 * Rewrites a component tree for a specific dual copy role.
 * - SHADOW (Copy 1): shadow quads carry shadow RGB with Alpha in range (M + 1)..(2 * M).
 *                    Leaves without shadow have shadow_color = 0 (no quad emitted).
 * - TEXT (Copy 2): shadow quads carry text RGB with Alpha in range 1..M.
 * Both roles collapse main quads (main color is set to WHITE with Alpha 255).
 */
fun Component.applyClientDualRole(
    role: DualCopyRole,
    tStartByte: Int,
    moduloM: Int,
    inheritedColor: TextColor = NamedTextColor.WHITE,
    inheritedShadow: TextColor? = null,
    inheritedShadowDisabled: Boolean = false
): Component {
    val style = style()
    val rawColor = style.color()
    val effectiveColor = rawColor ?: inheritedColor
    val (shadow, disabled) = resolveShadow(style, inheritedShadow, inheritedShadowDisabled, effectiveColor)

    val newShadow = when (role) {
        DualCopyRole.SHADOW -> {
            if (shadow != null) {
                val alpha = (1 + moduloM + tStartByte).coerceIn(moduloM + 1, 2 * moduloM)
                ShadowColor.shadowColor(shadow.red(), shadow.green(), shadow.blue(), alpha)
            } else {
                ShadowColor.shadowColor(0)
            }
        }
        DualCopyRole.TEXT -> {
            val alpha = (1 + tStartByte).coerceIn(1, moduloM)
            ShadowColor.shadowColor(effectiveColor.red(), effectiveColor.green(), effectiveColor.blue(), alpha)
        }
    }

    val newStyle = style.toBuilder()
        .color(NamedTextColor.WHITE)
        .shadowColor(newShadow)
        .build()

    val newChildren = children().map {
        it.applyClientDualRole(role, tStartByte, moduloM, effectiveColor, shadow, disabled)
    }

    return when (this) {
        is TextComponent -> this.style(newStyle).children(newChildren)
        else -> children(newChildren).style(newStyle)
    }
}

/**
 * Rewrites an Adventure Component tree for the BetterHud 'exact' single-copy mode (no shadow).
 */
fun Component.applyClientExact(
    tStartByte: Int,
    inheritedColor: TextColor = NamedTextColor.WHITE
): Component {
    val style = style()
    val rawColor = style.color()
    val effectiveColor = rawColor ?: inheritedColor
    val newTextColor = NamedTextColor.WHITE
    val newShadow = ShadowColor.shadowColor(
        effectiveColor.red(),
        effectiveColor.green(),
        effectiveColor.blue(),
        (tStartByte + 1).coerceIn(1, 240)
    )

    val newStyle = style.toBuilder()
        .color(newTextColor)
        .shadowColor(newShadow)
        .build()

    val newChildren = children().map {
        it.applyClientExact(tStartByte, effectiveColor)
    }

    return when (this) {
        is TextComponent -> this.style(newStyle).children(newChildren)
        else -> children(newChildren).style(newStyle)
    }
}

private const val EXACT_CACHE_MAX_SIZE = 2048
private val exactDualCache: MutableMap<ExactDualCacheKey, WidthComponent> = net.jodah.expiringmap.ExpiringMap.builder()
    .maxSize(EXACT_CACHE_MAX_SIZE)
    .build()
private val exactSingleCache: MutableMap<ExactSingleCacheKey, WidthComponent> = net.jodah.expiringmap.ExpiringMap.builder()
    .maxSize(EXACT_CACHE_MAX_SIZE)
    .build()

data class ExactDualCacheKey(val component: Component, val tStartByte: Int, val moduloM: Int, val width: Int)
data class ExactSingleCacheKey(val component: Component, val tStartByte: Int, val width: Int)

fun clearExactTextCache() {
    exactDualCache.clear()
    exactSingleCache.clear()
}

fun getExactDualCacheSize(): Int = exactDualCache.size
fun getExactSingleCacheSize(): Int = exactSingleCache.size

fun WidthComponent.applyClientExact(tStartByte: Int): WidthComponent {
    val built = component.build()
    val key = ExactSingleCacheKey(built, tStartByte, width)
    exactSingleCache[key]?.let { return it }

    val transformed = built.applyClientExact(tStartByte)
    val builder = if (transformed is TextComponent) transformed.toBuilder() else Component.text().append(transformed)
    val result = WidthComponent(builder, width)

    exactSingleCache[key] = result
    return result
}

fun PixelComponent.applyClientExact(tStartByte: Int): PixelComponent {
    return PixelComponent(component.applyClientExact(tStartByte), pixel)
}

/**
 * Rewrites a WidthComponent for the BetterHud 'dual' client text shadow mode.
 * Emits Copy 1 (shadow layer) + backstep (-W) + Copy 2 (text layer).
 * If no leaf has shadow, falls back to single-copy optimization without quad doubling.
 */
fun WidthComponent.applyClientExactDual(tStartByte: Int, moduloM: Int): WidthComponent {
    val built = component.build()
    if (!built.hasAnyShadow()) {
        return applyClientExact(tStartByte)
    }
    val key = ExactDualCacheKey(built, tStartByte, moduloM, width)
    exactDualCache[key]?.let { return it }

    val copyShadow = built.applyClientDualRole(DualCopyRole.SHADOW, tStartByte, moduloM)
    val copyText = built.applyClientDualRole(DualCopyRole.TEXT, tStartByte, moduloM)
    val advance = calculateClientAdvance(this)
    val result = if (advance <= 0) {
        val builder = if (copyText is TextComponent) copyText.toBuilder() else Component.text().append(copyText)
        WidthComponent(builder, width)
    } else {
        val backstep = (-advance).toSpaceComponent(-width).finalizeFont()
        val shadowWidthComp = WidthComponent(
            if (copyShadow is TextComponent) copyShadow.toBuilder() else Component.text().append(copyShadow),
            width
        )
        val textWidthComp = WidthComponent(
            if (copyText is TextComponent) copyText.toBuilder() else Component.text().append(copyText),
            width
        )
        shadowWidthComp + backstep + textWidthComp
    }
    exactDualCache[key] = result
    return result
}

fun PixelComponent.applyClientExactDual(tStartByte: Int, moduloM: Int): PixelComponent {
    return PixelComponent(component.applyClientExactDual(tStartByte, moduloM), pixel)
}
