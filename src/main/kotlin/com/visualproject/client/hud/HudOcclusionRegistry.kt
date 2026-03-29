package com.visualproject.client.hud

object HudOcclusionRegistry {
    private data class Entry(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val tick: Long,
    )

    private val entries = ArrayList<Entry>()
    private var currentTick = 0L

    fun advanceTick() {
        currentTick++
        trim()
    }

    fun mark(x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        entries += Entry(x, y, width, height, currentTick)
        trim()
    }

    fun visibilityAt(centerX: Float, centerY: Float, radius: Float): Float {
        if (radius <= 0f) return 1f
        var visibility = 1f
        entries.forEach { entry ->
            if (entry.tick < (currentTick - 1)) return@forEach
            val signedDistance = signedDistanceToRect(centerX, centerY, entry)
            val fadeStart = radius * 0.85f
            val fadeEnd = -radius
            val next = smoothVisibility(signedDistance, fadeStart, fadeEnd)
            if (next < visibility) {
                visibility = next
            }
        }
        return visibility.coerceIn(0f, 1f)
    }

    private fun trim() {
        entries.removeIf { it.tick < (currentTick - 2) }
    }

    private fun signedDistanceToRect(x: Float, y: Float, entry: Entry): Float {
        val rectCenterX = entry.x + (entry.width * 0.5f)
        val rectCenterY = entry.y + (entry.height * 0.5f)
        val halfWidth = entry.width * 0.5f
        val halfHeight = entry.height * 0.5f
        val qx = kotlin.math.abs(x - rectCenterX) - halfWidth
        val qy = kotlin.math.abs(y - rectCenterY) - halfHeight
        val outsideX = qx.coerceAtLeast(0f)
        val outsideY = qy.coerceAtLeast(0f)
        val outside = kotlin.math.sqrt((outsideX * outsideX) + (outsideY * outsideY))
        val inside = minOf(maxOf(qx, qy), 0f)
        return outside + inside
    }

    private fun smoothVisibility(distance: Float, fadeStart: Float, fadeEnd: Float): Float {
        if (distance >= fadeStart) return 1f
        if (distance <= fadeEnd) return 0f
        val t = ((distance - fadeEnd) / (fadeStart - fadeEnd)).coerceIn(0f, 1f)
        return t * t * (3f - (2f * t))
    }
}
