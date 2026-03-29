package com.visualproject.client.visuals.nimb

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

object NimbRenderer {
    private const val ringSegments = 72
    private const val modelRootOffsetY = -1.501f
    private const val baseRingY = -(10f / 16f)
    private const val sliderHeightScale = 0.125f
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { context ->
            render(context)
        })
    }

    private fun render(context: WorldRenderContext) {
        if (!NimbModule.isActive()) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.level == null) return
        if (client.options.getCameraType().isFirstPerson) return
        if (player.isSpectator) return

        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val renderer = client.entityRenderDispatcher.getPlayerRenderer(player)
        val renderState = renderer.createRenderState()
        renderer.extractRenderState(player, renderState, partialTick)
        val model: PlayerModel = renderer.model
        model.setupAnim(renderState)

        val camera = client.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val poseStack = context.matrices()
        val consumers = context.consumers()

        poseStack.pushPose()
        poseStack.translate(
            renderState.x - cameraPos.x,
            renderState.y - cameraPos.y,
            renderState.z - cameraPos.z,
        )
        applyEntityModelTransform(poseStack, renderState)
        model.head.translateAndRotate(poseStack)

        drawNimb(
            poseStack = poseStack,
            consumers = consumers,
            player = player,
        )

        poseStack.popPose()
    }

    private fun drawNimb(
        poseStack: PoseStack,
        consumers: MultiBufferSource,
        player: LocalPlayer,
    ) {
        val pose = poseStack.last()
        val y = baseRingY + (NimbModule.height() * sliderHeightScale)
        val innerRadius = maxOf(NimbModule.innerRadius(), player.getBbWidth() * 0.50f)
        val outerRadius = innerRadius + NimbModule.ringThickness()

        val baseColor = NimbModule.color()
        val gradientColor = NimbModule.gradientColor()
        val animationPhase = ((player.tickCount + Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)) * NimbModule.rotationSpeed() * 0.04f)
        val fillAlpha = 150
        val edgeAlpha = 235

        val innerRing = ringVertices(innerRadius)
        val outerRing = ringVertices(outerRadius)

        for (index in 0 until ringSegments) {
            val next = (index + 1) % ringSegments
            val innerA = innerRing[index]
            val innerB = innerRing[next]
            val outerA = outerRing[index]
            val outerB = outerRing[next]
            val angleA = ((PI * 2.0) * index.toDouble() / ringSegments.toDouble()).toFloat()
            val angleB = ((PI * 2.0) * next.toDouble() / ringSegments.toDouble()).toFloat()
            val fillColorA = gradientColor(baseColor, gradientColor, angleA, animationPhase, fillAlpha)
            val fillColorB = gradientColor(baseColor, gradientColor, angleB, animationPhase, fillAlpha)
            val edgeColorA = gradientColor(baseColor, gradientColor, angleA, animationPhase, edgeAlpha)
            val edgeColorB = gradientColor(baseColor, gradientColor, angleB, animationPhase, edgeAlpha)

            val fillConsumer = consumers.getBuffer(RenderTypes.debugTriangleFan())
            fillConsumer.addVertex(pose, outerA.x, y, outerA.z).setColor(fillColorA)
            fillConsumer.addVertex(pose, outerB.x, y, outerB.z).setColor(fillColorB)
            fillConsumer.addVertex(pose, innerB.x, y, innerB.z).setColor(fillColorB)
            fillConsumer.addVertex(pose, innerA.x, y, innerA.z).setColor(fillColorA)
            flushTriangleFan(consumers)
        }

        val lineConsumer = consumers.getBuffer(RenderTypes.linesTranslucent())
        for (index in 0 until ringSegments) {
            val next = (index + 1) % ringSegments
            val innerA = innerRing[index]
            val innerB = innerRing[next]
            val outerA = outerRing[index]
            val outerB = outerRing[next]
            val angleA = ((PI * 2.0) * index.toDouble() / ringSegments.toDouble()).toFloat()
            val angleB = ((PI * 2.0) * next.toDouble() / ringSegments.toDouble()).toFloat()
            val edgeColorA = gradientColor(baseColor, gradientColor, angleA, animationPhase, edgeAlpha)
            val edgeColorB = gradientColor(baseColor, gradientColor, angleB, animationPhase, edgeAlpha)

            line(lineConsumer, pose, outerA.x, y, outerA.z, outerB.x, y, outerB.z, edgeColorA, edgeColorB)
            line(lineConsumer, pose, innerA.x, y, innerA.z, innerB.x, y, innerB.z, edgeColorA, edgeColorB)
        }
    }

    private fun ringVertices(radius: Float): List<Vector3f> {
        return buildList(ringSegments) {
            for (index in 0 until ringSegments) {
                val angle = ((PI * 2.0) * index.toDouble() / ringSegments.toDouble()).toFloat()
                add(Vector3f(cos(angle) * radius, 0f, sin(angle) * radius))
            }
        }
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
        startColor: Int,
        endColor: Int,
    ) {
        val normalX = x2 - x1
        val normalY = y2 - y1
        val normalZ = z2 - z1
        consumer.addVertex(pose, x1, y1, z1).setColor(startColor).setLineWidth(1.75f).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(pose, x2, y2, z2).setColor(endColor).setLineWidth(1.75f).setNormal(pose, normalX, normalY, normalZ)
    }

    private fun applyEntityModelTransform(
        poseStack: PoseStack,
        renderState: AvatarRenderState,
    ) {
        val modelScale = if (renderState.scale == 0f) 1f else renderState.scale
        poseStack.scale(modelScale, modelScale, modelScale)
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - renderState.bodyRot))
        if (renderState.isFallFlying) {
            val flightScale = renderState.fallFlyingScale()
            if (!renderState.isAutoSpinAttack) {
                poseStack.mulPose(Axis.XP.rotationDegrees(flightScale * (-90f - renderState.xRot)))
            }
            if (renderState.shouldApplyFlyingYRot) {
                poseStack.mulPose(Axis.YP.rotation(renderState.flyingYRot))
            }
        } else if (renderState.swimAmount > 0f) {
            val targetX = if (renderState.isInWater) {
                -90f - renderState.xRot
            } else {
                -90f
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(renderState.swimAmount * targetX))
            if (renderState.isVisuallySwimming) {
                poseStack.translate(0f, -1f, 0.3f)
            }
        }
        poseStack.scale(-1f, -1f, 1f)
        poseStack.translate(0f, modelRootOffsetY, 0f)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
    }

    private fun flushTriangleFan(consumers: MultiBufferSource) {
        (consumers as? MultiBufferSource.BufferSource)?.endBatch(RenderTypes.debugTriangleFan())
    }

    private fun gradientColor(
        primaryColor: Int,
        secondaryColor: Int,
        angle: Float,
        phase: Float,
        alpha: Int,
    ): Int {
        if (!NimbModule.gradientEnabled()) {
            return withAlpha(primaryColor, alpha)
        }
        val mix = (0.5f + (0.5f * sin(angle + phase))).coerceIn(0f, 1f)
        return withAlpha(blendColor(primaryColor, secondaryColor, mix), alpha)
    }

    private fun blendColor(startColor: Int, endColor: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val startR = (startColor shr 16) and 0xFF
        val startG = (startColor shr 8) and 0xFF
        val startB = startColor and 0xFF
        val endR = (endColor shr 16) and 0xFF
        val endG = (endColor shr 8) and 0xFF
        val endB = endColor and 0xFF
        val r = (startR + ((endR - startR) * t)).roundToInt().coerceIn(0, 255)
        val g = (startG + ((endG - startG) * t)).roundToInt().coerceIn(0, 255)
        val b = (startB + ((endB - startB) * t)).roundToInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
