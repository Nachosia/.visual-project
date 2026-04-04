package com.visualproject.client.visuals.worldparticles

import com.visualproject.client.ModuleStateStore

object WorldParticlesModule {
    enum class ParticleType(val id: String, val label: String) {
        SPARK("spark", "Spark"),
        SUN("sun", "Sun"),
        SNOWFLAKE("snowflake", "Snowflake"),
        PAYMENTS("payments", "Payments"),
        DOLLAR("dollar", "Dollar"),
        HEART("heart", "Heart"),
        WATER_DROP("water_drop", "Drop"),
        STAR("star", "Star"),
        MOON("moon", "Moon"),
        BOLT("bolt", "Bolt"),
        NEARBY("nearby", "Nearby"),
        BLINK("blink", "Blink"),
        CORON("coron", "Coron"),
        FIREFLY("firefly", "Firefly"),
        FLAME("flame", "Flame"),
        GEOMETRIC("geometric", "Geometric"),
        VIRUS("virus", "Virus"),
        AMONGUS("amongus", "Among Us"),
        BLOOM("bloom", "Bloom"),
        GLYPH("glyph", "Glyph"),
        GLYPH_ALT("glyph_alt", "Glyph Alt"),
        CUSTOM("custom", "Custom");

        companion object {
            fun fromId(raw: String): ParticleType {
                val normalized = raw.trim()
                if (normalized.equals("wavy", ignoreCase = true)) return SPARK
                return entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: WATER_DROP
            }
        }
    }

    enum class PhysicsMode(val id: String, val label: String) {
        REALISTIC("realistic", "Real"),
        NO_COLLISION("no_collision", "NoClip"),
        NO_PHYSICS("no_physics", "NoPhys");

        companion object {
            fun fromId(raw: String): PhysicsMode {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: NO_PHYSICS
            }
        }
    }

    enum class ColorMode(val id: String, val label: String) {
        SYNC("sync", "Sync"),
        CUSTOM("custom", "Custom");

        companion object {
            fun fromId(raw: String): ColorMode {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: SYNC
            }
        }
    }

    enum class ColorAnimation(val id: String, val label: String) {
        WAVE("wave", "Wave"),
        VERTEX("vertex", "Vertex");

        companion object {
            fun fromId(raw: String): ColorAnimation {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: WAVE
            }
        }
    }

    enum class ColorCount(val id: String, val label: String, val count: Int) {
        SOLO("solo", "One", 1),
        DUO("duo", "Two", 2),
        TRIPLE("triple", "Three", 3),
        QUARTET("quartet", "Four", 4);

        companion object {
            fun fromId(raw: String): ColorCount {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: SOLO
            }
        }
    }

    const val moduleId = "world_particles"

