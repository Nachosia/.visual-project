package com.visualproject.client.texture

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object MaskedTextureConversions {
    fun buildWhiteMask(sourceImage: BufferedImage): BufferedImage {
        val background = sampleBackgroundColor(sourceImage)
        val width = sourceImage.width.coerceAtLeast(1)
        val height = sourceImage.height.coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = sourceImage.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha <= 0) {
                    output.setRGB(x, y, 0)
                    continue
                }

                val red = (argb ushr 16) and 0xFF
                val green = (argb ushr 8) and 0xFF
                val blue = argb and 0xFF
                if (red == 0 && green == 0 && blue == 0) {
                    output.setRGB(x, y, 0)
                    continue
                }

                val intensity = extractIntensity(red, green, blue, background)
                val extractedAlpha = extractAlpha(alpha, red, green, blue, background)
                if (extractedAlpha <= 0) {
                    output.setRGB(x, y, 0)
                    continue
                }

                val finalAlpha = ((extractedAlpha / 255f) * intensity * 255f).roundToInt().coerceIn(0, 255)
                output.setRGB(x, y, if (finalAlpha <= 0) 0 else ((finalAlpha shl 24) or 0x00FFFFFF))
            }
        }

        return output
    }

    fun buildGrayscaleMask(sourceImage: BufferedImage): BufferedImage {
        val background = sampleBackgroundColor(sourceImage)
        return transform(sourceImage, background) { intensity, alpha ->
            if (alpha <= 0) {
                0
            } else {
                val channel = (intensity * 255f).roundToInt().coerceIn(0, 255)
                (alpha shl 24) or (channel shl 16) or (channel shl 8) or channel
            }
        }
    }

    fun buildTintedMask(sourceImage: BufferedImage, tintColor: Int): BufferedImage {
        val background = sampleBackgroundColor(sourceImage)
        val tintRed = (tintColor ushr 16) and 0xFF
        val tintGreen = (tintColor ushr 8) and 0xFF
        val tintBlue = tintColor and 0xFF
        return transform(sourceImage, background) { intensity, alpha ->
            if (alpha <= 0) {
                0
            } else {
                val red = (tintRed * intensity).roundToInt().coerceIn(0, 255)
                val green = (tintGreen * intensity).roundToInt().coerceIn(0, 255)
                val blue = (tintBlue * intensity).roundToInt().coerceIn(0, 255)
                (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }

    private inline fun transform(
        sourceImage: BufferedImage,
        background: BackgroundColor,
        pixel: (intensity: Float, alpha: Int) -> Int,
    ): BufferedImage {
        val width = sourceImage.width.coerceAtLeast(1)
        val height = sourceImage.height.coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = sourceImage.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha <= 0) {
                    output.setRGB(x, y, 0)
                    continue
                }

                val red = (argb ushr 16) and 0xFF
                val green = (argb ushr 8) and 0xFF
                val blue = argb and 0xFF
                val intensity = extractIntensity(red, green, blue, background)
                val extractedAlpha = extractAlpha(alpha, red, green, blue, background)
                output.setRGB(x, y, pixel(intensity, extractedAlpha))
            }
        }

        return output
    }

    private fun extractIntensity(red: Int, green: Int, blue: Int, background: BackgroundColor): Float {
        val brightness = max(max(red, green), blue) / 255f
        val backgroundBrightness = background.maxChannel / 255f
        val relativeBrightness = ((brightness - backgroundBrightness) / (1f - backgroundBrightness).coerceAtLeast(0.001f))
            .coerceIn(0f, 1f)
        return relativeBrightness.pow(0.92f)
    }

    private fun extractAlpha(alpha: Int, red: Int, green: Int, blue: Int, background: BackgroundColor): Int {
        val rgbDistance = colorDistance(red, green, blue, background.red, background.green, background.blue)
        val backgroundContrast = (rgbDistance / MAX_RGB_DISTANCE).toFloat().coerceIn(0f, 1f)
        val brightnessDelta = (max(max(red, green), blue) - background.maxChannel).coerceAtLeast(0) / 255f
        val signal = max(backgroundContrast, brightnessDelta)
        if (signal <= 0.02f) return 0
        val normalized = smoothStep(0.02f, 0.16f, signal).pow(0.80f)
        return (alpha * normalized).roundToInt().coerceIn(0, 255)
    }

    private fun sampleBackgroundColor(sourceImage: BufferedImage): BackgroundColor {
        val width = sourceImage.width.coerceAtLeast(1)
        val height = sourceImage.height.coerceAtLeast(1)
        var totalWeight = 0f
        var totalRed = 0f
        var totalGreen = 0f
        var totalBlue = 0f

        fun sample(x: Int, y: Int) {
            val argb = sourceImage.getRGB(x, y)
            val alpha = ((argb ushr 24) and 0xFF) / 255f
            val weight = max(0.05f, alpha)
            totalRed += ((argb ushr 16) and 0xFF) * weight
            totalGreen += ((argb ushr 8) and 0xFF) * weight
            totalBlue += (argb and 0xFF) * weight
            totalWeight += weight
        }

        for (x in 0 until width) {
            sample(x, 0)
            sample(x, height - 1)
        }
        for (y in 1 until height - 1) {
            sample(0, y)
            sample(width - 1, y)
        }

        val safeWeight = totalWeight.coerceAtLeast(1f)
        val red = (totalRed / safeWeight).roundToInt().coerceIn(0, 255)
        val green = (totalGreen / safeWeight).roundToInt().coerceIn(0, 255)
        val blue = (totalBlue / safeWeight).roundToInt().coerceIn(0, 255)
        return BackgroundColor(red, green, blue)
    }

    private fun colorDistance(redA: Int, greenA: Int, blueA: Int, redB: Int, greenB: Int, blueB: Int): Double {
        val dr = abs(redA - redB).toDouble()
        val dg = abs(greenA - greenB).toDouble()
        val db = abs(blueA - blueB).toDouble()
        return sqrt((dr * dr) + (dg * dg) + (db * db))
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - (2f * t))
    }

    private data class BackgroundColor(
        val red: Int,
        val green: Int,
        val blue: Int,
    ) {
        val maxChannel: Int = max(max(red, green), blue)
    }

    private const val MAX_RGB_DISTANCE = 441.6729559300637
}
