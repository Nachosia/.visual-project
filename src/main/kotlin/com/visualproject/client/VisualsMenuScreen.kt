package com.visualproject.client

import com.daqem.uilib.gui.AbstractScreen
import com.daqem.uilib.gui.background.ColorBackground
import com.daqem.uilib.gui.widget.ScrollContainerWidget
import com.visualproject.client.ui.menu.DecorativeComponent
import com.visualproject.client.ui.menu.DockButtonWidget
import com.visualproject.client.ui.menu.EmptyStateComponent
import com.visualproject.client.ui.menu.ModuleRowComponent
import com.visualproject.client.ui.menu.ModuleSettingsPanelComponent
import com.visualproject.client.ui.menu.SearchBarComponent
import com.visualproject.client.ui.menu.SpacerComponent
import com.visualproject.client.ui.menu.TabChipWidget
import com.visualproject.client.ui.menu.VisualMenuTheme
import com.visualproject.client.ui.menu.drawRoundedPanel
import com.visualproject.client.ui.menu.drawVerticalGradient
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import kotlin.math.min

internal enum class VisualMenuTab(val title: String) {
    VISUALS("Visuals"),
    HUD("HUD"),
    UTILITIES("Utilities"),
}

internal enum class VisualMenuDock(val icon: String, val label: String) {
    SETTINGS("S", "Settings"),
    PROFILE("P", "Profile"),
    CHAT("C", "Chat"),
    MODPACK("M", "Modpack"),
    DOWNLOAD("D", "Download"),
}

internal data class VisualMenuModuleEntry(
    val id: String,
    val title: String,
    val iconGlyph: String,
    val tab: VisualMenuTab,
)

class VisualsMenuScreen : AbstractScreen(Component.empty()) {

    private var activeTab = VisualMenuTab.VISUALS
    private var activeDock = VisualMenuDock.SETTINGS
    private var searchQuery = ""
    private var openedModuleSettingsId: String? = null

    private val toggleProgressById: MutableMap<String, Float> = mutableMapOf()
    private val cardHoverProgressById: MutableMap<String, Float> = mutableMapOf()

    private val tabButtons = mutableListOf<TabChipWidget>()
    private val dockButtons = mutableListOf<DockButtonWidget>()

    private lateinit var searchBar: SearchBarComponent
    private lateinit var moduleScroll: ScrollContainerWidget

    private var panelWidth = 820
    private var panelHeight = 470
    private var panelX = 0
    private var panelY = 0
    private var searchWidth = 280
    private var moduleCardWidth = 300
    private var moduleRowsWidth = 610

