package com.visualproject.client.notifications

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.audio.CustomSoundPlayer

object ModuleToggleSoundController {

    fun initialize() {
        ModuleStateStore.addModuleEnabledListener(::handleModuleToggle)
    }

    private fun handleModuleToggle(moduleId: String, enabled: Boolean) {
        val config = NotificationsSettings.current()
        if (enabled) {
            if (!config.moduleEnableCustomSoundEnabled) return
            CustomSoundPlayer.play(config.moduleEnableSoundFile, config.moduleEnableSoundVolumeFactor())
            return
        }

        if (!config.moduleDisableCustomSoundEnabled) return
        CustomSoundPlayer.play(config.moduleDisableSoundFile, config.moduleDisableSoundVolumeFactor())
    }
}
