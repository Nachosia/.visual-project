package com.visualproject.client.hud.itembar

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.hud.HudOcclusionRegistry
import com.visualproject.client.vText
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.roundToInt

internal class ItemBarHudRenderer {

    companion object {
        private const val moduleId = ItemBarHudModule.moduleId
    }

    private object Layout {
        const val slotSize = 22
        const val slotGap = 3
        const val offhandGap = 8
        const val slotRadius = 8f
        const val statusBarHeight = 9
        const val statusSplitGap = 16
        const val statusRowGap = 3
        const val statusArmorY = 0
        const val statusHealthY = statusArmorY + statusBarHeight + statusRowGap
        const val statusXpY = statusHealthY + statusBarHeight + statusRowGap
        const val statusHeight = statusXpY + statusBarHeight
        const val statusGap = 5
        const val compactStatusRowHeight = 15
        const val compactStatusGap = 4
        const val compactStatusTextGap = 5
        const val compactStatusBarGap = 6
        const val compactStatusShellPaddingX = 5
        const val compactStatusShellPaddingY = 2
        const val compactShellOverflow = 2
        const val compactInlineValueGap = 4
        const val compactAfterHpTextGap = 6
        const val compactStatusTextScale = 0.52f
        const val compactLevelTextScale = 0.60f
        const val compactSlotIndexScale = 0.48f
        const val compactSlotIndexInsetX = 3
        const val compactSlotIndexInsetY = 3
        const val compactStatusValueOffsetY = 1
        const val compactSlotWidth = 24
        const val compactSlotHeight = 25
        const val compactItemOffsetY = -1
        const val compactHotbarRadius = 8f
        const val compactMainShellInset = 1
        const val compactSelectedInset = 1
        const val verticalStatusShellPaddingX = 5
        const val verticalStatusShellPaddingY = 6
        const val verticalStatusBarWidth = 9
        const val verticalStatusBarGap = 4
        const val verticalStatusTextGap = 4
        const val verticalStatusTextAreaHeight = 8
        const val defaultBottomInset = 8
    }

    private data class CompactStatusGeometry(
        val hpTextWidth: Int,
        val levelTextWidth: Int,
        val hpBarExtent: Int,
        val armorBarExtent: Int,
        val foodBarExtent: Int,
        val xpBarExtent: Int,
    ) {
        val maxBarExtent: Int
            get() = maxOf(hpBarExtent, armorBarExtent, foodBarExtent, xpBarExtent)
    }

