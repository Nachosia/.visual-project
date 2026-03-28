package com.visualproject.client.hud.armor

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object ArmorHudModule {

    enum class LayoutType(val id: String, val label: String) {
        VERTICAL("vertical", "Vertical"),
        RIGHT_90("right_90", "Right 90");

        companion object {
            fun fromId(raw: String): LayoutType {
                return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: VERTICAL
            }
        }
    }

    private const val moduleId = "armor_hud"
    const val layoutTypeKey = "${moduleId}:layout_type"
    private val renderer = ArmorHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${moduleId}:accent_sync", defaultValue = true)
        ModuleStateStore.ensureSetting("${moduleId}:slot_background", defaultValue = true)
        ModuleStateStore.ensureNumberSetting("${moduleId}:size", 1.0f)
        ModuleStateStore.ensureTextSetting(layoutTypeKey, LayoutType.VERTICAL.id)

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

    fun layoutType(): LayoutType {
        return LayoutType.fromId(ModuleStateStore.getTextSetting(layoutTypeKey, LayoutType.VERTICAL.id))
    }
}
