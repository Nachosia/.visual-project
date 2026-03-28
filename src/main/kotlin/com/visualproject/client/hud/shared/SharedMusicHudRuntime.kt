package com.visualproject.client.hud.shared

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.hud.music.MusicHudModule
import com.visualproject.client.hud.watermark.SpotifySoundCloudMusicProvider
import com.visualproject.client.hud.watermark.WatermarkHudModule
import com.visualproject.client.hud.watermark.WatermarkMusicProvider
import com.visualproject.client.hud.watermark.WatermarkTrackInfo
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object SharedMusicHudRuntime {

    private val providerInstance = SpotifySoundCloudMusicProvider()
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        syncScanning()
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            syncScanning()
        })
    }

    fun provider(): WatermarkMusicProvider = providerInstance

    fun currentTrack(client: Minecraft): WatermarkTrackInfo? = providerInstance.currentTrack(client)

    private fun syncScanning() {
        val watermarkRequestsMusic = ModuleStateStore.isEnabled(WatermarkHudModule.watermarkModuleId) &&
            WatermarkHudModule.watermarkType() == WatermarkHudModule.WatermarkType.CLASSIC &&
            ModuleStateStore.isSettingEnabled("${WatermarkHudModule.watermarkModuleId}:music_scan")

        val musicHudRequestsMusic = ModuleStateStore.isEnabled(MusicHudModule.moduleId) &&
            ModuleStateStore.isSettingEnabled(MusicHudModule.musicScanKey)

        providerInstance.setScanningEnabled(watermarkRequestsMusic || musicHudRequestsMusic)
    }
}
