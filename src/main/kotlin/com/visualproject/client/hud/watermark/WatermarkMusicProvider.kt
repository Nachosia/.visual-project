package com.visualproject.client.hud.watermark

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class WatermarkPlaybackState {
    PLAYING,
    PAUSED,
}

data class WatermarkTrackInfo(
    val title: String,
    val artist: String? = null,
    val source: String = "",
    val positionSeconds: Float = 0f,
    val durationSeconds: Float = 0f,
    val playbackState: WatermarkPlaybackState = WatermarkPlaybackState.PLAYING,
    val artworkTexture: ResourceLocation? = null,
) {
    val progressNormalized: Float
        get() = if (durationSeconds <= 0f) 0f else (positionSeconds / durationSeconds).coerceIn(0f, 1f)
}

interface WatermarkPlaybackController {
    fun previous(client: Minecraft)
    fun togglePlayPause(client: Minecraft)
    fun next(client: Minecraft)
}

interface WatermarkMusicProvider {
    fun currentTrack(client: Minecraft): WatermarkTrackInfo?

    fun playbackController(client: Minecraft): WatermarkPlaybackController? = null
}

internal enum class PlaybackAction(val id: String) {
    PREVIOUS("previous"),
    TOGGLE("toggle"),
    NEXT("next"),
}

internal data class SessionSnapshot(
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val subtitle: String,
    val status: String,
    val positionSeconds: Float,
    val durationSeconds: Float,
)

internal interface MediaSessionGateway {
    fun querySessions(): List<SessionSnapshot>
    fun control(sourceQuery: String, action: PlaybackAction): Boolean
}

internal class WindowsMediaSessionGateway : MediaSessionGateway {
    private val logger = LoggerFactory.getLogger("visualclient-watermark-media-gateway")
    private val debugMediaSessions = System.getProperty("visualclient.media.debug", "true").toBoolean()

    override fun querySessions(): List<SessionSnapshot> {
        if (!isWindows()) {
            if (debugMediaSessions) logger.info("media-gateway: non-windows OS, sessions unavailable")
            return emptyList()
        }

        val script = """
            ${'$'}ErrorActionPreference='SilentlyContinue'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}manager = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]::RequestAsync().AsTask().GetAwaiter().GetResult()
            ${'$'}result = @()
            foreach (${ '$' }session in ${ '$' }manager.GetSessions()) {
              ${ '$' }source = [string]${ '$' }session.SourceAppUserModelId
              ${ '$' }media = ${ '$' }session.TryGetMediaPropertiesAsync().AsTask().GetAwaiter().GetResult()
              ${ '$' }timeline = ${ '$' }session.GetTimelineProperties()
              ${ '$' }playback = ${ '$' }session.GetPlaybackInfo()
              ${ '$' }status = [string]${ '$' }playback.PlaybackStatus
              ${ '$' }position = [float]${ '$' }timeline.Position.TotalSeconds
              ${ '$' }duration = [float]${ '$' }timeline.EndTime.TotalSeconds
              ${ '$' }result += [PSCustomObject]@{
                source = ${ '$' }source
                title = [string]${ '$' }media.Title
                artist = [string]${ '$' }media.Artist
                album = [string]${ '$' }media.AlbumTitle
                subtitle = [string]${ '$' }media.Subtitle
                status = ${ '$' }status
                position = ${ '$' }position
                duration = ${ '$' }duration
              }
            }
            ${ '$' }result | ConvertTo-Json -Compress
        """.trimIndent()

        val output = runPowerShell(script, timeoutMs = 7000) ?: run {
            if (debugMediaSessions) {
                logger.info("media-gateway: powershell returned null/failed output")
            }
            return emptyList()
        }

        if (debugMediaSessions) {
            logger.info(
                "media-gateway: raw-output-length={} raw='{}'",
                output.length,
                shorten(output, 700),
            )
        }

        return parseSessionSnapshots(output)
    }

