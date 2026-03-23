package com.visualproject.client.hud.watermark

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.texture.NonDumpableDynamicTexture
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.vText
import com.visualproject.client.vBrandText
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class WatermarkHudRenderer(
    private val musicProvider: WatermarkMusicProvider = SpotifySoundCloudMusicProvider(),
) {
    companion object {
        private const val watermarkModuleId = "watermark"
        private val watermarkEyeTexture = Identifier.fromNamespaceAndPath("visualclient", "textures/gui/watermark_eye.png")
        private const val watermarkEyeTextureWidth = 512
        private const val watermarkEyeTextureHeight = 312
    }

    private data class ArtworkResolution(
        val texture: Identifier?,
        val reason: String,
        val textureWidth: Int = 0,
        val textureHeight: Int = 0,
    )

    private data class ArtworkCacheKey(
        val path: String,
        val sizeBytes: Long,
        val lastModifiedEpochMillis: Long,
    ) {
        val textureSuffix: String
            get() = "${path.hashCode().toUInt().toString(16)}_${sizeBytes.toULong().toString(16)}_${lastModifiedEpochMillis.toULong().toString(16)}"
    }

    private enum class ControlButton {
        PREVIOUS,
        PLAY_PAUSE,
        NEXT,
    }

    private data class ControlHitboxes(
        val previous: HudBounds,
        val playPause: HudBounds,
        val next: HudBounds,
    ) {
        fun resolveHovered(mouseX: Int, mouseY: Int): ControlButton? {
            return when {
                previous.contains(mouseX, mouseY) -> ControlButton.PREVIOUS
                playPause.contains(mouseX, mouseY) -> ControlButton.PLAY_PAUSE
                next.contains(mouseX, mouseY) -> ControlButton.NEXT
                else -> null
            }
        }
    }

    private val stateCalculator = WatermarkStateCalculator(musicProvider)
    private val animation = WatermarkAnimationController()
    private val logger = LoggerFactory.getLogger("visualclient-watermark-hud")

    private var wasLeftMousePressed = false
    private var lastRenderedControls: ControlHitboxes? = null
    private var lastRenderedExpansion: Float = 0f
    private var lastRenderedScale: Float = 1f
    private var lastRenderedActualBounds: HudBounds? = null
    private var smoothedTrackKey: String? = null
    private var smoothedPositionSeconds = 0f
    private var smoothedDurationSeconds = 0f
    private var lastProviderSnapshot: WatermarkTrackInfo? = null
    private var currentArtworkPath: String? = null
    private var lastArtworkResolveReason: String = "uninitialized"
    private var lastArtworkResolveState: String? = null
    private var lastSuccessfulArtworkTexture: Identifier? = null
    private var lastSuccessfulArtworkPath: String? = null
    private var lastSuccessfulArtworkWidth: Int = 0
    private var lastSuccessfulArtworkHeight: Int = 0
    private var lastExpandedFillState: String? = null
    private var displayedArtworkTexture: Identifier? = null
    private var displayedArtworkPath: String? = null
    private var previousArtworkTexture: Identifier? = null
    private var artworkSwitchStartedAtMs: Long = 0L
    private val artworkSwitchDurationMs = 165L
    private val lastArtworkDrawStateBySlot = HashMap<String, String>()

    private val artworkTextureCache = HashMap<ArtworkCacheKey, Identifier>()
    private val artworkTextureSizes = HashMap<ArtworkCacheKey, Pair<Int, Int>>()
    private val artworkSourceSizeByPath = HashMap<String, Pair<Int, Int>>()
    private val artworkSizeByTexture = HashMap<Identifier, Pair<Int, Int>>()
    private val artworkLoadFailures = HashSet<String>()
    private val artworkLoggedExists = HashSet<String>()
    private val artworkLoggedSignatures = HashSet<ArtworkCacheKey>()
    private val artworkRetryAfterMillis = HashMap<String, Long>()
    private val debugArtworkPipeline = System.getProperty("visualclient.media.debug", "true").toBoolean()
    private val debugControls = System.getProperty("visualclient.media.debug", "true").toBoolean()
    private val artworkTextureLogicalSize = 96

    fun render(
        context: GuiGraphics,
        @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker,
        client: Minecraft,
    ) {
        if (client.player == null || client.options.hideGui) return

        val musicScanEnabled = ModuleStateStore.isSettingEnabled("${watermarkModuleId}:music_scan")
        musicProvider.setScanningEnabled(musicScanEnabled)

        val font = client.font
        val mouseX = client.mouseHandler.getScaledXPos(client.window).toInt()
        val mouseY = client.mouseHandler.getScaledYPos(client.window).toInt()
        val scale = hudScale()

        val currentLocalBounds = computeLocalBounds(animation.currentExpansion())
        val currentActualBounds = computeActualBounds(context, animation.currentExpansion(), scale)
        val currentLocalMouseX = toLocalMouse(mouseX, currentActualBounds.left, scale)
        val currentLocalMouseY = toLocalMouse(mouseY, currentActualBounds.top, scale)
        val state = stateCalculator.resolve(client, currentLocalBounds, currentLocalMouseX, currentLocalMouseY)

        val marqueeWidth = calculateCompactMusicTextWidth(currentLocalBounds.width)
        val trackTitleWidth = state.track?.let { font.width(vText(it.title)) } ?: 0
        val marqueeCycle = (trackTitleWidth + WatermarkHudTheme.marqueeGapPx).toFloat()
        val marqueeActive = state.mode == WatermarkMode.MUSIC && trackTitleWidth > marqueeWidth

        val snapshot = animation.tick(
            targetExpanded = state.targetExpanded,
            marqueeActive = marqueeActive,
            marqueeCyclePx = marqueeCycle,
        )

        val localBounds = computeLocalBounds(snapshot.expansion)
        val actualBounds = computeActualBounds(context, snapshot.expansion, scale)
        val localMouseX = toLocalMouse(mouseX, actualBounds.left, scale)
        val localMouseY = toLocalMouse(mouseY, actualBounds.top, scale)
        val renderTrack = state.track?.let { prepareRenderTrack(client, it, snapshot.deltaSeconds) }
        lastRenderedActualBounds = actualBounds
        lastRenderedScale = scale

        context.pose().pushMatrix()
        context.pose().translate(actualBounds.left.toFloat(), actualBounds.top.toFloat())
        context.pose().scale(scale, scale)

        drawMainShell(context, localBounds, snapshot.expansion)

        val compactAlpha = (1f - (snapshot.expansion * 1.15f)).coerceIn(0f, 1f)
        if (state.mode == WatermarkMode.DEFAULT || renderTrack == null) {
            lastRenderedControls = null
            lastRenderedExpansion = 0f
            drawDefaultCompact(context, font, localBounds, client, compactAlpha)
            context.pose().popMatrix()
            consumeClickState(client)
            return
        }

        drawMusicCompact(
            context = context,
            font = font,
            bounds = localBounds,
            track = renderTrack,
            alpha = compactAlpha,
        )

        val controls = if (snapshot.expansion > 0.01f && state.canExpand) {
            drawExpandedMusic(context, font, localBounds, renderTrack, snapshot.expansion, localMouseX, localMouseY)
        } else {
            null
        }
        context.pose().popMatrix()
        lastRenderedControls = controls
        lastRenderedExpansion = snapshot.expansion

        handleControlClicks(client, controls, localMouseX, localMouseY, snapshot.expansion)
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (mouseEvent.button() != 0) return consumed

        val controls = lastRenderedControls
        val actualBounds = lastRenderedActualBounds
        val scale = lastRenderedScale
        val expansion = lastRenderedExpansion
        val mouseX = mouseEvent.x().toInt()
        val mouseY = mouseEvent.y().toInt()

        if (debugControls) {
            logger.info(
                "watermark-control: screen-event click screen='{}' mouse=({}, {}) consumed={} controlsPresent={} expansion={}",
                screen.javaClass.simpleName,
                mouseX,
                mouseY,
                consumed,
                controls != null,
                "%.3f".format(expansion),
            )
        }

        if (controls == null || actualBounds == null || expansion <= 0.01f) {
            return consumed
        }

        val localMouseX = toLocalMouse(mouseX, actualBounds.left, scale)
        val localMouseY = toLocalMouse(mouseY, actualBounds.top, scale)
        val hoveredControl = controls.resolveHovered(localMouseX, localMouseY) ?: return consumed
        val handled = dispatchControlClick(
            client = client,
            hoveredControl = hoveredControl,
            mouseX = localMouseX,
            mouseY = localMouseY,
            expansion = expansion,
            inputRoute = "screen-event",
        )

        return consumed || handled
    }

    private fun drawMainShell(context: GuiGraphics, bounds: HudBounds, expansion: Float) {
        SdfPanelRenderer.draw(
            context = context,
            x = bounds.left,
            y = bounds.top,
            width = bounds.width,
            height = bounds.height,
            style = watermarkShellStyle(expansion),
        )
    }

    private fun drawDefaultCompact(
        context: GuiGraphics,
        font: Font,
        bounds: HudBounds,
        client: Minecraft,
        alpha: Float,
    ) {
        val iconSize = 14
        val iconX = bounds.left + WatermarkHudTheme.paddingX
        val iconY = bounds.top + (WatermarkHudTheme.compactHeight - iconSize) / 2
        val baselineY = bounds.top + ((WatermarkHudTheme.compactHeight - font.lineHeight) / 2)

        drawEyeIcon(context, iconX, iconY, iconSize, alpha)

        val titleX = iconX + iconSize + 8
        val ping = resolvePing(client)
        val fps = client.fps
        val infoText = "${ping}ms  ${fps}fps"
        val infoWidth = font.width(vText(infoText))
        val infoX = bounds.left + bounds.width - WatermarkHudTheme.paddingX - infoWidth
        val titleMaxWidth = (infoX - 8 - titleX).coerceAtLeast(24)
        val title = clipStyledText("Hypnosia Visual", titleMaxWidth, font)
        context.drawString(
            font,
            title,
            titleX,
            baselineY,
            withAlpha(watermarkTextPrimaryColor(), alpha),
            false,
        )

        context.drawString(
            font,
            vText(infoText),
            infoX,
            baselineY,
            withAlpha(watermarkTextSecondaryColor(), alpha),
            false,
        )
    }

    private fun drawMusicCompact(
        context: GuiGraphics,
        font: Font,
        bounds: HudBounds,
        track: WatermarkTrackInfo,
        alpha: Float,
    ) {
        val iconSize = WatermarkHudTheme.compactArtworkSize
        val iconX = bounds.left + WatermarkHudTheme.paddingX
        val iconY = bounds.top + (WatermarkHudTheme.compactHeight - iconSize) / 2
        val baselineY = bounds.top + ((WatermarkHudTheme.compactHeight - font.lineHeight) / 2)

        // Compact mode uses the same artwork pipeline as expanded mode.
        drawArtwork(context, font, track, iconX, iconY, iconSize, alpha, slot = "compact")

        val rightMarker = if (track.playbackState == WatermarkPlaybackState.PLAYING) "PLAY" else "PAUSE"
        val rightMarkerWidth = font.width(vText(rightMarker))
        val rightMarkerX = bounds.left + bounds.width - WatermarkHudTheme.paddingX - rightMarkerWidth

        val textX = iconX + iconSize + 8
        val textWidth = (rightMarkerX - 8 - textX).coerceAtLeast(28)
        val textY = baselineY
        val clippedTitle = clipStyledText(track.title, textWidth, font)
        context.drawString(font, clippedTitle, textX, textY, withAlpha(watermarkTextPrimaryColor(), alpha), false)

        context.drawString(font, vText(rightMarker), rightMarkerX, textY, withAlpha(watermarkTextMutedColor(), alpha), false)
    }

    private fun drawEyeIcon(
        context: GuiGraphics,
        x: Int,
        y: Int,
        size: Int,
        alpha: Float,
    ) {
        val drawWidth = size
        val drawHeight = ((size.toFloat() * watermarkEyeTextureHeight) / watermarkEyeTextureWidth).roundToInt().coerceAtLeast(1)
        val drawY = y + ((size - drawHeight) / 2)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            watermarkEyeTexture,
            x,
            drawY,
            0f,
            0f,
            drawWidth,
            drawHeight,
            watermarkEyeTextureWidth,
            watermarkEyeTextureHeight,
            watermarkEyeTextureWidth,
            watermarkEyeTextureHeight,
            withAlpha(VisualThemeSettings.neonBorder(), alpha),
        )
    }

    private fun drawExpandedMusic(
        context: GuiGraphics,
        font: Font,
        bounds: HudBounds,
        track: WatermarkTrackInfo,
        expansion: Float,
        mouseX: Int,
        mouseY: Int,
    ): ControlHitboxes {
        val alpha = expansion.coerceIn(0f, 1f)
        val contentLeft = bounds.left + WatermarkHudTheme.expandedPadding
        val contentTop = bounds.top + WatermarkHudTheme.expandedPadding - 1
        val contentRight = bounds.left + bounds.width - WatermarkHudTheme.expandedPadding
        val contentBottom = bounds.top + bounds.height - WatermarkHudTheme.expandedPadding + 1

        // Keep expanded state as one cohesive rounded body (no rectangular "forehead" strip).
        val innerRadius = (WatermarkHudTheme.radius - 1).coerceAtLeast(10)
        SdfPanelRenderer.draw(
            context = context,
            x = bounds.left + 1,
            y = bounds.top + 1,
            width = bounds.width - 2,
            height = bounds.height - 2,
            style = watermarkExpandedInnerStyle(alpha, innerRadius.toFloat()),
        )
        if (debugArtworkPipeline) {
            val state = "${bounds.left},${bounds.top},${bounds.width},${bounds.height}"
            if (lastExpandedFillState != state) {
                lastExpandedFillState = state
                logger.info(
                    "watermark-panel: expanded-fill bounds=({}, {}) {}x{} alpha={} mode=rounded-body",
                    bounds.left + 1,
                    bounds.top + 1,
                    bounds.width - 2,
                    bounds.height - 2,
                    "%.2f".format(alpha),
                )
            }
        }

        val artworkSize = WatermarkHudTheme.expandedArtworkSize
        val artworkX = contentLeft
        val artworkY = (contentTop + ((contentBottom - contentTop - artworkSize) / 2)).coerceAtLeast(contentTop)
        val artworkBottom = artworkY + artworkSize
        drawArtwork(context, font, track, artworkX, artworkY, artworkSize, alpha, slot = "expanded")

        val textLeft = artworkX + artworkSize + 9
        val controlsWidth = (WatermarkHudTheme.expandedControlSize * 3) + (WatermarkHudTheme.expandedControlGap * 2)
        val controlsX = contentRight - controlsWidth
        val controlsY = (artworkBottom - WatermarkHudTheme.expandedControlSize).coerceIn(
            contentTop + 1,
            contentBottom - WatermarkHudTheme.expandedControlSize,
        )
        val prevBounds = HudBounds(
            controlsX,
            controlsY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )
        val playBounds = HudBounds(
            prevBounds.left + WatermarkHudTheme.expandedControlSize + WatermarkHudTheme.expandedControlGap,
            controlsY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )
        val nextBounds = HudBounds(
            playBounds.left + WatermarkHudTheme.expandedControlSize + WatermarkHudTheme.expandedControlGap,
            controlsY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )

        val textRight = (controlsX - 8).coerceAtLeast(textLeft + 32)
        val titleMaxWidth = (textRight - textLeft).coerceAtLeast(40)
        val clippedTitle = clipStyledText(track.title, titleMaxWidth, font)
        val subtitle = track.artist ?: "Unknown Artist"
        val clippedSubtitle = clipStyledText(subtitle, titleMaxWidth, font)
        val titleY = (artworkY + 1).coerceAtLeast(contentTop + 1)
        val artistY = (titleY + font.lineHeight + 1).coerceAtMost(controlsY - 14)
        context.drawString(font, clippedTitle, textLeft, titleY, withAlpha(watermarkTextPrimaryColor(), alpha), false)
        context.drawString(font, clippedSubtitle, textLeft, artistY, withAlpha(watermarkTextSecondaryColor(), alpha), false)

        val progressX = textLeft
        val timeText = "${formatTime(track.positionSeconds)} / ${formatTime(track.durationSeconds)}"
        val timeWidth = font.width(vText(timeText))
        val progressY = (artworkBottom - 5).coerceIn(
            artistY + font.lineHeight + 2,
            contentBottom - 6,
        )
        val timeX = (controlsX - 6 - timeWidth).coerceAtLeast(progressX + 24)
        val progressWidth = (timeX - progressX - 7).coerceAtLeast(30)
        val progressHeight = 4

        drawRoundedPanel(
            context,
            progressX,
            progressY,
            progressWidth,
            progressHeight,
            withAlpha(watermarkProgressTrackColor(), alpha),
            withAlpha(watermarkProgressBorderColor(), alpha),
            2,
        )

        val fillWidth = (progressWidth * track.progressNormalized).roundToInt()
            .coerceIn(0, progressWidth)
        if (fillWidth > 2) {
            fillRoundedRect(
                context,
                progressX + 1,
                progressY + 1,
                fillWidth - 2,
                (progressHeight - 2).coerceAtLeast(1),
                1,
                withAlpha(watermarkProgressFillColor(), alpha),
            )
        }

        context.drawString(
            font,
            vText(timeText),
            timeX,
            controlsY + ((WatermarkHudTheme.expandedControlSize - font.lineHeight) / 2),
            withAlpha(watermarkTextMutedColor(), alpha),
            false,
        )

        drawControlButton(
            context, font, prevBounds, "<", alpha,
            hovered = prevBounds.contains(mouseX, mouseY),
        )
        drawControlButton(
            context, font, playBounds,
            if (track.playbackState == WatermarkPlaybackState.PLAYING) "||" else ">",
            alpha,
            hovered = playBounds.contains(mouseX, mouseY),
        )
        drawControlButton(
            context, font, nextBounds, ">", alpha,
            hovered = nextBounds.contains(mouseX, mouseY),
        )

        return ControlHitboxes(prevBounds, playBounds, nextBounds)
    }

    private fun drawArtwork(
        context: GuiGraphics,
        font: Font,
        track: WatermarkTrackInfo,
        x: Int,
        y: Int,
        size: Int,
        alpha: Float,
        slot: String,
    ) {
        val backgroundColor = withAlpha(0xE40A0E18.toInt(), alpha)
        val borderColor = withAlpha(0x72384766, alpha)
        context.fill(x, y, x + size, y + size, backgroundColor)

        val texture = track.artworkTexture ?: displayedArtworkTexture
        if (texture != null) {
            val inset = if (slot == "compact") 2 else 2
            val drawLeft = x + inset
            val drawTop = y + inset
            val drawSize = (size - (inset * 2)).coerceAtLeast(1)
            val (sourceWidth, sourceHeight) = resolveTextureSize(texture, track.artworkPath)

            val now = System.currentTimeMillis()
            val previous = previousArtworkTexture
            val transitionProgress = if (previous != null && artworkSwitchStartedAtMs > 0L) {
                ((now - artworkSwitchStartedAtMs).toFloat() / artworkSwitchDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }

            if (previous != null && transitionProgress < 1f) {
                val (previousWidth, previousHeight) = resolveTextureSize(previous, null)
                blitArtworkTexture(
                    context = context,
                    texture = previous,
                    drawLeft = drawLeft,
                    drawTop = drawTop,
                    drawSize = drawSize,
                    sourceWidth = previousWidth,
                    sourceHeight = previousHeight,
                    alpha = (alpha * (1f - transitionProgress)).coerceIn(0f, 1f),
                )
                blitArtworkTexture(
                    context = context,
                    texture = texture,
                    drawLeft = drawLeft,
                    drawTop = drawTop,
                    drawSize = drawSize,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    alpha = (alpha * transitionProgress).coerceIn(0f, 1f),
                )
            } else {
                if (previous != null && transitionProgress >= 1f) {
                    previousArtworkTexture = null
                    artworkSwitchStartedAtMs = 0L
                    if (debugArtworkPipeline) {
                        logger.info("watermark-artwork: transition complete texture='{}'", texture)
                    }
                }
                blitArtworkTexture(
                    context = context,
                    texture = texture,
                    drawLeft = drawLeft,
                    drawTop = drawTop,
                    drawSize = drawSize,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    alpha = alpha.coerceIn(0f, 1f),
                )
            }
            drawSquareBorder(context, x, y, size, borderColor)
            if (debugArtworkPipeline) {
                val transitionState = if (previousArtworkTexture != null) "crossfade" else "steady"
                logDrawStateIfChanged(
                    slot = slot,
                    state = "texture|path=${track.artworkPath ?: "<null>"}|reason=$lastArtworkResolveReason|mode=$transitionState|method=GUI_TEXTURED|order=bg>tex>border|overlayAfterTexture=border-only|src=${sourceWidth}x${sourceHeight}",
                    drawLeft = drawLeft,
                    drawTop = drawTop,
                    drawSize = drawSize,
                )
            }
            return
        }

        drawSquareBorder(context, x, y, size, borderColor)

        if (debugArtworkPipeline) {
            logDrawStateIfChanged(
                slot = slot,
                state = "placeholder|path=${track.artworkPath ?: "<null>"}|reason=$lastArtworkResolveReason|order=bg>placeholder>border",
                drawLeft = x + 1,
                drawTop = y + 1,
                drawSize = (size - 2).coerceAtLeast(1),
            )
        }

        context.drawString(
            font,
            vText("M"),
            x + (size / 2) - 3,
            y + (size / 2) - 4,
            withAlpha(watermarkAccentColor(), alpha),
            false,
        )
    }

    private fun drawSquareBorder(
        context: GuiGraphics,
        x: Int,
        y: Int,
        size: Int,
        color: Int,
    ) {
        if (size <= 1) return
        context.fill(x, y, x + size, y + 1, color)
        context.fill(x, y + size - 1, x + size, y + size, color)
        context.fill(x, y, x + 1, y + size, color)
        context.fill(x + size - 1, y, x + size, y + size, color)
    }

    private fun logDrawStateIfChanged(
        slot: String,
        state: String,
        drawLeft: Int,
        drawTop: Int,
        drawSize: Int,
    ) {
        val previous = lastArtworkDrawStateBySlot[slot]
        if (previous == state) return
        lastArtworkDrawStateBySlot[slot] = state
        logger.info(
            "watermark-artwork: draw-branch slot='{}' state='{}' rect=({}, {}) size={}x{}",
            slot,
            state,
            drawLeft,
            drawTop,
            drawSize,
            drawSize,
        )
    }

    private fun blitArtworkTexture(
        context: GuiGraphics,
        texture: Identifier,
        drawLeft: Int,
        drawTop: Int,
        drawSize: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        alpha: Float,
    ) {
        if (drawSize <= 0 || alpha <= 0f) return
        val tint = withAlpha(0xFFFFFFFF.toInt(), alpha.coerceIn(0f, 1f))
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            drawLeft,
            drawTop,
            0f,
            0f,
            drawSize,
            drawSize,
            sourceWidth.coerceAtLeast(1),
            sourceHeight.coerceAtLeast(1),
            sourceWidth.coerceAtLeast(1),
            sourceHeight.coerceAtLeast(1),
            tint,
        )
    }

    private fun drawControlButton(
        context: GuiGraphics,
        font: Font,
        bounds: HudBounds,
        label: String,
        alpha: Float,
        hovered: Boolean,
    ) {
        val fill = if (hovered) {
            if (VisualThemeSettings.isLightPreset()) 0xCFE6EEF8.toInt() else 0xAA1A2237.toInt()
        } else {
            if (VisualThemeSettings.isLightPreset()) 0xA9EDF3FA.toInt() else 0x7A121A2D
        }
        val border = if (hovered) watermarkAccentColor() else if (VisualThemeSettings.isLightPreset()) 0x8DBFD1E4.toInt() else 0x4F3A4561

        drawRoundedPanel(
            context,
            bounds.left,
            bounds.top,
            bounds.width,
            bounds.height,
            withAlpha(fill, alpha),
            withAlpha(border, alpha),
            5,
        )

        val textWidth = font.width(vText(label))
        context.drawString(
            font,
            vText(label),
            bounds.left + ((bounds.width - textWidth) / 2),
            bounds.top + ((bounds.height - font.lineHeight) / 2),
            withAlpha(watermarkTextPrimaryColor(), alpha),
            false,
        )
    }

    private fun watermarkTextPrimaryColor(): Int = if (VisualThemeSettings.isLightPreset()) 0xFF111111.toInt() else WatermarkHudTheme.textPrimary

    private fun watermarkTextSecondaryColor(): Int = if (VisualThemeSettings.isLightPreset()) 0xFF232323.toInt() else WatermarkHudTheme.textSecondary

    private fun watermarkTextMutedColor(): Int = if (VisualThemeSettings.isLightPreset()) 0xFF363636.toInt() else WatermarkHudTheme.textMuted

    private fun watermarkAccentColor(): Int = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.accentStrong() else WatermarkHudTheme.accent

    private fun watermarkProgressTrackColor(): Int = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudTrackFill() else WatermarkHudTheme.progressTrack

    private fun watermarkProgressBorderColor(): Int = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudTrackBorder() else WatermarkHudTheme.progressBorder

    private fun watermarkProgressFillColor(): Int {
        return if (VisualThemeSettings.isLightPreset()) {
            blendColor(0xFFF4F8FE.toInt(), WatermarkHudTheme.progressFill, 0.72f)
        } else {
            WatermarkHudTheme.progressFill
        }
    }

    private fun watermarkShellStyle(expansion: Float): SdfPanelStyle {
        val accentSync = ModuleStateStore.isSettingEnabled("${watermarkModuleId}:accent_sync")
        val accentColor = if (accentSync) VisualThemeSettings.accentStrong() else watermarkAccentColor()
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else watermarkAccentColor()

        return SdfPanelStyle(
            baseColor = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudShellFill() else WatermarkHudTheme.panelFill,
            borderColor = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudShellBorder() else WatermarkHudTheme.panelBorder,
            borderWidthPx = 1.25f,
            radiusPx = WatermarkHudTheme.radius + (expansion * 2f),
            innerGlow = SdfGlowStyle(
                color = 0xFFFFFFFF.toInt(),
                radiusPx = 13f + (expansion * 4f),
                strength = 0.12f,
                opacity = 0.10f,
            ),
            outerGlow = SdfGlowStyle(
                color = if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.themedAccentGlowBase(accentColor) else accentColor,
                radiusPx = 18f + (expansion * 8f),
                strength = if (VisualThemeSettings.isLightPreset()) 0.12f else 0.18f,
                opacity = (if (VisualThemeSettings.isLightPreset()) 0.07f else 0.10f) + (expansion * if (VisualThemeSettings.isLightPreset()) 0.03f else 0.05f),
            ),
            shade = SdfShadeStyle(
                topColor = if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x12FFFFFF,
                bottomColor = if (VisualThemeSettings.isLightPreset()) 0x0ED0DBEA else 0x28000000,
            ),
            neonBorder = SdfNeonBorderStyle(
                color = VisualThemeSettings.withAlpha(neonColor, if (VisualThemeSettings.isLightPreset()) 0x84 else 0xC8),
                widthPx = 1.05f,
                softnessPx = 5.5f + (expansion * 1.5f),
                strength = if (VisualThemeSettings.isLightPreset()) 0.42f else 0.76f,
            ),
        )
    }

    private fun watermarkExpandedInnerStyle(alpha: Float, radiusPx: Float): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = withAlpha(if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudInnerFill() else WatermarkHudTheme.expandedInnerFill, alpha),
            borderColor = withAlpha(if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.hudInnerBorder() else WatermarkHudTheme.expandedInnerBorder, alpha),
            borderWidthPx = 1f,
            radiusPx = radiusPx,
            innerGlow = SdfGlowStyle(
                color = 0xFFFFFFFF.toInt(),
                radiusPx = 10f,
                strength = 0.10f,
                opacity = 0.08f * alpha,
            ),
            outerGlow = SdfGlowStyle(
                color = if (VisualThemeSettings.isLightPreset()) 0xFFE4ECF6.toInt() else 0xFF000000.toInt(),
                radiusPx = if (VisualThemeSettings.isLightPreset()) 12f else 0f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.06f else 0f,
                opacity = if (VisualThemeSettings.isLightPreset()) 0.05f * alpha else 0f,
            ),
            shade = SdfShadeStyle(
                topColor = withAlpha(if (VisualThemeSettings.isLightPreset()) 0x08FFFFFF else 0x14FFFFFF, alpha),
                bottomColor = withAlpha(if (VisualThemeSettings.isLightPreset()) 0x0CD0DBEA else 0x14000000, alpha),
            ),
            neonBorder = SdfNeonBorderStyle(
                color = VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), if (VisualThemeSettings.isLightPreset()) 0x30 else 0x52),
                widthPx = 0.9f,
                softnessPx = 4f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.18f else 0.30f,
            ),
        )
    }

    private fun handleControlClicks(
        client: Minecraft,
        controls: ControlHitboxes?,
        mouseX: Int,
        mouseY: Int,
        expansion: Float,
    ) {
        if (client.screen != null) {
            // Screen is responsible for mouse ownership. Use screen event routing in this case.
            consumeClickState(client)
            return
        }

        val leftPressed = client.mouseHandler.isLeftPressed
        val isNewClick = leftPressed && !wasLeftMousePressed
        wasLeftMousePressed = leftPressed

        if (!isNewClick) return

        if (debugControls) {
            logger.info(
                "watermark-control: click-detected mouse=({}, {}) controlsPresent={} expansion={}",
                mouseX,
                mouseY,
                controls != null,
                "%.3f".format(expansion),
            )
        }

        if (controls == null) {
            if (debugControls) {
                logger.info("watermark-control: click ignored, expanded control hitboxes are unavailable")
            }
            return
        }

        if (debugControls) {
            logger.info(
                "watermark-control: hitboxes prev=[{},{} {}x{}] play=[{},{} {}x{}] next=[{},{} {}x{}]",
                controls.previous.left,
                controls.previous.top,
                controls.previous.width,
                controls.previous.height,
                controls.playPause.left,
                controls.playPause.top,
                controls.playPause.width,
                controls.playPause.height,
                controls.next.left,
                controls.next.top,
                controls.next.width,
                controls.next.height,
            )
        }

        val hoveredControl = controls.resolveHovered(mouseX, mouseY)
        if (hoveredControl == null) {
            if (debugControls) {
                logger.info("watermark-control: click miss (no button hit)")
            }
            return
        }
        dispatchControlClick(
            client = client,
            hoveredControl = hoveredControl,
            mouseX = mouseX,
            mouseY = mouseY,
            expansion = expansion,
            inputRoute = "hud-polling",
        )
    }

    private fun dispatchControlClick(
        client: Minecraft,
        hoveredControl: ControlButton,
        mouseX: Int,
        mouseY: Int,
        expansion: Float,
        inputRoute: String,
    ): Boolean {
        val playbackController = musicProvider.playbackController(client)
        if (playbackController == null) {
            logger.warn(
                "watermark-control: route={} clicked '{}' but playback controller is unavailable",
                inputRoute,
                hoveredControl.name,
            )
            return false
        }

        logger.info(
            "watermark-control: route={} clicked '{}' mouse=({}, {}) expansion={}",
            inputRoute,
            hoveredControl.name,
            mouseX,
            mouseY,
            "%.3f".format(expansion),
        )

        when (hoveredControl) {
            ControlButton.PREVIOUS -> playbackController.previous(client)
            ControlButton.PLAY_PAUSE -> playbackController.togglePlayPause(client)
            ControlButton.NEXT -> playbackController.next(client)
        }

        if (debugControls) {
            logger.info(
                "watermark-control: route={} dispatch complete button='{}'",
                inputRoute,
                hoveredControl.name,
            )
        }

        return true
    }

    private fun consumeClickState(client: Minecraft) {
        wasLeftMousePressed = client.mouseHandler.isLeftPressed
    }

    private fun prepareRenderTrack(client: Minecraft, track: WatermarkTrackInfo, deltaSeconds: Float): WatermarkTrackInfo {
        val key = "${track.source}|${track.title}|${track.artist.orEmpty()}"
        val providerSnapshotChanged = lastProviderSnapshot !== track

        if (smoothedTrackKey != key) {
            smoothedTrackKey = key
            smoothedPositionSeconds = track.positionSeconds.coerceAtLeast(0f)
            smoothedDurationSeconds = track.durationSeconds.coerceAtLeast(0f)
            lastProviderSnapshot = track
        } else if (providerSnapshotChanged) {
            val targetPosition = track.positionSeconds.coerceAtLeast(0f)
            val diff = targetPosition - smoothedPositionSeconds
            smoothedPositionSeconds = when {
                abs(diff) >= 7f -> targetPosition
                else -> smoothedPositionSeconds + (diff * 0.34f)
            }

            val targetDuration = track.durationSeconds.coerceAtLeast(0f)
            smoothedDurationSeconds = when {
                targetDuration <= 0f -> smoothedDurationSeconds
                smoothedDurationSeconds <= 0f -> targetDuration
                else -> smoothedDurationSeconds + ((targetDuration - smoothedDurationSeconds) * 0.30f)
            }
            lastProviderSnapshot = track
        }

        if (track.playbackState == WatermarkPlaybackState.PLAYING) {
            smoothedPositionSeconds += deltaSeconds.coerceIn(0f, 0.05f)
        }

        val clampDuration = maxOf(smoothedDurationSeconds, track.durationSeconds)
        smoothedPositionSeconds = if (clampDuration > 0f) {
            smoothedPositionSeconds.coerceIn(0f, clampDuration)
        } else {
            smoothedPositionSeconds.coerceAtLeast(0f)
        }

        val artworkResolution = resolveArtworkTexture(client, track.artworkPath)
        updateDisplayedArtwork(track.artworkPath, artworkResolution.texture)
        lastArtworkResolveReason = artworkResolution.reason

        return track.copy(
            positionSeconds = smoothedPositionSeconds,
            durationSeconds = smoothedDurationSeconds.coerceAtLeast(track.durationSeconds.coerceAtLeast(0f)),
            artworkTexture = displayedArtworkTexture ?: artworkResolution.texture,
        )
    }

    private fun updateDisplayedArtwork(artworkPath: String?, resolvedTexture: Identifier?) {
        if (resolvedTexture == null) return
        if (displayedArtworkTexture == null) {
            displayedArtworkTexture = resolvedTexture
            displayedArtworkPath = artworkPath
            previousArtworkTexture = null
            artworkSwitchStartedAtMs = 0L
            if (debugArtworkPipeline) {
                logger.info("watermark-artwork: display initialized path='{}' texture='{}'", artworkPath ?: "<null>", resolvedTexture)
            }
            return
        }

        if (displayedArtworkTexture == resolvedTexture) {
            displayedArtworkPath = artworkPath
            return
        }

        previousArtworkTexture = displayedArtworkTexture
        displayedArtworkTexture = resolvedTexture
        displayedArtworkPath = artworkPath
        artworkSwitchStartedAtMs = System.currentTimeMillis()
        if (debugArtworkPipeline) {
            logger.info(
                "watermark-artwork: display switch prev='{}' next='{}' path='{}' durationMs={}",
                previousArtworkTexture,
                resolvedTexture,
                artworkPath ?: "<null>",
                artworkSwitchDurationMs,
            )
        }
    }

    private fun resolveArtworkTexture(client: Minecraft, artworkPath: String?): ArtworkResolution {
        if (artworkPath.isNullOrBlank()) {
            val resolution = ArtworkResolution(null, "missing-artwork-path", 0, 0)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        if (currentArtworkPath != artworkPath) {
            currentArtworkPath?.let { oldPath ->
                invalidateArtworkPath(oldPath)
                if (debugArtworkPipeline) {
                    logger.info("watermark-artwork: artworkPath changed old='{}' new='{}' (cache invalidated for old path)", oldPath, artworkPath)
                }
            }
            currentArtworkPath = artworkPath
        }

        val path = try {
            Path.of(artworkPath)
        } catch (throwable: Throwable) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: invalid path '{}'", artworkPath, throwable)
            }
            val resolution = resolutionOrPrevious("invalid-path", artworkPath)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        val exists = Files.exists(path)
        if (debugArtworkPipeline && artworkLoggedExists.add(artworkPath)) {
            logger.info("watermark-artwork: path='{}' exists={}", artworkPath, exists)
        }
        if (!exists) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: file does not exist '{}'", artworkPath)
            }
            val resolution = resolutionOrPrevious("file-missing", artworkPath)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        val sizeBytes = runCatching { Files.size(path) }.getOrDefault(-1L)
        if (sizeBytes <= 0L) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: file is empty path='{}' size={}", artworkPath, sizeBytes)
            }
            val resolution = resolutionOrPrevious("file-empty", artworkPath)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        val modifiedMillis = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(-1L)
        val cacheKey = ArtworkCacheKey(artworkPath, sizeBytes, modifiedMillis)

        if (debugArtworkPipeline && artworkLoggedSignatures.add(cacheKey)) {
            logger.info(
                "watermark-artwork: signature path='{}' size={} modifiedMs={}",
                artworkPath,
                sizeBytes,
                modifiedMillis,
            )
        }

        artworkTextureCache[cacheKey]?.let { cached ->
            lastSuccessfulArtworkTexture = cached
            lastSuccessfulArtworkPath = artworkPath
            val dims = artworkTextureSizes[cacheKey] ?: artworkSourceSizeByPath[artworkPath] ?: (artworkTextureLogicalSize to artworkTextureLogicalSize)
            lastSuccessfulArtworkWidth = dims.first
            lastSuccessfulArtworkHeight = dims.second
            artworkSizeByTexture[cached] = dims
            val resolution = ArtworkResolution(cached, "cache-hit", dims.first, dims.second)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        val now = System.currentTimeMillis()
        val retryAfter = artworkRetryAfterMillis[artworkPath] ?: 0L
        if (now < retryAfter) {
            val resolution = resolutionOrPrevious("retry-deferred", artworkPath)
            logResolveStateIfChanged(artworkPath, resolution)
            return resolution
        }

        return try {
            val image = loadArtworkImage(path)
            if (image == null) {
                artworkRetryAfterMillis[artworkPath] = now + 1_500L
                logger.warn("watermark-artwork: image decode failed '{}' (placeholder active, will retry)", artworkPath)
                val resolution = resolutionOrPrevious("decode-failed", artworkPath)
                logResolveStateIfChanged(artworkPath, resolution)
                return resolution
            }

            if (debugArtworkPipeline) {
                logger.info(
                    "watermark-artwork: decoder success path='{}' ext='{}' image={}x{}",
                    artworkPath,
                    path.fileName.toString().substringAfterLast('.', "<none>"),
                    image.width,
                    image.height,
                )
            }

            val textureId = Identifier.fromNamespaceAndPath(
                "visualclient",
                "watermark_artwork_${cacheKey.textureSuffix}",
            )

            // Do not close this NativeImage manually: DynamicTexture owns it after registration.
            client.textureManager.register(textureId, NonDumpableDynamicTexture({ "visualclient-watermark-artwork" }, image))
            artworkTextureCache[cacheKey] = textureId
            artworkTextureSizes[cacheKey] = image.width to image.height
            artworkSourceSizeByPath[artworkPath] = image.width to image.height
            artworkSizeByTexture[textureId] = image.width to image.height
            artworkRetryAfterMillis.remove(artworkPath)
            artworkLoadFailures.remove(artworkPath)
            lastSuccessfulArtworkTexture = textureId
            lastSuccessfulArtworkPath = artworkPath
            lastSuccessfulArtworkWidth = image.width
            lastSuccessfulArtworkHeight = image.height
            logger.info(
                "watermark-artwork: texture register success path='{}' texture='{}' liveBuffer={}",
                artworkPath,
                textureId,
                artworkPath.endsWith("current_cover.png", ignoreCase = true),
            )
            val resolution = ArtworkResolution(textureId, "loaded-texture", image.width, image.height)
            logResolveStateIfChanged(artworkPath, resolution)
            resolution
        } catch (throwable: Throwable) {
            artworkRetryAfterMillis[artworkPath] = now + 1_500L
            logger.warn("watermark-artwork: texture load failed path='{}' (placeholder active, will retry)", artworkPath, throwable)
            val resolution = resolutionOrPrevious("texture-load-exception", artworkPath)
            logResolveStateIfChanged(artworkPath, resolution)
            resolution
        }
    }

    private fun resolutionOrPrevious(reason: String, artworkPath: String?): ArtworkResolution {
        val previous = lastSuccessfulArtworkTexture
        return if (previous != null) {
            ArtworkResolution(
                previous,
                "stale-$reason",
                lastSuccessfulArtworkWidth.coerceAtLeast(artworkTextureLogicalSize),
                lastSuccessfulArtworkHeight.coerceAtLeast(artworkTextureLogicalSize),
            )
        } else {
            ArtworkResolution(null, reason, 0, 0)
        }
    }

    private fun logResolveStateIfChanged(artworkPath: String?, resolution: ArtworkResolution) {
        if (!debugArtworkPipeline) return
        val state = "${artworkPath ?: "<null>"}|${resolution.reason}|texture=${resolution.texture != null}|${resolution.textureWidth}x${resolution.textureHeight}"
        if (lastArtworkResolveState == state) return
        lastArtworkResolveState = state
        logger.info(
            "watermark-artwork: resolve path='{}' reason='{}' texturePresent={} source={}x{}",
            artworkPath ?: "<null>",
            resolution.reason,
            resolution.texture != null,
            resolution.textureWidth,
            resolution.textureHeight,
        )
    }

    private fun invalidateArtworkPath(artworkPath: String) {
        val keysToRemove = artworkTextureCache.keys.filter { it.path == artworkPath }
        keysToRemove.forEach { key ->
            val removedTexture = artworkTextureCache.remove(key)
            artworkTextureSizes.remove(key)
            if (removedTexture != null && removedTexture != displayedArtworkTexture && removedTexture != previousArtworkTexture) {
                artworkSizeByTexture.remove(removedTexture)
            }
        }
        artworkRetryAfterMillis.remove(artworkPath)
    }

    private fun resolveTextureSize(texture: Identifier, artworkPath: String?): Pair<Int, Int> {
        artworkSizeByTexture[texture]?.let { return it }
        if (!artworkPath.isNullOrBlank()) {
            artworkSourceSizeByPath[artworkPath]?.let { return it }
        }
        val width = lastSuccessfulArtworkWidth.coerceAtLeast(artworkTextureLogicalSize)
        val height = lastSuccessfulArtworkHeight.coerceAtLeast(artworkTextureLogicalSize)
        return width to height
    }

    private fun loadArtworkImage(path: Path): NativeImage? {
        return Files.newInputStream(path).use { input ->
            NativeImage.read(input)
        }
    }

    private fun calculateCompactMusicTextWidth(widgetWidth: Int): Int {
        val iconSize = WatermarkHudTheme.compactArtworkSize
        val left = WatermarkHudTheme.paddingX + iconSize + 8
        val rightReserved = WatermarkHudTheme.paddingX + 44
        return (widgetWidth - left - rightReserved).coerceAtLeast(26)
    }

    private fun computeLocalBounds(expansion: Float): HudBounds {
        val width = lerpInt(WatermarkHudTheme.compactWidth, WatermarkHudTheme.expandedWidth, expansion)
        val height = lerpInt(WatermarkHudTheme.compactHeight, WatermarkHudTheme.expandedHeight, expansion)
        return HudBounds(0, 0, width, height)
    }

    private fun computeActualBounds(context: GuiGraphics, expansion: Float, scale: Float): HudBounds {
        val localBounds = computeLocalBounds(expansion)
        val width = (localBounds.width * scale).roundToInt().coerceAtLeast(1)
        val height = (localBounds.height * scale).roundToInt().coerceAtLeast(1)
        val x = (context.guiWidth() - width) / 2
        val y = WatermarkHudTheme.anchorTop
        return HudBounds(x, y, width, height)
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${watermarkModuleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun toLocalMouse(globalMouse: Int, origin: Int, scale: Float): Int {
        return ((globalMouse - origin) / scale).toInt()
    }

    private fun resolvePing(client: Minecraft): Int {
        val player = client.player ?: return 0
        return client.connection?.getPlayerInfo(player.uuid)?.latency ?: 0
    }

    private fun formatTime(seconds: Float): String {
        val safe = seconds.coerceAtLeast(0f).roundToInt()
        val min = safe / 60
        val sec = safe % 60
        return String.format("%d:%02d", min, sec)
    }

    private fun clipStyledText(value: String, maxWidth: Int, font: Font): net.minecraft.network.chat.MutableComponent {
        if (font.width(vText(value)) <= maxWidth) return vText(value)
        var trimmed = value
        while (trimmed.isNotEmpty() && font.width(vText("$trimmed...")) > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) vText("...") else vText("$trimmed...")
    }

    private fun lerpInt(from: Int, to: Int, progress: Float): Int {
        return (from + ((to - from) * progress.coerceIn(0f, 1f))).roundToInt()
    }
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
    val clampedRadius = radius.coerceIn(0, min(width, height) / 2)

    fillRoundedRect(context, x, y, width, height, clampedRadius, borderColor)
    if (width > 2 && height > 2) {
        fillRoundedRect(
            context,
            x + 1,
            y + 1,
            width - 2,
            height - 2,
            (clampedRadius - 1).coerceAtLeast(0),
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

private fun withAlpha(color: Int, alphaMultiplier: Float): Int {
    val baseAlpha = (color ushr 24) and 0xFF
    val rgb = color and 0x00FFFFFF
    val sourceAlpha = if (baseAlpha == 0) 0xFF else baseAlpha
    val a = (sourceAlpha * alphaMultiplier.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    return (a shl 24) or rgb
}
