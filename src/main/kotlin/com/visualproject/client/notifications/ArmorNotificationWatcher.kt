package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.audio.CustomSoundPlayer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

internal class ArmorNotificationWatcher {

    private data class ArmorPart(
        val slot: EquipmentSlot,
        val nameGenitive: String,
    )

    private val trackedParts = listOf(
        ArmorPart(EquipmentSlot.HEAD, "шлема"),
        ArmorPart(EquipmentSlot.CHEST, "нагрудника"),
        ArmorPart(EquipmentSlot.LEGS, "поножей"),
        ArmorPart(EquipmentSlot.FEET, "ботинок"),
    )

    private val previousRemainingByKey = HashMap<String, Float>()
    private val lastPlayedByStageKey = HashMap<String, Long>()

    fun tick(client: Minecraft) {
        if (!ModuleStateStore.isEnabled(NotificationsSettings.moduleId)) {
            clear()
            return
        }

        val player = client.player ?: run {
            clear()
            return
        }

        val config = NotificationsSettings.current()
        if (!config.armorNotificationsEnabled) {
            clear()
            return
        }

        val currentKeys = HashSet<String>()
        val nowMs = System.currentTimeMillis()

        trackedParts.forEach { part ->
            val stack = player.getItemBySlot(part.slot)
            if (stack.isEmpty || !stack.isDamageableItem || stack.maxDamage <= 0) return@forEach

            val armorKey = armorKey(part.slot, stack)
            currentKeys += armorKey

            val remainingPercent = remainingPercent(stack)
            val previousRemaining = previousRemainingByKey[armorKey]

            if (previousRemaining != null) {
                maybeEmitStage(
                    armorKey = armorKey,
                    part = part,
                    stageIndex = 1,
                    thresholdPercent = config.armorStage1Percent,
                    previousRemaining = previousRemaining,
                    remainingPercent = remainingPercent,
                    textColor = 0xFFFF6666.toInt(),
                    customSoundEnabled = config.armorStage1CustomSoundEnabled,
                    soundFile = config.armorStage1SoundFile,
                    soundVolume = config.armorStage1SoundVolumeFactor(),
                    repeatPeriodMs = (config.repeatPeriodSeconds * 1000f).roundToInt().toLong(),
                    nowMs = nowMs,
                )

                if (config.mode == NotificationMode.TWO) {
                    maybeEmitStage(
                        armorKey = armorKey,
                        part = part,
                        stageIndex = 2,
                        thresholdPercent = config.armorStage2Percent,
                        previousRemaining = previousRemaining,
                        remainingPercent = remainingPercent,
                        textColor = 0xFFFFD86B.toInt(),
                        customSoundEnabled = config.armorStage2CustomSoundEnabled,
                        soundFile = config.armorStage2SoundFile,
                        soundVolume = config.armorStage2SoundVolumeFactor(),
                        repeatPeriodMs = (config.repeatPeriodSeconds * 1000f).roundToInt().toLong(),
                        nowMs = nowMs,
                    )
                }
            }

            previousRemainingByKey[armorKey] = remainingPercent
        }

        previousRemainingByKey.keys.removeIf { it !in currentKeys }
        lastPlayedByStageKey.keys.removeIf { stageKey -> currentKeys.none { currentKey -> stageKey.startsWith("$currentKey#") } }
    }

    private fun maybeEmitStage(
        armorKey: String,
        part: ArmorPart,
        stageIndex: Int,
        thresholdPercent: Float,
        previousRemaining: Float,
        remainingPercent: Float,
        textColor: Int,
        customSoundEnabled: Boolean,
        soundFile: String,
        soundVolume: Float,
        repeatPeriodMs: Long,
        nowMs: Long,
    ) {
        if (previousRemaining <= thresholdPercent || remainingPercent > thresholdPercent) return

        NotificationManager.push(
            "У вас осталось ${remainingPercent.roundToInt()}% прочности ${part.nameGenitive}",
            textColor,
        )

        if (!customSoundEnabled) return
        val debounceKey = "$armorKey#$stageIndex"
        val previousPlay = lastPlayedByStageKey[debounceKey]
        if (previousPlay != null && nowMs - previousPlay < repeatPeriodMs) return
        lastPlayedByStageKey[debounceKey] = nowMs
        CustomSoundPlayer.play(soundFile, soundVolume)
    }

    private fun armorKey(slot: EquipmentSlot, stack: ItemStack): String {
        return "${slot.name}:${stack.item.descriptionId}:${stack.maxDamage}"
    }

    private fun remainingPercent(stack: ItemStack): Float {
        val currentDurability = (stack.maxDamage - stack.damageValue).coerceAtLeast(0)
        return ((currentDurability.toFloat() / stack.maxDamage.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    private fun clear() {
        previousRemainingByKey.clear()
        lastPlayedByStageKey.clear()
    }
}
