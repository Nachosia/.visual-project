package com.visualproject.client.hud.test

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualClientMod
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfPanelPresets
import com.visualproject.client.render.sdf.SdfPanelRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object TestSdfHud {
    fun renderTest(context: GuiGraphics, screenWidth: Int, screenHeight: Int) {
        val outerGlowEnabled = ModuleStateStore.isSettingEnabled("${VisualClientMod.sdfTestModuleId}:outer_glow")
        val cardSize = 100
        val gap = 34
        val backdropWidth = (cardSize * 3) + (gap * 2) + 72
        val backdropHeight = cardSize + 84
        val backdropX = (screenWidth - backdropWidth) / 2
        val backdropY = (screenHeight - backdropHeight) / 2
        val cardsY = backdropY + 44
        val firstCardX = backdropX + 36
        val font = Minecraft.getInstance().font

        SdfPanelRenderer.draw(
            context = context,
            x = backdropX,
            y = backdropY,
            width = backdropWidth,
            height = backdropHeight,
            style = withOuterGlow(SdfPanelPresets.testBackdrop(), outerGlowEnabled),
        )

        context.drawString(font, "SDF Test", backdropX + 18, backdropY + 16, 0xFFF0F3FF.toInt(), false)
        context.drawString(font, "Inner glow + outer glow + soft backdrop", backdropX + 18, backdropY + 29, 0xFF8C93A5.toInt(), false)

        SdfPanelRenderer.draw(context, firstCardX, cardsY, cardSize, cardSize, withOuterGlow(SdfPanelPresets.testRed(), outerGlowEnabled))
        SdfPanelRenderer.draw(context, firstCardX + cardSize + gap, cardsY, cardSize, cardSize, withOuterGlow(SdfPanelPresets.testBlue(), outerGlowEnabled))
        SdfPanelRenderer.draw(context, firstCardX + ((cardSize + gap) * 2), cardsY, cardSize, cardSize, withOuterGlow(SdfPanelPresets.testGreen(), outerGlowEnabled))

        context.drawCenteredString(font, "Inner", firstCardX + (cardSize / 2), cardsY + cardSize + 10, 0xFFAEB4C6.toInt())
        context.drawCenteredString(font, "Soft", firstCardX + cardSize + gap + (cardSize / 2), cardsY + cardSize + 10, 0xFFAEB4C6.toInt())
        context.drawCenteredString(font, "Outer", firstCardX + ((cardSize + gap) * 2) + (cardSize / 2), cardsY + cardSize + 10, 0xFFAEB4C6.toInt())
    }

    private fun withOuterGlow(style: SdfPanelStyle, enabled: Boolean): SdfPanelStyle {
        if (enabled) return style
        return style.copy(
            outerGlow = SdfGlowStyle(
                color = style.outerGlow.color,
                radiusPx = style.outerGlow.radiusPx,
                strength = 0f,
                opacity = 0f,
            )
        )
    }
}
