package com.visualproject.client

import com.visualproject.client.ui.menu.VisualMenuTheme
import com.visualproject.client.ui.menu.blendColor

object VisualThemeSettings {
    enum class MenuPreset(val id: String) {
        DARK("dark"),
        LIGHT("light");

        companion object {
            fun fromId(raw: String): MenuPreset {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: DARK
            }
        }
    }

    const val menuPresetKey = "theme:menu_preset"
    const val accentColorKey = "theme:accent_color"
    const val neonBorderColorKey = "theme:neon_border_color"
    const val neonBorderEnabledKey = "theme:neon_border_enabled"
    const val neonGlowEnabledKey = "theme:neon_glow_enabled"
    const val toggleFillColorKey = "theme:toggle_fill_color"
    const val sliderFillColorKey = "theme:slider_fill_color"
    const val sliderKnobColorKey = "theme:slider_knob_color"

    fun initializeDefaults() {
        ModuleStateStore.ensureTextSetting(menuPresetKey, MenuPreset.DARK.id)
        ModuleStateStore.ensureTextSetting(accentColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(neonBorderColorKey, "#8A71FF")
        ModuleStateStore.ensureSetting(neonBorderEnabledKey, defaultValue = true)
        ModuleStateStore.ensureSetting(neonGlowEnabledKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(toggleFillColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderFillColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderKnobColorKey, "#F0F2FF")
    }

    fun menuPreset(): MenuPreset = MenuPreset.fromId(ModuleStateStore.getTextSetting(menuPresetKey, MenuPreset.DARK.id))

    fun isLightPreset(): Boolean = menuPreset() == MenuPreset.LIGHT

    fun accentStrong(): Int = parseColor(ModuleStateStore.getTextSetting(accentColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun accent(): Int {
        return blendColor(accentStrong(), 0xFFFFFFFF.toInt(), 0.08f)
    }

    fun neonBorder(): Int = parseColor(ModuleStateStore.getTextSetting(neonBorderColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun neonBorderEnabled(): Boolean = ModuleStateStore.isSettingEnabled(neonBorderEnabledKey)

    fun neonGlowEnabled(): Boolean = ModuleStateStore.isSettingEnabled(neonGlowEnabledKey)

    fun toggleFill(): Int = parseColor(ModuleStateStore.getTextSetting(toggleFillColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderFill(): Int = parseColor(ModuleStateStore.getTextSetting(sliderFillColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderKnob(): Int = parseColor(ModuleStateStore.getTextSetting(sliderKnobColorKey, "#F0F2FF"), 0xFFF0F2FF.toInt())

    fun textPrimary(): Int = if (isLightPreset()) 0xFF24324A.toInt() else 0xFFF4F6FF.toInt()

    fun textSecondary(): Int = if (isLightPreset()) 0xFF6A7891.toInt() else 0xFFA0AAC8.toInt()

    fun textMuted(): Int = if (isLightPreset()) 0xFF7A879E.toInt() else 0xFF7983A3.toInt()

    fun hudShellFill(): Int = if (isLightPreset()) 0xECF2F7FD.toInt() else 0xF40C1118.toInt()

    fun hudShellBorder(): Int = if (isLightPreset()) 0xAFC3D5E8.toInt() else 0x91353D4E.toInt()

    fun hudShellShadeTop(): Int = if (isLightPreset()) 0x08FFFFFF else 0x10FFFFFF

    fun hudShellShadeBottom(): Int = if (isLightPreset()) 0x0ED0DBEA else 0x18000000

    fun hudInnerFill(): Int = if (isLightPreset()) 0xF2F6FAFE.toInt() else 0xFF05070F.toInt()

    fun hudInnerBorder(): Int = if (isLightPreset()) 0xAAC5D5E8.toInt() else 0x7C3A4563

    fun hudIconFill(): Int = if (isLightPreset()) 0xD7E7EFFA.toInt() else 0x7A131B30

    fun hudIconBorder(): Int = if (isLightPreset()) 0x9FBFD1E6.toInt() else 0x4D36405C

    fun hudTrackFill(): Int = if (isLightPreset()) 0xCCE0E9F5.toInt() else 0x7A20263B

    fun hudTrackBorder(): Int = if (isLightPreset()) 0x8FBFD0E3.toInt() else 0x4A3A4560

    fun themedAccentGlowBase(accent: Int = accentStrong()): Int {
        return if (isLightPreset()) blendColor(0xFFE8EEF7.toInt(), accent, 0.20f) else blendColor(0xFF2E0F16.toInt(), accent, 0.28f)
    }

    fun themedFallbackGlow(color: Int): Int {
        return if (isLightPreset()) blendColor(0xFFE8EEF7.toInt(), color, 0.18f) else color
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
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