    private val modules = listOf(
        VisualMenuModuleEntry("animations", "Animations", "A", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("aspect_ratio", "Aspect Ratio", "R", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("block_overlay", "Block Overlay", "B", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("china_hat", "China Hat", "C", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("crosshair", "Crosshair", "X", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("custom_hand", "Custom Hand", "H", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("full_bright", "Full Bright", "F", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("hit_bubble", "Hit Bubble", "U", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("hit_color", "Hit Color", "K", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("hit_sounds", "Hit Sounds", "N", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("hitbox_customizer", "Hitbox Customizer", "Z", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("jump_circle", "Jump Circles", "J", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("nimb", "Nimb", "N", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("no_fluid", "No Fluid", "L", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("particles", "Particles", "P", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("render_tweaks", "Render Tweaks", "R", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("self_nametag", "Self Nametag", "S", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("target_esp", "Target ESP", "E", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("time_changer", "Time Changer", "T", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("trails", "Trails", "T", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("world_customizer", "World Customizer", "W", VisualMenuTab.VISUALS),
        VisualMenuModuleEntry("world_particles", "World Particles", "O", VisualMenuTab.VISUALS),

        VisualMenuModuleEntry("armor_hud", "Armor HUD", "A", VisualMenuTab.HUD),
        VisualMenuModuleEntry("cooldowns_hud", "Cooldowns HUD", "C", VisualMenuTab.HUD),
        VisualMenuModuleEntry("effect_notify", "Effect Notify", "E", VisualMenuTab.HUD),
        VisualMenuModuleEntry("hotkeys", "Hotkeys", "H", VisualMenuTab.HUD),
        VisualMenuModuleEntry("potions", "Potions", "P", VisualMenuTab.HUD),
        VisualMenuModuleEntry("direction", "Direction", "D", VisualMenuTab.HUD),
        VisualMenuModuleEntry("fps", "FPS", "F", VisualMenuTab.HUD),
        VisualMenuModuleEntry("keystrokes", "Key Strokes", "K", VisualMenuTab.HUD),
        VisualMenuModuleEntry("potion_icons", "Potion Icons", "P", VisualMenuTab.HUD),
        VisualMenuModuleEntry("radar", "Radar", "R", VisualMenuTab.HUD),
        VisualMenuModuleEntry("session_time", "Session Time", "S", VisualMenuTab.HUD),
        VisualMenuModuleEntry("target_hud", "Target HUD", "T", VisualMenuTab.HUD),
        VisualMenuModuleEntry("watermark", "Watermark", "W", VisualMenuTab.HUD),

        VisualMenuModuleEntry("auto_sprint", "Auto Sprint", "A", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("chat_cleaner", "Chat Cleaner", "C", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("fast_place", "Fast Place", "F", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("inventory_move", "Inventory Move", "I", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("name_protect", "Name Protect", "N", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("screenshot_tool", "Screenshot Tool", "S", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("timer", "Timer", "T", VisualMenuTab.UTILITIES),
        VisualMenuModuleEntry("zoom", "Zoom", "Z", VisualMenuTab.UTILITIES),
    )

    init {
        modules.forEach { module ->
            ModuleStateStore.ensureModule(module.id, defaultEnabled = false)
            toggleProgressById.putIfAbsent(module.id, 0f)

            ModuleStateStore.ensureSetting("${module.id}:visible_hud", defaultValue = false)
            ModuleStateStore.ensureSetting("${module.id}:accent_sync", defaultValue = true)
        }
    }

    override fun init() {
        calculateWindowGeometry()
        clear()
        setBackground(ColorBackground(0x00000000))

        tabButtons.clear()
        dockButtons.clear()

        buildUi()
        super.init()
        rebuildModuleCards(filteredModulesForActiveTab())
    }

    private fun calculateWindowGeometry() {
        val width = Minecraft.getInstance().window.guiScaledWidth
        val height = Minecraft.getInstance().window.guiScaledHeight

        val maxPanelWidth = (width - 110).coerceAtLeast(460)
        val targetPanelWidth = (width * 0.56f).toInt()
        panelWidth = targetPanelWidth.coerceIn(min(560, maxPanelWidth), min(810, maxPanelWidth))

        val maxPanelHeight = (height - 138).coerceAtLeast(300)
        val targetPanelHeight = (height * 0.60f).toInt()
        panelHeight = targetPanelHeight.coerceIn(min(330, maxPanelHeight), min(490, maxPanelHeight))

        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight - VisualMenuTheme.frameGap - VisualMenuTheme.dockHeight) / 2
        searchWidth = (panelWidth * 0.33f).toInt().coerceIn(220, 310)

        val moduleViewportWidth = (
            panelWidth
                - (VisualMenuTheme.panelPadding * 2)
                - (VisualMenuTheme.moduleBodyPadding * 2)
                - (VisualMenuTheme.moduleRowsSideInset * 2)
                - VisualMenuTheme.moduleViewportRightInset
            ).coerceAtLeast(360)
        moduleCardWidth = ((moduleViewportWidth - VisualMenuTheme.moduleColumnGap) / 2).coerceAtLeast(180)
        moduleRowsWidth = (moduleCardWidth * 2) + VisualMenuTheme.moduleColumnGap
    }

    private fun buildUi() {
        val mainPanel = buildMainPanel()
        addComponent(mainPanel)
        addComponent(buildDock())
    }

    private fun buildMainPanel(): DecorativeComponent {
        val mainPanel = DecorativeComponent(panelX, panelY, panelWidth, panelHeight) { context, x, y, width, height ->
            drawRoundedPanel(
                context,
                x,
                y,
                width,
                height,
                VisualMenuTheme.panelFill,
                VisualMenuTheme.panelBorder,
                VisualMenuTheme.panelRadius,
            )

            val glowInset = (VisualMenuTheme.panelRadius * 0.48f).toInt().coerceAtLeast(10)
            val glowHeight = (height * 0.24f).toInt().coerceAtLeast(32)
            drawVerticalGradient(
                context,
                x + glowInset,
                y + 2,
                (width - (glowInset * 2)).coerceAtLeast(2),
                glowHeight,
                VisualMenuTheme.panelTopGlowStart,
                VisualMenuTheme.panelTopGlowEnd,
            )

            val panelHighlightInset = (VisualMenuTheme.panelRadius * 0.60f).toInt().coerceAtLeast(14)
            drawVerticalGradient(
                context,
                x + panelHighlightInset,
                y + 2,
                (width - (panelHighlightInset * 2)).coerceAtLeast(2),
                12,
                0x16F8FAFF,
                0x00000000,
            )

            drawVerticalGradient(
                context,
                x + glowInset,
                y + height - 48,
                (width - (glowInset * 2)).coerceAtLeast(2),
                46,
                0x00000000,
                VisualMenuTheme.panelBottomShade,
            )
        }

        val headerWidth = panelWidth - (VisualMenuTheme.panelPadding * 2)
        val header = DecorativeComponent(
            VisualMenuTheme.panelPadding,
            VisualMenuTheme.panelPadding,
            headerWidth,
            VisualMenuTheme.headerHeight,
        ) { context, x, y, width, height ->
            drawRoundedPanel(
                context,
                x,
                y,
                width,
                height,
                VisualMenuTheme.headerFill,
                VisualMenuTheme.headerBorder,
                VisualMenuTheme.headerRadius,
            )
        }

        val tabY = (VisualMenuTheme.headerHeight - VisualMenuTheme.tabHeight) / 2
        var currentTabX = 10
        VisualMenuTab.entries.forEach { tab ->
            val button = TabChipWidget(currentTabX, tabY, tab.title, tab == activeTab) {
                if (activeTab != tab) {
                    activeTab = tab
                    syncTabButtons()
                    rebuildModuleCards(filteredModulesForActiveTab())
                }
            }
            tabButtons += button
            header.addWidget(button)
            currentTabX += button.width + 6
        }

        searchBar = SearchBarComponent(
            x = headerWidth - searchWidth - 10,
            y = (VisualMenuTheme.headerHeight - VisualMenuTheme.searchHeight) / 2,
            width = searchWidth,
            font = Minecraft.getInstance().font,
            initialQuery = searchQuery,
        ) { value ->
            searchQuery = value
            rebuildModuleCards(filteredModulesForActiveTab())
        }
        header.addComponent(searchBar)

        val moduleBodyHeight = panelHeight - (VisualMenuTheme.panelPadding * 2) - VisualMenuTheme.headerHeight - VisualMenuTheme.panelGap
        val moduleBody = DecorativeComponent(
            VisualMenuTheme.panelPadding,
            VisualMenuTheme.panelPadding + VisualMenuTheme.headerHeight + VisualMenuTheme.panelGap,
            headerWidth,
            moduleBodyHeight,
        ) { context, x, y, width, height ->
            drawRoundedPanel(
                context,
                x,
                y,
                width,
                height,
                VisualMenuTheme.moduleBodyFill,
                VisualMenuTheme.moduleBodyBorder,
                VisualMenuTheme.moduleBodyRadius,
            )
        }

        val scrollWidth = headerWidth - (VisualMenuTheme.moduleBodyPadding * 2)
        val scrollHeight = moduleBodyHeight - 9 - 14
        moduleScroll = ScrollContainerWidget(scrollWidth, scrollHeight, VisualMenuTheme.moduleRowGap)
        moduleScroll.setX(VisualMenuTheme.moduleBodyPadding)
        moduleScroll.setY(9)
        moduleBody.addWidget(moduleScroll)

        mainPanel.addComponent(header)
        mainPanel.addComponent(moduleBody)

        return mainPanel
    }

    private fun buildDock(): DecorativeComponent {
        val dockWidth = (panelWidth * VisualMenuTheme.dockWidthRatio).toInt().coerceAtLeast(260)
        val dockX = panelX + (panelWidth - dockWidth) / 2
        val dockY = panelY + panelHeight + VisualMenuTheme.frameGap

        val dock = DecorativeComponent(dockX, dockY, dockWidth, VisualMenuTheme.dockHeight) { context, x, y, width, height ->
            drawRoundedPanel(
                context,
                x,
                y,
                width,
                height,
                VisualMenuTheme.dockFill,
                VisualMenuTheme.dockBorder,
                VisualMenuTheme.dockRadius,
            )
        }

        val totalButtonsWidth = (VisualMenuDock.entries.size * 44) + ((VisualMenuDock.entries.size - 1) * VisualMenuTheme.dockGap)
        var currentX = (dockWidth - totalButtonsWidth) / 2
        val buttonY = (VisualMenuTheme.dockHeight - 34) / 2
        VisualMenuDock.entries.forEach { dockEntry ->
            val button = DockButtonWidget(currentX, buttonY, dockEntry, dockEntry == activeDock) {
                activeDock = dockEntry
                syncDockButtons()
                VisualClientMod.LOGGER.info("${dockEntry.label} icon clicked")
            }
            dockButtons += button
            dock.addWidget(button)
            currentX += 44 + VisualMenuTheme.dockGap
        }

        return dock
    }

    private fun syncTabButtons() {
        tabButtons.forEachIndexed { index, button ->
            button.setActiveState(VisualMenuTab.entries[index] == activeTab)
        }
    }

    private fun syncDockButtons() {
        dockButtons.forEachIndexed { index, button ->
            button.setActiveState(VisualMenuDock.entries[index] == activeDock)
        }
    }

    private fun filteredModulesForActiveTab(): List<VisualMenuModuleEntry> {
        val query = searchQuery.trim()
        return modules.filter { module ->
            module.tab == activeTab && (query.isBlank() || module.title.contains(query, ignoreCase = true))
        }
    }

    private fun rebuildModuleCards(entries: List<VisualMenuModuleEntry>) {
        if (openedModuleSettingsId != null && entries.none { it.id == openedModuleSettingsId }) {
            openedModuleSettingsId = null
        }

        moduleScroll.clearComponents()

        fun attach(component: com.daqem.uilib.api.component.IComponent) {
            moduleScroll.addComponent(component)
            component.updateParentPosition(moduleScroll.x, moduleScroll.y, moduleScroll.width, moduleScroll.height)
        }

        attach(SpacerComponent(1, VisualMenuTheme.moduleListTopInset))

        if (entries.isEmpty()) {
            attach(EmptyStateComponent(moduleRowsWidth))
            attach(SpacerComponent(1, VisualMenuTheme.moduleListBottomInset))
            return
        }

        entries.chunked(2).forEach { pair ->
            attach(
                ModuleRowComponent(
                    x = VisualMenuTheme.moduleRowsSideInset,
                    width = moduleRowsWidth,
                    cardWidth = moduleCardWidth,
                    leftModule = pair[0],
                    rightModule = pair.getOrNull(1),
                    hoverProgressById = cardHoverProgressById,
                    toggleProgressById = toggleProgressById,
                    isOpened = { moduleId -> openedModuleSettingsId == moduleId },
                    onToggle = { module ->
                        val next = ModuleStateStore.toggleEnabled(module.id)
                        VisualClientMod.LOGGER.info("${module.id}: $next")
                    },
                    onOpenSettings = { module ->
                        openedModuleSettingsId = if (openedModuleSettingsId == module.id) null else module.id
                        rebuildModuleCards(filteredModulesForActiveTab())
                    },
                )
            )

            val opened = pair.firstOrNull { it.id == openedModuleSettingsId }
            if (opened != null) {
                attach(
                    ModuleSettingsPanelComponent(moduleRowsWidth, opened) { key, enabled ->
                        ModuleStateStore.setSettingEnabled(key, enabled)
                        VisualClientMod.LOGGER.info("$key: $enabled")
                    }
                )
            }
        }

        attach(SpacerComponent(1, VisualMenuTheme.moduleListBottomInset))
    }
}
