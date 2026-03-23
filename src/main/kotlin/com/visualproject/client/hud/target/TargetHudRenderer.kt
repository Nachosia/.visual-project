package com.visualproject.client.hud.target

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.vText
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.roundToInt

internal class TargetHudRenderer {

    private object Style {
        val textPrimary: Int
            get() = VisualThemeSettings.textPrimary()

        val textSecondary: Int
            get() = VisualThemeSettings.textSecondary()

        val nameText: Int
            get() = if (VisualThemeSettings.isLightPreset()) 0xFF111111.toInt() else textSecondary

        fun shell(): SdfPanelStyle = SdfPanelStyle(
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.3f,
            radiusPx = TargetHudLayout.radius.toFloat(),
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 14f, strength = 0.04f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(VisualThemeSettings.themedAccentGlowBase(), radiusPx = 22f, strength = if (VisualThemeSettings.isLightPreset()) 0.10f else 0.14f, opacity = if (VisualThemeSettings.isLightPreset()) 0.06f else 0.08f),
            shade = SdfShadeStyle(VisualThemeSettings.hudShellShadeTop(), VisualThemeSettings.hudShellShadeBottom()),
            neonBorder = SdfNeonBorderStyle(
                VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x72 else 0xA2),
                widthPx = 1.0f,
                softnessPx = 5f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.40f else 0.64f,
            ),
        )

