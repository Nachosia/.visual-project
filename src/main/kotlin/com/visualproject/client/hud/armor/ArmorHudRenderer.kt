package com.visualproject.client.hud.armor

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.hud.HudOcclusionRegistry
import com.visualproject.client.render.sdf.BackdropBlurRenderer
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
        const val horizontalShellWidth = shellHeight
        const val horizontalShellHeight = shellWidth
        const val shellRadius = 18f
        const val padding = 8
        const val rowHeight = 16
        const val rowGap = 5
        const val slotSize = 16
        const val slotRadius = 6f
        const val barWidth = 4
        const val barGap = 1
        const val horizontalBarGap = 0
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

        val layoutType = ArmorHudModule.layoutType()
        val scale = hudScale()
        val baseWidth = shellWidth(layoutType)
        val baseHeight = shellHeight(layoutType)
        val actualWidth = scaled(baseWidth, scale)
        val actualHeight = scaled(baseHeight, scale)

        val state = ensureDragState(client, actualWidth, actualHeight)
        adjustPositionForScaleChange(client, state, baseWidth, baseHeight, scale)
        clampToScreen(client, state, layoutType)
        val bounds = ArmorHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds
        HudOcclusionRegistry.mark(bounds.x, bounds.y, bounds.width, bounds.height)

        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val slotBackgroundEnabled = ModuleStateStore.isSettingEnabled("${moduleId}:slot_background")
        val glowColor = if (accentSync) VisualThemeSettings.themedAccentGlowBase() else VisualThemeSettings.themedFallbackGlow(0xFF8A71FF.toInt())
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF8A71FF.toInt()
        BackdropBlurRenderer.captureBackdrop()

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = baseWidth,
            height = baseHeight,
            style = shellStyle(glowColor, neonColor),
        )

        val rows = listOf(
            ArmorRow("H", player.getItemBySlot(EquipmentSlot.HEAD)),
            ArmorRow("C", player.getItemBySlot(EquipmentSlot.CHEST)),
            ArmorRow("L", player.getItemBySlot(EquipmentSlot.LEGS)),
            ArmorRow("B", player.getItemBySlot(EquipmentSlot.FEET)),
        )

        when (layoutType) {
            ArmorHudModule.LayoutType.VERTICAL -> {
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
            }
            ArmorHudModule.LayoutType.RIGHT_90 -> {
                val contentX = ((baseWidth - horizontalContentWidth()) / 2).coerceAtLeast(0)
                val rowY = ((baseHeight - Layout.slotSize) / 2).coerceAtLeast(0)
                rows.forEachIndexed { index, row ->
                    val columnX = contentX + (index * horizontalColumnWidth())
                    drawArmorColumn(
                        context = context,
                        font = client.font,
                        row = row,
                        x = columnX,
                        y = rowY,
                        slotBackgroundEnabled = slotBackgroundEnabled,
                    )
                }
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

        val layoutType = ArmorHudModule.layoutType()
        val state = ensureDragState(client, scaled(shellWidth(layoutType), hudScale()), scaled(shellHeight(layoutType), hudScale()))
        clampToScreen(client, state, layoutType)

        val bounds = lastBounds ?: ArmorHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(shellWidth(layoutType), hudScale()),
            height = scaled(shellHeight(layoutType), hudScale()),
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
        val layoutType = ArmorHudModule.layoutType()
        val state = ensureDragState(client, scaled(shellWidth(layoutType), scale), scaled(shellHeight(layoutType), scale))
        if (!state.dragging) return consumed

        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(shellWidth(layoutType), scale),
            hudHeight = scaled(shellHeight(layoutType), scale),
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
                if (VisualThemeSettings.isTransparentPreset() || VisualThemeSettings.isLightPreset()) VisualThemeSettings.textSecondary() else 0xFFE8B6C4.toInt(),
                false,
            )
        } else {
            val itemX = x + ((Layout.slotSize - 16) / 2)
            val itemY = y + ((Layout.slotSize - 16) / 2)
            context.renderItem(row.stack, itemX, itemY)
        }

        val barX = x + Layout.slotSize + Layout.horizontalBarGap
        val ratio = durabilityRatio(row.stack)
        drawDurabilityBar(context, barX, y, ratio)
    }

    private fun drawArmorColumn(
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
                if (VisualThemeSettings.isTransparentPreset() || VisualThemeSettings.isLightPreset()) VisualThemeSettings.textSecondary() else 0xFFE8B6C4.toInt(),
                false,
            )
        } else {
            val itemX = x + ((Layout.slotSize - 16) / 2)
            val itemY = y + ((Layout.slotSize - 16) / 2)
            context.renderItem(row.stack, itemX, itemY)
        }

        val barX = x + Layout.slotSize + Layout.horizontalBarGap
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
        val allowGlow = !VisualThemeSettings.isTransparentPreset()

        val outerGlowColor = blendColor(0x00000000, fillColor, 0.14f)
        val innerGlowColor = blendColor(0x00000000, fillColor, 0.24f)
        val outerGlowTop = (fillTop - 2).coerceAtLeast(scaledInset - 2)
        val innerGlowTop = (fillTop - 1).coerceAtLeast(scaledInset - 1)
        val outerGlowBottom = (fillBottom + 2).coerceAtMost(scaledHeight - scaledInset + 2)
        val innerGlowBottom = (fillBottom + 1).coerceAtMost(scaledHeight - scaledInset + 1)
        val outerGlowHeight = (outerGlowBottom - outerGlowTop).coerceAtLeast(1)
        val innerGlowHeight = (innerGlowBottom - innerGlowTop).coerceAtLeast(1)

        if (allowGlow) {
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
        }
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

    private fun drawDurabilityBarHorizontal(
        context: GuiGraphics,
        x: Int,
        y: Int,
        ratio: Float,
    ) {
        if (ratio <= 0f) return

        val clampedRatio = ratio.coerceIn(0f, 1f)
        val usableWidth = (Layout.slotSize - 4).coerceAtLeast(1)
        val fillWidth = ceil(usableWidth * clampedRatio).toInt().coerceAtLeast(1)
        val fillX = x + 2
        val fillY = y
        val fillColor = durabilityColor(clampedRatio)
        val allowGlow = !VisualThemeSettings.isTransparentPreset()

        val outerGlowColor = blendColor(0x00000000, fillColor, 0.14f)
        val innerGlowColor = blendColor(0x00000000, fillColor, 0.24f)
        val outerGlowX = (fillX - 1).coerceAtLeast(x)
        val innerGlowX = fillX
        val outerGlowWidth = (fillWidth + 2).coerceAtMost(Layout.slotSize - (outerGlowX - x))
        val innerGlowWidth = fillWidth.coerceAtMost(Layout.slotSize - (innerGlowX - x))

        if (allowGlow) {
            fillRoundedRect(
                context,
                outerGlowX,
                fillY - 1,
                outerGlowWidth,
                Layout.barWidth + 2,
                4,
                outerGlowColor,
            )
            fillRoundedRect(
                context,
                innerGlowX,
                fillY,
                innerGlowWidth,
                Layout.barWidth,
                3,
                innerGlowColor,
            )
        }
        fillRoundedRect(
            context,
            fillX,
            fillY,
            fillWidth,
            Layout.barWidth,
            3,
            fillColor,
        )
        if (fillWidth > 2) {
            fillRoundedRect(
                context,
                fillX,
                fillY,
                fillWidth,
                1,
                1,
                blendColor(fillColor, 0xFFFFFFFF.toInt(), 0.10f),
            )
        }
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
            innerGlow = if (VisualThemeSettings.isTransparentPreset()) {
                SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.05f, opacity = 0.03f)
            } else {
                SdfGlowStyle.NONE
            },
            outerGlow = if (VisualThemeSettings.isLightPreset()) {
                SdfGlowStyle.NONE
            } else {
                SdfGlowStyle(glowColor, radiusPx = 20f, strength = 0.18f, opacity = 0.08f)
            },
            shade = if (VisualThemeSettings.isTransparentPreset()) {
                SdfShadeStyle(0x04FFFFFF, 0x10000000)
            } else {
                SdfShadeStyle(0x00000000, 0x00000000)
            },
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
                if (VisualThemeSettings.isTransparentPreset() || VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudIconFill() else 0xFF8A0922.toInt()
            } else {
                if (VisualThemeSettings.isTransparentPreset()) 0x00000000 else VisualThemeSettings.hudTrackFill()
            },
            borderColor = if (enabled) {
                if (VisualThemeSettings.isTransparentPreset() || VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudIconBorder() else 0xFF4B0613.toInt()
            } else {
                if (VisualThemeSettings.isTransparentPreset()) 0x00000000 else VisualThemeSettings.hudTrackBorder()
            },
            borderWidthPx = if (enabled) 1f else 0f,
            radiusPx = Layout.slotRadius,
            innerGlow = if (enabled && VisualThemeSettings.isTransparentPreset()) {
                SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 4f, strength = 0.04f, opacity = 0.03f)
            } else {
                SdfGlowStyle.NONE
            },
            outerGlow = SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f),
            shade = if (enabled && VisualThemeSettings.isTransparentPreset()) {
                SdfShadeStyle(0x04FFFFFF, 0x10000000)
            } else {
                SdfShadeStyle(0x00000000, 0x00000000)
            },
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

    private fun clampToScreen(client: Minecraft, state: ArmorHudDragState, layoutType: ArmorHudModule.LayoutType) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(shellWidth(layoutType), hudScale()),
            hudHeight = scaled(shellHeight(layoutType), hudScale()),
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

    private fun shellWidth(layoutType: ArmorHudModule.LayoutType): Int {
        return when (layoutType) {
            ArmorHudModule.LayoutType.VERTICAL -> Layout.shellWidth
            ArmorHudModule.LayoutType.RIGHT_90 -> Layout.horizontalShellWidth
        }
    }

    private fun shellHeight(layoutType: ArmorHudModule.LayoutType): Int {
        return when (layoutType) {
            ArmorHudModule.LayoutType.VERTICAL -> Layout.shellHeight
            ArmorHudModule.LayoutType.RIGHT_90 -> Layout.horizontalShellHeight
        }
    }

    private fun horizontalColumnWidth(): Int {
        return Layout.slotSize + Layout.horizontalBarGap + Layout.barWidth
    }

    private fun horizontalContentWidth(): Int {
        return horizontalColumnWidth() * 4
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

}
