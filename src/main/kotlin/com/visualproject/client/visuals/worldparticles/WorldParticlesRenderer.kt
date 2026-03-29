package com.visualproject.client.visuals.worldparticles

import com.mojang.math.Axis
import com.visualproject.client.ModuleStateStore
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

object WorldParticlesRenderer {
    private const val worldHalfSizeScale = 0.125f
    private const val fullBrightLight = 0x00F000F0
    private val particleBufferSource = MultiBufferSource.immediate(ByteBufferBuilder(262_144))

    private data class RenderedParticle(
        val x: Double,
        val y: Double,
        val z: Double,
        val halfSize: Float,
        val color: Int,
        val rotationRadians: Float,
        val distanceSq: Double,
    )

    private data class ParticleInstance(
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
        val color: Int,
        var prevRotation: Float,
        var rotation: Float,
        var rotationVelocity: Float,
    )

    private val random = Random(System.nanoTime())
    private val particles = ArrayList<ParticleInstance>()
    private var initialized = false
    private var spawnAccumulator = 0f

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
        if (!ModuleStateStore.isEnabled(WorldParticlesModule.moduleId) || world == null || player == null) {
            particles.clear()
            spawnAccumulator = 0f
            return
        }

        val texture = WorldParticleTextureRegistry.resolveTexture(client)
        if (texture == null) {
            particles.clear()
            spawnAccumulator = 0f
            return
        }

        val ratePerSecond = ModuleStateStore.getNumberSetting(WorldParticlesModule.spawnRateKey, 5.0f).coerceIn(1.0f, 20.0f)
        spawnAccumulator += ratePerSecond / 20.0f
        while (spawnAccumulator >= 1.0f) {
            spawnAccumulator -= 1.0f
            spawnBurst(client)
        }

