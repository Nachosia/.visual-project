package com.visualproject.client.hud.gif

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualClientMod
import com.visualproject.client.VisualFileSystem
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class GifHudRenderer {

    companion object {
        private const val moduleId = "gif_hud"
    }

    private object Layout {
        const val anchorX = 36
        const val anchorY = 96
        const val placeholderWidth = 150
        const val placeholderHeight = 62
        const val placeholderRadius = 16f
    }

    private data class GifFrame(
        val texture: Identifier,
        val durationMs: Int,
    )

    private data class GifAnimation(
        val width: Int,
        val height: Int,
        val frames: List<GifFrame>,
        val totalDurationMs: Int,
    ) {
        fun frameAt(elapsedMs: Long): GifFrame {
            if (frames.size == 1 || totalDurationMs <= 0) return frames.first()
            var cursor = ((elapsedMs % totalDurationMs) + totalDurationMs) % totalDurationMs
            frames.forEach { frame ->
                if (cursor < frame.durationMs) {
                    return frame
                }
                cursor -= frame.durationMs
            }
            return frames.last()
        }
    }

    private data class GifFrameMeta(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val delayMs: Int,
        val disposalMethod: String,
    )

    private var dragState: GifHudDragState? = null
    private var lastBounds: GifHudBounds? = null
    private var currentSignature: String? = null
    private var currentAnimation: GifAnimation? = null
    private var animationStartMs = System.currentTimeMillis()
    private var lastFailedSignature: String? = null
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val selection = resolveSelection()
        val scale = ModuleStateStore.getNumberSetting(
            "${moduleId}:size",
            ModuleStateStore.getNumberSetting("${moduleId}:scale", 1.0f),
        ).coerceIn(0.1f, 3.0f)
        val animation = resolveAnimation(client, selection)
        val bounds = if (animation != null) {
            val drawWidth = (animation.width * scale).roundToInt().coerceAtLeast(1)
            val drawHeight = (animation.height * scale).roundToInt().coerceAtLeast(1)
            ensureBounds(client, drawWidth, drawHeight, animation.width, animation.height, scale)
        } else {
            ensureBounds(client, Layout.placeholderWidth, Layout.placeholderHeight, Layout.placeholderWidth, Layout.placeholderHeight, scale)
        }
        lastBounds = bounds

        if (animation == null) {
            if (client.screen is ChatScreen) {
                drawPlaceholder(context, bounds, selection?.fileName)
            }
            return
        }

        val frame = animation.frameAt(System.currentTimeMillis() - animationStartMs)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            frame.texture,
            bounds.x,
            bounds.y,
            0f,
            0f,
            bounds.width,
            bounds.height,
            animation.width,
            animation.height,
            animation.width,
            animation.height,
            0xFFFFFFFF.toInt(),
        )
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = ensureDragState(client)
        val bounds = lastBounds ?: GifHudBounds(
            state.position.x,
            state.position.y,
            Layout.placeholderWidth,
            Layout.placeholderHeight,
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

        val bounds = lastBounds ?: return consumed
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
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

        val state = dragState ?: return consumed
        val ended = state.endDrag()
        if (ended) {
            GifHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private data class GifSelection(
        val path: Path,
        val fileName: String,
        val modifiedMs: Long,
        val chromaKeyEnabled: Boolean,
        val chromaKeyColor: Int,
        val chromaKeyStrength: Float,
        val invertColors: Boolean,
    ) {
        val signature: String
            get() = "${path.toAbsolutePath().normalize()}|$modifiedMs|enabled=$chromaKeyEnabled|$chromaKeyColor|${String.format(java.util.Locale.US, "%.2f", chromaKeyStrength)}|invertColors=$invertColors"
    }

    private fun resolveSelection(): GifSelection? {
        val requested = ModuleStateStore.getTextSetting("${moduleId}:file_name", "")
            .trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val path = resolveMediaPath(requested) ?: return null
        val modifiedMs = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        val chromaEnabled = ModuleStateStore.isSettingEnabled("${moduleId}:chroma_key_enabled")
        val chromaColor = parseColor(ModuleStateStore.getTextSetting("${moduleId}:chroma_key_color", "#00FF00"), 0xFF00FF00.toInt())
        val chromaStrength = ModuleStateStore.getNumberSetting("${moduleId}:chroma_key_strength", 0.18f).coerceIn(0f, 1f)
        val invertColors = ModuleStateStore.isSettingEnabled("${moduleId}:invert_colors")
        return GifSelection(
            path = path,
            fileName = path.fileName.toString(),
            modifiedMs = modifiedMs,
            chromaKeyEnabled = chromaEnabled,
            chromaKeyColor = chromaColor,
            chromaKeyStrength = chromaStrength,
            invertColors = invertColors,
        )
    }

    private fun resolveMediaPath(requestedName: String): Path? {
        val files = buildList {
            addAll(listMediaFiles(VisualFileSystem.gifDir(), listOf("gif")))
            addAll(listMediaFiles(VisualFileSystem.pngDir(), listOf("png", "jpg", "jpeg")))
        }.sortedWith(compareBy<Path>({ mediaPriority(it) }, { it.fileName.toString().lowercase() }))

        if (files.isEmpty()) return null
        if (requestedName.isBlank()) return files.first()

        files.firstOrNull { it.fileName.toString().equals(requestedName, ignoreCase = true) }?.let { return it }
        files.firstOrNull { it.fileName.toString().equals("$requestedName.gif", ignoreCase = true) }?.let { return it }
        files.firstOrNull { it.fileName.toString().equals("$requestedName.png", ignoreCase = true) }?.let { return it }
        files.firstOrNull { it.fileName.toString().equals("$requestedName.jpg", ignoreCase = true) }?.let { return it }
        files.firstOrNull { it.fileName.toString().equals("$requestedName.jpeg", ignoreCase = true) }?.let { return it }
        files.firstOrNull { it.fileName.toString().substringBeforeLast('.').equals(requestedName, ignoreCase = true) }?.let { return it }
        return null
    }

    private fun listMediaFiles(directory: Path, extensions: List<String>): List<Path> {
        return runCatching {
            Files.list(directory).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
                        extension in extensions
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun mediaPriority(path: Path): Int {
        return when (path.parent?.fileName?.toString()?.lowercase()) {
            "gif" -> 0
            "png" -> 1
            else -> 2
        }
    }

    private fun mediaExtension(path: Path): String {
        return path.fileName.toString().substringAfterLast('.', "").lowercase()
    }

    private fun resolveAnimation(client: Minecraft, selection: GifSelection?): GifAnimation? {
        if (selection == null) {
            currentSignature = null
            currentAnimation = null
            return null
        }

        if (currentSignature == selection.signature && currentAnimation != null) {
            return currentAnimation
        }

        val animation = runCatching {
            loadAnimation(client, selection)
        }.getOrElse { throwable ->
            if (lastFailedSignature != selection.signature) {
                lastFailedSignature = selection.signature
                VisualClientMod.LOGGER.warn("gif-hud: failed to decode '{}'", selection.path, throwable)
            }
            null
        }

        currentSignature = selection.signature
        currentAnimation = animation
        animationStartMs = System.currentTimeMillis()
        if (animation != null) {
            lastFailedSignature = null
        }
        return animation
    }

    private fun loadAnimation(client: Minecraft, selection: GifSelection): GifAnimation? {
        return when (mediaExtension(selection.path)) {
            "gif" -> loadGifAnimation(client, selection)
            "png", "jpg", "jpeg" -> loadStaticImage(client, selection)
            else -> null
        }
    }

    private fun mediaTextureId(selection: GifSelection, suffix: String): Identifier {
        return Identifier.fromNamespaceAndPath(
            "visualclient",
            "gif_hud_${selection.path.fileName.toString().hashCode().toUInt().toString(16)}_${selection.modifiedMs.toString(16)}_$suffix",
        )
    }

    private fun registerFrameTexture(
        client: Minecraft,
        selection: GifSelection,
        suffix: String,
        image: BufferedImage,
    ): Identifier {
        val textureId = mediaTextureId(selection, suffix)
        client.textureManager.register(textureId, DynamicTexture({ "visualclient-gif-hud" }, nativeImageFromBuffered(image)))
        return textureId
    }

    private fun toArgb(source: BufferedImage): BufferedImage {
        if (source.type == BufferedImage.TYPE_INT_ARGB) return source
        val converted = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        converted.createGraphics().use { graphics ->
            graphics.drawImage(source, 0, 0, null)
        }
        return converted
    }

    private fun loadStaticImage(client: Minecraft, selection: GifSelection): GifAnimation? {
        val source = Files.newInputStream(selection.path).use { input ->
            ImageIO.read(input)
        } ?: return null
        val processed = applyChromaKey(
            toArgb(source),
            selection.chromaKeyEnabled,
            selection.chromaKeyColor,
            selection.chromaKeyStrength,
        )
        if (selection.invertColors) {
            applyInvertColors(processed)
        }
        val textureId = registerFrameTexture(client, selection, "static", processed)
        return GifAnimation(
            width = processed.width.coerceAtLeast(1),
            height = processed.height.coerceAtLeast(1),
            frames = listOf(GifFrame(textureId, 1_000)),
            totalDurationMs = 1_000,
        )
    }

    private fun loadGifAnimation(client: Minecraft, selection: GifSelection): GifAnimation? {
        Files.newInputStream(selection.path).use { fileInput ->
            ImageIO.createImageInputStream(fileInput).use { imageInput ->
                val readers = ImageIO.getImageReaders(imageInput)
                if (!readers.hasNext()) return null

                val reader = readers.next()
                reader.setInput(imageInput, false, false)
                try {
                    val frameCount = reader.getNumImages(true)
                    if (frameCount <= 0) return null

                    val canvasWidth = reader.getWidth(0).coerceAtLeast(1)
                    val canvasHeight = reader.getHeight(0).coerceAtLeast(1)
                    var composite = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
                    var previousRect: Rectangle? = null
                    var previousDisposal = "none"
                    var restoreSnapshot: BufferedImage? = null

                    val frames = ArrayList<GifFrame>(frameCount)
                    repeat(frameCount) { index ->
                        when (previousDisposal) {
                            "restoreToBackgroundColor" -> clearRect(composite, previousRect)
                            "restoreToPrevious" -> restoreSnapshot?.let { composite = deepCopy(it) }
                        }

                        val beforeDraw = deepCopy(composite)
                        val frameImage = reader.read(index)
                        val frameMeta = readFrameMeta(reader.getImageMetadata(index), frameImage.width, frameImage.height)

                        composite.createGraphics().use { graphics ->
                            graphics.drawImage(frameImage, frameMeta.left, frameMeta.top, null)
                        }

                        val processed = applyChromaKey(
                            deepCopy(composite),
                            selection.chromaKeyEnabled,
                            selection.chromaKeyColor,
                            selection.chromaKeyStrength,
                        )
                        if (selection.invertColors) {
                            applyInvertColors(processed)
                        }
                        val textureId = registerFrameTexture(client, selection, index.toString(), processed)
                        frames += GifFrame(textureId, frameMeta.delayMs.coerceAtLeast(50))

                        previousRect = Rectangle(frameMeta.left, frameMeta.top, frameMeta.width, frameMeta.height)
                        previousDisposal = frameMeta.disposalMethod
                        restoreSnapshot = beforeDraw
                    }

                    val totalDuration = frames.sumOf { it.durationMs }.coerceAtLeast(1)
                    return GifAnimation(canvasWidth, canvasHeight, frames, totalDuration)
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    private fun readFrameMeta(metadata: javax.imageio.metadata.IIOMetadata, fallbackWidth: Int, fallbackHeight: Int): GifFrameMeta {
        val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as? IIOMetadataNode
        val descriptor = root?.getElementsByTagName("ImageDescriptor")?.item(0) as? IIOMetadataNode
        val control = root?.getElementsByTagName("GraphicControlExtension")?.item(0) as? IIOMetadataNode
        return GifFrameMeta(
            left = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0,
            top = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0,
            width = descriptor?.getAttribute("imageWidth")?.toIntOrNull() ?: fallbackWidth,
            height = descriptor?.getAttribute("imageHeight")?.toIntOrNull() ?: fallbackHeight,
            delayMs = ((control?.getAttribute("delayTime")?.toIntOrNull() ?: 10) * 10).coerceAtLeast(50),
            disposalMethod = control?.getAttribute("disposalMethod") ?: "none",
        )
    }

    private fun clearRect(image: BufferedImage, rect: Rectangle?) {
        if (rect == null) return
        image.createGraphics().use { graphics ->
            graphics.composite = AlphaComposite.Clear
            graphics.fillRect(rect.x, rect.y, rect.width, rect.height)
        }
    }

    private fun applyChromaKey(
        image: BufferedImage,
        enabled: Boolean,
        chromaColor: Int,
        strength: Float,
    ): BufferedImage {
        if (!enabled || strength <= 0f) return image

        val keyR = (chromaColor shr 16) and 0xFF
        val keyG = (chromaColor shr 8) and 0xFF
        val keyB = chromaColor and 0xFF
        val keyHsb = Color.RGBtoHSB(keyR, keyG, keyB, null)
        val keyRf = keyR / 255f
        val keyGf = keyG / 255f
        val keyBf = keyB / 255f
        val keyLuma = luma(keyRf, keyGf, keyBf)
        val keyValue = keyHsb[2]
        val keySaturation = keyHsb[1]
        val keyIsNearBlack = keyValue <= 0.18f
        val keyIsNearWhite = keyValue >= 0.82f && keySaturation <= 0.12f
        val keyIsAchromatic = keyIsNearBlack || keyIsNearWhite || keySaturation <= 0.10f
        val normalizedStrength = strength.coerceIn(0f, 1f)
        val hardCut = if (keyIsNearWhite) {
            lerp(0.040f, 0.255f, normalizedStrength)
        } else if (keyIsNearBlack) {
            lerp(0.012f, 0.170f, normalizedStrength)
        } else if (keyIsAchromatic) {
            lerp(0.020f, 0.185f, normalizedStrength)
        } else {
            lerp(0.018f, 0.205f, normalizedStrength)
        }
        val softCut = if (keyIsNearWhite) {
            hardCut + lerp(0.065f, 0.180f, normalizedStrength)
        } else if (keyIsNearBlack) {
            hardCut + lerp(0.040f, 0.110f, normalizedStrength)
        } else if (keyIsAchromatic) {
            hardCut + lerp(0.050f, 0.120f, normalizedStrength)
        } else {
            hardCut + lerp(0.055f, 0.145f, normalizedStrength)
        }
        val recoveryStrength = lerp(0.72f, 1.0f, normalizedStrength)
        val alphaFloor = lerp(0.010f, 0.026f, normalizedStrength)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue

                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val rf = r / 255f
                val gf = g / 255f
                val bf = b / 255f
                val hsb = Color.RGBtoHSB(r, g, b, null)

                val rgbDistance = sqrt((((rf - keyRf) * (rf - keyRf)) + ((gf - keyGf) * (gf - keyGf)) + ((bf - keyBf) * (bf - keyBf))).coerceAtLeast(0f)) / sqrt(3f)
                val vectorDistance = normalizedRgbAngleDistance(rf, gf, bf, keyRf, keyGf, keyBf)
                val hueDistance = circularHueDistance(hsb[0], keyHsb[0])
                val saturationDistance = kotlin.math.abs(hsb[1] - keyHsb[1])
                val lumaDistance = kotlin.math.abs(luma(rf, gf, bf) - keyLuma)
                val weightedDistance = when {
                    keyIsNearBlack -> {
                        (
                            (rgbDistance * 0.62f) +
                                (lumaDistance * 0.28f) +
                                (saturationDistance * 0.10f)
                            ).coerceIn(0f, 1f)
                    }
                    keyIsNearWhite -> {
                        (
                            (rgbDistance * 0.56f) +
                                (lumaDistance * 0.34f) +
                                (saturationDistance * 0.10f)
                            ).coerceIn(0f, 1f)
                    }
                    keyIsAchromatic -> {
                        (
                            (rgbDistance * 0.54f) +
                                (lumaDistance * 0.24f) +
                                (saturationDistance * 0.12f) +
                                (vectorDistance * 0.10f)
                            ).coerceIn(0f, 1f)
                    }
                    else -> {
                        (
                            (vectorDistance * 0.48f) +
                                (hueDistance * 0.26f) +
                                (saturationDistance * 0.16f) +
                                (rgbDistance * 0.06f) +
                                (lumaDistance * 0.04f)
                            ).coerceIn(0f, 1f)
                    }
                }

                val baseAlphaFactor = smoothStep(hardCut, softCut, weightedDistance)
                val alphaFactor = baseAlphaFactor
                if (alphaFactor <= alphaFloor) {
                    image.setRGB(x, y, 0)
                    continue
                }

                val outAlpha = (alpha * alphaFactor).roundToInt().coerceIn(0, 255)
                if (outAlpha <= 2) {
                    image.setRGB(x, y, 0)
                    continue
                }

                if (keyIsAchromatic) {
                    image.setRGB(x, y, (outAlpha shl 24) or (r shl 16) or (g shl 8) or b)
                    continue
                }

                val edgeFactor = 1f - baseAlphaFactor
                val recoveryAlpha = baseAlphaFactor.coerceAtLeast(0.06f)
                val recoveredR = ((rf - (keyRf * (1f - recoveryAlpha))) / recoveryAlpha).coerceIn(0f, 1f)
                val recoveredG = ((gf - (keyGf * (1f - recoveryAlpha))) / recoveryAlpha).coerceIn(0f, 1f)
                val recoveredB = ((bf - (keyBf * (1f - recoveryAlpha))) / recoveryAlpha).coerceIn(0f, 1f)
                val recoveryWeight = (edgeFactor * recoveryStrength).coerceIn(0f, 1f)
                val outR = (lerp(rf, recoveredR, recoveryWeight) * 255f).roundToInt().coerceIn(0, 255)
                val outG = (lerp(gf, recoveredG, recoveryWeight) * 255f).roundToInt().coerceIn(0, 255)
                val outB = (lerp(bf, recoveredB, recoveryWeight) * 255f).roundToInt().coerceIn(0, 255)
                image.setRGB(x, y, (outAlpha shl 24) or (outR shl 16) or (outG shl 8) or outB)
            }
        }

        return image
    }

    private fun applyInvertColors(image: BufferedImage) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue

                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val outR = 255 - r
                val outG = 255 - g
                val outB = 255 - b
                image.setRGB(x, y, (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB)
            }
        }
    }

    private fun circularHueDistance(a: Float, b: Float): Float {
        val distance = kotlin.math.abs(a - b)
        return minOf(distance, 1f - distance) * 2f
    }

    private fun normalizedRgbAngleDistance(
        r: Float,
        g: Float,
        b: Float,
        keyR: Float,
        keyG: Float,
        keyB: Float,
    ): Float {
        val magnitude = sqrt((r * r) + (g * g) + (b * b))
        val keyMagnitude = sqrt((keyR * keyR) + (keyG * keyG) + (keyB * keyB))
        if (magnitude <= 0.0001f || keyMagnitude <= 0.0001f) return 1f

        val dot = ((r * keyR) + (g * keyG) + (b * keyB)) / (magnitude * keyMagnitude)
        return ((1f - dot.coerceIn(-1f, 1f)) * 0.5f).coerceIn(0f, 1f)
    }

    private fun luma(r: Float, g: Float, b: Float): Float {
        return ((r * 0.2126f) + (g * 0.7152f) + (b * 0.0722f)).coerceIn(0f, 1f)
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge1 <= edge0) return if (value < edge0) 0f else 1f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - (2f * t))
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float {
        return from + ((to - from) * progress.coerceIn(0f, 1f))
    }

    private fun nativeImageFromBuffered(image: BufferedImage): NativeImage {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input ->
            NativeImage.read(input)
        }
    }

    private fun ensureBounds(client: Minecraft, width: Int, height: Int, baseWidth: Int, baseHeight: Int, scale: Float): GifHudBounds {
        val state = ensureDragState(client)
        adjustPositionForScaleChange(client, state, baseWidth, baseHeight, scale)
        clampToScreen(client, state, width, height)
        return GifHudBounds(state.position.x, state.position.y, width, height)
    }

    private fun ensureDragState(client: Minecraft): GifHudDragState {
        val existing = dragState
        if (existing != null) return existing

        val defaultPosition = GifHudPosition(Layout.anchorX, Layout.anchorY)
        return GifHudDragState(GifHudPositionStore.load(defaultPosition)).also {
            dragState = it
        }
    }

    private fun clampToScreen(client: Minecraft, state: GifHudDragState, width: Int, height: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = width,
            hudHeight = height,
        )
    }

    private fun adjustPositionForScaleChange(
        client: Minecraft,
        state: GifHudDragState,
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
        GifHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }

    private fun drawPlaceholder(context: GuiGraphics, bounds: GifHudBounds, fileName: String?) {
        val glowColor = blendColor(0xFF2E0F16.toInt(), VisualThemeSettings.accentStrong(), 0.24f)
        SdfPanelRenderer.draw(
            context,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            SdfPanelStyle(
                baseColor = 0xF40C1118.toInt(),
                borderColor = 0x91353D4E.toInt(),
                borderWidthPx = 1.1f,
                radiusPx = Layout.placeholderRadius,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 10f, strength = 0.03f, opacity = 0.03f),
                outerGlow = SdfGlowStyle(glowColor, radiusPx = 20f, strength = 0.16f, opacity = 0.10f),
                shade = SdfShadeStyle(0x10FFFFFF, 0x18000000),
                neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(VisualThemeSettings.neonBorder(), 0x7A), widthPx = 1.0f, softnessPx = 5f, strength = 0.46f),
            ),
        )
        context.drawString(Minecraft.getInstance().font, vText("MEDIA HUD"), bounds.x + 14, bounds.y + 16, 0xFFF4F6FF.toInt(), false)
        context.drawString(
            Minecraft.getInstance().font,
            vText(fileName?.let { "Missing: $it" } ?: "Put media in Visual/gif or Visual/png"),
            bounds.x + 14,
            bounds.y + 31,
            0xFF8490B3.toInt(),
            false,
        )
    }

    private fun parseColor(raw: String, fallback: Int): Int {
        val compact = raw.trim().removePrefix("#")
        if ((compact.length != 6 && compact.length != 8) || compact.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return fallback
        }
        return compact.toLongOrNull(16)?.let { packed ->
            if (compact.length == 6) {
                (0xFF000000 or packed).toInt()
            } else {
                packed.toInt()
            }
        } ?: fallback
    }

    private fun deepCopy(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        copy.createGraphics().use { graphics ->
            graphics.drawImage(source, 0, 0, null)
        }
        return copy
    }

    private fun Graphics2D.use(block: (Graphics2D) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
