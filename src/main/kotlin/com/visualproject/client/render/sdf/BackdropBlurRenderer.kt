package com.visualproject.client.render.sdf

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.visualproject.client.VisualThemeSettings
import net.minecraft.client.Minecraft
import java.util.OptionalDouble

object BackdropBlurRenderer {
    private var snapshotTexture: GpuTexture? = null
    private var snapshotTextureView: GpuTextureView? = null
    private var fallbackTexture: GpuTexture? = null
    private var fallbackTextureView: GpuTextureView? = null
    private var sampler: GpuSampler? = null
    private var snapshotWidth = -1
    private var snapshotHeight = -1

    fun captureBackdrop() {
        if (!VisualThemeSettings.isTransparentPreset()) {
            return
        }

        val client = Minecraft.getInstance()
        val target = client.mainRenderTarget
        val sourceTexture = target.colorTexture ?: return
        val width = sourceTexture.getWidth(0)
        val height = sourceTexture.getHeight(0)
        if (width <= 0 || height <= 0) return

        val device = RenderSystem.getDevice()
        ensureSampler(device)
        ensureSnapshotTexture(device, width, height)

        val destination = snapshotTexture ?: return
        device.createCommandEncoder().copyTextureToTexture(
            sourceTexture,
            destination,
            0,
            0,
            0,
            0,
            0,
            width,
            height,
        )
    }

    fun textureView(): GpuTextureView {
        snapshotTextureView?.let { return it }
        return ensureFallbackTexture()
    }

    fun sampler(): GpuSampler {
        val existing = sampler
        if (existing != null) return existing

        val created = RenderSystem.getDevice().createSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.LINEAR,
            1,
            OptionalDouble.empty(),
        )
        sampler = created
        return created
    }

    private fun ensureSampler(device: com.mojang.blaze3d.systems.GpuDevice) {
        if (sampler == null) {
            sampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty(),
            )
        }
    }

    private fun ensureSnapshotTexture(
        device: com.mojang.blaze3d.systems.GpuDevice,
        width: Int,
        height: Int,
    ) {
        if (snapshotTexture != null && snapshotWidth == width && snapshotHeight == height) {
            return
        }

        snapshotTextureView?.close()
        snapshotTexture?.close()

        snapshotTexture = device.createTexture(
            "visualclient_transparent_backdrop",
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1,
        )
        snapshotTextureView = snapshotTexture?.let(device::createTextureView)
        snapshotWidth = width
        snapshotHeight = height
    }

    private fun ensureFallbackTexture(): GpuTextureView {
        val existing = fallbackTextureView
        if (existing != null) return existing

        val device = RenderSystem.getDevice()
        val texture = device.createTexture(
            "visualclient_transparent_backdrop_fallback",
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            1,
            1,
            1,
            1,
        )
        device.createCommandEncoder().clearColorTexture(texture, 0x00000000)
        fallbackTexture = texture
        fallbackTextureView = device.createTextureView(texture)
        return fallbackTextureView!!
    }
}
