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
        baseColor = 0xF0101319.toInt(),
        borderColor = 0xFFFF73C8.toInt(),
        borderWidthPx = 1.5f,
        radiusPx = 18f,
        innerGlow = SdfGlowStyle(0xFFFF8FD2.toInt(), radiusPx = 16f, strength = 0.28f, opacity = 0.32f),
        outerGlow = SdfGlowStyle(0xFFFF63BE.toInt(), radiusPx = 34f, strength = 0.20f, opacity = 0.38f),
        shade = SdfShadeStyle(topColor = 0x0AFFFFFF, bottomColor = 0x12000000),
    )

    fun testBlue(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF010141A.toInt(),
        borderColor = 0xFF69B6FF.toInt(),
        borderWidthPx = 1f,
        radiusPx = 24f,
        innerGlow = SdfGlowStyle(0xFF7CC2FF.toInt(), radiusPx = 14f, strength = 0.28f, opacity = 0.34f),
        outerGlow = SdfGlowStyle(0xFF4F9DFF.toInt(), radiusPx = 38f, strength = 0.18f, opacity = 0.34f),
        shade = SdfShadeStyle(topColor = 0x0AFFFFFF, bottomColor = 0x12000000),
    )

    fun testGreen(): SdfPanelStyle = SdfPanelStyle(
        baseColor = 0xF0101513.toInt(),
        borderColor = 0xFF72FFD9.toInt(),
        borderWidthPx = 1.5f,
        radiusPx = 12f,
        innerGlow = SdfGlowStyle(0xFF8AFFE4.toInt(), radiusPx = 18f, strength = 0.24f, opacity = 0.30f),
        outerGlow = SdfGlowStyle(0xFF53F2C7.toInt(), radiusPx = 30f, strength = 0.18f, opacity = 0.34f),
        shade = SdfShadeStyle(topColor = 0x08FFFFFF, bottomColor = 0x10000000),
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
