package com.visualproject.client.hud.itembar

data class ItemBarHudPosition(
    val x: Int,
    val y: Int,
)

data class ItemBarHudBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in x until (x + width) && mouseY in y until (y + height)
    }
}

class ItemBarHudDragState(initialPosition: ItemBarHudPosition) {

    var position: ItemBarHudPosition = initialPosition
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
        position = ItemBarHudPosition(
            x = x.coerceIn(0, (screenWidth - hudWidth).coerceAtLeast(0)),
            y = y.coerceIn(0, (screenHeight - hudHeight).coerceAtLeast(0)),
        )
    }

    fun beginDrag(bounds: ItemBarHudBounds, mouseX: Int, mouseY: Int): Boolean {
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