    const val particleTypeKey = "${moduleId}:particle_type"
    const val physicsModeKey = "${moduleId}:physics_mode"
    const val spawnRateKey = "${moduleId}:spawn_rate"
    const val spawnCountKey = "${moduleId}:spawn_count"
    const val spawnRadiusKey = "${moduleId}:spawn_radius"
    const val spawnHeightKey = "${moduleId}:spawn_height"
    const val sizeKey = "${moduleId}:particle_size"
    const val lifetimeKey = "${moduleId}:lifetime"
    const val gravityKey = "${moduleId}:gravity"
    const val horizontalMovementKey = "${moduleId}:horizontal_movement"
    const val speedKey = "${moduleId}:movement_speed"
    const val clientColorKey = "${moduleId}:client_color"
    const val customColorKey = "${moduleId}:custom_color"
    const val colorModeKey = "${moduleId}:color_mode"
    const val colorAnimationKey = "${moduleId}:color_animation"
    const val colorCountKey = "${moduleId}:color_count"
    const val customColor1Key = "${moduleId}:custom_color_1"
    const val customColor2Key = "${moduleId}:custom_color_2"
    const val customColor3Key = "${moduleId}:custom_color_3"
    const val customColor4Key = "${moduleId}:custom_color_4"
    const val customFileKey = "${moduleId}:custom_file"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureTextSetting(particleTypeKey, ParticleType.WATER_DROP.id)
        ModuleStateStore.ensureTextSetting(physicsModeKey, PhysicsMode.NO_PHYSICS.id)
        ModuleStateStore.ensureNumberSetting(spawnRateKey, 5.0f)
        ModuleStateStore.ensureNumberSetting(spawnCountKey, 5.0f)
        ModuleStateStore.ensureNumberSetting(spawnRadiusKey, 30.0f)
        ModuleStateStore.ensureNumberSetting(spawnHeightKey, 10.0f)
        ModuleStateStore.ensureNumberSetting(sizeKey, 0.40f)
        ModuleStateStore.ensureNumberSetting(lifetimeKey, 100.0f)
        ModuleStateStore.ensureNumberSetting(gravityKey, 0.04f)
        ModuleStateStore.ensureSetting(horizontalMovementKey, defaultValue = true)
        ModuleStateStore.ensureNumberSetting(speedKey, 0.05f)
        ModuleStateStore.ensureSetting(clientColorKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(customColorKey, "#B31284")
        val legacyClientColor = ModuleStateStore.isSettingEnabled(clientColorKey)
        val legacyCustomColor = ModuleStateStore.getTextSetting(customColorKey, "#B31284")
        ModuleStateStore.ensureTextSetting(colorModeKey, if (legacyClientColor) ColorMode.SYNC.id else ColorMode.CUSTOM.id)
        ModuleStateStore.ensureTextSetting(colorAnimationKey, ColorAnimation.WAVE.id)
        ModuleStateStore.ensureTextSetting(colorCountKey, ColorCount.SOLO.id)
        ModuleStateStore.ensureTextSetting(customColor1Key, legacyCustomColor)
        ModuleStateStore.ensureTextSetting(customColor2Key, "#FF8A3D")
        ModuleStateStore.ensureTextSetting(customColor3Key, "#7F5BFF")
        ModuleStateStore.ensureTextSetting(customColor4Key, "#F9D648")
        ModuleStateStore.ensureTextSetting(customFileKey, "")
        if (ModuleStateStore.getTextSetting(particleTypeKey, ParticleType.WATER_DROP.id).equals("wavy", ignoreCase = true)) {
            ModuleStateStore.setTextSetting(particleTypeKey, ParticleType.SPARK.id)
        }

        WorldParticlesRenderer.initialize()
    }

    fun colorMode(): ColorMode =
        ColorMode.fromId(ModuleStateStore.getTextSetting(colorModeKey, if (ModuleStateStore.isSettingEnabled(clientColorKey)) ColorMode.SYNC.id else ColorMode.CUSTOM.id))

    fun colorAnimation(): ColorAnimation =
        ColorAnimation.fromId(ModuleStateStore.getTextSetting(colorAnimationKey, ColorAnimation.WAVE.id))

    fun colorCount(): ColorCount =
        ColorCount.fromId(ModuleStateStore.getTextSetting(colorCountKey, ColorCount.SOLO.id))

    fun customColors(): IntArray {
        val palette = intArrayOf(
            parseColor(ModuleStateStore.getTextSetting(customColor1Key, ModuleStateStore.getTextSetting(customColorKey, "#B31284")), 0xFFB31284.toInt()),
            parseColor(ModuleStateStore.getTextSetting(customColor2Key, "#FF8A3D"), 0xFFFF8A3D.toInt()),
            parseColor(ModuleStateStore.getTextSetting(customColor3Key, "#7F5BFF"), 0xFF7F5BFF.toInt()),
            parseColor(ModuleStateStore.getTextSetting(customColor4Key, "#F9D648"), 0xFFF9D648.toInt()),
        )
        return palette.copyOf(colorCount().count)
    }

    fun previewColor(): Int {
        return if (colorMode() == ColorMode.SYNC) {
            com.visualproject.client.VisualThemeSettings.accentStrong()
        } else {
            customColors().firstOrNull() ?: 0xFFFFFFFF.toInt()
        }
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
