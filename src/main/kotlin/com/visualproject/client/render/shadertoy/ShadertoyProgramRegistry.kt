package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceProvider
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.util.LinkedHashSet

object ShadertoyProgramRegistry {
    enum class ProgramDefinition(
        val id: String,
        val imageFragmentShaderId: Identifier,
        val fragmentShaderSourceId: Identifier,
    ) {
        OVERSATURATED_WEB(
            id = "oversaturated_web",
            imageFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/oversaturated_web_image_pass"),
            fragmentShaderSourceId = Identifier.fromNamespaceAndPath("visualclient", "shaders/core/oversaturated_web_image_pass.fsh"),
        ),
        STAR_NEST(
            id = "star_nest",
            imageFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/star_nest_image_pass"),
            fragmentShaderSourceId = Identifier.fromNamespaceAndPath("visualclient", "shaders/core/star_nest_image_pass.fsh"),
        ),
        SIMPLEX_NEBULA(
            id = "simplex_nebula",
            imageFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/simplex_nebula_image_pass"),
            fragmentShaderSourceId = Identifier.fromNamespaceAndPath("visualclient", "shaders/core/simplex_nebula_image_pass.fsh"),
        ),
    }

    data class CompiledProgram(
        val definition: ProgramDefinition,
        val imagePipeline: RenderPipeline,
        val boxFillPipeline: RenderPipeline,
        val boxFillInteriorPipeline: RenderPipeline,
        val worldRevealPipeline: RenderPipeline,
        val maskWritePipeline: RenderPipeline,
        val maskCompositePipeline: RenderPipeline,
        val compiledImagePipeline: CompiledRenderPipeline,
        val compiledBoxFillPipeline: CompiledRenderPipeline,
        val compiledBoxFillInteriorPipeline: CompiledRenderPipeline,
        val compiledWorldRevealPipeline: CompiledRenderPipeline,
        val compiledMaskWritePipeline: CompiledRenderPipeline,
        val compiledMaskCompositePipeline: CompiledRenderPipeline,
        val generation: Int,
    )

    private val logger = LoggerFactory.getLogger("visualclient-shadertoy")
    private val importPattern = Regex("""(?m)^\s*#moj_import\s+[<\"]([^>\"]+)[>\"]\s*$""")

    private val imagePassVertexShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_image_pass")
    private val boxFillVertexShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_box_fill")
    private val boxFillFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_box_fill")
    private val worldRevealVertexShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_world_reveal")
    private val worldRevealFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_world_reveal")
    private val maskWriteVertexShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_mask_write")
    private val maskWriteFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_mask_write")
    private val maskCompositeFragmentShaderId = Identifier.fromNamespaceAndPath("visualclient", "core/shadertoy_mask_composite")

    private val requiredCommonResources = listOf(
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_image_pass.vsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_box_fill.vsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_box_fill.fsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_world_reveal.vsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_world_reveal.fsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_mask_write.vsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_mask_write.fsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/shadertoy_mask_composite.fsh"),
    )

    private var lastAttemptProviderIdentity = 0
    private var resourcesReady = false
    private var generation = 0
    private val compiledPrograms = LinkedHashMap<ProgramDefinition, CompiledProgram>()

    fun initialize() {
        lastAttemptProviderIdentity = 0
        resourcesReady = false
        generation = 0
        compiledPrograms.clear()
    }

    fun loadCustomShaders(provider: ResourceProvider) {
        val providerIdentity = System.identityHashCode(provider)
        logger.debug("Shadertoy shader reload hook providerIdentity={}", providerIdentity)
        lastAttemptProviderIdentity = 0
        resourcesReady = false
        generation += 1
        compiledPrograms.clear()
        OffscreenShaderRenderer.invalidateAll()
    }

    fun ensureReady(definition: ProgramDefinition): Boolean {
        if (compiledPrograms.containsKey(definition)) {
            return true
        }

        val provider = Minecraft.getInstance().resourceManager
        val providerIdentity = System.identityHashCode(provider)
        if (providerIdentity != lastAttemptProviderIdentity) {
            compiledPrograms.clear()
            resourcesReady = false
            lastAttemptProviderIdentity = providerIdentity
        }

        val missingResources = (requiredCommonResources + definition.fragmentShaderSourceId).filterNot { hasResource(provider, it) }
        resourcesReady = missingResources.isEmpty()
        if (!resourcesReady) {
            logger.warn("Shadertoy pipeline disabled; missing shader resources [{}]", missingResources.joinToString { it.toString() })
            return false
        }

        val program = compileProgram(provider, definition) ?: return false
        compiledPrograms[definition] = program
        return true
    }

    fun compiledProgram(definition: ProgramDefinition): CompiledProgram? = compiledPrograms[definition]

    fun shutdown() {
        compiledPrograms.clear()
        resourcesReady = false
        ShadertoyChannels.invalidate()
        ShadertoyFrameProvider.shutdown()
        OffscreenShaderRenderer.shutdownAll()
        ShadertoyBoxRenderer.shutdown()
        ShadertoyWorldRevealRenderer.shutdown()
        ShadertoyMaskedWorldRenderer.shutdown()
    }

