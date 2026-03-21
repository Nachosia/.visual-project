package com.visualproject.client.render.sdf

object SdfPanelPresets {
    fun testBackdrop(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF0111318.toInt(),
        borderColor = 0xFF232A33.toInt(),
        borderWidthPx = 1f,
        radiusPx = 22f,
        innerGlow = SdfGlowStyle(0xFFB3C7FF.toInt(), radiusPx = 18f, strength = 0.10f, opacity = 0.10f),
        outerGlow = SdfGlowStyle(0xFF04070B.toInt(), radiusPx = 48f, strength = 0.35f, opacity = 0.72f),
        shade = SdfShadeStyle(topColor = 0x12FFFFFF, bottomColor = 0x22000000),
    )

    fun testRed(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF01A1118.toInt(),
        borderColor = 0xFF5D415B.toInt(),
        borderWidthPx = 1.5f,
        radiusPx = 18f,
        innerGlow = SdfGlowStyle(0xFFFF7AB8.toInt(), radiusPx = 16f, strength = 0.33f, opacity = 0.42f),
        outerGlow = SdfGlowStyle(0xFFB04B8A.toInt(), radiusPx = 34f, strength = 0.18f, opacity = 0.34f),
        shade = SdfShadeStyle(topColor = 0x18FFFFFF, bottomColor = 0x26000000),
    )

    fun testBlue(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF0101722.toInt(),
        borderColor = 0xFF3F6083.toInt(),
        borderWidthPx = 1f,
        radiusPx = 24f,
        innerGlow = SdfGlowStyle(0xFF6BB6FF.toInt(), radiusPx = 14f, strength = 0.35f, opacity = 0.45f),
        outerGlow = SdfGlowStyle(0xFF326DCC.toInt(), radiusPx = 38f, strength = 0.16f, opacity = 0.30f),
        shade = SdfShadeStyle(topColor = 0x16FFFFFF, bottomColor = 0x1A000000),
    )

    fun testGreen(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF0101C1D.toInt(),
        borderColor = 0xFF39696C.toInt(),
        borderWidthPx = 1.5f,
        radiusPx = 12f,
        innerGlow = SdfGlowStyle(0xFF7BE8E0.toInt(), radiusPx = 18f, strength = 0.28f, opacity = 0.40f),
        outerGlow = SdfGlowStyle(0xFF2D8E88.toInt(), radiusPx = 30f, strength = 0.16f, opacity = 0.30f),
        shade = SdfShadeStyle(topColor = 0x0EFFFFFF, bottomColor = 0x28000000),
    )

    fun shell(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF0121317.toInt(),
        borderColor = 0xFF2A2C36.toInt(),
        borderWidthPx = 1f,
        radiusPx = 12f,
        innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 4f, strength = 0.05f, opacity = 0.1f),
        outerGlow = SdfGlowStyle(0xFF000000.toInt(), radiusPx = 40f, strength = 0.3f, opacity = 0.6f),
        shade = SdfShadeStyle(topColor = 0x0AFFFFFF, bottomColor = 0x1A000000),
    )
}
