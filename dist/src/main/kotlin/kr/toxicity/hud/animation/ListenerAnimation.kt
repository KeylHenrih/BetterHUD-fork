package kr.toxicity.hud.animation

import kr.toxicity.hud.image.enums.SplitType

data class ListenerAnimation(
    val splitType: SplitType,
    val duration: Int,
    val easing: EasingType
) : Comparable<ListenerAnimation> {
    companion object {
        private val comparator = Comparator.comparingInt { a: ListenerAnimation ->
            a.splitType.ordinal
        }.thenComparingInt { a: ListenerAnimation ->
            a.duration
        }.thenComparing { a: ListenerAnimation ->
            a.easing
        }
    }

    override fun compareTo(other: ListenerAnimation): Int = comparator.compare(this, other)
}
