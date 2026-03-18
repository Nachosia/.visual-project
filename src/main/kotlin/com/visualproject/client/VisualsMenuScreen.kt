package com.visualproject.client

import io.wispforest.owo.ui.base.BaseUIComponent as BaseComponent
import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.UIComponents as Components
import io.wispforest.owo.ui.component.LabelComponent
import io.wispforest.owo.ui.component.TextBoxComponent
import io.wispforest.owo.ui.container.UIContainers as Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.ScrollContainer
import io.wispforest.owo.ui.core.Color
import io.wispforest.owo.ui.core.CursorStyle
import io.wispforest.owo.ui.core.HorizontalAlignment
import io.wispforest.owo.ui.core.Insets
import io.wispforest.owo.ui.core.OwoUIAdapter
import io.wispforest.owo.ui.core.OwoUIGraphics
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.Surface
import io.wispforest.owo.ui.core.VerticalAlignment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.MutableComponent
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class VisualsMenuScreen : BaseOwoScreen<FlowLayout>() {

    private object Theme {
        const val frameGap = 6

        const val panelFill = 0xFF0A0F19.toInt()
        const val panelBorder = 0xFF2A3650.toInt()
        const val panelRadius = 28
        const val panelPadding = 14
        const val panelGap = 8
        const val panelTopGlowStart = 0xAD253154.toInt()
        const val panelTopGlowMid = 0x751C2843
        const val panelTopGlowEnd = 0x00131B2A
        const val panelBottomShade = 0xD0080C15.toInt()

        const val headerFill = 0xFF0E1627.toInt()
        const val headerBorder = 0xFF354566.toInt()
        const val headerRadius = 18
        const val headerHeight = 44
        const val headerGap = 8

        const val topBarGap = 10
        const val tabHeight = 28

        const val searchFill = 0xFF121B2D.toInt()
        const val searchBorder = 0xFF3B4D71.toInt()
        const val searchRadius = 14
        const val searchHeight = 30

        const val moduleBodyFill = 0xFF0C1424.toInt()
        const val moduleBodyBorder = 0xFF2A3753.toInt()
        const val moduleBodyRadius = 18
        const val moduleBodyPadding = 12
        const val moduleRowsSideInset = 4
        const val moduleViewportRightInset = 7

        const val moduleRowGap = 10
        const val moduleColumnGap = 10
        const val moduleCardHeight = 48
        const val moduleRowHeight = moduleCardHeight + 10
        const val moduleListTopInset = 6
        const val moduleListBottomInset = 16

        const val cardFill = 0xFF111A2C.toInt()
        const val cardBorder = 0xFF2F3D5B.toInt()
        const val cardHoverFill = 0xFF172338.toInt()
        const val cardHoverBorder = 0xFF50618D.toInt()
        const val cardEnabledFill = 0xFF152037.toInt()
        const val cardEnabledBorder = 0xFF6077AC.toInt()
        const val cardExpandedFill = 0xFF1A2740.toInt()
        const val cardExpandedBorder = 0xFF8774DE.toInt()
        const val cardRadius = 16

        const val settingsFill = 0xFF10182A.toInt()
        const val settingsBorder = 0xFF3C4D70.toInt()
        const val settingsRadius = 16
        const val settingsHeight = 92

        const val iconSlotFill = 0x8F141D33.toInt()
        const val iconSlotBorder = 0x7A3A4D73
        const val iconSlotRadius = 9

        const val toggleWidth = 38
        const val toggleHeight = 20
        const val toggleRightInset = 12

        const val dockFill = 0xFF10182A.toInt()
        const val dockBorder = 0xFF3A4B6F.toInt()
        const val dockRadius = 18
        const val dockHeight = 48
        const val dockGap = 6
        const val dockWidthRatio = 0.68f

        const val accent = 0xFF7D5BFF.toInt()
        const val accentStrong = 0xFF8A71FF.toInt()
        const val accentSoft = 0x8F7863D8.toInt()

        const val textPrimary = 0xFFF4F6FF.toInt()
        const val textSecondary = 0xFFA2ACCA.toInt()
        const val textMuted = 0xFF7782A2.toInt()
        const val textDim = 0xFF69748F.toInt()

        const val scrollbarColor = 0x66505A7A
        const val scrollbarThickness = 3
        const val scrollStep = 12
    }

    private enum class Tab(val title: String) {
        VISUALS("Visuals"),
        HUD("HUD"),
        UTILITIES("Utilities"),
    }

    private enum class Dock(val icon: String, val label: String) {
        SETTINGS("S", "Settings"),
        PROFILE("P", "Profile"),
        CHAT("C", "Chat"),
        MODPACK("M", "Modpack"),
        DOWNLOAD("D", "Download"),
    }

    private data class ModuleEntry(
        val id: String,
        val title: String,
        val iconGlyph: String,
        val tab: Tab,
    )

    private var activeTab = Tab.VISUALS
    private var activeDock = Dock.SETTINGS
    private var searchQuery = ""

    // Explicit UI state: only one settings panel may be open at a time
    private var openedModuleSettingsId: String? = null

    private val toggleProgressById: MutableMap<String, Float> = mutableMapOf()
    private val cardHoverProgressById: MutableMap<String, Float> = mutableMapOf()

    private lateinit var tabStrip: FlowLayout
    private lateinit var dockStrip: FlowLayout
    private lateinit var searchInput: TextBoxComponent
    private lateinit var moduleRows: FlowLayout
    private lateinit var moduleScroll: ScrollContainer<FlowLayout>

    private var panelWidth = 820
    private var panelHeight = 470
    private var searchWidth = 280
    private var moduleCardWidth = 300

    private val modules = listOf(
        ModuleEntry("animations", "Animations", "A", Tab.VISUALS),
        ModuleEntry("aspect_ratio", "Aspect Ratio", "R", Tab.VISUALS),
        ModuleEntry("block_overlay", "Block Overlay", "B", Tab.VISUALS),
        ModuleEntry("china_hat", "China Hat", "C", Tab.VISUALS),
        ModuleEntry("crosshair", "Crosshair", "X", Tab.VISUALS),
        ModuleEntry("custom_hand", "Custom Hand", "H", Tab.VISUALS),
        ModuleEntry("full_bright", "Full Bright", "F", Tab.VISUALS),
        ModuleEntry("hit_bubble", "Hit Bubble", "U", Tab.VISUALS),
        ModuleEntry("hit_color", "Hit Color", "K", Tab.VISUALS),
        ModuleEntry("hit_sounds", "Hit Sounds", "N", Tab.VISUALS),
        ModuleEntry("hitbox_customizer", "Hitbox Customizer", "Z", Tab.VISUALS),
        ModuleEntry("jump_circle", "Jump Circles", "J", Tab.VISUALS),
        ModuleEntry("nimb", "Nimb", "N", Tab.VISUALS),
        ModuleEntry("no_fluid", "No Fluid", "L", Tab.VISUALS),
        ModuleEntry("particles", "Particles", "P", Tab.VISUALS),
        ModuleEntry("render_tweaks", "Render Tweaks", "R", Tab.VISUALS),
        ModuleEntry("self_nametag", "Self Nametag", "S", Tab.VISUALS),
        ModuleEntry("target_esp", "Target ESP", "E", Tab.VISUALS),
        ModuleEntry("time_changer", "Time Changer", "T", Tab.VISUALS),
        ModuleEntry("trails", "Trails", "T", Tab.VISUALS),
        ModuleEntry("world_customizer", "World Customizer", "W", Tab.VISUALS),
        ModuleEntry("world_particles", "World Particles", "O", Tab.VISUALS),

        ModuleEntry("armor_hud", "Armor HUD", "A", Tab.HUD),
        ModuleEntry("cooldowns_hud", "Cooldowns HUD", "C", Tab.HUD),
        ModuleEntry("effect_notify", "Effect Notify", "E", Tab.HUD),
        ModuleEntry("hotkeys", "Hotkeys", "H", Tab.HUD),
        ModuleEntry("potions", "Potions", "P", Tab.HUD),
        ModuleEntry("direction", "Direction", "D", Tab.HUD),
        ModuleEntry("fps", "FPS", "F", Tab.HUD),
        ModuleEntry("keystrokes", "Key Strokes", "K", Tab.HUD),
        ModuleEntry("potion_icons", "Potion Icons", "P", Tab.HUD),
        ModuleEntry("radar", "Radar", "R", Tab.HUD),
        ModuleEntry("session_time", "Session Time", "S", Tab.HUD),
        ModuleEntry("target_hud", "Target HUD", "T", Tab.HUD),
        ModuleEntry("watermark", "Watermark", "W", Tab.HUD),

        ModuleEntry("auto_sprint", "Auto Sprint", "A", Tab.UTILITIES),
        ModuleEntry("chat_cleaner", "Chat Cleaner", "C", Tab.UTILITIES),
        ModuleEntry("fast_place", "Fast Place", "F", Tab.UTILITIES),
        ModuleEntry("inventory_move", "Inventory Move", "I", Tab.UTILITIES),
        ModuleEntry("name_protect", "Name Protect", "N", Tab.UTILITIES),
        ModuleEntry("screenshot_tool", "Screenshot Tool", "S", Tab.UTILITIES),
        ModuleEntry("timer", "Timer", "T", Tab.UTILITIES),
        ModuleEntry("zoom", "Zoom", "Z", Tab.UTILITIES),
    )

    private val panelSurface = Surface { context, component ->
        drawRoundedPanel(
            context,
            component.x(),
            component.y(),
            component.width(),
            component.height(),
            Theme.panelFill,
            Theme.panelBorder,
            Theme.panelRadius,
        )

        val glowInset = (Theme.panelRadius * 0.48f).toInt().coerceAtLeast(10)
        val glowHeight = (component.height() * 0.24f).toInt().coerceAtLeast(32)
        context.drawGradientRect(
            component.x() + glowInset,
            component.y() + 2,
            (component.width() - (glowInset * 2)).coerceAtLeast(2),
            glowHeight,
            Theme.panelTopGlowStart,
            Theme.panelTopGlowMid,
            Theme.panelTopGlowEnd,
            Theme.panelTopGlowEnd,
        )

        val panelHighlightInset = (Theme.panelRadius * 0.60f).toInt().coerceAtLeast(14)
        context.drawGradientRect(
            component.x() + panelHighlightInset,
            component.y() + 2,
            (component.width() - (panelHighlightInset * 2)).coerceAtLeast(2),
            12,
            0x16F8FAFF,
            0x08D9E1FB,
            0x00000000,
            0x00000000,
        )

        context.drawGradientRect(
            component.x() + glowInset,
            component.y() + component.height() - 48,
            (component.width() - (glowInset * 2)).coerceAtLeast(2),
            46,
            0x00000000,
            0x00000000,
            Theme.panelBottomShade,
            Theme.panelBottomShade,
        )
    }

    private val searchSurface = roundedSurface(
        fill = Theme.searchFill,
        border = Theme.searchBorder,
        radius = Theme.searchRadius,
    )

    private val headerSurface = roundedSurface(
        fill = Theme.headerFill,
        border = Theme.headerBorder,
        radius = Theme.headerRadius,
    )

    private val moduleBodySurface = roundedSurface(
        fill = Theme.moduleBodyFill,
        border = Theme.moduleBodyBorder,
        radius = Theme.moduleBodyRadius,
    )

    private val cardSurface = roundedSurface(
        fill = Theme.cardFill,
        border = Theme.cardBorder,
        radius = Theme.cardRadius,
    )

    private val settingsSurface = roundedSurface(
        fill = Theme.settingsFill,
        border = Theme.settingsBorder,
        radius = Theme.settingsRadius,
    )

    private val dockSurface = roundedSurface(
        fill = Theme.dockFill,
        border = Theme.dockBorder,
        radius = Theme.dockRadius,
    )

    init {
        modules.forEach { module ->
            ModuleStateStore.ensureModule(module.id, defaultEnabled = false)
            toggleProgressById.putIfAbsent(module.id, 0f)

            ModuleStateStore.ensureSetting("${module.id}:visible_hud", defaultValue = false)
            ModuleStateStore.ensureSetting("${module.id}:accent_sync", defaultValue = true)
        }
    }

    override fun createAdapter(): OwoUIAdapter<FlowLayout> {
        return OwoUIAdapter.create(this, Containers::verticalFlow)
    }

    override fun build(rootComponent: FlowLayout) {
        calculateWindowGeometry()

        rootComponent
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(16))

        val frame = Containers.verticalFlow(Sizing.content(), Sizing.content())
        frame.horizontalAlignment(HorizontalAlignment.CENTER)
        frame.verticalAlignment(VerticalAlignment.CENTER)
        frame.gap(Theme.frameGap)

        frame.child(buildMainPanel())
        frame.child(buildDockHolder())

        rootComponent.child(frame)

        rebuildTabs()
        rebuildDock()
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

        searchWidth = (panelWidth * 0.33f).toInt().coerceIn(220, 310)

        val moduleViewportWidth = (
            panelWidth
                - (Theme.panelPadding * 2)
                - (Theme.moduleBodyPadding * 2)
                - (Theme.moduleRowsSideInset * 2)
                - Theme.moduleViewportRightInset
            ).coerceAtLeast(360)
        moduleCardWidth = ((moduleViewportWidth - Theme.moduleColumnGap) / 2).coerceAtLeast(180)
    }

    private fun buildMainPanel(): FlowLayout {
        val panel = Containers.verticalFlow(Sizing.fixed(panelWidth), Sizing.fixed(panelHeight))
        panel.surface(panelSurface)
        panel.padding(Insets.of(Theme.panelPadding))
        panel.gap(Theme.panelGap)

        val content = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
        content.gap(Theme.panelGap)

        buildTopBar(content)
        buildModuleSection(content)

        panel.child(content)

        return panel
    }

    private fun buildTopBar(panel: FlowLayout) {
        val topShell = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(Theme.headerHeight))
        topShell.surface(headerSurface)
        topShell.padding(Insets.of(6, 7, 10, 10))
        topShell.gap(Theme.headerGap)
        topShell.verticalAlignment(VerticalAlignment.CENTER)

        val topBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100))
        topBar.verticalAlignment(VerticalAlignment.CENTER)
        topBar.gap(Theme.topBarGap)

        tabStrip = Containers.horizontalFlow(Sizing.expand(100), Sizing.fixed(Theme.tabHeight))
        tabStrip.verticalAlignment(VerticalAlignment.CENTER)
        tabStrip.gap(6)
        topBar.child(tabStrip)

        topBar.child(buildSearchBar())

        topShell.child(topBar)
        panel.child(topShell)
    }

    private fun buildSearchBar(): FlowLayout {
        val searchBar = Containers.horizontalFlow(Sizing.fixed(searchWidth), Sizing.fixed(Theme.searchHeight))
        searchBar.surface(searchSurface)
        searchBar.padding(Insets.of(4, 6, 11, 11))
        searchBar.verticalAlignment(VerticalAlignment.CENTER)
        searchBar.gap(6)

        searchBar.child(
            Components.label(vText("?"))
                .configure<LabelComponent> { icon ->
                    icon.color(Color.ofRgb(0x7B829F))
                    icon.shadow(false)
                }
        )

        searchInput = Components.textBox(Sizing.expand(100))
        searchInput.setBordered(false)
        searchInput.setTextShadow(false)
        searchInput.setTextColor(0xFFBBC3E0.toInt())
        searchInput.setTextColorUneditable(0xFF7E88A6.toInt())
        searchInput.setHint(vText("Поиск"))
        searchInput.onChanged().subscribe(TextBoxComponent.OnChanged { value ->
            searchQuery = value.trim()
            rebuildModuleCards(filteredModulesForActiveTab())
        })
        searchBar.child(searchInput)

        searchBar.child(
            Components.label(vText("?"))
                .configure<LabelComponent> { icon ->
                    icon.color(Color.ofRgb(0x6E7591))
                    icon.shadow(false)
                }
        )

        return searchBar
    }

    private fun buildModuleSection(panel: FlowLayout) {
        moduleRows = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        moduleRows.gap(Theme.moduleRowGap)
        moduleRows.padding(Insets.of(Theme.moduleListTopInset, 4, Theme.moduleRowsSideInset, Theme.moduleRowsSideInset))

        moduleScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(100), moduleRows)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(Theme.scrollbarColor)))
            .scrollbarThiccness(Theme.scrollbarThickness)
            .scrollStep(Theme.scrollStep)

        val moduleBody = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
        moduleBody.surface(moduleBodySurface)
        moduleBody.padding(Insets.of(9, 14, Theme.moduleBodyPadding, Theme.moduleBodyPadding))
        moduleBody.child(moduleScroll)

        panel.child(moduleBody)
    }

    private fun buildDockHolder(): FlowLayout {
        val dockHolder = Containers.horizontalFlow(Sizing.fixed(panelWidth), Sizing.content())
        dockHolder.horizontalAlignment(HorizontalAlignment.CENTER)

        val dockWidth = (panelWidth * Theme.dockWidthRatio).toInt().coerceAtLeast(260)
        dockStrip = Containers.horizontalFlow(Sizing.fixed(dockWidth), Sizing.fixed(Theme.dockHeight))
        dockStrip.surface(dockSurface)
        dockStrip.padding(Insets.of(6, 6, 10, 10))
        dockStrip.horizontalAlignment(HorizontalAlignment.CENTER)
        dockStrip.verticalAlignment(VerticalAlignment.CENTER)
        dockStrip.gap(Theme.dockGap)

        dockHolder.child(dockStrip)
        return dockHolder
    }

    private fun rebuildTabs() {
        tabStrip.clearChildren()

        Tab.entries.forEach { tab ->
            tabStrip.child(
                TabButtonComponent(tab.title, tab == activeTab) {
                    if (activeTab != tab) {
                        activeTab = tab
                        rebuildTabs()
                        rebuildModuleCards(filteredModulesForActiveTab())
                    }
                }
            )
        }
    }

    private fun rebuildDock() {
        dockStrip.clearChildren()

        Dock.entries.forEach { dock ->
            dockStrip.child(
                DockButtonComponent(dock.icon, dock == activeDock) {
                    activeDock = dock
                    rebuildDock()
                    VisualClientMod.LOGGER.info("${dock.label} icon clicked")
                }
            )
        }
    }

    private fun filteredModulesForActiveTab(): List<ModuleEntry> {
        val query = searchQuery.trim()
        return modules.filter { module ->
            module.tab == activeTab && (query.isBlank() || module.title.contains(query, ignoreCase = true))
        }
    }

    private fun rebuildModuleCards(entries: List<ModuleEntry>) {
        if (openedModuleSettingsId != null && entries.none { it.id == openedModuleSettingsId }) {
            openedModuleSettingsId = null
        }

        moduleRows.clearChildren()

        if (entries.isEmpty()) {
            moduleRows.child(buildEmptyCard())
            moduleRows.child(
                Components.box(Sizing.fill(100), Sizing.fixed(Theme.moduleListBottomInset))
                    .fill(false)
            )
            return
        }

        entries.chunked(2).forEach { pair ->
            val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(Theme.moduleRowHeight))
            row.verticalAlignment(VerticalAlignment.CENTER)
            row.gap(Theme.moduleColumnGap)

            row.child(ModuleCardComponent(pair[0]))

            if (pair.size == 2) {
                row.child(ModuleCardComponent(pair[1]))
            } else {
                row.child(
                    Components.box(Sizing.fixed(moduleCardWidth), Sizing.fixed(Theme.moduleCardHeight))
                        .fill(false)
                )
            }

            moduleRows.child(row)

            val opened = pair.firstOrNull { it.id == openedModuleSettingsId }
            if (opened != null) {
                moduleRows.child(buildModuleSettingsPanel(opened))
            }
        }

        moduleRows.child(
            Components.box(Sizing.fill(100), Sizing.fixed(Theme.moduleListBottomInset))
                .fill(false)
        )
    }

    private fun buildEmptyCard(): FlowLayout {
        val emptyCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(Theme.moduleCardHeight))
        emptyCard.surface(cardSurface)
        emptyCard.padding(Insets.of(10, 11, 14, 14))
        emptyCard.verticalAlignment(VerticalAlignment.CENTER)

        emptyCard.child(
            Components.label(vText("Ничего не найдено"))
                .configure<LabelComponent> { label ->
                    label.color(Color.ofRgb(0x7D86A6))
                    label.shadow(false)
                }
        )

        return emptyCard
    }

    private fun buildModuleSettingsPanel(module: ModuleEntry): FlowLayout {
        val panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(Theme.settingsHeight))
        panel.surface(settingsSurface)
        panel.padding(Insets.of(8, 10, 12, 12))
        panel.gap(8)

        panel.child(
            Components.label(vText("Settings • ${module.title}"))
                .configure<LabelComponent> { label ->
                    label.color(Color.ofArgb(Theme.textSecondary))
                    label.shadow(false)
                }
        )

        panel.child(buildInlineSettingRow(module, "Visible In HUD", "visible_hud"))
        panel.child(buildInlineSettingRow(module, "Accent Sync", "accent_sync"))

        return panel
    }

    private fun buildInlineSettingRow(module: ModuleEntry, title: String, keySuffix: String): FlowLayout {
        val stateKey = "${module.id}:$keySuffix"

        val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24))
        row.verticalAlignment(VerticalAlignment.CENTER)
        row.gap(8)

        row.child(
            Components.label(vText(title))
                .configure<LabelComponent> { label ->
                    label.color(Color.ofArgb(Theme.textMuted))
                    label.shadow(false)
                    label.sizing(Sizing.expand(100), Sizing.content())
                }
        )

        row.child(
            InlineSwitchComponent(ModuleStateStore.isSettingEnabled(stateKey)) { enabled ->
                ModuleStateStore.setSettingEnabled(stateKey, enabled)
                VisualClientMod.LOGGER.info("$stateKey: $enabled")
            }
        )

        return row
    }

    private inner class ModuleCardComponent(
        private val module: ModuleEntry,
    ) : BaseComponent() {

        init {
            this.sizing(Sizing.fixed(moduleCardWidth), Sizing.fixed(Theme.moduleCardHeight))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = moduleCardWidth

        override fun determineVerticalContentSize(sizing: Sizing): Int = Theme.moduleCardHeight

        override fun draw(context: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)
            val enabled = ModuleStateStore.isEnabled(module.id)
            val expanded = openedModuleSettingsId == module.id

            val targetHover = if (hovered || expanded) 1f else 0f
            val currentHover = cardHoverProgressById[module.id] ?: 0f
            val nextHover = currentHover + (targetHover - currentHover) * 0.28f
            cardHoverProgressById[module.id] = if (abs(nextHover - targetHover) < 0.001f) targetHover else nextHover

            val baseFill = if (enabled) Theme.cardEnabledFill else Theme.cardFill
            val baseBorder = if (enabled) Theme.cardEnabledBorder else Theme.cardBorder
            val hoverFill = if (expanded) Theme.cardExpandedFill else Theme.cardHoverFill
            val hoverBorder = if (expanded) Theme.cardExpandedBorder else Theme.cardHoverBorder
            val fill = blendColor(baseFill, hoverFill, nextHover)
            val border = blendColor(baseBorder, hoverBorder, nextHover)

            drawRoundedPanel(context, this.x, this.y, this.width, this.height, fill, border, Theme.cardRadius)
            val cardHighlightInset = (Theme.cardRadius * 0.55f).toInt().coerceAtLeast(8)
            val highlightStrength = ((if (enabled) 0.16f else 0.04f) + (nextHover * 0.24f)).coerceIn(0f, 0.38f)
            val highlightStart = blendColor(0x00000000, 0x24F4F7FF, highlightStrength)
            val highlightMid = blendColor(0x00000000, 0x12D6DDF8, highlightStrength)
            context.drawGradientRect(
                this.x + cardHighlightInset,
                this.y + 2,
                (this.width - (cardHighlightInset * 2)).coerceAtLeast(2),
                5,
                highlightStart,
                highlightMid,
                0x00000000,
                0x00000000,
            )

            val slotSize = 22
            val slotX = this.x + 10
            val slotY = this.y + (this.height - slotSize) / 2
            drawRoundedPanel(context, slotX, slotY, slotSize, slotSize, Theme.iconSlotFill, Theme.iconSlotBorder, Theme.iconSlotRadius)

            val font = Minecraft.getInstance().font
            val iconWidth = font.width(vText(module.iconGlyph))
            context.drawString(
                font,
                vText(module.iconGlyph),
                slotX + (slotSize - iconWidth) / 2,
                slotY + (slotSize - font.lineHeight) / 2,
                if (enabled) 0xFFBAC4E8.toInt() else 0xFF7E86A4.toInt(),
                false,
            )

            val textColor = if (enabled) Theme.textPrimary else Theme.textSecondary
            val titleX = slotX + slotSize + 9
            val toggleX = this.x + this.width - Theme.toggleWidth - Theme.toggleRightInset
            val titleMaxWidth = (toggleX - 10 - titleX).coerceAtLeast(24)
            val fittedTitle = fitStyledText(font, module.title, titleMaxWidth)
            context.drawString(
                font,
                fittedTitle,
                titleX,
                this.y + (this.height - font.lineHeight) / 2 - 1,
                textColor,
                false,
            )

            val toggleY = this.y + (this.height - Theme.toggleHeight) / 2
            val trackBorder = if (enabled) 0xFF8B73F8.toInt() else 0x7A394866
            val trackFill = if (enabled) 0xC85F4FBE.toInt() else 0xAA1B2438.toInt()
            drawRoundedPanel(context, toggleX, toggleY, Theme.toggleWidth, Theme.toggleHeight, trackFill, trackBorder, Theme.toggleHeight / 2)

            val target = if (enabled) 1f else 0f
            val current = toggleProgressById[module.id] ?: target
            val next = current + (target - current) * 0.26f
            val settled = if (abs(target - next) < 0.002f) target else next
            toggleProgressById[module.id] = settled

            val knobRadius = ((Theme.toggleHeight - 6) / 2).coerceAtLeast(4)
            val minCenterX = toggleX + knobRadius + 3
            val maxCenterX = toggleX + Theme.toggleWidth - knobRadius - 3
            val knobCenterX = (minCenterX + (maxCenterX - minCenterX) * settled).toInt()
            val knobCenterY = toggleY + Theme.toggleHeight / 2

            if (enabled) {
                fillRoundedRect(
                    context,
                    knobCenterX - knobRadius - 1,
                    knobCenterY - knobRadius - 1,
                    knobRadius * 2 + 2,
                    knobRadius * 2 + 2,
                    knobRadius + 1,
                    0x6A927DFF,
                )
            }
            drawRoundedPanel(
                context,
                knobCenterX - knobRadius,
                knobCenterY - knobRadius,
                knobRadius * 2,
                knobRadius * 2,
                0xFFF1F4FF.toInt(),
                0x90CBD3F8.toInt(),
                knobRadius,
            )
        }

        override fun onMouseDown(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() == 1) {
                openedModuleSettingsId = if (openedModuleSettingsId == module.id) null else module.id
                rebuildModuleCards(filteredModulesForActiveTab())
                return true
            }

            if (click.button() == 0) {
                val next = ModuleStateStore.toggleEnabled(module.id)
                VisualClientMod.LOGGER.info("${module.id}: $next")
                this.notifyParentIfMounted()
                return true
            }

            return super.onMouseDown(click, doubled)
        }
    }

    private class TabButtonComponent(
        private val text: String,
        private val active: Boolean,
        private val onPress: () -> Unit,
    ) : BaseComponent() {

        init {
            this.sizing(Sizing.content(), Sizing.fixed(Theme.tabHeight))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = Minecraft.getInstance().font.width(vText(text)) + 16

        override fun determineVerticalContentSize(sizing: Sizing): Int = Theme.tabHeight

        override fun draw(context: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)

            val backgroundFill = when {
                active -> 0x4D2B3C66
                hovered -> 0x2A1A253D
                else -> 0x00000000
            }
            val backgroundBorder = when {
                active -> 0xAA6479B5.toInt()
                hovered -> 0x7A3E4B70
                else -> 0x002A3247
            }
            if (active || hovered) {
                drawRoundedPanel(
                    context,
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    backgroundFill,
                    backgroundBorder,
                    10,
                )
            }

            val color = when {
                active -> Theme.textPrimary
                hovered -> Theme.textSecondary
                else -> Theme.textDim
            }

            val font = Minecraft.getInstance().font
            val textY = this.y + ((this.height - font.lineHeight) / 2)
            context.drawString(font, vText(text), this.x + 7, textY, color, false)

            if (active) {
                context.drawGradientRect(
                    this.x + 6,
                    this.y + this.height - 2,
                    (this.width - 12).coerceAtLeast(2),
                    1,
                    Theme.accent,
                    Theme.accent,
                    Theme.accent,
                    Theme.accent,
                )
            }
        }

        override fun onMouseDown(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() == 0) {
                onPress.invoke()
                return true
            }
            return super.onMouseDown(click, doubled)
        }
    }

    private class DockButtonComponent(
        private val iconText: String,
        private val active: Boolean,
        private val onPress: () -> Unit,
    ) : BaseComponent() {

        init {
            this.sizing(Sizing.fixed(44), Sizing.fixed(34))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = 44

        override fun determineVerticalContentSize(sizing: Sizing): Int = 34

        override fun draw(context: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)

            val border = when {
                active -> Theme.accentStrong
                hovered -> 0x7A4B5E89
                else -> 0x4B35435F
            }
            val fill = when {
                active -> 0xFF5D4AC6.toInt()
                hovered -> 0x7A1C2942
                else -> 0x4D131B2E
            }

            drawRoundedPanel(context, this.x, this.y, this.width, this.height, fill, border, 12)

            val font = Minecraft.getInstance().font
            val color = if (active) 0xFFF9F6FF.toInt() else 0xFF95A1C4.toInt()
            val textWidth = font.width(vText(iconText))
            context.drawString(
                font,
                vText(iconText),
                this.x + (this.width - textWidth) / 2,
                this.y + (this.height - font.lineHeight) / 2 - 1,
                color,
                false,
            )
        }

        override fun onMouseDown(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() == 0) {
                onPress.invoke()
                return true
            }
            return super.onMouseDown(click, doubled)
        }
    }

    private class InlineSwitchComponent(
        initialValue: Boolean,
        private val onToggle: (Boolean) -> Unit,
    ) : BaseComponent() {

        private var enabled = initialValue

        init {
            this.sizing(Sizing.fixed(30), Sizing.fixed(16))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = 30

        override fun determineVerticalContentSize(sizing: Sizing): Int = 16

        override fun draw(context: OwoUIGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val trackBorder = if (enabled) 0xFF8E73FD.toInt() else 0x7A3D4A68
            val trackFill = if (enabled) 0xC45747B6.toInt() else 0x7A1A2436
            drawRoundedPanel(context, this.x, this.y, this.width, this.height, trackFill, trackBorder, this.height / 2)

            val knobRadius = ((this.height - 6) / 2).coerceAtLeast(3)
            val knobCenterX = if (enabled) this.x + this.width - knobRadius - 3 else this.x + knobRadius + 3
            val knobCenterY = this.y + this.height / 2

            if (enabled) {
                fillRoundedRect(
                    context,
                    knobCenterX - knobRadius - 1,
                    knobCenterY - knobRadius - 1,
                    knobRadius * 2 + 2,
                    knobRadius * 2 + 2,
                    knobRadius + 1,
                    0x5A8C79FF,
                )
            }
            drawRoundedPanel(
                context,
                knobCenterX - knobRadius,
                knobCenterY - knobRadius,
                knobRadius * 2,
                knobRadius * 2,
                0xFFF1F4FF.toInt(),
                0x9AC9D5F3.toInt(),
                knobRadius,
            )
        }

        override fun onMouseDown(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() == 0) {
                enabled = !enabled
                onToggle.invoke(enabled)
                this.notifyParentIfMounted()
                return true
            }
            return super.onMouseDown(click, doubled)
        }
    }
}

