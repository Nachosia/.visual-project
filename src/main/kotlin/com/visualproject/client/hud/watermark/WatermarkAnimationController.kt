package com.visualproject.client.hud.watermark

import kotlin.math.exp

class WatermarkAnimationController {

    private var expansionProgress = 0f
    private var marqueeOffset = 0f
    private var lastFrameNanos = System.nanoTime()

    data class AnimationSnapshot(
        val expansion: Float,
        val marqueePx: Float,
        val deltaSeconds: Float,
    )

    fun tick(targetExpanded: Float, marqueeActive: Boolean, marqueeCyclePx: Float): AnimationSnapshot {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        expansionProgress = smoothStep(expansionProgress, targetExpanded.coerceIn(0f, 1f), 14f, dt)

        marqueeOffset = if (marqueeActive && marqueeCyclePx > 1f) {
            (marqueeOffset + (WatermarkHudTheme.marqueeSpeedPxPerSecond * dt)).mod(marqueeCyclePx)
        } else {
            0f
        }

        return AnimationSnapshot(
            expansion = expansionProgress,
            marqueePx = marqueeOffset,
            deltaSeconds = dt,
        )
    }

    fun currentExpansion(): Float = expansionProgress

    private fun smoothStep(current: Float, target: Float, speed: Float, dt: Float): Float {
        val factor = (1f - exp((-speed * dt).toDouble())).toFloat()
        return current + ((target - current) * factor)
    }
}
