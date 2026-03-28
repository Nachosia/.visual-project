package com.visualproject.client.hud.btc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object BtcHudPositionStore {

    private val logger = LoggerFactory.getLogger("visualclient-btc-position")
    private val configPath: Path = Path.of("config", "visualclient-btc.json")

    fun load(defaults: Map<BtcBlockId, BtcHudPosition>): MutableMap<BtcBlockId, BtcHudPosition> {
        val positions = defaults.toMutableMap()
        return try {
            if (!Files.exists(configPath)) return positions
            val raw = Files.readString(configPath, StandardCharsets.UTF_8)
            val json = JsonParser.parseString(raw).asJsonObject
            BtcBlockId.entries.forEach { blockId ->
                val blockJson = json[blockId.key]?.asJsonObject ?: return@forEach
                positions[blockId] = BtcHudPosition(
                    x = blockJson["x"]?.asInt ?: positions.getValue(blockId).x,
                    y = blockJson["y"]?.asInt ?: positions.getValue(blockId).y,
                )
            }
            positions
        } catch (throwable: Throwable) {
            logger.warn("btc-position: failed to load '{}'", configPath, throwable)
            positions
        }
    }

    fun save(positions: Map<BtcBlockId, BtcHudPosition>) {
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
            logger.warn("btc-position: failed to save '{}'", configPath, throwable)
        }
    }
}
