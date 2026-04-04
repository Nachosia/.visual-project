package com.visualproject.client.visuals.worldparticles

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
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
    )

    private data class BaseParticleTexture(
        val textureId: Identifier,
        val textureWidth: Int,
        val textureHeight: Int,
        val useCutout: Boolean,
    )

    private val builtinTextures = mapOf(
        WorldParticlesModule.ParticleType.SPARK to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/spark.png"),
        ),
        WorldParticlesModule.ParticleType.SUN to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/sun.png"),
        ),
        WorldParticlesModule.ParticleType.SNOWFLAKE to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/snowflake.png"),
        ),
        WorldParticlesModule.ParticleType.PAYMENTS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/payments.png"),
        ),
        WorldParticlesModule.ParticleType.DOLLAR to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/dollar.png"),
        ),
        WorldParticlesModule.ParticleType.HEART to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/heart.png"),
        ),
        WorldParticlesModule.ParticleType.WATER_DROP to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/water_drop.png"),
        ),
        WorldParticlesModule.ParticleType.STAR to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/star.png"),
        ),
        WorldParticlesModule.ParticleType.MOON to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/moon.png"),
        ),
        WorldParticlesModule.ParticleType.BOLT to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/bolt.png"),
        ),
        WorldParticlesModule.ParticleType.NEARBY to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/world_particles/nearby.png"),
        ),
        WorldParticlesModule.ParticleType.BLINK to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/blink.png"),
        ),
        WorldParticlesModule.ParticleType.CORON to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/coron.png"),
        ),
        WorldParticlesModule.ParticleType.FIREFLY to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/firefly.png"),
        ),
        WorldParticlesModule.ParticleType.FLAME to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/flame.png"),
        ),
        WorldParticlesModule.ParticleType.GEOMETRIC to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/geometric.png"),
        ),
        WorldParticlesModule.ParticleType.VIRUS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/virus.png"),
        ),
        WorldParticlesModule.ParticleType.AMONGUS to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/amongus.png"),
        ),
        WorldParticlesModule.ParticleType.BLOOM to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/bloom/bloom.png"),
        ),
        WorldParticlesModule.ParticleType.GLYPH to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/glyph/circle.png"),
        ),
        WorldParticlesModule.ParticleType.GLYPH_ALT to BuiltinParticleTexture(
            Identifier.fromNamespaceAndPath("visualclient", "textures/soup/particles/glyph_alt/circle.png"),
        ),
    )

    private val dynamicTextures = LinkedHashMap<String, BaseParticleTexture>()

    fun resolveTexture(client: Minecraft): ParticleTexture? {
        val type = WorldParticlesModule.ParticleType.fromId(
            ModuleStateStore.getTextSetting(
                WorldParticlesModule.particleTypeKey,
                WorldParticlesModule.ParticleType.WATER_DROP.id,
            )
        )
        val customFile = ModuleStateStore.getTextSetting(WorldParticlesModule.customFileKey, "")
        return resolveTexture(client, type, resolveTintColor(), customFile)
    }

    fun resolveTexture(
        client: Minecraft,
        type: WorldParticlesModule.ParticleType,
        tintColor: Int,
        customFile: String = "",
    ): ParticleTexture? {
        val baseTexture = if (type == WorldParticlesModule.ParticleType.CUSTOM) {
            resolveCustomTexture(client, customFile)
        } else {
            resolveBuiltinTexture(client, type)
        } ?: return null

        return ParticleTexture(
            textureId = baseTexture.textureId,
            textureWidth = baseTexture.textureWidth,
            textureHeight = baseTexture.textureHeight,
            colorRgb = tintColor and 0x00FFFFFF,
            useCutout = baseTexture.useCutout,
        )
    }

    private fun resolveBuiltinTexture(
        client: Minecraft,
        type: WorldParticlesModule.ParticleType,
    ): BaseParticleTexture? {
        val source = builtinTextures[type] ?: return null
        val signature = "builtin|${source.textureId}"
        dynamicTextures[signature]?.let { return it }

        val sourceImage = client.resourceManager.open(source.textureId).use { input -> ImageIO.read(input) } ?: return null
        return resolveMaskTexture(client, signature, sourceImage)
    }

    private fun resolveCustomTexture(client: Minecraft, customFile: String): BaseParticleTexture? {
        val path = resolveCustomPath(customFile) ?: return null
        val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        val image = Files.newInputStream(path).use { input -> ImageIO.read(input) } ?: return null
        return resolveMaskTexture(client, "${path.toAbsolutePath().normalize()}|$modified", image)
    }

    private fun resolveMaskTexture(
        client: Minecraft,
        sourceSignature: String,
        sourceImage: BufferedImage,
    ): BaseParticleTexture {
        dynamicTextures[sourceSignature]?.let { return it }

        val maskedImage = MaskedTextureConversions.buildWhiteMask(sourceImage)
        val textureId = Identifier.fromNamespaceAndPath(
            "visualclient",
            "world_particles/mask_${sourceSignature.hashCode().toUInt().toString(16)}",
        )
        client.textureManager.register(
            textureId,
            NonDumpableDynamicTexture({ "visualclient-world-particle" }, nativeImageFromBuffered(maskedImage), false),
        )
        val texture = BaseParticleTexture(
            textureId = textureId,
            textureWidth = maskedImage.width.coerceAtLeast(1),
            textureHeight = maskedImage.height.coerceAtLeast(1),
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

    private fun resolveTintColor(): Int {
        return WorldParticlesModule.previewColor()
    }
}
