package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore

enum class NotificationMode {
    ONE,
    TWO,
}

enum class SoundMode {
    CLASSIC,
    CUSTOM,
}

data class NotificationsConfig(
    val mode: NotificationMode,
    val stage1LeadSeconds: Float,
    val stage2LeadSeconds: Float,
    val armorNotificationsEnabled: Boolean,
    val armorStage1Percent: Float,
    val armorStage2Percent: Float,
    val armorStage1CustomSoundEnabled: Boolean,
    val armorStage2CustomSoundEnabled: Boolean,
    val armorStage1SoundFile: String,
    val armorStage2SoundFile: String,
    val armorStage1SoundVolumePercent: Float,
    val armorStage2SoundVolumePercent: Float,
    val globalCustomSoundVolumePercent: Float,
    val moduleEnableCustomSoundEnabled: Boolean,
    val moduleEnableSoundFile: String,
    val moduleEnableSoundVolumePercent: Float,
    val moduleDisableCustomSoundEnabled: Boolean,
    val moduleDisableSoundFile: String,
    val moduleDisableSoundVolumePercent: Float,
    val stage1CustomSoundEnabled: Boolean,
    val stage2CustomSoundEnabled: Boolean,
    val stage1SoundFile: String,
    val stage2SoundFile: String,
    val stage1SoundVolumePercent: Float,
    val stage2SoundVolumePercent: Float,
    val repeatPeriodSeconds: Float,
    val hitSoundMode: SoundMode,
    val hitSoundFile: String,
    val hitSoundVolumePercent: Float,
    val critSoundMode: SoundMode,
    val critSoundFile: String,
    val critSoundVolumePercent: Float,
) {
    fun armorStage1SoundVolumeFactor(): Float = combinedVolume(armorStage1SoundVolumePercent)

    fun armorStage2SoundVolumeFactor(): Float = combinedVolume(armorStage2SoundVolumePercent)

    fun moduleEnableSoundVolumeFactor(): Float = combinedVolume(moduleEnableSoundVolumePercent)

    fun moduleDisableSoundVolumeFactor(): Float = combinedVolume(moduleDisableSoundVolumePercent)

    fun stage1SoundVolumeFactor(): Float = combinedVolume(stage1SoundVolumePercent)

    fun stage2SoundVolumeFactor(): Float = combinedVolume(stage2SoundVolumePercent)

    fun hitSoundVolumeFactor(): Float = combinedVolume(hitSoundVolumePercent)

    fun critSoundVolumeFactor(): Float = combinedVolume(critSoundVolumePercent)

    private fun combinedVolume(localPercent: Float): Float {
        return (globalCustomSoundVolumePercent.coerceIn(0f, 100f) / 100f) *
            (localPercent.coerceIn(0f, 100f) / 100f)
    }
}

object NotificationsSettings {

    const val moduleId = "effect_notify"

