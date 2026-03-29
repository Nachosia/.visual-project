package com.visualproject.client.visuals.chinahat

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings

object ChinaHatModule {
    const val moduleId = "china_hat"

    const val shapeKey = "${moduleId}:shape"
    const val sidesKey = "${moduleId}:sides"
    const val heightKey = "${moduleId}:height"
    const val hitboxOffsetKey = "${moduleId}:hitbox_offset"
    const val rhombusHitboxLockKey = "${moduleId}:rhombus_hitbox_lock"
    const val opacityKey = "${moduleId}:opacity"
    const val outlineKey = "${moduleId}:outline"
    const val clientColorKey = "${moduleId}:client_color"
    const val gradientKey = "${moduleId}:gradient"
    const val rotationSpeedKey = "${moduleId}:rotation_speed"
    const val customColorKey = "${moduleId}:custom_color"
    const val gradientColorKey = "${moduleId}:gradient_color"

    enum class Shape(val id: String, val label: String) {
        ROUND("round", "Round"),
        RHOMBUS("rhombus", "Rhombus");

        companion object {
            fun fromId(raw: String): Shape {
                if (raw.equals("diamond", ignoreCase = true)) return RHOMBUS
                return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: ROUND
            }
        }
    }

    private const val defaultSides = 4f
    private const val defaultHeight = 0.30f
    private const val defaultHitboxOffset = 0.00f
    private const val defaultOpacity = 0.50f
    private const val defaultRotationSpeed = 1.00f
    private const val defaultCustomColor = "#6A8CFF"
    private const val defaultGradientColor = "#FF00FC"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureTextSetting(shapeKey, Shape.ROUND.id)
        ModuleStateStore.ensureNumberSetting(sidesKey, defaultSides)
        ModuleStateStore.ensureNumberSetting(heightKey, defaultHeight)
        ModuleStateStore.ensureNumberSetting(hitboxOffsetKey, defaultHitboxOffset)
        ModuleStateStore.ensureSetting(rhombusHitboxLockKey, defaultValue = false)
        ModuleStateStore.ensureNumberSetting(opacityKey, defaultOpacity)
        ModuleStateStore.ensureSetting(outlineKey, defaultValue = true)
        ModuleStateStore.ensureSetting(clientColorKey, defaultValue = false)
        ModuleStateStore.ensureSetting(gradientKey, defaultValue = false)
        ModuleStateStore.ensureNumberSetting(rotationSpeedKey, defaultRotationSpeed)
        ModuleStateStore.ensureTextSetting(customColorKey, defaultCustomColor)
        ModuleStateStore.ensureTextSetting(gradientColorKey, defaultGradientColor)

        ChinaHatRenderer.initialize()
    }

    @JvmStatic
    fun isActive(): Boolean = ModuleStateStore.isEnabled(moduleId)

    @JvmStatic
    fun shape(): Shape = Shape.fromId(ModuleStateStore.getTextSetting(shapeKey, Shape.ROUND.id))

    @JvmStatic
    fun sides(): Int {
        return ModuleStateStore.getNumberSetting(sidesKey, defaultSides).coerceIn(2f, 32f).toInt()
    }

    @JvmStatic
    fun height(): Float {
        return ModuleStateStore.getNumberSetting(heightKey, defaultHeight).coerceIn(0.05f, 0.80f)
    }

    @JvmStatic
    fun hitboxOffset(): Float {
        return ModuleStateStore.getNumberSetting(hitboxOffsetKey, defaultHitboxOffset).coerceIn(-2.00f, 2.00f)
    }

    @JvmStatic
    fun rhombusHitboxLockEnabled(): Boolean = ModuleStateStore.isSettingEnabled(rhombusHitboxLockKey)

    @JvmStatic
    fun gradientEnabled(): Boolean = ModuleStateStore.isSettingEnabled(gradientKey)

    @JvmStatic
    fun rotationSpeed(): Float {
        return ModuleStateStore.getNumberSetting(rotationSpeedKey, defaultRotationSpeed).coerceIn(0.00f, 5.00f)
    }

    @JvmStatic
    fun opacity(): Float {
        return ModuleStateStore.getNumberSetting(opacityKey, defaultOpacity).coerceIn(0.05f, 1.00f)
    }

    @JvmStatic
    fun outlineEnabled(): Boolean = ModuleStateStore.isSettingEnabled(outlineKey)

    @JvmStatic
    fun color(): Int {
        return if (ModuleStateStore.isSettingEnabled(clientColorKey)) {
            VisualThemeSettings.accentStrong()
        } else {
            parseColor(ModuleStateStore.getTextSetting(customColorKey, defaultCustomColor), 0xFF6A8CFF.toInt())
        }
    }

    @JvmStatic
    fun gradientColor(): Int {
        return parseColor(ModuleStateStore.getTextSetting(gradientColorKey, defaultGradientColor), 0xFFFF00FC.toInt())
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
