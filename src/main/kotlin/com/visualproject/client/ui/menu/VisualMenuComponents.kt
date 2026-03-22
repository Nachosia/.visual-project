package com.visualproject.client.ui.menu

import com.daqem.uilib.api.widget.IWidget
import com.daqem.uilib.gui.component.AbstractComponent
import com.daqem.uilib.gui.component.text.TextComponent
import com.daqem.uilib.gui.component.text.TruncatedTextComponent
import com.daqem.uilib.gui.widget.EditBoxWidget
import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualClientMod
import com.visualproject.client.VisualMenuDock
import com.visualproject.client.VisualMenuModuleEntry
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.abs

internal class DecorativeComponent(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val painter: (GuiGraphics, Int, Int, Int, Int) -> Unit,
) : AbstractComponent(x, y, width, height) {

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) {
        painter(context, totalX, totalY, width, height)
    }
}

internal class SpacerComponent(
    width: Int,
    height: Int,
) : AbstractComponent(0, 0, width, height) {

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) = Unit
}

internal class SearchBarComponent(
    x: Int,
    y: Int,
    width: Int,
    font: Font,
    initialQuery: String,
    onChanged: (String) -> Unit,
) : AbstractComponent(x, y, width, VisualMenuTheme.searchHeight) {

    val input = EditBoxWidget(font, 30, 6, width - 48, VisualMenuTheme.searchHeight - 12, Component.empty())

    init {
        input.setBordered(false)
        input.setTextColor(0xFFBBC3E0.toInt())
        input.setTextColorUneditable(0xFF7E88A6.toInt())
        input.setHint(vText("Поиск"))
        input.setValue(initialQuery)
        input.setResponder { value -> onChanged(value.trim()) }
        addWidget(input)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) {
        drawRoundedPanel(
            context,
            totalX,
            totalY,
            width,
            height,
            VisualMenuTheme.searchFill,
            VisualMenuTheme.searchBorder,
            VisualMenuTheme.searchRadius,
        )

        val font = Minecraft.getInstance().font
        context.drawString(font, vText(VisualMenuTheme.searchIconLeft), totalX + 10, totalY + 8, 0xFF7B829F.toInt(), false)
        context.drawString(font, vText(VisualMenuTheme.searchIconRight), totalX + width - 18, totalY + 8, 0xFF6E7591.toInt(), false)
    }
}

internal class TabChipWidget(
    x: Int,
    y: Int,
    private val label: String,
    private var selected: Boolean,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, Minecraft.getInstance().font.width(vText(label)) + 16, VisualMenuTheme.tabHeight, Component.empty()), IWidget {

    fun setActiveState(active: Boolean) {
        this.selected = active
    }

    override fun getRectangle(): ScreenRectangle = ScreenRectangle(x, y, width, height)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isHoveredOrFocused

        val backgroundFill = when {
            selected -> 0x4D2B3C66
            hovered -> 0x2A1A253D
            else -> 0x00000000
        }
        val backgroundBorder = when {
            selected -> 0xAA6479B5.toInt()
            hovered -> 0x7A3E4B70
            else -> 0x002A3247
        }
        if (selected || hovered) {
            drawRoundedPanel(context, x, y, width, height, backgroundFill, backgroundBorder, 10)
        }

        val color = when {
            selected -> VisualMenuTheme.textPrimary
            hovered -> VisualMenuTheme.textSecondary
            else -> VisualMenuTheme.textDim
        }

        val font = Minecraft.getInstance().font
        val textY = y + ((height - font.lineHeight) / 2)
        context.drawString(font, vText(label), x + 7, textY, color, false)

        if (selected) {
            context.fill(x + 6, y + height - 2, x + width - 6, y + height - 1, VisualMenuTheme.accent)
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!selected && mouseButtonEvent.button() == 0 && isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            onPress.invoke()
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }
        return false
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) = Unit
}

internal class DockButtonWidget(
    x: Int,
    y: Int,
    private val dock: VisualMenuDock,
    private var selected: Boolean,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, 44, 34, Component.empty()), IWidget {

    fun setActiveState(active: Boolean) {
        this.selected = active
    }

    override fun getRectangle(): ScreenRectangle = ScreenRectangle(x, y, width, height)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isHoveredOrFocused

        val border = when {
            selected -> VisualMenuTheme.accentStrong
            hovered -> 0x7A4B5E89
            else -> 0x4B35435F
        }
        val fill = when {
            selected -> 0xFF5D4AC6.toInt()
            hovered -> 0x7A1C2942
            else -> 0x4D131B2E
        }

        drawRoundedPanel(context, x, y, width, height, fill, border, 12)

        val font = Minecraft.getInstance().font
        val color = if (selected) 0xFFF9F6FF.toInt() else 0xFF95A1C4.toInt()
        val textWidth = font.width(vText(dock.icon))
        context.drawString(font, vText(dock.icon), x + (width - textWidth) / 2, y + (height - font.lineHeight) / 2 - 1, color, false)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (mouseButtonEvent.button() == 0 && isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            onPress.invoke()
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }
        return false
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) = Unit
}