    override fun control(sourceQuery: String, action: PlaybackAction): Boolean {
        if (!isWindows() || sourceQuery.isBlank()) return false

        val query = sourceQuery.lowercase(Locale.ROOT)
        val script = """
            ${'$'}ErrorActionPreference='SilentlyContinue'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}query='${query.replace("'", "''")}'
            ${'$'}action='${action.id}'
            ${'$'}manager = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]::RequestAsync().AsTask().GetAwaiter().GetResult()
            ${'$'}session = ${ '$' }manager.GetSessions() | Where-Object {
              ${'$'}_.SourceAppUserModelId -and ${'$'}_.SourceAppUserModelId.ToLower().Contains(${ '$' }query)
            } | Select-Object -First 1
            if (${ '$' }null -eq ${ '$' }session) { exit 1 }
            switch (${ '$' }action) {
              'previous' { ${ '$' }session.TrySkipPreviousAsync().AsTask().GetAwaiter().GetResult() | Out-Null }
              'toggle'   { ${ '$' }session.TryTogglePlayPauseAsync().AsTask().GetAwaiter().GetResult() | Out-Null }
              'next'     { ${ '$' }session.TrySkipNextAsync().AsTask().GetAwaiter().GetResult() | Out-Null }
            }
            exit 0
        """.trimIndent()

        return runPowerShell(script, expectOutput = false, timeoutMs = 4500) != null
    }

    private fun runPowerShell(script: String, expectOutput: Boolean = true, timeoutMs: Long): String? {
        return try {
            val process = ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            )
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                if (debugMediaSessions) {
                    logger.info("media-gateway: powershell timeout after {} ms", timeoutMs)
                }
                process.destroyForcibly()
                return null
            }

            if (process.exitValue() != 0) {
                if (debugMediaSessions) {
                    val errOut = process.inputStream.bufferedReader().use { it.readText().trim() }
                    logger.info(
                        "media-gateway: powershell exit={} output='{}'",
                        process.exitValue(),
                        shorten(errOut, 500),
                    )
                }
                return null
            }
            if (!expectOutput) return ""

            process.inputStream.bufferedReader().use { it.readText().trim() }.ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseSessionSnapshots(json: String): List<SessionSnapshot> {
        return try {
            val root = JsonParser.parseString(json)
            when {
                root.isJsonArray -> root.asJsonArray.mapNotNull(::toSessionSnapshot)
                root.isJsonObject -> listOfNotNull(toSessionSnapshot(root))
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toSessionSnapshot(element: JsonElement): SessionSnapshot? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject

        val source = obj.get("source")?.asString?.trim().orEmpty()
        val title = obj.get("title")?.asString?.trim().orEmpty()

        return SessionSnapshot(
            source = source,
            title = title,
            artist = obj.get("artist")?.asString?.trim().orEmpty(),
            album = obj.get("album")?.asString?.trim().orEmpty(),
            subtitle = obj.get("subtitle")?.asString?.trim().orEmpty(),
            status = obj.get("status")?.asString?.trim().orEmpty(),
            positionSeconds = (obj.get("position")?.asFloat ?: 0f).coerceAtLeast(0f),
            durationSeconds = (obj.get("duration")?.asFloat ?: 0f).coerceAtLeast(0f),
        )
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")
    }

    private fun shorten(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.substring(0, max.coerceAtLeast(4) - 3) + "..."
    }
}

internal class SpotifySoundCloudMusicProvider(
    private val gateway: MediaSessionGateway = WindowsMediaSessionGateway(),
) : WatermarkMusicProvider, WatermarkPlaybackController {

    private data class MatchEvaluation(
        val session: SessionSnapshot,
        val accepted: Boolean,
        val reason: String,
    )

    private val logger = LoggerFactory.getLogger("visualclient-watermark-media")

    // Diagnostic fallback mode: intentionally permissive matching.
    private val debugMediaSessions = System.getProperty("visualclient.media.debug", "true").toBoolean()

    private val poller = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "visualclient-media-poller").apply { isDaemon = true }
    }

    private val cachedTrackRef = AtomicReference<WatermarkTrackInfo?>(null)
    private val controlQueryRef = AtomicReference<String?>(null)

