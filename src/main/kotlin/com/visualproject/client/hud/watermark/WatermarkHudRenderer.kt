package com.visualproject.client.hud.watermark

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.vText
import com.visualproject.client.vBrandText
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
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
    private var smoothedTrackKey: String? = null
    private var smoothedPositionSeconds = 0f
    private var smoothedDurationSeconds = 0f
    private var lastProviderSnapshot: WatermarkTrackInfo? = null

    private val artworkTextureCache = HashMap<String, ResourceLocation>()
    private val artworkLoadFailures = HashSet<String>()

    fun render(
        context: GuiGraphics,
        @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker,
        client: Minecraft,
    ) {
        if (client.player == null || client.options.hideGui) return

        val font = client.font
        val mouseX = client.mouseHandler.getScaledXPos(client.window).toInt()
        val mouseY = client.mouseHandler.getScaledYPos(client.window).toInt()

        val currentBounds = computeBounds(context, animation.currentExpansion())
        val state = stateCalculator.resolve(client, currentBounds, mouseX, mouseY)

        val marqueeWidth = calculateCompactMusicTextWidth(currentBounds.width)
        val trackTitleWidth = state.track?.let { font.width(vText(it.title)) } ?: 0
        val marqueeCycle = (trackTitleWidth + WatermarkHudTheme.marqueeGapPx).toFloat()
        val marqueeActive = state.mode == WatermarkMode.MUSIC && trackTitleWidth > marqueeWidth

        val snapshot = animation.tick(
            targetExpanded = state.targetExpanded,
            marqueeActive = marqueeActive,
            marqueeCyclePx = marqueeCycle,
        )

        val bounds = computeBounds(context, snapshot.expansion)
        val renderTrack = state.track?.let { prepareRenderTrack(client, it, snapshot.deltaSeconds) }
        drawMainShell(context, bounds, snapshot.expansion)

        val compactAlpha = (1f - (snapshot.expansion * 0.36f)).coerceIn(0.50f, 1f)
        if (state.mode == WatermarkMode.DEFAULT || renderTrack == null) {
            drawDefaultCompact(context, font, bounds, client, compactAlpha)
            consumeClickState(client)
            return
        }

        drawMusicCompact(context, font, bounds, renderTrack, snapshot.marqueePx, compactAlpha)

        val controls = if (snapshot.expansion > 0.01f && state.canExpand) {
            drawExpandedMusic(context, font, bounds, renderTrack, snapshot.expansion, mouseX, mouseY)
        } else {
            null
        }

        handleControlClicks(client, controls, mouseX, mouseY, snapshot.expansion)
    }

    private fun drawMainShell(context: GuiGraphics, bounds: HudBounds, expansion: Float) {
        val glowPadding = 2 + expansion.roundToInt()
        fillRoundedRect(
            context,
            bounds.left - glowPadding,
            bounds.top - glowPadding,
            bounds.width + (glowPadding * 2),
            bounds.height + (glowPadding * 2),
            WatermarkHudTheme.radius + glowPadding,
            withAlpha(WatermarkHudTheme.outerGlow, 0.28f + (expansion * 0.10f)),
        )

        drawRoundedPanel(
            context,
            bounds.left,
            bounds.top,
            bounds.width,
            bounds.height,
            WatermarkHudTheme.panelFill,
            WatermarkHudTheme.panelBorder,
            WatermarkHudTheme.radius + expansion.roundToInt(),
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

        drawRoundedPanel(
            context,
            iconX,
            iconY,
            iconSize,
            iconSize,
            withAlpha(WatermarkHudTheme.iconFill, alpha),
            withAlpha(WatermarkHudTheme.iconBorder, alpha),
            6,
        )

        context.drawString(font, vBrandText("N"), iconX + 4, iconY + 3, withAlpha(WatermarkHudTheme.textPrimary, alpha), false)

        val titleX = iconX + iconSize + 8
        context.drawString(
            font,
            vBrandText("Nachosia"),
            titleX,
            baselineY,
            withAlpha(WatermarkHudTheme.textPrimary, alpha),
            false,
        )

        val ping = resolvePing(client)
        val fps = client.fps
        val infoText = "${ping}ms  ${fps}fps"
        val infoWidth = font.width(vText(infoText))
        val infoX = bounds.left + bounds.width - WatermarkHudTheme.paddingX - infoWidth
        context.drawString(
            font,
            vText(infoText),
            infoX,
            baselineY,
            withAlpha(WatermarkHudTheme.textSecondary, alpha),
            false,
        )
    }

    private fun drawMusicCompact(
        context: GuiGraphics,
        font: Font,
        bounds: HudBounds,
        track: WatermarkTrackInfo,
        marqueeOffsetPx: Float,
        alpha: Float,
    ) {
        val iconSize = 14
        val iconX = bounds.left + WatermarkHudTheme.paddingX
        val iconY = bounds.top + (WatermarkHudTheme.compactHeight - iconSize) / 2
        val baselineY = bounds.top + ((WatermarkHudTheme.compactHeight - font.lineHeight) / 2)

        drawRoundedPanel(
            context,
            iconX,
            iconY,
            iconSize,
            iconSize,
            withAlpha(WatermarkHudTheme.iconFill, alpha),
            withAlpha(WatermarkHudTheme.iconBorder, alpha),
            6,
        )
        context.drawString(font, vText("M"), iconX + 4, iconY + 3, withAlpha(WatermarkHudTheme.accent, alpha), false)

        val rightMarker = if (track.playbackState == WatermarkPlaybackState.PLAYING) "PLAY" else "PAUSE"
        val rightMarkerWidth = font.width(vText(rightMarker))
        val rightMarkerX = bounds.left + bounds.width - WatermarkHudTheme.paddingX - rightMarkerWidth

        val textX = iconX + iconSize + 8
        val textWidth = (rightMarkerX - 8 - textX).coerceAtLeast(28)
        val textY = baselineY
        val titleWidth = font.width(vText(track.title))

        context.enableScissor(textX, bounds.top + 3, textX + textWidth, bounds.top + WatermarkHudTheme.compactHeight - 3)
        if (titleWidth <= textWidth) {
            context.drawString(font, vText(track.title), textX, textY, withAlpha(WatermarkHudTheme.textPrimary, alpha), false)
        } else {
            val cycle = (titleWidth + WatermarkHudTheme.marqueeGapPx).toFloat()
            val firstX = (textX - marqueeOffsetPx).roundToInt()
            val secondX = (firstX + cycle).roundToInt()
            val color = withAlpha(WatermarkHudTheme.textPrimary, alpha)
            context.drawString(font, vText(track.title), firstX, textY, color, false)
            context.drawString(font, vText(track.title), secondX, textY, color, false)
        }
        context.disableScissor()

        context.drawString(font, vText(rightMarker), rightMarkerX, textY, withAlpha(WatermarkHudTheme.textMuted, alpha), false)
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
        val compactHeight = WatermarkHudTheme.compactHeight
        val contentLeft = bounds.left + WatermarkHudTheme.expandedPadding
        val contentTop = bounds.top + compactHeight + 4
        val contentRight = bounds.left + bounds.width - WatermarkHudTheme.expandedPadding
        val contentBottom = bounds.top + bounds.height - 5
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(88)

        fillRoundedRect(
            context,
            bounds.left + 2,
            bounds.top + 2,
            bounds.width - 4,
            bounds.height - 4,
            (WatermarkHudTheme.radius - 2).coerceAtLeast(3),
            withAlpha(WatermarkHudTheme.expandedInnerFill, alpha),
        )
        context.fill(
            bounds.left + 14,
            bounds.top + compactHeight + 1,
            bounds.left + bounds.width - 14,
            bounds.top + compactHeight + 2,
            withAlpha(WatermarkHudTheme.expandedInnerBorder, alpha),
        )

        val artworkSize = WatermarkHudTheme.expandedArtworkSize
        val artworkX = contentLeft
        val artworkY = contentTop
        drawArtwork(context, font, track, artworkX, artworkY, artworkSize, alpha)

        val textLeft = artworkX + artworkSize + 8
        val controlsBottomY = contentBottom - WatermarkHudTheme.expandedControlSize
        val nextBounds = HudBounds(
            contentRight - WatermarkHudTheme.expandedControlSize,
            controlsBottomY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )
        val playBounds = HudBounds(
            nextBounds.left - WatermarkHudTheme.expandedControlGap - WatermarkHudTheme.expandedControlSize,
            controlsBottomY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )
        val prevBounds = HudBounds(
            playBounds.left - WatermarkHudTheme.expandedControlGap - WatermarkHudTheme.expandedControlSize,
            controlsBottomY,
            WatermarkHudTheme.expandedControlSize,
            WatermarkHudTheme.expandedControlSize,
        )

        val titleMaxWidth = (contentRight - textLeft).coerceAtLeast(40)
        val clippedTitle = clipStyledText(track.title, titleMaxWidth, font)
        val subtitle = track.artist ?: "Unknown Artist"
        val clippedSubtitle = clipStyledText(subtitle, titleMaxWidth, font)
        context.drawString(font, clippedTitle, textLeft, artworkY + 1, withAlpha(WatermarkHudTheme.textPrimary, alpha), false)
        context.drawString(font, clippedSubtitle, textLeft, artworkY + 11, withAlpha(WatermarkHudTheme.textSecondary, alpha), false)

        val progressX = textLeft
        val progressY = contentBottom - 17
        val progressWidth = (prevBounds.left - progressX - 7).coerceAtLeast(34)
        val progressHeight = 4

        drawRoundedPanel(
            context,
            progressX,
            progressY,
            progressWidth,
            progressHeight,
            withAlpha(WatermarkHudTheme.progressTrack, alpha),
            withAlpha(WatermarkHudTheme.progressBorder, alpha),
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
                withAlpha(WatermarkHudTheme.progressFill, alpha),
            )
        }

        val timeText = "${formatTime(track.positionSeconds)} / ${formatTime(track.durationSeconds)}"
        context.drawString(
            font,
            vText(timeText),
            progressX,
            progressY + 7,
            withAlpha(WatermarkHudTheme.textMuted, alpha),
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
    ) {
        drawRoundedPanel(
            context,
            x,
            y,
            size,
            size,
            withAlpha(0xA2121624.toInt(), alpha),
            withAlpha(0x5E3A4560, alpha),
            5,
        )

        val texture = track.artworkTexture
        if (texture != null) {
            context.blit(texture, x + 1, y + 1, size - 2, size - 2, 0f, 0f, 1f, 1f)
            return
        }

        context.drawString(
            font,
            vText("M"),
            x + (size / 2) - 3,
            y + (size / 2) - 4,
            withAlpha(WatermarkHudTheme.accent, alpha),
            false,
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
        val fill = if (hovered) 0xAA1A2237.toInt() else 0x7A121A2D
        val border = if (hovered) WatermarkHudTheme.accent else 0x4F3A4561

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
            withAlpha(WatermarkHudTheme.textPrimary, alpha),
            false,
        )
    }

    private fun handleControlClicks(
        client: Minecraft,
        controls: ControlHitboxes?,
        mouseX: Int,
        mouseY: Int,
        expansion: Float,
    ) {
        val leftPressed = client.mouseHandler.isLeftPressed
        val isNewClick = leftPressed && !wasLeftMousePressed
        wasLeftMousePressed = leftPressed

        if (!isNewClick || controls == null || expansion < 0.45f) return

        val hoveredControl = controls.resolveHovered(mouseX, mouseY) ?: return
        val playbackController = musicProvider.playbackController(client)
        if (playbackController == null) {
            logger.warn("watermark-control: clicked '{}' but playback controller is unavailable", hoveredControl.name)
            return
        }
        logger.info("watermark-control: clicked '{}'", hoveredControl.name)

        when (hoveredControl) {
            ControlButton.PREVIOUS -> playbackController.previous(client)
            ControlButton.PLAY_PAUSE -> playbackController.togglePlayPause(client)
            ControlButton.NEXT -> playbackController.next(client)
        }
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

        val artworkTexture = resolveArtworkTexture(client, track.artworkPath)

        return track.copy(
            positionSeconds = smoothedPositionSeconds,
            durationSeconds = smoothedDurationSeconds.coerceAtLeast(track.durationSeconds.coerceAtLeast(0f)),
            artworkTexture = artworkTexture,
        )
    }

    private fun resolveArtworkTexture(client: Minecraft, artworkPath: String?): ResourceLocation? {
        if (artworkPath.isNullOrBlank()) return null

        artworkTextureCache[artworkPath]?.let { return it }

        val path = try {
            Path.of(artworkPath)
        } catch (throwable: Throwable) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: invalid path '{}'", artworkPath, throwable)
            }
            return null
        }

        if (!Files.exists(path)) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: file does not exist '{}'", artworkPath)
            }
            return null
        }

        return try {
            Files.newInputStream(path).use { stream ->
                val image = NativeImage.read(stream)
                val textureId = ResourceLocation.fromNamespaceAndPath(
                    "visualclient",
                    "watermark_artwork_${artworkPath.hashCode().toUInt().toString(16)}",
                )
                client.textureManager.register(textureId, DynamicTexture({ "visualclient-watermark-artwork" }, image))
                artworkTextureCache[artworkPath] = textureId
                logger.info("watermark-artwork: loaded '{}' as '{}'", artworkPath, textureId)
                textureId
            }
        } catch (throwable: Throwable) {
            if (artworkLoadFailures.add(artworkPath)) {
                logger.warn("watermark-artwork: failed to load '{}'", artworkPath, throwable)
            }
            null
        }
    }

    private fun calculateCompactMusicTextWidth(widgetWidth: Int): Int {
        val iconSize = 14
        val left = WatermarkHudTheme.paddingX + iconSize + 8
        val rightReserved = WatermarkHudTheme.paddingX + 44
        return (widgetWidth - left - rightReserved).coerceAtLeast(26)
    }

    private fun computeBounds(context: GuiGraphics, expansion: Float): HudBounds {
        val width = lerpInt(WatermarkHudTheme.compactWidth, WatermarkHudTheme.expandedWidth, expansion)
        val height = lerpInt(WatermarkHudTheme.compactHeight, WatermarkHudTheme.expandedHeight, expansion)
        val x = (context.guiWidth() - width) / 2
        val y = WatermarkHudTheme.anchorTop
        return HudBounds(x, y, width, height)
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