internal class ModuleRowComponent(
    x: Int,
    width: Int,
    cardWidth: Int,
    leftModule: VisualMenuModuleEntry,
    rightModule: VisualMenuModuleEntry?,
    hoverProgressById: MutableMap<String, Float>,
    toggleProgressById: MutableMap<String, Float>,
    isOpened: (String) -> Boolean,
    onToggle: (VisualMenuModuleEntry) -> Unit,
    onOpenSettings: (VisualMenuModuleEntry) -> Unit,
) : AbstractComponent(x, 0, width, VisualMenuTheme.moduleRowHeight) {

    init {
        addWidget(
            ModuleCardWidget(
                module = leftModule,
                x = 0,
                y = (VisualMenuTheme.moduleRowHeight - VisualMenuTheme.moduleCardHeight) / 2,
                width = cardWidth,
                hoverProgressById = hoverProgressById,
                toggleProgressById = toggleProgressById,
                isOpened = isOpened,
                onToggle = onToggle,
                onOpenSettings = onOpenSettings,
            )
        )

        if (rightModule != null) {
            addWidget(
                ModuleCardWidget(
                    module = rightModule,
                    x = cardWidth + VisualMenuTheme.moduleColumnGap,
                    y = (VisualMenuTheme.moduleRowHeight - VisualMenuTheme.moduleCardHeight) / 2,
                    width = cardWidth,
                    hoverProgressById = hoverProgressById,
                    toggleProgressById = toggleProgressById,
                    isOpened = isOpened,
                    onToggle = onToggle,
                    onOpenSettings = onOpenSettings,
                )
            )
        }
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) = Unit
}

