package com.visualproject.client.hud.target

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object TargetHudModule {

    private const val moduleId = "target_hud"
    const val lifetimeSecondsKey = "target_hud:lifetime_seconds"
    private val renderer = TargetHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureNumberSetting("${moduleId}:size", 1.0f)
        ModuleStateStore.ensureNumberSetting(lifetimeSecondsKey, 0.0f)

        HudRenderCallback.EVENT.register(HudRenderCallback { context, deltaTracker ->
            if (!ModuleStateStore.isEnabled(moduleId)) return@HudRenderCallback
            val client = Minecraft.getInstance()
            renderer.render(context, deltaTracker, client)
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

