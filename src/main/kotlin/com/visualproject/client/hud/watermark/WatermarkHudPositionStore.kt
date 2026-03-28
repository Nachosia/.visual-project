package com.visualproject.client.hud.watermark

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object WatermarkHudPositionStore {

    private val logger = LoggerFactory.getLogger("visualclient-watermark-position")
    private val configPath: Path = Path.of("config", "visualclient-watermark.json")

    fun load(defaults: Map<WatermarkHudBlockId, WatermarkHudPosition>): MutableMap<WatermarkHudBlockId, WatermarkHudPosition> {
        val positions = defaults.toMutableMap()
        return try {
            if (!Files.exists(configPath)) return positions
            val raw = Files.readString(configPath, StandardCharsets.UTF_8)
            val json = JsonParser.parseString(raw).asJsonObject
            WatermarkHudBlockId.entries.forEach { blockId ->
                val blockJson = json[blockId.key]?.asJsonObject ?: return@forEach
                val fallback = positions.getValue(blockId)
                positions[blockId] = WatermarkHudPosition(
                    x = blockJson["x"]?.asInt ?: fallback.x,
                    y = blockJson["y"]?.asInt ?: fallback.y,
                )
            }
            positions
        } catch (throwable: Throwable) {
            logger.warn("watermark-position: failed to load '{}'", configPath, throwable)
            positions
        }
    }

    fun save(positions: Map<WatermarkHudBlockId, WatermarkHudPosition>) {
        try {
            Files.createDirectories(configPath.parent)
            val json = JsonObject()
            positions.forEach { (blockId, position) ->
                json.add(blockId.key, JsonObject().apply {
                    addProperty("x", position.x)
                    addProperty("y", position.y)
                })
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
            logger.warn("watermark-position: failed to save '{}'", configPath, throwable)
        }
    }
}
