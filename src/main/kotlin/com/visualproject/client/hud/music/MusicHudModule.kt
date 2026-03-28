package com.visualproject.client.hud.music

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object MusicHudModule {

    const val moduleId = "music_hud"
    const val musicScanKey = "${moduleId}:music_scan"

    private val renderer = MusicHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${moduleId}:visible_hud", defaultValue = false)
        ModuleStateStore.ensureSetting("${moduleId}:accent_sync", defaultValue = true)
        ModuleStateStore.ensureSetting(musicScanKey, defaultValue = true)
        ModuleStateStore.ensureNumberSetting("${moduleId}:size", 1.0f)

        HudRenderCallback.EVENT.register(HudRenderCallback { context, _ ->
            if (!ModuleStateStore.isEnabled(moduleId)) return@HudRenderCallback
            renderer.render(context, Minecraft.getInstance())
        })

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, _, _ ->
            ScreenMouseEvents.afterMouseClick(screen).register(
                ScreenMouseEvents.AfterMouseClick { activeScreen, mouseEvent, consumed ->
                    if (!ModuleStateStore.isEnabled(moduleId)) return@AfterMouseClick consumed
                    renderer.onScreenMouseClick(client, activeScreen, mouseEvent, consumed)
                }
            )
            ScreenMouseEvents.afterMouseDrag(screen).register(
                ScreenMouseEvents.AfterMouseDrag { activeScreen, mouseEvent, horizontalAmount, verticalAmount, consumed ->
                    if (!ModuleStateStore.isEnabled(moduleId)) return@AfterMouseDrag consumed
                    renderer.onScreenMouseDrag(
                        client = client,
                        screen = activeScreen,
                        mouseEvent = mouseEvent,
                        horizontalAmount = horizontalAmount,
                        verticalAmount = verticalAmount,
                        consumed = consumed,
                    )
                }
            )
            ScreenMouseEvents.afterMouseRelease(screen).register(
                ScreenMouseEvents.AfterMouseRelease { activeScreen, mouseEvent, consumed ->
                    if (!ModuleStateStore.isEnabled(moduleId)) return@AfterMouseRelease consumed
                    renderer.onScreenMouseRelease(activeScreen, mouseEvent, consumed)
                }
            )
        })
    }
}