    const val modeKey = "$moduleId:mode"
    const val stage1LeadKey = "$moduleId:stage1_lead_seconds"
    const val armorNotificationsEnabledKey = "$moduleId:armor_notifications_enabled"
    const val armorStage1PercentKey = "$moduleId:armor_stage1_percent"
    const val armorStage2PercentKey = "$moduleId:armor_stage2_percent"
    const val armorStage1SoundEnabledKey = "$moduleId:armor_stage1_custom_sound_enabled"
    const val armorStage1SoundFileKey = "$moduleId:armor_stage1_sound_file"
    const val armorStage1SoundVolumeKey = "$moduleId:armor_stage1_sound_volume"
    const val armorStage2SoundEnabledKey = "$moduleId:armor_stage2_custom_sound_enabled"
    const val armorStage2SoundFileKey = "$moduleId:armor_stage2_sound_file"
    const val armorStage2SoundVolumeKey = "$moduleId:armor_stage2_sound_volume"
    const val globalCustomSoundVolumeKey = "$moduleId:global_custom_sound_volume"
    const val moduleEnableSoundEnabledKey = "$moduleId:module_enable_custom_sound_enabled"
    const val moduleEnableSoundFileKey = "$moduleId:module_enable_sound_file"
    const val moduleEnableSoundVolumeKey = "$moduleId:module_enable_sound_volume"
    const val moduleDisableSoundEnabledKey = "$moduleId:module_disable_custom_sound_enabled"
    const val moduleDisableSoundFileKey = "$moduleId:module_disable_sound_file"
    const val moduleDisableSoundVolumeKey = "$moduleId:module_disable_sound_volume"
    const val stage1SoundEnabledKey = "$moduleId:stage1_custom_sound_enabled"
    const val stage1SoundFileKey = "$moduleId:stage1_sound_file"
    const val stage1SoundVolumeKey = "$moduleId:stage1_sound_volume"
    const val stage2LeadKey = "$moduleId:stage2_lead_seconds"
    const val stage2SoundEnabledKey = "$moduleId:stage2_custom_sound_enabled"
    const val stage2SoundFileKey = "$moduleId:stage2_sound_file"
    const val stage2SoundVolumeKey = "$moduleId:stage2_sound_volume"
    const val repeatPeriodKey = "$moduleId:repeat_period_seconds"
    const val hitSoundModeKey = "$moduleId:hit_sound_mode"
    const val hitSoundFileKey = "$moduleId:hit_sound_file"
    const val hitSoundVolumeKey = "$moduleId:hit_sound_volume"
    const val critSoundModeKey = "$moduleId:crit_sound_mode"
    const val critSoundFileKey = "$moduleId:crit_sound_file"
    const val critSoundVolumeKey = "$moduleId:crit_sound_volume"