    private var dragState: ItemBarHudDragState? = null
    private var lastBounds: ItemBarHudBounds? = null
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen && client.screen !is InventoryScreen) return

        val layoutType = ItemBarHudModule.layoutType()
        val scale = hudScale()
        val showPlayerStatus = ModuleStateStore.isSettingEnabled(ItemBarHudModule.showPlayerStatusKey)
        val baseWidth = shellWidth(showPlayerStatus, layoutType)
        val baseHeight = shellHeight(showPlayerStatus, layoutType)
        val actualWidth = scaled(baseWidth, scale)
        val actualHeight = scaled(baseHeight, scale)

        val state = ensureDragState(client, actualWidth, actualHeight)
        adjustPositionForScaleChange(client, state, baseWidth, baseHeight, scale)
        clampToScreen(client, state, actualWidth, actualHeight)

        val bounds = ItemBarHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds
        HudOcclusionRegistry.mark(bounds.x, bounds.y, bounds.width, bounds.height)

        val inventory = player.inventory
        val selectedSlot = inventory.selectedSlot
        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val slotY = slotTop(showPlayerStatus, layoutType)
        val hotbarStartX = hotbarStartX(layoutType)
        BackdropBlurRenderer.captureBackdrop()

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        if (showPlayerStatus) {
            when (layoutType) {
                ItemBarHudModule.LayoutType.PANEL -> drawPlayerStatus(
                    context = context,
                    font = client.font,
                    client = client,
                    hotbarX = hotbarStartX,
                )
                ItemBarHudModule.LayoutType.COMPACT -> drawCompactStatusRow(
                    context = context,
                    font = client.font,
                    client = client,
                    hotbarX = hotbarStartX,
                )
                ItemBarHudModule.LayoutType.VERTICAL -> drawVerticalStatusColumn(
                    context = context,
                    font = client.font,
                    client = client,
                    x = Layout.compactSlotWidth + Layout.offhandGap,
                    y = verticalStatusY(),
                )
            }
        }

        when (layoutType) {
            ItemBarHudModule.LayoutType.PANEL -> {
                drawSlot(
                    context = context,
                    font = client.font,
                    stack = player.offhandItem,
                    x = 0,
                    y = slotY,
                    selected = false,
                    accentSync = accentSync,
                )

                for (slot in 0 until 9) {
                    drawSlot(
                        context = context,
                        font = client.font,
                        stack = inventory.getItem(slot),
                        x = hotbarStartX + (slot * (Layout.slotSize + Layout.slotGap)),
                        y = slotY,
                        selected = slot == selectedSlot,
                        accentSync = accentSync,
                    )
                }
            }
            ItemBarHudModule.LayoutType.COMPACT -> {
                drawCompactSlots(
                    context = context,
                    font = client.font,
                    player = player,
                    selectedSlot = selectedSlot,
                    slotY = slotY,
                    hotbarStartX = hotbarStartX,
                    accentSync = accentSync,
                )
            }
            ItemBarHudModule.LayoutType.VERTICAL -> {
                drawVerticalSlots(
                    context = context,
                    font = client.font,
                    player = player,
                    selectedSlot = selectedSlot,
                    accentSync = accentSync,
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
        val showPlayerStatus = ModuleStateStore.isSettingEnabled(ItemBarHudModule.showPlayerStatusKey)
        val layoutType = ItemBarHudModule.layoutType()
        val state = ensureDragState(client, scaled(shellWidth(showPlayerStatus, layoutType), scale), scaled(shellHeight(showPlayerStatus, layoutType), scale))
        val bounds = lastBounds ?: ItemBarHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(shellWidth(showPlayerStatus, layoutType), scale),
            height = scaled(shellHeight(showPlayerStatus, layoutType), scale),
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

        val layoutType = ItemBarHudModule.layoutType()
        val scale = hudScale()
        val showPlayerStatus = ModuleStateStore.isSettingEnabled(ItemBarHudModule.showPlayerStatusKey)
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(shellWidth(showPlayerStatus, layoutType), scale),
            hudHeight = scaled(shellHeight(showPlayerStatus, layoutType), scale),
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
            ItemBarHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawSlot(
        context: GuiGraphics,
        font: Font,
        stack: ItemStack,
        x: Int,
        y: Int,
        selected: Boolean,
        accentSync: Boolean,
    ) {
        val filled = !stack.isEmpty
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = Layout.slotSize,
            height = Layout.slotSize,
            style = slotStyle(selected, filled, accentSync),
        )

        if (!filled) return

        val itemX = x + ((Layout.slotSize - 16) / 2)
        val itemY = y + ((Layout.slotSize - 16) / 2)
        context.renderItem(stack, itemX, itemY)
        context.renderItemDecorations(font, stack, itemX, itemY)
    }

    private fun drawCompactSlots(
        context: GuiGraphics,
        font: Font,
        player: net.minecraft.client.player.LocalPlayer,
        selectedSlot: Int,
        slotY: Int,
        hotbarStartX: Int,
        accentSync: Boolean,
    ) {
        drawCompactSlotShell(
            context = context,
            x = 0,
            y = slotY,
            width = Layout.compactSlotWidth,
            height = Layout.compactSlotHeight,
            selected = false,
            accentSync = accentSync,
        )
        val offhand = player.offhandItem
        if (!offhand.isEmpty) {
            val itemX = ((Layout.compactSlotWidth - 16) / 2)
            val itemY = slotY + (((Layout.compactSlotHeight - 16) / 2) + Layout.compactItemOffsetY)
            context.renderItem(offhand, itemX, itemY)
            context.renderItemDecorations(font, offhand, itemX, itemY)
        }

        SdfPanelRenderer.draw(
            context = context,
            x = hotbarStartX - Layout.compactShellOverflow,
            y = slotY,
            width = hotbarWidth(ItemBarHudModule.LayoutType.COMPACT) + (Layout.compactShellOverflow * 2),
            height = Layout.compactSlotHeight,
            style = compactMainShellStyle(),
        )

        for (slot in 0 until 9) {
            val slotX = hotbarStartX + (slot * (Layout.compactSlotWidth + Layout.slotGap))
            if (slot > 0) {
                val separatorX = slotX - ((Layout.slotGap + 1) / 2)
                context.fill(
                    separatorX,
                    slotY + 4,
                    separatorX + 1,
                    slotY + Layout.compactSlotHeight - 4,
                    compactSeparatorColor(),
                )
            }
            if (slot == selectedSlot) {
                drawCompactSlotShell(
                    context = context,
                    x = slotX + Layout.compactSelectedInset,
                    y = slotY + Layout.compactSelectedInset,
                    width = Layout.compactSlotWidth - (Layout.compactSelectedInset * 2),
                    height = Layout.compactSlotHeight - (Layout.compactSelectedInset * 2),
                    selected = true,
                    accentSync = accentSync,
                )
            }

            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) {
                val itemX = slotX + ((Layout.compactSlotWidth - 16) / 2)
                val itemY = slotY + (((Layout.compactSlotHeight - 16) / 2) + Layout.compactItemOffsetY)
                context.renderItem(stack, itemX, itemY)
                context.renderItemDecorations(font, stack, itemX, itemY)
            }

            if (stack.count <= 1) {
                val slotLabel = vText((slot + 1).toString())
                val labelWidth = scaledTextWidth(font, slotLabel, Layout.compactSlotIndexScale)
                val labelHeight = scaledTextHeight(font, Layout.compactSlotIndexScale)
                val labelInsetX = if (slot == 8) Layout.compactSlotIndexInsetX + 2 else Layout.compactSlotIndexInsetX
                drawScaledText(
                    context = context,
                    font = font,
                    text = slotLabel,
                    x = slotX + Layout.compactSlotWidth - labelWidth - labelInsetX,
                    y = slotY + Layout.compactSlotHeight - labelHeight - Layout.compactSlotIndexInsetY,
                    color = compactSlotIndexColor(slot == selectedSlot),
                    scale = Layout.compactSlotIndexScale,
                )
            }
        }
    }

    private fun drawCompactStatusRow(
        context: GuiGraphics,
        font: Font,
        client: Minecraft,
        hotbarX: Int,
    ) {
        val player = client.player ?: return
        val hotbarWidth = hotbarWidth(ItemBarHudModule.LayoutType.COMPACT)
        val maxHealth = player.maxHealth.coerceAtLeast(1f)
        val currentHealth = player.health.coerceAtLeast(0f)
        val absorptionAmount = player.absorptionAmount.coerceAtLeast(0f)
        val healthRatio = (currentHealth / maxHealth).coerceIn(0f, 1f)
        val absorptionRatio = (absorptionAmount / maxHealth).coerceAtLeast(0f)
        val armorRatio = (player.armorValue.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val foodRatio = (player.foodData.foodLevel.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val xpRatio = player.experienceProgress.coerceIn(0f, 1f)

        val hpText = currentHealth.roundToInt().coerceAtLeast(0).toString()
        val hpComponent = vText(hpText)
        val levelComponent = vText(player.experienceLevel.coerceAtLeast(0).toString())
        val geometry = compactStatusGeometry(
            hpTextWidth = scaledTextWidth(font, hpComponent, Layout.compactStatusTextScale),
            levelTextWidth = scaledTextWidth(font, levelComponent, Layout.compactLevelTextScale),
        )
        val barsShellX = hotbarX - Layout.compactShellOverflow
        val barsShellWidth = hotbarWidth + (Layout.compactShellOverflow * 2)
        val shellInnerX = barsShellX + Layout.compactStatusShellPaddingX
        val barY = ((Layout.compactStatusRowHeight - Layout.statusBarHeight) / 2).coerceAtLeast(0)
        val hpBarX = shellInnerX
        val hpTextX = hpBarX + geometry.hpBarExtent + Layout.compactInlineValueGap
        val armorBarX = hpTextX + geometry.hpTextWidth + Layout.compactAfterHpTextGap
        val foodBarX = armorBarX + geometry.armorBarExtent + Layout.compactStatusBarGap
        val xpBarX = foodBarX + geometry.foodBarExtent + Layout.compactStatusBarGap
        val levelTextX = xpBarX + geometry.xpBarExtent + Layout.compactInlineValueGap

        SdfPanelRenderer.draw(
            context = context,
            x = barsShellX,
            y = 0,
            width = barsShellWidth,
            height = Layout.compactStatusRowHeight,
            style = compactStatusShellStyle(),
        )

        drawHealthStatusBar(
            context = context,
            x = hpBarX,
            y = barY,
            width = geometry.hpBarExtent,
            healthRatio = healthRatio,
            absorptionRatio = absorptionRatio,
            currentHealth = currentHealth,
            absorptionAmount = absorptionAmount,
            maxHealth = maxHealth,
        )
        drawStatusBar(
            context = context,
            x = armorBarX,
            y = barY,
            width = geometry.armorBarExtent,
            segments = listOf(StatusSegment(armorRatio, armorStatusColor())),
        )
        drawStatusBar(
            context = context,
            x = foodBarX,
            y = barY,
            width = geometry.foodBarExtent,
            segments = listOf(StatusSegment(foodRatio, foodStatusColor())),
        )
        drawStatusBar(
            context = context,
            x = xpBarX,
            y = barY,
            width = geometry.xpBarExtent,
            segments = listOf(StatusSegment(xpRatio, xpStatusColor())),
        )

        val barCenterY = barY + (Layout.statusBarHeight / 2f)
        val levelTextY = (barCenterY - (scaledTextHeight(font, Layout.compactLevelTextScale) / 2f)).roundToInt()
            .plus(Layout.compactStatusValueOffsetY)
            .coerceAtLeast(0)
        val hpTextY = (barCenterY - (scaledTextHeight(font, Layout.compactStatusTextScale) / 2f)).roundToInt()
            .plus(Layout.compactStatusValueOffsetY)
            .coerceAtLeast(0)

        drawScaledText(
            context = context,
            font = font,
            text = hpComponent,
            x = hpTextX,
            y = hpTextY,
            color = healthStatusColor(),
            scale = Layout.compactStatusTextScale,
        )
        drawScaledText(
            context = context,
            font = font,
            text = levelComponent,
            x = levelTextX,
            y = levelTextY,
            color = xpStatusColor(),
            scale = Layout.compactLevelTextScale,
        )
    }

    private fun drawVerticalSlots(
        context: GuiGraphics,
        font: Font,
        player: net.minecraft.client.player.LocalPlayer,
        selectedSlot: Int,
        accentSync: Boolean,
    ) {
        drawCompactSlotShell(
            context = context,
            x = 0,
            y = 0,
            width = Layout.compactSlotWidth,
            height = Layout.compactSlotHeight,
            selected = false,
            accentSync = accentSync,
        )
        val offhand = player.offhandItem
        if (!offhand.isEmpty) {
            val itemX = ((Layout.compactSlotWidth - 16) / 2)
            val itemY = (((Layout.compactSlotHeight - 16) / 2) + Layout.compactItemOffsetY)
            context.renderItem(offhand, itemX, itemY)
            context.renderItemDecorations(font, offhand, itemX, itemY)
        }

        val mainShellY = verticalMainShellY()
        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = mainShellY,
            width = Layout.compactSlotWidth,
            height = verticalHotbarHeight(),
            style = compactMainShellStyle(),
        )

        for (slot in 0 until 9) {
            val slotY = mainShellY + (slot * (Layout.compactSlotHeight + Layout.slotGap))
            if (slot > 0) {
                val separatorY = slotY - ((Layout.slotGap + 1) / 2)
                context.fill(
                    4,
                    separatorY,
                    Layout.compactSlotWidth - 4,
                    separatorY + 1,
                    compactSeparatorColor(),
                )
            }
            if (slot == selectedSlot) {
                drawCompactSlotShell(
                    context = context,
                    x = Layout.compactSelectedInset,
                    y = slotY + Layout.compactSelectedInset,
                    width = Layout.compactSlotWidth - (Layout.compactSelectedInset * 2),
                    height = Layout.compactSlotHeight - (Layout.compactSelectedInset * 2),
                    selected = true,
                    accentSync = accentSync,
                )
            }

            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) {
                val itemX = ((Layout.compactSlotWidth - 16) / 2)
                val itemY = slotY + (((Layout.compactSlotHeight - 16) / 2) + Layout.compactItemOffsetY)
                context.renderItem(stack, itemX, itemY)
                context.renderItemDecorations(font, stack, itemX, itemY)
            }

            if (stack.count <= 1) {
                val slotLabel = vText((slot + 1).toString())
                val labelWidth = scaledTextWidth(font, slotLabel, Layout.compactSlotIndexScale)
                val labelHeight = scaledTextHeight(font, Layout.compactSlotIndexScale)
                drawScaledText(
                    context = context,
                    font = font,
                    text = slotLabel,
                    x = Layout.compactSlotWidth - labelWidth - Layout.compactSlotIndexInsetX,
                    y = slotY + Layout.compactSlotHeight - labelHeight - Layout.compactSlotIndexInsetY,
                    color = compactSlotIndexColor(slot == selectedSlot),
                    scale = Layout.compactSlotIndexScale,
                )
            }
        }
    }

    private fun drawVerticalStatusColumn(
        context: GuiGraphics,
        font: Font,
        client: Minecraft,
        x: Int,
        y: Int,
    ) {
        val player = client.player ?: return
        val maxHealth = player.maxHealth.coerceAtLeast(1f)
        val currentHealth = player.health.coerceAtLeast(0f)
        val absorptionAmount = player.absorptionAmount.coerceAtLeast(0f)
        val healthRatio = (currentHealth / maxHealth).coerceIn(0f, 1f)
        val absorptionRatio = (absorptionAmount / maxHealth).coerceAtLeast(0f)
        val armorRatio = (player.armorValue.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val foodRatio = (player.foodData.foodLevel.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val xpRatio = player.experienceProgress.coerceIn(0f, 1f)

        val hpText = vText(currentHealth.roundToInt().coerceAtLeast(0).toString())
        val levelText = vText(player.experienceLevel.coerceAtLeast(0).toString())
        val geometry = compactStatusGeometry(
            hpTextWidth = scaledTextWidth(font, hpText, Layout.compactStatusTextScale),
            levelTextWidth = scaledTextWidth(font, levelText, Layout.compactLevelTextScale),
        )
        val shellWidth = verticalStatusShellWidth(geometry)
        val shellHeight = verticalStatusShellHeight(geometry)
        val barX = x + ((shellWidth - Layout.verticalStatusBarWidth) / 2).coerceAtLeast(0)
        val contentTopY = y + ((shellHeight - verticalStatusContentHeight(geometry)) / 2).coerceAtLeast(0)
        val hpBarY = contentTopY
        val hpTextY = hpBarY + geometry.hpBarExtent + Layout.verticalStatusTextGap
        val armorBarY = hpTextY + Layout.verticalStatusTextAreaHeight + Layout.compactAfterHpTextGap
        val foodBarY = armorBarY + geometry.armorBarExtent + Layout.compactStatusBarGap
        val xpBarY = foodBarY + geometry.foodBarExtent + Layout.compactStatusBarGap
        val levelTextY = xpBarY + geometry.xpBarExtent + Layout.verticalStatusTextGap

        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = shellWidth,
            height = shellHeight,
            style = compactStatusShellStyle(),
        )

        drawVerticalHealthStatusBar(
            context = context,
            x = barX,
            y = hpBarY,
            height = geometry.hpBarExtent,
            healthRatio = healthRatio,
            absorptionRatio = absorptionRatio,
            currentHealth = currentHealth,
            absorptionAmount = absorptionAmount,
            maxHealth = maxHealth,
        )
        drawVerticalStatusBar(
            context = context,
            x = barX,
            y = armorBarY,
            height = geometry.armorBarExtent,
            segments = listOf(StatusSegment(armorRatio, armorStatusColor())),
        )
        drawVerticalStatusBar(
            context = context,
            x = barX,
            y = foodBarY,
            height = geometry.foodBarExtent,
            segments = listOf(StatusSegment(foodRatio, foodStatusColor())),
        )
        drawVerticalStatusBar(
            context = context,
            x = barX,
            y = xpBarY,
            height = geometry.xpBarExtent,
            segments = listOf(StatusSegment(xpRatio, xpStatusColor())),
        )

        drawScaledText(
            context = context,
            font = font,
            text = hpText,
            x = x + ((shellWidth - geometry.hpTextWidth) / 2).coerceAtLeast(0),
            y = hpTextY,
            color = healthStatusColor(),
            scale = Layout.compactStatusTextScale,
        )
        drawScaledText(
            context = context,
            font = font,
            text = levelText,
            x = x + ((shellWidth - geometry.levelTextWidth) / 2).coerceAtLeast(0),
            y = levelTextY,
            color = xpStatusColor(),
            scale = Layout.compactLevelTextScale,
        )
    }

    private fun drawPlayerStatus(
        context: GuiGraphics,
        @Suppress("UNUSED_PARAMETER") font: Font,
        client: Minecraft,
        hotbarX: Int,
    ) {
        val player = client.player ?: return
        val hotbarWidth = hotbarWidth(ItemBarHudModule.LayoutType.PANEL)
        val splitGap = Layout.statusSplitGap.coerceAtMost((hotbarWidth / 3).coerceAtLeast(10))
        val shortBarWidth = ((hotbarWidth - splitGap) / 2).coerceAtLeast(1)
        val rightBarX = hotbarX + hotbarWidth - shortBarWidth
        val maxHealth = player.maxHealth.coerceAtLeast(1f)
        val healthRatio = (player.health.coerceAtLeast(0f) / maxHealth).coerceIn(0f, 1f)
        val absorptionRatio = (player.absorptionAmount.coerceAtLeast(0f) / maxHealth).coerceIn(0f, 1f)
        val armorRatio = (player.armorValue.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val foodRatio = (player.foodData.foodLevel.coerceIn(0, 20) / 20f).coerceIn(0f, 1f)
        val xpRatio = player.experienceProgress.coerceIn(0f, 1f)

        drawStatusBar(
            context = context,
            x = hotbarX,
            y = Layout.statusArmorY,
            width = shortBarWidth,
            segments = listOf(StatusSegment(armorRatio, armorStatusColor())),
        )
        drawStatusBar(
            context = context,
            x = rightBarX,
            y = Layout.statusArmorY,
            width = shortBarWidth,
            segments = listOf(StatusSegment(foodRatio, foodStatusColor())),
        )
        drawHealthStatusBar(
            context = context,
            x = hotbarX,
            y = Layout.statusHealthY,
            width = hotbarWidth,
            healthRatio = healthRatio,
            absorptionRatio = absorptionRatio,
            currentHealth = player.health.coerceAtLeast(0f),
            absorptionAmount = player.absorptionAmount.coerceAtLeast(0f),
            maxHealth = maxHealth,
        )
        drawStatusBar(
            context = context,
            x = hotbarX,
            y = Layout.statusXpY,
            width = hotbarWidth,
            segments = listOf(StatusSegment(xpRatio, xpStatusColor())),
        )
    }

    private data class StatusSegment(
        val ratio: Float,
        val color: Int,
    )

    private fun drawStatusBar(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        segments: List<StatusSegment>,
    ) {
        val trackWidth = width.coerceAtLeast(1)
        drawStatusTrack(
            context = context,
            x = x,
            y = y,
            width = trackWidth,
        )

        val usableWidth = (trackWidth - 2).coerceAtLeast(0)
        if (usableWidth <= 0) return

        var cursor = x + 1
        var remaining = usableWidth
        val visibleSegments = segments.mapNotNull { segment ->
            val clampedRatio = segment.ratio.coerceAtLeast(0f)
            if (clampedRatio <= 0f || remaining <= 0) return@mapNotNull null
            val proposedWidth = (usableWidth * clampedRatio).roundToInt().coerceAtLeast(0)
            val actualWidth = proposedWidth.coerceAtMost(remaining)
            if (actualWidth <= 0) return@mapNotNull null
            remaining -= actualWidth
            val startX = cursor
            cursor += actualWidth
            Triple(startX, actualWidth, segment.color)
        }

        visibleSegments.forEachIndexed { index, (segmentX, segmentWidth, segmentColor) ->
            drawStatusSegment(
                context = context,
                x = segmentX,
                y = y + 1,
                width = segmentWidth,
                height = (Layout.statusBarHeight - 2).coerceAtLeast(1),
                color = segmentColor,
                roundLeft = index == 0,
                roundRight = index == visibleSegments.lastIndex,
            )
        }
    }

    private fun drawHealthStatusBar(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        healthRatio: Float,
        absorptionRatio: Float,
        currentHealth: Float,
        absorptionAmount: Float,
        maxHealth: Float,
    ) {
        val trackWidth = width.coerceAtLeast(1)
        drawStatusTrack(
            context = context,
            x = x,
            y = y,
            width = trackWidth,
        )

        val innerWidth = (trackWidth - 2).coerceAtLeast(0)
        val innerHeight = (Layout.statusBarHeight - 2).coerceAtLeast(1)
        if (innerWidth <= 0) return

        if (absorptionAmount <= 0f || absorptionRatio <= 0f) {
            val healthWidth = (innerWidth * healthRatio.coerceIn(0f, 1f)).roundToInt().coerceIn(0, innerWidth)
            if (healthWidth > 0) {
                drawStatusSegment(
                    context = context,
                    x = x + 1,
                    y = y + 1,
                    width = healthWidth,
                    height = innerHeight,
                    color = healthStatusColor(),
                    roundLeft = true,
                    roundRight = true,
                )
            }
            return
        }

        val effectivePool = (maxHealth + absorptionAmount).coerceAtLeast(1f)
        val normalizedHealth = (currentHealth / effectivePool).coerceIn(0f, 1f)
        val normalizedAbsorption = (absorptionAmount / effectivePool).coerceIn(0f, 1f)
        val healthWidth = (innerWidth * normalizedHealth).roundToInt().coerceIn(0, innerWidth)
        val absorptionWidth = (innerWidth * normalizedAbsorption).roundToInt().coerceIn(0, innerWidth - healthWidth)

        if (healthWidth > 0) {
            drawStatusSegment(
                context = context,
                x = x + 1,
                y = y + 1,
                width = healthWidth,
                height = innerHeight,
                color = healthStatusColor(),
                roundLeft = true,
                roundRight = absorptionWidth <= 0,
            )
        }
        if (absorptionWidth > 0) {
            drawStatusSegment(
                context = context,
                x = x + 1 + innerWidth - absorptionWidth,
                y = y + 1,
                width = absorptionWidth,
                height = innerHeight,
                color = absorptionStatusColor(),
                roundLeft = healthWidth <= 0,
                roundRight = true,
            )
        }
    }

    private fun drawStatusTrack(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = width,
            height = Layout.statusBarHeight,
            style = statusTrackStyle(),
        )
    }

    private fun drawVerticalStatusBar(
        context: GuiGraphics,
        x: Int,
        y: Int,
        height: Int,
        segments: List<StatusSegment>,
    ) {
        val trackHeight = height.coerceAtLeast(1)
        val usableHeight = trackHeight.coerceAtLeast(0)
        if (usableHeight <= 0) return

        var cursorBottom = y + usableHeight
        var remaining = usableHeight
        val visibleSegments = segments.mapNotNull { segment ->
            val clampedRatio = segment.ratio.coerceAtLeast(0f)
            if (clampedRatio <= 0f || remaining <= 0) return@mapNotNull null
            val proposedHeight = (usableHeight * clampedRatio).roundToInt().coerceAtLeast(0)
            val actualHeight = proposedHeight.coerceAtMost(remaining)
            if (actualHeight <= 0) return@mapNotNull null
            remaining -= actualHeight
            val segmentY = cursorBottom - actualHeight
            cursorBottom -= actualHeight
            Triple(segmentY, actualHeight, segment.color)
        }

        visibleSegments.forEachIndexed { index, (segmentY, segmentHeight, segmentColor) ->
            drawVerticalStatusSegment(
                context = context,
                x = x,
                y = segmentY,
                width = Layout.verticalStatusBarWidth.coerceAtLeast(1),
                height = segmentHeight,
                color = segmentColor,
                roundTop = index == visibleSegments.lastIndex,
                roundBottom = index == 0,
            )
        }
    }

    private fun drawVerticalHealthStatusBar(
        context: GuiGraphics,
        x: Int,
        y: Int,
        height: Int,
        healthRatio: Float,
        absorptionRatio: Float,
        currentHealth: Float,
        absorptionAmount: Float,
        maxHealth: Float,
    ) {
        val trackHeight = height.coerceAtLeast(1)
        val innerHeight = trackHeight.coerceAtLeast(0)
        val innerWidth = Layout.verticalStatusBarWidth.coerceAtLeast(1)
        if (innerHeight <= 0) return

        if (absorptionAmount <= 0f || absorptionRatio <= 0f) {
            val healthHeight = (innerHeight * healthRatio.coerceIn(0f, 1f)).roundToInt().coerceIn(0, innerHeight)
            if (healthHeight > 0) {
                drawVerticalStatusSegment(
                    context = context,
                    x = x,
                    y = y + innerHeight - healthHeight,
                    width = innerWidth,
                    height = healthHeight,
                    color = healthStatusColor(),
                    roundTop = true,
                    roundBottom = true,
                )
            }
            return
        }

        val effectivePool = (maxHealth + absorptionAmount).coerceAtLeast(1f)
        val normalizedHealth = (currentHealth / effectivePool).coerceIn(0f, 1f)
        val normalizedAbsorption = (absorptionAmount / effectivePool).coerceIn(0f, 1f)
        val healthHeight = (innerHeight * normalizedHealth).roundToInt().coerceIn(0, innerHeight)
        val absorptionHeight = (innerHeight * normalizedAbsorption).roundToInt().coerceIn(0, innerHeight - healthHeight)

        if (healthHeight > 0) {
            drawVerticalStatusSegment(
                context = context,
                x = x,
                y = y + innerHeight - healthHeight,
                width = innerWidth,
                height = healthHeight,
                color = healthStatusColor(),
                roundTop = absorptionHeight <= 0,
                roundBottom = true,
            )
        }
        if (absorptionHeight > 0) {
            drawVerticalStatusSegment(
                context = context,
                x = x,
                y = y,
                width = innerWidth,
                height = absorptionHeight,
                color = absorptionStatusColor(),
                roundTop = true,
                roundBottom = healthHeight <= 0,
            )
        }
    }

    private fun drawVerticalStatusTrack(
        context: GuiGraphics,
        x: Int,
        y: Int,
        height: Int,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = Layout.verticalStatusBarWidth,
            height = height,
            style = statusTrackStyle(),
        )
    }

    private fun drawStatusSegment(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        roundLeft: Boolean,
        roundRight: Boolean,
    ) {
        val clampedWidth = width.coerceAtLeast(1)
        val clampedHeight = height.coerceAtLeast(1)
        val radiusPad = ((clampedHeight / 2f).roundToInt() + 1).coerceAtLeast(2)
        val style = statusFillStyle(color, clampedHeight)

        when {
            roundLeft && roundRight -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x,
                    y = y,
                    width = clampedWidth,
                    height = clampedHeight,
                    style = style,
                )
            }
            roundLeft -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x,
                    y = y,
                    width = clampedWidth + radiusPad,
                    height = clampedHeight,
                    style = style,
                    clipRect = SdfPanelRenderer.ClipRect(x, y, clampedWidth, clampedHeight),
                )
            }
            roundRight -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x - radiusPad,
                    y = y,
                    width = clampedWidth + radiusPad,
                    height = clampedHeight,
                    style = style,
                    clipRect = SdfPanelRenderer.ClipRect(x, y, clampedWidth, clampedHeight),
                )
            }
            else -> {
                context.fill(x, y, x + clampedWidth, y + clampedHeight, color)
            }
        }
    }

    private fun drawVerticalStatusSegment(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        roundTop: Boolean,
        roundBottom: Boolean,
    ) {
        val clampedWidth = width.coerceAtLeast(1)
        val clampedHeight = height.coerceAtLeast(1)
        val radiusPad = ((clampedWidth / 2f).roundToInt() + 1).coerceAtLeast(2)
        val style = verticalStatusFillStyle(color)

        when {
            roundTop && roundBottom -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x,
                    y = y,
                    width = clampedWidth,
                    height = clampedHeight,
                    style = style,
                )
            }
            roundTop -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x,
                    y = y,
                    width = clampedWidth,
                    height = clampedHeight + radiusPad,
                    style = style,
                    clipRect = SdfPanelRenderer.ClipRect(x, y, clampedWidth, clampedHeight),
                )
            }
            roundBottom -> {
                SdfPanelRenderer.draw(
                    context = context,
                    x = x,
                    y = y - radiusPad,
                    width = clampedWidth,
                    height = clampedHeight + radiusPad,
                    style = style,
                    clipRect = SdfPanelRenderer.ClipRect(x, y, clampedWidth, clampedHeight),
                )
            }
            else -> {
                context.fill(x, y, x + clampedWidth, y + clampedHeight, color)
            }
        }
    }

    private fun slotStyle(selected: Boolean, filled: Boolean, accentSync: Boolean): SdfPanelStyle {
        val accent = if (accentSync) VisualThemeSettings.accentStrong() else 0xFF30E86C.toInt()
        val neon = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF30E86C.toInt()
        val glow = if (accentSync) VisualThemeSettings.themedAccentGlowBase(accent) else VisualThemeSettings.themedFallbackGlow(0xFF30E86C.toInt())

        val baseFill = if (VisualThemeSettings.isTransparentPreset()) {
            if (filled) VisualThemeSettings.hudIconFill() else VisualThemeSettings.hudShellFill()
        } else if (VisualThemeSettings.isLightPreset()) {
            if (filled) VisualThemeSettings.hudIconFill() else VisualThemeSettings.hudShellFill()
        } else {
            if (filled) VisualThemeSettings.hudIconFill() else VisualThemeSettings.hudShellFill()
        }
        val border = when {
            selected && VisualThemeSettings.isTransparentPreset() -> blendColor(VisualThemeSettings.hudIconBorder(), accent, 0.34f)
            filled && VisualThemeSettings.isTransparentPreset() -> VisualThemeSettings.hudIconBorder()
            VisualThemeSettings.isTransparentPreset() -> VisualThemeSettings.hudShellBorder()
            selected && VisualThemeSettings.isLightPreset() -> blendColor(0xFFBFD0E5.toInt(), accent, 0.36f)
            selected -> blendColor(0xFF344055.toInt(), accent, 0.48f)
            filled && VisualThemeSettings.isLightPreset() -> 0xFFC6D6E8.toInt()
            filled -> 0x8A33415A.toInt()
            VisualThemeSettings.isLightPreset() -> 0xFFC5D4E6.toInt()
            else -> 0x6A2B384F
        }

        return SdfPanelStyle(
            baseColor = baseFill,
            borderColor = border,
            borderWidthPx = if (selected) 1.25f else 1.0f,
            radiusPx = Layout.slotRadius,
            innerGlow = if (VisualThemeSettings.isTransparentPreset()) {
                SdfGlowStyle(
                    color = if (selected) accent else 0xFFFFFFFF.toInt(),
                    radiusPx = 8f,
                    strength = if (selected) 0.08f else 0.02f,
                    opacity = if (selected) 0.06f else 0.02f,
                )
            } else {
                SdfGlowStyle.NONE
            },
            outerGlow = if (VisualThemeSettings.isLightPreset()) {
                SdfGlowStyle.NONE
            } else {
                SdfGlowStyle(
                    color = if (selected) glow else 0xFF000000.toInt(),
                    radiusPx = if (selected) 14f else 10f,
                    strength = if (selected) 0.20f else 0.08f,
                    opacity = if (selected) 0.12f else 0.05f,
                )
            },
            shade = if (VisualThemeSettings.isTransparentPreset()) {
                SdfShadeStyle(0x04FFFFFF, 0x10000000)
            } else {
                SdfShadeStyle(0x00000000, 0x00000000)
            },
            neonBorder = SdfNeonBorderStyle(
                color = VisualThemeSettings.withAlpha(neon, if (selected) {
                    if (VisualThemeSettings.isLightPreset()) 0x9A else 0xD6
                } else {
                    if (VisualThemeSettings.isLightPreset()) 0x18 else 0x28
                }),
                widthPx = if (selected) 1.0f else 0.72f,
                softnessPx = if (selected) 5f else 3.5f,
                strength = if (selected) {
                    if (VisualThemeSettings.isLightPreset()) 0.40f else 0.72f
                } else {
                    if (VisualThemeSettings.isLightPreset()) 0.08f else 0.14f
                },
            ),
        )
    }

    private fun drawCompactSlotShell(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int = Layout.compactSlotHeight,
        selected: Boolean,
        accentSync: Boolean,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = width,
            height = height,
            style = if (selected) compactSelectedSlotStyle(accentSync) else compactMainShellStyle(),
        )
    }

    private fun compactMainShellStyle(): SdfPanelStyle = SdfPanelStyle(
        baseColor = if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudShellFill() else if (VisualThemeSettings.isLightPreset()) 0xFFF4F7FB.toInt() else 0xFF0F141D.toInt(),
        borderColor = if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudShellBorder() else if (VisualThemeSettings.isLightPreset()) 0xFFB7C5D6.toInt() else 0xFF242D3C.toInt(),
        borderWidthPx = 1.0f,
        radiusPx = Layout.compactHotbarRadius,
        innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        shade = if (VisualThemeSettings.isTransparentPreset()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else SdfShadeStyle(0x00000000, 0x00000000),
        neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
    )

    private fun compactSelectedSlotStyle(accentSync: Boolean): SdfPanelStyle {
        val accent = if (accentSync) VisualThemeSettings.accentStrong() else 0xFF30E86C.toInt()
        return SdfPanelStyle(
            baseColor = if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudInnerFill() else if (VisualThemeSettings.isLightPreset()) 0xFFF9FBFF.toInt() else 0xFF171D2A.toInt(),
            borderColor = blendColor(
                if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudInnerBorder() else if (VisualThemeSettings.isLightPreset()) 0xFFB8C8DA.toInt() else 0xFF324155.toInt(),
                accent,
                if (VisualThemeSettings.isTransparentPreset()) 0.24f else 0.34f,
            ),
            borderWidthPx = 1.1f,
            radiusPx = (Layout.compactHotbarRadius - 1f).coerceAtLeast(5f),
            innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            shade = if (VisualThemeSettings.isTransparentPreset()) SdfShadeStyle(0x04FFFFFF, 0x0E000000) else SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
        )
    }

    private fun compactSeparatorColor(): Int =
        if (VisualThemeSettings.isTransparentPreset()) 0x40677282 else if (VisualThemeSettings.isLightPreset()) 0x66B7C7D8 else 0x66323E52

    private fun compactSlotIndexColor(selected: Boolean): Int =
        when {
            selected && VisualThemeSettings.isTransparentPreset() -> VisualThemeSettings.textSecondary()
            VisualThemeSettings.isTransparentPreset() -> VisualThemeSettings.withAlpha(VisualThemeSettings.textMuted(), 0xB0)
            selected && VisualThemeSettings.isLightPreset() -> 0xFF7A879B.toInt()
            selected -> 0xFF8897B0.toInt()
            VisualThemeSettings.isLightPreset() -> 0x886E7A90.toInt()
            else -> 0x88909DB3.toInt()
        }

    private fun compactStatusShellStyle(): SdfPanelStyle = SdfPanelStyle(
        baseColor = if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudShellFill() else if (VisualThemeSettings.isLightPreset()) 0xFFF4F7FB.toInt() else 0xFF0F141D.toInt(),
        borderColor = if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudShellBorder() else if (VisualThemeSettings.isLightPreset()) 0xFFB7C5D6.toInt() else 0xFF242D3C.toInt(),
        borderWidthPx = 1.0f,
        radiusPx = (Layout.compactStatusRowHeight / 2f),
        innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        shade = if (VisualThemeSettings.isTransparentPreset()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else SdfShadeStyle(0x00000000, 0x00000000),
        neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
    )

    private fun statusTrackBorderColor(): Int =
        if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudTrackBorder() else if (VisualThemeSettings.isLightPreset()) 0xFFB6C2D0.toInt() else 0xFF090D12.toInt()

    private fun statusTrackBaseColor(): Int =
        if (VisualThemeSettings.isTransparentPreset()) VisualThemeSettings.hudTrackFill() else if (VisualThemeSettings.isLightPreset()) 0xFFF4F7FB.toInt() else 0xFF1A212B.toInt()

    private fun statusTrackStyle(): SdfPanelStyle = SdfPanelStyle(
        baseColor = statusTrackBaseColor(),
        borderColor = statusTrackBorderColor(),
        borderWidthPx = 0.9f,
        radiusPx = Layout.statusBarHeight / 2f,
        innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
        shade = SdfShadeStyle(0x00000000, 0x00000000),
        neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
    )

    private fun statusFillStyle(fillColor: Int, height: Int): SdfPanelStyle {
        val borderColor = blendColor(fillColor, 0xFFFFFFFF.toInt(), if (VisualThemeSettings.isLightPreset()) 0.10f else 0.06f)
        return SdfPanelStyle(
            baseColor = fillColor,
            borderColor = borderColor,
            borderWidthPx = 0.55f,
            radiusPx = height / 2f,
            innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
        )
    }

    private fun verticalStatusFillStyle(fillColor: Int): SdfPanelStyle {
        val borderColor = blendColor(fillColor, 0xFFFFFFFF.toInt(), if (VisualThemeSettings.isLightPreset()) 0.10f else 0.06f)
        return SdfPanelStyle(
            baseColor = fillColor,
            borderColor = borderColor,
            borderWidthPx = 0.55f,
            radiusPx = Layout.verticalStatusBarWidth / 2f,
            innerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle(0x00000000, 0f, 0f, 0f),
        )
    }

    private fun armorStatusColor(): Int =
        if (VisualThemeSettings.isLightPreset()) 0xFF8CA2C8.toInt() else 0xFF6E8EC9.toInt()

    private fun healthStatusColor(): Int =
        if (VisualThemeSettings.isLightPreset()) 0xFFE66572.toInt() else 0xFFD94A59.toInt()

    private fun absorptionStatusColor(): Int =
        if (VisualThemeSettings.isLightPreset()) 0xFFE2B957.toInt() else 0xFFD0A744.toInt()

    private fun foodStatusColor(): Int =
        if (VisualThemeSettings.isLightPreset()) 0xFFE19448.toInt() else 0xFFCE7B31.toInt()

    private fun xpStatusColor(): Int =
        if (VisualThemeSettings.isLightPreset()) 0xFF79C94B.toInt() else 0xFF64B53E.toInt()

    private fun ensureDragState(client: Minecraft, hudWidth: Int, hudHeight: Int): ItemBarHudDragState {
        val current = dragState
        if (current != null) return current

        val defaultPosition = ItemBarHudPosition(
            x = ((client.window.guiScaledWidth - hudWidth) / 2).coerceAtLeast(0),
            y = (client.window.guiScaledHeight - hudHeight - Layout.defaultBottomInset).coerceAtLeast(0),
        )
        return ItemBarHudDragState(ItemBarHudPositionStore.load(defaultPosition)).also {
            dragState = it
        }
    }

    private fun clampToScreen(client: Minecraft, state: ItemBarHudDragState, hudWidth: Int, hudHeight: Int) {
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
        state: ItemBarHudDragState,
        baseWidth: Int,
        baseHeight: Int,
        scale: Float,
    ) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (abs(lastScale - scale) < 0.001f || state.dragging) {
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
        ItemBarHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun shellWidth(showPlayerStatus: Boolean, layoutType: ItemBarHudModule.LayoutType): Int {
        val slotWidth = slotWidth(layoutType)
        val baseWidth = (slotWidth * 10) + (Layout.slotGap * 8) + Layout.offhandGap
        return when (layoutType) {
            ItemBarHudModule.LayoutType.PANEL -> baseWidth
            ItemBarHudModule.LayoutType.COMPACT -> baseWidth + Layout.compactShellOverflow
            ItemBarHudModule.LayoutType.VERTICAL -> slotWidth + if (showPlayerStatus) (Layout.offhandGap + verticalStatusShellWidth()) else 0
        }
    }

    private fun hotbarWidth(layoutType: ItemBarHudModule.LayoutType): Int {
        return (slotWidth(layoutType) * 9) + (Layout.slotGap * 8)
    }

    private fun hotbarStartX(layoutType: ItemBarHudModule.LayoutType): Int {
        return slotWidth(layoutType) + Layout.offhandGap
    }

    private fun slotWidth(layoutType: ItemBarHudModule.LayoutType): Int {
        return when (layoutType) {
            ItemBarHudModule.LayoutType.PANEL -> Layout.slotSize
            ItemBarHudModule.LayoutType.COMPACT -> Layout.compactSlotWidth
            ItemBarHudModule.LayoutType.VERTICAL -> Layout.compactSlotWidth
        }
    }

    private fun shellHeight(showPlayerStatus: Boolean, layoutType: ItemBarHudModule.LayoutType): Int {
        val slotHeight = when (layoutType) {
            ItemBarHudModule.LayoutType.PANEL -> Layout.slotSize
            ItemBarHudModule.LayoutType.COMPACT -> Layout.compactSlotHeight
            ItemBarHudModule.LayoutType.VERTICAL -> Layout.compactSlotHeight
        }
        return when (layoutType) {
            ItemBarHudModule.LayoutType.VERTICAL -> slotHeight + Layout.offhandGap + verticalHotbarHeight()
            else -> slotTop(showPlayerStatus, layoutType) + slotHeight
        }
    }

    private fun slotTop(showPlayerStatus: Boolean, layoutType: ItemBarHudModule.LayoutType): Int {
        if (!showPlayerStatus) return 0
        return when (layoutType) {
            ItemBarHudModule.LayoutType.PANEL -> Layout.statusHeight + Layout.statusGap
            ItemBarHudModule.LayoutType.COMPACT -> Layout.compactStatusRowHeight + Layout.compactStatusGap
            ItemBarHudModule.LayoutType.VERTICAL -> 0
        }
    }

    private fun verticalMainShellY(): Int {
        return Layout.compactSlotHeight + Layout.offhandGap
    }

    private fun verticalHotbarHeight(): Int {
        return (Layout.compactSlotHeight * 9) + (Layout.slotGap * 8)
    }

    private fun verticalStatusShellHeight(): Int {
        return verticalStatusShellHeight(estimatedCompactStatusGeometry())
    }

    private fun verticalStatusShellHeight(geometry: CompactStatusGeometry): Int {
        return verticalHotbarHeight()
    }

    private fun verticalStatusContentHeight(geometry: CompactStatusGeometry): Int {
        return geometry.hpBarExtent +
            Layout.verticalStatusTextGap +
            Layout.verticalStatusTextAreaHeight +
            Layout.compactAfterHpTextGap +
            geometry.armorBarExtent +
            Layout.compactStatusBarGap +
            geometry.foodBarExtent +
            Layout.compactStatusBarGap +
            geometry.xpBarExtent +
            Layout.verticalStatusTextGap +
            Layout.verticalStatusTextAreaHeight
    }

    private fun verticalStatusShellWidth(): Int {
        return verticalStatusShellWidth(estimatedCompactStatusGeometry())
    }

    private fun verticalStatusShellWidth(geometry: CompactStatusGeometry): Int {
        return maxOf(
            Layout.verticalStatusBarWidth,
            geometry.hpTextWidth,
            geometry.levelTextWidth,
        ) +
            (Layout.verticalStatusShellPaddingX * 2)
    }

    private fun verticalStatusY(): Int {
        return verticalMainShellY() + ((verticalHotbarHeight() - verticalStatusShellHeight()) / 2).coerceAtLeast(0)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

    private fun scaledTextWidth(font: Font, text: net.minecraft.network.chat.Component, scale: Float): Int {
        return (font.width(text) * scale).roundToInt().coerceAtLeast(1)
    }

    private fun scaledTextHeight(font: Font, scale: Float): Int {
        return (font.lineHeight * scale).roundToInt().coerceAtLeast(1)
    }

    private fun compactStatusGeometry(hpTextWidth: Int, levelTextWidth: Int): CompactStatusGeometry {
        val barsShellWidth = hotbarWidth(ItemBarHudModule.LayoutType.COMPACT) + (Layout.compactShellOverflow * 2)
        val availableWidth = (barsShellWidth - (Layout.compactStatusShellPaddingX * 2)).coerceAtLeast(60)
        val fixedSpacing = hpTextWidth +
            levelTextWidth +
            (Layout.compactInlineValueGap * 2) +
            Layout.compactAfterHpTextGap +
            (Layout.compactStatusBarGap * 2)
        val contentWidth = (availableWidth - fixedSpacing).coerceAtLeast(46 + 18 + 18 + 18)
        val hpBarExtent = (contentWidth * 0.44f).roundToInt().coerceAtLeast(46)
        val miniBarsWidth = (contentWidth - hpBarExtent).coerceAtLeast(18 * 3)
        val armorBarExtent = (miniBarsWidth * 0.22f).roundToInt().coerceAtLeast(18)
        val foodBarExtent = (miniBarsWidth * 0.24f).roundToInt().coerceAtLeast(18)
        val xpBarExtent = (miniBarsWidth - armorBarExtent - foodBarExtent).coerceAtLeast(24)
        return CompactStatusGeometry(
            hpTextWidth = hpTextWidth,
            levelTextWidth = levelTextWidth,
            hpBarExtent = hpBarExtent,
            armorBarExtent = armorBarExtent,
            foodBarExtent = foodBarExtent,
            xpBarExtent = xpBarExtent,
        )
    }

    private fun estimatedCompactStatusGeometry(): CompactStatusGeometry {
        return compactStatusGeometry(
            hpTextWidth = 10,
            levelTextWidth = 16,
        )
    }

    private fun drawScaledText(
        context: GuiGraphics,
        font: Font,
        text: net.minecraft.network.chat.Component,
        x: Int,
        y: Int,
        color: Int,
        scale: Float,
    ) {
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(scale, scale)
        context.drawString(font, text, 0, 0, color, false)
        context.pose().popMatrix()
    }
}
