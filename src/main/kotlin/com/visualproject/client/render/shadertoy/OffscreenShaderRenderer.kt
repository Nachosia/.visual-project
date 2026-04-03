package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.Minecraft
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt
import java.util.concurrent.CopyOnWriteArrayList

class OffscreenShaderRenderer(
    private val programDefinition: ShadertoyProgramRegistry.ProgramDefinition,
) : AutoCloseable {
    enum class QualityPreset(
        val scaleFactor: Float,
    ) {
        LOW(0.50f),
        MEDIUM(0.75f),
        HIGH(1.00f),
    }

    data class ShadertoyFrame internal constructor(
        val textureView: GpuTextureView,
        val sampler: GpuSampler,
        val width: Int,
        val height: Int,
        val frameIndex: Int,
        internal val compiledProgram: ShadertoyProgramRegistry.CompiledProgram,
    )

    companion object {
        private const val FULLSCREEN_VERTEX_COUNT = 4
        private const val FULLSCREEN_VERTEX_BYTES = FULLSCREEN_VERTEX_COUNT * 3 * 4
        private val activeRenderers = CopyOnWriteArrayList<OffscreenShaderRenderer>()

        fun invalidateAll() {
            activeRenderers.forEach(OffscreenShaderRenderer::invalidate)
        }

        fun shutdownAll() {
            activeRenderers.forEach(OffscreenShaderRenderer::close)
            activeRenderers.clear()
        }

        private fun buildFullscreenQuadData(): ByteBuffer {
            return ByteBuffer.allocateDirect(FULLSCREEN_VERTEX_BYTES)
                .order(ByteOrder.nativeOrder())
                .apply {
                    putVertex(-1f, -1f, 0f)
                    putVertex(-1f, 1f, 0f)
                    putVertex(1f, -1f, 0f)
                    putVertex(1f, 1f, 0f)
                    flip()
                }
        }

        private fun ByteBuffer.putVertex(x: Float, y: Float, z: Float) {
            putFloat(x)
            putFloat(y)
            putFloat(z)
        }
    }

    private val uniformState = ShadertoyUniformState()

    private var outputTexture: GpuTexture? = null
    private var outputTextureView: GpuTextureView? = null
    private var sampler: GpuSampler? = null
    private var fullscreenVertexBuffer: GpuBuffer? = null
    private var currentWidth = -1
    private var currentHeight = -1
    private var frameCounter = 0
    private var initNanoTime = System.nanoTime()
    private var previousRenderNanoTime = initNanoTime
    private var seenGeneration = -1

    init {
        activeRenderers += this
    }

    fun renderIfNeeded(client: Minecraft, qualityPreset: QualityPreset, timeScale: Float = 1.0f): ShadertoyFrame? {
        if (!ShadertoyProgramRegistry.ensureReady(programDefinition)) {
            return null
        }

        val compiledProgram = ShadertoyProgramRegistry.compiledProgram(programDefinition) ?: return null
        if (seenGeneration != compiledProgram.generation) {
            invalidate()
            seenGeneration = compiledProgram.generation
        }

        val device = RenderSystem.getDevice()
        val dimensions = resolveTargetDimensions(client, qualityPreset)
        ensureSampler(device)
        ensureFullscreenVertexBuffer(device)
        ensureOutputTarget(device, dimensions.width, dimensions.height)

        val outputView = outputTextureView ?: return null
        val fullscreenQuad = fullscreenVertexBuffer ?: return null
        val outputSampler = sampler ?: return null
        val channels = ShadertoyChannels.fallbackBindings()
        val encoder = device.createCommandEncoder()

        val now = System.nanoTime()
        val timeSeconds = (((now - initNanoTime).toDouble() / 1_000_000_000.0).toFloat() * timeScale.coerceAtLeast(0.01f))
        val timeDeltaSeconds = (((now - previousRenderNanoTime).toDouble() / 1_000_000_000.0).toFloat() * timeScale.coerceAtLeast(0.01f))
        previousRenderNanoTime = now

        val mouse = resolveMouse(client, dimensions.width, dimensions.height)
        val globalsBuffer = uniformState.uploadGlobals(
            device = device,
            encoder = encoder,
            width = dimensions.width,
            height = dimensions.height,
            timeSeconds = timeSeconds,
            timeDeltaSeconds = timeDeltaSeconds,
            frameIndex = frameCounter,
            mouseX = mouse.x,
            mouseY = mouse.y,
            mouseClickX = mouse.z,
            mouseClickY = mouse.w,
            channels = channels,
        )

        val renderPass = encoder.createRenderPass(
            { "visualclient_shadertoy_offscreen_pass" },
            outputView,
            OptionalInt.of(0xFF000000.toInt()),
        )
        try {
            renderPass.setPipeline(compiledProgram.imagePipeline)
            renderPass.setUniform("ShadertoyGlobals", globalsBuffer)
            channels.forEachIndexed { index, binding ->
                renderPass.bindTexture("iChannel$index", binding.textureView, binding.sampler)
            }
            renderPass.setVertexBuffer(0, fullscreenQuad)
            renderPass.draw(0, FULLSCREEN_VERTEX_COUNT)
        } finally {
            renderPass.close()
        }

        frameCounter += 1
        return ShadertoyFrame(
            textureView = outputView,
            sampler = outputSampler,
            width = dimensions.width,
            height = dimensions.height,
            frameIndex = frameCounter,
            compiledProgram = compiledProgram,
        )
    }

    fun invalidate() {
        outputTextureView?.close()
        outputTextureView = null
        outputTexture?.close()
        outputTexture = null
        currentWidth = -1
        currentHeight = -1
        frameCounter = 0
        initNanoTime = System.nanoTime()
        previousRenderNanoTime = initNanoTime
    }

    override fun close() {
        invalidate()
        fullscreenVertexBuffer?.close()
        fullscreenVertexBuffer = null
        sampler = null
        uniformState.close()
        activeRenderers -= this
    }

    private fun ensureSampler(device: com.mojang.blaze3d.systems.GpuDevice) {
        if (sampler == null) {
            sampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                java.util.OptionalDouble.empty(),
            )
        }
    }

    private fun ensureFullscreenVertexBuffer(device: com.mojang.blaze3d.systems.GpuDevice) {
        val existing = fullscreenVertexBuffer
        if (existing != null && !existing.isClosed) {
            return
        }

        fullscreenVertexBuffer = device.createBuffer(
            { "visualclient_shadertoy_fullscreen_quad" },
            GpuBuffer.USAGE_VERTEX,
            buildFullscreenQuadData(),
        )
    }

    private fun ensureOutputTarget(device: com.mojang.blaze3d.systems.GpuDevice, width: Int, height: Int) {
        if (outputTexture != null && outputTextureView != null && currentWidth == width && currentHeight == height) {
            return
        }

        outputTextureView?.close()
        outputTextureView = null
        outputTexture?.close()
        outputTexture = null

        val texture = device.createTexture(
            "visualclient_shadertoy_offscreen_${programDefinition.id}",
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1,
        )
        outputTexture = texture
        outputTextureView = device.createTextureView(texture)
        currentWidth = width
        currentHeight = height
    }

    private fun resolveTargetDimensions(client: Minecraft, qualityPreset: QualityPreset): TargetDimensions {
        val windowWidth = client.window.width.coerceAtLeast(1)
        val windowHeight = client.window.height.coerceAtLeast(1)
        val scaledWidth = (windowWidth * qualityPreset.scaleFactor).toInt().coerceAtLeast(64)
        val scaledHeight = (windowHeight * qualityPreset.scaleFactor).toInt().coerceAtLeast(64)
        return TargetDimensions(
            width = if ((scaledWidth and 1) == 0) scaledWidth else scaledWidth + 1,
            height = if ((scaledHeight and 1) == 0) scaledHeight else scaledHeight + 1,
        )
    }

    private fun resolveMouse(client: Minecraft, targetWidth: Int, targetHeight: Int): MouseState {
        val window = client.window
        val normalizedX = (client.mouseHandler.getScaledXPos(window) / window.guiScaledWidth.toDouble()).toFloat().coerceIn(0f, 1f)
        val normalizedY = (client.mouseHandler.getScaledYPos(window) / window.guiScaledHeight.toDouble()).toFloat().coerceIn(0f, 1f)
        val actualX = if (client.screen != null) normalizedX * targetWidth.toFloat() else targetWidth * 0.5f
        val actualY = if (client.screen != null) (1f - normalizedY) * targetHeight.toFloat() else targetHeight * 0.5f
        val clickX = if (client.screen != null && client.mouseHandler.isLeftPressed) actualX else 0f
        val clickY = if (client.screen != null && client.mouseHandler.isLeftPressed) actualY else 0f
        return MouseState(actualX, actualY, clickX, clickY)
    }

    private data class MouseState(
        val x: Float,
        val y: Float,
        val z: Float,
        val w: Float,
    )

    private data class TargetDimensions(
        val width: Int,
        val height: Int,
    )
}
