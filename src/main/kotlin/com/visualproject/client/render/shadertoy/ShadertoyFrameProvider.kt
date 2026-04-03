package com.visualproject.client.render.shadertoy

import com.visualproject.client.visuals.hitbox.HitboxCustomizerModule
import net.minecraft.client.Minecraft

object ShadertoyFrameProvider {
    private data class FrameRequestKey(
        val program: ShadertoyProgramRegistry.ProgramDefinition,
        val qualityPreset: OffscreenShaderRenderer.QualityPreset,
        val timeScaleBits: Int,
    )

    private val renderers = LinkedHashMap<ShadertoyProgramRegistry.ProgramDefinition, OffscreenShaderRenderer>()
    private val frameCache = HashMap<FrameRequestKey, OffscreenShaderRenderer.ShadertoyFrame?>()
    private var cachedFrameMarker = Long.MIN_VALUE

    fun currentFrame(
        client: Minecraft,
        program: ShadertoyProgramRegistry.ProgramDefinition,
        qualityPreset: OffscreenShaderRenderer.QualityPreset,
        timeScale: Float,
    ): OffscreenShaderRenderer.ShadertoyFrame? {
        val frameMarker = currentFrameMarker(client)
        if (frameMarker != cachedFrameMarker) {
            cachedFrameMarker = frameMarker
            frameCache.clear()
        }

        val requestKey = FrameRequestKey(
            program = program,
            qualityPreset = qualityPreset,
            timeScaleBits = java.lang.Float.floatToIntBits(timeScale),
        )
        frameCache[requestKey]?.let { return it }

        val renderer = renderers.getOrPut(program) {
            OffscreenShaderRenderer(program)
        }
        val frame = renderer.renderIfNeeded(
            client = client,
            qualityPreset = qualityPreset,
            timeScale = timeScale,
        )
        frameCache[requestKey] = frame
        return frame
    }

    fun currentThemeFrame(client: Minecraft): OffscreenShaderRenderer.ShadertoyFrame? {
        if (!HitboxCustomizerModule.shaderEnabled()) return null
        val preset = HitboxCustomizerModule.shaderPreset()
        return currentFrame(
            client = client,
            program = preset.program,
            qualityPreset = HitboxCustomizerModule.quality().preset,
            timeScale = HitboxCustomizerModule.shaderSpeed(),
        )
    }

    fun shutdown() {
        frameCache.clear()
        cachedFrameMarker = Long.MIN_VALUE
        renderers.values.forEach(OffscreenShaderRenderer::close)
        renderers.clear()
    }

    private fun currentFrameMarker(client: Minecraft): Long {
        val gameTime = client.level?.gameTime ?: 0L
        val partialTickBits = java.lang.Float.floatToIntBits(
            client.deltaTracker.getGameTimeDeltaPartialTick(false),
        ).toLong() and 0xFFFFFFFFL
        return (gameTime shl 32) xor partialTickBits
    }
}
