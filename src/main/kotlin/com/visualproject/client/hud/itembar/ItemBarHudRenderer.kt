package com.visualproject.client.hud.itembar

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.mixin.ExperienceBarSpriteAccessor
import com.visualproject.client.mixin.GuiSpriteAccessor
import com.visualproject.client.mixin.PlayerTabOverlaySpriteAccessor
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
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.ceil
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
        const val statusIconSize = 9
        const val statusIconStep = 8
        const val statusArmorY = 0
        const val statusVitalsY = 10
        const val statusXpY = 20
        const val statusHeight = 25
        const val statusGap = 4
        const val xpBarHeight = 5
        const val defaultBottomInset = 8
    }

    private var dragState: ItemBarHudDragState? = null
    private var lastBounds: ItemBarHudBounds? = null
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val scale = hudScale()
        val showPlayerStatus = ModuleStateStore.isSettingEnabled(ItemBarHudModule.showPlayerStatusKey)
        val baseWidth = shellWidth()
        val baseHeight = shellHeight(showPlayerStatus)
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

        val inventory = player.inventory
        val selectedSlot = inventory.selectedSlot
        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val slotY = slotTop(showPlayerStatus)
        val hotbarStartX = Layout.slotSize + Layout.offhandGap

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        if (showPlayerStatus) {
            drawPlayerStatus(
                context = context,
                font = client.font,
                client = client,
                hotbarX = hotbarStartX,
            )
        }

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
        val state = ensureDragState(client, scaled(shellWidth(), scale), scaled(shellHeight(showPlayerStatus), scale))
        val bounds = lastBounds ?: ItemBarHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(shellWidth(), scale),
            height = scaled(shellHeight(showPlayerStatus), scale),
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
        val showPlayerStatus = ModuleStateStore.isSettingEnabled(ItemBarHudModule.showPlayerStatusKey)
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(shellWidth(), scale),
            hudHeight = scaled(shellHeight(showPlayerStatus), scale),
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

    private fun drawPlayerStatus(
        context: GuiGraphics,
        font: Font,
        client: Minecraft,
        hotbarX: Int,
    ) {
        val player = client.player ?: return
        val hotbarWidth = hotbarWidth()
        val armorValue = player.armorValue.coerceIn(0, 20)
        val heartUnits = ceil(player.health.coerceAtLeast(0f)).toInt().coerceIn(0, 20)
        val maxHeartIcons = ceil(player.maxHealth / 2f).toInt().coerceIn(1, 10)
        val foodLevel = player.foodData.foodLevel.coerceIn(0, 20)
        val hunger = player.hasEffect(MobEffects.HUNGER)

        drawPipRow(
            context = context,
            x = hotbarX,
            y = Layout.statusArmorY,
            iconCount = 10,
            fullUnits = armorValue,
            emptySprite = GuiSpriteAccessor.getArmorEmptySprite(),
            halfSprite = GuiSpriteAccessor.getArmorHalfSprite(),
            fullSprite = GuiSpriteAccessor.getArmorFullSprite(),
        )
        drawPipRow(
            context = context,
            x = hotbarX,
            y = Layout.statusVitalsY,
            iconCount = maxHeartIcons,
            fullUnits = heartUnits,
            emptySprite = PlayerTabOverlaySpriteAccessor.getHeartContainerSprite(),
            halfSprite = PlayerTabOverlaySpriteAccessor.getHeartHalfSprite(),
            fullSprite = PlayerTabOverlaySpriteAccessor.getHeartFullSprite(),
        )
        drawPipRow(
            context = context,
            x = hotbarX + hotbarWidth - rowWidth(10),
            y = Layout.statusVitalsY,
            iconCount = 10,
            fullUnits = foodLevel,
            emptySprite = if (hunger) GuiSpriteAccessor.getFoodEmptyHungerSprite() else GuiSpriteAccessor.getFoodEmptySprite(),
            halfSprite = if (hunger) GuiSpriteAccessor.getFoodHalfHungerSprite() else GuiSpriteAccessor.getFoodHalfSprite(),
            fullSprite = if (hunger) GuiSpriteAccessor.getFoodFullHungerSprite() else GuiSpriteAccessor.getFoodFullSprite(),
        )

        val xpBarWidth = hotbarWidth.coerceAtLeast(1)
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            ExperienceBarSpriteAccessor.getExperienceBarBackgroundSprite(),
            hotbarX,
            Layout.statusXpY,
            xpBarWidth,
            Layout.xpBarHeight,
        )
        val xpFilled = (xpBarWidth * player.experienceProgress.coerceIn(0f, 1f)).roundToInt().coerceIn(0, xpBarWidth)
        if (xpFilled > 0) {
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                ExperienceBarSpriteAccessor.getExperienceBarProgressSprite(),
                hotbarX,
                Layout.statusXpY,
                xpFilled,
                Layout.xpBarHeight,
            )
        }

        if (player.experienceLevel > 0) {
            val levelText = vText(player.experienceLevel.toString())
            val levelWidth = font.width(levelText)
            context.drawString(
                font,
                levelText,
                hotbarX + ((hotbarWidth - levelWidth) / 2),
                Layout.statusVitalsY + 1,
                0xFF7FEA46.toInt(),
                false,
            )
        }
    }

    private fun drawPipRow(
        context: GuiGraphics,
        x: Int,
        y: Int,
        iconCount: Int,
        fullUnits: Int,
        emptySprite: net.minecraft.resources.Identifier,
        halfSprite: net.minecraft.resources.Identifier,
        fullSprite: net.minecraft.resources.Identifier,
    ) {
        for (index in 0 until iconCount) {
            val remaining = fullUnits - (index * 2)
            val sprite = when {
                remaining >= 2 -> fullSprite
                remaining == 1 -> halfSprite
                else -> emptySprite
            }
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                sprite,
                x + (index * Layout.statusIconStep),
                y,
                Layout.statusIconSize,
                Layout.statusIconSize,
            )
        }
    }

    private fun slotStyle(selected: Boolean, filled: Boolean, accentSync: Boolean): SdfPanelStyle {
        val accent = if (accentSync) VisualThemeSettings.accentStrong() else 0xFF30E86C.toInt()
        val neon = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF30E86C.toInt()
        val glow = if (accentSync) VisualThemeSettings.themedAccentGlowBase(accent) else VisualThemeSettings.themedFallbackGlow(0xFF30E86C.toInt())

        val baseFill = if (VisualThemeSettings.isLightPreset()) {
            if (filled) 0xEEF2F7FD.toInt() else 0xE5EDF4FC.toInt()
        } else {
            if (filled) 0xF20D121B.toInt() else 0xED0A1018.toInt()
        }
        val border = when {
            selected && VisualThemeSettings.isLightPreset() -> blendColor(0xFFBFD0E5.toInt(), accent, 0.36f)
            selected -> blendColor(0xFF344055.toInt(), accent, 0.48f)
            filled && VisualThemeSettings.isLightPreset() -> 0xAFC6D6E8.toInt()
            filled -> 0x8A33415A.toInt()
            VisualThemeSettings.isLightPreset() -> 0x88C5D4E6.toInt()
            else -> 0x6A2B384F
        }

        return SdfPanelStyle(
            baseColor = baseFill,
            borderColor = border,
            borderWidthPx = if (selected) 1.25f else 1.0f,
            radiusPx = Layout.slotRadius,
            innerGlow = SdfGlowStyle(
                color = if (selected) accent else 0xFFFFFFFF.toInt(),
                radiusPx = 8f,
                strength = if (selected) 0.08f else 0.02f,
                opacity = if (selected) 0.06f else 0.02f,
            ),
            outerGlow = SdfGlowStyle(
                color = if (selected) glow else if (VisualThemeSettings.isLightPreset()) 0xFFDDE8F4.toInt() else 0xFF000000.toInt(),
                radiusPx = if (selected) 14f else 10f,
                strength = if (selected) {
                    if (VisualThemeSettings.isLightPreset()) 0.14f else 0.20f
                } else {
                    if (VisualThemeSettings.isLightPreset()) 0.05f else 0.08f
                },
                opacity = if (selected) {
                    if (VisualThemeSettings.isLightPreset()) 0.10f else 0.12f
                } else {
                    if (VisualThemeSettings.isLightPreset()) 0.04f else 0.05f
                },
            ),
            shade = SdfShadeStyle(
                if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x0EFFFFFF,
                if (VisualThemeSettings.isLightPreset()) 0x0BCFDBEA else 0x14000000,
            ),
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

    private fun shellWidth(): Int {
        return (Layout.slotSize * 10) + (Layout.slotGap * 8) + Layout.offhandGap
    }

    private fun hotbarWidth(): Int {
        return (Layout.slotSize * 9) + (Layout.slotGap * 8)
    }

    private fun shellHeight(showPlayerStatus: Boolean): Int {
        return slotTop(showPlayerStatus) + Layout.slotSize
    }

    private fun slotTop(showPlayerStatus: Boolean): Int {
        return if (showPlayerStatus) Layout.statusHeight + Layout.statusGap else 0
    }

    private fun rowWidth(iconCount: Int): Int {
        return ((iconCount - 1) * Layout.statusIconStep) + Layout.statusIconSize
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }
}
