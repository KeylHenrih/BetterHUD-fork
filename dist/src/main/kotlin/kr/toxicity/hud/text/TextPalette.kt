package kr.toxicity.hud.text

import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

object TextPalette {
    private val lock = Any()
    private val paletteList = mutableListOf<TextColor>()
    private val paletteMap = mutableMapOf<Int, Int>() // color.value() -> index

    private val hexPattern = Pattern.compile("#([0-9a-fA-F]{6})")
    private val outlinePattern = Pattern.compile("""outline:\s*([0-9a-fA-F]{8})""")

    init {
        reset()
    }

    fun reset() {
        synchronized(lock) {
            paletteList.clear()
            paletteMap.clear()

            // 0 is always WHITE as standard text fallback
            registerInternal(NamedTextColor.WHITE)

            // Register standard Minecraft named colors
            val standardColors = listOf(
                NamedTextColor.BLACK,
                NamedTextColor.DARK_BLUE,
                NamedTextColor.DARK_GREEN,
                NamedTextColor.DARK_AQUA,
                NamedTextColor.DARK_RED,
                NamedTextColor.DARK_PURPLE,
                NamedTextColor.GOLD,
                NamedTextColor.GRAY,
                NamedTextColor.DARK_GRAY,
                NamedTextColor.BLUE,
                NamedTextColor.GREEN,
                NamedTextColor.AQUA,
                NamedTextColor.RED,
                NamedTextColor.LIGHT_PURPLE,
                NamedTextColor.YELLOW
            )
            standardColors.forEach { registerInternal(it) }
        }
    }

    private fun registerInternal(color: TextColor): Int {
        val rgb = color.value()
        paletteMap[rgb]?.let { return it }
        if (paletteList.size >= 256) {
            return findNearestIndex(color)
        }
        val newIndex = paletteList.size
        paletteList.add(color)
        paletteMap[rgb] = newIndex
        return newIndex
    }

    fun register(color: TextColor): Int {
        synchronized(lock) {
            return registerInternal(color)
        }
    }

    fun indexOf(color: TextColor): Int {
        synchronized(lock) {
            paletteMap[color.value()]?.let { return it }
            return findNearestIndex(color)
        }
    }

    fun getColor(index: Int): TextColor {
        synchronized(lock) {
            if (index in paletteList.indices) {
                return paletteList[index]
            }
            return NamedTextColor.WHITE
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return paletteList.size
        }
    }

    private fun findNearestIndex(target: TextColor): Int {
        var bestIndex = 0
        var minDistance = Long.MAX_VALUE
        val tr = target.red()
        val tg = target.green()
        val tb = target.blue()

        for (i in paletteList.indices) {
            val c = paletteList[i]
            val dr = (c.red() - tr).toLong()
            val dg = (c.green() - tg).toLong()
            val db = (c.blue() - tb).toLong()
            val dist = dr * dr + dg * dg + db * db
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
                if (dist == 0L) break
            }
        }
        return bestIndex
    }

    fun scanFolder(dataFolder: File) {
        if (!dataFolder.exists() || !dataFolder.isDirectory) return
        dataFolder.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            runCatching {
                val content = file.readText()
                val hexMatcher = hexPattern.matcher(content)
                while (hexMatcher.find()) {
                    val hex = hexMatcher.group(1)
                    TextColor.fromHexString("#$hex")?.let { register(it) }
                }
                val outlineMatcher = outlinePattern.matcher(content)
                while (outlineMatcher.find()) {
                    val hex8 = outlineMatcher.group(1)
                    val hex6 = hex8.substring(2)
                    TextColor.fromHexString("#$hex6")?.let { register(it) }
                }
            }
        }
    }

    fun generateGlsl(): List<String> {
        val lines = mutableListOf<String>()
        lines.add("vec3 getTextPaletteColor(int idx) {")
        lines.add("    switch (idx) {")
        synchronized(lock) {
            paletteList.forEachIndexed { idx, color ->
                val r = String.format(Locale.US, "%.6f", color.red() / 255.0)
                val g = String.format(Locale.US, "%.6f", color.green() / 255.0)
                val b = String.format(Locale.US, "%.6f", color.blue() / 255.0)
                lines.add("        case $idx: return vec3($r, $g, $b);")
            }
        }
        lines.add("        default: return vec3(1.0, 1.0, 1.0);")
        lines.add("    }")
        lines.add("}")
        return lines
    }
}

fun Component.applyClientPalette(
    tStartByte: Int,
    defaultOutline: Int = 0,
    inheritedColor: TextColor = NamedTextColor.WHITE
): Component {
    val style = style()
    val rawColor = style.color()
    val effectiveColor = rawColor ?: inheritedColor
    val paletteIdx = TextPalette.indexOf(effectiveColor)
    val newTextColor = TextColor.color(tStartByte, paletteIdx, 0)

    val shadowColorObj = style.shadowColor()
    val hasExplicitShadow = shadowColorObj != null && shadowColorObj != ShadowColor.none() && shadowColorObj.alpha() != 0
    val hasShadow = hasExplicitShadow || (defaultOutline != 0)

    val newShadow = if (hasShadow) {
        ShadowColor.shadowColor(tStartByte, paletteIdx, 1, 255)
    } else {
        ShadowColor.none()
    }

    val newStyle = style.toBuilder()
        .color(newTextColor)
        .shadowColor(newShadow)
        .build()

    val newChildren = children().map {
        it.applyClientPalette(tStartByte, defaultOutline, effectiveColor)
    }

    return when (this) {
        is TextComponent -> this.style(newStyle).children(newChildren)
        else -> children(newChildren).style(newStyle)
    }
}

fun WidthComponent.applyClientPalette(tStartByte: Int, defaultOutline: Int = 0): WidthComponent {
    val built = component.build()
    val transformed = built.applyClientPalette(tStartByte, defaultOutline)
    val builder = if (transformed is TextComponent) transformed.toBuilder() else Component.text().append(transformed)
    return WidthComponent(builder, width)
}

fun PixelComponent.applyClientPalette(tStartByte: Int, defaultOutline: Int = 0): PixelComponent {
    return PixelComponent(component.applyClientPalette(tStartByte, defaultOutline), pixel)
}
