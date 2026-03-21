package com.visualproject.client.render.sdf

data class Vec4f(val r: Float, val g: Float, val b: Float, val a: Float)

object SdfColor {
    fun argb(color: Int): Vec4f = Vec4f(
        ((color ushr 16) and 0xFF) / 255f,
        ((color ushr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
        ((color ushr 24) and 0xFF) / 255f,
    )
}
