package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import java.util.OptionalDouble

object ShadertoyChannels {
    data class ChannelBinding(
        val textureView: GpuTextureView,
        val sampler: GpuSampler,
        val width: Int,
        val height: Int,
    )

    private var fallbackTexture: GpuTexture? = null
    private var fallbackTextureView: GpuTextureView? = null
    private var fallbackSampler: GpuSampler? = null

    fun fallbackBindings(): List<ChannelBinding> {
        ensureReady()
        val textureView = fallbackTextureView ?: error("Shadertoy fallback texture view is unavailable")
        val sampler = fallbackSampler ?: error("Shadertoy fallback sampler is unavailable")
        return List(4) {
            ChannelBinding(
                textureView = textureView,
                sampler = sampler,
                width = 1,
                height = 1,
            )
        }
    }

    fun invalidate() {
        fallbackTextureView?.close()
        fallbackTextureView = null
        fallbackTexture?.close()
        fallbackTexture = null
        fallbackSampler = null
    }

    private fun ensureReady() {
        if (fallbackTexture != null && fallbackTextureView != null && fallbackSampler != null) {
            return
        }

        val device = RenderSystem.getDevice()
        if (fallbackSampler == null) {
            fallbackSampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty(),
            )
        }

        if (fallbackTexture == null || fallbackTextureView == null) {
            val texture = device.createTexture(
                "visualclient_shadertoy_black_channel",
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                1,
                1,
                1,
                1,
            )
            device.createCommandEncoder().clearColorTexture(texture, 0xFF000000.toInt())
            fallbackTexture = texture
            fallbackTextureView = device.createTextureView(texture)
        }
    }
}
