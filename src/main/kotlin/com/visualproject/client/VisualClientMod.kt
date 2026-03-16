package com.visualproject.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object VisualClientMod : ClientModInitializer {
    val LOGGER = LoggerFactory.getLogger("visualclient")

    private lateinit var openVisualsMenuKey: KeyMapping
    private val visualsCategory = KeyMapping.Category.register(
        ResourceLocation.fromNamespaceAndPath("visualclient", "visuals")
    )

    override fun onInitializeClient() {
        openVisualsMenuKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "Open Visuals Menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                visualsCategory
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { _ ->
            while (openVisualsMenuKey.consumeClick()) {
                Minecraft.getInstance().setScreen(VisualsMenuScreen())
            }
        })

        LOGGER.info("Visual Client initialized.")
    }
}
