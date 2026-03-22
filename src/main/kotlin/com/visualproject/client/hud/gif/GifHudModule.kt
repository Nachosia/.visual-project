package com.visualproject.client.hud.gif

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object GifHudModule {

    private const val moduleId = "gif_hud"
    private val renderer = GifHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${moduleId}:visible_hud", defaultValue = false)
        ModuleStateStore.ensureSetting("${moduleId}:chroma_key_enabled", defaultValue = true)
        ModuleStateStore.ensureSetting("${moduleId}:invert_colors", defaultValue = false)
        ModuleStateStore.ensureTextSetting("${moduleId}:file_name", "")
        ModuleStateStore.ensureTextSetting("${moduleId}:chroma_key_color", "#00FF00")
        ModuleStateStore.ensureNumberSetting("${moduleId}:chroma_key_strength", 0.18f)
        ModuleStateStore.ensureNumberSetting("${moduleId}:size", ModuleStateStore.getNumberSetting("${moduleId}:scale", 1.0f))
        ModuleStateStore.ensureNumberSetting("${moduleId}:scale", 1.0f)

        HudRenderCallback.EVENT.register(HudRenderCallback { context, _ ->
            if (!ModuleStateStore.isEnabled(moduleId)) return@HudRenderCallback
            val client = Minecraft.getInstance()
            renderer.render(context, client)
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
