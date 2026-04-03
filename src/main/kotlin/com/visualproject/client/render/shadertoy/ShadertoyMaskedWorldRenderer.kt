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
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt

object ShadertoyMaskedWorldRenderer {
    data class Quad(
        val ax: Float,
        val ay: Float,
        val az: Float,
        val bx: Float,
        val by: Float,
        val bz: Float,
        val cx: Float,
        val cy: Float,
        val cz: Float,
        val dx: Float,
        val dy: Float,
        val dz: Float,
    )

    private const val VERTEX_STRIDE_FLOATS = 3
    private const val VERTICES_PER_QUAD = 6
    private const val MASK_PARAMS_BYTES = 16
    private const val FULLSCREEN_VERTEX_COUNT = 4
    private const val FULLSCREEN_VERTEX_BYTES = FULLSCREEN_VERTEX_COUNT * 3 * 4

    private var geometryVertexBuffer: GpuBuffer? = null
    private var geometryVertexCapacityBytes = 0
    private var maskParamsBuffer: GpuBuffer? = null
    private var fullscreenQuadBuffer: GpuBuffer? = null
    private var maskTexture: GpuTexture? = null
    private var maskTextureView: GpuTextureView? = null
    private var maskSampler: GpuSampler? = null
    private var maskWidth = -1
    private var maskHeight = -1
    private var geometryUploadBuffer: ByteBuffer? = null
    private var geometryUploadCapacityBytes = 0
    private var maskParamsUploadBuffer: ByteBuffer? = null

    fun drawQuads(
        context: net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext,
        frame: OffscreenShaderRenderer.ShadertoyFrame,
        quads: List<Quad>,
        alpha: Float,
    ) {
        if (quads.isEmpty()) return

        val client = Minecraft.getInstance()
        val target = client.mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
        val depthView = RenderSystem.outputDepthTextureOverride ?: if (target.useDepth) target.depthTextureView else null
        val device = RenderSystem.getDevice()

        ensureMaskTarget(device, client.window.width.coerceAtLeast(1), client.window.height.coerceAtLeast(1))
        ensureBuffers(device, quads.size)
        ensureSampler(device)

        val activeGeometryBuffer = geometryVertexBuffer ?: return
        val activeMaskParams = maskParamsBuffer ?: return
        val activeFullscreenQuad = fullscreenQuadBuffer ?: return
        val activeMaskView = maskTextureView ?: return
        val activeMaskSampler = maskSampler ?: return

        val encoder = device.createCommandEncoder()
        encoder.writeToBuffer(activeGeometryBuffer.slice(), buildVertexData(quads))
        encoder.writeToBuffer(activeMaskParams.slice(), buildMaskParams(alpha))
        val modelView = Matrix4f(RenderSystem.getModelViewMatrix()).apply {
            m30(0f)
            m31(0f)
            m32(0f)
        }
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            modelView,
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f(),
        )

        val maskPass = if (depthView != null) {
            encoder.createRenderPass(
                { "visualclient_shadertoy_mask_write" },
                activeMaskView,
                OptionalInt.of(0x00000000),
                depthView,
                OptionalDouble.empty(),
            )
        } else {
            encoder.createRenderPass(
                { "visualclient_shadertoy_mask_write" },
                activeMaskView,
                OptionalInt.of(0x00000000),
            )
        }

        try {
            maskPass.setPipeline(frame.compiledProgram.maskWritePipeline)
            RenderSystem.bindDefaultUniforms(maskPass)
            RenderSystem.getProjectionMatrixBuffer()?.let { projectionBuffer ->
                maskPass.setUniform("Projection", projectionBuffer)
            }
            maskPass.setUniform("DynamicTransforms", dynamicTransforms)
            maskPass.setUniform("MaskParams", activeMaskParams)
            maskPass.setVertexBuffer(0, activeGeometryBuffer)
            maskPass.draw(0, quads.size * VERTICES_PER_QUAD)
        } finally {
            maskPass.close()
        }

        val compositePass = encoder.createRenderPass(
            { "visualclient_shadertoy_mask_composite" },
            colorView,
            OptionalInt.empty(),
        )