private fun fitStyledText(font: Font, value: String, maxWidth: Int): MutableComponent {
    val safeWidth = maxWidth.coerceAtLeast(12)
    val full = vText(value)
    if (font.width(full) <= safeWidth) return full

    var trimmed = value
    while (trimmed.isNotEmpty() && font.width(vText("$trimmed...")) > safeWidth) {
        trimmed = trimmed.dropLast(1)
    }

    return if (trimmed.isEmpty()) vText("...") else vText("$trimmed...")
}

private fun blendColor(from: Int, to: Int, t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)

    val fromA = (from ushr 24) and 0xFF
    val fromR = (from ushr 16) and 0xFF
    val fromG = (from ushr 8) and 0xFF
    val fromB = from and 0xFF

    val toA = (to ushr 24) and 0xFF
    val toR = (to ushr 16) and 0xFF
    val toG = (to ushr 8) and 0xFF
    val toB = to and 0xFF

    val outA = (fromA + ((toA - fromA) * clamped)).toInt().coerceIn(0, 255)
    val outR = (fromR + ((toR - fromR) * clamped)).toInt().coerceIn(0, 255)
    val outG = (fromG + ((toG - fromG) * clamped)).toInt().coerceIn(0, 255)
    val outB = (fromB + ((toB - fromB) * clamped)).toInt().coerceIn(0, 255)

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}

