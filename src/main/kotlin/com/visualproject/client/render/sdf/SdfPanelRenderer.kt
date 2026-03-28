package com.visualproject.client.render.sdf

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.visualproject.client.VisualThemeSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.ceil
import kotlin.math.max

object SdfPanelRenderer {
    private val logger = LoggerFactory.getLogger("visualclient-sdf-render")
    private val guiProjectionMatrixBuffer = CachedOrthoProjectionMatrixBuffer(
        "visualclient_sdf_gui",
        1000f,
        11000f,
        true,
    )
    private var loggedDrawSubmitted = false
    private var loggedPipelineUnavailable = false
    private var loggedMissingColorTarget = false

    data class ClipRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    fun draw(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        style: SdfPanelStyle,
        clipRect: ClipRect? = null,
    ) {
        if (!SdfShaderRegistry.ensureReady()) {
            if (!loggedPipelineUnavailable) {
                logger.warn("SDF panel draw skipped: pipeline unavailable")
                loggedPipelineUnavailable = true
            }
            return
        }
        val resolvedStyle = resolveStyle(style, width, height)
        val pipeline = SdfShaderRegistry.panelPipeline ?: run {
            if (!loggedPipelineUnavailable) {
                logger.warn("SDF panel draw skipped: pipeline missing after ensureReady")
                loggedPipelineUnavailable = true
            }
            return
        }

        val device = RenderSystem.getDevice()
        val renderPadding = computeRenderPadding(resolvedStyle)
        val resolvedClip = clipRect ?: ClipRect(
            x - renderPadding,
            y - renderPadding,
            width + (renderPadding * 2),
            height + (renderPadding * 2),
        )
        val vertexBuffer = SdfQuadRenderer.createQuadBuffer(
            device,
            (x - renderPadding).toFloat(),
            (y - renderPadding).toFloat(),
            (width + (renderPadding * 2)).toFloat(),
            (height + (renderPadding * 2)).toFloat(),
        )
        val uniformBuffer = device.createBuffer(
            { "visualclient_sdf_panel_style" },
            GpuBuffer.USAGE_UNIFORM,
            SdfUniformWriter.createPanelStyleBuffer(
                x.toFloat(),
                y.toFloat(),
                width.toFloat(),
                height.toFloat(),
                resolvedClip.x.toFloat(),
                resolvedClip.y.toFloat(),
                resolvedClip.width.toFloat(),
                resolvedClip.height.toFloat(),
                resolvedStyle,
            ),
        )
        val modelViewMat = Matrix4f()
            .setTranslation(0f, 0f, -11000f)
            .mul(context.pose())
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            modelViewMat,
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f(),
        )

        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val target = minecraft.mainRenderTarget
        val guiProjection = guiProjectionMatrixBuffer.getBuffer(
            window.width / window.guiScale.toFloat(),
            window.height / window.guiScale.toFloat(),
        )

        context.renderDeferredElements()
        RenderSystem.backupProjectionMatrix()
        try {
            RenderSystem.setProjectionMatrix(guiProjection, ProjectionType.ORTHOGRAPHIC)
            val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: run {
                if (!loggedMissingColorTarget) {
                    logger.warn("SDF panel draw skipped: no color target available")
                    loggedMissingColorTarget = true
                }
                return
            }
            val depthView = RenderSystem.outputDepthTextureOverride ?: if (target.useDepth) target.depthTextureView else null
            val backdropTextureView = BackdropBlurRenderer.textureView()
            val backdropSampler = BackdropBlurRenderer.sampler()
            val encoder = device.createCommandEncoder()
            val renderPass = if (depthView != null) {
                encoder.createRenderPass(
                    { "visualclient_sdf_panel_pass" },
                    colorView,
                    OptionalInt.empty(),
                    depthView,
                    OptionalDouble.empty(),
                )
            } else {
                encoder.createRenderPass(
                    { "visualclient_sdf_panel_pass" },
                    colorView,
                    OptionalInt.empty(),
                )
            }
            try {
                renderPass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(renderPass)
                renderPass.setUniform("DynamicTransforms", dynamicTransforms)
                renderPass.setUniform("PanelStyle", uniformBuffer)
                renderPass.bindTexture(
                    "BackdropTexture",
                    backdropTextureView,
                    backdropSampler,
                )
                renderPass.setVertexBuffer(0, vertexBuffer)
                renderPass.draw(0, 4)
                if (!loggedDrawSubmitted) {
                    logger.info(
                        "SDF panel draw submitted x={} y={} width={} height={} padding={}",
                        x,
                        y,
                        width,
                        height,
                        renderPadding,
                    )
                    loggedDrawSubmitted = true
                }
            } finally {
                renderPass.close()
            }
        } finally {
            RenderSystem.restoreProjectionMatrix()
            uniformBuffer.close()
            vertexBuffer.close()
            context.renderDeferredElements()
        }
    }

    private fun resolveStyle(style: SdfPanelStyle, width: Int, height: Int): SdfPanelStyle {
        if (!VisualThemeSettings.isTransparentPreset()) {
            return style
        }

        val backdrop = if (style.backdrop.enabled()) {
            style.backdrop
        } else if (shouldAutoEnableBackdrop(style, width, height)) {
            VisualThemeSettings.defaultGlassBackdrop()
        } else {
            SdfBackdropStyle.NONE
        }

        return style.copy(
            outerGlow = SdfGlowStyle(0x00000000, 0f, 0f, 0f),
            neonBorder = SdfNeonBorderStyle.NONE,
            backdrop = backdrop,
        )
    }

    private fun shouldAutoEnableBackdrop(style: SdfPanelStyle, width: Int, height: Int): Boolean {
        val alpha = (style.baseColor ushr 24) and 0xFF
        val largeEnough = (width >= 72 && height >= 18) || (width >= 40 && height >= 40) || height >= 72
        return alpha in 1..239 && style.radiusPx >= 6f && style.borderWidthPx > 0f && largeEnough
    }

    private fun computeRenderPadding(style: SdfPanelStyle): Int {
        val glowPadding = if (style.outerGlow.strength > 0f && style.outerGlow.opacity > 0f) {
            style.outerGlow.radiusPx
        } else {
            0f
        }
        val neonAlpha = (style.neonBorder.color ushr 24) and 0xFF
        val neonPadding = if (style.neonBorder.strength > 0f && neonAlpha > 0) {
            (style.neonBorder.widthPx * 0.5f) + style.neonBorder.softnessPx + style.borderWidthPx
        } else {
            0f
        }
        val edgePadding = max(style.borderWidthPx, 1f)
        return ceil(max(max(glowPadding, neonPadding), edgePadding) + 2f).toInt().coerceAtLeast(0)
    }
}