    private fun compileProgram(provider: ResourceProvider, definition: ProgramDefinition): CompiledProgram? {
        val imagePipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "${definition.id}_image_pass_pipeline"))
            .withVertexShader(imagePassVertexShaderId)
            .withFragmentShader(definition.imageFragmentShaderId)
            .withSampler("iChannel0")
            .withSampler("iChannel1")
            .withSampler("iChannel2")
            .withSampler("iChannel3")
            .withUniform("ShadertoyGlobals", UniformType.UNIFORM_BUFFER)
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLE_STRIP)
            .build()

        val boxFillPipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "shadertoy_box_fill_pipeline"))
            .withVertexShader(boxFillVertexShaderId)
            .withFragmentShader(boxFillFragmentShaderId)
            .withSampler("ShadertoyFrame")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("CompositeParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .build()

        val boxFillInteriorPipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "shadertoy_box_fill_interior_pipeline"))
            .withVertexShader(boxFillVertexShaderId)
            .withFragmentShader(boxFillFragmentShaderId)
            .withSampler("ShadertoyFrame")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("CompositeParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .build()

        val worldRevealPipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "shadertoy_world_reveal_pipeline"))
            .withVertexShader(worldRevealVertexShaderId)
            .withFragmentShader(worldRevealFragmentShaderId)
            .withSampler("ShadertoyFrame")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("CompositeParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .build()

        val maskWritePipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "shadertoy_mask_write_pipeline"))
            .withVertexShader(maskWriteVertexShaderId)
            .withFragmentShader(maskWriteFragmentShaderId)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("MaskParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .build()

        val maskCompositePipeline = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("visualclient", "shadertoy_mask_composite_pipeline"))
            .withVertexShader(imagePassVertexShaderId)
            .withFragmentShader(maskCompositeFragmentShaderId)
            .withSampler("ShadertoyFrame")
            .withSampler("MaskTexture")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLE_STRIP)
            .build()

        val shaderSource = ShaderSource { shaderLocation, shaderType ->
            val sourceId = shaderType.idConverter().idToFile(shaderLocation)
            val sourceText = provider.getResource(sourceId)
                .orElseThrow { FileNotFoundException("Missing shader source $sourceId") }
                .openAsReader()
                .use { it.readText() }
            resolveImports(provider, sourceText, shaderLocation.namespace, linkedSetOf(sourceId.toString()))
        }

        val device = RenderSystem.getDevice()
        val compiledImage = runCatching { device.precompilePipeline(imagePipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy image pass '{}'", definition.id, it)
                return null
            }
        val compiledBox = runCatching { device.precompilePipeline(boxFillPipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy box fill pipeline")
                return null
            }
        val compiledInteriorBox = runCatching { device.precompilePipeline(boxFillInteriorPipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy interior box fill pipeline")
                return null
            }
        val compiledWorldReveal = runCatching { device.precompilePipeline(worldRevealPipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy world reveal pipeline")
                return null
            }
        val compiledMaskWrite = runCatching { device.precompilePipeline(maskWritePipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy mask write pipeline")
                return null
            }
        val compiledMaskComposite = runCatching { device.precompilePipeline(maskCompositePipeline, shaderSource) }
            .getOrElse {
                logger.error("Failed to compile Shadertoy mask composite pipeline")
                return null
            }

        if (!compiledImage.isValid || !compiledBox.isValid || !compiledInteriorBox.isValid || !compiledWorldReveal.isValid || !compiledMaskWrite.isValid || !compiledMaskComposite.isValid) {
            logger.warn("Compiled Shadertoy pipeline invalid program='{}'", definition.id)
            return null
        }

        logger.info("Shadertoy pipelines compiled successfully program='{}'", definition.id)
        return CompiledProgram(
            definition = definition,
            imagePipeline = imagePipeline,
            boxFillPipeline = boxFillPipeline,
            boxFillInteriorPipeline = boxFillInteriorPipeline,
            worldRevealPipeline = worldRevealPipeline,
            maskWritePipeline = maskWritePipeline,
            maskCompositePipeline = maskCompositePipeline,
            compiledImagePipeline = compiledImage,
            compiledBoxFillPipeline = compiledBox,
            compiledBoxFillInteriorPipeline = compiledInteriorBox,
            compiledWorldRevealPipeline = compiledWorldReveal,
            compiledMaskWritePipeline = compiledMaskWrite,
            compiledMaskCompositePipeline = compiledMaskComposite,
            generation = generation,
        )
    }

    private fun hasResource(provider: ResourceProvider, resourceId: Identifier): Boolean {
        return runCatching {
            provider.getResource(resourceId).orElseThrow().openAsReader().use { }
            true
        }.getOrElse { false }
    }

    private fun resolveImports(
        provider: ResourceProvider,
        sourceText: String,
        shaderNamespace: String,
        visited: LinkedHashSet<String>,
    ): String {
        return importPattern.replace(sourceText) { match ->
            val includeRef = match.groupValues[1].trim()
            val includeId = parseIncludeIdentifier(includeRef, shaderNamespace)
            val includeKey = includeId.toString()
            check(visited.add(includeKey)) { "Recursive shader include detected: $includeKey" }
            val includeText = provider.getResource(includeId)
                .orElseThrow { FileNotFoundException("Missing shader include $includeId") }
                .openAsReader()
                .use { it.readText() }
            val resolved = resolveImports(provider, includeText, includeId.namespace, visited)
            visited.remove(includeKey)
            resolved
        }
    }

    private fun parseIncludeIdentifier(includeRef: String, fallbackNamespace: String): Identifier {
        val separator = includeRef.indexOf(':')
        val namespace = if (separator >= 0) includeRef.substring(0, separator) else fallbackNamespace
        val path = if (separator >= 0) includeRef.substring(separator + 1) else includeRef
        return Identifier.fromNamespaceAndPath(namespace, "shaders/include/$path")
    }
}
