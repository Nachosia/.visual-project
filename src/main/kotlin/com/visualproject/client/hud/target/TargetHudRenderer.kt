package com.visualproject.client.hud.target

import com.visualproject.client.vText
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class TargetHudRenderer {

    private object Style {
        const val panelFill = 0xF0101522.toInt()
        const val panelBorder = 0xCC39435A.toInt()
        const val previewFill = 0xCC121A2B.toInt()
        const val previewBorder = 0xC94A5676.toInt()
        const val barTrack = 0xA5202737.toInt()
        const val barBorder = 0x95414B65.toInt()
        const val barFill = 0xE87E63FF.toInt()
        const val slotFill = 0xA0121828.toInt()
        const val slotBorder = 0x8E3A4662.toInt()
        const val textPrimary = 0xFFF2F5FF.toInt()
        const val textSecondary = 0xFF9FAACC.toInt()
        const val sliderTrack = 0xB6222B3D.toInt()
        const val sliderTrackBorder = 0xA53B4763.toInt()
        const val sliderFill = 0xF08368FF.toInt()
        const val sliderKnob = 0xFFEDEFFF.toInt()
        const val sliderKnobBorder = 0xBB9AA6CF.toInt()
        const val sliderDockFill = 0xD00D1320.toInt()
        const val sliderDockBorder = 0xA5364462.toInt()
    }

    private enum class ActiveSlider {
        YAW,
        PITCH,
        ZOOM,
    }

    private val logger = LoggerFactory.getLogger("visualclient-target-hud")
    private val dragState = TargetHudDragState(TargetHudPositionStore.load(TargetHudPosition(14, 58)))

    private var lastBounds: TargetHudBounds? = null
    private var yawSliderBounds: TargetHudBounds? = null
    private var pitchSliderBounds: TargetHudBounds? = null
    private var zoomSliderBounds: TargetHudBounds? = null

    private var activeSlider: ActiveSlider? = null
    private var yawSlider = 0.50f
    private var pitchSlider = 0.46f
    private var zoomSlider = 0.0f

    fun render(
        context: GuiGraphics,
        @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker,
        client: Minecraft,
    ) {
        if (client.player == null || client.options.hideGui) return

        val target = TargetHudTargeting.currentTarget(client)
        val chatOpen = client.screen is ChatScreen
        val displayPlayer = target ?: if (chatOpen) client.player else null

        if (displayPlayer == null) {
            if (dragState.endDrag()) {
                TargetHudPositionStore.save(dragState.position)
            }
            lastBounds = null
            return
        }

        clampToScreen(client)

        val bounds = TargetHudBounds(
            x = dragState.position.x,
            y = dragState.position.y,
            width = TargetHudLayout.width,
            height = TargetHudLayout.height,
        )
        lastBounds = bounds

        if (chatOpen) {
            drawSliderDock(context, bounds)
        } else {
            yawSliderBounds = null
            pitchSliderBounds = null
            zoomSliderBounds = null
            activeSlider = null
        }

        drawRoundedPanel(
            context = context,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            fillColor = Style.panelFill,
            borderColor = Style.panelBorder,
            radius = TargetHudLayout.radius,
        )

        drawTargetContents(context, client.font, bounds, displayPlayer)
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        clampToScreen(client)

        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()

        if (yawSliderBounds?.contains(mouseX, mouseY) == true) {
            activeSlider = ActiveSlider.YAW
            updateSliderFromMouse(mouseX)
            return true
        }
        if (pitchSliderBounds?.contains(mouseX, mouseY) == true) {
            activeSlider = ActiveSlider.PITCH
            updateSliderFromMouse(mouseX)
            return true
        }
        if (zoomSliderBounds?.contains(mouseX, mouseY) == true) {
            activeSlider = ActiveSlider.ZOOM
            updateSliderFromMouse(mouseX)
            return true
        }

        val bounds = lastBounds ?: TargetHudBounds(
            x = dragState.position.x,
            y = dragState.position.y,
            width = TargetHudLayout.width,
            height = TargetHudLayout.height,
        )
        val handled = dragState.beginDrag(bounds, mouseX, mouseY)
        if (handled) {
            logger.info("target-hud: drag-start mouse=({}, {}) hud=({}, {})", mouseX, mouseY, bounds.x, bounds.y)
        }

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

        val mouseX = mouseEvent.x().toInt()
        if (activeSlider != null) {
            updateSliderFromMouse(mouseX)
            return true
        }

        if (!dragState.dragging) return consumed

        dragState.dragTo(
            mouseX = mouseX,
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = TargetHudLayout.width,
            hudHeight = TargetHudLayout.height,
        )
        if (horizontalAmount != 0.0 || verticalAmount != 0.0) {
            logger.info(
                "target-hud: dragging x={} y={} dx={} dy={}",
                dragState.position.x,
                dragState.position.y,
                "%.2f".format(horizontalAmount),
                "%.2f".format(verticalAmount),
            )
        }

        return true
    }

    fun onScreenMouseRelease(
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        if (activeSlider != null) {
            activeSlider = null
            return true
        }

        val ended = dragState.endDrag()
        if (ended) {
            TargetHudPositionStore.save(dragState.position)
            logger.info(
                "target-hud: drag-end saved x={} y={}",
                dragState.position.x,
                dragState.position.y,
            )
        }

        return consumed || ended
    }

    private fun clampToScreen(client: Minecraft) {
        dragState.setPositionClamped(
            x = dragState.position.x,
            y = dragState.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = TargetHudLayout.width,
            hudHeight = TargetHudLayout.height,
        )
    }

    private fun drawTargetContents(
        context: GuiGraphics,
        font: Font,
        bounds: TargetHudBounds,
        target: Player,
    ) {
        val padding = TargetHudLayout.panelPadding
        val previewWidth = TargetHudLayout.previewWidth
        val previewHeight = TargetHudLayout.previewHeight
        val previewX = bounds.x + padding
        val previewY = bounds.y + (bounds.height - previewHeight) / 2

        drawRoundedPanel(
            context = context,
            x = previewX,
            y = previewY,
            width = previewWidth,
            height = previewHeight,
            fillColor = Style.previewFill,
            borderColor = Style.previewBorder,
            radius = TargetHudLayout.previewRadius,
        )
        drawTargetPreview(
            context = context,
            target = target,
            x = previewX,
            y = previewY,
            width = previewWidth,
            height = previewHeight,
        )

        val rightX = previewX + previewWidth + 8
        val rightWidth = bounds.x + bounds.width - padding - rightX

        val currentHp = target.health.coerceAtLeast(0f)
        val maxHp = target.maxHealth.coerceAtLeast(1f)
        val hpText = "HP ${"%.1f".format(currentHp)} / ${"%.1f".format(maxHp)}"
        context.drawString(
            font,
            vText(hpText),
            rightX,
            bounds.y + 9,
            Style.textPrimary,
            false,
        )

        val barX = rightX
        val barY = bounds.y + 20
        val barWidth = rightWidth
        val barHeight = 6
        drawRoundedPanel(
            context = context,
            x = barX,
            y = barY,
            width = barWidth,
            height = barHeight,
            fillColor = Style.barTrack,
            borderColor = Style.barBorder,
            radius = 4,
        )

        val hpRatio = (currentHp / maxHp).coerceIn(0f, 1f)
        val hpFill = (barWidth * hpRatio).roundToInt().coerceIn(0, barWidth)
        if (hpFill > 2) {
            fillRoundedRect(
                context = context,
                x = barX + 1,
                y = barY + 1,
                width = hpFill - 2,
                height = barHeight - 2,
                radius = 3,
                color = Style.barFill,
            )
        }

        val nameY = bounds.y + bounds.height - padding - font.lineHeight

        // Explicit equipment layout model (2 columns x 3 rows) in the area
        // between HP bar and nickname baseline.
        val slotSize = TargetHudLayout.itemSlotSize
        val slotGapX = 8
        val slotGapY = 2
        val slotBlockWidth = (slotSize * 2) + slotGapX
        val slotBlockHeight = (slotSize * 3) + (slotGapY * 2)

        val slotAreaLeft = rightX
        val slotAreaRight = bounds.x + bounds.width - padding
        val slotAreaTop = barY + barHeight + 3
        val slotAreaBottom = nameY - 2

        val slotAreaHeight = (slotAreaBottom - slotAreaTop).coerceAtLeast(slotBlockHeight)

        val slotBlockRightInset = 2
        val slotBlockX = (slotAreaRight - slotBlockWidth - slotBlockRightInset).coerceAtLeast(slotAreaLeft)
        val slotBlockY = slotAreaTop + ((slotAreaHeight - slotBlockHeight) / 2)

        val col1X = slotBlockX
        val col2X = col1X + slotSize + slotGapX
        val row1Y = slotBlockY
        val row2Y = row1Y + slotSize + slotGapY
        val row3Y = row2Y + slotSize + slotGapY

        // Row 1: mainhand / offhand
        drawItemSlot(context, font, target.mainHandItem, col1X, row1Y, slotSize)
        drawItemSlot(context, font, target.offhandItem, col2X, row1Y, slotSize)

        // Row 2: helmet / chestplate
        val helmet = target.getItemBySlot(EquipmentSlot.HEAD)
        val chestplate = target.getItemBySlot(EquipmentSlot.CHEST)
        drawItemSlot(context, font, helmet, col1X, row2Y, slotSize)
        drawItemSlot(context, font, chestplate, col2X, row2Y, slotSize)

        // Row 3: leggings / boots
        val leggings = target.getItemBySlot(EquipmentSlot.LEGS)
        val boots = target.getItemBySlot(EquipmentSlot.FEET)
        drawItemSlot(context, font, leggings, col1X, row3Y, slotSize)
        drawItemSlot(context, font, boots, col2X, row3Y, slotSize)

        val nameX = rightX
        val nameMaxWidth = rightWidth.coerceAtLeast(20)
        val displayName = fitText(font, target.name.string, nameMaxWidth)
        context.drawString(
            font,
            vText(displayName),
            nameX,
            nameY,
            Style.textSecondary,
            false,
        )
    }

    private fun drawSliderDock(context: GuiGraphics, bounds: TargetHudBounds) {
        val dockWidth = (bounds.width - 20).coerceAtLeast(96)
        val dockHeight = 27
        val dockX = bounds.x + (bounds.width - dockWidth) / 2
        val dockY = bounds.y + bounds.height - 3

        drawRoundedPanel(
            context = context,
            x = dockX,
            y = dockY,
            width = dockWidth,
            height = dockHeight,
            fillColor = Style.sliderDockFill,
            borderColor = Style.sliderDockBorder,
            radius = 10,
        )

        val trackX = dockX + TargetHudLayout.sliderHorizontalInset
        val trackWidth = (dockWidth - (TargetHudLayout.sliderHorizontalInset * 2)).coerceAtLeast(36)
        val yawTrackY = dockY + 4
        val pitchTrackY = yawTrackY + TargetHudLayout.sliderTrackHeight + TargetHudLayout.sliderGap
        val zoomTrackY = pitchTrackY + TargetHudLayout.sliderTrackHeight + TargetHudLayout.sliderGap

        yawSliderBounds = TargetHudBounds(
            x = trackX,
            y = yawTrackY - 2,
            width = trackWidth,
            height = TargetHudLayout.sliderTrackHeight + 6,
        )
        pitchSliderBounds = TargetHudBounds(
            x = trackX,
            y = pitchTrackY - 2,
            width = trackWidth,
            height = TargetHudLayout.sliderTrackHeight + 6,
        )
        zoomSliderBounds = TargetHudBounds(
            x = trackX,
            y = zoomTrackY - 2,
            width = trackWidth,
            height = TargetHudLayout.sliderTrackHeight + 6,
        )

        drawPreviewSlider(context, trackX, yawTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, yawSlider)
        drawPreviewSlider(context, trackX, pitchTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, pitchSlider)
        drawPreviewSlider(context, trackX, zoomTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, zoomSlider)
    }

    private fun drawPreviewSlider(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: Float,
    ) {
        drawRoundedPanel(
            context = context,
            x = x,
            y = y,
            width = width,
            height = height,
            fillColor = Style.sliderTrack,
            borderColor = Style.sliderTrackBorder,
            radius = 3,
        )

        val filledWidth = (width * value.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1)
        fillRoundedRect(
            context = context,
            x = x + 1,
            y = y + 1,
            width = (filledWidth - 2).coerceAtLeast(1),
            height = (height - 2).coerceAtLeast(1),
            radius = 2,
            color = Style.sliderFill,
        )

        val knobSize = 8
        val knobCenterX = (x + (width * value.coerceIn(0f, 1f))).roundToInt().coerceIn(x + 4, x + width - 4)
        val knobX = knobCenterX - knobSize / 2
        val knobY = y + (height - knobSize) / 2
        drawRoundedPanel(
            context = context,
            x = knobX,
            y = knobY,
            width = knobSize,
            height = knobSize,
            fillColor = Style.sliderKnob,
            borderColor = Style.sliderKnobBorder,
            radius = 4,
        )
    }

    private fun drawTargetPreview(
        context: GuiGraphics,
        target: Player,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val innerPadding = 4
        val left = x + innerPadding
        val top = y + innerPadding
        val right = x + width - innerPadding
        val bottom = y + height - innerPadding
        val previewDownShiftPx = 8
        val renderTop = (top + previewDownShiftPx).coerceAtMost(bottom - 6)
        val renderBottom = bottom

        val renderWidth = (right - left).coerceAtLeast(8)
        val renderHeight = (bottom - top).coerceAtLeast(8)

        // Further reduce size so full body has comfortable margins in the preview frame
        val basePreviewSize = (min(renderWidth, renderHeight) * 0.62f).coerceAtLeast(18f)
        // Third slider controls distance/zoom: 0 = near(current), 1 = farther away
        val zoomFactor = lerp(1.0f, 0.52f, zoomSlider.coerceIn(0f, 1f))
        val previewSize = (basePreviewSize * zoomFactor).coerceAtLeast(12f)

        // Full 360 yaw + limited pitch range
        val yawDegrees = yawSlider.coerceIn(0f, 1f) * 360f
        val pitchDegrees = ((pitchSlider.coerceIn(0f, 1f) * 2f) - 1f) * 46f
        // Forward-facing default for this render path
        val baseYaw = 0f
        val finalYaw = baseYaw + yawDegrees
        val finalPitch = pitchDegrees
        val yawRad = Math.toRadians(finalYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(finalPitch.toDouble()).toFloat()

        // NameMC-like preview orbit:
        // - yaw turns the preview around the vertical center axis
        // - pitch changes vertical view angle while keeping a standing pose
        val modelRotation = Quaternionf()
            .rotateZ(Math.PI.toFloat())
            .rotateY(yawRad)
            .rotateX(pitchRad)

        val cameraRotation = Quaternionf().rotateX(pitchRad)
        try {
            val renderState = Minecraft.getInstance().entityRenderDispatcher.extractEntity(target, 1f)

            if (renderState is LivingEntityRenderState) {
                // Hard-detach preview orientation from in-world player look direction
                // and keep the model in a normal standing state.
                renderState.bodyRot = 180f
                renderState.yRot = 0f
                renderState.xRot = if (renderState.pose == Pose.FALL_FLYING) 0f else -finalPitch

                val normalizedScale = if (renderState.scale == 0f) 1f else renderState.scale
                renderState.boundingBoxWidth /= normalizedScale
                renderState.boundingBoxHeight /= normalizedScale
                renderState.scale = 1f
            }

            // Center pivot for both yaw and pitch orbit so there is no "feet-anchored wobble".
            val pivotCenterY = renderState.boundingBoxHeight * 0.5f
            val translation = Vector3f(0f, pivotCenterY, 0f)

            // Strip all non-model extras so preview area contains only frame + model
            renderState.lightCoords = 0x00F000F0
            renderState.outlineColor = 0
            renderState.shadowRadius = 0f
            renderState.shadowPieces.clear()
            renderState.leashStates?.clear()
            renderState.displayFireAnimation = false
            renderState.nameTag = null

            context.submitEntityRenderState(
                renderState,
                previewSize,
                translation,
                modelRotation,
                cameraRotation,
                left,
                renderTop,
                right,
                renderBottom,
            )
        } catch (_: Throwable) {
            // Fallback path for unexpected signature/runtime differences
            val centerX = (left + right) * 0.5f
            val centerY = (top + bottom) * 0.5f
            val yawNormalized = (yawSlider.coerceIn(0f, 1f) * 2f) - 1f
            val pitchNormalized = (pitchSlider.coerceIn(0f, 1f) * 2f) - 1f
            val syntheticMouseX = centerX - (yawNormalized * (renderWidth * 0.42f))
            val syntheticMouseY = centerY - (pitchNormalized * (renderHeight * 0.30f))
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                context,
                left,
                renderTop,
                right,
                renderBottom,
                previewSize.roundToInt(),
                0f,
                syntheticMouseX,
                syntheticMouseY,
                target,
            )
        }
    }

    private fun drawItemSlot(
        context: GuiGraphics,
        font: Font,
        stack: ItemStack,
        x: Int,
        y: Int,
        size: Int,
    ) {
        drawRoundedPanel(
            context = context,
            x = x,
            y = y,
            width = size,
            height = size,
            fillColor = Style.slotFill,
            borderColor = Style.slotBorder,
            radius = 6,
        )
        if (stack.isEmpty) return

        val itemX = x + ((size - 16) / 2)
        val itemY = y + ((size - 16) / 2)
        context.renderItem(stack, itemX, itemY)
        context.renderItemDecorations(font, stack, itemX, itemY)
    }

    private fun updateSliderFromMouse(mouseX: Int) {
        when (activeSlider) {
            ActiveSlider.YAW -> {
                val bounds = yawSliderBounds ?: return
                yawSlider = ((mouseX - bounds.x).toFloat() / bounds.width.toFloat()).coerceIn(0f, 1f)
            }
            ActiveSlider.PITCH -> {
                val bounds = pitchSliderBounds ?: return
                pitchSlider = ((mouseX - bounds.x).toFloat() / bounds.width.toFloat()).coerceIn(0f, 1f)
            }
            ActiveSlider.ZOOM -> {
                val bounds = zoomSliderBounds ?: return
                zoomSlider = ((mouseX - bounds.x).toFloat() / bounds.width.toFloat()).coerceIn(0f, 1f)
            }
            null -> Unit
        }
    }
}

