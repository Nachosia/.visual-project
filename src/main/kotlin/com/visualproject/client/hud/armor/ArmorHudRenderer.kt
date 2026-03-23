package com.visualproject.client.hud.armor

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.ui.menu.fillRoundedRect
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class ArmorHudRenderer {

    companion object {
        private const val moduleId = "armor_hud"
    }

    private object Layout {
        const val shellWidth = 37
        const val shellHeight = 95
        const val shellRadius = 18f
        const val padding = 8
        const val rowHeight = 16
        const val rowGap = 5
        const val slotSize = 16
        const val slotRadius = 6f
        const val barWidth = 4
        const val barGap = 1
        const val anchorX = 8
    }

    private data class ArmorRow(
        val label: String,
        val stack: ItemStack,
    )

    private var dragState: ArmorHudDragState? = null
    private var lastBounds: ArmorHudBounds? = null
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val scale = hudScale()
        val actualWidth = scaled(Layout.shellWidth, scale)
        val actualHeight = scaled(Layout.shellHeight, scale)

        val state = ensureDragState(client, actualWidth, actualHeight)
        adjustPositionForScaleChange(client, state, Layout.shellWidth, Layout.shellHeight, scale)
        clampToScreen(client, state)
        val bounds = ArmorHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds

        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val slotBackgroundEnabled = ModuleStateStore.isSettingEnabled("${moduleId}:slot_background")
        val glowColor = if (accentSync) VisualThemeSettings.themedAccentGlowBase() else VisualThemeSettings.themedFallbackGlow(0xFF8A71FF.toInt())
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF8A71FF.toInt()

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = Layout.shellWidth,
            height = Layout.shellHeight,
            style = shellStyle(glowColor, neonColor),
        )

        val rows = listOf(
            ArmorRow("H", player.getItemBySlot(EquipmentSlot.HEAD)),
            ArmorRow("C", player.getItemBySlot(EquipmentSlot.CHEST)),
            ArmorRow("L", player.getItemBySlot(EquipmentSlot.LEGS)),
            ArmorRow("B", player.getItemBySlot(EquipmentSlot.FEET)),
        )

        rows.forEachIndexed { index, row ->
            val rowY = Layout.padding + (index * (Layout.rowHeight + Layout.rowGap))
            drawArmorRow(
                context = context,
                font = client.font,
                row = row,
                x = Layout.padding,
                y = rowY,
                slotBackgroundEnabled = slotBackgroundEnabled,
            )
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

        val state = ensureDragState(client, scaled(Layout.shellWidth, hudScale()), scaled(Layout.shellHeight, hudScale()))
        clampToScreen(client, state)

        val bounds = lastBounds ?: ArmorHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(Layout.shellWidth, hudScale()),
            height = scaled(Layout.shellHeight, hudScale()),
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

        val scale = hudScale()
        val state = ensureDragState(client, scaled(Layout.shellWidth, scale), scaled(Layout.shellHeight, scale))
        if (!state.dragging) return consumed

        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(Layout.shellWidth, scale),
            hudHeight = scaled(Layout.shellHeight, scale),
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
            ArmorHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawArmorRow(
        context: GuiGraphics,
        font: Font,
        row: ArmorRow,
        x: Int,
        y: Int,
        slotBackgroundEnabled: Boolean,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = Layout.slotSize,
            height = Layout.slotSize,
            style = slotStyle(slotBackgroundEnabled),
        )

        if (row.stack.isEmpty) {
            val labelWidth = font.width(vText(row.label))
            context.drawString(
                font,
                vText(row.label),
                x + ((Layout.slotSize - labelWidth) / 2),
                y + ((Layout.slotSize - font.lineHeight) / 2),
                if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.textSecondary() else 0xFFE8B6C4.toInt(),
                false,
            )
        } else {
            val itemX = x + ((Layout.slotSize - 16) / 2)
            val itemY = y + ((Layout.slotSize - 16) / 2)
            context.renderItem(row.stack, itemX, itemY)
        }

        val barX = x + Layout.slotSize + Layout.barGap
        val ratio = durabilityRatio(row.stack)
        drawDurabilityBar(context, barX, y, ratio)
    }

    private fun drawDurabilityBar(
        context: GuiGraphics,
        x: Int,
        y: Int,
        ratio: Float,
    ) {
        if (ratio <= 0f) return

        val scaleY = 0.25f
        val scaledHeight = Layout.rowHeight * 4
        val scaledInset = 8

        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(1f, scaleY)

        val clampedRatio = ratio.coerceIn(0f, 1f)
        val usableHeight = (scaledHeight - (scaledInset * 2)).coerceAtLeast(1)
        val fillHeight = ceil(usableHeight * clampedRatio).toInt().coerceAtLeast(1)
        val fillTop = (scaledHeight - scaledInset - fillHeight).coerceAtLeast(scaledInset)
        val fillBottom = fillTop + fillHeight
        val fillColor = durabilityColor(clampedRatio)
        val fillX = 1
        val fillWidth = (Layout.barWidth - 2).coerceAtLeast(1)

        val outerGlowColor = blendColor(0x00000000, fillColor, 0.14f)
        val innerGlowColor = blendColor(0x00000000, fillColor, 0.24f)
        val outerGlowTop = (fillTop - 2).coerceAtLeast(scaledInset - 2)
        val innerGlowTop = (fillTop - 1).coerceAtLeast(scaledInset - 1)
        val outerGlowBottom = (fillBottom + 2).coerceAtMost(scaledHeight - scaledInset + 2)
        val innerGlowBottom = (fillBottom + 1).coerceAtMost(scaledHeight - scaledInset + 1)
        val outerGlowHeight = (outerGlowBottom - outerGlowTop).coerceAtLeast(1)
        val innerGlowHeight = (innerGlowBottom - innerGlowTop).coerceAtLeast(1)

        fillRoundedRect(
            context,
            fillX - 1,
            outerGlowTop,
            fillWidth + 2,
            outerGlowHeight,
            4,
            outerGlowColor,
        )
        fillRoundedRect(
            context,
            fillX,
            innerGlowTop,
            fillWidth,
            innerGlowHeight,
            3,
            innerGlowColor,
        )
        fillRoundedRect(
            context,
            fillX,
            fillTop,
            fillWidth,
            fillHeight,
            3,
            fillColor,
        )
        if (fillHeight > 2) {
            fillRoundedRect(
                context,
                fillX,
                fillTop,
                fillWidth,
                1,
                1,
                blendColor(fillColor, 0xFFFFFFFF.toInt(), 0.10f),
            )
        }

        context.pose().popMatrix()
    }

    private fun durabilityColor(ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val semanticColor = if (clamped >= 0.5f) {
            blendColor(0xFFF2D64B.toInt(), 0xFF2EEA6A.toInt(), (clamped - 0.5f) / 0.5f)
        } else {
            blendColor(0xFFE84C4F.toInt(), 0xFFF2D64B.toInt(), clamped / 0.5f)
        }
        return blendColor(0xFF17212A.toInt(), semanticColor, 0.72f)
    }

    private fun durabilityRatio(stack: ItemStack): Float {
        val maxDurability = stack.maxDamage
        if (stack.isEmpty || !stack.isDamageableItem || maxDurability <= 0) {
            return 0f
        }

        val currentDurability = (maxDurability - stack.damageValue).coerceAtLeast(0)
        return currentDurability.toFloat() / maxDurability.toFloat()
    }

    private fun shellStyle(glowColor: Int, neonColor: Int): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.1f,
            radiusPx = Layout.shellRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.05f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(glowColor, radiusPx = 20f, strength = if (VisualThemeSettings.isLightPreset()) 0.14f else 0.18f, opacity = if (VisualThemeSettings.isLightPreset()) 0.07f else 0.08f),
            shade = SdfShadeStyle(
                if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x0AFFFFFF,
                if (VisualThemeSettings.isLightPreset()) 0x0ED0DBEA else 0x16000000,
            ),
            neonBorder = SdfNeonBorderStyle(
                color = VisualThemeSettings.withAlpha(neonColor, if (VisualThemeSettings.isLightPreset()) 0x78 else 0xB2),
                widthPx = 1.0f,
                softnessPx = 5f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.38f else 0.58f,
            ),
        )
    }

    private fun slotStyle(enabled: Boolean): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = if (enabled) {
                if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudIconFill() else 0xFF8A0922.toInt()
            } else {
                0x00000000
            },
            borderColor = if (enabled) {
                if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudIconBorder() else 0xFF4B0613.toInt()
            } else {
                0x00000000
            },
            borderWidthPx = if (enabled) 1f else 0f,
            radiusPx = Layout.slotRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 4f, strength = if (enabled) 0.04f else 0f, opacity = if (enabled) 0.03f else 0f),
            outerGlow = SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f),
            shade = SdfShadeStyle(
                if (!enabled) 0x00000000 else if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x12000000,
                if (!enabled) 0x00000000 else if (VisualThemeSettings.isLightPreset()) 0x0CCFDCED else 0x22000000,
            ),
        )
    }

    private fun ensureDragState(client: Minecraft, hudWidth: Int, hudHeight: Int): ArmorHudDragState {
        val existing = dragState
        if (existing != null) return existing

        val defaultPosition = ArmorHudPosition(
            x = Layout.anchorX,
            y = ((client.window.guiScaledHeight - hudHeight) / 2).coerceAtLeast(12),
        )
        return ArmorHudDragState(ArmorHudPositionStore.load(defaultPosition)).also {
            dragState = it
        }
    }

    private fun clampToScreen(client: Minecraft, state: ArmorHudDragState) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(Layout.shellWidth, hudScale()),
            hudHeight = scaled(Layout.shellHeight, hudScale()),
        )
    }

    private fun adjustPositionForScaleChange(
        client: Minecraft,
        state: ArmorHudDragState,
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
        ArmorHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

}
