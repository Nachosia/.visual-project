package com.visualproject.client

import com.visualproject.client.ui.menu.VisualMenuTheme
import com.visualproject.client.ui.menu.blendColor

object VisualThemeSettings {
    const val accentColorKey = "theme:accent_color"
    const val neonBorderColorKey = "theme:neon_border_color"
    const val sliderFillColorKey = "theme:slider_fill_color"
    const val sliderKnobColorKey = "theme:slider_knob_color"

    fun initializeDefaults() {
        ModuleStateStore.ensureTextSetting(accentColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(neonBorderColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderFillColorKey, "#8A71FF")
        ModuleStateStore.ensureTextSetting(sliderKnobColorKey, "#F0F2FF")
    }

    fun accentStrong(): Int = parseColor(ModuleStateStore.getTextSetting(accentColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun accent(): Int {
        return blendColor(0xFF31446B.toInt(), accentStrong(), 0.72f)
    }

    fun neonBorder(): Int = parseColor(ModuleStateStore.getTextSetting(neonBorderColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderFill(): Int = parseColor(ModuleStateStore.getTextSetting(sliderFillColorKey, "#8A71FF"), VisualMenuTheme.accentStrong)

    fun sliderKnob(): Int = parseColor(ModuleStateStore.getTextSetting(sliderKnobColorKey, "#F0F2FF"), 0xFFF0F2FF.toInt())

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
