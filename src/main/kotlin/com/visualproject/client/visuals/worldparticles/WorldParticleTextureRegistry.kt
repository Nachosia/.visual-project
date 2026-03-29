package com.visualproject.client.visuals.worldparticles

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.VisualFileSystem
import com.visualproject.client.texture.NonDumpableDynamicTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.pow

object WorldParticleTextureRegistry {
    data class ParticleTexture(
        val textureId: Identifier,
        val textureWidth: Int,
        val textureHeight: Int,
        val colorRgb: Int,
        val useCutout: Boolean,
    )

    private data class DynamicTextureEntry(
        val signature: String,
        val texture: ParticleTexture,
    )

    private val tintedTextureId = Identifier.fromNamespaceAndPath("visualclient", "world_particles/tinted_runtime")

    private val builtinTextures = mapOf(
        WorldParticlesModule.ParticleType.SPARK to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/spark.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.SUN to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/sun.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.SNOWFLAKE to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/snowflake.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.PAYMENTS to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/payments.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.DOLLAR to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/dollar.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.HEART to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/heart.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.WATER_DROP to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/water_drop.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.STAR to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/star.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.MOON to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/moon.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.BOLT to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/bolt.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
        WorldParticlesModule.ParticleType.NEARBY to ParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/nearby.png"),
            512,
            512,
            0x00FFFFFF,
            true,
        ),
    )

    private var dynamicEntry: DynamicTextureEntry? = null

    fun resolveTexture(client: Minecraft): ParticleTexture? {
        val type = WorldParticlesModule.ParticleType.fromId(
            ModuleStateStore.getTextSetting(
                WorldParticlesModule.particleTypeKey,
                WorldParticlesModule.ParticleType.WATER_DROP.id,
            )
        )
        val tintColor = resolveTintColor()
        if (type != WorldParticlesModule.ParticleType.CUSTOM) {
            val source = builtinTextures[type] ?: return null
            return ParticleTexture(
                textureId = source.textureId,
                textureWidth = source.textureWidth,
                textureHeight = source.textureHeight,
                colorRgb = tintColor and 0x00FFFFFF,
                useCutout = source.useCutout,
            )
        }
        return resolveCustomTexture(client, tintColor)
    }

    private fun resolveCustomTexture(client: Minecraft, tintColor: Int): ParticleTexture? {
        val path = resolveCustomPath() ?: return null
        val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        val image = Files.newInputStream(path).use { input -> ImageIO.read(input) } ?: return null
        return resolveTintedTexture(
            client = client,
            sourceSignature = "${path.toAbsolutePath().normalize()}|$modified|${tintColor.toUInt().toString(16)}",
            sourceImage = image,
            tintColor = tintColor,
            strengthenAlpha = false,
        )
    }

    private fun resolveTintedTexture(
        client: Minecraft,
        sourceSignature: String,
        sourceImage: BufferedImage,
        tintColor: Int,
        strengthenAlpha: Boolean,
    ): ParticleTexture {
        dynamicEntry?.takeIf { it.signature == sourceSignature }?.let { return it.texture }

        val tintedImage = tintMaskedImage(sourceImage, tintColor, strengthenAlpha)
        client.textureManager.register(
            tintedTextureId,
            NonDumpableDynamicTexture({ "visualclient-world-particle" }, nativeImageFromBuffered(tintedImage), false),
        )
        val texture = ParticleTexture(
            textureId = tintedTextureId,
            textureWidth = tintedImage.width.coerceAtLeast(1),
            textureHeight = tintedImage.height.coerceAtLeast(1),
            colorRgb = 0x00FFFFFF,
            useCutout = false,
        )
        dynamicEntry = DynamicTextureEntry(sourceSignature, texture)
        return texture
    }

    private fun resolveCustomPath(): Path? {
        val requested = ModuleStateStore.getTextSetting(WorldParticlesModule.customFileKey, "")
            .trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val files = listCustomParticleFiles()
        if (files.isEmpty()) return null
        if (requested.isBlank()) return files.first()

        return files.firstOrNull { it.fileName.toString().equals(requested, ignoreCase = true) }
            ?: files.firstOrNull { it.fileName.toString().substringBeforeLast('.').equals(requested, ignoreCase = true) }
            ?: files.first()
    }

    fun listCustomParticleFiles(): List<Path> {
        return runCatching {
            Files.list(VisualFileSystem.particleDir()).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
                        extension == "png" || extension == "jpg" || extension == "jpeg"
                    }
                    .sorted { left, right -> left.fileName.toString().compareTo(right.fileName.toString(), ignoreCase = true) }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun nativeImageFromBuffered(image: BufferedImage): NativeImage {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input ->
            NativeImage.read(input)
        }
    }

    private fun tintMaskedImage(sourceImage: BufferedImage, tintColor: Int, strengthenAlpha: Boolean): BufferedImage {
        val width = sourceImage.width.coerceAtLeast(1)
        val height = sourceImage.height.coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val r = (tintColor ushr 16) and 0xFF
        val g = (tintColor ushr 8) and 0xFF
        val b = tintColor and 0xFF

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = sourceImage.getRGB(x, y)
                val alpha = boostAlpha((argb ushr 24) and 0xFF, strengthenAlpha)
                output.setRGB(
                    x,
                    y,
                    if (alpha <= 0) 0 else ((alpha shl 24) or (r shl 16) or (g shl 8) or b),
                )
            }
        }

        return output
    }

    private fun boostAlpha(alpha: Int, strengthenAlpha: Boolean): Int {
        if (!strengthenAlpha || alpha <= 0) return alpha
        val normalized = (alpha / 255.0).coerceIn(0.0, 1.0)
        return (normalized.pow(0.55) * 255.0).toInt().coerceIn(0, 255)
    }

    private fun resolveTintColor(): Int {
        return if (ModuleStateStore.isSettingEnabled(WorldParticlesModule.clientColorKey)) {
            VisualThemeSettings.accentStrong()
        } else {
            parseColor(ModuleStateStore.getTextSetting(WorldParticlesModule.customColorKey, "#B31284"), 0xFFB31284.toInt())
        }
    }

    private fun parseColor(raw: String, fallback: Int): Int {
        val compact = raw.trim().removePrefix("#")
        val parsed = when (compact.length) {
            6 -> compact.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
            8 -> compact.toLongOrNull(16)?.toInt()
            else -> null
        }
        return parsed ?: fallback
    }
}
