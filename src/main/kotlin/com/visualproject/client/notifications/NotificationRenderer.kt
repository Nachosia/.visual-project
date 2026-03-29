package com.visualproject.client.notifications

import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.hud.HudOcclusionRegistry
import com.visualproject.client.render.sdf.BackdropBlurRenderer
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.ui.menu.drawRoundedPanel
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen

internal class NotificationRenderer {

    private object Layout {
        const val marginTop = 12
        const val marginRight = 12
        const val toastHeight = 34
        const val toastGap = 8
        const val minWidth = 168
        const val maxWidth = 292
        const val paddingLeft = 14
        const val paddingRight = 14
        const val markerSize = 8
        const val markerGap = 10
    }

    fun render(context: GuiGraphics, client: Minecraft) {
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return

        val toasts = NotificationManager.snapshot()
        if (toasts.isEmpty()) return

        val font = client.font
        val screenWidth = client.window.guiScaledWidth
        var currentY = Layout.marginTop
        val now = System.currentTimeMillis()
        BackdropBlurRenderer.captureBackdrop()

        toasts.forEach { toast ->
            val alpha = toastAlpha(toast, now)
            if (alpha <= 0f) return@forEach

            val textWidth = font.width(vText(toast.text))
            val maxTextWidth = Layout.maxWidth - (Layout.paddingLeft + Layout.markerSize + Layout.markerGap + Layout.paddingRight)
            val clippedText = font.substrByWidth(vText(toast.text), maxTextWidth).string
            val width = (Layout.paddingLeft + Layout.markerSize + Layout.markerGap + textWidth + Layout.paddingRight)
                .coerceIn(Layout.minWidth, Layout.maxWidth)
            val x = screenWidth - width - Layout.marginRight
            val y = currentY
            currentY += Layout.toastHeight + Layout.toastGap
            HudOcclusionRegistry.mark(x, y, width, Layout.toastHeight)

            SdfPanelRenderer.draw(
                context = context,
                x = x,
                y = y,
                width = width,
                height = Layout.toastHeight,
                style = toastStyle(alpha, toast.textColor),
            )

            val markerY = y + (Layout.toastHeight - Layout.markerSize) / 2
            drawRoundedPanel(
                context,
                x + Layout.paddingLeft,
                markerY,
                Layout.markerSize,
                Layout.markerSize,
                withAlpha(blendColor(0xFF101723.toInt(), toast.textColor, 0.55f), alpha),
                withAlpha(blendColor(toast.textColor, 0xFFFFFFFF.toInt(), 0.20f), alpha),
                4,
            )
            val textColor = withAlpha(toast.textColor, alpha)
            context.drawString(
                font,
                vText(clippedText),
                x + Layout.paddingLeft + Layout.markerSize + Layout.markerGap,
                y + 12,
                textColor,
                false,
            )
        }
    }

    private fun toastStyle(alpha: Float, textColor: Int): SdfPanelStyle {
        val neon = blendColor(VisualThemeSettings.neonBorder(), textColor, 0.26f)
        val glow = blendColor(VisualThemeSettings.accentStrong(), textColor, 0.22f)
        return SdfPanelStyle(
            baseColor = withAlpha(VisualThemeSettings.hudShellFill(), alpha),
            borderColor = withAlpha(VisualThemeSettings.hudShellBorder(), alpha),
            borderWidthPx = 1.1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(withAlpha(0xFFFFFFFF.toInt(), alpha), radiusPx = 10f, strength = 0.03f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(withAlpha(if (VisualThemeSettings.isLightPreset()) VisualThemeSettings.themedAccentGlowBase(glow) else glow, alpha), radiusPx = 18f, strength = if (VisualThemeSettings.isLightPreset()) 0.10f else 0.14f, opacity = if (VisualThemeSettings.isLightPreset()) 0.07f else 0.09f),
            shade = SdfShadeStyle(withAlpha(VisualThemeSettings.hudShellShadeTop(), alpha), withAlpha(VisualThemeSettings.hudShellShadeBottom(), alpha)),
            neonBorder = SdfNeonBorderStyle(withAlpha(neon, alpha), widthPx = 0.95f, softnessPx = 4.5f, strength = if (VisualThemeSettings.isLightPreset()) 0.26f else 0.42f),
        )
    }

    private fun toastAlpha(toast: NotificationToast, nowMs: Long): Float {
        val age = (nowMs - toast.createdAtMs).coerceAtLeast(0L)
        val remaining = (toast.lifetimeMs - age).coerceAtLeast(0L)
        val fadeIn = 180f
        val fadeOut = 220f
        val fadeInAlpha = (age / fadeIn).coerceIn(0f, 1f)
        val fadeOutAlpha = (remaining / fadeOut).coerceIn(0f, 1f)
        return minOf(fadeInAlpha, fadeOutAlpha)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (((color ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
