package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.audio.CustomSoundPlayer
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.HitResult
import java.util.Locale
import java.util.concurrent.TimeUnit

object CombatSoundController {

    private val hitPaths = setOf(
        "entity.player.attack.weak",
        "entity.player.attack.strong",
        "entity.player.attack.knockback",
        "entity.player.attack.sweep",
        "entity.player.attack.nodamage",
    )

    private const val critPath = "entity.player.attack.crit"
    private const val attackWindowMs = 100L
    private const val ownAttackMaxDistanceSq = 4.0

    @Volatile
    private var armedUntilNanos: Long = 0L
    @Volatile
    private var armedX: Double = 0.0
    @Volatile
    private var armedY: Double = 0.0
    @Volatile
    private var armedZ: Double = 0.0

    fun initialize() {
        ClientPreAttackCallback.EVENT.register(ClientPreAttackCallback { client, _, _ ->
            val player = client.player
            if (player != null && client.hitResult?.type == HitResult.Type.ENTITY) {
                armedX = player.x
                armedY = player.y
                armedZ = player.z
                armedUntilNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(attackWindowMs)
            }
            false
        })
    }

    @JvmStatic
    fun shouldIntercept(soundInstance: SoundInstance): Boolean {
        if (!ModuleStateStore.isEnabled(NotificationsSettings.moduleId)) return false
        if (System.nanoTime() > armedUntilNanos) return false

        val path = soundInstance.identifier.path.lowercase(Locale.US)
        if (path != critPath && path !in hitPaths) return false
        if (soundInstance.source != SoundSource.PLAYERS || soundInstance.isRelative) return false
        if (!isLikelyOwnAttackSound(soundInstance)) return false

        val config = NotificationsSettings.current()

        return when {
            path == critPath && config.critSoundMode == SoundMode.CUSTOM -> {
                CustomSoundPlayer.play(config.critSoundFile, config.critSoundVolumeFactor())
                true
            }

            path in hitPaths && config.hitSoundMode == SoundMode.CUSTOM -> {
                CustomSoundPlayer.play(config.hitSoundFile, config.hitSoundVolumeFactor())
                true
            }

            else -> false
        }
    }

    private fun isLikelyOwnAttackSound(soundInstance: SoundInstance): Boolean {
        val dx = soundInstance.x - armedX
        val dy = soundInstance.y - armedY
        val dz = soundInstance.z - armedZ
        return (dx * dx) + (dy * dy) + (dz * dz) <= ownAttackMaxDistanceSq
    }
}
