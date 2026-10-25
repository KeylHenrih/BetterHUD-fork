package kr.toxicity.hud.animation

import net.kyori.adventure.text.format.TextColor
import kotlin.math.roundToInt

class ListenerTracker(
    val duration: Int,
    val easing: EasingType
) {
    private var lastTarget: Double = -1.0
    private var vFrom: Double = 1.0
    private var vTo: Double = 1.0
    private var startTick: Long = 0

    fun update(targetVal: Double, currentTick: Long): TextColor {
        val target = if (targetVal.isNaN()) 0.0 else targetVal.coerceIn(0.0, 1.0)
        val targetByte = (target * 255.0).roundToInt().coerceIn(0, 255)
        val currentToByte = (vTo * 255.0).roundToInt().coerceIn(0, 255)
        if (lastTarget < 0.0) {
            lastTarget = target
            vFrom = target
            vTo = target
            startTick = currentTick
        } else if (targetByte != currentToByte) {
            if (duration > 0) {
                val elapsed = (currentTick - startTick).coerceAtLeast(0)
                val progress = (elapsed.toDouble() / duration).coerceIn(0.0, 1.0)
                val currentVisual = vFrom + (vTo - vFrom) * easing.ease(progress)
                vFrom = currentVisual
            } else {
                vFrom = target
            }
            vTo = target
            startTick = currentTick
            lastTarget = target
        } else if (vFrom != vTo && duration > 0 && currentTick - startTick >= duration) {
            vFrom = vTo
        }
        val tStartByte = ((startTick % 240).toInt()).coerceIn(0, 239)
        val fromByte = (vFrom * 255.0).roundToInt().coerceIn(0, 255)
        val toByte = (vTo * 255.0).roundToInt().coerceIn(0, 255)
        return TextColor.color(tStartByte, fromByte, toByte)
    }

    val currentTarget: Double
        get() = lastTarget

    val fromVal: Double
        get() = vFrom

    val toVal: Double
        get() = vTo

    val startTickVal: Long
        get() = startTick
}
