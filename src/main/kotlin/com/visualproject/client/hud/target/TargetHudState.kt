package com.visualproject.client.hud.target

data class TargetHudPosition(
    val x: Int,
    val y: Int,
)

data class TargetHudBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in x until (x + width) && mouseY in y until (y + height)
    }
}

object TargetHudLayout {
    const val width = 212
    const val height = 104
    const val radius = 18

    const val previewWidth = 74
    const val previewHeight = 86
    const val previewRadius = 16
    const val panelPadding = 10

    const val itemSlotSize = 17
    const val itemSlotGap = 4

    const val sliderTrackHeight = 4
    const val sliderGap = 3
    const val sliderHorizontalInset = 12
}

class TargetHudDragState(initialPosition: TargetHudPosition) {

    var position: TargetHudPosition = initialPosition
        private set

    var dragging: Boolean = false
        private set

    private var dragOffsetX = 0
    private var dragOffsetY = 0

    fun setPositionClamped(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hudWidth: Int,
        hudHeight: Int,
    ) {
        position = TargetHudPosition(
            x = x.coerceIn(0, (screenWidth - hudWidth).coerceAtLeast(0)),
            y = y.coerceIn(0, (screenHeight - hudHeight).coerceAtLeast(0)),
        )
    }

    fun beginDrag(bounds: TargetHudBounds, mouseX: Int, mouseY: Int): Boolean {
        if (!bounds.contains(mouseX, mouseY)) return false
        dragging = true
        dragOffsetX = mouseX - bounds.x
        dragOffsetY = mouseY - bounds.y
        return true
    }

    fun dragTo(
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int,
        hudWidth: Int,
        hudHeight: Int,
    ) {
        if (!dragging) return
        val targetX = mouseX - dragOffsetX
        val targetY = mouseY - dragOffsetY
        setPositionClamped(targetX, targetY, screenWidth, screenHeight, hudWidth, hudHeight)
    }

    fun endDrag(): Boolean {
        val wasDragging = dragging
        dragging = false
        return wasDragging
    }
}
