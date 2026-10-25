package kr.toxicity.hud.animation

data class ClientAnimation(
    val duration: Int,
    val xGlsl: String,
    val yGlsl: String,
    val opacityGlsl: String,
    val type: AnimationType
) : Comparable<ClientAnimation> {
    companion object {
        private val comparator = Comparator.comparingInt { a: ClientAnimation ->
            a.duration
        }.thenComparing { a: ClientAnimation ->
            a.xGlsl
        }.thenComparing { a: ClientAnimation ->
            a.yGlsl
        }.thenComparing { a: ClientAnimation ->
            a.opacityGlsl
        }.thenComparing { a: ClientAnimation ->
            a.type
        }
    }

    override fun compareTo(other: ClientAnimation): Int {
        return comparator.compare(this, other)
    }
}
