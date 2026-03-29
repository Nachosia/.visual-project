package com.visualproject.client.visuals.chinahat

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object ChinaHatRenderer {
    private data class FillQuadSpec(
        val sortDistanceSq: Float,
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
        val color: Int,
    )

    private const val roundSegments = 48
    private const val roundFillBands = 28
    private const val rhombusFillBands = 18
    private const val brimY = -(6f / 16f)
    private const val modelRootOffsetY = -1.501f
    private const val brimRadius = 0.50f
    private const val extraWidth = 0.10f
    private const val gradientPhaseScale = 0.0064f
    private const val fullTurnRadians = (PI * 2.0).toFloat()
    private val fillBufferSource = net.minecraft.client.renderer.MultiBufferSource.immediate(ByteBufferBuilder(262_144))
    private val lineBufferSource = net.minecraft.client.renderer.MultiBufferSource.immediate(ByteBufferBuilder(131_072))
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        WorldRenderEvents.END_MAIN.register(WorldRenderEvents.EndMain { context ->
            render(context)
        })
    }

    private fun render(context: WorldRenderContext) {
        if (!ChinaHatModule.isActive()) return

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
        val gradientPhaseTurns = ((player.tickCount + partialTick) * ChinaHatModule.rotationSpeed() * gradientPhaseScale)

        val camera = client.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val poseStack = context.matrices()
        val fillRenderType = RenderTypes.entityTranslucentEmissive(ChinaHatTextureRegistry.resolveTexture(client))
        val fillConsumer = fillBufferSource.getBuffer(fillRenderType)
        val lineRenderType = RenderTypes.linesTranslucent()
        val lineConsumer = lineBufferSource.getBuffer(lineRenderType)

        poseStack.pushPose()
        poseStack.translate(
            renderState.x - cameraPos.x,
            renderState.y - cameraPos.y,
            renderState.z - cameraPos.z,
        )
        if (ChinaHatModule.shape() == ChinaHatModule.Shape.RHOMBUS && ChinaHatModule.rhombusHitboxLockEnabled()) {
            drawHitboxLockedRhombus(
                poseStack = poseStack,
                fillConsumer = fillConsumer,
                lineConsumer = lineConsumer,
                player = player,
                gradientPhaseTurns = gradientPhaseTurns,
            )
        } else {
            applyEntityModelTransform(poseStack, renderState)
            model.head.translateAndRotate(poseStack)

            drawHat(
                poseStack = poseStack,
                fillConsumer = fillConsumer,
                lineConsumer = lineConsumer,
                player = player,
                brimOffsetY = brimY - ChinaHatModule.hitboxOffset(),
                cameraLocal = cameraVectorInCurrentLocalHatSpace(poseStack),
                gradientPhaseTurns = gradientPhaseTurns,
            )
        }
        poseStack.popPose()
        fillBufferSource.endBatch(fillRenderType)
        lineBufferSource.endBatch(lineRenderType)
    }

    private fun drawHat(
        poseStack: PoseStack,
        fillConsumer: VertexConsumer,
        lineConsumer: VertexConsumer,
        player: LocalPlayer,
        brimOffsetY: Float,
        cameraLocal: Vector3f,
        gradientPhaseTurns: Float,
    ) {
        val pose = poseStack.last()
        val baseY = brimOffsetY
        val apexY = brimOffsetY - ChinaHatModule.height()
        val radius = max(player.getBbWidth() * 0.86f, brimRadius) + extraWidth

        val baseColor = ChinaHatModule.color()
        val secondaryColor = ChinaHatModule.gradientColor()
        val gradientEnabled = ChinaHatModule.gradientEnabled()
        val fillAlpha = ((ChinaHatModule.opacity() * 255.0f) * 1.40f).toInt().coerceIn(56, 255)
        val outlineAlpha = ((fillAlpha * 0.46f).toInt()).coerceIn(28, 132)

        when (ChinaHatModule.shape()) {
            ChinaHatModule.Shape.ROUND -> drawRoundHat(
                pose = pose,
                fillConsumer = fillConsumer,
                lineConsumer = lineConsumer,
                baseY = baseY,
                apexY = apexY,
                radius = radius,
                cameraLocal = cameraLocal,
                primaryColor = baseColor,
                secondaryColor = secondaryColor,
                gradientEnabled = gradientEnabled,
                gradientPhaseTurns = gradientPhaseTurns,
                fillAlpha = fillAlpha,
                outlineAlpha = outlineAlpha,
            )
            ChinaHatModule.Shape.RHOMBUS -> drawRhombusHat(
                pose = pose,
                fillConsumer = fillConsumer,
                lineConsumer = lineConsumer,
                centerY = baseY,
                upperApexY = apexY,
                lowerApexY = (baseY + ChinaHatModule.height()),
                radius = radius,
                cameraLocal = cameraLocal,
                primaryColor = baseColor,
                secondaryColor = secondaryColor,
                gradientEnabled = gradientEnabled,
                gradientPhaseTurns = gradientPhaseTurns,
                fillAlpha = fillAlpha,
                outlineAlpha = outlineAlpha,
            )
        }
    }

    private fun drawHitboxLockedRhombus(
        poseStack: PoseStack,
        fillConsumer: VertexConsumer,
        lineConsumer: VertexConsumer,
        player: LocalPlayer,
        gradientPhaseTurns: Float,
    ) {
        val pose = poseStack.last()
        val centerY = player.getBbHeight() + ChinaHatModule.hitboxOffset()
        val halfHeight = ChinaHatModule.height()
        val radius = max(player.getBbWidth() * 0.86f, brimRadius) + extraWidth

        val baseColor = ChinaHatModule.color()
        val secondaryColor = ChinaHatModule.gradientColor()
        val gradientEnabled = ChinaHatModule.gradientEnabled()
        val fillAlpha = ((ChinaHatModule.opacity() * 255.0f) * 1.40f).toInt().coerceIn(56, 255)
        val outlineAlpha = ((fillAlpha * 0.46f).toInt()).coerceIn(28, 132)

        drawRhombusHat(
            pose = pose,
            fillConsumer = fillConsumer,
            lineConsumer = lineConsumer,
            centerY = centerY,
            upperApexY = centerY - halfHeight,
            lowerApexY = centerY + halfHeight,
            radius = radius,
            cameraLocal = cameraVectorInCurrentLocalHatSpace(poseStack),
            primaryColor = baseColor,
            secondaryColor = secondaryColor,
            gradientEnabled = gradientEnabled,
            gradientPhaseTurns = gradientPhaseTurns,
            fillAlpha = fillAlpha,
            outlineAlpha = outlineAlpha,
        )
    }

    private fun drawRoundHat(
        pose: PoseStack.Pose,
        fillConsumer: VertexConsumer,
        lineConsumer: VertexConsumer,
        baseY: Float,
        apexY: Float,
        radius: Float,
        cameraLocal: Vector3f,
        primaryColor: Int,
        secondaryColor: Int,
        gradientEnabled: Boolean,
        gradientPhaseTurns: Float,
        fillAlpha: Int,
        outlineAlpha: Int,
    ) {
        val rimVertices = rimVertices(roundSegments, radius)
        val apexOutlineColor = angularHatColor(primaryColor, secondaryColor, 0f, gradientPhaseTurns, outlineAlpha, gradientEnabled)

        drawConeSurface(
            pose = pose,
            fillConsumer = fillConsumer,
            apexY = apexY,
            rimY = baseY,
            radius = radius,
            segments = rimVertices.size,
            gradientPhaseTurns = gradientPhaseTurns,
            alpha = fillAlpha,
        )

        if (!ChinaHatModule.outlineEnabled() || rimVertices.isEmpty()) return

        val horizontalX = cameraLocal.x
        val horizontalZ = cameraLocal.z
        val horizontalLength = sqrt((horizontalX * horizontalX) + (horizontalZ * horizontalZ))
        val apexToCameraY = cameraLocal.y - apexY
        val coneHalfAngle = atan2(radius, abs(baseY - apexY))
        val viewAngleFromAxis = atan2(horizontalLength, abs(apexToCameraY))
        val showSideOutline = viewAngleFromAxis > (coneHalfAngle + 0.06f)

        if (horizontalLength <= 0.001f || !showSideOutline) {
            for (index in rimVertices.indices) {
                val current = rimVertices[index]
                val next = rimVertices[(index + 1) % rimVertices.size]
                val currentColor = angularHatColor(primaryColor, secondaryColor, angleForIndex(index, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
                val nextColor = angularHatColor(primaryColor, secondaryColor, angleForIndex((index + 1) % rimVertices.size, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
                line(lineConsumer, pose, current.x, baseY, current.z, next.x, baseY, next.z, currentColor, nextColor)
            }
            return
        }

        val cameraDirX = horizontalX / horizontalLength
        val cameraDirZ = horizontalZ / horizontalLength
        val tangentDirX = -cameraDirZ
        val tangentDirZ = cameraDirX

        for (index in rimVertices.indices) {
            val current = rimVertices[index]
            val next = rimVertices[(index + 1) % rimVertices.size]
            val midpointX = (current.x + next.x) * 0.5f
            val midpointZ = (current.z + next.z) * 0.5f
            val visible = (midpointX * cameraDirX) + (midpointZ * cameraDirZ) >= 0f
            if (visible) {
                val currentColor = angularHatColor(primaryColor, secondaryColor, angleForIndex(index, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
                val nextColor = angularHatColor(primaryColor, secondaryColor, angleForIndex((index + 1) % rimVertices.size, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
                line(lineConsumer, pose, current.x, baseY, current.z, next.x, baseY, next.z, currentColor, nextColor)
            }
        }

        val leftVertex = rimVertices.maxByOrNull { (it.x * tangentDirX) + (it.z * tangentDirZ) } ?: return
        val rightVertex = rimVertices.minByOrNull { (it.x * tangentDirX) + (it.z * tangentDirZ) } ?: return
        val leftAngle = angleForIndex(rimVertices.indexOf(leftVertex), rimVertices.size)
        val rightAngle = angleForIndex(rimVertices.indexOf(rightVertex), rimVertices.size)
        line(lineConsumer, pose, 0f, apexY, 0f, leftVertex.x, baseY, leftVertex.z, apexOutlineColor, angularHatColor(primaryColor, secondaryColor, leftAngle, gradientPhaseTurns, outlineAlpha, gradientEnabled))
        line(lineConsumer, pose, 0f, apexY, 0f, rightVertex.x, baseY, rightVertex.z, apexOutlineColor, angularHatColor(primaryColor, secondaryColor, rightAngle, gradientPhaseTurns, outlineAlpha, gradientEnabled))
    }

    private fun drawRhombusHat(
        pose: PoseStack.Pose,
        fillConsumer: VertexConsumer,
        lineConsumer: VertexConsumer,
        centerY: Float,
        upperApexY: Float,
        lowerApexY: Float,
        radius: Float,
        cameraLocal: Vector3f,
        primaryColor: Int,
        secondaryColor: Int,
        gradientEnabled: Boolean,
        gradientPhaseTurns: Float,
        fillAlpha: Int,
        outlineAlpha: Int,
    ) {
        val rimVertices = rimVertices(ChinaHatModule.sides(), radius)
        if (rimVertices.isEmpty()) return
        val fillColor = withAlpha(0x00FFFFFF, fillAlpha)
        val quads = ArrayList<FillQuadSpec>(rimVertices.size * rhombusFillBands * 2)
        appendRhombusSurfaceQuads(
            quads = quads,
            apexY = upperApexY,
            rimY = centerY,
            rimVertices = rimVertices,
            cameraLocal = cameraLocal,
            color = fillColor,
        )
        appendRhombusSurfaceQuads(
            quads = quads,
            apexY = lowerApexY,
            rimY = centerY,
            rimVertices = rimVertices,
            cameraLocal = cameraLocal,
            color = fillColor,
        )
        quads.sortByDescending { it.sortDistanceSq }
        quads.forEach { quad ->
            fillQuad(
                consumer = fillConsumer,
                pose = pose,
                radius = radius,
                gradientPhaseTurns = gradientPhaseTurns,
                ax = quad.ax,
                ay = quad.ay,
                az = quad.az,
                aColor = quad.color,
                bx = quad.bx,
                by = quad.by,
                bz = quad.bz,
                bColor = quad.color,
                cx = quad.cx,
                cy = quad.cy,
                cz = quad.cz,
                cColor = quad.color,
                dx = quad.dx,
                dy = quad.dy,
                dz = quad.dz,
                dColor = quad.color,
            )
        }

        if (!ChinaHatModule.outlineEnabled()) return
        val apexOutlineColor = angularHatColor(primaryColor, secondaryColor, 0f, gradientPhaseTurns, outlineAlpha, gradientEnabled)
        for (index in rimVertices.indices) {
            val current = rimVertices[index]
            val next = rimVertices[(index + 1) % rimVertices.size]
            val currentColor = angularHatColor(primaryColor, secondaryColor, angleForIndex(index, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
            val nextColor = angularHatColor(primaryColor, secondaryColor, angleForIndex((index + 1) % rimVertices.size, rimVertices.size), gradientPhaseTurns, outlineAlpha, gradientEnabled)
            line(lineConsumer, pose, current.x, centerY, current.z, next.x, centerY, next.z, currentColor, nextColor)
            line(lineConsumer, pose, 0f, upperApexY, 0f, current.x, centerY, current.z, apexOutlineColor, currentColor)
            line(lineConsumer, pose, 0f, lowerApexY, 0f, current.x, centerY, current.z, apexOutlineColor, currentColor)
        }
    }

    private fun appendRhombusSurfaceQuads(
        quads: MutableList<FillQuadSpec>,
        apexY: Float,
        rimY: Float,
        rimVertices: List<Vector3f>,
        cameraLocal: Vector3f,
        color: Int,
    ) {
        for (index in rimVertices.indices) {
            val next = (index + 1) % rimVertices.size
            val current = rimVertices[index]
            val following = rimVertices[next]
            appendTriangleBandsAsQuads(
                quads = quads,
                apexX = 0f,
                apexY = apexY,
                apexZ = 0f,
                leftX = current.x,
                leftY = rimY,
                leftZ = current.z,
                rightX = following.x,
                rightY = rimY,
                rightZ = following.z,
                bands = rhombusFillBands,
                cameraLocal = cameraLocal,
                color = color,
            )
        }
    }

    private fun rimVertices(segments: Int, radius: Float): List<Vector3f> {
        val clampedSegments = segments.coerceAtLeast(2)
        return buildList(clampedSegments) {
            for (index in 0 until clampedSegments) {
                val angle = angleForIndex(index, clampedSegments)
                val x = cos(angle) * radius
                val z = sin(angle) * radius
                add(Vector3f(x, 0f, z))
            }
        }
    }

    private fun drawConeSurface(
        pose: PoseStack.Pose,
        fillConsumer: VertexConsumer,
        apexY: Float,
        rimY: Float,
        radius: Float,
        segments: Int,
        gradientPhaseTurns: Float,
        alpha: Int,
    ) {
        val clampedSegments = segments.coerceAtLeast(3)
        val fillColor = withAlpha(0x00FFFFFF, alpha)
        for (band in 0 until roundFillBands) {
            val startProgress = band.toFloat() / roundFillBands.toFloat()
            val endProgress = (band + 1).toFloat() / roundFillBands.toFloat()
            val startRadius = radius * startProgress
            val endRadius = radius * endProgress
            val startY = lerp(apexY, rimY, startProgress)
            val endY = lerp(apexY, rimY, endProgress)

            for (index in 0 until clampedSegments) {
                val next = (index + 1) % clampedSegments
                val angleA = angleForIndex(index, clampedSegments)
                val angleB = angleForIndex(next, clampedSegments)
                val upperA = Vector3f(cos(angleA) * startRadius, startY, sin(angleA) * startRadius)
                val upperB = Vector3f(cos(angleB) * startRadius, startY, sin(angleB) * startRadius)
                val lowerA = Vector3f(cos(angleA) * endRadius, endY, sin(angleA) * endRadius)
                val lowerB = Vector3f(cos(angleB) * endRadius, endY, sin(angleB) * endRadius)
                fillQuad(
                    consumer = fillConsumer,
                    pose = pose,
                    radius = radius,
                    gradientPhaseTurns = gradientPhaseTurns,
                    ax = upperA.x,
                    ay = upperA.y,
                    az = upperA.z,
                    aColor = fillColor,
                    bx = upperB.x,
                    by = upperB.y,
                    bz = upperB.z,
                    bColor = fillColor,
                    cx = lowerB.x,
                    cy = lowerB.y,
                    cz = lowerB.z,
                    cColor = fillColor,
                    dx = lowerA.x,
                    dy = lowerA.y,
                    dz = lowerA.z,
                    dColor = fillColor,
                )
            }
        }
    }

    private fun appendTriangleBandsAsQuads(
        quads: MutableList<FillQuadSpec>,
        apexX: Float,
        apexY: Float,
        apexZ: Float,
        leftX: Float,
        leftY: Float,
        leftZ: Float,
        rightX: Float,
        rightY: Float,
        rightZ: Float,
        bands: Int,
        cameraLocal: Vector3f,
        color: Int,
    ) {
        val clampedBands = bands.coerceAtLeast(1)
        for (band in 0 until clampedBands) {
            val startProgress = band.toFloat() / clampedBands.toFloat()
            val endProgress = (band + 1).toFloat() / clampedBands.toFloat()

            val upperLeftX = lerp(apexX, leftX, startProgress)
            val upperLeftY = lerp(apexY, leftY, startProgress)
            val upperLeftZ = lerp(apexZ, leftZ, startProgress)
            val upperRightX = lerp(apexX, rightX, startProgress)
            val upperRightY = lerp(apexY, rightY, startProgress)
            val upperRightZ = lerp(apexZ, rightZ, startProgress)
            val lowerLeftX = lerp(apexX, leftX, endProgress)
            val lowerLeftY = lerp(apexY, leftY, endProgress)
            val lowerLeftZ = lerp(apexZ, leftZ, endProgress)
            val lowerRightX = lerp(apexX, rightX, endProgress)
            val lowerRightY = lerp(apexY, rightY, endProgress)
            val lowerRightZ = lerp(apexZ, rightZ, endProgress)
            val centerX = (upperLeftX + upperRightX + lowerRightX + lowerLeftX) * 0.25f
            val centerY = (upperLeftY + upperRightY + lowerRightY + lowerLeftY) * 0.25f
            val centerZ = (upperLeftZ + upperRightZ + lowerRightZ + lowerLeftZ) * 0.25f
            val dx = centerX - cameraLocal.x
            val dy = centerY - cameraLocal.y
            val dz = centerZ - cameraLocal.z
            quads += FillQuadSpec(
                sortDistanceSq = (dx * dx) + (dy * dy) + (dz * dz),
                ax = upperLeftX,
                ay = upperLeftY,
                az = upperLeftZ,
                bx = upperRightX,
                by = upperRightY,
                bz = upperRightZ,
                cx = lowerRightX,
                cy = lowerRightY,
                cz = lowerRightZ,
                dx = lowerLeftX,
                dy = lowerLeftY,
                dz = lowerLeftZ,
                color = color,
            )
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
        color: Int,
    ) {
        line(consumer, pose, x1, y1, z1, x2, y2, z2, color, color)
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

    private fun fillTriangle(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        radius: Float,
        gradientPhaseTurns: Float,
        ax: Float,
        ay: Float,
        az: Float,
        aColor: Int,
        bx: Float,
        by: Float,
        bz: Float,
        bColor: Int,
        cx: Float,
        cy: Float,
        cz: Float,
        cColor: Int,
    ) {
        fillVertex(consumer, pose, radius, gradientPhaseTurns, ax, ay, az, aColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, bx, by, bz, bColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, cx, cy, cz, cColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, cx, cy, cz, cColor)
    }

    private fun fillQuad(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        radius: Float,
        gradientPhaseTurns: Float,
        ax: Float,
        ay: Float,
        az: Float,
        aColor: Int,
        bx: Float,
        by: Float,
        bz: Float,
        bColor: Int,
        cx: Float,
        cy: Float,
        cz: Float,
        cColor: Int,
        dx: Float,
        dy: Float,
        dz: Float,
        dColor: Int,
    ) {
        fillVertex(consumer, pose, radius, gradientPhaseTurns, ax, ay, az, aColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, bx, by, bz, bColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, cx, cy, cz, cColor)
        fillVertex(consumer, pose, radius, gradientPhaseTurns, dx, dy, dz, dColor)
    }

    private fun fillVertex(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        radius: Float,
        gradientPhaseTurns: Float,
        x: Float,
        y: Float,
        z: Float,
        color: Int,
    ) {
        val (u, v) = projectedUv(x, z, radius, gradientPhaseTurns)
        consumer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(0)
            .setLight(0x00F000F0)
            .setNormal(pose, 0f, 1f, 0f)
    }

    private fun projectedUv(
        x: Float,
        z: Float,
        radius: Float,
        gradientPhaseTurns: Float,
    ): Pair<Float, Float> {
        val phase = gradientPhaseTurns * fullTurnRadians
        val cosPhase = cos(phase)
        val sinPhase = sin(phase)
        val rotatedX = (x * cosPhase) - (z * sinPhase)
        val rotatedZ = (x * sinPhase) + (z * cosPhase)
        val safeRadius = radius.coerceAtLeast(0.0001f)
        val u = (0.5f + ((rotatedX / safeRadius) * 0.5f)).coerceIn(0f, 1f)
        val v = (0.5f + ((rotatedZ / safeRadius) * 0.5f)).coerceIn(0f, 1f)
        return u to v
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

    private fun cameraVectorInCurrentLocalHatSpace(
        poseStack: PoseStack,
    ): Vector3f {
        return Matrix4f(poseStack.last().pose()).invert().transformPosition(0f, 0f, 0f, Vector3f())
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
    }

    private fun angularHatColor(
        primaryColor: Int,
        secondaryColor: Int,
        angleRadians: Float,
        phaseTurns: Float,
        alpha: Int,
        gradientEnabled: Boolean,
    ): Int {
        if (!gradientEnabled) {
            return withAlpha(primaryColor, alpha)
        }
        return withAlpha(blendColor(primaryColor, secondaryColor, angularGradientMix(angleRadians, phaseTurns)), alpha)
    }

    private fun angularGradientMix(angleRadians: Float, phaseTurns: Float): Float {
        return (0.5f + (0.5f * sin(angleRadians + (phaseTurns * fullTurnRadians)))).coerceIn(0f, 1f)
    }

    private fun angleForIndex(index: Int, segments: Int): Float {
        val wrappedIndex = ((index % segments) + segments) % segments
        return (fullTurnRadians * wrappedIndex.toFloat()) / segments.toFloat()
    }

    private fun midAngle(first: Float, second: Float): Float {
        return atan2(sin(first) + sin(second), cos(first) + cos(second))
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + ((end - start) * progress)
    }

    private fun blendColor(startColor: Int, endColor: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val startR = (startColor shr 16) and 0xFF
        val startG = (startColor shr 8) and 0xFF
        val startB = startColor and 0xFF
        val endR = (endColor shr 16) and 0xFF
        val endG = (endColor shr 8) and 0xFF
        val endB = endColor and 0xFF
        val r = (startR + ((endR - startR) * t)).toInt().coerceIn(0, 255)
        val g = (startG + ((endG - startG) * t)).toInt().coerceIn(0, 255)
        val b = (startB + ((endB - startB) * t)).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
