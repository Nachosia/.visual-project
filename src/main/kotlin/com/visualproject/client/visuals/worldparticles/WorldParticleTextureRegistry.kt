package com.visualproject.client.visuals.worldparticles

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.VisualFileSystem
import com.visualproject.client.texture.MaskedTextureConversions
import com.visualproject.client.texture.NonDumpableDynamicTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
object WorldParticleTextureRegistry {
    data class ParticleTexture(
        val textureId: Identifier,
        val textureWidth: Int,
        val textureHeight: Int,
        val colorRgb: Int,
        val useCutout: Boolean,
    )

    private data class BuiltinParticleTexture(
        val textureId: Identifier,
        val useCutout: Boolean,
    )

    private val builtinTextures = mapOf(
        WorldParticlesModule.ParticleType.SPARK to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/spark.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.SUN to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/sun.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.SNOWFLAKE to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/snowflake.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.PAYMENTS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/payments.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.DOLLAR to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/dollar.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.HEART to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/heart.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.WATER_DROP to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/water_drop.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.STAR to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/star.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.MOON to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/moon.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.BOLT to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/bolt.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.NEARBY to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/nearby.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.BLINK to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/blink.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.CORON to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/coron.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.FIREFLY to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/firefly.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.FLAME to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/flame.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.GEOMETRIC to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/geometric.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.VIRUS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/virus.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.AMONGUS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/amongus.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.BLOOM to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/bloom/bloom.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.GLYPH to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/glyph/circle.png"),
            true,
        ),
        WorldParticlesModule.ParticleType.GLYPH_ALT to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/glyph_alt/circle.png"),
            true,
        ),
    )

    private val dynamicTextures = LinkedHashMap<String, ParticleTexture>()

    fun resolveTexture(client: Minecraft): ParticleTexture? {
        val type = WorldParticlesModule.ParticleType.fromId(
            ModuleStateStore.getTextSetting(
                WorldParticlesModule.particleTypeKey,
                WorldParticlesModule.ParticleType.WATER_DROP.id,
            )
        )
        val tintColor = resolveTintColor()
        val customFile = ModuleStateStore.getTextSetting(WorldParticlesModule.customFileKey, "")
        return resolveTexture(client, type, tintColor, customFile)
    }

    fun resolveTexture(
        client: Minecraft,
        type: WorldParticlesModule.ParticleType,
        tintColor: Int,
        customFile: String = "",
    ): ParticleTexture? {
        if (type != WorldParticlesModule.ParticleType.CUSTOM) {
            return resolveBuiltinTexture(client, type, tintColor)
        }
        return resolveCustomTexture(client, tintColor, customFile)
    }

    private fun resolveBuiltinTexture(
        client: Minecraft,
        type: WorldParticlesModule.ParticleType,
        tintColor: Int,
    ): ParticleTexture? {
        val source = builtinTextures[type] ?: return null
        val signature = "builtin|${source.textureId}|${tintColor.toUInt().toString(16)}"
        dynamicTextures[signature]?.let { return it }

        val sourceImage = client.resourceManager.open(source.textureId).use { input -> ImageIO.read(input) } ?: return null
        return resolveTintedTexture(
            client = client,
            sourceSignature = signature,
            sourceImage = sourceImage,
            tintColor = tintColor,
            strengthenAlpha = true,
        )
    }

    private fun resolveCustomTexture(client: Minecraft, tintColor: Int, customFile: String): ParticleTexture? {
        val path = resolveCustomPath(customFile) ?: return null
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
        dynamicTextures[sourceSignature]?.let { return it }

        val tintedImage = tintMaskedImage(sourceImage, tintColor, strengthenAlpha)
        val textureId = Identifier.fromNamespaceAndPath(
            "visualclient",
            "world_particles/tinted_${sourceSignature.hashCode().toUInt().toString(16)}",
        )
        client.textureManager.register(
            textureId,
            NonDumpableDynamicTexture({ "visualclient-world-particle" }, nativeImageFromBuffered(tintedImage), false),
        )
        val texture = ParticleTexture(
            textureId = textureId,
            textureWidth = tintedImage.width.coerceAtLeast(1),
            textureHeight = tintedImage.height.coerceAtLeast(1),
            colorRgb = 0x00FFFFFF,
            useCutout = false,
        )
        dynamicTextures[sourceSignature] = texture
        return texture
    }

    private fun resolveCustomPath(customFile: String): Path? {
        val requested = customFile
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
        return if (strengthenAlpha || isFullyOpaque(sourceImage)) {
            tintAlphaImage(MaskedTextureConversions.buildWhiteMask(sourceImage), tintColor)
        } else {
            tintAlphaImage(sourceImage, tintColor)
        }
    }

    private fun tintAlphaImage(sourceImage: BufferedImage, tintColor: Int): BufferedImage {
        val width = sourceImage.width.coerceAtLeast(1)
        val height = sourceImage.height.coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val r = (tintColor ushr 16) and 0xFF
        val g = (tintColor ushr 8) and 0xFF
        val b = tintColor and 0xFF

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = sourceImage.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                output.setRGB(
                    x,
                    y,
                    if (alpha <= 0) 0 else ((alpha shl 24) or (r shl 16) or (g shl 8) or b),
                )
            }
        }

        return output
    }

    private fun isFullyOpaque(sourceImage: BufferedImage): Boolean {
        for (y in 0 until sourceImage.height) {
            for (x in 0 until sourceImage.width) {
                if (((sourceImage.getRGB(x, y) ushr 24) and 0xFF) != 255) {
                    return false
                }
            }
        }
        return true
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
