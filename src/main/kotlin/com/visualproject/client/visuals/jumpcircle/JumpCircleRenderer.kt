package com.visualproject.client.visuals.jumpcircle

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.visualproject.client.render.shadertoy.ShadertoyFrameProvider
import com.visualproject.client.render.shadertoy.ShadertoyMaskedWorldRenderer
import com.visualproject.client.render.shadertoy.ShadertoyProgramRegistry
import com.visualproject.client.visuals.worldparticles.WorldParticleTextureRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.levelgen.Heightmap
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object JumpCircleRenderer {
    private data class CircleEffect(
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val maxRadius: Float,
        val speed: Float,
        val color: Int,
        var ageTicks: Int = 0,
    ) {
        val lifetimeTicks: Int = max(1, ceil((maxRadius / speed.coerceAtLeast(0.10f)) * 20f).toInt())
    }

    private data class BlockWaveEffect(
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val maxRadius: Float,
        val speed: Float,
        val thickness: Float,
        val outline: Boolean,
        val lineThickness: Float,
        val fill: Boolean,
        val fillType: JumpCircleModule.FillType,
        val fillAlpha: Float,
        val outlineColor: Int,
        val fillColor: Int,
        val shaderType: JumpCircleModule.ShaderType,
        val shaderSpeed: Float,
        val shaderAlpha: Float,
        var ageTicks: Int = 0,
        val surfaceCache: LinkedHashMap<Long, SurfaceCell> = LinkedHashMap(),
        var sampledOuterRadius: Float = -1f,
    ) {
        val lifetimeTicks: Int = max(1, ceil((maxRadius / speed.coerceAtLeast(0.10f)) * 20f).toInt())
    }

    private data class WaveQuad(
        val textureId: Identifier,
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
        val au: Float,
        val av: Float,
        val bu: Float,
        val bv: Float,
        val cu: Float,
        val cv: Float,
        val du: Float,
        val dv: Float,
        val aColor: Int,
        val bColor: Int,
        val cColor: Int,
        val dColor: Int,
    )

    private data class SurfaceCell(
        val blockX: Int,
        val blockZ: Int,
        val renderY: Double,
        val minRadialDistance: Float,
        val maxRadialDistance: Float,
    )

    private data class ShaderMaskBatchKey(
        val shaderType: JumpCircleModule.ShaderType,
        val shaderSpeedBits: Int,
        val shaderAlphaBits: Int,
    )

    private data class SurfaceColumnKey(
        val dimensionId: Identifier,
        val blockX: Int,
        val blockZ: Int,
        val referenceY: Int,
    )

    private data class JumpParticle(
        val texture: WorldParticleTextureRegistry.ParticleTexture,
        val physics: JumpCircleModule.ParticlePhysics,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        var prevX: Double,
        var prevY: Double,
        var prevZ: Double,
        var x: Double,
        var y: Double,
        var z: Double,
        var velocityX: Double,
        var velocityY: Double,
        var velocityZ: Double,
        var age: Int,
        val lifetime: Int,
        val size: Float,
        var prevRotation: Float,
        var rotation: Float,
        val rotationVelocity: Float,
    )

    private data class RenderedParticle(
        val texture: WorldParticleTextureRegistry.ParticleTexture,
        val x: Double,
        val y: Double,
        val z: Double,
        val halfSize: Float,
        val color: Int,
        val rotationRadians: Float,
        val distanceSq: Double,
    )

    private const val fullBrightLight = 0x00F000F0
    private const val particleWorldHalfSizeScale = 0.125f
    private const val circleYOffset = 0.035
    private const val waveYOffset = 0.045
    private const val waveSurfaceScanDepth = 16
    private const val waveSurfaceMaxRiseBlocks = 2

    private val fillBufferSource = MultiBufferSource.immediate(ByteBufferBuilder(524_288))
    private val lineBufferSource = MultiBufferSource.immediate(ByteBufferBuilder(262_144))
    private val particleBufferSource = MultiBufferSource.immediate(ByteBufferBuilder(524_288))
    private val random = Random(System.nanoTime())
    private val worldSurfaceCache = object : LinkedHashMap<SurfaceColumnKey, Double?>(8_192, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SurfaceColumnKey, Double?>?): Boolean {
            return size > 8_192
        }
    }

    private val circleEffects = ArrayList<CircleEffect>()
    private val blockWaveEffects = ArrayList<BlockWaveEffect>()
    private val particles = ArrayList<JumpParticle>()

    private var initialized = false
    private var wasOnGround = false

    fun initialize() {
        if (initialized) return
        initialized = true

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            tick(client)
        })

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldRenderEvents.BeforeTranslucent { context ->
            render(context)
        })
    }

    private fun tick(client: Minecraft) {
        val world = client.level
        val player = client.player
        if (!JumpCircleModule.isActive() || world == null || player == null) {
            clearRuntime()
            wasOnGround = false
            return
        }

        val currentOnGround = player.onGround()
        if (shouldTriggerTakeoff(player, currentOnGround)) {
            spawnTakeoffEffects(client, world)
        }
        wasOnGround = currentOnGround

        updateCircles()
        updateWaves()
        updateParticles(world)
    }

    private fun shouldTriggerTakeoff(player: LocalPlayer, currentOnGround: Boolean): Boolean {
        if (!wasOnGround || currentOnGround) return false
        if (player.deltaMovement.y <= 0.0) return false
        if (player.abilities.flying) return false
        if (player.isPassenger) return false
        if (player.isSwimming) return false
        if (player.onClimbable()) return false
        if (player.isFallFlying) return false
        return true
    }

    private fun spawnTakeoffEffects(client: Minecraft, world: ClientLevel) {
        val player = client.player ?: return
        val anchorX = player.x
        val anchorZ = player.z
        val anchorY = sampleSurfaceY(world, anchorX, anchorZ) + circleYOffset
        when (JumpCircleModule.mode()) {
            JumpCircleModule.Mode.CIRCLE_ONLY -> {
                circleEffects += createCircleEffect(anchorX, anchorY, anchorZ)
            }
            JumpCircleModule.Mode.PARTICLES_ONLY -> {
                spawnParticleBurst(client, anchorX, anchorY + 0.06, anchorZ)
            }
            JumpCircleModule.Mode.CIRCLE_AND_PARTICLES -> {
                circleEffects += createCircleEffect(anchorX, anchorY, anchorZ)
                spawnParticleBurst(client, anchorX, anchorY + 0.06, anchorZ)
            }
            JumpCircleModule.Mode.BLOCK_WAVE -> {
                blockWaveEffects += BlockWaveEffect(
                    centerX = anchorX,
                    centerY = sampleSurfaceY(world, anchorX, anchorZ) + waveYOffset,
                    centerZ = anchorZ,
                    maxRadius = JumpCircleModule.waveRadius(),
                    speed = JumpCircleModule.waveSpeed(),
                    thickness = JumpCircleModule.waveThickness(),
                    outline = JumpCircleModule.waveOutline(),
                    lineThickness = JumpCircleModule.waveLineThickness(),
                    fill = JumpCircleModule.waveFill(),
                    fillType = JumpCircleModule.waveFillType(),
                    fillAlpha = JumpCircleModule.waveFillAlpha(),
                    outlineColor = JumpCircleModule.waveColor(),
                    fillColor = JumpCircleModule.waveFillColor(),
                    shaderType = JumpCircleModule.waveShaderType(),
                    shaderSpeed = JumpCircleModule.waveShaderSpeed(),
                    shaderAlpha = JumpCircleModule.waveShaderAlpha(),
                )
            }
        }
    }

    private fun createCircleEffect(anchorX: Double, anchorY: Double, anchorZ: Double): CircleEffect {
        return CircleEffect(
            centerX = anchorX,
            centerY = anchorY,
            centerZ = anchorZ,
            maxRadius = JumpCircleModule.circleRadius(),
            speed = JumpCircleModule.circleSpeed(),
            color = JumpCircleModule.circleColor(),
        )
    }

    private fun spawnParticleBurst(client: Minecraft, centerX: Double, centerY: Double, centerZ: Double) {
        val texture = WorldParticleTextureRegistry.resolveTexture(
            client = client,
            type = JumpCircleModule.particleType(),
            tintColor = JumpCircleModule.particleColor(),
            customFile = JumpCircleModule.particleCustomFile(),
        ) ?: return
        val physics = JumpCircleModule.particlePhysics()
        val spread = JumpCircleModule.particleSpread().toDouble()
        val lifetime = JumpCircleModule.particleLifetime()
        val size = JumpCircleModule.particleSize()

        repeat(JumpCircleModule.particleCount()) {
            val angle = random.nextDouble(0.0, PI * 2.0)
            val upward = spread * random.nextDouble(0.65, 1.15)
            val horizontal = spread * random.nextDouble(0.45, 1.05)
            val initialRotation = random.nextFloat() * (PI.toFloat() * 2f)
            particles += JumpParticle(
                texture = texture,
                physics = physics,
                centerX = centerX,
                centerY = centerY,
                centerZ = centerZ,
                prevX = centerX,
                prevY = centerY,
                prevZ = centerZ,
                x = centerX,
                y = centerY,
                z = centerZ,
                velocityX = cos(angle) * horizontal,
                velocityY = upward,
                velocityZ = sin(angle) * horizontal,
                age = 0,
                lifetime = lifetime,
                size = size,
                prevRotation = initialRotation,
                rotation = initialRotation,
                rotationVelocity = ((random.nextFloat() * 2f) - 1f) * 0.10f,
            )
        }
    }
    private fun updateCircles() {
        val iterator = circleEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.ageTicks++
            if (effect.ageTicks > effect.lifetimeTicks) {
                iterator.remove()
            }
        }
    }

    private fun updateWaves() {
        val iterator = blockWaveEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.ageTicks++
            if (effect.ageTicks > effect.lifetimeTicks) {
                iterator.remove()
            }
        }
    }

    private fun updateParticles(world: ClientLevel) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.prevX = particle.x
            particle.prevY = particle.y
            particle.prevZ = particle.z
            particle.prevRotation = particle.rotation
            particle.age++
            if (particle.age >= particle.lifetime) {
                iterator.remove()
                continue
            }

            when (particle.physics) {
                JumpCircleModule.ParticlePhysics.REALISTIC -> {
                    particle.velocityY -= 0.028
                    moveParticleWithCollision(world, particle, collide = true)
                    particle.velocityX *= 0.982
                    particle.velocityY *= 0.982
                    particle.velocityZ *= 0.982
                }
                JumpCircleModule.ParticlePhysics.NO_COLLISION -> {
                    particle.velocityY -= 0.026
                    moveParticleWithCollision(world, particle, collide = false)
                    particle.velocityX *= 0.985
                    particle.velocityY *= 0.985
                    particle.velocityZ *= 0.985
                }
                JumpCircleModule.ParticlePhysics.NO_PHYSICS -> {
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    particle.z += particle.velocityZ
                    particle.velocityX *= 0.992
                    particle.velocityY *= 0.992
                    particle.velocityZ *= 0.992
                }
                JumpCircleModule.ParticlePhysics.ATTRACTION -> {
                    val dx = particle.centerX - particle.x
                    val dy = particle.centerY - particle.y
                    val dz = particle.centerZ - particle.z
                    val distance = sqrt((dx * dx) + (dy * dy) + (dz * dz)).coerceAtLeast(0.0001)
                    val attraction = 0.012
                    particle.velocityX += (dx / distance) * attraction
                    particle.velocityY += (dy / distance) * attraction
                    particle.velocityZ += (dz / distance) * attraction
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    particle.z += particle.velocityZ
                    particle.velocityX *= 0.942
                    particle.velocityY *= 0.942
                    particle.velocityZ *= 0.942
                }
            }

            particle.rotation += particle.rotationVelocity
        }
    }

    private fun moveParticleWithCollision(world: ClientLevel, particle: JumpParticle, collide: Boolean) {
        var nextX = particle.x + particle.velocityX
        var nextY = particle.y + particle.velocityY
        var nextZ = particle.z + particle.velocityZ

        if (collide) {
            if (collides(world, nextX, particle.y, particle.z)) {
                nextX = particle.x
                particle.velocityX *= -0.52
            }
            if (collides(world, nextX, nextY, particle.z)) {
                nextY = particle.y
                particle.velocityY = if (particle.velocityY < 0.0) particle.velocityY * -0.42 else particle.velocityY * -0.28
            }
            if (collides(world, nextX, nextY, nextZ)) {
                nextZ = particle.z
                particle.velocityZ *= -0.52
            }
        }

        particle.x = nextX
        particle.y = nextY
        particle.z = nextZ
    }

    private fun collides(world: ClientLevel, x: Double, y: Double, z: Double): Boolean {
        val blockPos = BlockPos.containing(x, y, z)
        if (!world.isLoaded(blockPos)) return false
        val state = world.getBlockState(blockPos)
        return !state.isAir && state.blocksMotion()
    }

    private fun render(context: WorldRenderContext) {
        if (!JumpCircleModule.isActive()) return
        val client = Minecraft.getInstance()
        val world = client.level ?: return
        if (circleEffects.isEmpty() && blockWaveEffects.isEmpty() && particles.isEmpty()) return
        if (client.options.getCameraType().isFirstPerson && !JumpCircleModule.showFirstPerson()) return

        val camera = client.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)

        renderCircles(context, client, cameraPos.x, cameraPos.y, cameraPos.z, partialTick)
        renderBlockWaves(context, client, world, cameraPos.x, cameraPos.y, cameraPos.z, partialTick)
        renderParticles(context, camera, cameraPos.x, cameraPos.y, cameraPos.z, partialTick)
    }

    private fun renderCircles(
        context: WorldRenderContext,
        client: Minecraft,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        partialTick: Float,
    ) {
        if (circleEffects.isEmpty()) return
        val pose = context.matrices().last()
        val renderType = RenderTypes.entityTranslucentEmissive(JumpCircleTextureRegistry.resolveRingTexture(client))
        val consumer = fillBufferSource.getBuffer(renderType)

        circleEffects
            .sortedByDescending { distanceSq(it.centerX, it.centerY, it.centerZ, cameraX, cameraY, cameraZ) }
            .forEach { effect ->
                val radius = currentRadius(effect.maxRadius, effect.speed, effect.ageTicks, partialTick)
                if (radius <= 0.01f) return@forEach
                val progress = (radius / effect.maxRadius.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                val fade = ((progress - 0.84f) / 0.16f).coerceIn(0f, 1f)
                val alpha = ((1.0f - fade) * 255f).roundToInt().coerceIn(0, 255)
                val color = withAlpha(effect.color, alpha)
                drawHorizontalTexturedQuad(
                    consumer = consumer,
                    pose = pose,
                    centerX = (effect.centerX - cameraX).toFloat(),
                    centerY = (effect.centerY - cameraY).toFloat(),
                    centerZ = (effect.centerZ - cameraZ).toFloat(),
                    halfSize = radius,
                    color = color,
                )
            }

        fillBufferSource.endBatch(renderType)
    }
    private fun renderBlockWaves(
        context: WorldRenderContext,
        client: Minecraft,
        world: ClientLevel,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        partialTick: Float,
    ) {
        if (blockWaveEffects.isEmpty()) return
        val pose = context.matrices().last()
        val quadsByTexture = LinkedHashMap<Identifier, MutableList<WaveQuad>>()
        val revealQuadsByShader = LinkedHashMap<ShaderMaskBatchKey, MutableList<ShadertoyMaskedWorldRenderer.Quad>>()
        val lineRenderType = RenderTypes.linesTranslucent()
        val lineConsumer = lineBufferSource.getBuffer(lineRenderType)
        var drewLines = false

        blockWaveEffects.forEach { effect ->
            val radius = currentRadius(effect.maxRadius, effect.speed, effect.ageTicks, partialTick)
            if (radius <= 0.01f) return@forEach
            val innerRadius = max(0f, radius - effect.thickness)
            val outerRadius = radius
            val fillAlpha = effect.fillAlpha
            val textureId = JumpCircleTextureRegistry.whitePixelTextureId
            val fillColor = withAlpha(
                effect.fillColor,
                (fillAlpha * 255f).roundToInt().coerceIn(0, 255),
            )
            populateWaveSurfaceCache(effect, world, outerRadius)
            val cells = LinkedHashMap<Long, SurfaceCell>()

            effect.surfaceCache.values.forEach { cell ->
                if (cell.maxRadialDistance < innerRadius || cell.minRadialDistance > outerRadius) return@forEach
                cells[surfaceCellKey(cell.blockX, cell.blockZ)] = cell
            }

            if (cells.isNotEmpty()) {
                val lineColor = withAlpha(effect.outlineColor, 232)
                cells.values.forEach { cell ->
                    val north = cells[surfaceCellKey(cell.blockX, cell.blockZ - 1)]
                    val south = cells[surfaceCellKey(cell.blockX, cell.blockZ + 1)]
                    val west = cells[surfaceCellKey(cell.blockX - 1, cell.blockZ)]
                    val east = cells[surfaceCellKey(cell.blockX + 1, cell.blockZ)]
                    val y = (cell.renderY - cameraY).toFloat()
                    val minX = (cell.blockX.toDouble() - cameraX).toFloat()
                    val minZ = (cell.blockZ.toDouble() - cameraZ).toFloat()
                    val maxX = ((cell.blockX + 1).toDouble() - cameraX).toFloat()
                    val maxZ = ((cell.blockZ + 1).toDouble() - cameraZ).toFloat()

                    if (effect.outline) {
                        if (north == null || abs(north.renderY - cell.renderY) > 0.015) {
                            drewLines = true
                            line(lineConsumer, pose, minX, y, minZ, maxX, y, minZ, effect.lineThickness, lineColor, lineColor)
                        }
                        if (south == null || abs(south.renderY - cell.renderY) > 0.015) {
                            drewLines = true
                            line(lineConsumer, pose, minX, y, maxZ, maxX, y, maxZ, effect.lineThickness, lineColor, lineColor)
                        }
                        if (west == null || abs(west.renderY - cell.renderY) > 0.015) {
                            drewLines = true
                            line(lineConsumer, pose, minX, y, minZ, minX, y, maxZ, effect.lineThickness, lineColor, lineColor)
                        }
                        if (east == null || abs(east.renderY - cell.renderY) > 0.015) {
                            drewLines = true
                            line(lineConsumer, pose, maxX, y, minZ, maxX, y, maxZ, effect.lineThickness, lineColor, lineColor)
                        }
                    }
                }

                if (effect.fill) {
                    cells.values.forEach { cell ->
                        val minX = cell.blockX.toDouble()
                        val minZ = cell.blockZ.toDouble()
                        val maxX = minX + 1.0
                        val maxZ = minZ + 1.0
                        val y = cell.renderY

                        when (effect.fillType) {
                            JumpCircleModule.FillType.COLOR -> {
                                quadsByTexture.getOrPut(textureId) { ArrayList() } += WaveQuad(
                                    textureId = textureId,
                                    ax = (minX - cameraX).toFloat(),
                                    ay = (y - cameraY).toFloat(),
                                    az = (minZ - cameraZ).toFloat(),
                                    bx = (maxX - cameraX).toFloat(),
                                    by = (y - cameraY).toFloat(),
                                    bz = (minZ - cameraZ).toFloat(),
                                    cx = (maxX - cameraX).toFloat(),
                                    cy = (y - cameraY).toFloat(),
                                    cz = (maxZ - cameraZ).toFloat(),
                                    dx = (minX - cameraX).toFloat(),
                                    dy = (y - cameraY).toFloat(),
                                    dz = (maxZ - cameraZ).toFloat(),
                                    au = 0f,
                                    av = 1f,
                                    bu = 1f,
                                    bv = 1f,
                                    cu = 1f,
                                    cv = 0f,
                                    du = 0f,
                                    dv = 0f,
                                    aColor = fillColor,
                                    bColor = fillColor,
                                    cColor = fillColor,
                                    dColor = fillColor,
                                )
                            }

                            JumpCircleModule.FillType.SHADER_MASK -> {
                                val shaderBatchKey = ShaderMaskBatchKey(
                                    shaderType = effect.shaderType,
                                    shaderSpeedBits = java.lang.Float.floatToIntBits(effect.shaderSpeed),
                                    shaderAlphaBits = java.lang.Float.floatToIntBits(effect.shaderAlpha),
                                )
                                revealQuadsByShader.getOrPut(shaderBatchKey) { ArrayList() } += buildShaderMaskQuad(
                                    minX = minX,
                                    maxX = maxX,
                                    y = y,
                                    minZ = minZ,
                                    maxZ = maxZ,
                                    cameraX = cameraX,
                                    cameraY = cameraY,
                                    cameraZ = cameraZ,
                                )
                            }
                        }
                    }
                }
            }
        }

        quadsByTexture.forEach { (textureId, quads) ->
            if (quads.isEmpty()) return@forEach
            val renderType = RenderTypes.entityTranslucentEmissive(textureId)
            val consumer = fillBufferSource.getBuffer(renderType)
            quads.forEach { quad ->
                drawTexturedQuad(
                    consumer = consumer,
                    pose = pose,
                    ax = quad.ax,
                    ay = quad.ay,
                    az = quad.az,
                    bx = quad.bx,
                    by = quad.by,
                    bz = quad.bz,
                    cx = quad.cx,
                    cy = quad.cy,
                    cz = quad.cz,
                    dx = quad.dx,
                    dy = quad.dy,
                    dz = quad.dz,
                    au = quad.au,
                    av = quad.av,
                    bu = quad.bu,
                    bv = quad.bv,
                    cu = quad.cu,
                    cv = quad.cv,
                    du = quad.du,
                    dv = quad.dv,
                    aColor = quad.aColor,
                    bColor = quad.bColor,
                    cColor = quad.cColor,
                    dColor = quad.dColor,
                    normalY = 1f,
                )
            }
            fillBufferSource.endBatch(renderType)
        }

        revealQuadsByShader.forEach { (batch, quads) ->
            if (quads.isEmpty()) return@forEach
            val frame = ShadertoyFrameProvider.currentFrame(
                client = client,
                program = batch.shaderType.toProgramDefinition(),
                qualityPreset = com.visualproject.client.visuals.hitbox.HitboxCustomizerModule.quality().preset,
                timeScale = Float.fromBits(batch.shaderSpeedBits),
            ) ?: return@forEach
            ShadertoyMaskedWorldRenderer.drawQuads(
                context = context,
                frame = frame,
                quads = quads,
                alpha = Float.fromBits(batch.shaderAlphaBits),
            )
        }

        if (drewLines) {
            lineBufferSource.endBatch(lineRenderType)
        }

    }

    private fun renderParticles(
        context: WorldRenderContext,
        camera: net.minecraft.client.Camera,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        partialTick: Float,
    ) {
        if (particles.isEmpty()) return
        val rendered = particles.mapNotNull { particle ->
            buildRenderedParticle(particle, cameraX, cameraY, cameraZ, partialTick)
        }
        if (rendered.isEmpty()) return

        val poseStack = context.matrices()
        val cameraRotation = Quaternionf(camera.rotation())
        rendered
            .groupBy { "${it.texture.textureId}|${it.texture.useCutout}" }
            .values
            .forEach { group ->
                val texture = group.first().texture
                val renderType = if (texture.useCutout) {
                    RenderTypes.entitySmoothCutout(texture.textureId)
                } else {
                    RenderTypes.entityTranslucent(texture.textureId)
                }
                val consumer = particleBufferSource.getBuffer(renderType)
                group.sortedByDescending { it.distanceSq }.forEach { particle ->
                    poseStack.pushPose()
                    poseStack.translate(
                        particle.x - cameraX,
                        particle.y - cameraY,
                        particle.z - cameraZ,
                    )
                    poseStack.mulPose(cameraRotation)
                    poseStack.mulPose(Axis.ZP.rotation(particle.rotationRadians))
                    drawBillboardQuad(consumer, poseStack.last(), particle.halfSize, particle.color)
                    poseStack.popPose()
                }
                particleBufferSource.endBatch(renderType)
            }
    }

    private fun buildRenderedParticle(
        particle: JumpParticle,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        partialTick: Float,
    ): RenderedParticle? {
        val interpolatedX = lerp(particle.prevX, particle.x, partialTick)
        val interpolatedY = lerp(particle.prevY, particle.y, partialTick)
        val interpolatedZ = lerp(particle.prevZ, particle.z, partialTick)
        val progress = (particle.age.toFloat() / particle.lifetime.toFloat()).coerceIn(0f, 1f)
        val fadeStart = 0.78f
        val fadeProgress = ((progress - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
        val alpha = if (particle.texture.useCutout) {
            0xFF
        } else {
            ((1.0f - fadeProgress) * 255f).roundToInt().coerceIn(0, 255)
        }
        if (alpha <= 0) return null

        val scaleFactor = if (particle.texture.useCutout) {
            (1.0f - (fadeProgress * 0.55f)).coerceIn(0.2f, 1.0f)
        } else {
            1.0f
        }
        val halfSize = max(0.02f, particle.size * particleWorldHalfSizeScale * scaleFactor)
        val dx = interpolatedX - cameraX
        val dy = interpolatedY - cameraY
        val dz = interpolatedZ - cameraZ

        return RenderedParticle(
            texture = particle.texture,
            x = interpolatedX,
            y = interpolatedY,
            z = interpolatedZ,
            halfSize = halfSize,
            color = (alpha shl 24) or (particle.texture.colorRgb and 0x00FFFFFF),
            rotationRadians = lerp(particle.prevRotation.toDouble(), particle.rotation.toDouble(), partialTick).toFloat(),
            distanceSq = (dx * dx) + (dy * dy) + (dz * dz),
        )
    }
    private fun drawHorizontalTexturedQuad(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        halfSize: Float,
        color: Int,
    ) {
        drawTexturedQuad(
            consumer = consumer,
            pose = pose,
            ax = centerX - halfSize,
            ay = centerY,
            az = centerZ - halfSize,
            bx = centerX + halfSize,
            by = centerY,
            bz = centerZ - halfSize,
            cx = centerX + halfSize,
            cy = centerY,
            cz = centerZ + halfSize,
            dx = centerX - halfSize,
            dy = centerY,
            dz = centerZ + halfSize,
            au = 0f,
            av = 1f,
            bu = 1f,
            bv = 1f,
            cu = 1f,
            cv = 0f,
            du = 0f,
            dv = 0f,
            aColor = color,
            bColor = color,
            cColor = color,
            dColor = color,
            normalY = 1f,
        )
    }

    private fun drawTexturedQuad(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        ax: Float,
        ay: Float,
        az: Float,
        bx: Float,
        by: Float,
        bz: Float,
        cx: Float,
        cy: Float,
        cz: Float,
        dx: Float,
        dy: Float,
        dz: Float,
        au: Float,
        av: Float,
        bu: Float,
        bv: Float,
        cu: Float,
        cv: Float,
        du: Float,
        dv: Float,
        aColor: Int,
        bColor: Int,
        cColor: Int,
        dColor: Int,
        normalY: Float,
    ) {
        if (((aColor ushr 24) and 0xFF) <= 0 && ((bColor ushr 24) and 0xFF) <= 0 && ((cColor ushr 24) and 0xFF) <= 0 && ((dColor ushr 24) and 0xFF) <= 0) return
        addTexturedVertex(consumer, pose, ax, ay, az, au, av, aColor, normalY)
        addTexturedVertex(consumer, pose, bx, by, bz, bu, bv, bColor, normalY)
        addTexturedVertex(consumer, pose, cx, cy, cz, cu, cv, cColor, normalY)
        addTexturedVertex(consumer, pose, dx, dy, dz, du, dv, dColor, normalY)
    }

    private fun addTexturedVertex(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        color: Int,
        normalY: Float,
    ) {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        consumer.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(0).setLight(fullBrightLight).setNormal(0f, normalY, 0f)
    }

    private fun drawBillboardQuad(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        halfSize: Float,
        color: Int,
    ) {
        val a = (color ushr 24) and 0xFF
        if (a <= 0) return
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        consumer.addVertex(pose, -halfSize, -halfSize, 0f).setColor(r, g, b, a).setUv(0f, 1f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, halfSize, -halfSize, 0f).setColor(r, g, b, a).setUv(1f, 1f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, halfSize, halfSize, 0f).setColor(r, g, b, a).setUv(1f, 0f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, -halfSize, halfSize, 0f).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
    }

    private fun line(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        lineWidth: Float,
        startColor: Int,
        endColor: Int,
    ) {
        val normalX = x2 - x1
        val normalY = y2 - y1
        val normalZ = z2 - z1
        consumer.addVertex(pose, x1, y1, z1).setColor(startColor).setLineWidth(lineWidth).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(pose, x2, y2, z2).setColor(endColor).setLineWidth(lineWidth).setNormal(pose, normalX, normalY, normalZ)
    }

    private fun sampleSurfaceY(world: ClientLevel, x: Double, z: Double): Double {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ).toDouble()
    }

    private fun populateWaveSurfaceCache(effect: BlockWaveEffect, world: ClientLevel, outerRadius: Float) {
        val targetOuterRadius = outerRadius + 1.25f
        val previousOuterRadius = effect.sampledOuterRadius
        if (targetOuterRadius <= previousOuterRadius + 0.001f) return

        val minBlockX = floor(effect.centerX - targetOuterRadius - 1.0).toInt()
        val maxBlockX = ceil(effect.centerX + targetOuterRadius + 1.0).toInt()
        val minBlockZ = floor(effect.centerZ - targetOuterRadius - 1.0).toInt()
        val maxBlockZ = ceil(effect.centerZ + targetOuterRadius + 1.0).toInt()
        val scanMinRadius = max(0f, previousOuterRadius)

        for (blockX in minBlockX..maxBlockX) {
            for (blockZ in minBlockZ..maxBlockZ) {
                val key = surfaceCellKey(blockX, blockZ)
                if (effect.surfaceCache.containsKey(key)) continue

                val minRadialDistance = minDistanceToCell(
                    centerX = effect.centerX,
                    centerZ = effect.centerZ,
                    minX = blockX.toDouble(),
                    maxX = blockX + 1.0,
                    minZ = blockZ.toDouble(),
                    maxZ = blockZ + 1.0,
                )
                val maxRadialDistance = maxDistanceToCell(
                    centerX = effect.centerX,
                    centerZ = effect.centerZ,
                    minX = blockX.toDouble(),
                    maxX = blockX + 1.0,
                    minZ = blockZ.toDouble(),
                    maxZ = blockZ + 1.0,
                )
                if (maxRadialDistance < scanMinRadius || minRadialDistance > targetOuterRadius) continue

                val renderY = sampleWaveSurfaceYCached(effect, world, blockX, blockZ) ?: continue
                effect.surfaceCache[key] = SurfaceCell(blockX, blockZ, renderY, minRadialDistance, maxRadialDistance)
            }
        }

        effect.sampledOuterRadius = targetOuterRadius
    }

    private fun sampleWaveSurfaceYCached(effect: BlockWaveEffect, world: ClientLevel, blockX: Int, blockZ: Int): Double? {
        val referenceTopY = floor(effect.centerY - waveYOffset).toInt()
        val cacheKey = SurfaceColumnKey(world.dimension().identifier(), blockX, blockZ, referenceTopY)
        if (worldSurfaceCache.containsKey(cacheKey)) {
            return worldSurfaceCache[cacheKey]
        }

        val topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) - 1
        if (topY < world.minY) {
            worldSurfaceCache[cacheKey] = null
            return null
        }
        val startY = min(topY, referenceTopY + waveSurfaceMaxRiseBlocks)
        if (startY < world.minY) {
            worldSurfaceCache[cacheKey] = null
            return null
        }
        val minScanY = max(world.minY, referenceTopY - waveSurfaceScanDepth)
        for (y in startY downTo minScanY) {
            val pos = BlockPos(blockX, y, blockZ)
            if (!world.isLoaded(pos)) {
                worldSurfaceCache[cacheKey] = null
                return null
            }
            val state = world.getBlockState(pos)
            if (state.isAir) continue
            if (state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.LOGS)) continue
            if (!state.blocksMotion()) continue
            val renderY = y + 1.0 + waveYOffset
            worldSurfaceCache[cacheKey] = renderY
            return renderY
        }
        worldSurfaceCache[cacheKey] = null
        return null
    }

    private fun surfaceCellKey(blockX: Int, blockZ: Int): Long {
        return (blockX.toLong() shl 32) xor (blockZ.toLong() and 0xFFFFFFFFL)
    }

    private fun currentRadius(maxRadius: Float, speed: Float, ageTicks: Int, partialTick: Float): Float {
        return min(maxRadius, ((ageTicks + partialTick) / 20f) * speed)
    }

    private fun minDistanceToCell(
        centerX: Double,
        centerZ: Double,
        minX: Double,
        maxX: Double,
        minZ: Double,
        maxZ: Double,
    ): Float {
        val nearestX = centerX.coerceIn(minX, maxX)
        val nearestZ = centerZ.coerceIn(minZ, maxZ)
        val dx = nearestX - centerX
        val dz = nearestZ - centerZ
        return sqrt((dx * dx) + (dz * dz)).toFloat()
    }

    private fun maxDistanceToCell(
        centerX: Double,
        centerZ: Double,
        minX: Double,
        maxX: Double,
        minZ: Double,
        maxZ: Double,
    ): Float {
        var maxDistanceSq = 0.0
        val corners = arrayOf(
            doubleArrayOf(minX, minZ),
            doubleArrayOf(maxX, minZ),
            doubleArrayOf(maxX, maxZ),
            doubleArrayOf(minX, maxZ),
        )
        corners.forEach { corner ->
            val dx = corner[0] - centerX
            val dz = corner[1] - centerZ
            val distanceSq = (dx * dx) + (dz * dz)
            if (distanceSq > maxDistanceSq) {
                maxDistanceSq = distanceSq
            }
        }
        return sqrt(maxDistanceSq).toFloat()
    }

    private fun distanceSq(x: Double, y: Double, z: Double, otherX: Double, otherY: Double, otherZ: Double): Double {
        val dx = x - otherX
        val dy = y - otherY
        val dz = z - otherZ
        return (dx * dx) + (dy * dy) + (dz * dz)
    }

    private fun lerp(previous: Double, current: Double, delta: Float): Double {
        return previous + ((current - previous) * delta)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
    }

    private fun buildShaderMaskQuad(
        minX: Double,
        maxX: Double,
        y: Double,
        minZ: Double,
        maxZ: Double,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ): ShadertoyMaskedWorldRenderer.Quad {
        return ShadertoyMaskedWorldRenderer.Quad(
            ax = (minX - cameraX).toFloat(),
            ay = (y - cameraY).toFloat(),
            az = (minZ - cameraZ).toFloat(),
            bx = (maxX - cameraX).toFloat(),
            by = (y - cameraY).toFloat(),
            bz = (minZ - cameraZ).toFloat(),
            cx = (maxX - cameraX).toFloat(),
            cy = (y - cameraY).toFloat(),
            cz = (maxZ - cameraZ).toFloat(),
            dx = (minX - cameraX).toFloat(),
            dy = (y - cameraY).toFloat(),
            dz = (maxZ - cameraZ).toFloat(),
        )
    }

    private fun JumpCircleModule.ShaderType.toProgramDefinition(): ShadertoyProgramRegistry.ProgramDefinition {
        return when (this) {
            JumpCircleModule.ShaderType.NEBULA -> ShadertoyProgramRegistry.ProgramDefinition.SIMPLEX_NEBULA
            JumpCircleModule.ShaderType.STARS -> ShadertoyProgramRegistry.ProgramDefinition.STAR_NEST
            JumpCircleModule.ShaderType.WEB -> ShadertoyProgramRegistry.ProgramDefinition.OVERSATURATED_WEB
        }
    }

    private fun clearRuntime() {
        circleEffects.clear()
        blockWaveEffects.clear()
        particles.clear()
    }
}
