package com.visualproject.client.hud.itembar

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object ItemBarHudModule {

    enum class LayoutType(val id: String, val label: String) {
        PANEL("panel", "Panel"),
        COMPACT("compact", "Compact"),
        VERTICAL("vertical", "Vertical");

        companion object {
            fun fromId(raw: String): LayoutType {
                return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } ?: PANEL
            }
        }
    }

    const val moduleId = "item_bar_hud"
    const val hideVanillaHotbarKey = "${moduleId}:hide_vanilla_hotbar"
    const val showPlayerStatusKey = "${moduleId}:show_player_status"
    const val layoutTypeKey = "${moduleId}:layout_type"

    private val renderer = ItemBarHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(moduleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${moduleId}:visible_hud", defaultValue = false)
        ModuleStateStore.ensureSetting("${moduleId}:accent_sync", defaultValue = true)
        ModuleStateStore.ensureSetting(hideVanillaHotbarKey, defaultValue = true)
        ModuleStateStore.ensureSetting(showPlayerStatusKey, defaultValue = true)
        ModuleStateStore.ensureTextSetting(layoutTypeKey, LayoutType.PANEL.id)
        ModuleStateStore.ensureNumberSetting("${moduleId}:size", 1.0f)

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

    @JvmStatic
    fun shouldHideVanillaHotbar(): Boolean {
        return ModuleStateStore.isEnabled(moduleId) &&
            ModuleStateStore.isSettingEnabled(hideVanillaHotbarKey)
    }

    @JvmStatic
    fun shouldHideVanillaStatusBars(): Boolean {
        return shouldHideVanillaHotbar() &&
            ModuleStateStore.isSettingEnabled(showPlayerStatusKey)
    }

    @JvmStatic
    fun layoutType(): LayoutType {
        return LayoutType.fromId(ModuleStateStore.getTextSetting(layoutTypeKey, LayoutType.PANEL.id))
    }
}
