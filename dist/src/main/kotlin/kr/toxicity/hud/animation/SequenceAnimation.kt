package kr.toxicity.hud.animation

data class SequenceAnimation(
    val frameIndex: Int,
    val totalFrames: Int
) : Comparable<SequenceAnimation> {
    companion object {
        private val comparator = Comparator.comparingInt<SequenceAnimation> { it.frameIndex }
            .thenComparingInt { it.totalFrames }
    }

    override fun compareTo(other: SequenceAnimation): Int = comparator.compare(this, other)
}
