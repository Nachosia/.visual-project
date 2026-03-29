package com.visualproject.client.visuals.chinahat

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.texture.NonDumpableDynamicTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

object ChinaHatTextureRegistry {
    private const val textureSize = 1024
    private val textureId = Identifier.fromNamespaceAndPath("visualclient", "china_hat/fill_runtime")
    private var signature: String? = null

    fun resolveTexture(client: Minecraft): Identifier {
        val primary = ChinaHatModule.color() and 0x00FFFFFF
        val secondary = ChinaHatModule.gradientColor() and 0x00FFFFFF
        val gradientEnabled = ChinaHatModule.gradientEnabled()
        val nextSignature = "$primary|$secondary|$gradientEnabled"

        if (signature != nextSignature) {
            val image = buildTexture(primary, secondary, gradientEnabled)
            client.textureManager.register(
                textureId,
                NonDumpableDynamicTexture({ "visualclient-china-hat" }, nativeImageFromBuffered(image), true),
            )
            signature = nextSignature
        }

        return textureId
    }

    private fun buildTexture(primaryColor: Int, secondaryColor: Int, gradientEnabled: Boolean): BufferedImage {
        val image = BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB)
        val center = (textureSize - 1) * 0.5f
        val radius = center.coerceAtLeast(1f)

        for (y in 0 until textureSize) {
            for (x in 0 until textureSize) {
                val normalizedX = (x - center) / radius
                val normalizedY = (y - center) / radius
                val angle = atan2(normalizedY, normalizedX)
                val rgb = if (gradientEnabled) {
                    blendColor(
                        primaryColor,
                        secondaryColor,
                        (0.5f + (0.5f * sin(angle))).coerceIn(0f, 1f),
                    )
                } else {
                    primaryColor
                }
                image.setRGB(x, y, 0xFF000000.toInt() or rgb)
            }
        }

        return image
    }

    private fun nativeImageFromBuffered(image: BufferedImage): NativeImage {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input -> NativeImage.read(input) }
    }

    private fun blendColor(startColor: Int, endColor: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val startR = (startColor shr 16) and 0xFF
        val startG = (startColor shr 8) and 0xFF
        val startB = startColor and 0xFF
        val endR = (endColor shr 16) and 0xFF
        val endG = (endColor shr 8) and 0xFF
        val endB = endColor and 0xFF
        val r = (startR + ((endR - startR) * t)).toInt().coerceIn(0, 255)
        val g = (startG + ((endG - startG) * t)).toInt().coerceIn(0, 255)
        val b = (startB + ((endB - startB) * t)).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
