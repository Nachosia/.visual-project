package com.visualproject.client.hud.btc

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.hud.HudOcclusionRegistry
import com.visualproject.client.hud.shared.HudRuntimeStats
import com.visualproject.client.render.sdf.BackdropBlurRenderer
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.texture.TextureFiltering
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
import kotlin.math.roundToInt

internal class BtcHudRenderer {

    private data class BlockVisual(
        val id: BtcBlockId,
        val label: String,
        val texture: Identifier,
        val textureWidth: Int,
        val textureHeight: Int,
        val width: Int,
        val height: Int,
    )

    private object Layout {
        const val height = 28
        const val paddingX = 10
        const val iconSize = 11
        const val iconTextGap = 6
        const val blockGap = 8
        const val radius = 11f
        const val defaultBottomInset = 58
    }

    companion object {
        private val compassIcon = Identifier.fromNamespaceAndPath("visualclient", "textures/gui/icons/btc_compass.png")
        private val layersIcon = Identifier.fromNamespaceAndPath("visualclient", "textures/gui/icons/btc_layers.png")
        private val mapPinIcon = Identifier.fromNamespaceAndPath("visualclient", "textures/gui/icons/btc_map_pin.png")
    }

    private var positions: MutableMap<BtcBlockId, BtcHudPosition>? = null
    private val lastBounds = mutableMapOf<BtcBlockId, BtcHudBounds>()
    private var activeDragBlock: BtcBlockId? = null
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val font = client.font
        val visuals = buildVisuals(client, font)
        if (visuals.isEmpty()) {
            lastBounds.clear()
            return
        }

        val scale = hudScale()
        val blockPositions = ensurePositions(client, visuals, scale)
        BackdropBlurRenderer.captureBackdrop()
        lastBounds.clear()

        visuals.forEach { visual ->
            val position = blockPositions.getValue(visual.id)
            val actualWidth = scaled(visual.width, scale)
            val actualHeight = scaled(visual.height, scale)
            val clampedPosition = clampPosition(client, position, actualWidth, actualHeight)
            if (clampedPosition != position) {
                blockPositions[visual.id] = clampedPosition
            }
            lastBounds[visual.id] = BtcHudBounds(clampedPosition.x, clampedPosition.y, actualWidth, actualHeight)
            HudOcclusionRegistry.mark(clampedPosition.x, clampedPosition.y, actualWidth, actualHeight)

            context.pose().pushMatrix()
            context.pose().translate(clampedPosition.x.toFloat(), clampedPosition.y.toFloat())
            context.pose().scale(scale, scale)
            drawBlock(context, font, visual)
            context.pose().popMatrix()
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

        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()
        val hovered = lastBounds.entries.firstOrNull { it.value.contains(mouseX, mouseY) } ?: return consumed
        activeDragBlock = hovered.key
        dragOffsetX = mouseX - hovered.value.x
        dragOffsetY = mouseY - hovered.value.y
        return true
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
        val dragBlock = activeDragBlock ?: return consumed
        val positions = positions ?: return consumed
        val bounds = lastBounds[dragBlock] ?: return consumed

        positions[dragBlock] = clampPosition(
            client = client,
            position = BtcHudPosition(
                x = mouseEvent.x().toInt() - dragOffsetX,
                y = mouseEvent.y().toInt() - dragOffsetY,
            ),
            hudWidth = bounds.width,
            hudHeight = bounds.height,
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
        val dragBlock = activeDragBlock ?: return consumed
        activeDragBlock = null
        positions?.let(BtcHudPositionStore::save)
        return consumed || (dragBlock != null)
    }

    private fun buildVisuals(client: Minecraft, font: Font): List<BlockVisual> {
        val visuals = mutableListOf<BlockVisual>()
        if (ModuleStateStore.isSettingEnabled(BtcHudModule.showBpsKey)) {
            val label = String.format(java.util.Locale.US, "%.1f BPS", HudRuntimeStats.currentBps(client))
            visuals += blockVisual(BtcBlockId.BPS, label, compassIcon, font)
        }
        if (ModuleStateStore.isSettingEnabled(BtcHudModule.showTpsKey)) {
            val label = String.format(java.util.Locale.US, "%.1f TPS", HudRuntimeStats.currentTps(client))
            visuals += blockVisual(BtcBlockId.TPS, label, layersIcon, font)
        }
        if (ModuleStateStore.isSettingEnabled(BtcHudModule.showCoordsKey)) {
            val (x, y, z) = HudRuntimeStats.currentCoords(client)
            visuals += blockVisual(BtcBlockId.COORDS, "$x, $y, $z", mapPinIcon, font)
        }
        return visuals
    }

    private fun blockVisual(id: BtcBlockId, label: String, texture: Identifier, font: Font): BlockVisual {
        val textWidth = font.width(vText(label))
        val width = Layout.paddingX + Layout.iconSize + Layout.iconTextGap + textWidth + Layout.paddingX
        return BlockVisual(
            id = id,
            label = label,
            texture = texture,
            textureWidth = 512,
            textureHeight = 512,
            width = width,
            height = Layout.height,
        )
    }

    private fun drawBlock(context: GuiGraphics, font: Font, visual: BlockVisual) {
        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = visual.width,
            height = visual.height,
            style = blockStyle(),
        )
        val iconY = (visual.height - Layout.iconSize) / 2
        drawIcon(context, visual.texture, 10, iconY, Layout.iconSize, visual.textureWidth, visual.textureHeight)
        context.drawString(
            font,
            vText(visual.label),
            10 + Layout.iconSize + Layout.iconTextGap,
            (visual.height - font.lineHeight) / 2,
            VisualThemeSettings.textPrimary(),
            false,
        )
    }

