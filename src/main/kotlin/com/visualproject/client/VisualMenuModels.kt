package com.visualproject.client

enum class VisualMenuTab(val title: String) {
    VISUALS("Visuals"),
    HUD("HUD"),
    UTILITIES("Utilities"),
    THEME("Theme"),
}

data class VisualMenuModuleEntry(
    val id: String,
    val title: String,
    val iconGlyph: String,
    val tab: VisualMenuTab,
)

enum class VisualMenuDock(val icon: String) {
    HOME("H"),
    HUD("U"),
    SETTINGS("S"),
}
