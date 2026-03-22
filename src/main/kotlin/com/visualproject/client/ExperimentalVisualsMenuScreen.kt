package com.visualproject.client

import com.mojang.blaze3d.platform.NativeImage
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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

    private data class ThemePickerLayout(
        val bounds: IntRect,
        val wheelRect: IntRect,
        val squareRect: IntRect,
        val previewRect: IntRect,
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

    private enum class PickerDragMode {
        HUE_WHEEL,
        SATURATION_VALUE,
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
    private var activeThemeColorKey: String? = null
    private var pickerDragMode: PickerDragMode? = null
    private var pickerHue = 0f
    private var pickerSaturation = 1f
    private var pickerValue = 1f
    private var pickerSquareHue = -1f
    private var hueWheelTextureReady = false

    private lateinit var searchBox: EditBox
    private val settingInputs = LinkedHashMap<String, EditBox>()
    private val hueWheelTextureId = Identifier.fromNamespaceAndPath("visualclient", "theme_picker_wheel")
    private val saturationValueTextureId = Identifier.fromNamespaceAndPath("visualclient", "theme_picker_square")

    init {
        modules.forEach { module ->
            ModuleStateStore.ensureModule(module.id, defaultEnabled = false)
            ModuleStateStore.ensureSetting("${module.id}:visible_hud", defaultValue = false)
            ModuleStateStore.ensureSetting("${module.id}:accent_sync", defaultValue = true)
            if (module.id == "armor_hud") {
                ModuleStateStore.ensureSetting("${module.id}:slot_background", defaultValue = true)
            }
            if (module.id == VisualClientMod.sdfTestModuleId) {
                ModuleStateStore.ensureSetting("${module.id}:outer_glow", defaultValue = true)
            }
        }
        VisualThemeSettings.initializeDefaults()
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
        if (activeTab == VisualMenuTab.THEME) {
            drawThemePreview(context, layout)
        } else {
            drawCards(context, layout, filtered, mouseX.toDouble(), mouseY.toDouble())
        }
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

        if (activeThemeColorKey != null && activeTab == VisualMenuTab.THEME) {
            drawThemeColorPicker(context, layout, mouseX.toDouble(), mouseY.toDouble())
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        if (mouseButtonEvent.button() == 0) {
            val layout = computeLayout()

            if (activeTab == VisualMenuTab.THEME) {
                settingInputRows(layout).firstOrNull { row ->
                    themeColorSwatchRect(row)?.contains(mouseX, mouseY) == true
                }?.let { row ->
                    openThemeColorPicker(row.key)
                    return true
                }
            }

            themePickerLayout(layout)?.let { picker ->
                when {
                    picker.squareRect.contains(mouseX, mouseY) -> {
                        pickerDragMode = PickerDragMode.SATURATION_VALUE
                        updatePickerSaturationValue(mouseX, mouseY, picker)
                        return true
                    }

                    picker.wheelRect.contains(mouseX, mouseY) -> {
                        pickerDragMode = PickerDragMode.HUE_WHEEL
                        updatePickerHue(mouseX, mouseY, picker)
                        return true
                    }

                    picker.bounds.contains(mouseX, mouseY) -> {
                        return true
                    }

                    else -> {
                        closeThemeColorPicker()
                        return true
                    }
                }
            }

            tabLayouts(layout).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { clicked ->
                if (activeTab != clicked.tab) {
                    activeTab = clicked.tab
                    cardScroll = 0
                    settingScroll = 0
                    closeThemeColorPicker()
                    syncSelectedModule(forceSelectFirst = true)
                    rebuildMenuWidgets()
                }
                return true
            }

            settingToggleRows(layout).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { row ->
                ModuleStateStore.setSettingEnabled(row.key, !ModuleStateStore.isSettingEnabled(row.key))
                return true
            }

            cardLayouts(layout, filteredModules()).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { card ->
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
        themePickerLayout(layout)?.let { picker ->
            if (picker.bounds.contains(mouseX, mouseY)) {
                return true
            }
        }
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
            val textRect = inputTextRect(row)
            val input = EditBox(
                font,
                textRect.x + 8,
                textRect.y + 2,
                textRect.width - 16,
                textRect.height - 4,
                Component.empty(),
            )
            input.setBordered(false)
            input.setTextColor(VisualMenuTheme.textPrimary)
            input.setTextColorUneditable(VisualMenuTheme.textDim)
            input.setHint(vText(row.hint))
            input.setValue(settingValueFor(row.key))
            input.setResponder { raw ->
                when (row.key) {
                    VisualThemeSettings.accentColorKey,
                    VisualThemeSettings.neonBorderColorKey,
                    VisualThemeSettings.sliderFillColorKey,
                    VisualThemeSettings.sliderKnobColorKey -> normalizeHexColor(raw)?.let {
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

        if (activeTab != VisualMenuTab.THEME) {
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
        } else {
            val counter = "Theme editor"
            val counterWidth = font.width(vText(counter))
            context.drawString(
                font,
                vText(counter),
                layout.header.x + layout.header.width - counterWidth - 22,
                layout.header.y + 18,
                0xFF7380A1.toInt(),
                false,
            )
        }
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
        if (activeTab == VisualMenuTab.THEME) {
            context.drawString(font, vText("Settings"), layout.settings.x + 16, layout.settings.y + 14, 0xFF7683A5.toInt(), false)
            context.drawString(font, vText("Theme"), layout.settings.x + 16, layout.settings.y + 28, 0xFFF4F6FF.toInt(), false)

            val viewport = settingsViewport(layout)
            context.enableScissor(
                viewport.x,
                viewport.y,
                viewport.x + viewport.width,
                viewport.y + viewport.height,
            )
            settingRows(layout).forEach { row ->
                val rowStyle = inputRowStyle()
                SdfPanelRenderer.draw(
                    context,
                    row.rect.x,
                    row.rect.y,
                    row.rect.width,
                    row.rect.height,
                    rowStyle,
                    SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height),
                )
                context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, 0xFFD8DFF8.toInt(), false)
                if (row is SettingRowLayout.Input) {
                    val textRect = inputTextRect(row)
                    drawRoundedPanel(
                        context,
                        textRect.x,
                        textRect.y,
                        textRect.width,
                        textRect.height,
                        0xCC10182A.toInt(),
                        0x57384866,
                        11,
                    )
                    drawThemeFieldSwatch(context, row)
                }
            }
            context.disableScissor()
            return
        }

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
                    val textRect = inputTextRect(row)
                    drawRoundedPanel(
                        context,
                        textRect.x,
                        textRect.y,
                        textRect.width,
                        textRect.height,
                        0xCC10182A.toInt(),
                        0x57384866,
                        11,
                    )
                    drawThemeFieldSwatch(context, row)
                }
            }
        }
        context.disableScissor()
    }

    private fun drawThemePreview(context: GuiGraphics, layout: ScreenLayout) {
        val viewport = layout.cardsViewport
        context.enableScissor(
            viewport.x,
            viewport.y,
            viewport.x + viewport.width,
            viewport.y + viewport.height,
        )

        val previewX = viewport.x + 16
        val previewY = viewport.y + 16
        val previewWidth = viewport.width - 32
        val previewHeight = viewport.height - 32
        val previewClip = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)

        SdfPanelRenderer.draw(
            context,
            previewX,
            previewY,
            previewWidth,
            previewHeight,
            sectionStyle(),
            previewClip,
        )

        context.drawString(font, vText("Theme Preview"), previewX + 18, previewY + 16, 0xFFF4F6FF.toInt(), false)
        context.drawString(font, vText("Accent, neon and slider colors update live here"), previewX + 18, previewY + 30, 0xFF8290B1.toInt(), false)

        val sampleCardY = previewY + 56
        val sampleCardWidth = ((previewWidth - 52) / 2).coerceAtLeast(160)
        val sampleCardHeight = 74
        val leftCard = IntRect(previewX + 18, sampleCardY, sampleCardWidth, sampleCardHeight)
        val rightCard = IntRect(leftCard.x + leftCard.width + 16, sampleCardY, sampleCardWidth, sampleCardHeight)

        drawThemeSampleCard(context, leftCard, "Selected Card", "Uses accent + neon", enabled = true, selected = true, clipRect = previewClip)
        drawThemeSampleCard(context, rightCard, "Default Card", "Neutral surface", enabled = false, selected = false, clipRect = previewClip)

        val toggleRow = IntRect(previewX + 18, sampleCardY + sampleCardHeight + 18, previewWidth - 36, 36)
        SdfPanelRenderer.draw(context, toggleRow.x, toggleRow.y, toggleRow.width, toggleRow.height, settingRowStyle(hovered = true), previewClip)
        context.drawString(font, vText("Toggle Preview"), toggleRow.x + 14, toggleRow.y + 11, 0xFFD8DFF8.toInt(), false)
        drawToggle(
            context,
            IntRect(toggleRow.x + toggleRow.width - 44, toggleRow.y + 8, 32, 18),
            enabled = true,
        )

        val sliderTrackX = previewX + 30
        val sliderTrackY = toggleRow.y + 58
        val sliderTrackWidth = (previewWidth - 60).coerceAtLeast(140)
        val sliderTrackHeight = 10
        drawThemeSlider(context, sliderTrackX, sliderTrackY, sliderTrackWidth, sliderTrackHeight, 0.68f, previewClip)
        context.drawString(font, vText("Slider Preview"), sliderTrackX, sliderTrackY - 14, 0xFFD8DFF8.toInt(), false)

        context.disableScissor()
    }

    private fun drawThemeSampleCard(
        context: GuiGraphics,
        rect: IntRect,
        title: String,
        subtitle: String,
        enabled: Boolean,
        selected: Boolean,
        clipRect: SdfPanelRenderer.ClipRect,
    ) {
        val backdrop = IntRect(rect.x - 6, rect.y - 6, rect.width + 12, rect.height + 12)
        SdfPanelRenderer.draw(context, backdrop.x, backdrop.y, backdrop.width, backdrop.height, cardBackdropStyle(enabled, selected, hovered = false), clipRect)
        SdfPanelRenderer.draw(context, rect.x, rect.y, rect.width, rect.height, cardSurfaceStyle(enabled, selected, hovered = false), clipRect)
        drawRoundedPanel(context, rect.x + 14, rect.y + 14, 26, 26, 0xA2141E33.toInt(), 0x5B3E4A69, 10)
        context.drawString(font, vText(if (selected) "T" else "N"), rect.x + 23, rect.y + 22, 0xFFD6DDF7.toInt(), false)
        context.drawString(font, fitStyledText(font, title, rect.width - 116), rect.x + 50, rect.y + 16, 0xFFF4F6FF.toInt(), false)
        context.drawString(font, fitStyledText(font, subtitle, rect.width - 116), rect.x + 50, rect.y + 31, 0xFF7E8BAE.toInt(), false)
        drawToggle(context, IntRect(rect.x + rect.width - 48, rect.y + 28, 34, 18), enabled)
    }

    private fun drawThemeSlider(
        context: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float,
        clipRect: SdfPanelRenderer.ClipRect,
    ) {
        SdfPanelRenderer.draw(
            context,
            x,
            y,
            width,
            height,
            SdfPanelStyle(
                baseColor = 0xB71A2335.toInt(),
                borderColor = blendColor(0xFF3B4B67.toInt(), accentStrongColor(), 0.25f),
                borderWidthPx = 1.0f,
                radiusPx = (height / 2f),
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.02f, opacity = 0.02f),
                outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 8f, strength = 0.08f, opacity = 0.04f),
                shade = SdfShadeStyle(0x08FFFFFF, 0x0E000000),
                neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x54), widthPx = 0.8f, softnessPx = 3.5f, strength = 0.24f),
            ),
            clipRect,
        )

        val filledWidth = ((width - 2) * progress.coerceIn(0f, 1f)).toInt().coerceAtLeast(6)
        SdfPanelRenderer.draw(
            context,
            x + 1,
            y + 1,
            filledWidth,
            (height - 2).coerceAtLeast(1),
            SdfPanelStyle(
                baseColor = sliderFillColor(),
                borderColor = blendColor(sliderFillColor(), 0xFFFFFFFF.toInt(), 0.18f),
                borderWidthPx = 0.8f,
                radiusPx = ((height - 2) / 2f),
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.05f, opacity = 0.04f),
                outerGlow = SdfGlowStyle(sliderFillColor(), radiusPx = 8f, strength = 0.14f, opacity = 0.08f),
                shade = SdfShadeStyle(0x10FFFFFF, 0x0A000000),
                neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0xD5), widthPx = 0.9f, softnessPx = 4f, strength = 0.54f),
            ),
            clipRect,
        )

        val knobSize = 12
        val knobX = (x + (width * progress.coerceIn(0f, 1f))).toInt().coerceIn(x + knobSize / 2, x + width - knobSize / 2) - (knobSize / 2)
        val knobY = y + (height - knobSize) / 2
        SdfPanelRenderer.draw(
            context,
            knobX,
            knobY,
            knobSize,
            knobSize,
            SdfPanelStyle(
                baseColor = sliderKnobColor(),
                borderColor = blendColor(sliderKnobColor(), accentStrongColor(), 0.32f),
                borderWidthPx = 1.0f,
                radiusPx = 6f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.06f, opacity = 0.06f),
                outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 8f, strength = 0.16f, opacity = 0.12f),
                shade = SdfShadeStyle(0x0EFFFFFF, 0x08000000),
                neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0xC8), widthPx = 0.9f, softnessPx = 4f, strength = 0.58f),
            ),
            clipRect,
        )
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

        if (activeTab == VisualMenuTab.THEME) {
            addInput(VisualThemeSettings.accentColorKey, "Accent Glow", "#RRGGBB")
            addInput(VisualThemeSettings.neonBorderColorKey, "Neon Border", "#RRGGBB")
            addInput(VisualThemeSettings.sliderFillColorKey, "Slider Fill", "#RRGGBB")
            addInput(VisualThemeSettings.sliderKnobColorKey, "Slider Knob", "#RRGGBB")
            return rows
        }

        val selectedModule = modules.firstOrNull { it.id == selectedModuleId } ?: return emptyList()

        addToggle("${selectedModule.id}:visible_hud", "Visible In HUD")
        addToggle("${selectedModule.id}:accent_sync", "Accent Sync")
        if (selectedModule.id == "armor_hud") {
            addToggle("${selectedModule.id}:slot_background", "Slot Background")
        }

        if (selectedModule.id == VisualClientMod.sdfTestModuleId) {
            addToggle("${selectedModule.id}:outer_glow", "Outer Glow")
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
        if (activeTab == VisualMenuTab.THEME) {
            selectedModuleId = null
            return
        }
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
            val searchVisible = activeTab != VisualMenuTab.THEME
            searchBox.setVisible(searchVisible)
            searchBox.active = searchVisible
        }

        val viewport = settingsViewport(layout)
        val inputRows = settingInputRows(layout).associateBy { it.key }
        settingInputs.forEach { (key, input) ->
            val row = inputRows[key]
            if (row == null) {
                input.setVisible(false)
                input.active = false
            } else {
                val textRect = inputTextRect(row)
                input.setX(textRect.x + 8)
                input.setY(textRect.y + 2)
                input.setWidth(textRect.width - 16)
                input.setHeight(textRect.height - 4)

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
            VisualThemeSettings.accentColorKey -> ModuleStateStore.getTextSetting(key, "#8A71FF")
            VisualThemeSettings.neonBorderColorKey -> ModuleStateStore.getTextSetting(key, "#8A71FF")
            VisualThemeSettings.sliderFillColorKey -> ModuleStateStore.getTextSetting(key, "#8A71FF")
            VisualThemeSettings.sliderKnobColorKey -> ModuleStateStore.getTextSetting(key, "#F0F2FF")
            else -> ""
        }
    }

    private fun isThemeColorKey(key: String): Boolean {
        return key == VisualThemeSettings.accentColorKey ||
            key == VisualThemeSettings.neonBorderColorKey ||
            key == VisualThemeSettings.sliderFillColorKey ||
            key == VisualThemeSettings.sliderKnobColorKey
    }

    private fun themeColorSwatchRect(row: SettingRowLayout.Input): IntRect? {
        if (!isThemeColorKey(row.key)) return null
        return IntRect(row.fieldRect.x + 6, row.fieldRect.y + 2, 18, row.fieldRect.height - 4)
    }

    private fun inputTextRect(row: SettingRowLayout.Input): IntRect {
        val swatch = themeColorSwatchRect(row) ?: return row.fieldRect
        return IntRect(
            swatch.x + swatch.width + 8,
            row.fieldRect.y,
            (row.fieldRect.x + row.fieldRect.width) - (swatch.x + swatch.width + 8),
            row.fieldRect.height,
        )
    }

    private fun drawThemeFieldSwatch(context: GuiGraphics, row: SettingRowLayout.Input) {
        val swatch = themeColorSwatchRect(row) ?: return
        val color = currentThemeColorForKey(row.key)
        val border = if (activeThemeColorKey == row.key) accentStrongColor() else blendColor(0xFF3B4D6D.toInt(), color, 0.35f)
        drawRoundedPanel(context, swatch.x, swatch.y, swatch.width, swatch.height, 0xDD0F1727.toInt(), border, 7)
        fillRoundedRect(context, swatch.x + 3, swatch.y + 3, swatch.width - 6, swatch.height - 6, 4, color)
    }

    private fun themePickerLayout(layout: ScreenLayout): ThemePickerLayout? {
        if (activeThemeColorKey == null || activeTab != VisualMenuTab.THEME) return null
        val bounds = IntRect(
            layout.panel.x + (layout.panel.width - 252) / 2,
            layout.panel.y + (layout.panel.height - 212) / 2,
            252,
            212,
        )
        val wheelRect = IntRect(bounds.x + 18, bounds.y + 44, 120, 120)
        val squareRect = IntRect(bounds.x + 48, bounds.y + 74, 60, 60)
        val previewRect = IntRect(bounds.x + 156, bounds.y + 58, 72, 72)
        return ThemePickerLayout(bounds, wheelRect, squareRect, previewRect)
    }

    private fun openThemeColorPicker(key: String) {
        activeThemeColorKey = key
        pickerDragMode = null
        val current = currentThemeColorForKey(key)
        val hsb = FloatArray(3)
        Color.RGBtoHSB((current shr 16) and 0xFF, (current shr 8) and 0xFF, current and 0xFF, hsb)
        pickerHue = hsb[0]
        pickerSaturation = hsb[1]
        pickerValue = hsb[2]
        pickerSquareHue = -1f
    }

    private fun closeThemeColorPicker() {
        activeThemeColorKey = null
        pickerDragMode = null
    }

    private fun updatePickerHue(mouseX: Double, mouseY: Double, layout: ThemePickerLayout) {
        val centerX = layout.wheelRect.x + (layout.wheelRect.width / 2f)
        val centerY = layout.wheelRect.y + (layout.wheelRect.height / 2f)
        val dx = (mouseX - centerX).toFloat()
        val dy = (mouseY - centerY).toFloat()
        val angle = kotlin.math.atan2(dy, dx)
        pickerHue = ((angle / (kotlin.math.PI.toFloat() * 2f)) + 1f).mod(1f)
        applyPickerColor()
    }

    private fun updatePickerSaturationValue(mouseX: Double, mouseY: Double, layout: ThemePickerLayout) {
        val localX = ((mouseX - layout.squareRect.x) / layout.squareRect.width.toDouble()).toFloat().coerceIn(0f, 1f)
        val localY = ((mouseY - layout.squareRect.y) / layout.squareRect.height.toDouble()).toFloat().coerceIn(0f, 1f)
        pickerSaturation = localX
        pickerValue = 1f - localY
        applyPickerColor()
    }

    private fun applyPickerColor() {
        val key = activeThemeColorKey ?: return
        val rgb = Color.HSBtoRGB(pickerHue.coerceIn(0f, 1f), pickerSaturation.coerceIn(0f, 1f), pickerValue.coerceIn(0f, 1f))
        val normalized = "#%06X".format(java.util.Locale.US, rgb and 0x00FFFFFF)
        ModuleStateStore.setTextSetting(key, normalized)
        settingInputs[key]?.setValue(normalized)
    }

    private fun currentThemeColorForKey(key: String): Int {
        return when (key) {
            VisualThemeSettings.accentColorKey -> accentStrongColor()
            VisualThemeSettings.neonBorderColorKey -> neonBorderColor()
            VisualThemeSettings.sliderFillColorKey -> sliderFillColor()
            VisualThemeSettings.sliderKnobColorKey -> sliderKnobColor()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun drawThemeColorPicker(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        val picker = themePickerLayout(layout) ?: return
        ensureThemePickerTextures()

        context.fill(
            layout.panel.x,
            layout.panel.y,
            layout.panel.x + layout.panel.width,
            layout.panel.y + layout.panel.height,
            0x66000000,
        )
        SdfPanelRenderer.draw(context, picker.bounds.x, picker.bounds.y, picker.bounds.width, picker.bounds.height, settingsStyle())

        val label = when (activeThemeColorKey) {
            VisualThemeSettings.accentColorKey -> "Accent Glow"
            VisualThemeSettings.neonBorderColorKey -> "Neon Border"
            VisualThemeSettings.sliderFillColorKey -> "Slider Fill"
            VisualThemeSettings.sliderKnobColorKey -> "Slider Knob"
            else -> "Theme Color"
        }
        context.drawString(font, vText(label), picker.bounds.x + 18, picker.bounds.y + 16, 0xFFF4F6FF.toInt(), false)
        context.drawString(font, vText("Click the ring for hue and the square for tone"), picker.bounds.x + 18, picker.bounds.y + 30, 0xFF7F8CB0.toInt(), false)

        blitTexture(context, hueWheelTextureId, picker.wheelRect)
        blitTexture(context, saturationValueTextureId, picker.squareRect)

        val centerX = picker.wheelRect.x + (picker.wheelRect.width / 2f)
        val centerY = picker.wheelRect.y + (picker.wheelRect.height / 2f)
        val indicatorRadius = 45f
        val angle = pickerHue * (kotlin.math.PI.toFloat() * 2f)
        val hueIndicatorX = (centerX + kotlin.math.cos(angle) * indicatorRadius).roundToInt()
        val hueIndicatorY = (centerY + kotlin.math.sin(angle) * indicatorRadius).roundToInt()
        drawRoundedPanel(context, hueIndicatorX - 5, hueIndicatorY - 5, 10, 10, 0xFFF2F4FF.toInt(), accentStrongColor(), 5)

        val squareIndicatorX = (picker.squareRect.x + (picker.squareRect.width * pickerSaturation)).roundToInt().coerceIn(picker.squareRect.x + 4, picker.squareRect.x + picker.squareRect.width - 4)
        val squareIndicatorY = (picker.squareRect.y + (picker.squareRect.height * (1f - pickerValue))).roundToInt().coerceIn(picker.squareRect.y + 4, picker.squareRect.y + picker.squareRect.height - 4)
        drawRoundedPanel(context, squareIndicatorX - 4, squareIndicatorY - 4, 8, 8, 0xFFF2F4FF.toInt(), 0xFF111827.toInt(), 4)

        val currentColor = currentThemeColorForKey(activeThemeColorKey ?: return)
        SdfPanelRenderer.draw(
            context,
            picker.previewRect.x,
            picker.previewRect.y,
            picker.previewRect.width,
            picker.previewRect.height,
            SdfPanelStyle(
                baseColor = 0xFF111A2D.toInt(),
                borderColor = blendColor(0xFF33415F.toInt(), currentColor, 0.30f),
                borderWidthPx = 1f,
                radiusPx = 16f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
                outerGlow = SdfGlowStyle(currentColor, radiusPx = 16f, strength = 0.12f, opacity = 0.08f),
                shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
                neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x84), widthPx = 0.9f, softnessPx = 4f, strength = 0.34f),
            ),
        )
        fillRoundedRect(context, picker.previewRect.x + 12, picker.previewRect.y + 12, picker.previewRect.width - 24, picker.previewRect.height - 24, 14, currentColor)
        context.drawString(font, vText(settingValueFor(activeThemeColorKey ?: "")), picker.previewRect.x - 2, picker.previewRect.y + picker.previewRect.height + 12, 0xFFD8DFF8.toInt(), false)

        val closeHovered = picker.bounds.contains(mouseX, mouseY).not()
        context.drawString(font, vText(if (closeHovered) "Click outside to close" else "Release mouse to finish"), picker.bounds.x + 18, picker.bounds.y + picker.bounds.height - 18, 0xFF7A86A6.toInt(), false)
    }

    private fun ensureThemePickerTextures() {
        val client = Minecraft.getInstance()
        if (!hueWheelTextureReady) {
            client.textureManager.register(
                hueWheelTextureId,
                DynamicTexture({ "visualclient-theme-picker-wheel" }, createHueWheelImage(120)),
            )
            hueWheelTextureReady = true
        }

        if (kotlin.math.abs(pickerSquareHue - pickerHue) > 0.001f) {
            client.textureManager.register(
                saturationValueTextureId,
                DynamicTexture({ "visualclient-theme-picker-square" }, createSaturationValueImage(60, pickerHue)),
            )
            pickerSquareHue = pickerHue
        }
    }

    private fun createHueWheelImage(size: Int): NativeImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val center = (size - 1) / 2f
        val outerRadius = (size / 2f) - 3f
        val innerRadius = outerRadius - 18f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = sqrt((dx * dx) + (dy * dy))
                if (distance < innerRadius || distance > outerRadius) {
                    image.setRGB(x, y, 0x00000000)
                } else {
                    val hue = ((kotlin.math.atan2(dy, dx) / (kotlin.math.PI.toFloat() * 2f)) + 1f).mod(1f)
                    val color = Color.HSBtoRGB(hue, 1f, 1f) or (0xFF shl 24)
                    image.setRGB(x, y, color)
                }
            }
        }
        return nativeImageFromBuffered(image)
    }

    private fun createSaturationValueImage(size: Int, hue: Float): NativeImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until size) {
            val value = 1f - (y / (size - 1).toFloat())
            for (x in 0 until size) {
                val saturation = x / (size - 1).toFloat()
                val color = Color.HSBtoRGB(hue, saturation, value) or (0xFF shl 24)
                image.setRGB(x, y, color)
            }
        }
        return nativeImageFromBuffered(image)
    }

    private fun nativeImageFromBuffered(image: BufferedImage): NativeImage {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return ByteArrayInputStream(output.toByteArray()).use { input ->
            NativeImage.read(input)
        }
    }

    private fun blitTexture(context: GuiGraphics, texture: Identifier, rect: IntRect) {
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            rect.x,
            rect.y,
            0f,
            0f,
            rect.width,
            rect.height,
            rect.width,
            rect.height,
            rect.width,
            rect.height,
            0xFFFFFFFF.toInt(),
        )
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

    private fun accentStrongColor(): Int = VisualThemeSettings.accentStrong()

    private fun accentColor(): Int = VisualThemeSettings.accent()

    private fun neonBorderColor(): Int = VisualThemeSettings.neonBorder()

    private fun sliderFillColor(): Int = VisualThemeSettings.sliderFill()

    private fun sliderKnobColor(): Int = VisualThemeSettings.sliderKnob()

    private fun shellStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xFB0B111B.toInt(),
            borderColor = 0xBE334362.toInt(),
            borderWidthPx = 1.5f,
            radiusPx = 30f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 20f, strength = 0.08f, opacity = 0.06f),
            outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 44f, strength = 0.22f, opacity = 0.14f),
            shade = SdfShadeStyle(0x1AFFFFFF, 0x22000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0xB2), widthPx = 1.25f, softnessPx = 10f, strength = 0.90f),
        )
    }

    private fun sidebarStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF50E1728.toInt(),
            borderColor = 0xA13A4A69.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 16f, strength = 0.04f, opacity = 0.05f),
            outerGlow = SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.16f, opacity = 0.24f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x12000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x5E), widthPx = 0.95f, softnessPx = 5f, strength = 0.42f),
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
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x4A), widthPx = 0.9f, softnessPx = 4.5f, strength = 0.34f),
        )
    }

    private fun listStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF70C1323.toInt(),
            borderColor = 0x96354563.toInt(),
            borderWidthPx = 1f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(accentColor(), radiusPx = 18f, strength = 0.10f, opacity = 0.06f),
            shade = SdfShadeStyle(0x0BFFFFFF, 0x12000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x3E), widthPx = 0.9f, softnessPx = 4f, strength = 0.28f),
        )
    }

    private fun settingsStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = 0xF70D1627.toInt(),
            borderColor = 0xA13A4A69.toInt(),
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 12f, strength = 0.05f, opacity = 0.04f),
            outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 24f, strength = 0.14f, opacity = 0.08f),
            shade = SdfShadeStyle(0x10FFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x66), widthPx = 1.0f, softnessPx = 5f, strength = 0.42f),
        )
    }

    private fun tabStyle(selected: Boolean, hovered: Boolean): SdfPanelStyle {
        val border = when {
            selected -> accentStrongColor()
            hovered -> blendColor(0xFF536691.toInt(), accentStrongColor(), 0.35f)
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
                color = if (selected) accentStrongColor() else 0xFFFFFFFF.toInt(),
                radiusPx = 12f,
                strength = if (selected) 0.10f else 0.03f,
                opacity = if (selected) 0.08f else 0.02f,
            ),
            outerGlow = SdfGlowStyle(
                color = if (selected) accentStrongColor() else 0xFF000000.toInt(),
                radiusPx = 18f,
                strength = if (selected) 0.22f else 0.12f,
                opacity = glowOpacity,
            ),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(
                color = if (selected) VisualThemeSettings.withAlpha(neonBorderColor(), 0xC0) else if (hovered) VisualThemeSettings.withAlpha(neonBorderColor(), 0x54) else 0x00000000,
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
                color = if (enabled || selected) accentStrongColor() else 0xFF324869.toInt(),
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
            selected -> accentStrongColor()
            enabled -> blendColor(0xFF536B9E.toInt(), accentStrongColor(), 0.45f)
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
                    color = accentStrongColor(),
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
                color = if (selected) accentStrongColor() else 0xFF000000.toInt(),
                radiusPx = 14f,
                strength = 0.10f,
                opacity = if (selected) 0.06f else 0.02f,
            ),
            shade = SdfShadeStyle(0x12FFFFFF, 0x16000000),
            neonBorder = SdfNeonBorderStyle(
                color = when {
                    selected -> VisualThemeSettings.withAlpha(neonBorderColor(), 0xCC)
                    enabled -> VisualThemeSettings.withAlpha(neonBorderColor(), 0x8C)
                    hovered -> VisualThemeSettings.withAlpha(neonBorderColor(), 0x44)
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
            borderColor = if (hovered) blendColor(0xFF4B5D85.toInt(), accentStrongColor(), 0.25f) else 0xFF33415F.toInt(),
            borderWidthPx = 1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 18f, strength = 0.12f, opacity = if (hovered) 0.07f else 0.04f),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(
                color = if (hovered) VisualThemeSettings.withAlpha(neonBorderColor(), 0x88) else VisualThemeSettings.withAlpha(neonBorderColor(), 0x30),
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
            outerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 18f, strength = 0.10f, opacity = 0.05f),
            shade = SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = SdfNeonBorderStyle(VisualThemeSettings.withAlpha(neonBorderColor(), 0x42), widthPx = 0.9f, softnessPx = 4.5f, strength = 0.30f),
        )
    }

    private fun drawToggle(context: GuiGraphics, rect: IntRect, enabled: Boolean) {
        val trackFill = if (enabled) VisualThemeSettings.withAlpha(sliderFillColor(), 0xCC) else 0xAA1A2436.toInt()
        val trackBorder = if (enabled) accentStrongColor() else 0x7A3E4C68
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
                VisualThemeSettings.withAlpha(accentStrongColor(), 0x6A),
            )
        }
        drawRoundedPanel(
            context,
            knobCenterX - knobRadius,
            knobCenterY - knobRadius,
            knobRadius * 2,
            knobRadius * 2,
            sliderKnobColor(),
            blendColor(sliderKnobColor(), accentStrongColor(), 0.28f),
            knobRadius,
        )
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val layout = computeLayout()
        val picker = themePickerLayout(layout)
        if (mouseButtonEvent.button() == 0 && picker != null) {
            when (pickerDragMode) {
                PickerDragMode.HUE_WHEEL -> {
                    updatePickerHue(mouseButtonEvent.x(), mouseButtonEvent.y(), picker)
                    return true
                }

                PickerDragMode.SATURATION_VALUE -> {
                    updatePickerSaturationValue(mouseButtonEvent.x(), mouseButtonEvent.y(), picker)
                    return true
                }

                null -> Unit
            }
        }
        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0 && pickerDragMode != null) {
            pickerDragMode = null
            return true
        }
        return super.mouseReleased(mouseButtonEvent)
    }
}