    private fun drawIcon(
        context: GuiGraphics,
        texture: Identifier,
        x: Int,
        y: Int,
        size: Int,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        TextureFiltering.ensureSmooth(Minecraft.getInstance().textureManager, texture)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            0f,
            0f,
            size,
            size,
            textureWidth,
            textureHeight,
            textureWidth,
            textureHeight,
            btcIconTint(),
        )
    }

    private fun btcIconTint(): Int {
        return if (VisualThemeSettings.isLightPreset()) 0xFF111111.toInt() else VisualThemeSettings.textSecondary()
    }

    private fun blockStyle(): SdfPanelStyle {
        val accentSync = ModuleStateStore.isSettingEnabled("${BtcHudModule.moduleId}:accent_sync")
        val accent = if (accentSync) VisualThemeSettings.accentStrong() else 0xFF64C8E6.toInt()
        val neon = if (accentSync) VisualThemeSettings.neonBorder() else accent
        return SdfPanelStyle(
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.05f,
            radiusPx = Layout.radius,
            innerGlow = if (VisualThemeSettings.isTransparentPreset()) {
                SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.04f, opacity = 0.03f)
            } else {
                SdfGlowStyle.NONE
            },
            outerGlow = if (VisualThemeSettings.themeAllowsOuterGlow() && !VisualThemeSettings.isLightPreset()) {
                SdfGlowStyle(VisualThemeSettings.themedAccentGlowBase(accent), radiusPx = 14f, strength = 0.12f, opacity = 0.07f)
            } else {
                SdfGlowStyle.NONE
            },
            shade = if (VisualThemeSettings.isTransparentPreset()) {
                SdfShadeStyle(VisualThemeSettings.hudShellShadeTop(), VisualThemeSettings.hudShellShadeBottom())
            } else {
                SdfShadeStyle(0x00000000, 0x00000000)
            },
            neonBorder = if (VisualThemeSettings.themeAllowsNeon()) {
                SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neon, 0x96), widthPx = 0.9f, softnessPx = 4f, strength = 0.38f)
            } else {
                SdfNeonBorderStyle.NONE
            },
        )
    }

    private fun ensurePositions(client: Minecraft, visuals: List<BlockVisual>, scale: Float): MutableMap<BtcBlockId, BtcHudPosition> {
        val existing = positions
        if (existing != null) {
            visuals.forEachIndexed { index, visual ->
                if (!existing.containsKey(visual.id)) {
                    existing[visual.id] = defaultPositionFor(client, visuals, index, scale)
                }
            }
            return existing
        }

        val defaults = mutableMapOf<BtcBlockId, BtcHudPosition>()
        visuals.forEachIndexed { index, visual ->
            defaults[visual.id] = defaultPositionFor(client, visuals, index, scale)
        }
        return BtcHudPositionStore.load(defaults).also { positions = it }
    }

    private fun defaultPositionFor(client: Minecraft, visuals: List<BlockVisual>, index: Int, scale: Float): BtcHudPosition {
        val totalWidth = visuals.sumOf { scaled(it.width, scale) } + (Layout.blockGap * (visuals.size - 1))
        var currentX = ((client.window.guiScaledWidth - totalWidth) / 2).coerceAtLeast(0)
        for (i in 0 until index) {
            currentX += scaled(visuals[i].width, scale) + Layout.blockGap
        }
        val y = (client.window.guiScaledHeight - scaled(Layout.height, scale) - Layout.defaultBottomInset).coerceAtLeast(0)
        return BtcHudPosition(currentX, y)
    }

    private fun clampPosition(client: Minecraft, position: BtcHudPosition, hudWidth: Int, hudHeight: Int): BtcHudPosition {
        return BtcHudPosition(
            x = position.x.coerceIn(0, (client.window.guiScaledWidth - hudWidth).coerceAtLeast(0)),
            y = position.y.coerceIn(0, (client.window.guiScaledHeight - hudHeight).coerceAtLeast(0)),
        )
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${BtcHudModule.moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }
}
