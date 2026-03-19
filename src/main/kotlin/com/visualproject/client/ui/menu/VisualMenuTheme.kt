package com.visualproject.client.ui.menu

import com.visualproject.client.vText
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.MutableComponent
import kotlin.math.min
import kotlin.math.sqrt

internal object VisualMenuTheme {
    const val frameGap = 6

    const val panelFill = 0xFF0A0F19.toInt()
    const val panelBorder = 0xFF2A3650.toInt()
    const val panelRadius = 28
    const val panelPadding = 14
    const val panelGap = 8
    const val panelTopGlowStart = 0xAD253154.toInt()
    const val panelTopGlowMid = 0x751C2843
    const val panelTopGlowEnd = 0x00131B2A
    const val panelBottomShade = 0xD0080C15.toInt()

    const val headerFill = 0xFF0E1627.toInt()
    const val headerBorder = 0xFF354566.toInt()
    const val headerRadius = 18
    const val headerHeight = 44
    const val headerGap = 8

    const val topBarGap = 10
    const val tabHeight = 28

    const val searchFill = 0xFF121B2D.toInt()
    const val searchBorder = 0xFF3B4D71.toInt()
    const val searchRadius = 14
    const val searchHeight = 30
    const val searchIconLeft = "?"
    const val searchIconRight = "?"

    const val moduleBodyFill = 0xFF0C1424.toInt()
    const val moduleBodyBorder = 0xFF2A3753.toInt()
    const val moduleBodyRadius = 18
    const val moduleBodyPadding = 12
    const val moduleRowsSideInset = 4
    const val moduleViewportRightInset = 7

    const val moduleRowGap = 10
    const val moduleColumnGap = 10
    const val moduleCardHeight = 48
    const val moduleRowHeight = moduleCardHeight + 10
    const val moduleListTopInset = 6
    const val moduleListBottomInset = 16

    const val cardFill = 0xFF111A2C.toInt()
    const val cardBorder = 0xFF2F3D5B.toInt()
    const val cardHoverFill = 0xFF172338.toInt()
    const val cardHoverBorder = 0xFF50618D.toInt()
    const val cardEnabledFill = 0xFF152037.toInt()
    const val cardEnabledBorder = 0xFF6077AC.toInt()
    const val cardExpandedFill = 0xFF1A2740.toInt()
    const val cardExpandedBorder = 0xFF8774DE.toInt()
    const val cardRadius = 16

    const val settingsFill = 0xFF10182A.toInt()
    const val settingsBorder = 0xFF3C4D70.toInt()
    const val settingsRadius = 16
    const val settingsHeight = 92

    const val iconSlotFill = 0x8F141D33.toInt()
    const val iconSlotBorder = 0x7A3A4D73
    const val iconSlotRadius = 9

    const val toggleWidth = 38
    const val toggleHeight = 20
    const val toggleRightInset = 12

    const val dockFill = 0xFF10182A.toInt()
    const val dockBorder = 0xFF3A4B6F.toInt()
    const val dockRadius = 18
    const val dockHeight = 48
    const val dockGap = 6
    const val dockWidthRatio = 0.68f

    const val accent = 0xFF7D5BFF.toInt()
    const val accentStrong = 0xFF8A71FF.toInt()

    const val textPrimary = 0xFFF4F6FF.toInt()
    const val textSecondary = 0xFFA2ACCA.toInt()
    const val textMuted = 0xFF7782A2.toInt()
    const val textDim = 0xFF69748F.toInt()

    const val scrollbarColor = 0x66505A7A
    const val scrollStep = 12
}

internal fun fitStyledText(font: Font, value: String, maxWidth: Int): MutableComponent {
    val safeWidth = maxWidth.coerceAtLeast(12)
    val full = vText(value)
    if (font.width(full) <= safeWidth) return full

    var trimmed = value
    while (trimmed.isNotEmpty() && font.width(vText("$trimmed...")) > safeWidth) {
        trimmed = trimmed.dropLast(1)
    }

    return if (trimmed.isEmpty()) vText("...") else vText("$trimmed...")
}

internal fun blendColor(from: Int, to: Int, t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)

    val fromA = (from ushr 24) and 0xFF
    val fromR = (from ushr 16) and 0xFF
    val fromG = (from ushr 8) and 0xFF
    val fromB = from and 0xFF

    val toA = (to ushr 24) and 0xFF
    val toR = (to ushr 16) and 0xFF
    val toG = (to ushr 8) and 0xFF
    val toB = to and 0xFF

    val outA = (fromA + ((toA - fromA) * clamped)).toInt().coerceIn(0, 255)
    val outR = (fromR + ((toR - fromR) * clamped)).toInt().coerceIn(0, 255)
    val outG = (fromG + ((toG - fromG) * clamped)).toInt().coerceIn(0, 255)
    val outB = (fromB + ((toB - fromB) * clamped)).toInt().coerceIn(0, 255)

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}

internal fun drawRoundedPanel(
    context: GuiGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    fillColor: Int,
    borderColor: Int,
    radius: Int = 12,
) {
    if (width <= 0 || height <= 0) return

    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)
    fillRoundedRect(context, x, y, width, height, cornerRadius, borderColor)

    val innerInset = if (cornerRadius >= 16 && width > 8 && height > 8) 2 else 1
    if (width > innerInset * 2 && height > innerInset * 2) {
        fillRoundedRect(
            context,
            x + innerInset,
            y + innerInset,
            width - (innerInset * 2),
            height - (innerInset * 2),
            (cornerRadius - innerInset).coerceAtLeast(0),
            fillColor,
        )
    }
}

internal fun fillRoundedRect(
    context: GuiGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    radius: Int,
    color: Int,
) {
    if (width <= 0 || height <= 0) return

    if (radius <= 0) {
        context.fill(x, y, x + width, y + height, color)
        return
    }

    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)

    context.fill(x + cornerRadius, y, x + width - cornerRadius, y + height, color)
    context.fill(x, y + cornerRadius, x + cornerRadius, y + height - cornerRadius, color)
    context.fill(x + width - cornerRadius, y + cornerRadius, x + width, y + height - cornerRadius, color)

    for (row in 0 until cornerRadius) {
        val dy = cornerRadius - row - 1
        val dx = sqrt((cornerRadius * cornerRadius - dy * dy).toDouble()).toInt().coerceAtMost(cornerRadius)
        val left = x + cornerRadius - dx
        val right = x + width - cornerRadius + dx

        context.fill(left, y + row, right, y + row + 1, color)
        context.fill(left, y + height - row - 1, right, y + height - row, color)
    }
}

internal fun drawVerticalGradient(
    context: GuiGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    topColor: Int,
    bottomColor: Int,
) {
    if (width <= 0 || height <= 0) return
    context.fillGradient(x, y, x + width, y + height, topColor, bottomColor)
}
