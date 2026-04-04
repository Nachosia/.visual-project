package com.visualproject.client.visuals.jumpcircle

enum class CircleInterpolationPreset(
    val id: String,
    val label: String,
) {
    LINEAR("linear", "Linear") {
        override fun interpolate(progress: Float): Float = progress
    },
    SMOOTH("smooth", "Smooth") {
        override fun interpolate(progress: Float): Float = progress * progress
    },
    FAST("fast", "Fast") {
        override fun interpolate(progress: Float): Float = 1f - ((1f - progress) * (1f - progress))
    },
    BALANCED("balanced", "Balanced") {
        override fun interpolate(progress: Float): Float {
            return if (progress < 0.5f) {
                2f * progress * progress
            } else {
                1f - (((-2f * progress) + 2f) * ((-2f * progress) + 2f) * 0.5f)
            }
        }
    },
    BACK("back", "Back") {
        override fun interpolate(progress: Float): Float {
            val shifted = progress - 1f
            return (1f + (2.70158f * shifted * shifted * shifted) + (1.70158f * shifted * shifted))
        }
    },
    OVERSHOOT("overshoot", "Overshoot") {
        override fun interpolate(progress: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            return (c3 * progress * progress * progress) - (c1 * progress * progress)
        }
    },
    ELASTIC("elastic", "Elastic") {
        override fun interpolate(progress: Float): Float {
            if (progress <= 0f) return 0f
            if (progress >= 1f) return 1f
            val c4 = ((2.0 * Math.PI) / 3.0).toFloat()
            return ((Math.pow(2.0, (-10f * progress).toDouble()) * kotlin.math.sin(((progress * 10f) - 0.75f) * c4)) + 1.0).toFloat()
        }
    },
    BOUNCE("bounce", "Bounce") {
        override fun interpolate(progress: Float): Float {
            val n1 = 7.5625f
            val d1 = 2.75f
            var p = progress
            return when {
                p < 1f / d1 -> n1 * p * p
                p < 2f / d1 -> {
                    p -= 1.5f / d1
                    (n1 * p * p) + 0.75f
                }
                p < 2.5f / d1 -> {
                    p -= 2.25f / d1
                    (n1 * p * p) + 0.9375f
                }
                else -> {
                    p -= 2.625f / d1
                    (n1 * p * p) + 0.984375f
                }
            }
        }
    },
    EASE_OUT("ease_out", "Ease Out") {
        override fun interpolate(progress: Float): Float = 1f - ((1f - progress) * (1f - progress) * (1f - progress))
    },
    SPRING("spring", "Spring") {
        override fun interpolate(progress: Float): Float {
            val damping = 0.7
            val frequency = 1.5
            val decay = Math.exp((-damping * frequency) * progress.toDouble())
            val oscillation = kotlin.math.cos((frequency * progress * Math.PI * 2.0).toFloat()).toDouble()
            return (1.0 - ((decay * oscillation * 0.1) + (decay * (1.0 - progress)))).toFloat()
        }
    },
    DECELERATE("decelerate", "Decelerate") {
        override fun interpolate(progress: Float): Float = progress * (2f - progress)
    };

    abstract fun interpolate(progress: Float): Float

    companion object {
        fun fromId(raw: String): CircleInterpolationPreset {
            return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: LINEAR
        }
    }
}

data class ThreeStageAnimation(
    val appearDuration: Float,
    val existDuration: Float,
    val disappearDuration: Float,
    val appearInterpolation: CircleInterpolationPreset,
    val disappearInterpolation: CircleInterpolationPreset,
) {
    val totalDuration: Float = appearDuration + existDuration + disappearDuration

    fun value(elapsedSeconds: Float): Float {
        if (elapsedSeconds >= totalDuration) return 0f
        if (elapsedSeconds <= appearDuration) {
            val progress = (elapsedSeconds / appearDuration.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
            return appearInterpolation.interpolate(progress)
        }
        if (elapsedSeconds <= appearDuration + existDuration) {
            return 1f
        }
        val progress = ((elapsedSeconds - appearDuration - existDuration) / disappearDuration.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
        return 1f - disappearInterpolation.interpolate(progress)
    }

    fun stage(elapsedSeconds: Float): Stage {
        return when {
            elapsedSeconds <= appearDuration -> Stage.APPEAR
            elapsedSeconds <= appearDuration + existDuration -> Stage.EXIST
            elapsedSeconds <= totalDuration -> Stage.DISAPPEAR
            else -> Stage.FINISHED
        }
    }

    enum class Stage {
        APPEAR,
        EXIST,
        DISAPPEAR,
        FINISHED,
    }
}
