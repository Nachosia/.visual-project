package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualClientMod
import com.visualproject.client.audio.CustomSoundPlayer
import net.minecraft.sounds.SoundEvent
import java.util.Locale

object CombatSoundController {

    private val hitPaths = setOf(
        "entity.player.attack.weak",
        "entity.player.attack.strong",
        "entity.player.attack.knockback",
        "entity.player.attack.sweep",
        "entity.player.attack.nodamage",
    )

    private const val critPath = "entity.player.attack.crit"

    fun initialize() = Unit

    @JvmStatic
    fun replaceLocalAttackSound(soundEvent: SoundEvent): Boolean {
        if (!ModuleStateStore.isEnabled(NotificationsSettings.moduleId)) return false

        val path = soundEvent.location().path.lowercase(Locale.US)
        if (path != critPath && path !in hitPaths) return false

        val config = NotificationsSettings.current()

        return when {
            path == critPath && config.critSoundMode == SoundMode.CUSTOM -> {
                VisualClientMod.LOGGER.info(
                    "combat-sound: replace path='{}' kind='crit' sound='{}' volume={}",
                    path,
                    config.critSoundFile,
                    config.critSoundVolumeFactor(),
                )
                CustomSoundPlayer.play(config.critSoundFile, config.critSoundVolumeFactor())
                true
            }

            path in hitPaths && config.hitSoundMode == SoundMode.CUSTOM -> {
                VisualClientMod.LOGGER.info(
                    "combat-sound: replace path='{}' kind='hit' sound='{}' volume={}",
                    path,
                    config.hitSoundFile,
                    config.hitSoundVolumeFactor(),
                )
                CustomSoundPlayer.play(config.hitSoundFile, config.hitSoundVolumeFactor())
                true
            }

            else -> {
                VisualClientMod.LOGGER.info(
                    "combat-sound: pass-through path='{}' hitMode={} critMode={}",
                    path,
                    config.hitSoundMode,
                    config.critSoundMode,
                )
                false
            }
        }
    }
}
