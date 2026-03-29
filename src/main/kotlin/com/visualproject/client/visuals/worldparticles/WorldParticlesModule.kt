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
        ModuleStateStore.ensureTextSetting(customFileKey, "")
        if (ModuleStateStore.getTextSetting(particleTypeKey, ParticleType.WATER_DROP.id).equals("wavy", ignoreCase = true)) {
            ModuleStateStore.setTextSetting(particleTypeKey, ParticleType.SPARK.id)
        }

        WorldParticlesRenderer.initialize()
    }
}
