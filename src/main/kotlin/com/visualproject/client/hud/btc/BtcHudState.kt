package com.visualproject.client.hud.btc

enum class BtcBlockId(val key: String) {
    BPS("bps"),
    TPS("tps"),
    COORDS("coords");

    companion object {
        fun fromKey(raw: String?): BtcBlockId? {
            return entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

data class BtcHudPosition(
    val x: Int,
    val y: Int,
)

data class BtcHudBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in x until (x + width) && mouseY in y until (y + height)
    }
}
