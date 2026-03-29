package com.visualproject.client.visuals.time

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.mixin.ClientLevelDataAccessor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object TimeChangerModule {

    enum class TimePreset(val id: String, val label: String, val dayTime: Long) {
        DAY("day", "Day", 1000L),
        SUNSET("sunset", "Sunset", 12000L),
        NIGHT("night", "Night", 13000L),
        MIDNIGHT("midnight", "Midnight", 18000L),
        DAWN("dawn", "Dawn", 23000L);

        companion object {
            fun fromId(raw: String): TimePreset {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: NIGHT
            }
        }
    }

    const val moduleId = "time_changer"
    const val presetKey = "${moduleId}:preset"

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureTextSetting(presetKey, TimePreset.NIGHT.id)

        ClientTickEvents.END_WORLD_TICK.register(ClientTickEvents.EndWorldTick { world ->
            val override = overrideDayTimeOrNull() ?: return@EndWorldTick
            if (world.isClientSide) {
                (world.levelData as ClientLevelDataAccessor).setDayTime(override)
            }
        })
    }

    @JvmStatic
    fun isActive(): Boolean = ModuleStateStore.isEnabled(moduleId)

    @JvmStatic
    fun overrideDayTimeOrNull(): Long? {
        if (!isActive()) return null
        return TimePreset.fromId(ModuleStateStore.getTextSetting(presetKey, TimePreset.NIGHT.id)).dayTime
    }
}
