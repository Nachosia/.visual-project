package com.visualproject.client.visuals.nimb

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings

object NimbModule {
    const val moduleId = "nimb"

    const val ringThicknessKey = "${moduleId}:ring_thickness"
    const val innerRadiusKey = "${moduleId}:inner_radius"
    const val heightKey = "${moduleId}:height"
    const val rotationSpeedKey = "${moduleId}:rotation_speed"
    const val clientColorKey = "${moduleId}:client_color"
    const val gradientKey = "${moduleId}:gradient"
    const val customColorKey = "${moduleId}:custom_color"
    const val gradientColorKey = "${moduleId}:gradient_color"

    private const val defaultRingThickness = 0.05f
    private const val defaultInnerRadius = 0.50f
    private const val defaultHeight = -1.00f
    private const val defaultRotationSpeed = 1.00f
    private const val defaultCustomColor = "#FFFFFF"
    private const val defaultGradientColor = "#8A71FF"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureNumberSetting(ringThicknessKey, defaultRingThickness)
        ModuleStateStore.ensureNumberSetting(innerRadiusKey, defaultInnerRadius)
        ModuleStateStore.ensureNumberSetting(heightKey, defaultHeight)
        ModuleStateStore.ensureNumberSetting(rotationSpeedKey, defaultRotationSpeed)
        ModuleStateStore.ensureSetting(clientColorKey, defaultValue = false)
        ModuleStateStore.ensureSetting(gradientKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(customColorKey, defaultCustomColor)
        ModuleStateStore.ensureTextSetting(gradientColorKey, defaultGradientColor)

        NimbRenderer.initialize()
    }

    @JvmStatic
    fun isActive(): Boolean = ModuleStateStore.isEnabled(moduleId)

    @JvmStatic
    fun ringThickness(): Float {
        return ModuleStateStore.getNumberSetting(ringThicknessKey, defaultRingThickness).coerceIn(0.01f, 0.25f)
    }

    @JvmStatic
    fun innerRadius(): Float {
        return ModuleStateStore.getNumberSetting(innerRadiusKey, defaultInnerRadius).coerceIn(0.10f, 1.50f)
    }

    @JvmStatic
    fun height(): Float {
        return ModuleStateStore.getNumberSetting(heightKey, defaultHeight).coerceIn(-2.00f, 2.00f)
    }

    @JvmStatic
    fun rotationSpeed(): Float {
        return ModuleStateStore.getNumberSetting(rotationSpeedKey, defaultRotationSpeed).coerceIn(0.00f, 5.00f)
    }

    @JvmStatic
    fun gradientEnabled(): Boolean = ModuleStateStore.isSettingEnabled(gradientKey)

    @JvmStatic
    fun color(): Int {
        return if (ModuleStateStore.isSettingEnabled(clientColorKey)) {
            VisualThemeSettings.accentStrong()
        } else {
            parseColor(ModuleStateStore.getTextSetting(customColorKey, defaultCustomColor), 0xFFFFFFFF.toInt())
        }
    }

    @JvmStatic
    fun gradientColor(): Int {
        return parseColor(ModuleStateStore.getTextSetting(gradientColorKey, defaultGradientColor), 0xFF8A71FF.toInt())
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
