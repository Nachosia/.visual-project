package com.visualproject.client.hud.cooldowns

import com.visualproject.client.ModuleStateStore
import com.visualproject.client.VisualThemeSettings
import com.visualproject.client.render.sdf.SdfGlowStyle
import com.visualproject.client.render.sdf.SdfNeonBorderStyle
import com.visualproject.client.render.sdf.SdfPanelRenderer
import com.visualproject.client.render.sdf.SdfPanelStyle
import com.visualproject.client.render.sdf.SdfShadeStyle
import com.visualproject.client.ui.menu.blendColor
import com.visualproject.client.vText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemCooldowns
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

internal class CooldownsHudRenderer {

    companion object {
        private const val moduleId = "cooldowns_hud"
    }

    private object Layout {
        const val shellWidth = 178
        const val shellRadius = 17f
        const val padding = 8
        const val rowHeight = 16
        const val rowGap = 4
        const val iconSize = 14
        const val iconGap = 6
        const val timeRightInset = 8
        const val anchorX = 12
        const val anchorY = 94
    }

    private data class CooldownEntry(
        val stack: ItemStack,
        val label: String,
        val durationText: String,
        val remainingTicks: Int,
    )

    private var dragState: CooldownsHudDragState? = null
    private var lastBounds: CooldownsHudBounds? = null

    private var reflectionFailureLogged = false
    private var lastScale = -1f

    fun render(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        if (client.options.hideGui) return
        if (client.screen != null && client.screen !is ChatScreen) return
        val scale = hudScale()

        val entries = activeEntries(player)
        if (entries.isEmpty() && client.screen !is ChatScreen) {
            return
        }

        val visibleEntries = if (entries.isNotEmpty()) entries else previewEntries()
        val baseShellHeight = shellHeightFor(visibleEntries.size)

        val state = ensureDragState(client)
        adjustPositionForScaleChange(client, state, Layout.shellWidth, baseShellHeight, scale)
        val actualWidth = scaled(Layout.shellWidth, scale)
        val actualHeight = scaled(baseShellHeight, scale)
        clampToScreen(client, state, actualWidth, actualHeight)
        val bounds = CooldownsHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = actualWidth,
            height = actualHeight,
        )
        lastBounds = bounds

        val accentSync = ModuleStateStore.isSettingEnabled("${moduleId}:accent_sync")
        val neonColor = if (accentSync) VisualThemeSettings.neonBorder() else 0xFF7A2730.toInt()
        val glowColor = if (accentSync) VisualThemeSettings.themedAccentGlowBase() else VisualThemeSettings.themedFallbackGlow(0xFF4A171D.toInt())

        context.pose().pushMatrix()
        context.pose().translate(bounds.x.toFloat(), bounds.y.toFloat())
        context.pose().scale(scale, scale)

        SdfPanelRenderer.draw(
            context = context,
            x = 0,
            y = 0,
            width = Layout.shellWidth,
            height = baseShellHeight,
            style = shellStyle(glowColor, neonColor),
        )

        visibleEntries.forEachIndexed { index, entry ->
            val rowY = Layout.padding + (index * (Layout.rowHeight + Layout.rowGap))
            drawCooldownRow(context, client.font, Layout.padding, rowY, entry)
        }

