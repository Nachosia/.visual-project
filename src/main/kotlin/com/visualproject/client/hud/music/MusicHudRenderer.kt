package com.visualproject.client.hud.music

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.hud.HudOcclusionRegistry
import com.visualproject.client.hud.shared.SharedMusicHudRuntime
import com.visualproject.client.hud.watermark.WatermarkMusicProvider
import com.visualproject.client.hud.watermark.WatermarkPlaybackState
import com.visualproject.client.hud.watermark.WatermarkTrackInfo
import com.visualproject.client.render.sdf.BackdropBlurRenderer
import com.visualproject.client.render.sdf.SdfBackdropStyle
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.texture.NonDumpableDynamicTexture
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.roundToInt

internal class MusicHudRenderer(
    private val musicProvider: WatermarkMusicProvider = SharedMusicHudRuntime.provider(),
) {

    private data class ArtworkSignature(
        val path: String,
        val sizeBytes: Long,
        val modifiedMs: Long,
    )

    private enum class ControlButton {
        PREVIOUS,
        PLAY_PAUSE,
        NEXT,
    }

    private data class ControlHitboxes(
        val previous: MusicHudBounds,
        val playPause: MusicHudBounds,
        val next: MusicHudBounds,
    ) {
        fun resolve(mouseX: Int, mouseY: Int): ControlButton? {
            return when {
                previous.contains(mouseX, mouseY) -> ControlButton.PREVIOUS
                playPause.contains(mouseX, mouseY) -> ControlButton.PLAY_PAUSE
                next.contains(mouseX, mouseY) -> ControlButton.NEXT
                else -> null
            }
        }
    }

    private object Layout {
        const val width = 192
        const val height = 50
        const val radius = 8f
        const val padding = 6
        const val artworkSize = 24
        const val controlSize = 10
        const val controlGap = 3
        const val progressHeight = 4
        const val defaultTop = 52
    }

    private var dragState: MusicHudDragState? = null
    private var lastBounds: MusicHudBounds? = null
    private var lastScale = -1f
    private var lastControls: ControlHitboxes? = null
    private var wasLeftMousePressed = false

    private var lastFrameAtNs = 0L
    private var smoothedTrackKey: String? = null
    private var smoothedPositionSeconds = 0f
    private var smoothedDurationSeconds = 0f
    private var lastProviderSnapshot: WatermarkTrackInfo? = null

    private var currentArtworkSignature: ArtworkSignature? = null
    private var currentArtworkTexture: Identifier? = null
    private var currentArtworkWidth = 0
    private var currentArtworkHeight = 0

    fun render(context: GuiGraphics, client: Minecraft) {
        if (client.player == null || client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val track = musicProvider.currentTrack(client) ?: run {
            lastBounds = null
            lastControls = null
            consumeClickState(client)
            return
        }

        val displayTrack = prepareTrack(track, frameDeltaSeconds())
        val scale = hudScale()
        val actualWidth = scaled(Layout.width, scale)
        val actualHeight = scaled(Layout.height, scale)
        val state = ensureDragState(client, actualWidth, actualHeight)
        adjustPositionForScaleChange(client, state, scale)
        clampToScreen(client, state, actualWidth, actualHeight)

        val bounds = MusicHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds
        HudOcclusionRegistry.mark(bounds.x, bounds.y, bounds.width, bounds.height)

        val mouseX = client.mouseHandler.getScaledXPos(client.window).toInt()
        val mouseY = client.mouseHandler.getScaledYPos(client.window).toInt()
        val localMouseX = toLocalMouse(mouseX, bounds.x, scale)
        val localMouseY = toLocalMouse(mouseY, bounds.y, scale)

        if (VisualThemeSettings.isTransparentPreset()) {
            BackdropBlurRenderer.captureBackdrop()
        }

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)
        drawShell(context)
        val controls = drawContent(context, client, displayTrack, localMouseX, localMouseY)
        context.pose().popMatrix()

        lastControls = controls
        handleHudClicks(client, localMouseX, localMouseY)
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val bounds = lastBounds
        val currentBounds = bounds ?: return consumed
        val scale = hudScale()
        val localMouseX = toLocalMouse(mouseEvent.x().toInt(), currentBounds.x, scale)
        val localMouseY = toLocalMouse(mouseEvent.y().toInt(), currentBounds.y, scale)
        val control = lastControls?.resolve(localMouseX, localMouseY)
        if (control != null) {
            return consumed || dispatchControl(client, control)
        }

        if (currentBounds.contains(mouseEvent.x().toInt(), mouseEvent.y().toInt())) {
            val state = ensureDragState(client, currentBounds.width, currentBounds.height)
            if (state.beginDrag(currentBounds, mouseEvent.x().toInt(), mouseEvent.y().toInt())) {
                return true
            }
        }

        return consumed
    }

    fun onScreenMouseDrag(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        @Suppress("UNUSED_PARAMETER") horizontalAmount: Double,
        @Suppress("UNUSED_PARAMETER") verticalAmount: Double,
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
            hudWidth = scaled(Layout.width, scale),
            hudHeight = scaled(Layout.height, scale),
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
            MusicHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawShell(context: GuiGraphics) {
        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = Layout.width,
            height = Layout.height,
            style = shellStyle(),
        )
    }

    private fun drawContent(
        context: GuiGraphics,
        client: Minecraft,
        track: WatermarkTrackInfo,
        localMouseX: Int,
        localMouseY: Int,
    ): ControlHitboxes {
        val font = client.font
        val artworkX = Layout.padding
        val artworkY = (Layout.height - Layout.artworkSize) / 2
        drawArtwork(context, client, track, artworkX, artworkY)

        val controlsWidth = (Layout.controlSize * 3) + (Layout.controlGap * 2)
        val controlsX = (Layout.width - Layout.padding - controlsWidth - 8).coerceAtLeast(textSafeStart())
        val controlsY = Layout.padding + 1
        val prevBounds = MusicHudBounds(controlsX, controlsY, Layout.controlSize, Layout.controlSize)
        val playBounds = MusicHudBounds(
            x = prevBounds.x + Layout.controlSize + Layout.controlGap,
            y = controlsY,
            width = Layout.controlSize,
            height = Layout.controlSize,
        )
        val nextBounds = MusicHudBounds(
            x = playBounds.x + Layout.controlSize + Layout.controlGap,
            y = controlsY,
            width = Layout.controlSize,
            height = Layout.controlSize,
        )

        val textX = artworkX + Layout.artworkSize + 6
        val textRight = (controlsX - 12).coerceAtLeast(textX + 40)
        val titleWidth = (textRight - textX).coerceAtLeast(40)
        context.drawString(
            font,
            clipStyledText(track.title, titleWidth, font),
            textX,
            artworkY,
            watermarkTextPrimaryColor(),
            false,
        )
        context.drawString(
            font,
            clipStyledText(track.artist ?: "Unknown Artist", titleWidth, font),
            textX,
            artworkY + 11,
            watermarkTextSecondaryColor(),
            false,
        )

        drawControlChip(context, font, prevBounds, "<", prevBounds.contains(localMouseX, localMouseY))
        drawControlChip(
            context,
            font,
            playBounds,
            if (track.playbackState == WatermarkPlaybackState.PLAYING) "||" else ">",
            playBounds.contains(localMouseX, localMouseY),
        )
        drawControlChip(context, font, nextBounds, ">", nextBounds.contains(localMouseX, localMouseY))

        val timeText = "${formatTime(track.positionSeconds)} / ${formatTime(track.durationSeconds)}"
        val timeWidth = font.width(vText(timeText))
        val progressX = artworkX
        val progressY = Layout.height - Layout.padding - Layout.progressHeight + 1
        val timeX = (Layout.width - Layout.padding - timeWidth).coerceAtLeast(textX + 6)
        val progressWidth = (timeX - progressX - 3).coerceAtLeast(62)
        val timeY = (Layout.height - Layout.padding - font.lineHeight + 1).coerceAtLeast(artworkY + 13)

        SdfPanelRenderer.draw(
            context = context,
            x = progressX,
            y = progressY,
            width = progressWidth,
            height = Layout.progressHeight,
            style = progressTrackStyle(),
        )
        val fillWidth = (progressWidth * track.progressNormalized).roundToInt().coerceIn(0, progressWidth)
        if (fillWidth > 0) {
            SdfPanelRenderer.draw(
                context = context,
                x = progressX,
                y = progressY,
                width = fillWidth,
                height = Layout.progressHeight,
                style = progressFillStyle(),
            )
        }
        context.drawString(
            font,
            vText(timeText),
            timeX,
            timeY,
            watermarkTextMutedColor(),
            false,
        )

        return ControlHitboxes(prevBounds, playBounds, nextBounds)
    }

    private fun drawArtwork(
        context: GuiGraphics,
        client: Minecraft,
        track: WatermarkTrackInfo,
        x: Int,
        y: Int,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = x,
            y = y,
            width = Layout.artworkSize,
            height = Layout.artworkSize,
            style = artworkShellStyle(),
        )

        val resolvedTexture = track.artworkTexture ?: resolveArtworkTexture(client, track)
        if (resolvedTexture != null) {
            TextureFiltering.ensureSmooth(client.textureManager, resolvedTexture)
            val inset = 2
            val drawSize = Layout.artworkSize - (inset * 2)
            val sourceWidth = currentArtworkWidth.coerceAtLeast(drawSize)
            val sourceHeight = currentArtworkHeight.coerceAtLeast(drawSize)
            context.blit(
                RenderPipelines.GUI_TEXTURED,
                resolvedTexture,
                x + inset,
                y + inset,
                0f,
                0f,
                drawSize,
                drawSize,
                sourceWidth,
                sourceHeight,
                sourceWidth,
                sourceHeight,
                0xFFFFFFFF.toInt(),
            )
            return
        }

        context.drawString(
            client.font,
            vText("M"),
            x + (Layout.artworkSize / 2) - 3,
            y + (Layout.artworkSize / 2) - 4,
            watermarkTextSecondaryColor(),
            false,
        )
    }

    private fun drawControlChip(
        context: GuiGraphics,
        font: Font,
        bounds: MusicHudBounds,
        label: String,
        hovered: Boolean,
    ) {
        SdfPanelRenderer.draw(
            context = context,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            style = controlChipStyle(hovered),
        )
        val textWidth = font.width(vText(label))
        context.drawString(
            font,
            vText(label),
            bounds.x + ((bounds.width - textWidth) / 2),
            bounds.y + ((bounds.height - font.lineHeight) / 2),
            watermarkTextPrimaryColor(),
            false,
        )
    }

    private fun handleHudClicks(client: Minecraft, localMouseX: Int, localMouseY: Int) {
        if (client.screen != null) {
            consumeClickState(client)
            return
        }

        val leftPressed = client.mouseHandler.isLeftPressed
        val isNewClick = leftPressed && !wasLeftMousePressed
        wasLeftMousePressed = leftPressed
        if (!isNewClick) return

        val control = lastControls?.resolve(localMouseX, localMouseY) ?: return
        dispatchControl(client, control)
    }

    private fun dispatchControl(client: Minecraft, control: ControlButton): Boolean {
        val playbackController = musicProvider.playbackController(client) ?: return false
        when (control) {
            ControlButton.PREVIOUS -> playbackController.previous(client)
            ControlButton.PLAY_PAUSE -> playbackController.togglePlayPause(client)
            ControlButton.NEXT -> playbackController.next(client)
        }
        return true
    }

    private fun textSafeStart(): Int {
        return Layout.padding + Layout.artworkSize + 48
    }

    private fun consumeClickState(client: Minecraft) {
        wasLeftMousePressed = client.mouseHandler.isLeftPressed
    }

    private fun shellStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = musicShellFillColor(),
            borderColor = musicShellBorderColor(),
            borderWidthPx = 1.05f,
            radiusPx = Layout.radius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 9f, strength = 0.04f, opacity = 0.03f),
            outerGlow = if (VisualThemeSettings.isTransparentPreset() || VisualThemeSettings.isLightPreset()) {
                SdfGlowStyle.NONE
            } else {
                SdfGlowStyle(
                    color = if (VisualThemeSettings.isLightPreset()) 0xFFD5DFEB.toInt() else 0xFF000000.toInt(),
                    radiusPx = 12f,
                    strength = if (VisualThemeSettings.isLightPreset()) 0.06f else 0.10f,
                    opacity = if (VisualThemeSettings.isLightPreset()) 0.05f else 0.09f,
                )
            },
            shade = SdfShadeStyle(
                if (VisualThemeSettings.isTransparentPreset()) 0x04FFFFFF else 0x00000000,
                if (VisualThemeSettings.isTransparentPreset()) 0x0C000000 else 0x00000000,
            ),
            neonBorder = musicShellNeonStyle(),
            backdrop = if (VisualThemeSettings.isTransparentPreset()) watermarkGlassBackdrop() else SdfBackdropStyle.NONE,
        )
    }

    private fun artworkShellStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = musicInnerFillColor(),
            borderColor = musicInnerBorderColor(),
            borderWidthPx = 0.95f,
            radiusPx = 3f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle.NONE,
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle.NONE,
        )
    }

    private fun controlChipStyle(hovered: Boolean): SdfPanelStyle {
        val baseColor = if (hovered) {
            if (VisualThemeSettings.isLightPreset()) 0xC8E8EEF7.toInt() else 0xA21A2230.toInt()
        } else {
            if (VisualThemeSettings.isLightPreset()) 0xB8EEF4FB.toInt() else 0x86111924.toInt()
        }
        val borderColor = if (hovered) {
            if (VisualThemeSettings.isLightPreset()) 0xFFADBCCD.toInt() else blendColor(0xFF394A62.toInt(), VisualThemeSettings.accentStrong(), 0.30f)
        } else {
            musicInnerBorderColor()
        }
        return SdfPanelStyle(
            baseColor = baseColor,
            borderColor = borderColor,
            borderWidthPx = 0.9f,
            radiusPx = 7f,
            innerGlow = SdfGlowStyle.NONE,
            outerGlow = SdfGlowStyle.NONE,
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle.NONE,
        )
    }

    private fun progressTrackStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = if (VisualThemeSettings.isLightPreset()) 0xCFE4EBF4.toInt() else 0x8A1B2230.toInt(),
            borderColor = if (VisualThemeSettings.isLightPreset()) 0x90BCCBDD.toInt() else 0x64374658,
            borderWidthPx = 0.7f,
            radiusPx = Layout.progressHeight / 2f,
            innerGlow = SdfGlowStyle.NONE,
            outerGlow = SdfGlowStyle.NONE,
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle.NONE,
        )
    }

    private fun progressFillStyle(): SdfPanelStyle {
        val accentSync = ModuleStateStore.isSettingEnabled("${MusicHudModule.moduleId}:accent_sync")
        val accent = if (accentSync) VisualThemeSettings.sliderFill() else 0xFF76B5FF.toInt()
        return SdfPanelStyle(
            baseColor = accent,
            borderColor = blendColor(accent, 0xFFFFFFFF.toInt(), 0.08f),
            borderWidthPx = 0.6f,
            radiusPx = Layout.progressHeight / 2f,
            innerGlow = SdfGlowStyle.NONE,
            outerGlow = SdfGlowStyle.NONE,
            shade = SdfShadeStyle(0x00000000, 0x00000000),
            neonBorder = SdfNeonBorderStyle.NONE,
        )
    }

    private fun musicShellFillColor(): Int {
        return VisualThemeSettings.hudShellFill()
    }

    private fun musicShellBorderColor(): Int {
        return VisualThemeSettings.hudShellBorder()
    }

    private fun musicInnerFillColor(): Int {
        return VisualThemeSettings.hudInnerFill()
    }

    private fun musicInnerBorderColor(): Int {
        return VisualThemeSettings.hudInnerBorder()
    }

    private fun watermarkGlassBackdrop(): SdfBackdropStyle {
        return SdfBackdropStyle(
            blurRadiusPx = 5.2f,
            tintMix = 0.60f,
            opacity = 0.72f,
        )
    }

    private fun musicShellNeonStyle(): SdfNeonBorderStyle {
        if (!VisualThemeSettings.themeAllowsNeon()) {
            return SdfNeonBorderStyle.NONE
        }
        return SdfNeonBorderStyle(
            color = VisualThemeSettings.withAlpha(
                VisualThemeSettings.neonBorder(),
                if (VisualThemeSettings.isLightPreset()) 0x68 else 0x9A,
            ),
            widthPx = 0.95f,
            softnessPx = 4.5f,
            strength = if (VisualThemeSettings.isLightPreset()) 0.30f else 0.50f,
        )
    }

    private fun watermarkTextPrimaryColor(): Int {
        return if (VisualThemeSettings.isLightPreset()) 0xFF111111.toInt() else VisualThemeSettings.textPrimary()
    }

    private fun watermarkTextSecondaryColor(): Int {
        return if (VisualThemeSettings.isLightPreset()) 0xFF232323.toInt() else VisualThemeSettings.textSecondary()
    }

    private fun watermarkTextMutedColor(): Int {
        return if (VisualThemeSettings.isLightPreset()) 0xFF363636.toInt() else VisualThemeSettings.textMuted()
    }

    private fun prepareTrack(track: WatermarkTrackInfo, deltaSeconds: Float): WatermarkTrackInfo {
        val key = "${track.source}|${track.title}|${track.artist.orEmpty()}"
        val providerSnapshotChanged = lastProviderSnapshot !== track
        if (smoothedTrackKey != key) {
            smoothedTrackKey = key
            smoothedPositionSeconds = track.positionSeconds.coerceAtLeast(0f)
            smoothedDurationSeconds = track.durationSeconds.coerceAtLeast(0f)
            lastProviderSnapshot = track
            return track.copy(
                positionSeconds = smoothedPositionSeconds,
                durationSeconds = smoothedDurationSeconds,
            )
        }

        if (providerSnapshotChanged) {
            val targetPosition = track.positionSeconds.coerceAtLeast(0f)
            val targetDuration = track.durationSeconds.coerceAtLeast(0f)
            val diff = targetPosition - smoothedPositionSeconds
            smoothedPositionSeconds = when {
                abs(diff) >= 6f -> targetPosition
                else -> smoothedPositionSeconds + (diff * 0.34f)
            }
            smoothedDurationSeconds = when {
                targetDuration <= 0f -> smoothedDurationSeconds
                smoothedDurationSeconds <= 0f -> targetDuration
                else -> smoothedDurationSeconds + ((targetDuration - smoothedDurationSeconds) * 0.40f)
            }
            lastProviderSnapshot = track
        }

        if (track.playbackState == WatermarkPlaybackState.PLAYING && deltaSeconds > 0f) {
            smoothedPositionSeconds = (smoothedPositionSeconds + deltaSeconds)
                .coerceAtMost(smoothedDurationSeconds.takeIf { it > 0f } ?: Float.MAX_VALUE)
        }

        return track.copy(
            positionSeconds = smoothedPositionSeconds.coerceAtLeast(0f),
            durationSeconds = smoothedDurationSeconds.coerceAtLeast(0f),
        )
    }

    private fun resolveArtworkTexture(client: Minecraft, track: WatermarkTrackInfo): Identifier? {
        val artworkPath = track.artworkPath?.trim().orEmpty()
        if (artworkPath.isBlank()) return currentArtworkTexture

        val path = runCatching { Path.of(artworkPath) }.getOrNull() ?: return currentArtworkTexture
        if (!Files.exists(path) || !Files.isRegularFile(path)) return currentArtworkTexture

        val signature = runCatching {
            ArtworkSignature(
                path = artworkPath,
                sizeBytes = Files.size(path),
                modifiedMs = Files.getLastModifiedTime(path).toMillis(),
            )
        }.getOrNull() ?: return currentArtworkTexture

        if (signature == currentArtworkSignature) {
            return currentArtworkTexture
        }

        return try {
            val image = Files.newInputStream(path).use { input -> NativeImage.read(input) } ?: return currentArtworkTexture
            val textureId = Identifier.fromNamespaceAndPath(
                "visualclient",
                "music_hud_artwork_${signature.path.hashCode().toUInt().toString(16)}_${signature.modifiedMs.toULong().toString(16)}",
            )
            client.textureManager.register(textureId, NonDumpableDynamicTexture({ "visualclient-music-hud-artwork" }, image))
            currentArtworkSignature = signature
            currentArtworkTexture = textureId
            currentArtworkWidth = image.width
            currentArtworkHeight = image.height
            textureId
        } catch (_: Throwable) {
            currentArtworkTexture
        }
    }

    private fun frameDeltaSeconds(): Float {
        val now = System.nanoTime()
        val delta = if (lastFrameAtNs == 0L) {
            0f
        } else {
            ((now - lastFrameAtNs).toDouble() / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
        }
        lastFrameAtNs = now
        return delta
    }

    private fun ensureDragState(client: Minecraft, hudWidth: Int, hudHeight: Int): MusicHudDragState {
        val existing = dragState
        if (existing != null) return existing

        val defaultPosition = MusicHudPosition(
            x = ((client.window.guiScaledWidth - hudWidth) / 2).coerceAtLeast(0),
            y = Layout.defaultTop.coerceIn(0, (client.window.guiScaledHeight - hudHeight).coerceAtLeast(0)),
        )
        return MusicHudDragState(MusicHudPositionStore.load(defaultPosition)).also { dragState = it }
    }

    private fun clampToScreen(client: Minecraft, state: MusicHudDragState, hudWidth: Int, hudHeight: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = hudWidth,
            hudHeight = hudHeight,
        )
    }

    private fun adjustPositionForScaleChange(client: Minecraft, state: MusicHudDragState, scale: Float) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (abs(lastScale - scale) < 0.001f || state.dragging) {
            lastScale = scale
            return
        }

        val oldWidth = scaled(Layout.width, lastScale)
        val oldHeight = scaled(Layout.height, lastScale)
        val newWidth = scaled(Layout.width, scale)
        val newHeight = scaled(Layout.height, scale)
        state.setPositionClamped(
            x = state.position.x - ((newWidth - oldWidth) / 2),
            y = state.position.y - ((newHeight - oldHeight) / 2),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = newWidth,
            hudHeight = newHeight,
        )
        MusicHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${MusicHudModule.moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun toLocalMouse(globalMouse: Int, origin: Int, scale: Float): Int {
        return ((globalMouse - origin) / scale).toInt()
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

    private fun formatTime(seconds: Float): String {
        val safe = seconds.coerceAtLeast(0f).roundToInt()
        val minutes = safe / 60
        val remainder = safe % 60
        return String.format("%d:%02d", minutes, remainder)
    }

    private fun clipStyledText(value: String, maxWidth: Int, font: Font): net.minecraft.network.chat.MutableComponent {
        if (font.width(vText(value)) <= maxWidth) return vText(value)
        var trimmed = value
        while (trimmed.isNotEmpty() && font.width(vText("$trimmed...")) > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) vText("...") else vText("$trimmed...")
    }
}
