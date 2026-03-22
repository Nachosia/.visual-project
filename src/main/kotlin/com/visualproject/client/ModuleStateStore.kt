package com.visualproject.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val moduleNumberSettingsState: MutableMap<String, Float> = ConcurrentHashMap()
    private val moduleTextSettingsState: MutableMap<String, String> = ConcurrentHashMap()
    private val moduleEnabledListeners = CopyOnWriteArrayList<(String, Boolean) -> Unit>()

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
        if (previous != enabled) {
            persist()
            moduleEnabledListeners.forEach { listener ->
                runCatching { listener(id, enabled) }
                    .onFailure { throwable ->
                        logger.warn("module-state: enabled-listener failed id='{}' enabled={}", id, enabled, throwable)
                    }
            }
        }
    }

    fun toggleEnabled(id: String): Boolean {
        ensureInitialized()
        val next = !isEnabled(id)
        setEnabled(id, next)
        return next
    }

    fun addModuleEnabledListener(listener: (String, Boolean) -> Unit) {
        ensureInitialized()
        moduleEnabledListeners += listener
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

    fun ensureNumberSetting(key: String, defaultValue: Float = 0f) {
        ensureInitialized()
        val inserted = moduleNumberSettingsState.putIfAbsent(key, defaultValue) == null
        if (inserted) persist()
    }

    fun getNumberSetting(key: String, defaultValue: Float = 0f): Float {
        ensureInitialized()
        return moduleNumberSettingsState[key] ?: defaultValue
    }

    fun setNumberSetting(key: String, value: Float) {
        ensureInitialized()
        val previous = moduleNumberSettingsState.put(key, value)
        if (previous != value) persist()
    }

    fun ensureTextSetting(key: String, defaultValue: String = "") {
        ensureInitialized()
        val inserted = moduleTextSettingsState.putIfAbsent(key, defaultValue) == null
        if (inserted) persist()
    }

    fun getTextSetting(key: String, defaultValue: String = ""): String {
        ensureInitialized()
        return moduleTextSettingsState[key] ?: defaultValue
    }

    fun setTextSetting(key: String, value: String) {
        ensureInitialized()
        val previous = moduleTextSettingsState.put(key, value)
        if (previous != value) persist()
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
            add("numberSettings", JsonObject())
            add("textSettings", JsonObject())
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
            moduleNumberSettingsState.clear()
            moduleTextSettingsState.clear()

            json.getAsJsonObject("modules")?.entrySet()?.forEach { entry ->
                moduleEnabledState[entry.key] = entry.value?.asBoolean == true
            }
            json.getAsJsonObject("settings")?.entrySet()?.forEach { entry ->
                moduleSettingsState[entry.key] = entry.value?.asBoolean == true
            }
            json.getAsJsonObject("numberSettings")?.entrySet()?.forEach { entry ->
                val value = runCatching { entry.value?.asFloat }.getOrNull()
                if (value != null) {
                    moduleNumberSettingsState[entry.key] = value
                }
            }
            json.getAsJsonObject("textSettings")?.entrySet()?.forEach { entry ->
                val value = runCatching { entry.value?.asString }.getOrNull()
                if (value != null) {
                    moduleTextSettingsState[entry.key] = value
                }
            }

            logger.info(
                "module-state: loaded '{}' modules={} settings={} numberSettings={} textSettings={}",
                configPath,
                moduleEnabledState.size,
                moduleSettingsState.size,
                moduleNumberSettingsState.size,
                moduleTextSettingsState.size,
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
                val numberSettingsJson = JsonObject().apply {
                    moduleNumberSettingsState
                        .toSortedMap()
                        .forEach { (key, value) -> addProperty(key, value) }
                }
                val textSettingsJson = JsonObject().apply {
                    moduleTextSettingsState
                        .toSortedMap()
                        .forEach { (key, value) -> addProperty(key, value) }
                }

                val rootJson = JsonObject().apply {
                    add("modules", modulesJson)
                    add("settings", settingsJson)
                    add("numberSettings", numberSettingsJson)
                    add("textSettings", textSettingsJson)
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
