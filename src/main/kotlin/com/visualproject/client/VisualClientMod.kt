package com.visualproject.client

import com.mojang.blaze3d.platform.InputConstants
import com.visualproject.client.hud.armor.ArmorHudModule
import com.visualproject.client.hud.cooldowns.CooldownsHudModule
import com.visualproject.client.hud.gif.GifHudModule
import com.visualproject.client.hud.itembar.ItemBarHudModule
import com.visualproject.client.hud.potions.PotionHudModule
import com.visualproject.client.hud.test.TestSdfHud
import com.visualproject.client.hud.target.TargetHudModule
import com.visualproject.client.hud.watermark.WatermarkHudModule
import com.visualproject.client.notifications.NotificationsModule
import com.visualproject.client.render.sdf.SdfShaderRegistry
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object VisualClientMod : ClientModInitializer {
    val LOGGER = LoggerFactory.getLogger("visualclient")
    const val sdfTestModuleId = "sdf_test_hud"

    private lateinit var openVisualsMenuKey: KeyMapping
    private val visualsCategory = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("visualclient", "visuals")
    )

    override fun onInitializeClient() {
        VisualFileSystem.initialize(LOGGER)
        ModuleStateStore.initialize()
        VisualThemeSettings.initializeDefaults()
        ModuleStateStore.ensureModule(sdfTestModuleId, defaultEnabled = false)
        ModuleStateStore.ensureNumberSetting("${sdfTestModuleId}:size", 1.0f)
        SdfShaderRegistry.registerEvent()

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
                Minecraft.getInstance().setScreen(ExperimentalVisualsMenuScreen())
            }
        })

        HudRenderCallback.EVENT.register(HudRenderCallback { context, _ ->
            val client = Minecraft.getInstance()
            if (!ModuleStateStore.isEnabled(sdfTestModuleId)) return@HudRenderCallback
            if (client.screen != null) return@HudRenderCallback

            TestSdfHud.renderTest(
                context = context,
                screenWidth = client.window.guiScaledWidth,
                screenHeight = client.window.guiScaledHeight,
            )
        })

        WatermarkHudModule.initialize()
        ArmorHudModule.initialize()
        CooldownsHudModule.initialize()
        GifHudModule.initialize()
        ItemBarHudModule.initialize()
        NotificationsModule.initialize()
        PotionHudModule.initialize()
        TargetHudModule.initialize()

        LOGGER.info("Visual Client initialized.")
    }
}
