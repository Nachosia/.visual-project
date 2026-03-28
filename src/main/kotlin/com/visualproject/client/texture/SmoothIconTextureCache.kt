package com.visualproject.client.texture

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object SmoothIconTextureCache {
    private data class CacheKey(
        val source: Identifier,
        val width: Int,
        val height: Int,
    )

    private val cache = HashMap<CacheKey, Identifier>()

    @Synchronized
    fun resolve(client: Minecraft, source: Identifier, width: Int, height: Int): Identifier {
        val targetWidth = width.coerceAtLeast(1)
        val targetHeight = height.coerceAtLeast(1)
        val key = CacheKey(source, targetWidth, targetHeight)
        return cache.getOrPut(key) {
            val normalizedPath = source.path
                .replace('/', '_')
                .replace('\\', '_')
                .replace('.', '_')
            val smoothId = Identifier.fromNamespaceAndPath(
                "visualclient",
                "smooth_icons/${source.namespace}_${normalizedPath}_${targetWidth}x${targetHeight}",
            )
            val sourceImage = client.resourceManager.open(source).use { input ->
                ImageIO.read(input)
            } ?: error("Failed to load icon resource: $source")
            val image = resizeToNativeImage(sourceImage, targetWidth, targetHeight)
            client.textureManager.register(
                smoothId,
                NonDumpableDynamicTexture({ "visualclient-smooth-icon" }, image),
            )
            smoothId
        }
    }

    private fun resizeToNativeImage(source: BufferedImage, width: Int, height: Int): NativeImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        target.createGraphics().use { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, width, height, null)
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(target, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input ->
            NativeImage.read(input)
        }
    }

    private inline fun Graphics2D.use(block: (Graphics2D) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
