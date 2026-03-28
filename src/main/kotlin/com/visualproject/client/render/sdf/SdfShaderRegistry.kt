package com.visualproject.client.render.sdf

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
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceProvider
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.util.LinkedHashSet

object SdfShaderRegistry {
    private val logger = LoggerFactory.getLogger("visualclient-sdf")
    private val importPattern = Regex("""(?m)^\s*#moj_import\s+[<"]([^>"]+)[>"]\s*$""")

    private val pipelineId = Identifier.fromNamespaceAndPath("visualclient", "sdf_panel_pipeline")
    private val shaderId = Identifier.fromNamespaceAndPath("visualclient", "core/sdf_panel")
    private val requiredResources = listOf(
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/sdf_panel.vsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/core/sdf_panel.fsh"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/include/sdf_shapes.glsl"),
        Identifier.fromNamespaceAndPath("visualclient", "shaders/include/sdf_lighting.glsl"),
    )

    var panelPipeline: RenderPipeline? = null
        private set

    private var compiledPipeline: CompiledRenderPipeline? = null

    var resourcesReady: Boolean = false
        private set

    var pipelineReady: Boolean = false
        private set

    private var lastMissingResources: List<Identifier> = emptyList()
    private var lastAttemptProviderIdentity: Int = 0
    private var lastAttemptSucceeded = false

    fun registerEvent() {
        panelPipeline = null
        compiledPipeline = null
        resourcesReady = false
        pipelineReady = false
        lastMissingResources = emptyList()
        lastAttemptProviderIdentity = 0
        lastAttemptSucceeded = false
    }

    fun loadCustomShaders(provider: ResourceProvider) {
        compileIfPossible(provider, emitMissingWarning = false, reason = "hook")
    }

    fun ensureReady(): Boolean {
        if (pipelineReady) {
            return true
        }

        val minecraft = Minecraft.getInstance()
        val provider = minecraft.getResourceManager()
        return compileIfPossible(provider, emitMissingWarning = true, reason = "runtime")
    }

    private fun compileIfPossible(
        provider: ResourceProvider,
        emitMissingWarning: Boolean,
        reason: String,
    ): Boolean {
        val providerIdentity = System.identityHashCode(provider)
        if (pipelineReady && providerIdentity == lastAttemptProviderIdentity) {
            return true
        }
        if (providerIdentity == lastAttemptProviderIdentity && !lastAttemptSucceeded) {
            return false
        }

        panelPipeline = null
        compiledPipeline = null
        pipelineReady = false

        val missingResources = requiredResources.filterNot { hasResource(provider, it) }
        resourcesReady = missingResources.isEmpty()

        if (!resourcesReady) {
            if (emitMissingWarning) {
                lastMissingResources = missingResources
                lastAttemptProviderIdentity = providerIdentity
                lastAttemptSucceeded = false
            }
            val missingSummary = missingResources.joinToString { it.toString() }
            if (emitMissingWarning) {
                logger.warn("SDF panel pipeline disabled; missing shader resources [{}]", missingSummary)
            } else {
                logger.debug("SDF hook '{}' saw incomplete shader resources [{}]", reason, missingSummary)
            }
            return false
        }

        val pipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(pipelineId)
            .withVertexShader(shaderId)
            .withFragmentShader(shaderId)
            .withSampler("BackdropTexture")
            .withUniform("PanelStyle", UniformType.UNIFORM_BUFFER)
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
            resolveImports(
                provider = provider,
                sourceText = sourceText,
                shaderNamespace = shaderLocation.namespace,
                visited = linkedSetOf(sourceId.toString()),
            )
        }

        val compiled = runCatching {
            RenderSystem.getDevice().precompilePipeline(pipeline, shaderSource)
        }.getOrElse { throwable ->
            logger.error("Failed to compile SDF panel pipeline during {}", reason, throwable)
            null
        }

        panelPipeline = pipeline
        compiledPipeline = compiled
        pipelineReady = compiled?.isValid == true
        lastMissingResources = emptyList()
        lastAttemptProviderIdentity = providerIdentity
        lastAttemptSucceeded = pipelineReady

        if (pipelineReady) {
            logger.info("SDF panel pipeline compiled successfully ({})", reason)
        } else {
            logger.warn("SDF panel pipeline compiled invalid or unavailable ({})", reason)
        }

        return pipelineReady
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