private fun lerp(from: Float, to: Float, t: Float): Float {
    return from + (to - from) * t
}

private fun fitText(font: Font, source: String, maxWidth: Int): String {
    if (maxWidth <= 0 || source.isBlank()) return ""
    if (font.width(source) <= maxWidth) return source
    if (font.width("...") > maxWidth) return ""

    var end = source.length
    while (end > 0) {
        val candidate = source.substring(0, end).trimEnd() + "..."
        if (font.width(candidate) <= maxWidth) {
            return candidate
        }
        end--
    }

    return ""
}

private fun drawRoundedPanel(
    context: GuiGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    fillColor: Int,
    borderColor: Int,
    radius: Int,
) {
    if (width <= 0 || height <= 0) return
    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)
    fillRoundedRect(context, x, y, width, height, cornerRadius, borderColor)
    if (width > 2 && height > 2) {
        fillRoundedRect(
            context,
            x + 1,
            y + 1,
            width - 2,
            height - 2,
            (cornerRadius - 1).coerceAtLeast(0),
            fillColor,
        )
    }
}

private fun fillRoundedRect(
    context: GuiGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    radius: Int,
    color: Int,
) {
    if (width <= 0 || height <= 0) return

    if (radius <= 0) {
        context.fill(x, y, x + width, y + height, color)
        return
    }

    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)
    context.fill(x + cornerRadius, y, x + width - cornerRadius, y + height, color)
    context.fill(x, y + cornerRadius, x + cornerRadius, y + height - cornerRadius, color)
    context.fill(x + width - cornerRadius, y + cornerRadius, x + width, y + height - cornerRadius, color)

    for (row in 0 until cornerRadius) {
        val dy = cornerRadius - row - 1
        val dx = sqrt((cornerRadius * cornerRadius - dy * dy).toDouble()).toInt().coerceAtMost(cornerRadius)
        val left = x + cornerRadius - dx
        val right = x + width - cornerRadius + dx
        context.fill(left, y + row, right, y + row + 1, color)
        context.fill(left, y + height - row - 1, right, y + height - row, color)
    }
}
