package com.visualproject.client.hud.inv

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.BackdropBlurRenderer
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.roundToInt

internal class InvHudRenderer {

    private object Layout {
        const val columns = 9
        const val slotSize = 20
        const val slotGap = 4
        const val padding = 10
        const val rowGap = 4
        const val radius = 18f
        const val defaultX = 14
        const val defaultY = 132
    }

    private var dragState: InvHudDragState? = null
    private var lastBounds: InvHudBounds? = null
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val scale = hudScale()
        val actualWidth = scaled(shellWidth(), scale)
        val actualHeight = scaled(shellHeight(), scale)
        val state = ensureDragState(client, actualWidth, actualHeight)
        adjustPositionForScaleChange(client, state, scale)
        clampToScreen(client, state, actualWidth, actualHeight)

        val bounds = InvHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds
        BackdropBlurRenderer.captureBackdrop()

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = shellWidth(),
            height = shellHeight(),
            style = shellStyle(),
        )

        for (row in 0 until 3) {
            for (column in 0 until Layout.columns) {
                val slotIndex = 9 + (row * Layout.columns) + column
                drawSlot(
                    context = context,
                    font = client.font,
                    stack = player.inventory.getItem(slotIndex),
                    x = slotX(column),
                    y = rowY(row),
                )
            }
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
        val bounds = lastBounds ?: InvHudBounds(
            x = ensureDragState(client, scaled(shellWidth(), scale), scaled(shellHeight(), scale)).position.x,
            y = ensureDragState(client, scaled(shellWidth(), scale), scaled(shellHeight(), scale)).position.y,
            width = scaled(shellWidth(), scale),
            height = scaled(shellHeight(), scale),
        )
        val handled = ensureDragState(client, bounds.width, bounds.height)
            .beginDrag(bounds, mouseEvent.x().toInt(), mouseEvent.y().toInt())
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
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(shellWidth(), scale),
            hudHeight = scaled(shellHeight(), scale),
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
            InvHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawSlot(
        context: GuiGraphics,
        font: Font,
        stack: ItemStack,
        x: Int,
        y: Int,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = Layout.slotSize,
            height = Layout.slotSize,
            style = slotStyle(!stack.isEmpty),
        )
        if (stack.isEmpty) return

        val itemX = x + ((Layout.slotSize - 16) / 2)
        val itemY = y + ((Layout.slotSize - 16) / 2)
        context.renderItem(stack, itemX, itemY)
        context.renderItemDecorations(font, stack, itemX, itemY)
    }

    private fun shellStyle(): SdfPanelStyle {
        val accentSync = ModuleStateStore.isSettingEnabled("${InvHudModule.moduleId}:accent_sync")
        val accent = if (accentSync) VisualThemeSettings.accentStrong() else 0xFF61D27D.toInt()
        val neon = if (accentSync) VisualThemeSettings.neonBorder() else accent
        return SdfPanelStyle(
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.15f,
            radiusPx = Layout.radius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.05f, opacity = 0.04f),
            outerGlow = if (VisualThemeSettings.themeAllowsOuterGlow()) {
                SdfGlowStyle(VisualThemeSettings.themedAccentGlowBase(accent), radiusPx = 18f, strength = 0.14f, opacity = 0.08f)
            } else {
                SdfGlowStyle.NONE
            },
            shade = SdfShadeStyle(VisualThemeSettings.hudShellShadeTop(), VisualThemeSettings.hudShellShadeBottom()),
            neonBorder = if (VisualThemeSettings.themeAllowsNeon()) {
                SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neon, 0xA2), widthPx = 0.95f, softnessPx = 4.5f, strength = 0.46f)
            } else {
                SdfNeonBorderStyle.NONE
            },
        )
    }

    private fun slotStyle(filled: Boolean): SdfPanelStyle {
        val base = if (filled) VisualThemeSettings.hudIconFill() else VisualThemeSettings.hudTrackFill()
        val border = if (filled) VisualThemeSettings.hudIconBorder() else VisualThemeSettings.hudTrackBorder()
        return SdfPanelStyle(
            baseColor = base,
            borderColor = border,
            borderWidthPx = 1.0f,
            radiusPx = 8f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.03f, opacity = 0.02f),
            outerGlow = SdfGlowStyle.NONE,
            shade = SdfShadeStyle(
                if (VisualThemeSettings.isTransparentPreset()) 0x04FFFFFF else 0x08FFFFFF,
                if (VisualThemeSettings.isTransparentPreset()) 0x0A000000 else 0x10000000,
            ),
            neonBorder = SdfNeonBorderStyle.NONE,
        )
    }

    private fun slotX(column: Int): Int {
        return Layout.padding + (column * (Layout.slotSize + Layout.slotGap))
    }

    private fun rowY(row: Int): Int {
        return Layout.padding + (row * (Layout.slotSize + Layout.rowGap))
    }

    private fun shellWidth(): Int {
        return (Layout.padding * 2) + (Layout.columns * Layout.slotSize) + ((Layout.columns - 1) * Layout.slotGap)
    }

    private fun shellHeight(): Int {
        return (Layout.padding * 2) + (3 * Layout.slotSize) + (2 * Layout.rowGap)
    }

    private fun ensureDragState(client: Minecraft, hudWidth: Int, hudHeight: Int): InvHudDragState {
        val existing = dragState
        if (existing != null) return existing

        val defaultPosition = InvHudPosition(
            x = Layout.defaultX.coerceIn(0, (client.window.guiScaledWidth - hudWidth).coerceAtLeast(0)),
            y = Layout.defaultY.coerceIn(0, (client.window.guiScaledHeight - hudHeight).coerceAtLeast(0)),
        )
        return InvHudDragState(InvHudPositionStore.load(defaultPosition)).also { dragState = it }
    }

    private fun clampToScreen(client: Minecraft, state: InvHudDragState, hudWidth: Int, hudHeight: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = hudWidth,
            hudHeight = hudHeight,
        )
    }

    private fun adjustPositionForScaleChange(client: Minecraft, state: InvHudDragState, scale: Float) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (abs(lastScale - scale) < 0.001f || state.dragging) {
            lastScale = scale
            return
        }

        val oldWidth = scaled(shellWidth(), lastScale)
        val oldHeight = scaled(shellHeight(), lastScale)
        val newWidth = scaled(shellWidth(), scale)
        val newHeight = scaled(shellHeight(), scale)
        state.setPositionClamped(
            x = state.position.x - ((newWidth - oldWidth) / 2),
            y = state.position.y - ((newHeight - oldHeight) / 2),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = newWidth,
            hudHeight = newHeight,
        )
        InvHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${InvHudModule.moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }
}
