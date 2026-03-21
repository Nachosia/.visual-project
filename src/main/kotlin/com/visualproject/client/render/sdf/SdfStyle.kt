package com.visualproject.client.render.sdf

data class SdfGlowStyle(
    val color: Int,
    val radiusPx: Float,
    val strength: Float,
    val opacity: Float,
)

data class SdfNeonBorderStyle(
    val color: Int,
    val widthPx: Float,
    val softnessPx: Float,
    val strength: Float,
) {
    companion object {
        val NONE = SdfNeonBorderStyle(
            color = 0x00000000,
            widthPx = 0f,
            softnessPx = 0f,
            strength = 0f,
        )
    }
}

data class SdfShadeStyle(
    val topColor: Int,
    val bottomColor: Int,
)

data class SdfPanelStyle(
    val baseColor: Int,
    val borderColor: Int,
    val borderWidthPx: Float,
    val radiusPx: Float,
    val innerGlow: SdfGlowStyle,
    val outerGlow: SdfGlowStyle,
    val shade: SdfShadeStyle,
    val neonBorder: SdfNeonBorderStyle = SdfNeonBorderStyle.NONE,
)
