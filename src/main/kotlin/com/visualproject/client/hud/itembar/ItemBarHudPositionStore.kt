package com.visualproject.client.hud.itembar

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object ItemBarHudPositionStore {

    private val logger = LoggerFactory.getLogger("visualclient-itembar-position")
    private val configPath: Path = Path.of("config", "visualclient-itembar.json")

    fun load(defaultPosition: ItemBarHudPosition): ItemBarHudPosition {
        return try {
            if (!Files.exists(configPath)) return defaultPosition
            val raw = Files.readString(configPath, StandardCharsets.UTF_8)
            val json = JsonParser.parseString(raw).asJsonObject
            val x = json["x"]?.asInt ?: defaultPosition.x
            val y = json["y"]?.asInt ?: defaultPosition.y
            ItemBarHudPosition(x, y)
        } catch (throwable: Throwable) {
            logger.warn("item-bar-position: failed to load '{}'", configPath, throwable)
            defaultPosition
        }
    }

    fun save(position: ItemBarHudPosition) {
        try {
            Files.createDirectories(configPath.parent)

            val json = JsonObject().apply {
                addProperty("x", position.x)
                addProperty("y", position.y)
            }

            Files.writeString(
                configPath,
                json.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        } catch (throwable: Throwable) {
            logger.warn("item-bar-position: failed to save '{}'", configPath, throwable)
        }
    }
}
