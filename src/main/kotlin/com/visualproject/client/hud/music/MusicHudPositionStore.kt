package com.visualproject.client.hud.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object MusicHudPositionStore {

    private val logger = LoggerFactory.getLogger("visualclient-music-hud-position")
    private val configPath: Path = Path.of("config", "visualclient-music-hud.json")

    fun load(defaultPosition: MusicHudPosition): MusicHudPosition {
        return try {
            if (!Files.exists(configPath)) return defaultPosition
            val raw = Files.readString(configPath, StandardCharsets.UTF_8)
            val json = JsonParser.parseString(raw).asJsonObject
            MusicHudPosition(
                x = json["x"]?.asInt ?: defaultPosition.x,
                y = json["y"]?.asInt ?: defaultPosition.y,
            )
        } catch (throwable: Throwable) {
            logger.warn("music-hud-position: failed to load '{}'", configPath, throwable)
            defaultPosition
        }
    }

    fun save(position: MusicHudPosition) {
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
            logger.warn("music-hud-position: failed to save '{}'", configPath, throwable)
        }
    }
}
