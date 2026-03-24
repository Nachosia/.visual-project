package com.visualproject.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object VisualFont {
    private val jalnanFontDescription = fontDescription("jalnan")
    private val satyrFontDescription = fontDescription("satyrsp")
    private val blackcraftFontDescription = fontDescription("blackcraft")

    fun text(value: String): MutableComponent {
        return Component.literal(value).withStyle(Style.EMPTY.withFont(selectedFontDescription()))
    }

    fun brandText(value: String): MutableComponent {
        return text(value)
    }

    private fun selectedFontDescription(): FontDescription.Resource {
        return when (VisualThemeSettings.themeFont()) {
            VisualThemeSettings.ThemeFont.JALNAN -> jalnanFontDescription
            VisualThemeSettings.ThemeFont.SATYR -> satyrFontDescription
            VisualThemeSettings.ThemeFont.BLACKCRAFT -> blackcraftFontDescription
        }
    }

    private fun fontDescription(path: String): FontDescription.Resource {
        return FontDescription.Resource(Identifier.fromNamespaceAndPath("visualclient", path))
    }
}

fun vText(value: String): MutableComponent = VisualFont.text(value)
fun vBrandText(value: String): MutableComponent = VisualFont.brandText(value)
