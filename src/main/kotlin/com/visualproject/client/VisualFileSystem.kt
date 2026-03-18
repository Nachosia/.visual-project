package com.visualproject.client

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

object VisualFileSystem {

    private const val ROOT_DIR_NAME = "Visual"
    private const val CFG_DIR_NAME = "cfg"
    private const val SOUND_DIR_NAME = "sound"
    private const val GIF_DIR_NAME = "gif"
    private const val PNG_DIR_NAME = "png"
    private const val VID_DIR_NAME = "vid"

    private val requiredSubDirs = listOf(
        CFG_DIR_NAME,
        SOUND_DIR_NAME,
        GIF_DIR_NAME,
        PNG_DIR_NAME,
        VID_DIR_NAME,
    )

    @Volatile
    private var initialized = false

    @Volatile
    private var rootPath: Path? = null

    fun initialize(logger: Logger) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val resolvedRoot = FabricLoader.getInstance()
                .gameDir
                .resolve(ROOT_DIR_NAME)
                .toAbsolutePath()
                .normalize()

            Files.createDirectories(resolvedRoot)
            requiredSubDirs.forEach { subDir ->
                Files.createDirectories(resolvedRoot.resolve(subDir))
            }

            rootPath = resolvedRoot
            initialized = true

            logger.info("visual-fs: initialized root='{}'", resolvedRoot)
        }
    }

    fun rootDir(): Path {
        val current = rootPath
        if (current != null && initialized) return current
        error("VisualFileSystem is not initialized. Call VisualFileSystem.initialize(...) during client startup.")
    }

    fun cfgDir(): Path = rootDir().resolve(CFG_DIR_NAME)
    fun soundDir(): Path = rootDir().resolve(SOUND_DIR_NAME)
    fun gifDir(): Path = rootDir().resolve(GIF_DIR_NAME)
    fun pngDir(): Path = rootDir().resolve(PNG_DIR_NAME)
    fun vidDir(): Path = rootDir().resolve(VID_DIR_NAME)
}

