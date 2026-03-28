package com.visualproject.client.hud.watermark

import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft

object WatermarkHudModule {

    enum class WatermarkType(val id: String, val label: String) {
        CLASSIC("classic", "Classic"),
        HYPMOSIA_INFO("hypmosia_info", "Hypnosia");

        companion object {
            fun fromId(raw: String): WatermarkType {
                val value = raw.trim()
                return entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?:
                    if (value.equals("hypnosia_info", ignoreCase = true)) HYPMOSIA_INFO else CLASSIC
            }
        }
    }

    const val watermarkModuleId = "watermark"
    const val typeKey = "${watermarkModuleId}:type"
    const val customLabelKey = "${watermarkModuleId}:custom_label"

    private val renderer = WatermarkHudRenderer()

    fun initialize() {
        ModuleStateStore.ensureModule(watermarkModuleId, defaultEnabled = false)
        ModuleStateStore.ensureSetting("${watermarkModuleId}:accent_sync", defaultValue = true)
        ModuleStateStore.ensureSetting("${watermarkModuleId}:music_scan", defaultValue = true)
        ModuleStateStore.ensureTextSetting(typeKey, WatermarkType.CLASSIC.id)
        ModuleStateStore.ensureTextSetting(customLabelKey, "Developer")
        ModuleStateStore.ensureNumberSetting("${watermarkModuleId}:size", 1.0f)

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
            ScreenMouseEvents.afterMouseDrag(screen).register(
                ScreenMouseEvents.AfterMouseDrag { activeScreen, mouseEvent, horizontalAmount, verticalAmount, consumed ->
                    if (!ModuleStateStore.isEnabled(watermarkModuleId)) {
                        return@AfterMouseDrag consumed
                    }

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
                    if (!ModuleStateStore.isEnabled(watermarkModuleId)) {
                        return@AfterMouseRelease consumed
                    }

                    renderer.onScreenMouseRelease(activeScreen, mouseEvent, consumed)
                }
            )
        })
    }

    fun watermarkType(): WatermarkType {
        return WatermarkType.fromId(ModuleStateStore.getTextSetting(typeKey, WatermarkType.CLASSIC.id))
    }

    fun customLabel(): String {
        return ModuleStateStore.getTextSetting(customLabelKey, "Developer").trim().ifBlank { "Developer" }
    }
}
