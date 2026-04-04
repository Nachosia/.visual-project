package com.visualproject.client.visuals.worldparticles

import kotlin.math.floor
import kotlin.math.roundToInt

object WorldParticleColorUtil {
    fun multAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF)
        val scaledAlpha = (alpha * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (scaledAlpha shl 24) or (color and 0x00FFFFFF)
    }

    fun overColor(startColor: Int, endColor: Int, progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return color(
            lerp(channel(startColor, 16), channel(endColor, 16), clamped),
            lerp(channel(startColor, 8), channel(endColor, 8), clamped),
            lerp(channel(startColor, 0), channel(endColor, 0), clamped),
            lerp(channel(startColor, 24), channel(endColor, 24), clamped),
        )
    }

    fun waveColor(colors: IntArray, alphaFactor: Float, offset: Int = 0): Int {
        if (colors.isEmpty()) return multAlpha(0xFFFFFFFF.toInt(), alphaFactor)
        if (colors.size == 1) return multAlpha(opaque(colors[0]), alphaFactor)

        if (colors.size == 2) {
            var angle = (((System.currentTimeMillis() / 8L) + offset) % 360L).toInt()
            angle = if (angle >= 180) 360 - angle else angle
            return multAlpha(overColor(opaque(colors[0]), opaque(colors[1]), angle / 180f), alphaFactor)
        }

        val timeProgress = (((System.currentTimeMillis() / 10L) + offset) % (colors.size * 360L)).toFloat() / 360f
        val index1 = floor(timeProgress).toInt().mod(colors.size)
        val index2 = (index1 + 1).mod(colors.size)
        val blend = timeProgress - floor(timeProgress)
        return multAlpha(overColor(opaque(colors[index1]), opaque(colors[index2]), blend), alphaFactor)
    }

    fun vertexGradientColor(angleOffset: Int, colors: IntArray, alphaFactor: Float): Int {
        if (colors.isEmpty()) return multAlpha(0xFFFFFFFF.toInt(), alphaFactor)
        if (colors.size == 1) return multAlpha(opaque(colors[0]), alphaFactor)

        var angle = (((System.currentTimeMillis() / 8L) + angleOffset) % 360L).toInt()
        if (colors.size == 2) {
            angle = if (angle >= 180) 360 - angle else angle
            return multAlpha(overColor(opaque(colors[0]), opaque(colors[1]), angle / 180f), alphaFactor)
        }

        val progress = angle / 360f
        val colorIndex = progress * colors.size
        val index1 = floor(colorIndex).toInt().mod(colors.size)
        val index2 = (index1 + 1).mod(colors.size)
        val blend = colorIndex - floor(colorIndex)
        return multAlpha(overColor(opaque(colors[index1]), opaque(colors[index2]), blend), alphaFactor)
    }

    private fun opaque(color: Int): Int {
        return if (((color ushr 24) and 0xFF) == 0) {
            0xFF000000.toInt() or (color and 0x00FFFFFF)
        } else {
            color
        }
    }

    private fun color(red: Int, green: Int, blue: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255))
    }

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

    private fun lerp(start: Int, end: Int, progress: Float): Int {
        return (start + ((end - start) * progress)).roundToInt()
    }
}
