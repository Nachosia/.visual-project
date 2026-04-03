package com.visualproject.client.visuals.hitbox

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.render.shadertoy.OffscreenShaderRenderer
import com.visualproject.client.render.shadertoy.ShadertoyProgramRegistry

object HitboxCustomizerModule {
    const val moduleId = "hitbox_customizer"

    const val shaderEnabledKey = "$moduleId:shader_enabled"
    const val shaderPresetKey = "$moduleId:shader_preset"
    const val qualityKey = "$moduleId:quality"
    const val shaderSpeedKey = "$moduleId:shader_speed"
    const val showFirstPersonKey = "$moduleId:show_first_person"
    const val fillAlphaKey = "$moduleId:fill_alpha"
    const val outlineKey = "$moduleId:outline"
    const val outlineThicknessKey = "$moduleId:outline_thickness"
    const val inflateKey = "$moduleId:inflate"

    enum class ShaderPreset(
        val id: String,
        val label: String,
        val program: ShadertoyProgramRegistry.ProgramDefinition,
    ) {
        OVERSATURATED_WEB("oversaturated_web", "Oversaturated Web", ShadertoyProgramRegistry.ProgramDefinition.OVERSATURATED_WEB),
        STAR_NEST("star_nest", "Star Nest", ShadertoyProgramRegistry.ProgramDefinition.STAR_NEST),
        SIMPLEX_NEBULA("simplex_nebula", "Simplex Nebula", ShadertoyProgramRegistry.ProgramDefinition.SIMPLEX_NEBULA);

        companion object {
            fun fromId(raw: String): ShaderPreset {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: OVERSATURATED_WEB
            }
        }
    }

    enum class Quality(val id: String, val label: String, val preset: OffscreenShaderRenderer.QualityPreset) {
        LOW("low", "LOW", OffscreenShaderRenderer.QualityPreset.LOW),
        MEDIUM("medium", "MEDIUM", OffscreenShaderRenderer.QualityPreset.MEDIUM),
        HIGH("high", "HIGH", OffscreenShaderRenderer.QualityPreset.HIGH);

        companion object {
            fun fromId(raw: String): Quality {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: MEDIUM
            }
        }
    }

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting(shaderEnabledKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(shaderPresetKey, ShaderPreset.OVERSATURATED_WEB.id)
        ModuleStateStore.ensureTextSetting(qualityKey, Quality.MEDIUM.id)
        ModuleStateStore.ensureNumberSetting(shaderSpeedKey, 1.0f)
        ModuleStateStore.ensureSetting(showFirstPersonKey, defaultValue = false)
        ModuleStateStore.ensureNumberSetting(fillAlphaKey, 0.72f)
        ModuleStateStore.ensureSetting(outlineKey, defaultValue = true)
        ModuleStateStore.ensureNumberSetting(outlineThicknessKey, 1.25f)
        ModuleStateStore.ensureNumberSetting(inflateKey, 0.035f)
        HitboxCustomizerRenderer.initialize()
    }

    fun isActive(): Boolean = ModuleStateStore.isEnabled(moduleId)

    fun shaderEnabled(): Boolean = ModuleStateStore.isSettingEnabled(shaderEnabledKey)

    fun shaderPreset(): ShaderPreset = ShaderPreset.fromId(ModuleStateStore.getTextSetting(shaderPresetKey, ShaderPreset.OVERSATURATED_WEB.id))

    fun quality(): Quality = Quality.fromId(ModuleStateStore.getTextSetting(qualityKey, Quality.MEDIUM.id))

    fun shaderSpeed(): Float = ModuleStateStore.getNumberSetting(shaderSpeedKey, 1.0f).coerceIn(0.10f, 4.0f)

    fun showFirstPerson(): Boolean = ModuleStateStore.isSettingEnabled(showFirstPersonKey)

    fun fillAlpha(): Float = ModuleStateStore.getNumberSetting(fillAlphaKey, 0.72f).coerceIn(0.05f, 1.0f)

    fun outlineEnabled(): Boolean = ModuleStateStore.isSettingEnabled(outlineKey)

    fun outlineThickness(): Float = ModuleStateStore.getNumberSetting(outlineThicknessKey, 1.25f).coerceIn(0.5f, 5.0f)

    fun inflate(): Float = ModuleStateStore.getNumberSetting(inflateKey, 0.035f).coerceIn(0.0f, 0.75f)
}
