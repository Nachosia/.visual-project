package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt

object ShadertoyWorldRevealRenderer {
    data class Quad(
        val ax: Float,
        val ay: Float,
        val az: Float,
        val au: Float,
        val av: Float,
        val bx: Float,
        val by: Float,
        val bz: Float,
        val bu: Float,
        val bv: Float,
        val cx: Float,
        val cy: Float,
        val cz: Float,
        val cu: Float,
        val cv: Float,
        val dx: Float,
        val dy: Float,
        val dz: Float,
        val du: Float,
        val dv: Float,
    )

    private const val VERTEX_STRIDE_FLOATS = 5
    private const val VERTICES_PER_QUAD = 6
    private const val COMPOSITE_PARAMS_BYTES = 16

    private var geometryVertexBuffer: GpuBuffer? = null
    private var geometryVertexCapacityBytes = 0
    private var compositeParamsBuffer: GpuBuffer? = null

    fun drawQuads(
        context: WorldRenderContext,
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

        ensureBuffers(device, quads.size)

        val activeGeometryBuffer = geometryVertexBuffer ?: return
        val activeCompositeParams = compositeParamsBuffer ?: return
        val encoder = device.createCommandEncoder()
        encoder.writeToBuffer(activeGeometryBuffer.slice(), buildVertexData(quads))
        encoder.writeToBuffer(activeCompositeParams.slice(), buildCompositeParams(client, alpha))

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            Matrix4f(context.matrices().last().pose()),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f(),
        )

        val renderPass = if (depthView != null) {
            encoder.createRenderPass(
                { "visualclient_shadertoy_world_reveal" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty(),
            )
        } else {
            encoder.createRenderPass(
                { "visualclient_shadertoy_world_reveal" },
                colorView,
                OptionalInt.empty(),
            )
        }

        try {
            renderPass.setPipeline(frame.compiledProgram.worldRevealPipeline)
            RenderSystem.bindDefaultUniforms(renderPass)
            RenderSystem.getProjectionMatrixBuffer()?.let { projectionBuffer ->
                renderPass.setUniform("Projection", projectionBuffer)
            }
            renderPass.setUniform("DynamicTransforms", dynamicTransforms)
            renderPass.setUniform("CompositeParams", activeCompositeParams)
            renderPass.bindTexture("ShadertoyFrame", frame.textureView, frame.sampler)
            renderPass.setVertexBuffer(0, activeGeometryBuffer)
            renderPass.draw(0, quads.size * VERTICES_PER_QUAD)
        } finally {
            renderPass.close()
        }
    }

    fun shutdown() {
        geometryVertexBuffer?.close()
        geometryVertexBuffer = null
        geometryVertexCapacityBytes = 0
        compositeParamsBuffer?.close()
        compositeParamsBuffer = null
    }

    private fun ensureBuffers(device: com.mojang.blaze3d.systems.GpuDevice, quadCount: Int) {
        val requiredBytes = quadCount * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS * 4
        if (geometryVertexBuffer == null || geometryVertexBuffer?.isClosed == true || requiredBytes > geometryVertexCapacityBytes) {
            geometryVertexBuffer?.close()
            geometryVertexBuffer = device.createBuffer(
                { "visualclient_shadertoy_world_reveal_geometry" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                requiredBytes.toLong(),
            )
            geometryVertexCapacityBytes = requiredBytes
        }

        if (compositeParamsBuffer == null || compositeParamsBuffer?.isClosed == true) {
            compositeParamsBuffer?.close()
            compositeParamsBuffer = device.createBuffer(
                { "visualclient_shadertoy_world_reveal_params" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                COMPOSITE_PARAMS_BYTES.toLong(),
            )
        }
    }

    private fun buildVertexData(quads: List<Quad>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(quads.size * VERTICES_PER_QUAD * VERTEX_STRIDE_FLOATS * 4)
            .order(ByteOrder.nativeOrder())
        quads.forEach { quad ->
            byteBuffer.putTriangle(
                quad.ax, quad.ay, quad.az, quad.au, quad.av,
                quad.dx, quad.dy, quad.dz, quad.du, quad.dv,
                quad.cx, quad.cy, quad.cz, quad.cu, quad.cv,
            )
            byteBuffer.putTriangle(
                quad.ax, quad.ay, quad.az, quad.au, quad.av,
                quad.cx, quad.cy, quad.cz, quad.cu, quad.cv,
                quad.bx, quad.by, quad.bz, quad.bu, quad.bv,
            )
        }
        byteBuffer.flip()
        return byteBuffer
    }

    private fun buildCompositeParams(client: Minecraft, alpha: Float): ByteBuffer {
        return ByteBuffer.allocateDirect(COMPOSITE_PARAMS_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                putFloat(client.window.width.toFloat().coerceAtLeast(1f))
                putFloat(client.window.height.toFloat().coerceAtLeast(1f))
                putFloat(alpha.coerceIn(0.01f, 1f))
                putFloat(0f)
                flip()
            }
    }

    private fun ByteBuffer.putTriangle(
        ax: Float,
        ay: Float,
        az: Float,
        au: Float,
        av: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bu: Float,
        bv: Float,
        cx: Float,
        cy: Float,
        cz: Float,
        cu: Float,
        cv: Float,
    ) {
        putVertex(ax, ay, az, au, av)
        putVertex(bx, by, bz, bu, bv)
        putVertex(cx, cy, cz, cu, cv)
    }

    private fun ByteBuffer.putVertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        putFloat(x)
        putFloat(y)
        putFloat(z)
        putFloat(u)
        putFloat(v)
    }
}
