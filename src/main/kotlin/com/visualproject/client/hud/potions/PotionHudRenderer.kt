package com.visualproject.client.hud.potions

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.BackdropBlurRenderer
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
import kotlin.math.roundToInt

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
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return
        val scale = hudScale()

        val entries = activeEntries(player.activeEffects.toList())
        if (entries.isEmpty() && client.screen !is ChatScreen) {
            return
        }

        val visibleEntries = if (entries.isNotEmpty()) entries else previewEntries()
        val baseShellHeight = shellHeightFor(visibleEntries.size)

        val state = ensureDragState(client)
        adjustPositionForScaleChange(client, state, Layout.shellWidth, baseShellHeight, scale)
        val actualWidth = scaled(Layout.shellWidth, scale)
        val actualHeight = scaled(baseShellHeight, scale)
        clampToScreen(client, state, actualWidth, actualHeight)
        val bounds = PotionHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds

        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF7A2730.toInt()
        val glowColor = if (accentSync) VisualThemeSettings.themedAccentGlowBase() else VisualThemeSettings.themedFallbackGlow(0xFF4A171D.toInt())
        BackdropBlurRenderer.captureBackdrop()

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = Layout.shellWidth,
            height = baseShellHeight,
            style = shellStyle(glowColor, neonColor),
        )

        visibleEntries.forEachIndexed { index, entry ->
            val rowY = Layout.padding + (index * (Layout.rowHeight + Layout.rowGap))
            drawEffectRow(context, client, client.font, Layout.padding, rowY, entry)
        }

        context.pose().popMatrix()
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val scale = hudScale()
        val state = ensureDragState(client)
        val bounds = lastBounds ?: PotionHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(Layout.shellWidth, scale),
            height = scaled(shellHeightFor(3), scale),
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

        val scale = hudScale()
        val hudHeight = lastBounds?.height ?: scaled(shellHeightFor(3), scale)
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(Layout.shellWidth, scale),
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
        context.drawString(font, vText(label), textX, y + 3, VisualThemeSettings.textPrimary(), false)
        context.drawString(font, vText(entry.durationText), timeX, y + 3, VisualThemeSettings.textPrimary(), false)
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
                VisualThemeSettings.textPrimary(),
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
                VisualThemeSettings.textPrimary(),
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
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.2f,
            radiusPx = Layout.shellRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.03f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(glowColor, radiusPx = 22f, strength = if (VisualThemeSettings.isLightPreset()) 0.14f else 0.18f, opacity = if (VisualThemeSettings.isLightPreset()) 0.08f else 0.11f),
            shade = SdfShadeStyle(VisualThemeSettings.hudShellShadeTop(), VisualThemeSettings.hudShellShadeBottom()),
            neonBorder = SdfNeonBorderStyle(
                VisualThemeSettings.withAlpha(neonColor, if (VisualThemeSettings.isLightPreset()) 0x62 else 0x88),
                widthPx = 1.0f,
                softnessPx = 5f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.36f else 0.52f,
            ),
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
            clampToScreen(client, loaded, scaled(Layout.shellWidth, hudScale()), scaled(shellHeightFor(3), hudScale()))
            dragState = loaded
        }
    }

    private fun clampToScreen(client: Minecraft, state: PotionHudDragState, hudWidth: Int, hudHeight: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = hudWidth,
            hudHeight = hudHeight,
        )
    }

    private fun adjustPositionForScaleChange(
        client: Minecraft,
        state: PotionHudDragState,
        baseWidth: Int,
        baseHeight: Int,
        scale: Float,
    ) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (kotlin.math.abs(lastScale - scale) < 0.001f || state.dragging) {
            lastScale = scale
            return
        }

        val oldWidth = scaled(baseWidth, lastScale)
        val oldHeight = scaled(baseHeight, lastScale)
        val newWidth = scaled(baseWidth, scale)
        val newHeight = scaled(baseHeight, scale)
        val targetX = state.position.x - ((newWidth - oldWidth) / 2)
        val targetY = state.position.y - ((newHeight - oldHeight) / 2)
        state.setPositionClamped(
            x = targetX,
            y = targetY,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = newWidth,
            hudHeight = newHeight,
        )
        PotionHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }
}
