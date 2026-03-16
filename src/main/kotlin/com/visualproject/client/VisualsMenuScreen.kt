package com.visualproject.client

import io.wispforest.owo.ui.base.BaseComponent
import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.component.LabelComponent
import io.wispforest.owo.ui.component.TextBoxComponent
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.ScrollContainer
import io.wispforest.owo.ui.core.Color
import io.wispforest.owo.ui.core.CursorStyle
import io.wispforest.owo.ui.core.HorizontalAlignment
import io.wispforest.owo.ui.core.Insets
import io.wispforest.owo.ui.core.OwoUIAdapter
import io.wispforest.owo.ui.core.OwoUIDrawContext
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.Surface
import io.wispforest.owo.ui.core.VerticalAlignment
import net.minecraft.client.Minecraft
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class VisualsMenuScreen : BaseOwoScreen<FlowLayout>() {

    private object Theme {
        const val frameGap = 8

        const val panelFill = 0xC00A0D19.toInt()
        const val panelBorder = 0x60303852
        const val panelRadius = 20
        const val panelPadding = 14
        const val panelGap = 6
        const val panelTopGlowStart = 0x2C2D3660
        const val panelTopGlowMid = 0x18232A48
        const val panelTopGlowEnd = 0x001A2137
        const val panelBottomShade = 0x14111723

        const val topBarHeight = 28
        const val topBarGap = 8

        const val searchFill = 0xA8101525.toInt()
        const val searchBorder = 0x46343D58
        const val searchRadius = 12
        const val searchHeight = 26

        const val moduleRowGap = 7
        const val moduleColumnGap = 8
        const val moduleCardHeight = 44

        const val cardFill = 0x960E1220.toInt()
        const val cardBorder = 0x2D323A4F
        const val cardHoverFill = 0xB1131A2C.toInt()
        const val cardHoverBorder = 0x4A3C4968
        const val cardRadius = 14

        const val settingsFill = 0x9E10182A.toInt()
        const val settingsBorder = 0x4E374460
        const val settingsRadius = 12
        const val settingsHeight = 92

        const val iconSlotFill = 0x6A12172A
        const val iconSlotBorder = 0x40323A57
        const val iconSlotRadius = 9

        const val toggleWidth = 34
        const val toggleHeight = 18

        const val dockFill = 0xB10B1021.toInt()
        const val dockBorder = 0x55313858
        const val dockRadius = 16
        const val dockHeight = 48
        const val dockGap = 8

        const val accent = 0xFF7D5BFF.toInt()
        const val accentStrong = 0xFF8A71FF.toInt()

        const val textPrimary = 0xFFF4F6FF.toInt()
        const val textSecondary = 0xFF8F97B5.toInt()
        const val textMuted = 0xFF707895.toInt()

        const val scrollbarColor = 0x66505A7A
        const val scrollbarThickness = 3
        const val scrollStep = 12
    }

    companion object {
        private val persistentModuleEnabled: MutableMap<String, Boolean> = mutableMapOf()
        private val persistentModuleSettingsEnabled: MutableMap<String, Boolean> = mutableMapOf()
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

    private val moduleEnabledState = persistentModuleEnabled
    private val moduleSettingsEnabledState = persistentModuleSettingsEnabled
    private val toggleProgressById: MutableMap<String, Float> = mutableMapOf()

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
        ModuleEntry("jump_circle", "Jump Circle", "J", Tab.VISUALS),

        ModuleEntry("armor_hud", "Armor HUD", "A", Tab.HUD),
        ModuleEntry("direction", "Direction", "D", Tab.HUD),
        ModuleEntry("fps", "FPS", "F", Tab.HUD),
        ModuleEntry("keystrokes", "Key Strokes", "K", Tab.HUD),
        ModuleEntry("potion_icons", "Potion Icons", "P", Tab.HUD),
        ModuleEntry("radar", "Radar", "R", Tab.HUD),
        ModuleEntry("session_time", "Session Time", "S", Tab.HUD),
        ModuleEntry("target_hud", "Target HUD", "T", Tab.HUD),

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

        val glowHeight = (component.height() * 0.26f).toInt().coerceAtLeast(38)
        context.drawGradientRect(
            component.x() + 2,
            component.y() + 2,
            (component.width() - 4).coerceAtLeast(2),
            glowHeight,
            Theme.panelTopGlowStart,
            Theme.panelTopGlowMid,
            Theme.panelTopGlowEnd,
            Theme.panelTopGlowEnd,
        )

        context.drawGradientRect(
            component.x() + 2,
            component.y() + component.height() - 48,
            (component.width() - 4).coerceAtLeast(2),
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
            moduleEnabledState.putIfAbsent(module.id, false)
            toggleProgressById.putIfAbsent(module.id, 0f)

            moduleSettingsEnabledState.putIfAbsent("${module.id}:visible_hud", false)
            moduleSettingsEnabledState.putIfAbsent("${module.id}:accent_sync", true)
        }
    }

    override fun createAdapter(): OwoUIAdapter<FlowLayout> {
        return OwoUIAdapter.create(this, Containers::verticalFlow)
    }

    override fun build(rootComponent: FlowLayout) {
        calculateWindowGeometry()

        rootComponent
            .surface(Surface.blur(2.6f, 14f))
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

        val maxPanelWidth = (width - 96).coerceAtLeast(460)
        val targetPanelWidth = (width * 0.58f).toInt()
        panelWidth = targetPanelWidth.coerceIn(min(560, maxPanelWidth), min(840, maxPanelWidth))

        val maxPanelHeight = (height - 128).coerceAtLeast(300)
        val targetPanelHeight = (height * 0.62f).toInt()
        panelHeight = targetPanelHeight.coerceIn(min(330, maxPanelHeight), min(500, maxPanelHeight))

        searchWidth = (panelWidth * 0.34f).toInt().coerceIn(220, 320)

        val contentWidth = panelWidth - (Theme.panelPadding * 2)
        moduleCardWidth = ((contentWidth - Theme.moduleColumnGap) / 2).coerceAtLeast(180)
    }

    private fun buildMainPanel(): FlowLayout {
        val panel = Containers.verticalFlow(Sizing.fixed(panelWidth), Sizing.fixed(panelHeight))
        panel.surface(panelSurface)
        panel.padding(Insets.of(Theme.panelPadding))
        panel.gap(0)

        val content = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100))
        content.gap(Theme.panelGap)

        buildTopBar(content)
        buildModuleSection(content)

        panel.child(content)

        return panel
    }

    private fun buildTopBar(panel: FlowLayout) {
        val topShell = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        topShell.gap(4)

        val topBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(Theme.topBarHeight))
        topBar.verticalAlignment(VerticalAlignment.CENTER)
        topBar.gap(Theme.topBarGap)

        tabStrip = Containers.horizontalFlow(Sizing.expand(100), Sizing.fixed(22))
        tabStrip.verticalAlignment(VerticalAlignment.CENTER)
        tabStrip.gap(6)
        topBar.child(tabStrip)

        topBar.child(buildSearchBar())

        topShell.child(topBar)
        topShell.child(
            Components.box(Sizing.fill(100), Sizing.fixed(1))
                .fill(true)
                .color(Color.ofArgb(0x1A2D3550))
        )

        panel.child(topShell)
    }

    private fun buildSearchBar(): FlowLayout {
        val searchBar = Containers.horizontalFlow(Sizing.fixed(searchWidth), Sizing.fixed(Theme.searchHeight))
        searchBar.surface(searchSurface)
        searchBar.padding(Insets.of(4, 4, 10, 10))
        searchBar.verticalAlignment(VerticalAlignment.CENTER)
        searchBar.gap(6)

        searchBar.child(
            Components.label(Component.literal("?"))
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
        searchInput.setHint(Component.literal("Поиск"))
        searchInput.onChanged().subscribe(TextBoxComponent.OnChanged { value ->
            searchQuery = value.trim()
            rebuildModuleCards(filteredModulesForActiveTab())
        })
        searchBar.child(searchInput)

        searchBar.child(
            Components.label(Component.literal("?"))
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
        moduleRows.padding(Insets.of(1))

        moduleScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(100), moduleRows)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(Theme.scrollbarColor)))
            .scrollbarThiccness(Theme.scrollbarThickness)
            .scrollStep(Theme.scrollStep)

        val moduleBody = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
        moduleBody.child(moduleScroll)

        panel.child(moduleBody)
    }

    private fun buildDockHolder(): FlowLayout {
        val dockHolder = Containers.horizontalFlow(Sizing.fixed(panelWidth), Sizing.content())
        dockHolder.horizontalAlignment(HorizontalAlignment.CENTER)

        dockStrip = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(Theme.dockHeight))
        dockStrip.surface(dockSurface)
        dockStrip.padding(Insets.of(8, 8, 10, 10))
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
            return
        }

        entries.chunked(2).forEach { pair ->
            val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(Theme.moduleCardHeight))
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
    }

    private fun buildEmptyCard(): FlowLayout {
        val emptyCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(Theme.moduleCardHeight))
        emptyCard.surface(cardSurface)
        emptyCard.padding(Insets.of(10, 10, 14, 14))
        emptyCard.verticalAlignment(VerticalAlignment.CENTER)

        emptyCard.child(
            Components.label(Component.literal("Ничего не найдено"))
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
        panel.padding(Insets.of(8, 8, 12, 12))
        panel.gap(8)

        panel.child(
            Components.label(Component.literal("Settings • ${module.title}"))
                .configure<LabelComponent> { label ->
                    label.color(Color.ofRgb(0xA9B3D4))
                    label.shadow(false)
                }
        )

        panel.child(buildInlineSettingRow(module, "Visible In HUD", "visible_hud"))
        panel.child(buildInlineSettingRow(module, "Accent Sync", "accent_sync"))

        return panel
    }

    private fun buildInlineSettingRow(module: ModuleEntry, title: String, keySuffix: String): FlowLayout {
        val stateKey = "${module.id}:$keySuffix"

        val row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22))
        row.verticalAlignment(VerticalAlignment.CENTER)
        row.gap(8)

        row.child(
            Components.label(Component.literal(title))
                .configure<LabelComponent> { label ->
                    label.color(Color.ofRgb(0x8D97B8))
                    label.shadow(false)
                    label.sizing(Sizing.expand(100), Sizing.content())
                }
        )

        row.child(
            InlineSwitchComponent(moduleSettingsEnabledState[stateKey] == true) { enabled ->
                moduleSettingsEnabledState[stateKey] = enabled
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

        override fun draw(context: OwoUIDrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)
            val enabled = moduleEnabledState[module.id] == true
            val expanded = openedModuleSettingsId == module.id

            val border = when {
                expanded -> 0xB1876DFF.toInt()
                hovered -> Theme.cardHoverBorder
                enabled -> 0x3F4E5B86
                else -> Theme.cardBorder
            }
            val fill = when {
                hovered -> Theme.cardHoverFill
                enabled -> 0xA012192B.toInt()
                else -> Theme.cardFill
            }

            drawRoundedPanel(context, this.x, this.y, this.width, this.height, fill, border, Theme.cardRadius)

            val slotSize = 18
            val slotX = this.x + 10
            val slotY = this.y + (this.height - slotSize) / 2
            drawRoundedPanel(context, slotX, slotY, slotSize, slotSize, Theme.iconSlotFill, Theme.iconSlotBorder, Theme.iconSlotRadius)

            val font = Minecraft.getInstance().font
            val iconWidth = font.width(module.iconGlyph)
            context.drawString(
                font,
                Component.literal(module.iconGlyph),
                slotX + (slotSize - iconWidth) / 2,
                slotY + (slotSize - font.lineHeight) / 2,
                0xFF7E86A4.toInt(),
                false,
            )

            val textColor = if (enabled) 0xFFA2ACCA.toInt() else 0xFF8E96B4.toInt()
            context.drawString(
                font,
                Component.literal(module.title),
                slotX + slotSize + 8,
                this.y + (this.height - font.lineHeight) / 2,
                textColor,
                false,
            )

            val toggleX = this.x + this.width - Theme.toggleWidth - 10
            val toggleY = this.y + (this.height - Theme.toggleHeight) / 2
            val trackBorder = if (enabled) 0xFF8D72FF.toInt() else 0x7A3A425A
            val trackFill = if (enabled) 0xD4664BFF.toInt() else 0x6E1E2639
            drawRoundedPanel(context, toggleX, toggleY, Theme.toggleWidth, Theme.toggleHeight, trackFill, trackBorder, Theme.toggleHeight / 2)

            val target = if (enabled) 1f else 0f
            val current = toggleProgressById[module.id] ?: target
            val next = current + (target - current) * 0.35f
            val settled = if (abs(target - next) < 0.002f) target else next
            toggleProgressById[module.id] = settled

            val knobRadius = ((Theme.toggleHeight - 6) / 2).coerceAtLeast(3)
            val minCenterX = toggleX + knobRadius + 3
            val maxCenterX = toggleX + Theme.toggleWidth - knobRadius - 3
            val knobCenterX = (minCenterX + (maxCenterX - minCenterX) * settled).toInt()
            val knobCenterY = toggleY + Theme.toggleHeight / 2

            drawRoundedPanel(
                context,
                knobCenterX - knobRadius,
                knobCenterY - knobRadius,
                knobRadius * 2,
                knobRadius * 2,
                0xFFF1F4FF.toInt(),
                0xA0C8D0F0.toInt(),
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
                val next = !(moduleEnabledState[module.id] ?: false)
                moduleEnabledState[module.id] = next
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
            this.sizing(Sizing.content(), Sizing.fixed(20))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = Minecraft.getInstance().font.width(text) + 8

        override fun determineVerticalContentSize(sizing: Sizing): Int = 20

        override fun draw(context: OwoUIDrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)
            val color = when {
                active -> Theme.textPrimary
                hovered -> Theme.textSecondary
                else -> Theme.textMuted
            }

            val font = Minecraft.getInstance().font
            context.drawString(font, Component.literal(text), this.x + 2, this.y + (this.height - font.lineHeight) / 2, color, false)

            if (active) {
                context.drawGradientRect(
                    this.x + 2,
                    this.y + this.height - 2,
                    (this.width - 4).coerceAtLeast(2),
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
            this.sizing(Sizing.fixed(34), Sizing.fixed(34))
            this.cursorStyle(CursorStyle.HAND)
        }

        override fun determineHorizontalContentSize(sizing: Sizing): Int = 34

        override fun determineVerticalContentSize(sizing: Sizing): Int = 34

        override fun draw(context: OwoUIDrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val hovered = mouseX in this.x until (this.x + this.width) && mouseY in this.y until (this.y + this.height)

            val border = when {
                active -> Theme.accentStrong
                hovered -> 0x7A49506D
                else -> 0x55343E59
            }
            val fill = when {
                active -> 0xFF6D4CFF.toInt()
                hovered -> 0x7A1A2138
                else -> 0x6010172A
            }

            drawRoundedPanel(context, this.x, this.y, this.width, this.height, fill, border, 10)

            val font = Minecraft.getInstance().font
            val color = if (active) 0xFFF9F6FF.toInt() else 0xFF838CAC.toInt()
            val textWidth = font.width(iconText)
            context.drawString(
                font,
                Component.literal(iconText),
                this.x + (this.width - textWidth) / 2,
                this.y + (this.height - font.lineHeight) / 2,
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

        override fun draw(context: OwoUIDrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, delta: Float) {
            val trackBorder = if (enabled) 0xFF8D72FF.toInt() else 0x7A3A425A
            val trackFill = if (enabled) 0xD4664BFF.toInt() else 0x6E1E2639
            drawRoundedPanel(context, this.x, this.y, this.width, this.height, trackFill, trackBorder, this.height / 2)

            val knobRadius = ((this.height - 6) / 2).coerceAtLeast(3)
            val knobCenterX = if (enabled) this.x + this.width - knobRadius - 3 else this.x + knobRadius + 3
            val knobCenterY = this.y + this.height / 2

            drawRoundedPanel(
                context,
                knobCenterX - knobRadius,
                knobCenterY - knobRadius,
                knobRadius * 2,
                knobRadius * 2,
                0xFFF1F4FF.toInt(),
                0xA0C8D0F0.toInt(),
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
    context: OwoUIDrawContext,
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

    if (width > 2 && height > 2) {
        fillRoundedRect(
            context,
            x + 1,
            y + 1,
            width - 2,
            height - 2,
            (cornerRadius - 1).coerceAtLeast(0),
            fillColor,
        )
    }
}

private fun fillRoundedRect(
    context: OwoUIDrawContext,
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
