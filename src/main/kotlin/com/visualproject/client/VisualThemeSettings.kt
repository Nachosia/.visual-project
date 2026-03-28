package com.visualproject.client

import com.visualproject.client.render.sdf.SdfBackdropStyle
import com.visualproject.client.ui.menu.VisualMenuTheme
import com.visualproject.client.ui.menu.blendColor

object VisualThemeSettings {
    enum class MenuPreset(val id: String) {
        DARK("dark"),
        LIGHT("light"),
        TRANSPARENT("transparent");

        companion object {
            fun fromId(raw: String): MenuPreset {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: DARK
            }
        }
    }

    enum class ThemeFont(val id: String, val label: String) {
        JALNAN("jalnan", "Jalnan"),
        SATYR("satyrsp", "Satyr"),
        BLACKCRAFT("blackcraft", "Blackcraft");

        companion object {
            fun fromId(raw: String): ThemeFont {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: JALNAN
            }
        }
    }

    const val menuPresetKey = "theme:menu_preset"
    const val themeFontKey = "theme:font"
    const val accentColorKey = "theme:accent_color"
    const val neonBorderColorKey = "theme:neon_border_color"
    const val neonBorderEnabledKey = "theme:neon_border_enabled"
    const val neonGlowEnabledKey = "theme:neon_glow_enabled"
    const val toggleFillColorKey = "theme:toggle_fill_color"
    const val sliderFillColorKey = "theme:slider_fill_color"
    const val sliderKnobColorKey = "theme:slider_knob_color"

    fun initializeDefaults() {
        ModuleStateStore.ensureTextSetting(menuPresetKey, MenuPreset.DARK.id)
        ModuleStateStore.ensureTextSetting(themeFontKey, ThemeFont.JALNAN.id)
        ModuleStateStore.ensureTextSetting(accentColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(neonBorderColorKey, "#8A71FF")
        ModuleStateStore.ensureSetting(neonBorderEnabledKey, defaultValue = true)
        ModuleStateStore.ensureSetting(neonGlowEnabledKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(toggleFillColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderFillColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderKnobColorKey, "#F0F2FF")
    }

    fun menuPreset(): MenuPreset = MenuPreset.fromId(ModuleStateStore.getTextSetting(menuPresetKey, MenuPreset.DARK.id))

    fun themeFont(): ThemeFont = ThemeFont.fromId(ModuleStateStore.getTextSetting(themeFontKey, ThemeFont.JALNAN.id))

    fun isLightPreset(): Boolean = menuPreset() == MenuPreset.LIGHT

    fun isTransparentPreset(): Boolean = menuPreset() == MenuPreset.TRANSPARENT

    fun accentStrong(): Int = parseColor(ModuleStateStore.getTextSetting(accentColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun accent(): Int {
        return blendColor(accentStrong(), 0xFFFFFFFF.toInt(), 0.08f)
    }

    fun neonBorder(): Int = parseColor(ModuleStateStore.getTextSetting(neonBorderColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun neonBorderEnabled(): Boolean = ModuleStateStore.isSettingEnabled(neonBorderEnabledKey)

    fun neonGlowEnabled(): Boolean = ModuleStateStore.isSettingEnabled(neonGlowEnabledKey)

    fun themeAllowsNeon(): Boolean = !isTransparentPreset() && neonBorderEnabled()

    fun themeAllowsOuterGlow(): Boolean = !isTransparentPreset() && neonGlowEnabled()

    fun toggleFill(): Int = parseColor(ModuleStateStore.getTextSetting(toggleFillColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderFill(): Int = parseColor(ModuleStateStore.getTextSetting(sliderFillColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderKnob(): Int = parseColor(ModuleStateStore.getTextSetting(sliderKnobColorKey, "#F0F2FF"), 0xFFF0F2FF.toInt())

    fun textPrimary(): Int = when {
        isTransparentPreset() -> 0xFFF1F4F8.toInt()
        isLightPreset() -> 0xFF24324A.toInt()
        else -> 0xFFF4F6FF.toInt()
    }

    fun textSecondary(): Int = when {
        isTransparentPreset() -> 0xFFD4DAE2.toInt()
        isLightPreset() -> 0xFF6A7891.toInt()
        else -> 0xFFA0AAC8.toInt()
    }

    fun textMuted(): Int = when {
        isTransparentPreset() -> 0xFFB0B8C4.toInt()
        isLightPreset() -> 0xFF7A879E.toInt()
        else -> 0xFF7983A3.toInt()
    }

    fun hudShellFill(): Int = when {
        isTransparentPreset() -> 0x72121519
        isLightPreset() -> 0xFFFFFFFF.toInt()
        else -> 0xFF0C1118.toInt()
    }

    fun hudShellBorder(): Int = when {
        isTransparentPreset() -> 0x6B7A8696
        isLightPreset() -> 0xFFD0D7E0.toInt()
        else -> 0x91353D4E.toInt()
    }

    fun hudShellShadeTop(): Int = when {
        isTransparentPreset() -> 0x06FFFFFF
        isLightPreset() -> 0x03FFFFFF
        else -> 0x10FFFFFF
    }

    fun hudShellShadeBottom(): Int = when {
        isTransparentPreset() -> 0x0E000000
        isLightPreset() -> 0x05E7EBEF
        else -> 0x18000000
    }

    fun hudInnerFill(): Int = when {
        isTransparentPreset() -> 0x62161A20
        isLightPreset() -> 0xFFFDFDFD.toInt()
        else -> 0xFF05070F.toInt()
    }

    fun hudInnerBorder(): Int = when {
        isTransparentPreset() -> 0x64778392
        isLightPreset() -> 0xFFCFD7E0.toInt()
        else -> 0x7C3A4563
    }

    fun hudIconFill(): Int = when {
        isTransparentPreset() -> 0x4E161A1F
        isLightPreset() -> 0xFFF5F6F8.toInt()
        else -> 0xFF131B30.toInt()
    }

    fun hudIconBorder(): Int = when {
        isTransparentPreset() -> 0x60778490
        isLightPreset() -> 0xFFC8D0DA.toInt()
        else -> 0x4D36405C
    }

    fun hudTrackFill(): Int = when {
        isTransparentPreset() -> 0x3A12161B
        isLightPreset() -> 0xFFF0F2F5.toInt()
        else -> 0xFF20263B.toInt()
    }

    fun hudTrackBorder(): Int = when {
        isTransparentPreset() -> 0x58778490
        isLightPreset() -> 0xFFC7CFD8.toInt()
        else -> 0x4A3A4560
    }

    fun themedAccentGlowBase(accent: Int = accentStrong()): Int {
        return if (isLightPreset()) blendColor(0xFFE8EEF7.toInt(), accent, 0.20f) else blendColor(0xFF2E0F16.toInt(), accent, 0.28f)
    }

    fun themedFallbackGlow(color: Int): Int {
        return if (isLightPreset()) blendColor(0xFFE8EEF7.toInt(), color, 0.18f) else color
    }

    fun defaultGlassBackdrop(): SdfBackdropStyle {
        return SdfBackdropStyle(
            blurRadiusPx = 7.0f,
            tintMix = 0.42f,
            opacity = 0.82f,
        )
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
