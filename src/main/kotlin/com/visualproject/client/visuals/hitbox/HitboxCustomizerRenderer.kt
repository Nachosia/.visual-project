package com.visualproject.client.visuals.hitbox

import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.shadertoy.ShadertoyBoxRenderer
import com.visualproject.client.render.shadertoy.ShadertoyFrameProvider
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.AABB

object HitboxCustomizerRenderer {
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { context ->
            render(context)
        })
    }

    private fun render(context: WorldRenderContext) {
        if (!HitboxCustomizerModule.isActive()) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (!shouldRender(client, player)) return

        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val box = interpolatedBox(player, partialTick).inflate(HitboxCustomizerModule.inflate().toDouble())
        if (!HitboxCustomizerModule.shaderEnabled()) {
            if (HitboxCustomizerModule.outlineEnabled()) {
                ShadertoyBoxRenderer.drawOutlineOnly(
                    context = context,
                    box = box,
                    outlineColor = VisualThemeSettings.accentStrong(),
                    lineThickness = HitboxCustomizerModule.outlineThickness(),
                )
            }
            return
        }

        val frame = ShadertoyFrameProvider.currentThemeFrame(client) ?: return
        ShadertoyBoxRenderer.drawBox(
            context = context,
            frame = frame,
            box = box,
            alpha = HitboxCustomizerModule.fillAlpha(),
            outlineEnabled = HitboxCustomizerModule.outlineEnabled(),
            outlineColor = VisualThemeSettings.accentStrong(),
            lineThickness = HitboxCustomizerModule.outlineThickness(),
        )
    }

    private fun shouldRender(client: Minecraft, player: LocalPlayer): Boolean {
        if (player.isRemoved) return false
        if (client.options.getCameraType().isFirstPerson && !HitboxCustomizerModule.showFirstPerson()) return false
        return true
    }

    private fun interpolatedBox(player: LocalPlayer, partialTick: Float): AABB {
        val currentBox = player.boundingBox
        val interpolatedX = lerp(player.xOld, player.x, partialTick)
        val interpolatedY = lerp(player.yOld, player.y, partialTick)
        val interpolatedZ = lerp(player.zOld, player.z, partialTick)
        val offsetX = interpolatedX - player.x
        val offsetY = interpolatedY - player.y
        val offsetZ = interpolatedZ - player.z
        return currentBox.move(offsetX, offsetY, offsetZ)
    }

    private fun lerp(previous: Double, current: Double, delta: Float): Double {
        return previous + ((current - previous) * delta)
    }
}