    init {
        poller.scheduleWithFixedDelay(
            { safeRefresh() },
            0L,
            2500L,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun currentTrack(client: Minecraft): WatermarkTrackInfo? {
        if (client.player == null) return null
        return cachedTrackRef.get()
    }

    override fun playbackController(client: Minecraft): WatermarkPlaybackController? {
        return if (cachedTrackRef.get() != null && controlQueryRef.get() != null) this else null
    }

    override fun previous(client: Minecraft) {
        enqueueControl(PlaybackAction.PREVIOUS)
    }

    override fun togglePlayPause(client: Minecraft) {
        enqueueControl(PlaybackAction.TOGGLE)
    }

    override fun next(client: Minecraft) {
        enqueueControl(PlaybackAction.NEXT)
    }

    private fun enqueueControl(action: PlaybackAction) {
        val query = controlQueryRef.get() ?: return
        poller.execute { gateway.control(query, action) }
    }

    private fun safeRefresh() {
        try {
            refreshFromGateway()
        } catch (throwable: Throwable) {
            logger.warn("Media poll failed: {}", throwable.message)
        }
    }

    private fun refreshFromGateway() {
        val sessions = gateway.querySessions()
        val evaluations = sessions.map(::evaluateSession)

        val best = evaluations.firstOrNull { it.accepted }

        if (best == null) {
            cachedTrackRef.set(null)
            controlQueryRef.set(null)
        } else {
            cachedTrackRef.set(
                WatermarkTrackInfo(
                    title = best.session.title,
                    artist = best.session.artist.ifBlank { null },
                    source = if (best.session.source.isBlank()) "unknown" else best.session.source,
                    positionSeconds = best.session.positionSeconds,
                    durationSeconds = best.session.durationSeconds,
                    playbackState = WatermarkPlaybackState.PLAYING,
                    artworkTexture = null,
                )
            )
            controlQueryRef.set(best.session.source.lowercase(Locale.ROOT).ifBlank { null })
        }

        if (debugMediaSessions) {
            logDebugEvaluations(evaluations, best)
        }
    }

    private fun evaluateSession(session: SessionSnapshot): MatchEvaluation {
        val playing = isPlaying(session.status)
        val titlePresent = session.title.isNotBlank()

        val accepted = playing && titlePresent
        val reason = when {
            accepted -> "accepted: playing + non-empty title"
            !playing -> "rejected: status is not playing (${session.status})"
            !titlePresent -> "rejected: title is empty"
            else -> "rejected: unknown"
        }

        return MatchEvaluation(
            session = session,
            accepted = accepted,
            reason = reason,
        )
    }

    private fun isPlaying(status: String): Boolean {
        val normalized = status.trim().lowercase(Locale.ROOT)
        return normalized == "playing" || normalized == "4"
    }

    private fun logDebugEvaluations(
        evaluations: List<MatchEvaluation>,
        best: MatchEvaluation?,
    ) {
        logger.info("media-debug: poll-cycle sessions-found={}", evaluations.size)

        if (evaluations.isEmpty()) {
            logger.info("media-debug: no media sessions detected; issue is upstream in session enumeration")
            return
        }

        evaluations.forEachIndexed { index, eval ->
            logger.info(
                "media-debug: session#{} {} source='{}' status='{}' title='{}' artist='{}' reason='{}'",
                index,
                if (eval.accepted) "ACCEPT" else "REJECT",
                shorten(eval.session.source, 72),
                eval.session.status,
                shorten(eval.session.title, 60),
                shorten(eval.session.artist, 40),
                eval.reason,
            )
        }

        if (best != null) {
            logger.info(
                "media-debug: SELECTED source='{}' title='{}' (first accepted candidate)",
                shorten(best.session.source, 72),
                shorten(best.session.title, 60),
            )
        } else {
            logger.info("media-debug: SELECTED none (no accepted playing session with title)")
        }
    }

    private fun shorten(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.substring(0, max.coerceAtLeast(4) - 3) + "..."
    }
}
