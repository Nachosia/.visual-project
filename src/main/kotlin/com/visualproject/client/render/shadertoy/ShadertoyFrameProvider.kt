package com.visualproject.client.render.shadertoy

import com.visualproject.client.visuals.hitbox.HitboxCustomizerModule
import net.minecraft.client.Minecraft

object ShadertoyFrameProvider {
    private val renderers = LinkedHashMap<ShadertoyProgramRegistry.ProgramDefinition, OffscreenShaderRenderer>()

    fun currentFrame(
        client: Minecraft,
        program: ShadertoyProgramRegistry.ProgramDefinition,
        qualityPreset: OffscreenShaderRenderer.QualityPreset,
        timeScale: Float,
    ): OffscreenShaderRenderer.ShadertoyFrame? {
        val renderer = renderers.getOrPut(program) {
            OffscreenShaderRenderer(program)
        }
        return renderer.renderIfNeeded(
            client = client,
            qualityPreset = qualityPreset,
            timeScale = timeScale,
        )
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
        renderers.values.forEach(OffscreenShaderRenderer::close)
        renderers.clear()
    }
}
