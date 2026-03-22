package com.visualproject.client.hud.potions

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance

internal class PotionHudRenderer {

    companion object {
        private const val moduleId = "potions"
    }

    private object Layout {
        const val shellWidth = 170
        const val shellRadius = 17f
        const val padding = 8
        const val headerHeight = 0
        const val rowHeight = 16
        const val rowGap = 4
        const val iconSize = 14
        const val iconGap = 6
        const val timeRightInset = 8
        const val anchorX = 12
        const val anchorY = 72
    }

    private data class EffectEntry(
        val effect: MobEffectInstance?,
        val effectHolder: Holder<MobEffect>?,
        val label: String,
        val durationText: String,
    )

    private var dragState: PotionHudDragState? = null
    private var lastBounds: PotionHudBounds? = null

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val entries = activeEntries(player.activeEffects.toList())
        if (entries.isEmpty() && client.screen !is ChatScreen) {
            return
        }

        val visibleEntries = if (entries.isNotEmpty()) entries else previewEntries()
        val shellHeight = shellHeightFor(visibleEntries.size)

        val state = ensureDragState(client)
        clampToScreen(client, state, shellHeight)
        val bounds = PotionHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = Layout.shellWidth,
            height = shellHeight,
        )
        lastBounds = bounds

        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF7A2730.toInt()
        val glowColor = if (accentSync) blendColor(0xFF2E0F16.toInt(), VisualThemeSettings.accentStrong(), 0.28f) else 0xFF4A171D.toInt()

        SdfPanelRenderer.draw(
            context = context,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            style = shellStyle(glowColor, neonColor),
        )

        visibleEntries.forEachIndexed { index, entry ->
            val rowY = bounds.y + Layout.padding + (index * (Layout.rowHeight + Layout.rowGap))
            drawEffectRow(context, client, client.font, bounds.x + Layout.padding, rowY, entry)
        }
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = ensureDragState(client)
        val bounds = lastBounds ?: PotionHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = Layout.shellWidth,
            height = shellHeightFor(3),
        )
        val handled = state.beginDrag(bounds, mouseEvent.x().toInt(), mouseEvent.y().toInt())
        return consumed || handled
    }

    fun onScreenMouseDrag(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        horizontalAmount: Double,
        verticalAmount: Double,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = dragState ?: return consumed
        if (!state.dragging) return consumed

        val hudHeight = lastBounds?.height ?: shellHeightFor(3)
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = Layout.shellWidth,
            hudHeight = hudHeight,
        )
        return true
    }

    fun onScreenMouseRelease(
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = dragState ?: return consumed
        val ended = state.endDrag()
        if (ended) {
            PotionHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawEffectRow(
        context: GuiGraphics,
        client: Minecraft,
        font: Font,
        x: Int,
        y: Int,
        entry: EffectEntry,
    ) {
        val effectHolder = entry.effectHolder
        val iconX = x
        val iconY = y
        val textX = iconX + Layout.iconSize + Layout.iconGap
        val timeTextWidth = font.width(vText(entry.durationText))
        val timeX = x + Layout.shellWidth - (Layout.padding * 2) - Layout.timeRightInset - timeTextWidth
        val nameMaxWidth = (timeX - textX - 8).coerceAtLeast(24)

        drawEffectIcon(context, client, effectHolder, iconX, iconY)

        val label = entry.label.takeIf { font.width(vText(it)) <= nameMaxWidth }
            ?: font.substrByWidth(vText(entry.label), nameMaxWidth).string
        context.drawString(font, vText(label), textX, y + 3, 0xFFF4F6FF.toInt(), false)
        context.drawString(font, vText(entry.durationText), timeX, y + 3, 0xFFF4F6FF.toInt(), false)
    }

    private fun drawEffectIcon(
        context: GuiGraphics,
        client: Minecraft,
        effectHolder: Holder<MobEffect>?,
        x: Int,
        y: Int,
    ) {
        if (effectHolder == null) {
            context.drawString(
                client.font,
                vText("P"),
                x + 4,
                y + 3,
                0xFFF4F6FF.toInt(),
                false,
            )
            return
        }

        val texture = resolveEffectTexture(client, effectHolder)
        if (texture != null) {
            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                0f,
                0f,
                Layout.iconSize,
                Layout.iconSize,
                Layout.iconSize,
                Layout.iconSize,
                Layout.iconSize,
                Layout.iconSize,
                0xFFFFFFFF.toInt(),
            )
        } else {
            context.drawString(
                client.font,
                vText("P"),
                x + 4,
                y + 3,
                0xFFF4F6FF.toInt(),
                false,
            )
        }
    }

    private fun activeEntries(effects: List<MobEffectInstance>): List<EffectEntry> {
        return effects
            .sortedWith(
                compareByDescending<MobEffectInstance> { it.amplifier }
                    .thenByDescending { if (it.isInfiniteDuration) Int.MAX_VALUE else it.duration }
                    .thenBy { effectName(it) }
            )
            .map { effect ->
                EffectEntry(
                    effect = effect,
                    effectHolder = effect.effect,
                    label = effectName(effect),
                    durationText = durationText(effect),
                )
            }
    }

    private fun previewEntries(): List<EffectEntry> {
        return listOf(
            EffectEntry(effect = null, effectHolder = null, label = "Speed", durationText = "1:28"),
            EffectEntry(effect = null, effectHolder = null, label = "Strength", durationText = "0:41"),
            EffectEntry(effect = null, effectHolder = null, label = "Regeneration", durationText = "2:05"),
        )
    }

    private fun effectName(effect: MobEffectInstance): String {
        val holder = effect.effect
        val base = holder.value().displayName.string
        return if (effect.amplifier > 0) "$base ${effect.amplifier + 1}" else base
    }

    private fun durationText(effect: MobEffectInstance): String {
        if (effect.isInfiniteDuration) return "inf"
        val totalSeconds = (effect.duration / 20).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun resolveEffectTexture(client: Minecraft, effectHolder: Holder<MobEffect>): Identifier? {
        val effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value()) ?: return null
        return Identifier.fromNamespaceAndPath(effectId.namespace, "textures/mob_effect/${effectId.path}.png")
    }

    private fun shellStyle(glowColor: Int, neonColor: Int): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF40C1118.toInt(),
            borderColor = 0x91353D4E.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Layout.shellRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.03f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(glowColor, radiusPx = 22f, strength = 0.18f, opacity = 0.11f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x18000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonColor, 0x88), widthPx = 1.0f, softnessPx = 5f, strength = 0.52f),
        )
    }

    private fun shellHeightFor(rowCount: Int): Int {
        return Layout.padding + (rowCount * Layout.rowHeight) + ((rowCount - 1).coerceAtLeast(0) * Layout.rowGap) + Layout.padding
    }

    private fun ensureDragState(client: Minecraft): PotionHudDragState {
        val current = dragState
        if (current != null) return current

        val defaultPosition = PotionHudPosition(
            x = Layout.anchorX,
            y = Layout.anchorY,
        )
        return PotionHudDragState(PotionHudPositionStore.load(defaultPosition)).also { loaded ->
            clampToScreen(client, loaded, shellHeightFor(3))
            dragState = loaded
        }
    }

    private fun clampToScreen(client: Minecraft, state: PotionHudDragState, hudHeight: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = Layout.shellWidth,
            hudHeight = hudHeight,
        )
    }
}
