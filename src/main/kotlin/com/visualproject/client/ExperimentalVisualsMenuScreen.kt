package com.visualproject.client

import com.mojang.blaze3d.platform.NativeImage
import com.visualproject.client.audio.CustomSoundRegistry
import com.visualproject.client.hud.armor.ArmorHudModule
import com.visualproject.client.hud.btc.BtcHudModule
import com.visualproject.client.hud.inv.InvHudModule
import com.visualproject.client.hud.itembar.ItemBarHudModule
import com.visualproject.client.hud.music.MusicHudModule
import com.visualproject.client.hud.target.TargetHudModule
import com.visualproject.client.hud.watermark.WatermarkHudModule
import com.visualproject.client.notifications.NotificationsSettings
import com.visualproject.client.render.sdf.BackdropBlurRenderer
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
import com.visualproject.client.texture.NonDumpableDynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
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

    private data class ChoiceOptionLayout(
        val value: String,
        val label: String,
        val rect: IntRect,
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

        class Slider(
            key: String,
            label: String,
            rect: IntRect,
            val trackRect: IntRect,
            val min: Float,
            val max: Float,
        ) : SettingRowLayout(key, label, rect)

        class Choice(
            key: String,
            label: String,
            rect: IntRect,
            val options: List<ChoiceOptionLayout>,
        ) : SettingRowLayout(key, label, rect)

        class Section(
            label: String,
            rect: IntRect,
        ) : SettingRowLayout(label, label, rect)
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
        VisualMenuModuleEntry(BtcHudModule.moduleId, "BTC", "B", VisualMenuTab.HUD),
        VisualMenuModuleEntry("cooldowns_hud", "Cooldowns HUD", "C", VisualMenuTab.HUD),
        VisualMenuModuleEntry("effect_notify", "Notifications", "E", VisualMenuTab.HUD),
        VisualMenuModuleEntry("gif_hud", "GIF", "G", VisualMenuTab.HUD),
        VisualMenuModuleEntry(InvHudModule.moduleId, "INV", "I", VisualMenuTab.HUD),
        VisualMenuModuleEntry("item_bar_hud", "Item Bar", "I", VisualMenuTab.HUD),
        VisualMenuModuleEntry(MusicHudModule.moduleId, "Music", "M", VisualMenuTab.HUD),
        VisualMenuModuleEntry("potions", "Potions", "P", VisualMenuTab.HUD),
        VisualMenuModuleEntry(VisualClientMod.sdfTestModuleId, "SDF Test HUD", "Q", VisualMenuTab.HUD),
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
    private var activeSliderKey: String? = null
    private var pickerHue = 0f
    private var pickerSaturation = 1f
    private var pickerValue = 1f
    private var pickerSquareHue = -1f
    private var hueWheelTextureReady = false

    private lateinit var searchBox: EditBox
    private val settingInputs = LinkedHashMap<String, EditBox>()
    private val toggleProgressByKey = HashMap<String, Float>()
    private val hueWheelTextureId = Identifier.fromNamespaceAndPath("visualclient", "theme_picker_wheel")
    private val saturationValueTextureId = Identifier.fromNamespaceAndPath("visualclient", "theme_picker_square")

    init {
        modules.forEach { module ->
            ModuleStateStore.ensureModule(module.id, defaultEnabled = false)
            ModuleStateStore.ensureSetting("${module.id}:visible_hud", defaultValue = false)
            ModuleStateStore.ensureSetting("${module.id}:accent_sync", defaultValue = true)
            if (module.tab == VisualMenuTab.HUD) {
                val defaultSize = if (module.id == "gif_hud") {
                    ModuleStateStore.getNumberSetting("${module.id}:scale", 1.0f)
                } else {
                    1.0f
                }
                ModuleStateStore.ensureNumberSetting("${module.id}:size", defaultSize)
            }
            if (module.id == "armor_hud") {
                ModuleStateStore.ensureSetting("${module.id}:slot_background", defaultValue = true)
            }
            if (module.id == "watermark" || module.id == MusicHudModule.moduleId) {
                ModuleStateStore.ensureSetting("${module.id}:music_scan", defaultValue = true)
            }
            if (module.id == "item_bar_hud") {
                ModuleStateStore.ensureSetting("${module.id}:hide_vanilla_hotbar", defaultValue = true)
            }
            if (module.id == "gif_hud") {
                ModuleStateStore.ensureSetting("${module.id}:chroma_key_enabled", defaultValue = true)
                ModuleStateStore.ensureSetting("${module.id}:invert_colors", defaultValue = false)
                ModuleStateStore.ensureTextSetting("${module.id}:file_name", "")
                ModuleStateStore.ensureTextSetting("${module.id}:chroma_key_color", "#00FF00")
                ModuleStateStore.ensureNumberSetting("${module.id}:chroma_key_strength", 0.18f)
                ModuleStateStore.ensureNumberSetting("${module.id}:scale", 1.0f)
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
        BackdropBlurRenderer.captureBackdrop()
        if (isTransparentMenuTheme()) {
            context.fill(0, 0, width, height, 0x2A090B10)
        }

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

        if (activeThemeColorKey != null) {
            drawThemeColorPicker(context, layout, mouseX.toDouble(), mouseY.toDouble())
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        if (mouseButtonEvent.button() == 0) {
            val layout = computeLayout()

            settingInputRows(layout).firstOrNull { row ->
                themeColorSwatchRect(row)?.contains(mouseX, mouseY) == true
            }?.let { row ->
                openThemeColorPicker(row.key)
                return true
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

            settingChoiceRows(layout).firstNotNullOfOrNull { row ->
                row.options.firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { option -> row to option }
            }?.let { (row, option) ->
                ModuleStateStore.setTextSetting(row.key, option.value)
                normalizeNotificationChoiceState(row.key)
                if (choiceAffectsLayout(row.key)) {
                    rebuildMenuWidgets()
                }
                return true
            }

            settingSliderRows(layout).firstOrNull { it.rect.contains(mouseX, mouseY) }?.let { row ->
                activeSliderKey = row.key
                updateSliderValue(row, mouseX)
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
        val searchRect = searchInputRect(layout)
        searchBox = EditBox(font, searchRect.x, searchRect.y, searchRect.width, searchRect.height, Component.empty())
        searchBox.setBordered(false)
        searchBox.setTextColor(menuTextPrimaryColor())
        searchBox.setTextColorUneditable(menuTextDimColor())
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
            input.setTextColor(menuTextPrimaryColor())
            input.setTextColorUneditable(menuTextDimColor())
            input.setHint(vText(row.hint))
            input.setValue(settingValueFor(row.key))
            input.setResponder { raw ->
                when (row.key) {
                    VisualThemeSettings.accentColorKey,
                    VisualThemeSettings.neonBorderColorKey,
                    VisualThemeSettings.toggleFillColorKey,
                    VisualThemeSettings.sliderFillColorKey,
                    VisualThemeSettings.sliderKnobColorKey,
                    "gif_hud:chroma_key_color" -> normalizeHexColor(raw)?.let {
                        ModuleStateStore.setTextSetting(row.key, it)
                    }

                    "gif_hud:file_name" -> ModuleStateStore.setTextSetting(row.key, sanitizeFileName(raw))
                    NotificationsSettings.moduleEnableSoundFileKey,
                    NotificationsSettings.moduleDisableSoundFileKey,
                    NotificationsSettings.stage1SoundFileKey,
                    NotificationsSettings.stage2SoundFileKey,
                    NotificationsSettings.armorStage1SoundFileKey,
                    NotificationsSettings.armorStage2SoundFileKey,
                    NotificationsSettings.hitSoundFileKey,
                    NotificationsSettings.critSoundFileKey -> ModuleStateStore.setTextSetting(row.key, sanitizeSoundFileName(raw))
                    else -> ModuleStateStore.setTextSetting(row.key, raw.trim())
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
        context.drawString(font, vBrandText("Visual Client"), layout.header.x + 18, layout.header.y + 14, menuTextPrimaryColor(), false)
        context.drawString(font, vText("SDF prototype"), layout.header.x + 18, layout.header.y + 28, menuTextDimColor(), false)

        if (activeTab != VisualMenuTab.THEME) {
            drawRoundedPanel(
                context,
                layout.search.x,
                layout.search.y,
                layout.search.width,
                layout.search.height,
                menuSearchFillColor(),
                menuSearchBorderColor(),
                14,
            )

            val counter = "${filteredModules().size} modules"
            val counterWidth = font.width(vText(counter))
            context.drawString(
                font,
                vText(counter),
                layout.search.x - counterWidth - 12,
                layout.search.y + 9,
                menuTextDimColor(),
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
                menuTextDimColor(),
                false,
            )
        }
    }

    private fun drawTabs(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        context.drawString(font, vText("Menu"), layout.sidebar.x + 16, layout.sidebar.y + 16, menuTextDimColor(), false)
        context.drawString(font, vText("Visual Client"), layout.sidebar.x + 16, layout.sidebar.y + 30, menuTextPrimaryColor(), false)

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
                if (selected) menuTextPrimaryColor() else menuTextSecondaryColor(),
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

            drawRoundedPanel(context, card.rect.x + 12, card.rect.y + 12, 26, 26, menuIconSlotFillColor(), menuIconSlotBorderColor(), 10)
            context.drawString(font, vText(card.module.iconGlyph), card.rect.x + 21, card.rect.y + 20, menuTextSecondaryColor(), false)

            val title = fitStyledText(font, card.module.title, card.rect.width - 88)
            context.drawString(font, title, card.rect.x + 48, card.rect.y + 16, menuTextPrimaryColor(), false)
            context.drawString(
                font,
                vText(if (enabled) "Enabled" else "Disabled"),
                card.rect.x + 48,
                card.rect.y + 31,
                if (enabled) accentStrongColor() else menuTextDimColor(),
                false,
            )

            drawToggle(context, card.toggleRect, enabled, "module:${card.module.id}", clipRect)
        }

        context.disableScissor()
    }

    private fun drawSettings(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        if (activeTab == VisualMenuTab.THEME) {
            context.drawString(font, vText("Settings"), layout.settings.x + 16, layout.settings.y + 14, menuTextDimColor(), false)
            context.drawString(font, vText("Theme"), layout.settings.x + 16, layout.settings.y + 28, menuTextPrimaryColor(), false)

            val viewport = settingsViewport(layout)
            context.enableScissor(
                viewport.x,
                viewport.y,
                viewport.x + viewport.width,
                viewport.y + viewport.height,
            )
            settingRows(layout).forEach { row ->
                when (row) {
                    is SettingRowLayout.Section -> {
                        context.drawString(font, vText(row.label), row.rect.x, row.rect.y + 2, menuTextMutedColor(), false)
                    }

                    is SettingRowLayout.Toggle -> {
                        val hovered = row.rect.contains(mouseX, mouseY)
                        val enabled = ModuleStateStore.isSettingEnabled(row.key)
                        val rowStyle = settingRowStyle(hovered)
                        SdfPanelRenderer.draw(
                            context,
                            row.rect.x,
                            row.rect.y,
                            row.rect.width,
                            row.rect.height,
                            rowStyle,
                            SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height),
                        )
                        context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 11, menuTextPrimaryColor(), false)
                        drawToggle(
                            context,
                            row.switchRect,
                            enabled,
                            "setting:${row.key}",
                            SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height),
                        )
                    }

                    is SettingRowLayout.Input -> {
                        val isThemeRow = isThemeColorKey(row.key)
                        val rowStyle = inputRowStyle(
                            themeColorRow = isThemeRow,
                            themeColor = if (isThemeRow) currentThemeColorForKey(row.key) else null,
                        )
                        SdfPanelRenderer.draw(
                            context,
                            row.rect.x,
                            row.rect.y,
                            row.rect.width,
                            row.rect.height,
                            rowStyle,
                            SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height),
                        )
                        context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, menuTextPrimaryColor(), false)
                        val textRect = inputTextRect(row)
                        drawRoundedPanel(
                            context,
                            textRect.x,
                            textRect.y,
                            textRect.width,
                            textRect.height,
                            menuFieldFillColor(),
                            menuFieldBorderColor(),
                            11,
                        )
                        drawThemeFieldSwatch(context, row)
                    }

                    is SettingRowLayout.Choice -> {
                        val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                        SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, inputRowStyle(), clipRect)
                        context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, menuTextPrimaryColor(), false)
                        val selectedValue = choiceValueFor(row.key, row.options.firstOrNull()?.value.orEmpty())
                        row.options.forEach { option ->
                            val hovered = option.rect.contains(mouseX, mouseY)
                            val selected = selectedValue == option.value
                            val fill = choiceChipFill(selected, hovered)
                            val border = choiceChipBorder(selected, hovered)
                            drawRoundedPanel(context, option.rect.x, option.rect.y, option.rect.width, option.rect.height, fill, border, 9)
                            val text = vText(option.label)
                            val textWidth = font.width(text)
                            context.drawString(
                                font,
                                text,
                                option.rect.x + ((option.rect.width - textWidth) / 2),
                                option.rect.y + 5,
                                if (selected) menuTextPrimaryColor() else menuTextSecondaryColor(),
                                false,
                            )
                        }
                    }

                    else -> {
                    }
                }
            }
            context.disableScissor()
            return
        }

        val selectedModule = modules.firstOrNull { it.id == selectedModuleId }
        if (selectedModule == null) {
            context.drawString(font, vText("No module selected"), layout.settings.x + 16, layout.settings.y + 16, menuTextDimColor(), false)
            return
        }

        context.drawString(font, vText("Settings"), layout.settings.x + 16, layout.settings.y + 14, menuTextDimColor(), false)
        context.drawString(font, fitStyledText(font, selectedModule.title, layout.settings.width - 32), layout.settings.x + 16, layout.settings.y + 28, menuTextPrimaryColor(), false)

        val viewport = settingsViewport(layout)
        context.enableScissor(
            viewport.x,
            viewport.y,
            viewport.x + viewport.width,
            viewport.y + viewport.height,
        )
        settingRows(layout).forEach { row ->
            when (row) {
                is SettingRowLayout.Section -> {
                    context.drawString(font, vText(row.label), row.rect.x, row.rect.y + 2, menuTextMutedColor(), false)
                }

                is SettingRowLayout.Toggle -> {
                    val hovered = row.rect.contains(mouseX, mouseY)
                    val enabled = ModuleStateStore.isSettingEnabled(row.key)
                    val rowStyle = settingRowStyle(hovered)
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, rowStyle, clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 11, menuTextPrimaryColor(), false)
                    drawToggle(
                        context,
                        row.switchRect,
                        enabled,
                        "setting:${row.key}",
                        clipRect,
                    )
                }

                is SettingRowLayout.Input -> {
                    val invalid = isMissingSoundSetting(row.key)
                    val isThemeRow = isThemeColorKey(row.key)
                    val rowStyle = inputRowStyle(
                        invalid,
                        themeColorRow = isThemeRow,
                        themeColor = if (isThemeRow) currentThemeColorForKey(row.key) else null,
                    )
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, rowStyle, clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, menuTextPrimaryColor(), false)
                    if (invalid) {
                        val status = "Missing"
                        val statusWidth = font.width(vText(status))
                        context.drawString(font, vText(status), row.rect.x + row.rect.width - statusWidth - 14, row.rect.y + 10, 0xFFFF8181.toInt(), false)
                    }
                    val textRect = inputTextRect(row)
                    drawRoundedPanel(
                        context,
                        textRect.x,
                        textRect.y,
                        textRect.width,
                        textRect.height,
                        menuFieldFillColor(),
                        menuFieldBorderColor(),
                        11,
                    )
                    drawThemeFieldSwatch(context, row)
                }

                is SettingRowLayout.Choice -> {
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, inputRowStyle(), clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, menuTextPrimaryColor(), false)
                    val selectedValue = choiceValueFor(row.key, row.options.firstOrNull()?.value.orEmpty())
                    row.options.forEach { option ->
                        val hovered = option.rect.contains(mouseX, mouseY)
                        val selected = selectedValue == option.value
                        val fill = choiceChipFill(selected, hovered)
                        val border = choiceChipBorder(selected, hovered)
                        drawRoundedPanel(context, option.rect.x, option.rect.y, option.rect.width, option.rect.height, fill, border, 9)
                        val text = vText(option.label)
                        val textWidth = font.width(text)
                        context.drawString(
                            font,
                            text,
                            option.rect.x + ((option.rect.width - textWidth) / 2),
                            option.rect.y + 5,
                            if (selected) menuTextPrimaryColor() else menuTextSecondaryColor(),
                            false,
                        )
                    }
                }

                is SettingRowLayout.Slider -> {
                    val rowStyle = inputRowStyle()
                    val clipRect = SdfPanelRenderer.ClipRect(viewport.x, viewport.y, viewport.width, viewport.height)
                    SdfPanelRenderer.draw(context, row.rect.x, row.rect.y, row.rect.width, row.rect.height, rowStyle, clipRect)
                    context.drawString(font, vText(row.label), row.rect.x + 14, row.rect.y + 10, menuTextPrimaryColor(), false)
                    val valueText = formatSliderValue(row.key, ModuleStateStore.getNumberSetting(row.key, row.min))
                    val valueWidth = font.width(vText(valueText))
                    context.drawString(font, vText(valueText), row.rect.x + row.rect.width - valueWidth - 14, row.rect.y + 10, menuTextSecondaryColor(), false)
                    drawThemeSlider(
                        context,
                        row.trackRect.x,
                        row.trackRect.y,
                        row.trackRect.width,
                        row.trackRect.height,
                        sliderProgress(row),
                        clipRect,
                    )
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

        context.drawString(font, vText("Theme Preview"), previewX + 18, previewY + 16, menuTextPrimaryColor(), false)
        context.drawString(font, vText("Accent, neon, toggle and slider colors update live here"), previewX + 18, previewY + 30, menuTextDimColor(), false)

        val sampleCardY = previewY + 56
        val sampleCardWidth = ((previewWidth - 52) / 2).coerceAtLeast(160)
        val sampleCardHeight = 74
        val leftCard = IntRect(previewX + 18, sampleCardY, sampleCardWidth, sampleCardHeight)
        val rightCard = IntRect(leftCard.x + leftCard.width + 16, sampleCardY, sampleCardWidth, sampleCardHeight)

        drawThemeSampleCard(context, leftCard, "Selected Card", "Uses accent + neon", enabled = true, selected = true, clipRect = previewClip)
        drawThemeSampleCard(context, rightCard, "Default Card", "Neutral surface", enabled = false, selected = false, clipRect = previewClip)

        val toggleRow = IntRect(previewX + 18, sampleCardY + sampleCardHeight + 18, previewWidth - 36, 36)
        SdfPanelRenderer.draw(context, toggleRow.x, toggleRow.y, toggleRow.width, toggleRow.height, settingRowStyle(hovered = true), previewClip)
        context.drawString(font, vText("Toggle Preview"), toggleRow.x + 14, toggleRow.y + 11, menuTextPrimaryColor(), false)
        drawToggle(
            context,
            IntRect(toggleRow.x + toggleRow.width - 44, toggleRow.y + 8, 32, 18),
            enabled = true,
            animationKey = "theme_preview_toggle",
            clipRect = previewClip,
        )

        val sliderTrackX = previewX + 30
        val sliderTrackY = toggleRow.y + 58
        val sliderTrackWidth = (previewWidth - 60).coerceAtLeast(140)
        val sliderTrackHeight = 10
        drawThemeSlider(context, sliderTrackX, sliderTrackY, sliderTrackWidth, sliderTrackHeight, 0.68f, previewClip)
        context.drawString(font, vText("Slider Preview"), sliderTrackX, sliderTrackY - 14, menuTextPrimaryColor(), false)

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
        drawRoundedPanel(context, rect.x + 14, rect.y + 14, 26, 26, menuIconSlotFillColor(), menuIconSlotBorderColor(), 10)
        context.drawString(font, vText(if (selected) "T" else "N"), rect.x + 23, rect.y + 22, menuTextSecondaryColor(), false)
        context.drawString(font, fitStyledText(font, title, rect.width - 116), rect.x + 50, rect.y + 16, menuTextPrimaryColor(), false)
        context.drawString(font, fitStyledText(font, subtitle, rect.width - 116), rect.x + 50, rect.y + 31, menuTextDimColor(), false)
        drawToggle(
            context,
            IntRect(rect.x + rect.width - 48, rect.y + 28, 34, 18),
            enabled,
            "theme_sample:$title",
            clipRect,
        )
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
                baseColor = if (isTransparentMenuTheme()) {
                    0x66202936
                } else if (isLightMenuTheme()) {
                    0xE8EEF4FC.toInt()
                } else {
                    0xB71A2335.toInt()
                },
                borderColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFF677283.toInt(), sliderFillColor(), 0.14f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFBCC9DC.toInt(), sliderFillColor(), 0.16f)
                } else {
                    blendColor(0xFF3B4B67.toInt(), accentStrongColor(), 0.25f)
                },
                borderWidthPx = 1.0f,
                radiusPx = (height / 2f),
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.02f, opacity = 0.02f),
                outerGlow = menuGlow(accentStrongColor(), radiusPx = 8f, strength = if (isLightMenuTheme()) 0.05f else 0.08f, opacity = if (isLightMenuTheme()) 0.03f else 0.04f),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x0E000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0AD5DFED) else SdfShadeStyle(0x08FFFFFF, 0x0E000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x3A else 0x54),
                    widthPx = 0.8f,
                    softnessPx = 3.5f,
                    strength = if (isLightMenuTheme()) 0.16f else 0.24f,
                ),
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
                baseColor = if (isTransparentMenuTheme()) {
                    blendColor(0x7A1F2836, sliderFillColor(), 0.76f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFF7FBFF.toInt(), sliderFillColor(), 0.72f)
                } else {
                    sliderFillColor()
                },
                borderColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFF697383.toInt(), sliderFillColor(), 0.26f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFDCE7F6.toInt(), sliderFillColor(), 0.44f)
                } else {
                    blendColor(sliderFillColor(), 0xFFFFFFFF.toInt(), 0.18f)
                },
                borderWidthPx = 0.8f,
                radiusPx = ((height - 2) / 2f),
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 5f, strength = 0.05f, opacity = 0.04f),
                outerGlow = menuGlow(sliderFillColor(), radiusPx = 8f, strength = if (isLightMenuTheme()) 0.10f else 0.14f, opacity = if (isLightMenuTheme()) 0.06f else 0.08f),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x0A000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x08D0DBEA) else SdfShadeStyle(0x10FFFFFF, 0x0A000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x78 else 0xD5),
                    widthPx = 0.9f,
                    softnessPx = 4f,
                    strength = if (isLightMenuTheme()) 0.26f else 0.54f,
                ),
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
                baseColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFFF7FAFF.toInt(), sliderKnobColor(), 0.50f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFFFFFFF.toInt(), sliderKnobColor(), 0.56f)
                } else {
                    sliderKnobColor()
                },
                borderColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFF697383.toInt(), sliderFillColor(), 0.20f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFCDD9EC.toInt(), sliderFillColor(), 0.24f)
                } else {
                    blendColor(sliderKnobColor(), accentStrongColor(), 0.32f)
                },
                borderWidthPx = 1.0f,
                radiusPx = 6f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.06f, opacity = 0.06f),
                outerGlow = menuGlow(accentStrongColor(), radiusPx = 8f, strength = if (isLightMenuTheme()) 0.10f else 0.16f, opacity = if (isLightMenuTheme()) 0.08f else 0.12f),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x08000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x06CDD8E8) else SdfShadeStyle(0x0EFFFFFF, 0x08000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x70 else 0xC8),
                    widthPx = 0.9f,
                    softnessPx = 4f,
                    strength = if (isLightMenuTheme()) 0.28f else 0.58f,
                ),
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
            menuTextDimColor(),
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

        fun addSection(label: String) {
            val rect = IntRect(startX, currentY, width, 18)
            rows += SettingRowLayout.Section(label, rect)
            currentY += 22
        }

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

        fun addSlider(key: String, label: String, min: Float, max: Float) {
            val rect = IntRect(startX, currentY, width, Theme.inputRowHeight)
            val trackRect = IntRect(rect.x + 14, rect.y + 26, rect.width - 28, 10)
            rows += SettingRowLayout.Slider(key, label, rect, trackRect, min, max)
            currentY += Theme.inputRowHeight + 10
        }

        fun addChoice(key: String, label: String, vararg options: Pair<String, String>) {
            val rect = IntRect(startX, currentY, width, Theme.inputRowHeight)
            val optionsY = rect.y + 22
            val optionGap = 8
            val optionWidth = ((rect.width - 20 - ((options.size - 1) * optionGap)) / options.size).coerceAtLeast(34)
            val layouts = options.mapIndexed { index, option ->
                val x = rect.x + 10 + (index * (optionWidth + optionGap))
                ChoiceOptionLayout(option.first, option.second, IntRect(x, optionsY, optionWidth, 18))
            }
            rows += SettingRowLayout.Choice(key, label, rect, layouts)
            currentY += Theme.inputRowHeight + 10
        }

        if (activeTab == VisualMenuTab.THEME) {
            addChoice(
                VisualThemeSettings.menuPresetKey,
                "Theme",
                VisualThemeSettings.MenuPreset.DARK.id to "Dark",
                VisualThemeSettings.MenuPreset.LIGHT.id to "White",
                VisualThemeSettings.MenuPreset.TRANSPARENT.id to "Transparent",
            )
            addChoice(
                VisualThemeSettings.themeFontKey,
                "Font",
                VisualThemeSettings.ThemeFont.JALNAN.id to VisualThemeSettings.ThemeFont.JALNAN.label,
                VisualThemeSettings.ThemeFont.SATYR.id to VisualThemeSettings.ThemeFont.SATYR.label,
                VisualThemeSettings.ThemeFont.BLACKCRAFT.id to VisualThemeSettings.ThemeFont.BLACKCRAFT.label,
            )
            addToggle(VisualThemeSettings.neonBorderEnabledKey, "Neon Border")
            addToggle(VisualThemeSettings.neonGlowEnabledKey, "Neon Glow")
            addInput(VisualThemeSettings.accentColorKey, "Accent Glow", "#RRGGBB")
            addInput(VisualThemeSettings.neonBorderColorKey, "Neon Border Color", "#RRGGBB")
            addInput(VisualThemeSettings.toggleFillColorKey, "Toggle Fill", "#RRGGBB")
            addInput(VisualThemeSettings.sliderFillColorKey, "Slider Fill", "#RRGGBB")
            addInput(VisualThemeSettings.sliderKnobColorKey, "Slider Knob", "#RRGGBB")
            return rows
        }

        val selectedModule = modules.firstOrNull { it.id == selectedModuleId } ?: return emptyList()

        if (selectedModule.id == NotificationsSettings.moduleId) {
            val mode = choiceValueFor(NotificationsSettings.modeKey, "1")
            val hitMode = choiceValueFor(NotificationsSettings.hitSoundModeKey, "classic")
            val critMode = choiceValueFor(NotificationsSettings.critSoundModeKey, "classic")

            addSection("Custom Sound Volume")
            addSlider(NotificationsSettings.globalCustomSoundVolumeKey, "Global Custom Sound Volume", 0f, 100f)

            addSection("Module Toggle Sounds")
            addToggle(NotificationsSettings.moduleEnableSoundEnabledKey, "Enable Custom Sound")
            addInput(NotificationsSettings.moduleEnableSoundFileKey, "Enable Sound File", "name / name.mp3")
            addSlider(NotificationsSettings.moduleEnableSoundVolumeKey, "Enable Sound Volume", 0f, 100f)
            addToggle(NotificationsSettings.moduleDisableSoundEnabledKey, "Disable Custom Sound")
            addInput(NotificationsSettings.moduleDisableSoundFileKey, "Disable Sound File", "name / name.mp3")
            addSlider(NotificationsSettings.moduleDisableSoundVolumeKey, "Disable Sound Volume", 0f, 100f)

            addSection("Potion Warnings")
            addChoice(NotificationsSettings.modeKey, "Notification Mode", "1" to "1", "2" to "2")
            addSlider(NotificationsSettings.stage1LeadKey, "Stage 1 Lead (Final)", 0.1f, 30f)
            addToggle(NotificationsSettings.stage1SoundEnabledKey, "Stage 1 Custom Sound")
            addInput(NotificationsSettings.stage1SoundFileKey, "Stage 1 Sound File", "name / name.mp3")
            addSlider(NotificationsSettings.stage1SoundVolumeKey, "Stage 1 Volume", 0f, 100f)
            if (mode == "2") {
                addSlider(NotificationsSettings.stage2LeadKey, "Stage 2 Lead (Early)", 0.1f, 30f)
                addToggle(NotificationsSettings.stage2SoundEnabledKey, "Stage 2 Custom Sound")
                addInput(NotificationsSettings.stage2SoundFileKey, "Stage 2 Sound File", "name / name.mp3")
                addSlider(NotificationsSettings.stage2SoundVolumeKey, "Stage 2 Volume", 0f, 100f)
            }
            addSlider(NotificationsSettings.repeatPeriodKey, "Sound Repeat Period", 0.1f, 10f)

            addSection("Armor Warnings")
            addToggle(NotificationsSettings.armorNotificationsEnabledKey, "Armor Notifications")
            addSlider(NotificationsSettings.armorStage1PercentKey, "Armor Stage 1 (Final)", 1f, 100f)
            addToggle(NotificationsSettings.armorStage1SoundEnabledKey, "Armor Stage 1 Sound")
            addInput(NotificationsSettings.armorStage1SoundFileKey, "Armor Stage 1 Sound File", "name / name.mp3")
            addSlider(NotificationsSettings.armorStage1SoundVolumeKey, "Armor Stage 1 Volume", 0f, 100f)
            if (mode == "2") {
                addSlider(NotificationsSettings.armorStage2PercentKey, "Armor Stage 2 (Early)", 1f, 100f)
                addToggle(NotificationsSettings.armorStage2SoundEnabledKey, "Armor Stage 2 Sound")
                addInput(NotificationsSettings.armorStage2SoundFileKey, "Armor Stage 2 Sound File", "name / name.mp3")
                addSlider(NotificationsSettings.armorStage2SoundVolumeKey, "Armor Stage 2 Volume", 0f, 100f)
            }

            addSection("Hit / Crit Sounds")
            addChoice(NotificationsSettings.hitSoundModeKey, "Hit Sound", "classic" to "Classic", "custom" to "Custom")
            if (hitMode == "custom") {
                addInput(NotificationsSettings.hitSoundFileKey, "Hit Sound File", "name / name.mp3")
                addSlider(NotificationsSettings.hitSoundVolumeKey, "Hit Sound Volume", 0f, 100f)
            }
            addChoice(NotificationsSettings.critSoundModeKey, "Crit Sound", "classic" to "Classic", "custom" to "Custom")
            if (critMode == "custom") {
                addInput(NotificationsSettings.critSoundFileKey, "Crit Sound File", "name / name.mp3")
                addSlider(NotificationsSettings.critSoundVolumeKey, "Crit Sound Volume", 0f, 100f)
            }
            return rows
        }

        addToggle("${selectedModule.id}:visible_hud", "Visible In HUD")
        if (selectedModule.tab == VisualMenuTab.HUD) {
            val minSize = if (selectedModule.id == "gif_hud") 0.1f else 0.5f
            addSlider("${selectedModule.id}:size", "Size", minSize, 3f)
        }
        if (selectedModule.id != "gif_hud") {
            addToggle("${selectedModule.id}:accent_sync", "Accent Sync")
        }
        if (selectedModule.id == WatermarkHudModule.watermarkModuleId) {
            addChoice(
                WatermarkHudModule.typeKey,
                "Type",
                WatermarkHudModule.WatermarkType.CLASSIC.id to WatermarkHudModule.WatermarkType.CLASSIC.label,
                WatermarkHudModule.WatermarkType.HYPMOSIA_INFO.id to WatermarkHudModule.WatermarkType.HYPMOSIA_INFO.label,
            )
            if (choiceValueFor(WatermarkHudModule.typeKey, WatermarkHudModule.WatermarkType.CLASSIC.id) ==
                WatermarkHudModule.WatermarkType.CLASSIC.id
            ) {
                addToggle("${selectedModule.id}:music_scan", "Music Scan")
            } else {
                addInput(WatermarkHudModule.customLabelKey, "Custom Label", "Developer")
            }
        }
        if (selectedModule.id == MusicHudModule.moduleId) {
            addToggle(MusicHudModule.musicScanKey, "Music Scan")
        }
        if (selectedModule.id == "item_bar_hud") {
            addChoice(
                ItemBarHudModule.layoutTypeKey,
                "Type",
                ItemBarHudModule.LayoutType.PANEL.id to ItemBarHudModule.LayoutType.PANEL.label,
                ItemBarHudModule.LayoutType.COMPACT.id to ItemBarHudModule.LayoutType.COMPACT.label,
                ItemBarHudModule.LayoutType.VERTICAL.id to ItemBarHudModule.LayoutType.VERTICAL.label,
            )
            addToggle(ItemBarHudModule.showPlayerStatusKey, "Show Player Status")
            addToggle("${selectedModule.id}:hide_vanilla_hotbar", "Hide Vanilla Hotbar")
        }
        if (selectedModule.id == "armor_hud") {
            addChoice(
                ArmorHudModule.layoutTypeKey,
                "Type",
                ArmorHudModule.LayoutType.VERTICAL.id to ArmorHudModule.LayoutType.VERTICAL.label,
                ArmorHudModule.LayoutType.RIGHT_90.id to ArmorHudModule.LayoutType.RIGHT_90.label,
            )
            addToggle("${selectedModule.id}:slot_background", "Slot Background")
        }
        if (selectedModule.id == "target_hud") {
            addSlider(TargetHudModule.lifetimeSecondsKey, "Lifetime", 0f, 5f)
        }
        if (selectedModule.id == BtcHudModule.moduleId) {
            addToggle(BtcHudModule.showBpsKey, "Show BPS")
            addToggle(BtcHudModule.showTpsKey, "Show TPS")
            addToggle(BtcHudModule.showCoordsKey, "Show Coords")
        }
        if (selectedModule.id == "gif_hud") {
            addInput("${selectedModule.id}:file_name", "Media File", "name.gif / .png / .jpg")
            addToggle("${selectedModule.id}:chroma_key_enabled", "Chroma Key")
            addInput("${selectedModule.id}:chroma_key_color", "Chroma Key Color", "#RRGGBB")
            addToggle("${selectedModule.id}:invert_colors", "Invert Colors")
            addSlider("${selectedModule.id}:chroma_key_strength", "Chroma Key Strength", 0f, 1f)
        }

        if (selectedModule.id == VisualClientMod.sdfTestModuleId) {
            addToggle("${selectedModule.id}:outer_glow", "Outer Glow")
        }

        return rows
    }

    private fun settingToggleRows(layout: ScreenLayout): List<SettingRowLayout.Toggle> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Toggle>()
    }

    private fun settingChoiceRows(layout: ScreenLayout): List<SettingRowLayout.Choice> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Choice>()
    }

    private fun settingInputRows(layout: ScreenLayout): List<SettingRowLayout.Input> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Input>()
    }

    private fun settingSliderRows(layout: ScreenLayout): List<SettingRowLayout.Slider> {
        return settingRows(layout).filterIsInstance<SettingRowLayout.Slider>()
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

    private fun searchInputRect(layout: ScreenLayout): IntRect {
        return IntRect(
            layout.search.x + 12,
            layout.search.y + 6,
            layout.search.width - 24,
            layout.search.height - 10,
        )
    }

    private fun syncWidgetLayout(layout: ScreenLayout) {
        if (::searchBox.isInitialized) {
            val searchRect = searchInputRect(layout)
            searchBox.setX(searchRect.x)
            searchBox.setY(searchRect.y)
            searchBox.setWidth(searchRect.width)
            searchBox.setHeight(searchRect.height)
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
            VisualThemeSettings.toggleFillColorKey -> ModuleStateStore.getTextSetting(key, "#8A71FF")
            VisualThemeSettings.sliderFillColorKey -> ModuleStateStore.getTextSetting(key, "#8A71FF")
            VisualThemeSettings.sliderKnobColorKey -> ModuleStateStore.getTextSetting(key, "#F0F2FF")
            "gif_hud:file_name" -> ModuleStateStore.getTextSetting(key, "")
            "gif_hud:chroma_key_color" -> ModuleStateStore.getTextSetting(key, "#00FF00")
            WatermarkHudModule.customLabelKey -> ModuleStateStore.getTextSetting(key, "Developer")
            else -> ModuleStateStore.getTextSetting(key, "")
        }
    }

    private fun isThemeColorKey(key: String): Boolean {
        return key == VisualThemeSettings.accentColorKey ||
            key == VisualThemeSettings.neonBorderColorKey ||
            key == VisualThemeSettings.toggleFillColorKey ||
            key == VisualThemeSettings.sliderFillColorKey ||
            key == VisualThemeSettings.sliderKnobColorKey ||
            key == "gif_hud:chroma_key_color"
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
        val border = if (activeThemeColorKey == row.key) {
            accentStrongColor()
        } else {
            if (isLightMenuTheme()) blendColor(0xFFC5D2E5.toInt(), color, 0.26f) else blendColor(0xFF3B4D6D.toInt(), color, 0.35f)
        }
        drawRoundedPanel(
            context,
            swatch.x,
            swatch.y,
            swatch.width,
            swatch.height,
            if (isLightMenuTheme()) 0xEEF5F8FE.toInt() else 0xDD0F1727.toInt(),
            border,
            7,
        )
        fillRoundedRect(context, swatch.x + 3, swatch.y + 3, swatch.width - 6, swatch.height - 6, 4, color)
    }

    private fun themePickerLayout(layout: ScreenLayout): ThemePickerLayout? {
        if (activeThemeColorKey == null) return null
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
            VisualThemeSettings.toggleFillColorKey -> toggleFillColor()
            VisualThemeSettings.sliderFillColorKey -> sliderFillColor()
            VisualThemeSettings.sliderKnobColorKey -> sliderKnobColor()
            "gif_hud:chroma_key_color" -> parseColorSetting(ModuleStateStore.getTextSetting(key, "#00FF00"), 0xFF00FF00.toInt())
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun drawThemeColorPicker(context: GuiGraphics, layout: ScreenLayout, mouseX: Double, mouseY: Double) {
        val picker = themePickerLayout(layout) ?: return
        ensureThemePickerTextures()

        SdfPanelRenderer.draw(context, picker.bounds.x, picker.bounds.y, picker.bounds.width, picker.bounds.height, settingsStyle())

        val label = when (activeThemeColorKey) {
            VisualThemeSettings.accentColorKey -> "Accent Glow"
            VisualThemeSettings.neonBorderColorKey -> "Neon Border Color"
            VisualThemeSettings.toggleFillColorKey -> "Toggle Fill"
            VisualThemeSettings.sliderFillColorKey -> "Slider Fill"
            VisualThemeSettings.sliderKnobColorKey -> "Slider Knob"
            "gif_hud:chroma_key_color" -> "Chroma Key Color"
            else -> "Theme Color"
        }
        context.drawString(font, vText(label), picker.bounds.x + 18, picker.bounds.y + 16, menuTextPrimaryColor(), false)
        context.drawString(font, vText("Click the ring for hue and the square for tone"), picker.bounds.x + 18, picker.bounds.y + 30, menuTextDimColor(), false)

        blitTexture(context, hueWheelTextureId, picker.wheelRect)
        blitTexture(context, saturationValueTextureId, picker.squareRect)

        val centerX = picker.wheelRect.x + (picker.wheelRect.width / 2f)
        val centerY = picker.wheelRect.y + (picker.wheelRect.height / 2f)
        val indicatorRadius = 45f
        val angle = pickerHue * (kotlin.math.PI.toFloat() * 2f)
        val hueIndicatorX = (centerX + kotlin.math.cos(angle) * indicatorRadius).roundToInt()
        val hueIndicatorY = (centerY + kotlin.math.sin(angle) * indicatorRadius).roundToInt()
        drawRoundedPanel(
            context,
            hueIndicatorX - 5,
            hueIndicatorY - 5,
            10,
            10,
            if (isTransparentMenuTheme()) 0xFFF6F8FF.toInt() else if (isLightMenuTheme()) 0xFFFFFFFF.toInt() else 0xFFF2F4FF.toInt(),
            accentStrongColor(),
            5,
        )

        val squareIndicatorX = (picker.squareRect.x + (picker.squareRect.width * pickerSaturation)).roundToInt().coerceIn(picker.squareRect.x + 4, picker.squareRect.x + picker.squareRect.width - 4)
        val squareIndicatorY = (picker.squareRect.y + (picker.squareRect.height * (1f - pickerValue))).roundToInt().coerceIn(picker.squareRect.y + 4, picker.squareRect.y + picker.squareRect.height - 4)
        drawRoundedPanel(
            context,
            squareIndicatorX - 4,
            squareIndicatorY - 4,
            8,
            8,
            if (isTransparentMenuTheme()) 0xFFF6F8FF.toInt() else if (isLightMenuTheme()) 0xFFFFFFFF.toInt() else 0xFFF2F4FF.toInt(),
            if (isTransparentMenuTheme()) 0xFF556171.toInt() else if (isLightMenuTheme()) 0xFF33415F.toInt() else 0xFF111827.toInt(),
            4,
        )

        val currentColor = currentThemeColorForKey(activeThemeColorKey ?: return)
        SdfPanelRenderer.draw(
            context,
            picker.previewRect.x,
            picker.previewRect.y,
            picker.previewRect.width,
            picker.previewRect.height,
            SdfPanelStyle(
                baseColor = if (isTransparentMenuTheme()) {
                    0x6C1E2633
                } else if (isLightMenuTheme()) {
                    0xFFF5F8FE.toInt()
                } else {
                    0xFF111A2D.toInt()
                },
                borderColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFF697383.toInt(), currentColor, 0.20f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFC8D3E6.toInt(), currentColor, 0.24f)
                } else {
                    blendColor(0xFF33415F.toInt(), currentColor, 0.30f)
                },
                borderWidthPx = 1f,
                radiusPx = 16f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
                outerGlow = menuGlow(currentColor, radiusPx = 16f, strength = if (isLightMenuTheme()) 0.08f else 0.12f, opacity = if (isLightMenuTheme()) 0.06f else 0.08f),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0CD2DDEC) else SdfShadeStyle(0x0EFFFFFF, 0x14000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x54 else 0x84),
                    widthPx = 0.9f,
                    softnessPx = 4f,
                    strength = if (isLightMenuTheme()) 0.22f else 0.34f,
                ),
            ),
        )
        fillRoundedRect(context, picker.previewRect.x + 12, picker.previewRect.y + 12, picker.previewRect.width - 24, picker.previewRect.height - 24, 14, currentColor)
        context.drawString(font, vText(settingValueFor(activeThemeColorKey ?: "")), picker.previewRect.x - 2, picker.previewRect.y + picker.previewRect.height + 12, menuTextPrimaryColor(), false)

        val closeHovered = picker.bounds.contains(mouseX, mouseY).not()
        context.drawString(font, vText(if (closeHovered) "Click outside to close" else "Release mouse to finish"), picker.bounds.x + 18, picker.bounds.y + picker.bounds.height - 18, menuTextDimColor(), false)
    }

    private fun ensureThemePickerTextures() {
        val client = Minecraft.getInstance()
        if (!hueWheelTextureReady) {
            client.textureManager.register(
                hueWheelTextureId,
                NonDumpableDynamicTexture({ "visualclient-theme-picker-wheel" }, createHueWheelImage(120)),
            )
            hueWheelTextureReady = true
        }

        if (kotlin.math.abs(pickerSquareHue - pickerHue) > 0.001f) {
            client.textureManager.register(
                saturationValueTextureId,
                NonDumpableDynamicTexture({ "visualclient-theme-picker-square" }, createSaturationValueImage(60, pickerHue)),
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

    private fun sanitizeSoundFileName(raw: String): String {
        return CustomSoundRegistry.sanitizeForStorage(raw)
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
    }

    private fun choiceValueFor(key: String, fallback: String): String {
        val raw = ModuleStateStore.getTextSetting(key, fallback).trim()
        return if (raw.isBlank()) fallback else raw
    }

    private fun choiceAffectsLayout(key: String): Boolean {
        return key == VisualThemeSettings.menuPresetKey ||
            key == VisualThemeSettings.themeFontKey ||
            key == WatermarkHudModule.typeKey ||
            key == NotificationsSettings.modeKey ||
            key == NotificationsSettings.hitSoundModeKey ||
            key == NotificationsSettings.critSoundModeKey
    }

    private fun normalizeNotificationChoiceState(changedKey: String) {
        if (!changedKey.startsWith("${NotificationsSettings.moduleId}:")) return

        ModuleStateStore.setTextSetting(
            NotificationsSettings.modeKey,
            if (choiceValueFor(NotificationsSettings.modeKey, "1") == "2") "2" else "1",
        )
        ModuleStateStore.setTextSetting(
            NotificationsSettings.hitSoundModeKey,
            if (choiceValueFor(NotificationsSettings.hitSoundModeKey, "classic").equals("custom", ignoreCase = true)) "custom" else "classic",
        )
        ModuleStateStore.setTextSetting(
            NotificationsSettings.critSoundModeKey,
            if (choiceValueFor(NotificationsSettings.critSoundModeKey, "classic").equals("custom", ignoreCase = true)) "custom" else "classic",
        )

        val stage1 = ModuleStateStore.getNumberSetting(NotificationsSettings.stage1LeadKey, 1.0f).coerceIn(0.1f, 30.0f)
        val stage2 = ModuleStateStore.getNumberSetting(NotificationsSettings.stage2LeadKey, 5.0f).coerceIn(0.1f, 30.0f)
        ModuleStateStore.setNumberSetting(NotificationsSettings.stage1LeadKey, min(stage1, stage2))
        ModuleStateStore.setNumberSetting(NotificationsSettings.stage2LeadKey, max(stage1, stage2))

        val armorStage1 = ModuleStateStore.getNumberSetting(NotificationsSettings.armorStage1PercentKey, 10.0f).coerceIn(1.0f, 100.0f)
        val armorStage2 = ModuleStateStore.getNumberSetting(NotificationsSettings.armorStage2PercentKey, 25.0f).coerceIn(1.0f, 100.0f)
        if (choiceValueFor(NotificationsSettings.modeKey, "1") == "2") {
            ModuleStateStore.setNumberSetting(NotificationsSettings.armorStage1PercentKey, min(armorStage1, armorStage2))
            ModuleStateStore.setNumberSetting(NotificationsSettings.armorStage2PercentKey, max(armorStage1, armorStage2))
        } else {
            ModuleStateStore.setNumberSetting(NotificationsSettings.armorStage1PercentKey, armorStage1)
            ModuleStateStore.setNumberSetting(NotificationsSettings.armorStage2PercentKey, armorStage2)
        }
    }

    private fun isMissingSoundSetting(key: String): Boolean {
        if (key != NotificationsSettings.moduleEnableSoundFileKey &&
            key != NotificationsSettings.moduleDisableSoundFileKey &&
            key != NotificationsSettings.stage1SoundFileKey &&
            key != NotificationsSettings.stage2SoundFileKey &&
            key != NotificationsSettings.armorStage1SoundFileKey &&
            key != NotificationsSettings.armorStage2SoundFileKey &&
            key != NotificationsSettings.hitSoundFileKey &&
            key != NotificationsSettings.critSoundFileKey
        ) {
            return false
        }

        val value = ModuleStateStore.getTextSetting(key, "").trim()
        return value.isNotBlank() && !CustomSoundRegistry.exists(value)
    }

    private fun parseColorSetting(value: String, fallback: Int): Int {
        val normalized = normalizeHexColor(value) ?: return fallback
        val compact = normalized.removePrefix("#")
        return compact.toLongOrNull(16)?.let { packed ->
            if (compact.length == 6) {
                (0xFF000000 or packed).toInt()
            } else {
                packed.toInt()
            }
        } ?: fallback
    }

    private fun sliderProgress(row: SettingRowLayout.Slider): Float {
        val value = ModuleStateStore.getNumberSetting(row.key, row.min)
        if (row.max <= row.min) return 0f
        return ((value - row.min) / (row.max - row.min)).coerceIn(0f, 1f)
    }

    private fun updateSliderValue(row: SettingRowLayout.Slider, mouseX: Double) {
        val progress = ((mouseX - row.trackRect.x) / row.trackRect.width.toDouble()).toFloat().coerceIn(0f, 1f)
        val rawValue = row.min + ((row.max - row.min) * progress)
        val value = when (row.key) {
            "gif_hud:chroma_key_strength" -> (kotlin.math.round(rawValue * 100f) / 100f)
            "gif_hud:scale" -> (kotlin.math.round(rawValue * 10f) / 10f)
            NotificationsSettings.globalCustomSoundVolumeKey,
            NotificationsSettings.moduleEnableSoundVolumeKey,
            NotificationsSettings.moduleDisableSoundVolumeKey,
            NotificationsSettings.stage1SoundVolumeKey,
            NotificationsSettings.stage2SoundVolumeKey,
            NotificationsSettings.armorStage1SoundVolumeKey,
            NotificationsSettings.armorStage2SoundVolumeKey,
            NotificationsSettings.hitSoundVolumeKey,
            NotificationsSettings.critSoundVolumeKey,
            NotificationsSettings.armorStage1PercentKey,
            NotificationsSettings.armorStage2PercentKey -> kotlin.math.round(rawValue)
            NotificationsSettings.stage1LeadKey,
            NotificationsSettings.stage2LeadKey,
            NotificationsSettings.repeatPeriodKey,
            TargetHudModule.lifetimeSecondsKey -> kotlin.math.round(rawValue * 10f) / 10f
            else -> if (row.key.endsWith(":size")) {
                kotlin.math.round(rawValue * 10f) / 10f
            } else {
                rawValue
            }
        }
        ModuleStateStore.setNumberSetting(row.key, value)
        normalizeNotificationChoiceState(row.key)
    }

    private fun formatSliderValue(key: String, value: Float): String {
        return when (key) {
            "gif_hud:chroma_key_strength" -> "${(value * 100f).roundToInt()}%"
            "gif_hud:scale" -> "${String.format(java.util.Locale.US, "%.1f", value)}x"
            NotificationsSettings.globalCustomSoundVolumeKey,
            NotificationsSettings.moduleEnableSoundVolumeKey,
            NotificationsSettings.moduleDisableSoundVolumeKey,
            NotificationsSettings.stage1SoundVolumeKey,
            NotificationsSettings.stage2SoundVolumeKey,
            NotificationsSettings.armorStage1SoundVolumeKey,
            NotificationsSettings.armorStage2SoundVolumeKey,
            NotificationsSettings.hitSoundVolumeKey,
            NotificationsSettings.critSoundVolumeKey,
            NotificationsSettings.armorStage1PercentKey,
            NotificationsSettings.armorStage2PercentKey -> "${value.roundToInt()}%"
            NotificationsSettings.stage1LeadKey,
            NotificationsSettings.stage2LeadKey,
            NotificationsSettings.repeatPeriodKey,
            TargetHudModule.lifetimeSecondsKey -> "${String.format(java.util.Locale.US, "%.1f", value)}s"
            else -> if (key.endsWith(":size")) {
                "${String.format(java.util.Locale.US, "%.1f", value)}x"
            } else {
                formatNumber(value)
            }
        }
    }

    private fun accentStrongColor(): Int = VisualThemeSettings.accentStrong()

    private fun accentColor(): Int = VisualThemeSettings.accent()

    private fun neonBorderColor(): Int = VisualThemeSettings.neonBorder()

    private fun neonBorderVisualEnabled(): Boolean = VisualThemeSettings.themeAllowsNeon()

    private fun neonGlowVisualEnabled(): Boolean = VisualThemeSettings.themeAllowsOuterGlow()

    private fun toggleFillColor(): Int = VisualThemeSettings.toggleFill()

    private fun sliderFillColor(): Int = VisualThemeSettings.sliderFill()

    private fun sliderKnobColor(): Int = VisualThemeSettings.sliderKnob()

    private fun isLightMenuTheme(): Boolean = VisualThemeSettings.menuPreset() == VisualThemeSettings.MenuPreset.LIGHT

    private fun isTransparentMenuTheme(): Boolean = VisualThemeSettings.isTransparentPreset()

    private fun menuTextPrimaryColor(): Int = VisualThemeSettings.textPrimary()

    private fun menuTextSecondaryColor(): Int = VisualThemeSettings.textSecondary()

    private fun menuTextMutedColor(): Int = VisualThemeSettings.textMuted()

    private fun menuTextDimColor(): Int = when {
        isTransparentMenuTheme() -> 0xFFB2BAC7.toInt()
        isLightMenuTheme() -> 0xFF7B879C.toInt()
        else -> 0xFF7380A1.toInt()
    }

    private fun menuSearchFillColor(): Int = when {
        isTransparentMenuTheme() -> 0x40191C21
        isLightMenuTheme() -> 0xEAF1F6FF.toInt()
        else -> 0xD011192A.toInt()
    }

    private fun menuSearchBorderColor(): Int = when {
        isTransparentMenuTheme() -> 0x647A8696
        isLightMenuTheme() -> 0x8EBCCDE5.toInt()
        else -> 0x523E4D74
    }

    private fun menuFieldFillColor(): Int = when {
        isTransparentMenuTheme() -> 0x48181B20
        isLightMenuTheme() -> 0xEEF0F5FD.toInt()
        else -> 0xCC10182A.toInt()
    }

    private fun menuFieldBorderColor(): Int = when {
        isTransparentMenuTheme() -> 0x60778493
        isLightMenuTheme() -> 0x8CBCCDDF.toInt()
        else -> 0x57384866
    }

    private fun menuIconSlotFillColor(): Int = when {
        isTransparentMenuTheme() -> 0x4A171B1F
        isLightMenuTheme() -> 0xCFEAF1FC.toInt()
        else -> 0xA2141E33.toInt()
    }

    private fun menuIconSlotBorderColor(): Int = when {
        isTransparentMenuTheme() -> 0x5F778391
        isLightMenuTheme() -> 0x94BACBE4.toInt()
        else -> 0x5B3E4A69
    }

    private fun boostedNeonColor(color: Int): Int {
        return color
    }

    private fun menuGlow(color: Int, radiusPx: Float, strength: Float, opacity: Float): SdfGlowStyle {
        if (!VisualThemeSettings.themeAllowsOuterGlow()) {
            return SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f)
        }
        val strengthScale = if (isLightMenuTheme()) 0.78f else 1.08f
        val opacityScale = if (isLightMenuTheme()) 0.82f else 1.18f
        return SdfGlowStyle(
            boostedNeonColor(0xFF000000.toInt() or (color and 0x00FFFFFF)),
            radiusPx = radiusPx,
            strength = strength * strengthScale,
            opacity = (opacity * opacityScale).coerceAtMost(1f),
        )
    }

    private fun menuNeonBorder(color: Int, widthPx: Float, softnessPx: Float, strength: Float): SdfNeonBorderStyle {
        if (!VisualThemeSettings.themeAllowsNeon()) {
            return SdfNeonBorderStyle(0x00000000, widthPx = 0f, softnessPx = 0f, strength = 0f)
        }
        val widthScale = if (isLightMenuTheme()) 1.18f else 1.18f
        val softnessScale = if (isLightMenuTheme()) 1.04f else 1.12f
        val strengthScale = if (isLightMenuTheme()) 1.10f else 1.24f
        return SdfNeonBorderStyle(
            color = boostedNeonColor(color),
            widthPx = widthPx * widthScale,
            softnessPx = softnessPx * softnessScale,
            strength = (strength * strengthScale).coerceAtMost(1f),
        )
    }

    private fun choiceChipFill(selected: Boolean, hovered: Boolean): Int = when {
        isTransparentMenuTheme() && selected -> blendColor(0x66212A37, sliderFillColor(), 0.62f)
        isTransparentMenuTheme() && hovered -> 0x5A26303E
        isTransparentMenuTheme() -> 0x50212936
        selected && isLightMenuTheme() -> blendColor(0xFFF2F7FF.toInt(), sliderFillColor(), 0.38f)
        selected -> blendColor(0xFF19243A.toInt(), sliderFillColor(), 0.78f)
        hovered && isLightMenuTheme() -> 0xEAF2F7FF.toInt()
        hovered -> 0xD1141D30.toInt()
        isLightMenuTheme() -> 0xDDF7FAFE.toInt()
        else -> 0xC910182A.toInt()
    }

    private fun choiceChipBorder(selected: Boolean, hovered: Boolean): Int = when {
        isTransparentMenuTheme() && selected -> blendColor(0x6A677383, sliderFillColor(), 0.34f)
        isTransparentMenuTheme() && hovered -> blendColor(0x58677383, accentStrongColor(), 0.18f)
        isTransparentMenuTheme() -> 0x4E677383
        selected && isLightMenuTheme() -> blendColor(0xFFB9CAE3.toInt(), sliderFillColor(), 0.34f)
        selected -> blendColor(accentStrongColor(), neonBorderColor(), 0.35f)
        hovered && isLightMenuTheme() -> blendColor(0xFFCBD7E8.toInt(), accentStrongColor(), 0.10f)
        hovered -> blendColor(0xFF425270.toInt(), accentStrongColor(), 0.18f)
        isLightMenuTheme() -> 0x88C6D1E3.toInt()
        else -> 0x6A33415E
    }

    private fun shellStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x58121519
                isLightMenuTheme() -> 0xE8EEF4FB.toInt()
                else -> 0xFB0B111B.toInt()
            },
            borderColor = when {
                isTransparentMenuTheme() -> 0x697A8595
                isLightMenuTheme() -> 0xAFC3D6E8.toInt()
                else -> 0xBE334362.toInt()
            },
            borderWidthPx = 1.5f,
            radiusPx = 30f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 20f, strength = 0.08f, opacity = 0.06f),
            outerGlow = menuGlow(accentStrongColor(), radiusPx = 44f, strength = if (isLightMenuTheme()) 0.14f else 0.22f, opacity = if (isLightMenuTheme()) 0.08f else 0.14f),
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0C000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x0CFFFFFF, 0x0CCED9E8)
            } else {
                SdfShadeStyle(0x1AFFFFFF, 0x22000000)
            },
            neonBorder = menuNeonBorder(VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x9E else 0xB2), widthPx = 1.25f, softnessPx = 10f, strength = if (isLightMenuTheme()) 0.62f else 0.90f),
        )
    }

    private fun sidebarStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x4E14171C
                isLightMenuTheme() -> 0xE4EDF3FA.toInt()
                else -> 0xF50E1728.toInt()
            },
            borderColor = when {
                isTransparentMenuTheme() -> 0x64798695
                isLightMenuTheme() -> 0xA3BACDE3.toInt()
                else -> 0xA13A4A69.toInt()
            },
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 16f, strength = 0.04f, opacity = 0.05f),
            outerGlow = if (isTransparentMenuTheme()) {
                SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f)
            } else if (isLightMenuTheme()) {
                SdfGlowStyle(0xFFD8E2F0.toInt(), radiusPx = 18f, strength = 0.06f, opacity = 0.07f)
            } else {
                SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.16f, opacity = 0.24f)
            },
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0C000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x08FFFFFF, 0x0CD2DDEC)
            } else {
                SdfShadeStyle(0x10FFFFFF, 0x12000000)
            },
            neonBorder = menuNeonBorder(VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x48 else 0x5E), widthPx = 0.95f, softnessPx = 5f, strength = if (isLightMenuTheme()) 0.30f else 0.42f),
        )
    }

    private fun sectionStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x4A14171C
                isLightMenuTheme() -> 0xE7F0F6FC.toInt()
                else -> 0xF40D1627.toInt()
            },
            borderColor = when {
                isTransparentMenuTheme() -> 0x64798695
                isLightMenuTheme() -> 0xA6C0CEE3.toInt()
                else -> 0xA13A4A69.toInt()
            },
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 10f, strength = 0.03f, opacity = 0.03f),
            outerGlow = if (isTransparentMenuTheme()) {
                SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f)
            } else if (isLightMenuTheme()) {
                SdfGlowStyle(0xFFDDE6F4.toInt(), radiusPx = 18f, strength = 0.06f, opacity = 0.07f)
            } else {
                SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.15f, opacity = 0.18f)
            },
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0C000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x08FFFFFF, 0x0CD2DEEE)
            } else {
                SdfShadeStyle(0x10FFFFFF, 0x10000000)
            },
            neonBorder = menuNeonBorder(VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x44 else 0x4A), widthPx = 0.9f, softnessPx = 4.5f, strength = if (isLightMenuTheme()) 0.28f else 0.34f),
        )
    }

    private fun listStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x4613161B
                isLightMenuTheme() -> 0xE9F1F7FC.toInt()
                else -> 0xF70C1323.toInt()
            },
            borderColor = when {
                isTransparentMenuTheme() -> 0x61788594
                isLightMenuTheme() -> 0x9DBFD0E3.toInt()
                else -> 0x96354563.toInt()
            },
            borderWidthPx = 1f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = menuGlow(accentColor(), radiusPx = 18f, strength = if (isLightMenuTheme()) 0.06f else 0.10f, opacity = if (isLightMenuTheme()) 0.04f else 0.06f),
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0A000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0AD0DDED)
            } else {
                SdfShadeStyle(0x0BFFFFFF, 0x12000000)
            },
            neonBorder = menuNeonBorder(VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x36 else 0x3E), widthPx = 0.9f, softnessPx = 4f, strength = if (isLightMenuTheme()) 0.22f else 0.28f),
        )
    }

    private fun settingsStyle(): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x4A14171C
                isLightMenuTheme() -> 0xE8EFF6FC.toInt()
                else -> 0xF70D1627.toInt()
            },
            borderColor = when {
                isTransparentMenuTheme() -> 0x64798695
                isLightMenuTheme() -> 0xA2C0D0E4.toInt()
                else -> 0xA13A4A69.toInt()
            },
            borderWidthPx = 1.2f,
            radiusPx = Theme.sectionRadius,
            innerGlow = SdfGlowStyle(accentStrongColor(), radiusPx = 12f, strength = 0.05f, opacity = 0.04f),
            outerGlow = menuGlow(accentStrongColor(), radiusPx = 24f, strength = if (isLightMenuTheme()) 0.08f else 0.14f, opacity = if (isLightMenuTheme()) 0.05f else 0.08f),
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x06FFFFFF, 0x0C000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x08FFFFFF, 0x0CD1DDEB)
            } else {
                SdfShadeStyle(0x10FFFFFF, 0x14000000)
            },
            neonBorder = menuNeonBorder(VisualThemeSettings.withAlpha(neonBorderColor(), if (isLightMenuTheme()) 0x4A else 0x66), widthPx = 1.0f, softnessPx = 5f, strength = if (isLightMenuTheme()) 0.30f else 0.42f),
        )
    }

    private fun tabStyle(selected: Boolean, hovered: Boolean): SdfPanelStyle {
        val border = when {
            selected && isTransparentMenuTheme() -> blendColor(0xFF687382.toInt(), accentStrongColor(), 0.42f)
            hovered && isTransparentMenuTheme() -> blendColor(0xFF677382.toInt(), accentStrongColor(), 0.18f)
            isTransparentMenuTheme() -> 0xFF5A6578.toInt()
            selected -> accentStrongColor()
            hovered -> if (isLightMenuTheme()) blendColor(0xFF9FB9DE.toInt(), accentStrongColor(), 0.22f) else blendColor(0xFF536691.toInt(), accentStrongColor(), 0.35f)
            else -> if (isLightMenuTheme()) 0xFFC4D0E5.toInt() else 0xFF2E3B55.toInt()
        }
        val fill = when {
            selected && isTransparentMenuTheme() -> blendColor(0x721A2230, accentStrongColor(), 0.28f)
            hovered && isTransparentMenuTheme() -> 0x64222B39
            isTransparentMenuTheme() -> 0x54202936
            selected -> if (isLightMenuTheme()) 0xFFEFF5FF.toInt() else 0xFF1A2540.toInt()
            hovered -> if (isLightMenuTheme()) 0xFFF4F8FF.toInt() else 0xFF142034.toInt()
            else -> if (isLightMenuTheme()) 0xFFF8FAFE.toInt() else 0xFF101827.toInt()
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
                strength = if (isTransparentMenuTheme()) 0.03f else if (selected) 0.10f else 0.03f,
                opacity = if (isTransparentMenuTheme()) 0.02f else if (selected) 0.08f else 0.02f,
            ),
            outerGlow = if (selected) {
                menuGlow(accentStrongColor(), radiusPx = 18f, strength = 0.22f, opacity = glowOpacity)
            } else {
                if (isTransparentMenuTheme()) {
                    SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f)
                } else if (isLightMenuTheme()) {
                    SdfGlowStyle(0xFFDDE6F4.toInt(), radiusPx = 18f, strength = 0.08f, opacity = glowOpacity)
                } else {
                    SdfGlowStyle(0xFF000000.toInt(), radiusPx = 18f, strength = 0.12f, opacity = glowOpacity)
                }
            },
            shade = if (isTransparentMenuTheme()) {
                SdfShadeStyle(0x04FFFFFF, 0x10000000)
            } else if (isLightMenuTheme()) {
                SdfShadeStyle(0x08FFFFFF, 0x0ED4DEEE)
            } else {
                SdfShadeStyle(0x0EFFFFFF, 0x14000000)
            },
            neonBorder = menuNeonBorder(
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
            baseColor = if (isTransparentMenuTheme()) {
                blendColor(0x08161B24, 0x141E2635, emphasis)
            } else if (isLightMenuTheme()) {
                blendColor(0x00FFFFFF, 0x18DCE7F8, emphasis)
            } else {
                blendColor(0x08000000, 0x1A17264A, emphasis)
            },
            borderColor = 0x00000000,
            borderWidthPx = 0f,
            radiusPx = 22f,
            innerGlow = SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f),
            outerGlow = menuGlow(
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
            selected && isTransparentMenuTheme() -> blendColor(0x781A2230, accentStrongColor(), 0.24f)
            enabled && isTransparentMenuTheme() -> 0x6C1D2532
            hovered && isTransparentMenuTheme() -> 0x66212936
            isTransparentMenuTheme() -> 0x5A1D2531
            selected -> if (isLightMenuTheme()) 0xFFEFF5FF.toInt() else 0xFF18253E.toInt()
            enabled -> if (isLightMenuTheme()) 0xFFF4F8FF.toInt() else 0xFF152137.toInt()
            hovered -> if (isLightMenuTheme()) 0xFFF7FAFF.toInt() else 0xFF131F32.toInt()
            else -> if (isLightMenuTheme()) 0xFFF9FBFF.toInt() else 0xFF101829.toInt()
        }
        val border = when {
            selected && isTransparentMenuTheme() -> blendColor(0xFF697382.toInt(), accentStrongColor(), 0.34f)
            enabled && isTransparentMenuTheme() -> blendColor(0xFF5D697C.toInt(), accentStrongColor(), 0.18f)
            hovered && isTransparentMenuTheme() -> 0xFF647081.toInt()
            isTransparentMenuTheme() -> 0xFF566171.toInt()
            selected -> accentStrongColor()
            enabled -> if (isLightMenuTheme()) blendColor(0xFFB3C5E3.toInt(), accentStrongColor(), 0.26f) else blendColor(0xFF536B9E.toInt(), accentStrongColor(), 0.45f)
            hovered -> if (isLightMenuTheme()) 0xFFBFCDE2.toInt() else 0xFF415474.toInt()
            else -> if (isLightMenuTheme()) 0xFFC9D3E4.toInt() else 0xFF2C3953.toInt()
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
            outerGlow = if (selected) {
                menuGlow(accentStrongColor(), radiusPx = 14f, strength = 0.10f, opacity = 0.06f)
            } else {
                if (isTransparentMenuTheme()) SdfGlowStyle(0x00000000, radiusPx = 0f, strength = 0f, opacity = 0f)
                else if (isLightMenuTheme()) SdfGlowStyle(0xFFE0E8F4.toInt(), radiusPx = 14f, strength = 0.08f, opacity = 0.04f)
                else SdfGlowStyle(0xFF000000.toInt(), radiusPx = 14f, strength = 0.10f, opacity = 0.02f)
            },
            shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0ED4DFEE) else SdfShadeStyle(0x12FFFFFF, 0x16000000),
            neonBorder = menuNeonBorder(
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
            baseColor = if (hovered) {
                if (isTransparentMenuTheme()) 0x68222A38
                else if (isLightMenuTheme()) 0xFFF2F6FD.toInt() else 0xFF152138.toInt()
            } else {
                if (isTransparentMenuTheme()) 0x601E2633
                else if (isLightMenuTheme()) 0xFFF7F9FE.toInt() else 0xFF111A2D.toInt()
            },
            borderColor = if (hovered) {
                if (isTransparentMenuTheme()) blendColor(0xFF667282.toInt(), accentStrongColor(), 0.18f)
                else if (isLightMenuTheme()) blendColor(0xFFBCCCE3.toInt(), accentStrongColor(), 0.14f) else blendColor(0xFF4B5D85.toInt(), accentStrongColor(), 0.25f)
            } else {
                if (isTransparentMenuTheme()) 0xFF5B6777.toInt()
                else if (isLightMenuTheme()) 0xFFC7D2E5.toInt() else 0xFF33415F.toInt()
            },
            borderWidthPx = 1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = menuGlow(accentStrongColor(), radiusPx = 18f, strength = 0.12f, opacity = if (hovered) 0.07f else 0.04f),
            shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0ED5E0EF) else SdfShadeStyle(0x0EFFFFFF, 0x14000000),
            neonBorder = menuNeonBorder(
                color = if (hovered) VisualThemeSettings.withAlpha(neonBorderColor(), 0x88) else VisualThemeSettings.withAlpha(neonBorderColor(), 0x30),
                widthPx = if (hovered) 1.0f else 0.8f,
                softnessPx = if (hovered) 5f else 4f,
                strength = if (hovered) 0.54f else 0.28f,
            ),
        )
    }

    private fun inputRowStyle(invalid: Boolean = false, themeColorRow: Boolean = false, themeColor: Int? = null): SdfPanelStyle {
        val borderColor = if (invalid) 0xFF7B3A44.toInt() else if (isTransparentMenuTheme()) 0xFF5A6678.toInt() else 0xFF33415F.toInt()
        val neonColor = if (invalid) 0xA0FF7A7A.toInt() else VisualThemeSettings.withAlpha(neonBorderColor(), if (themeColorRow) 0x20 else 0x42)
        val glowColor = if (invalid) 0xFFFF7676.toInt() else accentStrongColor()
        return SdfPanelStyle(
            baseColor = when {
                isTransparentMenuTheme() -> 0x621E2633
                isLightMenuTheme() -> 0xFFF7F9FE.toInt()
                else -> 0xFF111A2D.toInt()
            },
            borderColor = if (isLightMenuTheme() && !invalid) 0xFFC7D2E5.toInt() else borderColor,
            borderWidthPx = 1f,
            radiusPx = 14f,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 8f, strength = 0.02f, opacity = 0.02f),
            outerGlow = menuGlow(glowColor, radiusPx = if (themeColorRow && !invalid) 16f else 18f, strength = if (themeColorRow && !invalid) 0.08f else 0.10f, opacity = if (invalid) 0.08f else if (themeColorRow) 0.03f else 0.05f),
            shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0ED5E0EF) else SdfShadeStyle(0x0AFFFFFF, 0x12000000),
            neonBorder = menuNeonBorder(neonColor, widthPx = if (themeColorRow && !invalid) 0.82f else 0.9f, softnessPx = if (themeColorRow && !invalid) 3.8f else 4.5f, strength = if (invalid) 0.48f else if (themeColorRow) 0.14f else 0.30f),
        )
    }

    private fun drawToggle(
        context: GuiGraphics,
        rect: IntRect,
        enabled: Boolean,
        animationKey: String,
        clipRect: SdfPanelRenderer.ClipRect? = null,
    ) {
        val target = if (enabled) 1f else 0f
        val current = toggleProgressByKey[animationKey] ?: target
        val next = current + (target - current) * 0.26f
        val settled = if (abs(target - next) < 0.002f) target else next
        toggleProgressByKey[animationKey] = settled

        val trackFill = if (isTransparentMenuTheme()) {
            blendColor(0x66202836, VisualThemeSettings.withAlpha(toggleFillColor(), 0xD6), settled)
        } else if (isLightMenuTheme()) {
            blendColor(0xFFE2EAF6.toInt(), VisualThemeSettings.withAlpha(toggleFillColor(), 0xE8), settled)
        } else {
            blendColor(0xC617202F.toInt(), VisualThemeSettings.withAlpha(toggleFillColor(), 0xD6), settled)
        }
        val brightTrackBorder = blendColor(boostedNeonColor(neonBorderColor()), accentStrongColor(), 0.28f)
        val trackBorder = if (VisualThemeSettings.themeAllowsNeon()) {
            if (isTransparentMenuTheme()) {
                blendColor(0xFF687282.toInt(), brightTrackBorder, 0.18f + (0.22f * settled))
            } else if (isLightMenuTheme()) {
                blendColor(0xFFBCC9DE.toInt(), brightTrackBorder, 0.18f + (0.38f * settled))
            } else {
                blendColor(0xFF42506B.toInt(), brightTrackBorder, 0.30f + (0.58f * settled))
            }
        } else {
            if (isTransparentMenuTheme()) blendColor(0xFF647080.toInt(), 0xFF546071.toInt(), 0.18f + (0.20f * settled))
            else if (isLightMenuTheme()) blendColor(0xFFD4DDEC.toInt(), 0xFFB8C4D8.toInt(), 0.18f + (0.20f * settled))
            else blendColor(0x7A394866, 0xFF46536E.toInt(), 0.18f + (0.20f * settled))
        }
        val trackGlowColor = blendColor(accentStrongColor(), boostedNeonColor(neonBorderColor()), 0.46f)
        SdfPanelRenderer.draw(
            context,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            SdfPanelStyle(
                baseColor = trackFill,
                borderColor = trackBorder,
                borderWidthPx = 1f,
                radiusPx = rect.height / 2f,
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.03f, opacity = 0.02f),
                outerGlow = menuGlow(
                    trackGlowColor,
                    radiusPx = 12f,
                    strength = 0.18f + (0.10f * settled),
                    opacity = 0.06f + (0.12f * settled),
                ),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x10000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0ED4DFEE) else SdfShadeStyle(0x0EFFFFFF, 0x12000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(boostedNeonColor(neonBorderColor()), (0x48 + (0x7A * settled).toInt()).coerceIn(0, 255)),
                    widthPx = 0.95f + (0.18f * settled),
                    softnessPx = 4.8f + (1.8f * settled),
                    strength = 0.28f + (0.42f * settled),
                ),
            ),
            clipRect,
        )

        val knobRadius = ((rect.height - 6) / 2).coerceAtLeast(4)
        val minCenterX = rect.x + knobRadius + 3
        val maxCenterX = rect.x + rect.width - knobRadius - 3
        val knobCenterX = (minCenterX + (maxCenterX - minCenterX) * settled).toInt()
        val knobCenterY = rect.y + rect.height / 2

        SdfPanelRenderer.draw(
            context,
            knobCenterX - knobRadius,
            knobCenterY - knobRadius,
            knobRadius * 2,
            knobRadius * 2,
            SdfPanelStyle(
                baseColor = if (isTransparentMenuTheme()) {
                    blendColor(0xFFF9FBFF.toInt(), sliderKnobColor(), 0.52f)
                } else if (isLightMenuTheme()) {
                    blendColor(0xFFFDFEFF.toInt(), sliderKnobColor(), 0.65f)
                } else {
                    sliderKnobColor()
                },
                borderColor = if (VisualThemeSettings.themeAllowsNeon()) {
                    blendColor(sliderKnobColor(), brightTrackBorder, 0.44f)
                } else {
                    blendColor(sliderKnobColor(), accentStrongColor(), 0.28f)
                },
                borderWidthPx = 1f,
                radiusPx = knobRadius.toFloat(),
                innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 6f, strength = 0.06f, opacity = 0.05f),
                outerGlow = menuGlow(
                    blendColor(accentStrongColor(), boostedNeonColor(neonBorderColor()), 0.34f),
                    radiusPx = 10f,
                    strength = 0.12f + (0.10f * settled),
                    opacity = 0.04f + (0.10f * settled),
                ),
                shade = if (isTransparentMenuTheme()) SdfShadeStyle(0x04FFFFFF, 0x08000000) else if (isLightMenuTheme()) SdfShadeStyle(0x08FFFFFF, 0x0ACEDAE9) else SdfShadeStyle(0x10FFFFFF, 0x08000000),
                neonBorder = menuNeonBorder(
                    VisualThemeSettings.withAlpha(boostedNeonColor(neonBorderColor()), (0x34 + (0x88 * settled).toInt()).coerceIn(0, 255)),
                    widthPx = 0.82f + (0.20f * settled),
                    softnessPx = 4f + (1.6f * settled),
                    strength = 0.22f + (0.42f * settled),
                ),
            ),
            clipRect,
        )
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val layout = computeLayout()
        val sliderRow = activeSliderKey?.let { key ->
            settingSliderRows(layout).firstOrNull { it.key == key }
        }
        if (mouseButtonEvent.button() == 0 && sliderRow != null) {
            updateSliderValue(sliderRow, mouseButtonEvent.x())
            return true
        }
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
        if (mouseButtonEvent.button() == 0 && activeSliderKey != null) {
            activeSliderKey = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && pickerDragMode != null) {
            pickerDragMode = null
            return true
        }
        return super.mouseReleased(mouseButtonEvent)
    }
}