internal class ModuleSettingsPanelComponent(
    width: Int,
    module: VisualMenuModuleEntry,
    onSettingChanged: (String, Boolean) -> Unit,
    onNumberChanged: (String, Float) -> Unit,
    onTextChanged: (String, String) -> Unit,
) : AbstractComponent(VisualMenuTheme.moduleRowsSideInset, 0, width, computeSettingsPanelHeight(module)) {

    private sealed class SettingRow(val key: String, val label: String, val rowHeight: Int) {
        class Toggle(key: String, label: String) : SettingRow(key, label, 24)

        class Input(
            key: String,
            label: String,
            val hint: String,
        ) : SettingRow(key, label, 42)
    }

    init {
        val title = TruncatedTextComponent(10, 10, width - 24, vText("Settings - ${module.title}"), VisualMenuTheme.textSecondary)
        title.setDrawShadow(false)
        addComponent(title)

        var currentY = 40
        settingsForModule(module).forEach { setting ->
            when (setting) {
                is SettingRow.Toggle -> {
                    val label = TextComponent(10, currentY + 4, vText(setting.label), VisualMenuTheme.textMuted)
                    label.setDrawShadow(false)
                    addComponent(label)

                    addWidget(
                        InlineSwitchWidget(
                            x = width - 42,
                            y = currentY,
                            initialValue = ModuleStateStore.isSettingEnabled(setting.key),
                            onToggle = { onSettingChanged(setting.key, it) },
                        )
                    )
                }

                is SettingRow.Input -> {
                    addComponent(
                        SettingInputFieldComponent(
                            x = 10,
                            y = currentY,
                            width = width - 20,
                            label = setting.label,
                            initialValue = initialInputValue(setting),
                            hint = setting.hint,
                            onChanged = { _ -> },
                        )
                    )
                }
            }

            currentY += setting.rowHeight
        }
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) {
        drawRoundedPanel(
            context,
            totalX,
            totalY,
            width,
            height,
            VisualMenuTheme.settingsFill,
            VisualMenuTheme.settingsBorder,
            VisualMenuTheme.settingsRadius,
        )
    }

    companion object {
        private fun settingsForModule(module: VisualMenuModuleEntry): List<SettingRow> {
            val rows = mutableListOf<SettingRow>(
                SettingRow.Toggle("${module.id}:visible_hud", "Visible In HUD"),
                SettingRow.Toggle("${module.id}:accent_sync", "Accent Sync"),
            )
            if (module.id == VisualClientMod.sdfTestModuleId) {
                rows += SettingRow.Toggle("${module.id}:outer_glow", "Outer Glow")
            }
            return rows
        }

        private fun computeSettingsPanelHeight(module: VisualMenuModuleEntry): Int {
            return 44 + settingsForModule(module).sumOf { it.rowHeight }
        }

        private fun initialInputValue(setting: SettingRow.Input): String {
            return ""
        }

        private fun formatFloat(value: Float): String {
            return if (value == value.toInt().toFloat()) {
                value.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", value)
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
    }
}

internal class SettingInputFieldComponent(
    x: Int,
    y: Int,
    width: Int,
    private val label: String,
    initialValue: String,
    hint: String,
    onChanged: (String) -> Unit,
) : AbstractComponent(x, y, width, 38) {

    private val input = EditBoxWidget(
        Minecraft.getInstance().font,
        9,
        18,
        width - 18,
        18,
        Component.empty(),
    )

    init {
        input.setBordered(false)
        input.setTextColor(0xFFDEE5FF.toInt())
        input.setTextColorUneditable(0xFF7E88A6.toInt())
        input.setHint(vText(hint))
        input.setValue(initialValue)
        input.setResponder { value -> onChanged(value.trim()) }
        addWidget(input)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) {
        context.drawString(
            Minecraft.getInstance().font,
            vText(label),
            totalX,
            totalY + 2,
            VisualMenuTheme.textMuted,
            false,
        )

        drawRoundedPanel(
            context,
            totalX,
            totalY + 16,
            width,
            20,
            VisualMenuTheme.settingInputFill,
            VisualMenuTheme.settingInputBorder,
            10,
        )
    }
}

internal class EmptyStateComponent(
    width: Int,
) : AbstractComponent(VisualMenuTheme.moduleRowsSideInset, 0, width, VisualMenuTheme.moduleCardHeight) {

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, parentWidth: Int, parentHeight: Int) {
        drawRoundedPanel(
            context,
            totalX,
            totalY,
            width,
            height,
            VisualMenuTheme.cardFill,
            VisualMenuTheme.cardBorder,
            VisualMenuTheme.cardRadius,
        )
        context.drawString(
            Minecraft.getInstance().font,
            vText("Ничего не найдено"),
            totalX + 14,
            totalY + (height - Minecraft.getInstance().font.lineHeight) / 2,
            0xFF7D86A6.toInt(),
            false,
        )
    }
}

internal class ModuleCardWidget(
    private val module: VisualMenuModuleEntry,
    x: Int,
    y: Int,
    width: Int,
    private val hoverProgressById: MutableMap<String, Float>,
    private val toggleProgressById: MutableMap<String, Float>,
    private val isOpened: (String) -> Boolean,
    private val onToggle: (VisualMenuModuleEntry) -> Unit,
    private val onOpenSettings: (VisualMenuModuleEntry) -> Unit,
) : AbstractWidget(x, y, width, VisualMenuTheme.moduleCardHeight, Component.empty()), IWidget {

    override fun getRectangle(): ScreenRectangle = ScreenRectangle(x, y, width, height)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isHoveredOrFocused
        val enabled = ModuleStateStore.isEnabled(module.id)
        val expanded = isOpened(module.id)

        val targetHover = if (hovered || expanded) 1f else 0f
        val currentHover = hoverProgressById[module.id] ?: 0f
        val nextHover = currentHover + (targetHover - currentHover) * 0.28f
        hoverProgressById[module.id] = if (abs(nextHover - targetHover) < 0.001f) targetHover else nextHover

        val baseFill = if (enabled) VisualMenuTheme.cardEnabledFill else VisualMenuTheme.cardFill
        val baseBorder = if (enabled) VisualMenuTheme.cardEnabledBorder else VisualMenuTheme.cardBorder
        val hoverFill = if (expanded) VisualMenuTheme.cardExpandedFill else VisualMenuTheme.cardHoverFill
        val hoverBorder = if (expanded) VisualMenuTheme.cardExpandedBorder else VisualMenuTheme.cardHoverBorder
        val fill = blendColor(baseFill, hoverFill, nextHover)
        val border = blendColor(baseBorder, hoverBorder, nextHover)

        drawRoundedPanel(context, x, y, width, height, fill, border, VisualMenuTheme.cardRadius)

        val cardHighlightInset = (VisualMenuTheme.cardRadius * 0.55f).toInt().coerceAtLeast(8)
        val highlightStrength = ((if (enabled) 0.16f else 0.04f) + (nextHover * 0.24f)).coerceIn(0f, 0.38f)
        val highlightStart = blendColor(0x00000000, 0x24F4F7FF, highlightStrength)
        val highlightMid = blendColor(0x00000000, 0x12D6DDF8, highlightStrength)
        drawVerticalGradient(
            context,
            x + cardHighlightInset,
            y + 2,
            (width - (cardHighlightInset * 2)).coerceAtLeast(2),
            5,
            highlightStart,
            highlightMid,
        )

        val slotSize = 22
        val slotX = x + 10
        val slotY = y + (height - slotSize) / 2
        drawRoundedPanel(context, slotX, slotY, slotSize, slotSize, VisualMenuTheme.iconSlotFill, VisualMenuTheme.iconSlotBorder, VisualMenuTheme.iconSlotRadius)

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

        val titleX = slotX + slotSize + 9
        val toggleX = x + width - VisualMenuTheme.toggleWidth - VisualMenuTheme.toggleRightInset
        val titleMaxWidth = (toggleX - 10 - titleX).coerceAtLeast(24)
        val fittedTitle = fitStyledText(font, module.title, titleMaxWidth)
        context.drawString(
            font,
            fittedTitle,
            titleX,
            y + (height - font.lineHeight) / 2 - 1,
            if (enabled) VisualMenuTheme.textPrimary else VisualMenuTheme.textSecondary,
            false,
        )

        val toggleY = y + (height - VisualMenuTheme.toggleHeight) / 2
        val trackBorder = if (enabled) 0xFF8B73F8.toInt() else 0x7A394866
        val trackFill = if (enabled) 0xC85F4FBE.toInt() else 0xAA1B2438.toInt()
        drawRoundedPanel(
            context,
            toggleX,
            toggleY,
            VisualMenuTheme.toggleWidth,
            VisualMenuTheme.toggleHeight,
            trackFill,
            trackBorder,
            VisualMenuTheme.toggleHeight / 2,
        )

        val target = if (enabled) 1f else 0f
        val current = toggleProgressById[module.id] ?: target
        val next = current + (target - current) * 0.26f
        val settled = if (abs(target - next) < 0.002f) target else next
        toggleProgressById[module.id] = settled

        val knobRadius = ((VisualMenuTheme.toggleHeight - 6) / 2).coerceAtLeast(4)
        val minCenterX = toggleX + knobRadius + 3
        val maxCenterX = toggleX + VisualMenuTheme.toggleWidth - knobRadius - 3
        val knobCenterX = (minCenterX + (maxCenterX - minCenterX) * settled).toInt()
        val knobCenterY = toggleY + VisualMenuTheme.toggleHeight / 2

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

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!visible || !active || !isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) return false

        return when (mouseButtonEvent.button()) {
            1 -> {
                onOpenSettings.invoke(module)
                playDownSound(Minecraft.getInstance().soundManager)
                true
            }
            0 -> {
                onToggle.invoke(module)
                playDownSound(Minecraft.getInstance().soundManager)
                true
            }
            else -> false
        }
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) = Unit
}

internal class InlineSwitchWidget(
    x: Int,
    y: Int,
    initialValue: Boolean,
    private val onToggle: (Boolean) -> Unit,
) : AbstractWidget(x, y, 30, 16, Component.empty()), IWidget {

    private var enabledValue = initialValue

    fun setEnabledValue(enabledValue: Boolean) {
        this.enabledValue = enabledValue
    }

    override fun getRectangle(): ScreenRectangle = ScreenRectangle(x, y, width, height)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val trackBorder = if (enabledValue) 0xFF8E73FD.toInt() else 0x7A3D4A68
        val trackFill = if (enabledValue) 0xC45747B6.toInt() else 0x7A1A2436
        drawRoundedPanel(context, x, y, width, height, trackFill, trackBorder, height / 2)

        val knobRadius = ((height - 6) / 2).coerceAtLeast(3)
        val knobCenterX = if (enabledValue) x + width - knobRadius - 3 else x + knobRadius + 3
        val knobCenterY = y + height / 2

        if (enabledValue) {
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

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (mouseButtonEvent.button() == 0 && isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            enabledValue = !enabledValue
            onToggle.invoke(enabledValue)
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }
        return false
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) = Unit
}

