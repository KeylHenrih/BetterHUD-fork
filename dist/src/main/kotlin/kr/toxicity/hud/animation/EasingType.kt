package kr.toxicity.hud.animation

enum class EasingType {
    LINEAR {
        override fun ease(t: Double): Double = t
        override val glslFunction: String = "easeLinear"
    },
    EASE_OUT_CUBIC {
        override fun ease(t: Double): Double {
            val f = t - 1.0
            return f * f * f + 1.0
        }
        override val glslFunction: String = "easeOutCubic"
    },
    EASE_IN_OUT_CUBIC {
        override fun ease(t: Double): Double = if (t < 0.5) 4.0 * t * t * t else {
            val f = 2.0 * t - 2.0
            (t - 1.0) * f * f + 1.0
        }
        override val glslFunction: String = "easeInOutCubic"
    },
    EASE_OUT_QUAD {
        override fun ease(t: Double): Double = t * (2.0 - t)
        override val glslFunction: String = "easeOutQuad"
    },
    EASE_IN_OUT_QUAD {
        override fun ease(t: Double): Double = if (t < 0.5) 2.0 * t * t else -1.0 + (4.0 - 2.0 * t) * t
        override val glslFunction: String = "easeInOutQuad"
    },
    EASE_OUT_SINE {
        override fun ease(t: Double): Double = kotlin.math.sin((t * kotlin.math.PI) / 2.0)
        override val glslFunction: String = "easeOutSine"
    },
    EASE_IN_OUT_SINE {
        override fun ease(t: Double): Double = -(kotlin.math.cos(kotlin.math.PI * t) - 1.0) / 2.0
        override val glslFunction: String = "easeInOutSine"
    };

    abstract fun ease(t: Double): Double
    abstract val glslFunction: String

    companion object {
        fun fromString(str: String?): EasingType = when (str?.lowercase()?.replace("-", "_")?.trim()) {
            "linear" -> LINEAR
            "ease_out_cubic", "easeoutcubic" -> EASE_OUT_CUBIC
            "ease_in_out_cubic", "easeinoutcubic" -> EASE_IN_OUT_CUBIC
            "ease_out_quad", "easeoutquad" -> EASE_OUT_QUAD
            "ease_in_out_quad", "easeinoutquad" -> EASE_IN_OUT_QUAD
            "ease_out_sine", "easeoutsine" -> EASE_OUT_SINE
            "ease_in_out_sine", "easeinoutsine" -> EASE_IN_OUT_SINE
            else -> EASE_OUT_CUBIC
        }
    }
}