        context.pose().popMatrix()
    }

    fun onScreenMouseClick(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val scale = hudScale()
        val state = ensureDragState(client)
        val bounds = lastBounds ?: CooldownsHudBounds(
            x = state.position.x,
            y = state.position.y,
            width = scaled(Layout.shellWidth, scale),
            height = scaled(shellHeightFor(3), scale),
        )
        val handled = state.beginDrag(bounds, mouseEvent.x().toInt(), mouseEvent.y().toInt())
        return consumed || handled
    }

    fun onScreenMouseDrag(
        client: Minecraft,
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        horizontalAmount: Double,
        verticalAmount: Double,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = dragState ?: return consumed
        if (!state.dragging) return consumed

        val scale = hudScale()
        val hudHeight = lastBounds?.height ?: scaled(shellHeightFor(3), scale)
        state.dragTo(
            mouseX = mouseEvent.x().toInt(),
            mouseY = mouseEvent.y().toInt(),
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = scaled(Layout.shellWidth, scale),
            hudHeight = hudHeight,
        )
        return true
    }

    fun onScreenMouseRelease(
        screen: Screen,
        mouseEvent: MouseButtonEvent,
        consumed: Boolean,
    ): Boolean {
        if (screen !is ChatScreen) return consumed
        if (mouseEvent.button() != 0) return consumed

        val state = dragState ?: return consumed
        val ended = state.endDrag()
        if (ended) {
            CooldownsHudPositionStore.save(state.position)
        }
        return consumed || ended
    }

    private fun drawCooldownRow(
        context: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        entry: CooldownEntry,
    ) {
        val iconX = x
        val iconY = y
        val textX = iconX + Layout.iconSize + Layout.iconGap
        val timeTextWidth = font.width(vText(entry.durationText))
        val timeX = x + Layout.shellWidth - (Layout.padding * 2) - Layout.timeRightInset - timeTextWidth
        val nameMaxWidth = (timeX - textX - 8).coerceAtLeast(24)

        drawItemIcon(context, entry.stack, iconX, iconY)

        val label = entry.label.takeIf { font.width(vText(it)) <= nameMaxWidth }
            ?: font.substrByWidth(vText(entry.label), nameMaxWidth).string
        context.drawString(font, vText(label), textX, y + 3, VisualThemeSettings.textPrimary(), false)
        context.drawString(font, vText(entry.durationText), timeX, y + 3, VisualThemeSettings.textPrimary(), false)
    }

    private fun drawItemIcon(
        context: GuiGraphics,
        stack: ItemStack,
        x: Int,
        y: Int,
    ) {
        if (stack.isEmpty) {
            return
        }
        context.renderItem(stack, x + ((Layout.iconSize - 16) / 2), y + ((Layout.iconSize - 16) / 2))
    }

    private fun activeEntries(player: Player): List<CooldownEntry> {
        val cooldowns = player.cooldowns
        val activeMap = readCooldownMap(cooldowns) ?: return emptyList()
        val tickCount = readTickCount(cooldowns) ?: return emptyList()
        val inventoryStacks = collectCandidateStacks(player)

        return activeMap.entries
            .mapNotNull { (groupId, instance) ->
                val endTick = readEndTick(instance) ?: return@mapNotNull null
                val remainingTicks = endTick - tickCount
                if (remainingTicks <= 0) return@mapNotNull null

                val stack = inventoryStacks.firstOrNull { stack ->
                    !stack.isEmpty && stackCooldownGroup(cooldowns, stack) == groupId
                } ?: fallbackStack(groupId)

                CooldownEntry(
                    stack = stack,
                    label = cooldownLabel(stack, groupId),
                    durationText = remainingText(remainingTicks),
                    remainingTicks = remainingTicks,
                )
            }
            .sortedBy { it.remainingTicks }
    }

    private fun previewEntries(): List<CooldownEntry> {
        return listOf(
            CooldownEntry(ItemStack(Items.ENDER_PEARL), "Ender Pearl", "5.3", 106),
            CooldownEntry(ItemStack(Items.CHORUS_FRUIT), "Chorus Fruit", "12", 240),
            CooldownEntry(ItemStack(Items.GOAT_HORN), "Goat Horn", "1:04", 1280),
        )
    }

    private fun collectCandidateStacks(player: Player): List<ItemStack> {
        val inventory = player.inventory
        return buildList {
            for (slot in 0 until inventory.containerSize) {
                val stack = inventory.getItem(slot)
                if (!stack.isEmpty) {
                    add(stack)
                }
            }
        }
    }

    private fun cooldownLabel(stack: ItemStack, groupId: Identifier): String {
        if (!stack.isEmpty) return stack.hoverName.string
        return groupId.path
            .split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            .ifBlank { "Cooldown" }
    }

    private fun remainingText(remainingTicks: Int): String {
        val seconds = remainingTicks / 20f
        if (seconds < 10f) {
            val tenths = floor(seconds * 10f).coerceAtLeast(0f) / 10f
            return String.format(Locale.US, "%.1f", tenths)
        }

        val roundedSeconds = kotlin.math.ceil(seconds.toDouble()).toInt().coerceAtLeast(0)
        return if (roundedSeconds >= 60) {
            "%d:%02d".format(roundedSeconds / 60, roundedSeconds % 60)
        } else {
            roundedSeconds.toString()
        }
    }

    private fun fallbackStack(groupId: Identifier): ItemStack {
        return runCatching {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(groupId)) {
                ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(groupId))
            } else {
                ItemStack.EMPTY
            }
        }.getOrDefault(ItemStack.EMPTY)
    }

    private fun readCooldownMap(cooldowns: ItemCooldowns): Map<Identifier, Any>? {
        val field = findField(cooldowns.javaClass, "cooldowns", "field_8024") ?: return null.also { logReflectionFailure() }
        val raw = runCatching { field.get(cooldowns) }.getOrNull() as? Map<*, *> ?: return null.also { logReflectionFailure() }
        return raw.entries
            .mapNotNull { entry ->
                val key = entry.key as? Identifier ?: return@mapNotNull null
                val value = entry.value ?: return@mapNotNull null
                key to value
            }
            .toMap()
    }

    private fun readTickCount(cooldowns: ItemCooldowns): Int? {
        val field = findField(cooldowns.javaClass, "tickCount", "field_8025") ?: return null.also { logReflectionFailure() }
        return runCatching { field.getInt(cooldowns) }.getOrNull()
    }

    private fun readEndTick(instance: Any): Int? {
        findField(instance.javaClass, "endTime", "comp_3084")?.let { field ->
            return runCatching { field.getInt(instance) }.getOrNull()
        }
        findMethod(instance.javaClass, arrayOf("endTime", "comp_3084"))?.let { method ->
            return runCatching { method.invoke(instance) as? Int }.getOrNull()
        }
        logReflectionFailure()
        return null
    }

    private fun stackCooldownGroup(cooldowns: ItemCooldowns, stack: ItemStack): Identifier? {
        val method = findMethod(cooldowns.javaClass, arrayOf("getCooldownGroup", "method_62836"), ItemStack::class.java)
            ?: return null.also { logReflectionFailure() }
        return runCatching { method.invoke(cooldowns, stack) as? Identifier }.getOrNull()
    }

    private fun findField(type: Class<*>, vararg names: String): Field? {
        names.forEach { name ->
            runCatching {
                type.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun findMethod(type: Class<*>, names: Array<String>, vararg parameterTypes: Class<*>): Method? {
        names.forEach { name ->
            runCatching {
                type.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun logReflectionFailure() {
        if (reflectionFailureLogged) return
        reflectionFailureLogged = true
        com.visualproject.client.VisualClientMod.LOGGER.warn("cooldowns-hud: failed to reflect cooldown internals; HUD will stay empty until mappings are adjusted")
    }

    private fun shellStyle(glowColor: Int, neonColor: Int): SdfPanelStyle {
        return SdfPanelStyle(
            baseColor = VisualThemeSettings.hudShellFill(),
            borderColor = VisualThemeSettings.hudShellBorder(),
            borderWidthPx = 1.2f,
            radiusPx = Layout.shellRadius,
            innerGlow = SdfGlowStyle(0xFFFFFFFF.toInt(), radiusPx = 12f, strength = 0.03f, opacity = 0.03f),
            outerGlow = SdfGlowStyle(glowColor, radiusPx = 22f, strength = if (VisualThemeSettings.isLightPreset()) 0.14f else 0.18f, opacity = if (VisualThemeSettings.isLightPreset()) 0.08f else 0.11f),
            shade = SdfShadeStyle(VisualThemeSettings.hudShellShadeTop(), VisualThemeSettings.hudShellShadeBottom()),
            neonBorder = SdfNeonBorderStyle(
                color = VisualThemeSettings.withAlpha(neonColor, if (VisualThemeSettings.isLightPreset()) 0x62 else 0x88),
                widthPx = 1.0f,
                softnessPx = 5f,
                strength = if (VisualThemeSettings.isLightPreset()) 0.36f else 0.52f,
            ),
        )
    }

    private fun shellHeightFor(rowCount: Int): Int {
        return Layout.padding + (rowCount * Layout.rowHeight) + ((rowCount - 1).coerceAtLeast(0) * Layout.rowGap) + Layout.padding
    }

    private fun ensureDragState(client: Minecraft): CooldownsHudDragState {
        val current = dragState
        if (current != null) return current

        val defaultPosition = CooldownsHudPosition(
            x = Layout.anchorX,
            y = Layout.anchorY,
        )
        return CooldownsHudDragState(CooldownsHudPositionStore.load(defaultPosition)).also { loaded ->
            clampToScreen(client, loaded, scaled(Layout.shellWidth, hudScale()), scaled(shellHeightFor(3), hudScale()))
            dragState = loaded
        }
    }

    private fun clampToScreen(client: Minecraft, state: CooldownsHudDragState, hudWidth: Int, hudHeight: Int) {
        state.setPositionClamped(
            x = state.position.x,
            y = state.position.y,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = hudWidth,
            hudHeight = hudHeight,
        )
    }

    private fun adjustPositionForScaleChange(
        client: Minecraft,
        state: CooldownsHudDragState,
        baseWidth: Int,
        baseHeight: Int,
        scale: Float,
    ) {
        if (lastScale <= 0f) {
            lastScale = scale
            return
        }
        if (kotlin.math.abs(lastScale - scale) < 0.001f || state.dragging) {
            lastScale = scale
            return
        }

        val oldWidth = scaled(baseWidth, lastScale)
        val oldHeight = scaled(baseHeight, lastScale)
        val newWidth = scaled(baseWidth, scale)
        val newHeight = scaled(baseHeight, scale)
        val targetX = state.position.x - ((newWidth - oldWidth) / 2)
        val targetY = state.position.y - ((newHeight - oldHeight) / 2)
        state.setPositionClamped(
            x = targetX,
            y = targetY,
            screenWidth = client.window.guiScaledWidth,
            screenHeight = client.window.guiScaledHeight,
            hudWidth = newWidth,
            hudHeight = newHeight,
        )
        CooldownsHudPositionStore.save(state.position)
        lastScale = scale
    }

    private fun hudScale(): Float {
        return ModuleStateStore.getNumberSetting("${moduleId}:size", 1.0f).coerceIn(0.5f, 3.0f)
    }

    private fun scaled(value: Int, scale: Float): Int {
        return (value * scale).roundToInt().coerceAtLeast(1)
    }
}
