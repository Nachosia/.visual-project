package com.visualproject.client.notifications

import com.visualproject.client.audio.CustomSoundRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

object NotificationsModule {

    private val renderer = NotificationRenderer()
    private val potionWatcher = PotionNotificationWatcher()
    private val armorWatcher = ArmorNotificationWatcher()

    fun initialize() {
        NotificationsSettings.initializeDefaults()
        CustomSoundRegistry.initialize()
        CombatSoundController.initialize()
        ModuleToggleSoundController.initialize()

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            potionWatcher.tick(client)
            armorWatcher.tick(client)
        })

        HudRenderCallback.EVENT.register(HudRenderCallback { context, _ ->
            renderer.render(context, Minecraft.getInstance())
        })
    }
}