        updateParticles(client)
        val maxParticles = 800
        if (particles.size > maxParticles) {
            particles.subList(0, particles.size - maxParticles).clear()
        }
    }

    private fun spawnBurst(client: Minecraft) {
        val player = client.player ?: return
        val type = WorldParticlesModule.ParticleType.fromId(
            ModuleStateStore.getTextSetting(WorldParticlesModule.particleTypeKey, WorldParticlesModule.ParticleType.WATER_DROP.id)
        )
        if (type == WorldParticlesModule.ParticleType.CUSTOM && WorldParticleTextureRegistry.resolveTexture(client) == null) {
            return
        }

        val count = ModuleStateStore.getNumberSetting(WorldParticlesModule.spawnCountKey, 5.0f).toInt().coerceIn(1, 20)
        val radius = ModuleStateStore.getNumberSetting(WorldParticlesModule.spawnRadiusKey, 30.0f).coerceIn(2.0f, 64.0f).toDouble()
        val height = ModuleStateStore.getNumberSetting(WorldParticlesModule.spawnHeightKey, 10.0f).coerceIn(1.0f, 32.0f).toDouble()
        val size = ModuleStateStore.getNumberSetting(WorldParticlesModule.sizeKey, 0.40f).coerceIn(0.10f, 1.50f)
        val lifetime = ModuleStateStore.getNumberSetting(WorldParticlesModule.lifetimeKey, 100.0f).toInt().coerceIn(10, 240)
        val horizontalMovement = ModuleStateStore.isSettingEnabled(WorldParticlesModule.horizontalMovementKey)
        val speed = ModuleStateStore.getNumberSetting(WorldParticlesModule.speedKey, 0.05f).coerceIn(0.0f, 0.30f).toDouble()
        val physicsMode = WorldParticlesModule.PhysicsMode.fromId(
            ModuleStateStore.getTextSetting(WorldParticlesModule.physicsModeKey, WorldParticlesModule.PhysicsMode.NO_PHYSICS.id)
        )

        repeat(count) {
            val angle = random.nextDouble(0.0, PI * 2.0)
            val distance = radius * kotlin.math.sqrt(random.nextDouble())
            val x = player.x + (cos(angle) * distance)
            val y = player.y + random.nextDouble(0.2, height)
            val z = player.z + (sin(angle) * distance)

            val (velocityX, velocityY, velocityZ) = when (physicsMode) {
                WorldParticlesModule.PhysicsMode.NO_PHYSICS -> {
                    val vector = randomUnitVector()
                    val vectorSpeed = speed * random.nextDouble(0.45, 1.20)
                    val horizontalFactor = if (horizontalMovement) 1.0 else 0.20
                    Triple(
                        vector.x * vectorSpeed * horizontalFactor,
                        vector.y * vectorSpeed,
                        vector.z * vectorSpeed * horizontalFactor,
                    )
                }
                else -> {
                    val horizontalSpeed = if (horizontalMovement) speed * random.nextDouble(0.45, 1.0) else 0.0
                    val verticalSpeed = speed * random.nextDouble(0.15, 0.55)
                    Triple(
                        if (horizontalMovement) cos(angle) * horizontalSpeed else 0.0,
                        verticalSpeed,
                        if (horizontalMovement) sin(angle) * horizontalSpeed else 0.0,
                    )
                }
            }
            val initialRotation = random.nextFloat() * (PI.toFloat() * 2.0f)
            val rotationVelocity = ((random.nextFloat() * 2.0f) - 1.0f) * 0.07f

            particles += ParticleInstance(
                prevX = x,
                prevY = y,
                prevZ = z,
                x = x,
                y = y,
                z = z,
                velocityX = velocityX,
                velocityY = velocityY,
                velocityZ = velocityZ,
                age = 0,
                lifetime = lifetime,
                size = size,
                color = 0xFFFFFFFF.toInt(),
                prevRotation = initialRotation,
                rotation = initialRotation,
                rotationVelocity = rotationVelocity,
            )
        }
    }

    private fun updateParticles(client: Minecraft) {
        val world = client.level ?: return
        val gravity = ModuleStateStore.getNumberSetting(WorldParticlesModule.gravityKey, 0.04f).coerceIn(0.0f, 0.20f).toDouble()
        val physicsMode = WorldParticlesModule.PhysicsMode.fromId(
            ModuleStateStore.getTextSetting(WorldParticlesModule.physicsModeKey, WorldParticlesModule.PhysicsMode.NO_PHYSICS.id)
        )

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

            if (physicsMode != WorldParticlesModule.PhysicsMode.NO_PHYSICS) {
                particle.velocityY -= gravity
            }

            var nextX = particle.x + particle.velocityX
            var nextY = particle.y + particle.velocityY
            var nextZ = particle.z + particle.velocityZ

            if (physicsMode == WorldParticlesModule.PhysicsMode.REALISTIC) {
                if (collides(world = world, x = nextX, y = particle.y, z = particle.z)) {
                    nextX = particle.x
                    particle.velocityX *= -0.62
                }
                if (collides(world = world, x = nextX, y = nextY, z = particle.z)) {
                    nextY = particle.y
                    particle.velocityY = if (particle.velocityY < 0.0) {
                        particle.velocityY * -0.58
                    } else {
                        particle.velocityY * -0.32
                    }
                }
                if (collides(world = world, x = nextX, y = nextY, z = nextZ)) {
                    nextZ = particle.z
                    particle.velocityZ *= -0.62
                }

                if (abs(particle.velocityX) < 0.0025) particle.velocityX = 0.0
                if (abs(particle.velocityY) < 0.0025) particle.velocityY = 0.0
                if (abs(particle.velocityZ) < 0.0025) particle.velocityZ = 0.0
            }

            particle.x = nextX
            particle.y = nextY
            particle.z = nextZ
            particle.rotation += particle.rotationVelocity

            particle.velocityX *= 0.985
            particle.velocityY *= 0.985
            particle.velocityZ *= 0.985
        }
    }

    private fun collides(world: net.minecraft.client.multiplayer.ClientLevel, x: Double, y: Double, z: Double): Boolean {
        val blockPos = BlockPos.containing(x, y, z)
        if (!world.isLoaded(blockPos)) return false
        val state = world.getBlockState(blockPos)
        return !state.isAir && state.blocksMotion()
    }

    private fun render(context: WorldRenderContext) {
        val client = Minecraft.getInstance()
        if (!ModuleStateStore.isEnabled(WorldParticlesModule.moduleId)) return
        if (particles.isEmpty()) return

        val texture = WorldParticleTextureRegistry.resolveTexture(client) ?: return
        val camera = client.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val renderedParticles = particles.mapNotNull { particle ->
            buildRenderedParticle(
                particle = particle,
                texture = texture,
                cameraX = cameraPos.x,
                cameraY = cameraPos.y,
                cameraZ = cameraPos.z,
                partialTick = partialTick,
            )
        }.sortedByDescending { it.distanceSq }
        if (renderedParticles.isEmpty()) return

        val poseStack = context.matrices()
        val consumers = particleBufferSource
        val coreType = if (texture.useCutout) {
            RenderTypes.entitySmoothCutout(texture.textureId)
        } else {
            RenderTypes.entityTranslucent(texture.textureId)
        }
        val cameraRotation = Quaternionf(camera.rotation())

        renderedParticles.forEach { particle ->
            poseStack.pushPose()
            poseStack.translate(
                particle.x - cameraPos.x,
                particle.y - cameraPos.y,
                particle.z - cameraPos.z,
            )
            poseStack.mulPose(cameraRotation)
            poseStack.mulPose(Axis.ZP.rotation(particle.rotationRadians))

            drawWorldQuad(
                consumer = consumers.getBuffer(coreType),
                pose = poseStack.last(),
                halfSize = particle.halfSize,
                color = particle.color,
            )
            poseStack.popPose()
        }
        consumers.endBatch(coreType)
    }

    private fun buildRenderedParticle(
        particle: ParticleInstance,
        texture: WorldParticleTextureRegistry.ParticleTexture,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        partialTick: Float,
    ): RenderedParticle? {
        val interpolatedX = lerp(particle.prevX, particle.x, partialTick)
        val interpolatedY = lerp(particle.prevY, particle.y, partialTick)
        val interpolatedZ = lerp(particle.prevZ, particle.z, partialTick)
        val lifeProgress = (particle.age.toFloat() / particle.lifetime.toFloat()).coerceIn(0f, 1f)
        val fadeStart = 0.82f
        val fadeProgress = ((lifeProgress - fadeStart) / (1.0f - fadeStart)).coerceIn(0f, 1f)
        val alphaFactor = 1.0f - fadeProgress
        val rotationRadians = lerp(particle.prevRotation.toDouble(), particle.rotation.toDouble(), partialTick).toFloat()
        val scaleFactor = if (texture.useCutout) {
            (1.0f - fadeProgress).coerceIn(0f, 1f)
        } else {
            1.0f
        }
        val alpha = if (texture.useCutout) {
            0xFF
        } else {
            (255.0f * alphaFactor).toInt().coerceIn(0, 255)
        }
        if (alpha <= 0) return null
        val halfSize = max(0.02f, particle.size * worldHalfSizeScale * max(scaleFactor, 0.0001f))
        if (texture.useCutout && halfSize <= 0.02f && fadeProgress >= 1.0f) return null
        val color = (alpha shl 24) or (texture.colorRgb and 0x00FFFFFF)
        val dx = interpolatedX - cameraX
        val dy = interpolatedY - cameraY
        val dz = interpolatedZ - cameraZ

        return RenderedParticle(
            x = interpolatedX,
            y = interpolatedY,
            z = interpolatedZ,
            halfSize = halfSize,
            color = color,
            rotationRadians = rotationRadians,
            distanceSq = (dx * dx) + (dy * dy) + (dz * dz),
        )
    }

    private fun drawWorldQuad(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
        halfSize: Float,
        color: Int,
    ) {
        if ((color ushr 24) <= 0) return
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        consumer.addVertex(pose, -halfSize, -halfSize, 0f).setColor(r, g, b, a).setUv(0f, 1f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, halfSize, -halfSize, 0f).setColor(r, g, b, a).setUv(1f, 1f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, halfSize, halfSize, 0f).setColor(r, g, b, a).setUv(1f, 0f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
        consumer.addVertex(pose, -halfSize, halfSize, 0f).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(0).setLight(fullBrightLight).setNormal(0f, 1f, 0f)
    }

    private fun lerp(previous: Double, current: Double, delta: Float): Double {
        return previous + ((current - previous) * delta)
    }

    private fun randomUnitVector(): Vector3f {
        val theta = random.nextDouble(0.0, PI * 2.0)
        val z = random.nextDouble(-1.0, 1.0)
        val radius = kotlin.math.sqrt(1.0 - (z * z))
        return Vector3f(
            (radius * cos(theta)).toFloat(),
            z.toFloat(),
            (radius * sin(theta)).toFloat(),
        )
    }

}