        try {
            compositePass.setPipeline(frame.compiledProgram.maskCompositePipeline)
            RenderSystem.bindDefaultUniforms(compositePass)
            compositePass.bindTexture("ShadertoyFrame", frame.textureView, frame.sampler)
            compositePass.bindTexture("MaskTexture", activeMaskView, activeMaskSampler)
            compositePass.setVertexBuffer(0, activeFullscreenQuad)
            compositePass.draw(0, FULLSCREEN_VERTEX_COUNT)
        } finally {
            compositePass.close()
        }
    }

    fun shutdown() {
        geometryVertexBuffer?.close()
        geometryVertexBuffer = null
        geometryVertexCapacityBytes = 0
        maskParamsBuffer?.close()
        maskParamsBuffer = null
        fullscreenQuadBuffer?.close()
        fullscreenQuadBuffer = null
        maskTextureView?.close()
        maskTextureView = null
        maskTexture?.close()
        maskTexture = null
        maskSampler = null
        maskWidth = -1
        maskHeight = -1
        geometryUploadBuffer = null
        geometryUploadCapacityBytes = 0
        maskParamsUploadBuffer = null
    }

    private fun ensureBuffers(device: com.mojang.blaze3d.systems.GpuDevice, quadCount: Int) {
        val requiredBytes = quadCount * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS * 4
        if (geometryVertexBuffer == null || geometryVertexBuffer?.isClosed == true || requiredBytes > geometryVertexCapacityBytes) {
            geometryVertexBuffer?.close()
            geometryVertexBuffer = device.createBuffer(
                { "visualclient_shadertoy_mask_geometry" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                requiredBytes.toLong(),
            )
            geometryVertexCapacityBytes = requiredBytes
        }

        if (maskParamsBuffer == null || maskParamsBuffer?.isClosed == true) {
            maskParamsBuffer?.close()
            maskParamsBuffer = device.createBuffer(
                { "visualclient_shadertoy_mask_params" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                MASK_PARAMS_BYTES.toLong(),
            )
        }

        if (fullscreenQuadBuffer == null || fullscreenQuadBuffer?.isClosed == true) {
            fullscreenQuadBuffer?.close()
            fullscreenQuadBuffer = device.createBuffer(
                { "visualclient_shadertoy_mask_fullscreen_quad" },
                GpuBuffer.USAGE_VERTEX,
                buildFullscreenQuadData(),
            )
        }
    }

    private fun ensureMaskTarget(device: com.mojang.blaze3d.systems.GpuDevice, width: Int, height: Int) {
        if (maskTexture != null && maskTextureView != null && maskWidth == width && maskHeight == height) {
            return
        }

        maskTextureView?.close()
        maskTextureView = null
        maskTexture?.close()
        maskTexture = null

        val texture = device.createTexture(
            "visualclient_shadertoy_mask_target",
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1,
        )
        maskTexture = texture
        maskTextureView = device.createTextureView(texture)
        maskWidth = width
        maskHeight = height
    }

    private fun ensureSampler(device: com.mojang.blaze3d.systems.GpuDevice) {
        if (maskSampler == null) {
            maskSampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty(),
            )
        }
    }

    private fun buildVertexData(quads: List<Quad>): ByteBuffer {
        val requiredBytes = quads.size * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS * 4
        val byteBuffer = ensureGeometryUploadBuffer(requiredBytes)
        byteBuffer.clear()
        quads.forEach { quad ->
            byteBuffer.putTriangle(quad.ax, quad.ay, quad.az, quad.dx, quad.dy, quad.dz, quad.cx, quad.cy, quad.cz)
            byteBuffer.putTriangle(quad.ax, quad.ay, quad.az, quad.cx, quad.cy, quad.cz, quad.bx, quad.by, quad.bz)
        }
        byteBuffer.flip()
        return byteBuffer
    }

    private fun buildMaskParams(alpha: Float): ByteBuffer {
        val byteBuffer = ensureMaskParamsUploadBuffer()
        byteBuffer.clear()
        byteBuffer.putFloat(1f)
        byteBuffer.putFloat(1f)
        byteBuffer.putFloat(1f)
        byteBuffer.putFloat(alpha.coerceIn(0.01f, 1f))
        byteBuffer.flip()
        return byteBuffer
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

    private fun ByteBuffer.putTriangle(
        ax: Float,
        ay: Float,
        az: Float,
        bx: Float,
        by: Float,
        bz: Float,
        cx: Float,
        cy: Float,
        cz: Float,
    ) {
        putVertex(ax, ay, az)
        putVertex(bx, by, bz)
        putVertex(cx, cy, cz)
    }

    private fun ByteBuffer.putVertex(x: Float, y: Float, z: Float) {
        putFloat(x)
        putFloat(y)
        putFloat(z)
    }

    private fun ensureGeometryUploadBuffer(requiredBytes: Int): ByteBuffer {
        val existing = geometryUploadBuffer
        if (existing != null && geometryUploadCapacityBytes >= requiredBytes) {
            return existing
        }

        return ByteBuffer.allocateDirect(requiredBytes)
            .order(ByteOrder.nativeOrder())
            .also { allocated ->
                geometryUploadBuffer = allocated
                geometryUploadCapacityBytes = requiredBytes
            }
    }

    private fun ensureMaskParamsUploadBuffer(): ByteBuffer {
        val existing = maskParamsUploadBuffer
        if (existing != null) {
            return existing
        }

        return ByteBuffer.allocateDirect(MASK_PARAMS_BYTES)
            .order(ByteOrder.nativeOrder())
            .also { allocated ->
                maskParamsUploadBuffer = allocated
            }
    }
}
