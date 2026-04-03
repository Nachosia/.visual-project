package com.visualproject.client.visuals.jumpcircle

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.texture.NonDumpableDynamicTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.sqrt

object JumpCircleTextureRegistry {
    private const val ringTextureSize = 512

    val whitePixelTextureId: Identifier = Identifier.fromNamespaceAndPath("visualclient", "textures/misc/white_pixel.png")
    private val ringTextureId = Identifier.fromNamespaceAndPath("visualclient", "jump_circle/ring_runtime")

    private var ringReady = false

    fun resolveRingTexture(client: Minecraft): Identifier {
        if (!ringReady) {
            client.textureManager.register(
                ringTextureId,
                NonDumpableDynamicTexture({ "visualclient-jump-circle-ring" }, nativeImageFromBuffered(buildRingTexture()), false),
            )
            ringReady = true
        }
        return ringTextureId
    }

    private fun buildRingTexture(): BufferedImage {
        val image = BufferedImage(ringTextureSize, ringTextureSize, BufferedImage.TYPE_INT_ARGB)
        val center = (ringTextureSize - 1) * 0.5f
        val radius = center.coerceAtLeast(1f)

        for (y in 0 until ringTextureSize) {
            for (x in 0 until ringTextureSize) {
                val dx = (x - center) / radius
                val dy = (y - center) / radius
                val distance = sqrt((dx * dx) + (dy * dy))
                image.setRGB(x, y, packWhite(ringAlpha(distance)))
            }
        }

        return image
    }

    private fun ringAlpha(distance: Float): Int {
        val innerFeather = smoothStep(0.76f, 0.79f, distance)
        val outerFeather = 1f - smoothStep(0.88f, 0.91f, distance)
        return ((innerFeather * outerFeather).coerceIn(0f, 1f) * 224f).toInt().coerceIn(0, 255)
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - (2f * t))
    }

    private fun packWhite(alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or 0x00FFFFFF
    }

    private fun nativeImageFromBuffered(image: BufferedImage): NativeImage {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input ->
            NativeImage.read(input)
        }
    }
}
