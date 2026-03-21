package com.visualproject.client

import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.VisualMenuTheme
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.ui.menu.drawRoundedPanel
import com.visualproject.client.ui.menu.drawVerticalGradient
import com.visualproject.client.ui.menu.fillRoundedRect
import com.visualproject.client.ui.menu.fitStyledText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.min

class ExperimentalVisualsMenuScreen : Screen(Component.empty()) {

    private data class IntRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        }
    }

    private data class ScreenLayout(
        val panel: IntRect,
        val sidebar: IntRect,
        val header: IntRect,
        val search: IntRect,
        val cardsViewport: IntRect,
        val settings: IntRect,
        val footer: IntRect,
    )

    private data class TabLayout(
        val tab: VisualMenuTab,
        val rect: IntRect,
    )

    private data class ModuleCardLayout(
        val module: VisualMenuModuleEntry,
        val rect: IntRect,
        val toggleRect: IntRect,
    )

    private sealed class SettingRowLayout(
        val key: String,
        val label: String,
        val rect: IntRect,
    ) {
        class Toggle(
            key: String,
            label: String,
            rect: IntRect,
            val switchRect: IntRect,
        ) : SettingRowLayout(key, label, rect)

        class Input(
            key: String,
            label: String,
            rect: IntRect,
            val fieldRect: IntRect,
            val hint: String,
        ) : SettingRowLayout(key, label, rect)
    }

    private object Theme {
        const val overlayTop = 0xBC050913.toInt()
        const val overlayBottom = 0xD20A1019.toInt()
        const val panelGap = 14
        const val sectionGap = 12
        const val sidebarWidth = 108
        const val headerHeight = 58
        const val footerHeight = 24
        const val cardsPadding = 12
        const val cardVerticalInset = 6
        const val settingsWidth = 236
        const val tabHeight = 34
        const val tabGap = 10
        const val cardHeight = 68
        const val cardGap = 12
        const val toggleWidth = 40
        const val toggleHeight = 20
        const val toggleInset = 14
        const val settingsRowHeight = 34
        const val inputRowHeight = 46
        const val sectionRadius = 22f
    }

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
        VisualMenuModuleEntry(VisualClientMod.sdfTestModuleId, "SDF Test HUD", "Q", VisualMenuTab.HUD),
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

    private var activeTab = VisualMenuTab.VISUALS
    private var searchQuery = ""
    private var selectedModuleId: String? = null
    private var cardScroll = 0
    private var settingScroll = 0

    private lateinit var searchBox: EditBox
    private val settingInputs = LinkedHashMap<String, EditBox>()

    init {
        modules.forEach { module ->
            ModuleStateStore.ensureModule(module.id, defaultEnabled = false)
            ModuleStateStore.ensureSetting("${module.id}:visible_hud", defaultValue = false)
            ModuleStateStore.ensureSetting("${module.id}:accent_sync", defaultValue = true)
            if (module.id == "watermark" || module.id == VisualClientMod.sdfTestModuleId) {
                ModuleStateStore.ensureSetting("${module.id}:outer_glow", defaultValue = true)
            }
            if (module.id == "watermark") {
                ModuleStateStore.ensureNumberSetting("${module.id}:outer_glow_strength", defaultValue = 0.60f)
                ModuleStateStore.ensureNumberSetting("${module.id}:outer_glow_distance", defaultValue = 22f)
                ModuleStateStore.ensureTextSetting("${module.id}:outer_glow_color", defaultValue = "#6170D8")
            }
        }
    }

    override fun init() {
        syncSelectedModule(forceSelectFirst = true)
        rebuildMenuWidgets()
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Disable vanilla menu blur for the experimental screen.
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val layout = computeLayout()
        val filtered = filteredModules()
        clampScroll(layout, filtered)
        syncWidgetLayout(layout)

        renderTransparentBackground(context)
        context.fillGradient(0, 0, width, height, 0x22060A12, 0x32080D16)
        drawVerticalGradient(context, 0, 0, width, height / 3, 0x163F63B8, 0x00000000)
        drawVerticalGradient(context, 0, height - (height / 3), width, height / 3, 0x00000000, 0x14000000)

        SdfPanelRenderer.draw(context, layout.panel.x, layout.panel.y, layout.panel.width, layout.panel.height, shellStyle())
        SdfPanelRenderer.draw(context, layout.sidebar.x, layout.sidebar.y, layout.sidebar.width, layout.sidebar.height, sidebarStyle())
        SdfPanelRenderer.draw(context, layout.header.x, layout.header.y, layout.header.width, layout.header.height, sectionStyle())
        SdfPanelRenderer.draw(context, layout.cardsViewport.x, layout.cardsViewport.y, layout.cardsViewport.width, layout.cardsViewport.height, listStyle())
        SdfPanelRenderer.draw(context, layout.settings.x, layout.settings.y, layout.settings.width, layout.settings.height, settingsStyle())

        drawHeader(context, layout)
        drawTabs(context, layout, mouseX.toDouble(), mouseY.toDouble())
        drawCards(context, layout, filtered, mouseX.toDouble(), mouseY.toDouble())
        drawSettings(context, layout, mouseX.toDouble(), mouseY.toDouble())
        drawFooter(context, layout)

        context.enableScissor(
            layout.panel.x,
            layout.panel.y,
            layout.panel.x + layout.panel.width,
            layout.panel.y + layout.panel.height,
        )
        super.render(context, mouseX, mouseY, partialTick)
        context.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        if (mouseButtonEvent.button() == 0) {
            tabLayouts(computeLayout()).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { clicked ->
                if (activeTab != clicked.tab) {
                    activeTab = clicked.tab
                    cardScroll = 0
                    settingScroll = 0
                    syncSelectedModule(forceSelectFirst = true)
                    rebuildMenuWidgets()
                }
                return true
            }

            settingToggleRows(computeLayout()).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { row ->
                ModuleStateStore.setSettingEnabled(row.key, !ModuleStateStore.isSettingEnabled(row.key))
                return true
            }

            cardLayouts(computeLayout(), filteredModules()).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { card ->
                if (card.toggleRect.contains(mouseX, mouseY)) {
                    ModuleStateStore.toggleEnabled(card.module.id)
                } else {
                    selectedModuleId = card.module.id
                    settingScroll = 0
                    rebuildMenuWidgets()
                }
                return true
            }
        }

        return super.mouseClicked(mouseButtonEvent, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = computeLayout()
        if (settingsViewport(layout).contains(mouseX, mouseY)) {
            val maxScroll = maxSettingScroll(layout)
            if (maxScroll > 0) {
                settingScroll = (settingScroll - (scrollY * 28.0).toInt()).coerceIn(0, maxScroll)
                syncWidgetLayout(layout)
                return true
            }
        }
        if (layout.cardsViewport.contains(mouseX, mouseY)) {
            val maxScroll = maxCardScroll(layout, filteredModules())
            if (maxScroll > 0) {
                cardScroll = (cardScroll - (scrollY * 28.0).toInt()).coerceIn(0, maxScroll)
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun rebuildMenuWidgets() {
        clearWidgets()
        settingInputs.clear()

        val layout = computeLayout()
        searchBox = EditBox(font, layout.search.x, layout.search.y, layout.search.width, layout.search.height, Component.empty())
        searchBox.setBordered(false)
        searchBox.setTextColor(VisualMenuTheme.textPrimary)
        searchBox.setTextColorUneditable(VisualMenuTheme.textDim)
        searchBox.setHint(vText("Search modules"))
        searchBox.setValue(searchQuery)
        searchBox.setResponder { value ->
            val previous = selectedModuleId
            searchQuery = value.trim()
            cardScroll = 0
            syncSelectedModule(forceSelectFirst = false)
            if (previous != selectedModuleId) {
                rebuildMenuWidgets()
            }
        }
        addRenderableWidget(searchBox)

        settingInputRows(layout).forEach { row ->
            val input = EditBox(
                font,
                row.fieldRect.x + 8,
                row.fieldRect.y + 2,
                row.fieldRect.width - 16,
                row.fieldRect.height - 4,
                Component.empty(),
            )
            input.setBordered(false)
            input.setTextColor(VisualMenuTheme.textPrimary)
            input.setTextColorUneditable(VisualMenuTheme.textDim)
            input.setHint(vText(row.hint))
            input.setValue(settingValueFor(row.key))
            input.setResponder { raw ->
                when (row.key) {
                    "watermark:outer_glow_strength" -> normalizeFloat(raw, 0f, 1.25f)?.let {
                        ModuleStateStore.setNumberSetting(row.key, it)
                    }

                    "watermark:outer_glow_distance" -> normalizeFloat(raw, 0f, 64f)?.let {
                        ModuleStateStore.setNumberSetting(row.key, it)
                    }

                    "watermark:outer_glow_color" -> normalizeHexColor(raw)?.let {
                        ModuleStateStore.setTextSetting(row.key, it)
                    }
                }
            }
            settingInputs[row.key] = input
            addRenderableWidget(input)
        }

        syncWidgetLayout(layout)
    }

    private fun computeLayout(): ScreenLayout {
        val panelWidth = (width * 0.84f).toInt().coerceIn(700, 1120.coerceAtMost(width - 40))
        val panelHeight = (height * 0.80f).toInt().coerceIn(390, 760.coerceAtMost(height - 28))
        val panelX = (width - panelWidth) / 2
        val panelY = (height - panelHeight) / 2
        val panel = IntRect(panelX, panelY, panelWidth, panelHeight)

        val sidebar = IntRect(panelX + Theme.panelGap, panelY + Theme.panelGap, Theme.sidebarWidth, panelHeight - (Theme.panelGap * 2))
        val contentX = sidebar.x + sidebar.width + Theme.sectionGap
        val contentWidth = panelX + panelWidth - Theme.panelGap - contentX

        val header = IntRect(contentX, panelY + Theme.panelGap, contentWidth, Theme.headerHeight)
        val footer = IntRect(contentX, panelY + panelHeight - Theme.panelGap - Theme.footerHeight, contentWidth, Theme.footerHeight)

        val settingsWidth = min(Theme.settingsWidth, (contentWidth * 0.34f).toInt().coerceAtLeast(212))
        val contentTop = header.y + header.height + Theme.sectionGap
        val contentBottom = footer.y - Theme.sectionGap
        val viewportHeight = (contentBottom - contentTop).coerceAtLeast(120)

        val cardsViewport = IntRect(
            contentX,
            contentTop,
            (contentWidth - settingsWidth - Theme.sectionGap).coerceAtLeast(220),
            viewportHeight,
        )
        val settings = IntRect(
            cardsViewport.x + cardsViewport.width + Theme.sectionGap,
            contentTop,
            settingsWidth,
            viewportHeight,
        )

        val search = IntRect(
            header.x + header.width - 258,
            header.y + 14,
            236,
            28,
        )

        return ScreenLayout(panel, sidebar, header, search, cardsViewport, settings, footer)
    }

    private fun drawHeader(context: GuiGraphics, layout: ScreenLayout) {
        context.drawString(font, vBrandText("Visual Client"), layout.header.x + 18, layout.header.y + 14, 0xFFF3F5FF.toInt(), false)
        context.drawString(font, vText("SDF prototype"), layout.header.x + 18, layout.header.y + 28, 0xFF8692B4.toInt(), false)

        drawRoundedPanel(
            context,
            layout.search.x,
            layout.search.y,
            layout.search.width,
            layout.search.height,
            0xD011192A.toInt(),
            0x523E4D74,
            14,
        )

        val counter = "${filteredModules().size} modules"
        val counterWidth = font.width(vText(counter))
        context.drawString(
            font,
            vText(counter),
            layout.search.x - counterWidth - 12,
            layout.search.y + 9,
            0xFF7380A1.toInt(),
            false,
        )
    }

    private fun drawTabs(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        context.drawString(font, vText("Menu"), layout.sidebar.x + 16, layout.sidebar.y + 16, 0xFF7480A5.toInt(), false)
        context.drawString(font, vText("Visual Client"), layout.sidebar.x + 16, layout.sidebar.y + 30, 0xFFF1F3FF.toInt(), false)

        tabLayouts(layout).forEach { tabLayout ->
            val selected = tabLayout.tab == activeTab
            val hovered = tabLayout.rect.contains(mouseX, mouseY)

            SdfPanelRenderer.draw(
                context,
                tabLayout.rect.x,
                tabLayout.rect.y,
                tabLayout.rect.width,
                tabLayout.rect.height,
                tabStyle(selected, hovered),
            )

            context.drawString(
                font,
                vText(tabLayout.tab.title),
                tabLayout.rect.x + 14,
                tabLayout.rect.y + 11,
                if (selected) 0xFFF6F2FF.toInt() else 0xFFA5B0D0.toInt(),
                false,
            )
        }
    }

    private fun drawCards(context: GuiGraphics, layout: ScreenLayout, filtered: List<VisualMenuModuleEntry>, mouseX: Double, mouseY: Double) {
        context.enableScissor(
            layout.cardsViewport.x,
            layout.cardsViewport.y,
            layout.cardsViewport.x + layout.cardsViewport.width,
            layout.cardsViewport.y + layout.cardsViewport.height,
        )

        cardLayouts(layout, filtered).forEach { card ->
            if (card.rect.y + card.rect.height < layout.cardsViewport.y || card.rect.y > layout.cardsViewport.y + layout.cardsViewport.height) {
                return@forEach
            }

            val hovered = card.rect.contains(mouseX, mouseY)
            val enabled = ModuleStateStore.isEnabled(card.module.id)
            val selected = selectedModuleId == card.module.id
            val backdropRect = IntRect(card.rect.x - 6, card.rect.y - 6, card.rect.width + 12, card.rect.height + 12)
            val backdropStyle = cardBackdropStyle(enabled, selected, hovered)
            val surfaceStyle = cardSurfaceStyle(enabled, selected, hovered)
            val clipRect = SdfPanelRenderer.ClipRect(
                layout.cardsViewport.x,
                layout.cardsViewport.y,
                layout.cardsViewport.width,
                layout.cardsViewport.height,
            )

            SdfPanelRenderer.draw(
                context,
                backdropRect.x,
                backdropRect.y,
                backdropRect.width,
                backdropRect.height,
                backdropStyle,
                clipRect,
            )
            SdfPanelRenderer.draw(
                context,
                card.rect.x,
                card.rect.y,
                card.rect.width,
                card.rect.height,
                surfaceStyle,
                clipRect,
            )

            drawRoundedPanel(context, card.rect.x + 12, card.rect.y + 12, 26, 26, 0xA2141E33.toInt(), 0x5B3E4A69, 10)
            context.drawString(font, vText(card.module.iconGlyph), card.rect.x + 21, card.rect.y + 20, 0xFFD6DDF7.toInt(), false)

            val title = fitStyledText(font, card.module.title, card.rect.width - 88)
            context.drawString(font, title, card.rect.x + 48, card.rect.y + 16, 0xFFF4F6FF.toInt(), false)
            context.drawString(
                font,
                vText(if (enabled) "Enabled" else "Disabled"),
                card.rect.x + 48,
                card.rect.y + 31,
                if (enabled) 0xFF9CB8FF.toInt() else 0xFF7280A3.toInt(),
                false,
            )

            drawToggle(context, card.toggleRect, enabled)
        }

        context.disableScissor()
    }

    private fun drawSettings(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        val selectedModule = modules.firstOrNull { it.id == selectedModuleId }
        if (selectedModule == null) {
            context.drawString(font, vText("No module selected"), layout.settings.x + 16, layout.settings.y + 16, 0xFF7E88A8.toInt(), false)
            return
        }

        context.drawString(font, vText("Settings"), layout.settings.x + 16, layout.settings.y + 14, 0xFF7683A5.toInt(), false)
        context.drawString(font, fitStyledText(font, selectedModule.title, layout.settings.width - 32), layout.settings.x + 16, layout.settings.y + 28, 0xFFF4F6FF.toInt(), false)

        val viewport = settingsViewport(layout)
        context.enableScissor(
            viewport.x,
            viewport.y,
            viewport.x + viewport.width,
            viewport.y + viewport.height,
        )
        settingRows(layout).forEach { row ->
            when (row) {
                is SettingRowLayout.Toggle -> {
                    val hovered = row.rect.contains(mouseX, mouseY)
                    val enabled = ModuleStateStore.isSettingEnabled(row.key)
                    val rowStyle = settingRowStyle(hovered)
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, rowStyle, clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 11, 0xFFD8DFF8.toInt(), false)
                    drawToggle(context, row.switchRect, enabled)
                }

                is SettingRowLayout.Input -> {
                    val rowStyle = inputRowStyle()
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, rowStyle, clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, 0xFFD8DFF8.toInt(), false)
                    drawRoundedPanel(
                        context,
                        row.fieldRect.x,
                        row.fieldRect.y,
                        row.fieldRect.width,
                        row.fieldRect.height,
                        0xCC10182A.toInt(),
                        0x57384866,
                        11,
                    )
                }
            }
        }
        context.disableScissor()
    }

    private fun drawFooter(context: GuiGraphics, layout: ScreenLayout) {
        context.drawString(
            font,
            vText("Right Shift: open menu"),
            layout.footer.x + 4,
            layout.footer.y + 7,
            0xFF6D7998.toInt(),
            false,
        )
    }

    private fun tabLayouts(layout: ScreenLayout): List<TabLayout> {
        var currentY = layout.sidebar.y + 64
        return VisualMenuTab.entries.map { tab ->
            val rect = IntRect(layout.sidebar.x + 10, currentY, layout.sidebar.width - 20, Theme.tabHeight)
            currentY += Theme.tabHeight + Theme.tabGap
            TabLayout(tab, rect)
        }
    }

    private fun cardLayouts(layout: ScreenLayout, filtered: List<VisualMenuModuleEntry>): List<ModuleCardLayout> {
        val columns = cardColumns(layout)
        val gap = Theme.cardGap
        val cardWidth = ((layout.cardsViewport.width - (Theme.cardsPadding * 2) - ((columns - 1) * gap)) / columns).coerceAtLeast(132)
        return filtered.mapIndexed { index, module ->
            val row = index / columns
            val column = index % columns
            val x = layout.cardsViewport.x + Theme.cardsPadding + (column * (cardWidth + gap))
            val y = layout.cardsViewport.y + Theme.cardsPadding + Theme.cardVerticalInset + (row * (Theme.cardHeight + gap)) - cardScroll
            val rect = IntRect(x, y, cardWidth, Theme.cardHeight)
            val toggleRect = IntRect(
                x + cardWidth - Theme.toggleWidth - Theme.toggleInset,
                y + ((Theme.cardHeight - Theme.toggleHeight) / 2),
                Theme.toggleWidth,
                Theme.toggleHeight,
            )
            ModuleCardLayout(module, rect, toggleRect)
        }
    }

    private fun settingRows(layout: ScreenLayout): List<SettingRowLayout> {
        val selectedModule = modules.firstOrNull { it.id == selectedModuleId } ?: return emptyList()
        val rows = mutableListOf<SettingRowLayout>()
        val startX = layout.settings.x + 14
        val width = layout.settings.width - 28
        var currentY = layout.settings.y + 56 - settingScroll

        fun addToggle(key: String, label: String) {
            val rect = IntRect(startX, currentY, width, Theme.settingsRowHeight)
            val switchRect = IntRect(rect.x + rect.width - 40, rect.y + 7, 30, 18)
            rows += SettingRowLayout.Toggle(key, label, rect, switchRect)
            currentY += Theme.settingsRowHeight + 10
        }

        fun addInput(key: String, label: String, hint: String) {
            val rect = IntRect(startX, currentY, width, Theme.inputRowHeight)
            val fieldRect = IntRect(rect.x + 10, rect.y + 22, rect.width - 20, 18)
            rows += SettingRowLayout.Input(key, label, rect, fieldRect, hint)
            currentY += Theme.inputRowHeight + 10
        }

        addToggle("${selectedModule.id}:visible_hud", "Visible In HUD")
        addToggle("${selectedModule.id}:accent_sync", "Accent Sync")

        if (selectedModule.id == "watermark" || selectedModule.id == VisualClientMod.sdfTestModuleId) {
            addToggle("${selectedModule.id}:outer_glow", "Outer Glow")
        }
        if (selectedModule.id == "watermark") {
            addInput("watermark:outer_glow_strength", "Glow Strength", "0.00 - 1.25")
            addInput("watermark:outer_glow_distance", "Glow Distance", "0 - 64")
            addInput("watermark:outer_glow_color", "Glow Color", "#RRGGBB")
        }

        return rows
    }

    private fun settingToggleRows(layout: ScreenLayout): List<SettingRowLayout.Toggle> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Toggle>()
    }

    private fun settingInputRows(layout: ScreenLayout): List<SettingRowLayout.Input> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Input>()
    }

    private fun filteredModules(): List<VisualMenuModuleEntry> {
        val query = searchQuery.trim()
        return modules.filter { module ->
            module.tab == activeTab && (query.isBlank() || module.title.contains(query, ignoreCase = true))
        }
    }

    private fun syncSelectedModule(forceSelectFirst: Boolean) {
        val filtered = filteredModules()
        if (filtered.isEmpty()) {
            selectedModuleId = null
            return
        }
        if (forceSelectFirst || filtered.none { it.id == selectedModuleId }) {
            selectedModuleId = filtered.first().id
        }
    }

    private fun cardColumns(layout: ScreenLayout): Int {
        return ((layout.cardsViewport.width - (Theme.cardsPadding * 2) + Theme.cardGap) / (156 + Theme.cardGap)).coerceIn(2, 3)
    }

    private fun maxCardScroll(layout: ScreenLayout, filtered: List<VisualMenuModuleEntry>): Int {
        val columns = cardColumns(layout)
        val rowCount = ceil(filtered.size / columns.toDouble()).toInt().coerceAtLeast(1)
        val contentHeight = Theme.cardsPadding + Theme.cardVerticalInset + (rowCount * Theme.cardHeight) + ((rowCount - 1) * Theme.cardGap) + Theme.cardsPadding + Theme.cardVerticalInset
        return (contentHeight - layout.cardsViewport.height).coerceAtLeast(0)
    }

    private fun clampScroll(layout: ScreenLayout, filtered: List<VisualMenuModuleEntry>) {
        cardScroll = cardScroll.coerceIn(0, maxCardScroll(layout, filtered))
        settingScroll = settingScroll.coerceIn(0, maxSettingScroll(layout))
    }

    private fun maxSettingScroll(layout: ScreenLayout): Int {
        val viewport = settingsViewport(layout)
        val bottom = settingRowsAt(layout, scroll = 0).maxOfOrNull { it.rect.y + it.rect.height } ?: viewport.y
        return (bottom - (viewport.y + viewport.height) + 12).coerceAtLeast(0)
    }

    private fun settingsViewport(layout: ScreenLayout): IntRect {
        val top = layout.settings.y + 52
        val bottom = layout.settings.y + layout.settings.height - 12
        return IntRect(
            layout.settings.x + 8,
            top,
            layout.settings.width - 16,
            (bottom - top).coerceAtLeast(0),
        )
    }

    private fun syncWidgetLayout(layout: ScreenLayout) {
        if (::searchBox.isInitialized) {
            searchBox.setX(layout.search.x)
            searchBox.setY(layout.search.y)
            searchBox.setWidth(layout.search.width)
            searchBox.setHeight(layout.search.height)
            searchBox.setVisible(true)
            searchBox.active = true
        }

        val viewport = settingsViewport(layout)
        val inputRows = settingInputRows(layout).associateBy { it.key }
        settingInputs.forEach { (key, input) ->
            val row = inputRows[key]
            if (row == null) {
                input.setVisible(false)
                input.active = false
            } else {
                input.setX(row.fieldRect.x + 8)
                input.setY(row.fieldRect.y + 2)
                input.setWidth(row.fieldRect.width - 16)
                input.setHeight(row.fieldRect.height - 4)

                val visible = row.fieldRect.y >= viewport.y &&
                    row.fieldRect.y + row.fieldRect.height <= viewport.y + viewport.height
                input.setVisible(visible)
                input.active = visible
            }
        }
    }

    private fun settingRowsAt(layout: ScreenLayout, scroll: Int): List<SettingRowLayout> {
        val previousScroll = settingScroll
        settingScroll = scroll
        return try {
            settingRows(layout)
        } finally {
            settingScroll = previousScroll
        }
    }

    private fun settingValueFor(key: String): String {
        return when (key) {
            "watermark:outer_glow_strength" -> formatNumber(ModuleStateStore.getNumberSetting(key, 0.60f))
            "watermark:outer_glow_distance" -> formatNumber(ModuleStateStore.getNumberSetting(key, 22f))
            "watermark:outer_glow_color" -> ModuleStateStore.getTextSetting(key, "#6170D8")
            else -> ""
        }
    }

    private fun formatNumber(value: Float): String {
        return if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
    }

    private fun normalizeFloat(raw: String, min: Float, max: Float): Float? {
        val parsed = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
        return parsed.coerceIn(min, max)
    }

    private fun normalizeHexColor(raw: String): String? {
        val compact = raw.trim().removePrefix("#")
        if (compact.length != 6 && compact.length != 8) return null
        if (!compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return "#${compact.uppercase()}"
    }

    private fun shellStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xFB0B111B.toInt(),
            borderColor = 0xBE334362.toInt(),
            borderWidthPx = 1.5f,
            radiusPx = 30f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 20f, strength = 0.08f, opacity = 0.06f),
            outerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 44f, strength = 0.22f, opacity = 0.14f),
            shade = SdfShadeStyle(0x1AFFFFFF, 0x22000000),
            neonBorder = SdfNeonBorderStyle(0xB28A71FF.toInt(), widthPx = 1.25f, softnessPx = 10f, strength = 0.90f),
        )
    }

    private fun sidebarStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF50E1728.toInt(),
            borderColor = 0xA13A4A69.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 16f, strength = 0.04f, opacity = 0.05f),
            outerGlow = SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.16f, opacity = 0.24f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x12000000),
            neonBorder = SdfNeonBorderStyle(0x5E6079FF, widthPx = 0.95f, softnessPx = 5f, strength = 0.42f),
        )
    }

    private fun sectionStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF40D1627.toInt(),
            borderColor = 0xA13A4A69.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 10f, strength = 0.03f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.15f, opacity = 0.18f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x10000000),
            neonBorder = SdfNeonBorderStyle(0x4A5F78FF, widthPx = 0.9f, softnessPx = 4.5f, strength = 0.34f),
        )
    }

    private fun listStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF70C1323.toInt(),
            borderColor = 0x96354563.toInt(),
            borderWidthPx = 1f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(VisualMenuTheme.accent, radiusPx = 18f, strength = 0.10f, opacity = 0.06f),
            shade = SdfShadeStyle(0x0BFFFFFF, 0x12000000),
            neonBorder = SdfNeonBorderStyle(0x3E546EFF, widthPx = 0.9f, softnessPx = 4f, strength = 0.28f),
        )
    }

    private fun settingsStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF70D1627.toInt(),
            borderColor = 0xA13A4A69.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 12f, strength = 0.05f, opacity = 0.04f),
            outerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 24f, strength = 0.14f, opacity = 0.08f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(0x666A79FF, widthPx = 1.0f, softnessPx = 5f, strength = 0.42f),
        )
    }

    private fun tabStyle(selected: Boolean, hovered: Boolean): SdfPanelStyle {
        val border = when {
            selected -> VisualMenuTheme.accentStrong
            hovered -> 0xFF536691.toInt()
            else -> 0xFF2E3B55.toInt()
        }
        val fill = when {
            selected -> 0xFF1A2540.toInt()
            hovered -> 0xFF142034.toInt()
            else -> 0xFF101827.toInt()
        }
        val glowOpacity = when {
            selected -> 0.14f
            hovered -> 0.08f
            else -> 0.03f
        }
        return SdfPanelStyle(
            baseColor = fill,
            borderColor = border,
            borderWidthPx = 1.2f,
            radiusPx = 16f,
            innerGlow = SdfGlowStyle(
                color = if (selected) VisualMenuTheme.accentStrong else 0xFFFFFFFF.toInt(),
                radiusPx = 12f,
                strength = if (selected) 0.10f else 0.03f,
                opacity = if (selected) 0.08f else 0.02f,
            ),
            outerGlow = SdfGlowStyle(
                color = if (selected) VisualMenuTheme.accentStrong else 0xFF000000.toInt(),
                radiusPx = 18f,
                strength = if (selected) 0.22f else 0.12f,
                opacity = glowOpacity,
            ),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(
                color = if (selected) 0xC08A71FF.toInt() else if (hovered) 0x545C79CC else 0x00000000,
                widthPx = if (selected) 1.1f else 0.9f,
                softnessPx = if (selected) 6f else 4f,
                strength = if (selected) 0.82f else if (hovered) 0.30f else 0f,
            ),
        )
    }

    private fun cardBackdropStyle(enabled: Boolean, selected: Boolean, hovered: Boolean): SdfPanelStyle {
        val emphasis = when {
            selected -> 1f
            enabled -> 0.72f
            hovered -> 0.45f
            else -> 0.18f
        }
        return SdfPanelStyle(
            baseColor = blendColor(0x08000000, 0x1A17264A, emphasis),
            borderColor = 0x00000000,
            borderWidthPx = 0f,
            radiusPx = 22f,
            innerGlow = SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f),
            outerGlow = SdfGlowStyle(
                color = if (enabled || selected) VisualMenuTheme.accentStrong else 0xFF324869.toInt(),
                radiusPx = 34f,
                strength = 0.26f + (0.14f * emphasis),
                opacity = 0.06f + (0.10f * emphasis),
            ),
            shade = SdfShadeStyle(0x00000000, 0x00000000),
        )
    }

    private fun cardSurfaceStyle(enabled: Boolean, selected: Boolean, hovered: Boolean): SdfPanelStyle {
        val fill = when {
            selected -> 0xFF18253E.toInt()
            enabled -> 0xFF152137.toInt()
            hovered -> 0xFF131F32.toInt()
            else -> 0xFF101829.toInt()
        }
        val border = when {
            selected -> VisualMenuTheme.accentStrong
            enabled -> 0xFF536B9E.toInt()
            hovered -> 0xFF415474.toInt()
            else -> 0xFF2C3953.toInt()
        }
        return SdfPanelStyle(
            baseColor = fill,
            borderColor = border,
            borderWidthPx = if (selected) 1.35f else 1.1f,
            radiusPx = 18f,
            innerGlow = if (enabled) {
                SdfGlowStyle(
                    color = VisualMenuTheme.accentStrong,
                    radiusPx = 14f,
                    strength = 0.12f,
                    opacity = if (selected) 0.16f else 0.11f,
                )
            } else {
                SdfGlowStyle(
                    color = 0xFFFFFFFF.toInt(),
                    radiusPx = 8f,
                    strength = 0.02f,
                    opacity = if (hovered) 0.03f else 0.02f,
                )
            },
            outerGlow = SdfGlowStyle(
                color = if (selected) VisualMenuTheme.accentStrong else 0xFF000000.toInt(),
                radiusPx = 14f,
                strength = 0.10f,
                opacity = if (selected) 0.06f else 0.02f,
            ),
            shade = SdfShadeStyle(0x12FFFFFF, 0x16000000),
            neonBorder = SdfNeonBorderStyle(
                color = when {
                    selected -> 0xCC8A71FF.toInt()
                    enabled -> 0x8C7285FF.toInt()
                    hovered -> 0x444F6DC8
                    else -> 0x00000000
                },
                widthPx = when {
                    selected -> 1.2f
                    enabled -> 1.0f
                    hovered -> 0.85f
                    else -> 0f
                },
                softnessPx = when {
                    selected -> 7f
                    enabled -> 5.5f
                    hovered -> 4f
                    else -> 0f
                },
                strength = when {
                    selected -> 1.00f
                    enabled -> 0.72f
                    hovered -> 0.25f
                    else -> 0f
                },
            ),
        )
    }

    private fun settingRowStyle(hovered: Boolean): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = if (hovered) 0xFF152138.toInt() else 0xFF111A2D.toInt(),
            borderColor = if (hovered) 0xFF4B5D85.toInt() else 0xFF33415F.toInt(),
            borderWidthPx = 1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 18f, strength = 0.12f, opacity = if (hovered) 0.07f else 0.04f),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(
                color = if (hovered) 0x885E71FF.toInt() else 0x30566CC4,
                widthPx = if (hovered) 1.0f else 0.8f,
                softnessPx = if (hovered) 5f else 4f,
                strength = if (hovered) 0.54f else 0.28f,
            ),
        )
    }

    private fun inputRowStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xFF111A2D.toInt(),
            borderColor = 0xFF33415F.toInt(),
            borderWidthPx = 1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(VisualMenuTheme.accentStrong, radiusPx = 18f, strength = 0.10f, opacity = 0.05f),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(0x425D72D1, widthPx = 0.9f, softnessPx = 4.5f, strength = 0.30f),
        )
    }

    private fun drawToggle(context: GuiGraphics, rect: IntRect, enabled: Boolean) {
        val trackFill = if (enabled) 0xCC5E4FBE.toInt() else 0xAA1A2436.toInt()
        val trackBorder = if (enabled) VisualMenuTheme.accentStrong else 0x7A3E4C68
        drawRoundedPanel(context, rect.x, rect.y, rect.width, rect.height, trackFill, trackBorder, rect.height / 2)

        val knobRadius = ((rect.height - 6) / 2).coerceAtLeast(4)
        val knobCenterX = if (enabled) rect.x + rect.width - knobRadius - 3 else rect.x + knobRadius + 3
        val knobCenterY = rect.y + rect.height / 2

        if (enabled) {
            fillRoundedRect(
                context,
                knobCenterX - knobRadius - 1,
                knobCenterY - knobRadius - 1,
                knobRadius * 2 + 2,
                knobRadius * 2 + 2,
                knobRadius + 1,
                0x6A8F7CFF,
            )
        }
        drawRoundedPanel(
            context,
            knobCenterX - knobRadius,
            knobCenterY - knobRadius,
            knobRadius * 2,
            knobRadius * 2,
            0xFFF2F4FF.toInt(),
            0x9AC8D5F6.toInt(),
            knobRadius,
        )
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }
}
