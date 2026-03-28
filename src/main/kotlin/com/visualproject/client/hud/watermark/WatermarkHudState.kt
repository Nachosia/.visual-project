package com.visualproject.client.hud.watermark

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen

data class HudBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height

    fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }
}

enum class WatermarkHudBlockId(val key: String) {
    CLASSIC("classic"),
    INFO_TOP("info_top"),
    INFO_BOTTOM("info_bottom");

    companion object {
        fun fromKey(raw: String?): WatermarkHudBlockId? {
            return entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

data class WatermarkHudPosition(
    val x: Int,
    val y: Int,
)

data class WatermarkHudBlockBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in x until (x + width) && mouseY in y until (y + height)
    }
}

enum class WatermarkMode {
    DEFAULT,
    MUSIC,
}

data class WatermarkRenderState(
    val mode: WatermarkMode,
    val track: WatermarkTrackInfo?,
    val canExpand: Boolean,
    val isHovered: Boolean,
    val targetExpanded: Float,
)

class WatermarkStateCalculator(
    private val musicProvider: WatermarkMusicProvider,
) {
    fun resolve(client: Minecraft, bounds: HudBounds, mouseX: Int, mouseY: Int): WatermarkRenderState {
        val activeTrack = musicProvider.currentTrack(client)
        val mode = if (activeTrack != null) WatermarkMode.MUSIC else WatermarkMode.DEFAULT

        val currentScreen = client.screen
        val canExpand = activeTrack != null && (currentScreen is ChatScreen || currentScreen != null)
        val hovered = bounds.contains(mouseX, mouseY)
        val targetExpanded = if (canExpand && hovered) 1f else 0f

        return WatermarkRenderState(
            mode = mode,
            track = activeTrack,
            canExpand = canExpand,
            isHovered = hovered,
            targetExpanded = targetExpanded,
        )
    }
}
