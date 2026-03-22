package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.audio.CustomSoundPlayer
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffectInstance
import kotlin.math.roundToInt

internal class PotionNotificationWatcher {

    private val previousRemainingByKey = HashMap<String, Float>()
    private val lastPlayedByStageKey = HashMap<String, Long>()

    fun tick(client: Minecraft) {
        if (!ModuleStateStore.isEnabled(NotificationsSettings.moduleId)) {
            previousRemainingByKey.clear()
            lastPlayedByStageKey.clear()
            return
        }

        val player = client.player ?: run {
            previousRemainingByKey.clear()
            lastPlayedByStageKey.clear()
            return
        }

        val config = NotificationsSettings.current()
        val currentKeys = HashSet<String>()
        val nowMs = System.currentTimeMillis()

        player.activeEffects.forEach { effect ->
            val effectKey = effectKey(effect)
            currentKeys += effectKey

            val remainingSeconds = (effect.duration / 20.0f).coerceAtLeast(0f)
            val previousRemaining = previousRemainingByKey[effectKey]

            if (previousRemaining != null) {
                maybeEmitStage(
                    effect = effect,
                    effectKey = effectKey,
                    stageIndex = 1,
                    threshold = config.stage1LeadSeconds,
                    previousRemaining = previousRemaining,
                    remainingSeconds = remainingSeconds,
                    textColor = 0xFFFF6666.toInt(),
                    customSoundEnabled = config.stage1CustomSoundEnabled,
                    soundFile = config.stage1SoundFile,
                    soundVolume = config.stage1SoundVolumeFactor(),
                    repeatPeriodMs = (config.repeatPeriodSeconds * 1000f).roundToInt().toLong(),
                    nowMs = nowMs,
                )

                if (config.mode == NotificationMode.TWO) {
                    maybeEmitStage(
                        effect = effect,
                        effectKey = effectKey,
                        stageIndex = 2,
                        threshold = config.stage2LeadSeconds,
                        previousRemaining = previousRemaining,
                        remainingSeconds = remainingSeconds,
                        textColor = 0xFFFFD86B.toInt(),
                        customSoundEnabled = config.stage2CustomSoundEnabled,
                        soundFile = config.stage2SoundFile,
                        soundVolume = config.stage2SoundVolumeFactor(),
                        repeatPeriodMs = (config.repeatPeriodSeconds * 1000f).roundToInt().toLong(),
                        nowMs = nowMs,
                    )
                }
            }

            previousRemainingByKey[effectKey] = remainingSeconds
        }

        previousRemainingByKey.keys.removeIf { it !in currentKeys }
        lastPlayedByStageKey.keys.removeIf { stageKey -> currentKeys.none { currentKey -> stageKey.startsWith("$currentKey#") } }
    }

    private fun maybeEmitStage(
        effect: MobEffectInstance,
        effectKey: String,
        stageIndex: Int,
        threshold: Float,
        previousRemaining: Float,
        remainingSeconds: Float,
        textColor: Int,
        customSoundEnabled: Boolean,
        soundFile: String,
        soundVolume: Float,
        repeatPeriodMs: Long,
        nowMs: Long,
    ) {
        if (previousRemaining <= threshold || remainingSeconds > threshold) return

        val effectName = effectName(effect)
        NotificationManager.push(
            "$effectName \u0437\u0430\u043A\u043E\u043D\u0447\u0438\u0442\u0441\u044F \u0447\u0435\u0440\u0435\u0437 ${formatNotificationTime(remainingSeconds)}",
            textColor,
        )

        if (!customSoundEnabled) return
        val debounceKey = "$effectKey#$stageIndex"
        val previousPlay = lastPlayedByStageKey[debounceKey]
        if (previousPlay != null && nowMs - previousPlay < repeatPeriodMs) return
        lastPlayedByStageKey[debounceKey] = nowMs
        CustomSoundPlayer.play(soundFile, soundVolume)
    }

    private fun effectKey(effect: MobEffectInstance): String {
        val id = BuiltInRegistries.MOB_EFFECT.getKey(effect.effect.value())
        return if (id != null) {
            "${id.namespace}:${id.path}:${effect.amplifier}"
        } else {
            "${effect.effect.value().descriptionId}:${effect.amplifier}"
        }
    }

    private fun effectName(effect: MobEffectInstance): String {
        val base = effect.effect.value().displayName.string
        return if (effect.amplifier > 0) "$base ${effect.amplifier + 1}" else base
    }

    private fun formatNotificationTime(seconds: Float): String {
        val safeSeconds = seconds.coerceAtLeast(0f)
        return when {
            safeSeconds < 10f -> String.format(java.util.Locale.US, "%.1f", safeSeconds)
            safeSeconds >= 60f -> {
                val total = safeSeconds.roundToInt()
                val minutes = total / 60
                val secs = total % 60
                "%d:%02d".format(minutes, secs)
            }
            else -> safeSeconds.roundToInt().toString()
        }
    }
}
