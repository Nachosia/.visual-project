package com.visualproject.client.hud.watermark

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

object WatermarkHudModule {

    private const val watermarkModuleId = "watermark"

    private val renderer = WatermarkHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(watermarkModuleId, defaultEnabled = false)

        HudRenderCallback.EVENT.register(HudRenderCallback { context, deltaTracker ->
            if (!ModuleStateStore.isEnabled(watermarkModuleId)) return@HudRenderCallback

            val client = Minecraft.getInstance()
            renderer.render(context, deltaTracker, client)
        })
    }
}
