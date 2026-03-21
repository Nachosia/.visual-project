package com.visualproject.client.hud.watermark

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object WatermarkHudModule {

    private const val watermarkModuleId = "watermark"

    private val renderer = WatermarkHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(watermarkModuleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${watermarkModuleId}:outer_glow", defaultValue = true)
        ModuleStateStore.ensureNumberSetting("${watermarkModuleId}:outer_glow_strength", defaultValue = 0.60f)
        ModuleStateStore.ensureNumberSetting("${watermarkModuleId}:outer_glow_distance", defaultValue = 22f)
        ModuleStateStore.ensureTextSetting("${watermarkModuleId}:outer_glow_color", defaultValue = "#6170D8")

        HudRenderCallback.EVENT.register(HudRenderCallback { context, deltaTracker ->
            if (!ModuleStateStore.isEnabled(watermarkModuleId)) return@HudRenderCallback

            val client = Minecraft.getInstance()
            renderer.render(context, deltaTracker, client)
        })

        // Route watermark control clicks through real screen input when a screen owns the mouse (e.g., ChatScreen).
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, _, _ ->
            ScreenMouseEvents.afterMouseClick(screen).register(
                ScreenMouseEvents.AfterMouseClick { activeScreen, mouseEvent, consumed ->
                    if (!ModuleStateStore.isEnabled(watermarkModuleId)) {
                        return@AfterMouseClick consumed
                    }

                    renderer.onScreenMouseClick(client, activeScreen, mouseEvent, consumed)
                }
            )
        })
    }
}