        fun preview(): SdfPanelStyle = SdfPanelStyle(
            baseColor = VisualThemeSettings.hudInnerFill(),
            borderColor = VisualThemeSettings.hudInnerBorder(),
            borderWidthPx = 1.1f,
            radiusPx = TargetHudLayout.previewRadius.toFloat(),
            innerGlow = SdfGlowStyle(VisualThemeSettings.accentStrong(), radiusPx = 12f, strength = 0.06f, opacity = 0.05f),
            outerGlow = SdfGlowStyle(if (VisualThemeSettings.isLightPreset()) 0xFFE3EAF4.toInt() else 0xFF000000.toInt(), radiusPx = 14f, strength = if (VisualThemeSettings.isLightPreset()) 0.08f else 0.12f, opacity = if (VisualThemeSettings.isLightPreset()) 0.08f else 0.12f),
            shade = SdfShadeStyle(
                if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x0EFFFFFF,
                if (VisualThemeSettings.isLightPreset()) 0x0CD1DCEC else 0x16000000,
            ),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x38 else 0x56), widthPx = 0.9f, softnessPx = 4f, strength = if (VisualThemeSettings.isLightPreset()) 0.20f else 0.34f),
        )

        fun barTrack(): SdfPanelStyle = SdfPanelStyle(
            baseColor = VisualThemeSettings.hudTrackFill(),
            borderColor = VisualThemeSettings.hudTrackBorder(),
            borderWidthPx = 1.0f,
            radiusPx = 5f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f),
            shade = SdfShadeStyle(0x08FFFFFF, if (VisualThemeSettings.isLightPreset()) 0x08D0DBEA else 0x10000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x20 else 0x34), widthPx = 0.75f, softnessPx = 3.5f, strength = if (VisualThemeSettings.isLightPreset()) 0.12f else 0.20f),
        )

        fun barFill(ratio: Float): SdfPanelStyle {
            val fill = blendColor(0xFFE86573.toInt(), VisualThemeSettings.accentStrong(), ratio)
            val glow = blendColor(0xFFFC9E87.toInt(), VisualThemeSettings.accentStrong(), ratio)
            return SdfPanelStyle(
                baseColor = fill,
                borderColor = blendColor(fill, 0xFFFFFFFF.toInt(), 0.15f),
                borderWidthPx = 0.8f,
                radiusPx = 4f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.05f, opacity = 0.04f),
                outerGlow = SdfGlowStyle(glow, radiusPx = 8f, strength = 0.16f, opacity = 0.08f),
                shade = SdfShadeStyle(0x10FFFFFF, 0x0E000000),
                neonBorder = SdfNeonBorderStyle(blendColor(VisualThemeSettings.neonBorder(), glow, 0.45f), widthPx = 0.9f, softnessPx = 4f, strength = 0.48f),
            )
        }

        fun slot(filled: Boolean): SdfPanelStyle = SdfPanelStyle(
            baseColor = if (filled) {
                if (VisualThemeSettings.isLightPreset()) blendColor(0xFFF0F5FC.toInt(), VisualThemeSettings.sliderFill(), 0.42f) else 0xC6141C2E.toInt()
            } else {
                if (VisualThemeSettings.isLightPreset()) 0xD7E4EEF8.toInt() else 0x9E111827.toInt()
            },
            borderColor = if (filled) {
                if (VisualThemeSettings.isLightPreset()) blendColor(0xFFCAD7E8.toInt(), VisualThemeSettings.neonBorder(), 0.18f) else 0x7A50638D
            } else {
                if (VisualThemeSettings.isLightPreset()) 0x8DBFD1E4.toInt() else 0x5632405D
            },
            borderWidthPx = 1.0f,
            radiusPx = 7f,
            innerGlow = SdfGlowStyle(
                color = if (filled) VisualThemeSettings.accentStrong() else 0xFFFFFFFF.toInt(),
                radiusPx = 8f,
                strength = if (filled) 0.08f else 0.02f,
                opacity = if (filled) 0.06f else 0.02f,
            ),
            outerGlow = SdfGlowStyle(
                color = if (filled) VisualThemeSettings.themedAccentGlowBase() else if (VisualThemeSettings.isLightPreset()) 0xFFE5ECF6.toInt() else 0xFF000000.toInt(),
                radiusPx = 10f,
                strength = if (filled) 0.10f else if (VisualThemeSettings.isLightPreset()) 0.05f else 0.08f,
                opacity = if (filled) 0.06f else if (VisualThemeSettings.isLightPreset()) 0.05f else 0.08f,
            ),
            shade = SdfShadeStyle(0x08FFFFFF, if (VisualThemeSettings.isLightPreset()) 0x08D0DBEA else 0x10000000),
            neonBorder = SdfNeonBorderStyle(
                color = if (filled) {
                    VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x44 else 0x70)
                } else {
                    VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x16 else 0x24)
                },
                widthPx = if (filled) 0.9f else 0.75f,
                softnessPx = 4f,
                strength = if (filled) {
                    if (VisualThemeSettings.isLightPreset()) 0.22f else 0.38f
                } else {
                    if (VisualThemeSettings.isLightPreset()) 0.08f else 0.14f
                },
            ),
        )

        fun sliderDock(): SdfPanelStyle = SdfPanelStyle(
            baseColor = VisualThemeSettings.hudInnerFill(),
            borderColor = VisualThemeSettings.hudInnerBorder(),
            borderWidthPx = 1.1f,
            radiusPx = 12f,
            innerGlow = SdfGlowStyle(VisualThemeSettings.accentStrong(), radiusPx = 12f, strength = 0.05f, opacity = 0.04f),
            outerGlow = SdfGlowStyle(if (VisualThemeSettings.isLightPreset()) 0xFFE4ECF6.toInt() else 0xFF000000.toInt(), radiusPx = 14f, strength = if (VisualThemeSettings.isLightPreset()) 0.06f else 0.10f, opacity = if (VisualThemeSettings.isLightPreset()) 0.07f else 0.12f),
            shade = SdfShadeStyle(0x0CFFFFFF, if (VisualThemeSettings.isLightPreset()) 0x0CD0DBEA else 0x14000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x30 else 0x5A), widthPx = 0.9f, softnessPx = 4f, strength = if (VisualThemeSettings.isLightPreset()) 0.18f else 0.30f),
        )

        fun sliderTrack(active: Boolean): SdfPanelStyle = SdfPanelStyle(
            baseColor = if (active) {
                if (VisualThemeSettings.isLightPreset()) 0xD9E5EEF8.toInt() else 0xCF172133.toInt()
            } else {
                if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudTrackFill() else 0xB71A2335.toInt()
            },
            borderColor = if (active) {
                if (VisualThemeSettings.isLightPreset()) blendColor(0xFFC4D2E5.toInt(), VisualThemeSettings.accentStrong(), 0.18f) else blendColor(0xA752638B.toInt(), VisualThemeSettings.accentStrong(), 0.35f)
            } else {
                if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudTrackBorder() else 0x863B4B67.toInt()
            },
            borderWidthPx = 1.0f,
            radiusPx = 3f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(VisualThemeSettings.themedAccentGlowBase(), radiusPx = 8f, strength = if (active) if (VisualThemeSettings.isLightPreset()) 0.08f else 0.12f else if (VisualThemeSettings.isLightPreset()) 0.05f else 0.08f, opacity = if (active) if (VisualThemeSettings.isLightPreset()) 0.05f else 0.08f else if (VisualThemeSettings.isLightPreset()) 0.03f else 0.04f),
            shade = SdfShadeStyle(0x08FFFFFF, if (VisualThemeSettings.isLightPreset()) 0x08D0DBEA else 0x0E000000),
            neonBorder = SdfNeonBorderStyle(
                if (active) VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x40 else 0x74) else VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x20 else 0x34),
                widthPx = 0.8f,
                softnessPx = 3.5f,
                strength = if (active) if (VisualThemeSettings.isLightPreset()) 0.24f else 0.42f else if (VisualThemeSettings.isLightPreset()) 0.12f else 0.22f,
            ),
        )

        fun sliderFill(): SdfPanelStyle = SdfPanelStyle(
            baseColor = if (VisualThemeSettings.isLightPreset()) blendColor(0xFFF5F9FE.toInt(), VisualThemeSettings.sliderFill(), 0.72f) else VisualThemeSettings.sliderFill(),
            borderColor = if (VisualThemeSettings.isLightPreset()) blendColor(0xFFD3E0EF.toInt(), VisualThemeSettings.sliderFill(), 0.30f) else blendColor(VisualThemeSettings.sliderFill(), 0xFFFFFFFF.toInt(), 0.18f),
            borderWidthPx = 0.8f,
            radiusPx = 2.5f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.06f, opacity = 0.05f),
            outerGlow = SdfGlowStyle(VisualThemeSettings.sliderFill(), radiusPx = 8f, strength = if (VisualThemeSettings.isLightPreset()) 0.10f else 0.16f, opacity = if (VisualThemeSettings.isLightPreset()) 0.07f else 0.10f),
            shade = SdfShadeStyle(0x10FFFFFF, if (VisualThemeSettings.isLightPreset()) 0x08D0DBEA else 0x0A000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x78 else 0xD5), widthPx = 0.9f, softnessPx = 4f, strength = if (VisualThemeSettings.isLightPreset()) 0.28f else 0.55f),
        )

        fun sliderKnob(active: Boolean): SdfPanelStyle = SdfPanelStyle(
            baseColor = if (VisualThemeSettings.isLightPreset()) blendColor(0xFFFFFFFF.toInt(), VisualThemeSettings.sliderKnob(), 0.58f) else VisualThemeSettings.sliderKnob(),
            borderColor = if (active) blendColor(VisualThemeSettings.sliderKnob(), VisualThemeSettings.accentStrong(), if (VisualThemeSettings.isLightPreset()) 0.18f else 0.30f) else if (VisualThemeSettings.isLightPreset()) 0xFFC4D0E4.toInt() else 0xFFB3BEDD.toInt(),
            borderWidthPx = 1.0f,
            radiusPx = 5f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.06f, opacity = 0.06f),
            outerGlow = SdfGlowStyle(if (active) VisualThemeSettings.themedAccentGlowBase() else if (VisualThemeSettings.isLightPreset()) 0xFFE4ECF6.toInt() else 0xFFAAB6DB.toInt(), radiusPx = 8f, strength = if (VisualThemeSettings.isLightPreset()) 0.10f else 0.16f, opacity = if (active) if (VisualThemeSettings.isLightPreset()) 0.08f else 0.12f else if (VisualThemeSettings.isLightPreset()) 0.05f else 0.07f),
            shade = SdfShadeStyle(0x0EFFFFFF, if (VisualThemeSettings.isLightPreset()) 0x06CDD8E8 else 0x08000000),
            neonBorder = SdfNeonBorderStyle(
                if (active) VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x74 else 0xCD) else VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x40 else 0x6F),
                widthPx = 0.9f,
                softnessPx = 4f,
                strength = if (active) if (VisualThemeSettings.isLightPreset()) 0.30f else 0.58f else if (VisualThemeSettings.isLightPreset()) 0.18f else 0.32f,
            ),
        )
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
    private var lastScale = -1f

    fun render(
        context: GuiGraphics,
        @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker,
        client: Minecraft,
    ) {
        if (client.player == null || client.options.hideGui) return
        val scale = hudScale()

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

        adjustPositionForScaleChange(client, scale)
        clampToScreen(client, scale)

        val actualBounds = TargetHudBounds(
            x = dragState.position.x,
            y = dragState.position.y,
            width = scaled(TargetHudLayout.width, scale),
            height = scaled(TargetHudLayout.height, scale),
        )
        val localBounds = TargetHudBounds(
            x = 0,
            y = 0,
            width = TargetHudLayout.width,
            height = TargetHudLayout.height,
        )
        lastBounds = actualBounds
        val mouseX = client.mouseHandler.getScaledXPos(client.window).toInt()
        val mouseY = client.mouseHandler.getScaledYPos(client.window).toInt()
        val localMouseX = toLocal(mouseX, actualBounds.x, scale)
        val localMouseY = toLocal(mouseY, actualBounds.y, scale)

        context.pose().pushMatrix()
        context.pose().translate(actualBounds.x.toFloat(), actualBounds.y.toFloat())
        context.pose().scale(scale, scale)

        if (chatOpen) {
            drawSliderDock(context, localBounds)
        } else {
            yawSliderBounds = null
            pitchSliderBounds = null
            zoomSliderBounds = null
            activeSlider = null
        }

        SdfPanelRenderer.draw(
            context = context,
            x = localBounds.x,
            y = localBounds.y,
            width = localBounds.width,
            height = localBounds.height,
            style = Style.shell(),
        )

        drawTargetContents(context, client.font, localBounds, actualBounds, scale, displayPlayer)
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
        clampToScreen(client, scale)

        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()
        val bounds = lastBounds ?: TargetHudBounds(
            x = dragState.position.x,
            y = dragState.position.y,
            width = scaled(TargetHudLayout.width, scale),
            height = scaled(TargetHudLayout.height, scale),
        )
        val localMouseX = toLocal(mouseX, bounds.x, scale)
        val localMouseY = toLocal(mouseY, bounds.y, scale)

        if (yawSliderBounds?.contains(localMouseX, localMouseY) == true) {
            activeSlider = ActiveSlider.YAW
            updateSliderFromMouse(localMouseX)
            return true
        }
        if (pitchSliderBounds?.contains(localMouseX, localMouseY) == true) {
            activeSlider = ActiveSlider.PITCH
            updateSliderFromMouse(localMouseX)
            return true
        }
        if (zoomSliderBounds?.contains(localMouseX, localMouseY) == true) {
            activeSlider = ActiveSlider.ZOOM
            updateSliderFromMouse(localMouseX)
            return true
        }

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

        val scale = hudScale()
        val mouseX = mouseEvent.x().toInt()
        if (activeSlider != null) {
            val bounds = lastBounds ?: return consumed
            updateSliderFromMouse(toLocal(mouseX, bounds.x, scale))
            return true
        }

        if (!dragState.dragging) return consumed

        dragState.dragTo(
            mouseX = mouseX,
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(TargetHudLayout.width, scale),
            hudHeight = scaled(TargetHudLayout.height, scale),
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

    private fun clampToScreen(client: Minecraft, scale: Float) {
        dragState.setPositionClamped(
            x = dragState.position.x,
            y = dragState.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(TargetHudLayout.width, scale),
            hudHeight = scaled(TargetHudLayout.height, scale),
        )
    }

    private fun adjustPositionForScaleChange(client: Minecraft, scale: Float) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (kotlin.math.abs(lastScale - scale) < 0.001f || dragState.dragging) {
            lastScale = scale
            return
        }

        val oldWidth = scaled(TargetHudLayout.width, lastScale)
        val oldHeight = scaled(TargetHudLayout.height, lastScale)
        val newWidth = scaled(TargetHudLayout.width, scale)
        val newHeight = scaled(TargetHudLayout.height, scale)
        val targetX = dragState.position.x - ((newWidth - oldWidth) / 2)
        val targetY = dragState.position.y - ((newHeight - oldHeight) / 2)
        dragState.setPositionClamped(
            x = targetX,
            y = targetY,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = newWidth,
            hudHeight = newHeight,
        )
        TargetHudPositionStore.save(dragState.position)
        lastScale = scale
    }

    private fun drawTargetContents(
        context: GuiGraphics,
        font: Font,
        bounds: TargetHudBounds,
        actualBounds: TargetHudBounds,
        scale: Float,
        target: Player,
    ) {
        val padding = TargetHudLayout.panelPadding
        val previewWidth = TargetHudLayout.previewWidth
        val previewHeight = TargetHudLayout.previewHeight
        val previewX = bounds.x + padding
        val previewY = bounds.y + (bounds.height - previewHeight) / 2
        val previewActualX = actualBounds.x + scaled(previewX, scale)
        val previewActualY = actualBounds.y + scaled(previewY, scale)
        val previewActualWidth = scaled(previewWidth, scale)
        val previewActualHeight = scaled(previewHeight, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = previewX,
            y = previewY,
            width = previewWidth,
            height = previewHeight,
            style = Style.preview(),
        )
        drawTargetPreview(
            context = context,
            target = target,
            x = previewActualX,
            y = previewActualY,
            width = previewActualWidth,
            height = previewActualHeight,
        )

        val rightX = previewX + previewWidth + 8
        val rightWidth = bounds.x + bounds.width - padding - rightX

        val currentHp = target.health.coerceAtLeast(0f)
        val maxHp = target.maxHealth.coerceAtLeast(1f)
        val hpText = "HP ${"%.1f".format(currentHp)} / ${"%.1f".format(maxHp)}"
        val hpTextY = bounds.y + 9
        context.drawString(
            font,
            vText(hpText),
            rightX,
            hpTextY,
            Style.textPrimary,
            false,
        )

        val barX = rightX
        val barY = bounds.y + 20
        val barWidth = rightWidth
        val barHeight = 6
        SdfPanelRenderer.draw(
            context = context,
            x = barX,
            y = barY,
            width = barWidth,
            height = barHeight,
            style = Style.barTrack(),
        )

        val hpRatio = (currentHp / maxHp).coerceIn(0f, 1f)
        val hpFill = (barWidth * hpRatio).roundToInt().coerceIn(0, barWidth)
        if (hpFill > 1) {
            SdfPanelRenderer.draw(
                context = context,
                x = barX + 1,
                y = barY + 1,
                width = (hpFill - 2).coerceAtLeast(1),
                height = (barHeight - 2).coerceAtLeast(1),
                style = Style.barFill(hpRatio),
            )
        }

        val nameY = bounds.y + bounds.height - padding - font.lineHeight

        // Two independent groups:
        // 1) held items row directly under HP text
        // 2) armor 2x2 grid under HP bar on the right side
        val slotSize = TargetHudLayout.itemSlotSize
        val slotGapX = 8
        val slotGapY = 3
        val rowWidth = (slotSize * 2) + slotGapX
        val rightContentRight = bounds.x + bounds.width - padding

        // Group B: armor grid (under HP bar, anchored to right side)
        val armorBlockHeight = (slotSize * 2) + slotGapY
        val armorLeftX = (rightContentRight - rowWidth - 2).coerceAtLeast(rightX)
        val armorRightX = armorLeftX + slotSize + slotGapX
        val armorTopPreferredY = barY + barHeight + 2
        val armorTopMaxY = nameY - armorBlockHeight - 10
        val armorTopY = armorTopPreferredY.coerceAtMost(armorTopMaxY)
        val armorBottomY = armorTopY + slotSize + slotGapY

        // Group A: held items row (raised to align with armor-block vertical center)
        val heldRowX = (rightX + 6).coerceAtMost(rightContentRight - rowWidth - 2).coerceAtLeast(rightX)
        val heldRowYMax = nameY - slotSize - 2
        val heldRowYMin = hpTextY + font.lineHeight + 2
        val heldRowYTarget = armorTopY + ((armorBlockHeight - slotSize) / 2)
        val heldRowY = heldRowYTarget.coerceIn(heldRowYMin, heldRowYMax)
        val heldLeftX = heldRowX
        val heldRightX = heldLeftX + slotSize + slotGapX

        // Held items row
        drawItemSlot(context, font, target.mainHandItem, heldLeftX, heldRowY, slotSize)
        drawItemSlot(context, font, target.offhandItem, heldRightX, heldRowY, slotSize)

        // Armor grid (2x2)
        val helmet = target.getItemBySlot(EquipmentSlot.HEAD)
        val chestplate = target.getItemBySlot(EquipmentSlot.CHEST)
        drawItemSlot(context, font, helmet, armorLeftX, armorTopY, slotSize)
        drawItemSlot(context, font, chestplate, armorRightX, armorTopY, slotSize)

        val leggings = target.getItemBySlot(EquipmentSlot.LEGS)
        val boots = target.getItemBySlot(EquipmentSlot.FEET)
        drawItemSlot(context, font, leggings, armorLeftX, armorBottomY, slotSize)
        drawItemSlot(context, font, boots, armorRightX, armorBottomY, slotSize)

        val nameX = rightX
        val nameMaxWidth = rightWidth.coerceAtLeast(20)
        val displayName = fitText(font, target.name.string, nameMaxWidth)
        context.drawString(
            font,
            vText(displayName),
            nameX,
            nameY,
            Style.nameText,
            false,
        )
    }

    private fun drawSliderDock(context: GuiGraphics, bounds: TargetHudBounds) {
        val dockWidth = (bounds.width - 20).coerceAtLeast(96)
        val dockHeight = 27
        val dockX = bounds.x + (bounds.width - dockWidth) / 2
        val dockY = bounds.y + bounds.height - 3

        SdfPanelRenderer.draw(
            context = context,
            x = dockX,
            y = dockY,
            width = dockWidth,
            height = dockHeight,
            style = Style.sliderDock(),
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

        drawPreviewSlider(context, trackX, yawTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, yawSlider, activeSlider == ActiveSlider.YAW)
        drawPreviewSlider(context, trackX, pitchTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, pitchSlider, activeSlider == ActiveSlider.PITCH)
        drawPreviewSlider(context, trackX, zoomTrackY, trackWidth, TargetHudLayout.sliderTrackHeight, zoomSlider, activeSlider == ActiveSlider.ZOOM)
    }

    private fun drawPreviewSlider(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: Float,
        active: Boolean,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = width,
            height = height,
            style = Style.sliderTrack(active),
        )

        val filledWidth = (width * value.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1)
        SdfPanelRenderer.draw(
            context = context,
            x = x + 1,
            y = y + 1,
            width = (filledWidth - 2).coerceAtLeast(1),
            height = (height - 2).coerceAtLeast(1),
            style = Style.sliderFill(),
        )

        val knobSize = 8
        val knobCenterX = (x + (width * value.coerceIn(0f, 1f))).roundToInt().coerceIn(x + 4, x + width - 4)
        val knobX = knobCenterX - knobSize / 2
        val knobY = y + (height - knobSize) / 2
        SdfPanelRenderer.draw(
            context = context,
            x = knobX,
            y = knobY,
            width = knobSize,
            height = knobSize,
            style = Style.sliderKnob(active),
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

        // Orbit angles:
        // slider1 => full horizontal orbit
        // slider2 => vertical orbit elevation
        val orbitYawDegrees = (yawSlider.coerceIn(0f, 1f) * 360f) - 180f
        val orbitPitchDegrees = ((pitchSlider.coerceIn(0f, 1f) * 2f) - 1f) * 46f
        val orbitPitchRad = Math.toRadians(orbitPitchDegrees.toDouble()).toFloat()
        val pitchQuaternion = Quaternionf().rotateX(orbitPitchRad)
        val modelRotation = Quaternionf()
            .rotateZ(Math.PI.toFloat())
            .mul(Quaternionf(pitchQuaternion))

        try {
            val renderState = Minecraft.getInstance().entityRenderDispatcher.extractEntity(target, 1f)

            if (renderState is LivingEntityRenderState) {
                // Hard-detach preview orientation from in-world player look direction.
                // Yaw comes only from slider 1 (full 360° range).
                val previewBodyYaw = 180f + orbitYawDegrees
                renderState.bodyRot = previewBodyYaw
                // Keep head aligned with body in preview so it doesn't drift/twist
                // independently during yaw orbit.
                renderState.yRot = 0f
                renderState.xRot = 0f

                if (renderState is AvatarRenderState) {
                    renderState.shouldApplyFlyingYRot = false
                    renderState.flyingYRot = 0f
                }

                val normalizedScale = if (renderState.scale == 0f) 1f else renderState.scale
                renderState.boundingBoxWidth /= normalizedScale
                renderState.boundingBoxHeight /= normalizedScale
                renderState.scale = 1f
            }

            // Fixed-center preview: entity stays anchored with a stable center pivot.
            val pivotCenterY = renderState.boundingBoxHeight * 0.5f
            val translation = Vector3f(0f, pivotCenterY, 0f)
            // Vanilla-like inventory preview path: pitch is applied through both
            // modelRotation and cameraRotation so vertical orbit is visible in runtime.
            val cameraRotation = Quaternionf(pitchQuaternion)

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
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = size,
            height = size,
            style = Style.slot(!stack.isEmpty),
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

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("target_hud:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

    private fun toLocal(global: Int, origin: Int, scale: Float): Int {
        return ((global - origin) / scale).toInt()
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
