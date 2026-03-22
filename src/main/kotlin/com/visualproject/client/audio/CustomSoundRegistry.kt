package com.visualproject.client.audio

import com.visualproject.client.VisualClientMod
import com.visualproject.client.VisualFileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.name

object CustomSoundRegistry {

    private const val refreshCooldownMs = 750L

    private val pathByKey = ConcurrentHashMap<String, Path>()
    private val bytesByKey = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    private var lastRefreshMs = 0L

    fun initialize() {
        refresh(force = true)
    }

    fun normalizeKey(raw: String): String {
        val trimmed = raw.trim().trim('"', '\'')
        if (trimmed.isBlank()) return ""
        val fileName = trimmed.replace('\\', '/').substringAfterLast('/')
        val withoutExtension = if (fileName.lowercase(Locale.US).endsWith(".mp3")) {
            fileName.dropLast(4)
        } else {
            fileName
        }
        return withoutExtension.trim().lowercase(Locale.US)
    }

    fun sanitizeForStorage(raw: String): String = normalizeKey(raw)

    fun exists(raw: String): Boolean = resolvePath(raw) != null

    fun resolvePath(raw: String): Path? {
        val key = normalizeKey(raw)
        if (key.isBlank()) return null
        refresh()
        return pathByKey[key] ?: run {
            refresh(force = true)
            pathByKey[key]
        }
    }

    fun loadBytes(raw: String): ByteArray? {
        val key = normalizeKey(raw)
        if (key.isBlank()) return null
        val path = resolvePath(key) ?: return null
        bytesByKey[key]?.let { return it }

        return runCatching { Files.readAllBytes(path) }
            .onFailure {
                VisualClientMod.LOGGER.warn("custom-sound: failed to read '{}'", path, it)
            }
            .getOrNull()
            ?.also { bytesByKey[key] = it }
    }

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshMs < refreshCooldownMs) return

        synchronized(this) {
            val refreshedNow = System.currentTimeMillis()
            if (!force && refreshedNow - lastRefreshMs < refreshCooldownMs) return

            val nextPaths = HashMap<String, Path>()
            runCatching {
                Files.createDirectories(VisualFileSystem.soundDir())
                Files.list(VisualFileSystem.soundDir()).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { it.extension.equals("mp3", ignoreCase = true) }
                        .sorted { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
                        .forEach { file ->
                            val key = normalizeKey(file.name)
                            if (key.isNotBlank()) {
                                nextPaths.putIfAbsent(key, file)
                            }
                        }
                }
            }.onFailure {
                VisualClientMod.LOGGER.warn("custom-sound: failed to scan '{}'", VisualFileSystem.soundDir(), it)
            }

            pathByKey.clear()
            pathByKey.putAll(nextPaths)
            bytesByKey.keys.retainAll(nextPaths.keys)
            lastRefreshMs = refreshedNow
        }
    }
}
