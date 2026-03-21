package com.visualproject.client.render.sdf

import java.nio.ByteBuffer
import java.nio.ByteOrder

object SdfUniformWriter {
    private const val VEC4_BYTES = 16
    const val PANEL_STYLE_BYTES = VEC4_BYTES * 13

    fun createPanelStyleBuffer(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        clipX: Float,
        clipY: Float,
        clipWidth: Float,
        clipHeight: Float,
        style: SdfPanelStyle,
    ): ByteBuffer {
        return ByteBuffer.allocateDirect(PANEL_STYLE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                putVec4(x, y, width, height)
                putVec4(style.radiusPx, style.borderWidthPx, 0f, 0f)
                putVec4(style.innerGlow.radiusPx, style.innerGlow.strength, style.innerGlow.opacity, 0f)
                putVec4(style.outerGlow.radiusPx, style.outerGlow.strength, style.outerGlow.opacity, 0f)
                putVec4(SdfColor.argb(style.baseColor))
                putVec4(SdfColor.argb(style.borderColor))
                putVec4(SdfColor.argb(style.innerGlow.color))
                putVec4(SdfColor.argb(style.outerGlow.color))
                putVec4(style.neonBorder.widthPx, style.neonBorder.softnessPx, style.neonBorder.strength, 0f)
                putVec4(SdfColor.argb(style.neonBorder.color))
                putVec4(SdfColor.argb(style.shade.topColor))
                putVec4(SdfColor.argb(style.shade.bottomColor))
                putVec4(clipX, clipY, clipWidth, clipHeight)
                flip()
            }
    }

    private fun ByteBuffer.putVec4(x: Float, y: Float, z: Float, w: Float) {
        putFloat(x)
        putFloat(y)
        putFloat(z)
        putFloat(w)
    }

    private fun ByteBuffer.putVec4(vec: Vec4f) {
        putVec4(vec.r, vec.g, vec.b, vec.a)
    }
}
