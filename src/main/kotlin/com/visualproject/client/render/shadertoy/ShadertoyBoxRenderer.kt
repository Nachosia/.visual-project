package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt

object ShadertoyBoxRenderer {
    private const val BOX_VERTEX_COUNT = 36
    private const val BOX_VERTEX_STRIDE_FLOATS = 3
    private const val BOX_VERTEX_BUFFER_BYTES = BOX_VERTEX_COUNT * BOX_VERTEX_STRIDE_FLOATS * 4
    private const val BOX_PARAMS_BYTES = 16

    private data class FaceSpec(
        val distanceSq: Double,
        val a: Vertex,
        val b: Vertex,
        val c: Vertex,
        val d: Vertex,
    )

    private data class Vertex(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    private var boxVertexBuffer: GpuBuffer? = null
    private var boxParamsBuffer: GpuBuffer? = null
    private val lineBufferSource = MultiBufferSource.immediate(ByteBufferBuilder(131_072))

    fun drawBox(
        context: WorldRenderContext,
        frame: OffscreenShaderRenderer.ShadertoyFrame,
        box: AABB,
        alpha: Float,
        outlineEnabled: Boolean,
        outlineColor: Int,
        lineThickness: Float,
    ) {
        val client = Minecraft.getInstance()
        val target = client.mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
        val depthView = RenderSystem.outputDepthTextureOverride ?: if (target.useDepth) target.depthTextureView else null
        val device = RenderSystem.getDevice()
        ensureBuffers(device)

        val vertexBuffer = boxVertexBuffer ?: return
        val paramsBuffer = boxParamsBuffer ?: return
        val encoder = device.createCommandEncoder()
        encoder.writeToBuffer(vertexBuffer.slice(), buildBoxVertexData(box, client))
        encoder.writeToBuffer(paramsBuffer.slice(), createCompositeParamsBuffer(client, alpha))

        val camera = client.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val cameraInsideBox = box.contains(cameraPos.x, cameraPos.y, cameraPos.z)
        val poseStack = context.matrices()
        poseStack.pushPose()
        poseStack.translate(box.minX - cameraPos.x, box.minY - cameraPos.y, box.minZ - cameraPos.z)
        poseStack.scale(box.xsize.toFloat(), box.ysize.toFloat(), box.zsize.toFloat())
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            Matrix4f(poseStack.last().pose()),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f(),
        )
        poseStack.popPose()

        val renderPass = if (depthView != null) {
            encoder.createRenderPass(
                { "visualclient_shadertoy_box_fill" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty(),
            )
        } else {
            encoder.createRenderPass(
                { "visualclient_shadertoy_box_fill" },
                colorView,
                OptionalInt.empty(),
            )
        }
        try {
            renderPass.setPipeline(
                if (cameraInsideBox) frame.compiledProgram.boxFillInteriorPipeline
                else frame.compiledProgram.boxFillPipeline,
            )
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("DynamicTransforms", dynamicTransforms)
            renderPass.setUniform("CompositeParams", paramsBuffer)
            renderPass.bindTexture("ShadertoyFrame", frame.textureView, frame.sampler)
            renderPass.setVertexBuffer(0, vertexBuffer)
            renderPass.draw(0, BOX_VERTEX_COUNT)
        } finally {
            renderPass.close()
        }

        if (outlineEnabled) {
            drawOutline(context, box, outlineColor, lineThickness)
        }
    }

    fun drawOutlineOnly(
        context: WorldRenderContext,
        box: AABB,
        outlineColor: Int,
        lineThickness: Float,
    ) {
        drawOutline(context, box, outlineColor, lineThickness)
    }

    fun shutdown() {
        boxVertexBuffer?.close()
        boxVertexBuffer = null
        boxParamsBuffer?.close()
        boxParamsBuffer = null
    }

    private fun ensureBuffers(device: com.mojang.blaze3d.systems.GpuDevice) {
        if (boxVertexBuffer == null || boxVertexBuffer?.isClosed == true) {
            boxVertexBuffer = device.createBuffer(
                { "visualclient_shadertoy_box_vertices" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                BOX_VERTEX_BUFFER_BYTES.toLong(),
            )
        }
        if (boxParamsBuffer == null || boxParamsBuffer?.isClosed == true) {
            boxParamsBuffer = device.createBuffer(
                { "visualclient_shadertoy_box_params" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                BOX_PARAMS_BYTES.toLong(),
            )
        }
    }

    private fun buildBoxVertexData(box: AABB, client: Minecraft): ByteBuffer {
        val cameraPos = client.gameRenderer.mainCamera.position()
        val faces = buildFaces(box, cameraPos.x, cameraPos.y, cameraPos.z)
        return ByteBuffer.allocateDirect(BOX_VERTEX_BUFFER_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                faces.forEach { face ->
                    putQuad(face.a, face.b, face.c, face.d)
                }
                flip()
            }
    }

    private fun buildFaces(box: AABB, cameraX: Double, cameraY: Double, cameraZ: Double): List<FaceSpec> {
        fun distanceSq(centerX: Double, centerY: Double, centerZ: Double): Double {
            val dx = centerX - cameraX
            val dy = centerY - cameraY
            val dz = centerZ - cameraZ
            return (dx * dx) + (dy * dy) + (dz * dz)
        }

        return listOf(
            FaceSpec(
                distanceSq = distanceSq(box.center.x, box.center.y, box.maxZ),
                a = Vertex(0f, 0f, 1f),
                b = Vertex(1f, 0f, 1f),
                c = Vertex(1f, 1f, 1f),
                d = Vertex(0f, 1f, 1f),
            ),
            FaceSpec(
                distanceSq = distanceSq(box.center.x, box.center.y, box.minZ),
                a = Vertex(1f, 0f, 0f),
                b = Vertex(0f, 0f, 0f),
                c = Vertex(0f, 1f, 0f),
                d = Vertex(1f, 1f, 0f),
            ),
            FaceSpec(
                distanceSq = distanceSq(box.minX, box.center.y, box.center.z),
                a = Vertex(0f, 0f, 0f),
                b = Vertex(0f, 0f, 1f),
                c = Vertex(0f, 1f, 1f),
                d = Vertex(0f, 1f, 0f),
            ),
            FaceSpec(
                distanceSq = distanceSq(box.maxX, box.center.y, box.center.z),
                a = Vertex(1f, 0f, 1f),
                b = Vertex(1f, 0f, 0f),
                c = Vertex(1f, 1f, 0f),
                d = Vertex(1f, 1f, 1f),
            ),
            FaceSpec(
                distanceSq = distanceSq(box.center.x, box.maxY, box.center.z),
                a = Vertex(0f, 1f, 1f),
                b = Vertex(1f, 1f, 1f),
                c = Vertex(1f, 1f, 0f),
                d = Vertex(0f, 1f, 0f),
            ),
            FaceSpec(
                distanceSq = distanceSq(box.center.x, box.minY, box.center.z),
                a = Vertex(0f, 0f, 0f),
                b = Vertex(1f, 0f, 0f),
                c = Vertex(1f, 0f, 1f),
                d = Vertex(0f, 0f, 1f),
            ),
        ).sortedByDescending { it.distanceSq }
    }

    private fun ByteBuffer.putQuad(a: Vertex, b: Vertex, c: Vertex, d: Vertex) {
        putVertex(a)
        putVertex(b)
        putVertex(c)
        putVertex(a)
        putVertex(c)
        putVertex(d)
    }

    private fun ByteBuffer.putVertex(vertex: Vertex) {
        putFloat(vertex.x)
        putFloat(vertex.y)
        putFloat(vertex.z)
    }

    private fun createCompositeParamsBuffer(client: Minecraft, alpha: Float): ByteBuffer {
        return ByteBuffer.allocateDirect(BOX_PARAMS_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                putFloat(client.window.width.toFloat().coerceAtLeast(1f))
                putFloat(client.window.height.toFloat().coerceAtLeast(1f))
                putFloat(alpha.coerceIn(0.01f, 1f))
                putFloat(0f)
                flip()
            }
    }

    private fun drawOutline(
        context: WorldRenderContext,
        box: AABB,
        color: Int,
        lineThickness: Float,
    ) {
        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position()
        val pose = context.matrices().last()
        val renderType = RenderTypes.linesTranslucent()
        val consumer = lineBufferSource.getBuffer(renderType)
        val minX = (box.minX - cameraPos.x).toFloat()
        val minY = (box.minY - cameraPos.y).toFloat()
        val minZ = (box.minZ - cameraPos.z).toFloat()
        val maxX = (box.maxX - cameraPos.x).toFloat()
        val maxY = (box.maxY - cameraPos.y).toFloat()
        val maxZ = (box.maxZ - cameraPos.z).toFloat()

        line(consumer, pose, minX, minY, minZ, maxX, minY, minZ, lineThickness, color)
        line(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, lineThickness, color)
        line(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, lineThickness, color)
        line(consumer, pose, minX, minY, maxZ, minX, minY, minZ, lineThickness, color)

        line(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, lineThickness, color)
        line(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, lineThickness, color)
        line(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, lineThickness, color)
        line(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, lineThickness, color)

        line(consumer, pose, minX, minY, minZ, minX, maxY, minZ, lineThickness, color)
        line(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, lineThickness, color)
        line(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, lineThickness, color)
        line(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, lineThickness, color)
        lineBufferSource.endBatch(renderType)
    }

    private fun line(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        lineWidth: Float,
        color: Int,
    ) {
        val normalX = x2 - x1
        val normalY = y2 - y1
        val normalZ = z2 - z1
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setLineWidth(lineWidth).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(pose, x2, y2, z2).setColor(color).setLineWidth(lineWidth).setNormal(pose, normalX, normalY, normalZ)
    }
}