private fun roundedSurface(fill: Int, border: Int, radius: Int): Surface {
    return Surface { context, component ->
        drawRoundedPanel(
            context,
            component.x(),
            component.y(),
            component.width(),
            component.height(),
            fill,
            border,
            radius,
        )
    }
}

private fun drawRoundedPanel(
    context: OwoUIGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    fillColor: Int,
    borderColor: Int,
    radius: Int = 12,
) {
    if (width <= 0 || height <= 0) return

    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)
    fillRoundedRect(context, x, y, width, height, cornerRadius, borderColor)

    val innerInset = if (cornerRadius >= 16 && width > 8 && height > 8) 2 else 1
    if (width > innerInset * 2 && height > innerInset * 2) {
        fillRoundedRect(
            context,
            x + innerInset,
            y + innerInset,
            width - (innerInset * 2),
            height - (innerInset * 2),
            (cornerRadius - innerInset).coerceAtLeast(0),
            fillColor,
        )
    }
}

private fun fillRoundedRect(
    context: OwoUIGraphics,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    radius: Int,
    color: Int,
) {
    if (width <= 0 || height <= 0) return

    if (radius <= 0) {
        context.fill(x, y, x + width, y + height, color)
        return
    }

    val cornerRadius = radius.coerceIn(0, min(width, height) / 2)

    context.fill(x + cornerRadius, y, x + width - cornerRadius, y + height, color)
    context.fill(x, y + cornerRadius, x + cornerRadius, y + height - cornerRadius, color)
    context.fill(x + width - cornerRadius, y + cornerRadius, x + width, y + height - cornerRadius, color)

    for (row in 0 until cornerRadius) {
        val dy = cornerRadius - row - 1
        val dx = sqrt((cornerRadius * cornerRadius - dy * dy).toDouble()).toInt().coerceAtMost(cornerRadius)
        val left = x + cornerRadius - dx
        val right = x + width - cornerRadius + dx

        context.fill(left, y + row, right, y + row + 1, color)
        context.fill(left, y + height - row - 1, right, y + height - row, color)
    }
}
