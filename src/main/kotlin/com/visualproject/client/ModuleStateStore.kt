package com.visualproject.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

object ModuleStateStore {

    private val logger = LoggerFactory.getLogger("visualclient-module-state")
    private val initLock = Any()
    private val ioLock = Any()
    private val configPath: Path
        get() = VisualFileSystem.cfgDir().resolve("module-state.json")

    @Volatile
    private var initialized = false

    private val moduleEnabledState: MutableMap<String, Boolean> = ConcurrentHashMap()
    private val moduleSettingsState: MutableMap<String, Boolean> = ConcurrentHashMap()

    fun initialize() {
        ensureInitialized()
    }

    fun ensureModule(id: String, defaultEnabled: Boolean = false) {
        ensureInitialized()
        val inserted = moduleEnabledState.putIfAbsent(id, defaultEnabled) == null
        if (inserted) persist()
    }

    fun isEnabled(id: String): Boolean {
        ensureInitialized()
        return moduleEnabledState[id] == true
    }

    fun setEnabled(id: String, enabled: Boolean) {
        ensureInitialized()
        val previous = moduleEnabledState.put(id, enabled)
        if (previous != enabled) persist()
    }

    fun toggleEnabled(id: String): Boolean {
        ensureInitialized()
        val next = !isEnabled(id)
        setEnabled(id, next)
        return next
    }

    fun ensureSetting(key: String, defaultValue: Boolean = false) {
        ensureInitialized()
        val inserted = moduleSettingsState.putIfAbsent(key, defaultValue) == null
        if (inserted) persist()
    }

    fun isSettingEnabled(key: String): Boolean {
        ensureInitialized()
        return moduleSettingsState[key] == true
    }

    fun setSettingEnabled(key: String, enabled: Boolean) {
        ensureInitialized()
        val previous = moduleSettingsState.put(key, enabled)
        if (previous != enabled) persist()
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return

            Files.createDirectories(configPath.parent)
            if (!Files.exists(configPath)) {
                writeDefaultConfigIfMissing()
            } else {
                loadExistingConfig()
            }

            initialized = true
        }
    }

    private fun writeDefaultConfigIfMissing() {
        val defaultJson = JsonObject().apply {
            add("modules", JsonObject())
            add("settings", JsonObject())
        }

        try {
            Files.writeString(
                configPath,
                defaultJson.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            logger.info("module-state: created default config '{}'", configPath)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // Another caller created it first. Safe to ignore.
            loadExistingConfig()
        } catch (throwable: Throwable) {
            logger.warn("module-state: failed to create default config '{}'", configPath, throwable)
        }
    }

    private fun loadExistingConfig() {
        try {
            val raw = Files.readString(configPath, StandardCharsets.UTF_8)
            val json = JsonParser.parseString(raw).asJsonObject

            moduleEnabledState.clear()
            moduleSettingsState.clear()

            json.getAsJsonObject("modules")?.entrySet()?.forEach { entry ->
                moduleEnabledState[entry.key] = entry.value?.asBoolean == true
            }
            json.getAsJsonObject("settings")?.entrySet()?.forEach { entry ->
                moduleSettingsState[entry.key] = entry.value?.asBoolean == true
            }

            logger.info(
                "module-state: loaded '{}' modules={} settings={}",
                configPath,
                moduleEnabledState.size,
                moduleSettingsState.size,
            )
        } catch (throwable: Throwable) {
            logger.warn("module-state: failed to load existing config '{}'", configPath, throwable)
        }
    }

    private fun persist() {
        synchronized(ioLock) {
            try {
                Files.createDirectories(configPath.parent)

                val modulesJson = JsonObject().apply {
                    moduleEnabledState
                        .toSortedMap()
                        .forEach { (id, enabled) -> addProperty(id, enabled) }
                }

                val settingsJson = JsonObject().apply {
                    moduleSettingsState
                        .toSortedMap()
                        .forEach { (key, enabled) -> addProperty(key, enabled) }
                }

                val rootJson = JsonObject().apply {
                    add("modules", modulesJson)
                    add("settings", settingsJson)
                }

                Files.writeString(
                    configPath,
                    rootJson.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            } catch (throwable: Throwable) {
                logger.warn("module-state: failed to persist '{}'", configPath, throwable)
            }
        }
    }
}