    fun initializeDefaults() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureTextSetting(modeKey, "1")
        ModuleStateStore.ensureNumberSetting(stage1LeadKey, 1.0f)
        ModuleStateStore.ensureSetting(armorNotificationsEnabledKey, defaultValue = false)
        ModuleStateStore.ensureNumberSetting(armorStage1PercentKey, 10.0f)
        ModuleStateStore.ensureNumberSetting(armorStage2PercentKey, 25.0f)
        ModuleStateStore.ensureSetting(armorStage1SoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(armorStage1SoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(armorStage1SoundVolumeKey, 100.0f)
        ModuleStateStore.ensureSetting(armorStage2SoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(armorStage2SoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(armorStage2SoundVolumeKey, 100.0f)
        ModuleStateStore.ensureNumberSetting(globalCustomSoundVolumeKey, 100.0f)
        ModuleStateStore.ensureSetting(moduleEnableSoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(moduleEnableSoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(moduleEnableSoundVolumeKey, 100.0f)
        ModuleStateStore.ensureSetting(moduleDisableSoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(moduleDisableSoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(moduleDisableSoundVolumeKey, 100.0f)
        ModuleStateStore.ensureSetting(stage1SoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(stage1SoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(stage1SoundVolumeKey, 100.0f)
        ModuleStateStore.ensureNumberSetting(stage2LeadKey, 5.0f)
        ModuleStateStore.ensureSetting(stage2SoundEnabledKey, defaultValue = false)
        ModuleStateStore.ensureTextSetting(stage2SoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(stage2SoundVolumeKey, 100.0f)
        ModuleStateStore.ensureNumberSetting(repeatPeriodKey, 2.0f)
        ModuleStateStore.ensureTextSetting(hitSoundModeKey, "classic")
        ModuleStateStore.ensureTextSetting(hitSoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(hitSoundVolumeKey, 100.0f)
        ModuleStateStore.ensureTextSetting(critSoundModeKey, "classic")
        ModuleStateStore.ensureTextSetting(critSoundFileKey, "")
        ModuleStateStore.ensureNumberSetting(critSoundVolumeKey, 100.0f)
    }

    fun current(): NotificationsConfig {
        val rawMode = ModuleStateStore.getTextSetting(modeKey, "1")
        val mode = if (rawMode == "2") NotificationMode.TWO else NotificationMode.ONE
        val rawStage1 = ModuleStateStore.getNumberSetting(stage1LeadKey, 1.0f).coerceIn(0.1f, 30.0f)
        val rawStage2 = ModuleStateStore.getNumberSetting(stage2LeadKey, 5.0f).coerceIn(0.1f, 30.0f)
        val stage1Lead = minOf(rawStage1, rawStage2)
        val stage2Lead = maxOf(rawStage1, rawStage2)
        val rawArmorStage1 = ModuleStateStore.getNumberSetting(armorStage1PercentKey, 10.0f).coerceIn(1.0f, 100.0f)
        val rawArmorStage2 = ModuleStateStore.getNumberSetting(armorStage2PercentKey, 25.0f).coerceIn(1.0f, 100.0f)
        val armorStage1 = if (mode == NotificationMode.TWO) minOf(rawArmorStage1, rawArmorStage2) else rawArmorStage1
        val armorStage2 = if (mode == NotificationMode.TWO) maxOf(rawArmorStage1, rawArmorStage2) else rawArmorStage2
        return NotificationsConfig(
            mode = mode,
            stage1LeadSeconds = stage1Lead,
            stage2LeadSeconds = stage2Lead,
            armorNotificationsEnabled = ModuleStateStore.isSettingEnabled(armorNotificationsEnabledKey),
            armorStage1Percent = armorStage1,
            armorStage2Percent = armorStage2,
            armorStage1CustomSoundEnabled = ModuleStateStore.isSettingEnabled(armorStage1SoundEnabledKey),
            armorStage2CustomSoundEnabled = ModuleStateStore.isSettingEnabled(armorStage2SoundEnabledKey),
            armorStage1SoundFile = ModuleStateStore.getTextSetting(armorStage1SoundFileKey, ""),
            armorStage2SoundFile = ModuleStateStore.getTextSetting(armorStage2SoundFileKey, ""),
            armorStage1SoundVolumePercent = ModuleStateStore.getNumberSetting(armorStage1SoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            armorStage2SoundVolumePercent = ModuleStateStore.getNumberSetting(armorStage2SoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            globalCustomSoundVolumePercent = ModuleStateStore.getNumberSetting(globalCustomSoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            moduleEnableCustomSoundEnabled = ModuleStateStore.isSettingEnabled(moduleEnableSoundEnabledKey),
            moduleEnableSoundFile = ModuleStateStore.getTextSetting(moduleEnableSoundFileKey, ""),
            moduleEnableSoundVolumePercent = ModuleStateStore.getNumberSetting(moduleEnableSoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            moduleDisableCustomSoundEnabled = ModuleStateStore.isSettingEnabled(moduleDisableSoundEnabledKey),
            moduleDisableSoundFile = ModuleStateStore.getTextSetting(moduleDisableSoundFileKey, ""),
            moduleDisableSoundVolumePercent = ModuleStateStore.getNumberSetting(moduleDisableSoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            stage1CustomSoundEnabled = ModuleStateStore.isSettingEnabled(stage1SoundEnabledKey),
            stage2CustomSoundEnabled = ModuleStateStore.isSettingEnabled(stage2SoundEnabledKey),
            stage1SoundFile = ModuleStateStore.getTextSetting(stage1SoundFileKey, ""),
            stage2SoundFile = ModuleStateStore.getTextSetting(stage2SoundFileKey, ""),
            stage1SoundVolumePercent = ModuleStateStore.getNumberSetting(stage1SoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            stage2SoundVolumePercent = ModuleStateStore.getNumberSetting(stage2SoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            repeatPeriodSeconds = ModuleStateStore.getNumberSetting(repeatPeriodKey, 2.0f).coerceIn(0.1f, 10.0f),
            hitSoundMode = parseSoundMode(ModuleStateStore.getTextSetting(hitSoundModeKey, "classic")),
            hitSoundFile = ModuleStateStore.getTextSetting(hitSoundFileKey, ""),
            hitSoundVolumePercent = ModuleStateStore.getNumberSetting(hitSoundVolumeKey, 100.0f).coerceIn(0f, 100f),
            critSoundMode = parseSoundMode(ModuleStateStore.getTextSetting(critSoundModeKey, "classic")),
            critSoundFile = ModuleStateStore.getTextSetting(critSoundFileKey, ""),
            critSoundVolumePercent = ModuleStateStore.getNumberSetting(critSoundVolumeKey, 100.0f).coerceIn(0f, 100f),
        )
    }

    private fun parseSoundMode(raw: String): SoundMode {
        return if (raw.equals("custom", ignoreCase = true)) SoundMode.CUSTOM else SoundMode.CLASSIC
    }
}
