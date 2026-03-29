package com.visualproject.client.visuals.world

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import net.minecraft.client.multiplayer.ClientLevel
import kotlin.math.max

object WorldCustomizerModule {
    const val moduleId = "world_customizer"

    const val customizeFogKey = "${moduleId}:customize_fog"
    const val customFogDistanceKey = "${moduleId}:custom_fog_distance"
    const val fogDistanceKey = "${moduleId}:fog_distance"

    const val customizeSkyKey = "${moduleId}:customize_sky"
    const val clientSkyColorKey = "${moduleId}:client_sky_color"
    const val customSkyColorKey = "${moduleId}:custom_sky_color"

    private const val defaultFogDistanceFactor = 0.70f
    private const val defaultSkyColor = "#4B5DFF"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting(customizeFogKey, defaultValue = false)
        ModuleStateStore.ensureSetting(customFogDistanceKey, defaultValue = true)
        ModuleStateStore.ensureNumberSetting(fogDistanceKey, defaultFogDistanceFactor)
        ModuleStateStore.ensureSetting(customizeSkyKey, defaultValue = false)
        ModuleStateStore.ensureSetting(clientSkyColorKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(customSkyColorKey, defaultSkyColor)
    }

    @JvmStatic
    fun isFogDistanceOverrideActive(world: ClientLevel?): Boolean {
        return world != null &&
            ModuleStateStore.isEnabled(moduleId) &&
            ModuleStateStore.isSettingEnabled(customizeFogKey) &&
            ModuleStateStore.isSettingEnabled(customFogDistanceKey)
    }

    @JvmStatic
    fun isSkyOverrideActive(world: ClientLevel?): Boolean {
        return world != null &&
            ModuleStateStore.isEnabled(moduleId) &&
            ModuleStateStore.isSettingEnabled(customizeSkyKey)
    }

    @JvmStatic
    fun isFogColorOverrideActive(world: ClientLevel?): Boolean {
        return isSkyOverrideActive(world)
    }

    @JvmStatic
    fun fogDistanceFactor(): Float {
        return ModuleStateStore.getNumberSetting(fogDistanceKey, defaultFogDistanceFactor)
            .coerceIn(0.10f, 1.00f)
    }

    @JvmStatic
    fun fogEndDistanceBlocks(renderDistanceChunks: Int): Float {
        val baseDistance = renderDistanceChunks.coerceAtLeast(2) * 16f
        return max(16f, baseDistance * fogDistanceFactor())
    }

    @JvmStatic
    fun fogStartDistanceBlocks(renderDistanceChunks: Int): Float {
        return max(4f, fogEndDistanceBlocks(renderDistanceChunks) * 0.28f)
    }

    @JvmStatic
    fun skyColor(): Int {
        return if (ModuleStateStore.isSettingEnabled(clientSkyColorKey)) {
            VisualThemeSettings.accentStrong()
        } else {
            parseColor(ModuleStateStore.getTextSetting(customSkyColorKey, defaultSkyColor), 0xFF4B5DFF.toInt())
        }
    }

    @JvmStatic
    fun fogColor(): Int {
        val color = skyColor()
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        val liftedRed = red + (((255 - red) * 20) / 100)
        val liftedGreen = green + (((255 - green) * 20) / 100)
        val liftedBlue = blue + (((255 - blue) * 20) / 100)
        return (0xFF shl 24) or (liftedRed shl 16) or (liftedGreen shl 8) or liftedBlue
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
