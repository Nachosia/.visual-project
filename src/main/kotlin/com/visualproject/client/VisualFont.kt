package com.visualproject.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object VisualFont {
    private val customFontId = Identifier.fromNamespaceAndPath("visualclient", "jalnan")
    private val customFontDescription = FontDescription.Resource(customFontId)

    // Primary UI font.
    fun text(value: String): MutableComponent {
        return Component.literal(value).withStyle(Style.EMPTY.withFont(customFontDescription))
    }

    // Branding-only text: use Jalnan where visual identity matters.
    fun brandText(value: String): MutableComponent {
        return text(value)
    }
}

fun vText(value: String): MutableComponent = VisualFont.text(value)
fun vBrandText(value: String): MutableComponent = VisualFont.brandText(value)
