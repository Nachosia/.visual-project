package com.visualproject.client.hud.watermark

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue

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
    val artworkPath: String? = null,
    val artworkTexture: Identifier? = null,
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
    val artworkPath: String?,
    val isCurrent: Boolean,
    val playbackError: String?,
    val timelineError: String?,
    val mediaError: String?,
    val error: String?,
)

internal interface MediaSessionGateway {
    fun querySessions(): List<SessionSnapshot>
    fun control(sourceQuery: String, action: PlaybackAction): Boolean
}

internal class WindowsNativeBridgeGateway : MediaSessionGateway {

    private data class ProcessExecutionResult(
        val launched: Boolean,
        val timedOut: Boolean,
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val launchError: Throwable? = null,
    ) {
        val success: Boolean
            get() = launched && !timedOut && exitCode == 0
    }

    private val logger = LoggerFactory.getLogger("visualclient-watermark-media-gateway")
    private val debugMediaSessions = System.getProperty("visualclient.media.debug", "true").toBoolean()
    private val cachedHelperPath = AtomicReference<Path?>()

    override fun querySessions(): List<SessionSnapshot> {
        if (!isWindows()) return emptyList()

        val helperPath = ensureHelperBinary() ?: return emptyList()
        val command = buildList {
            add(helperPath.toAbsolutePath().toString())
            add("query")
            if (debugMediaSessions) add("--debug")
        }
        val execution = executeProcess(command, timeoutMs = 6_500L)

        if (!execution.success) {
            logExecutionFailure("query", command, execution)
            return emptyList()
        }

        if (execution.stdout.isBlank()) {
            logger.warn(
                "media-gateway: query produced empty stdout (exit={}, stderr='{}')",
                execution.exitCode,
                shorten(execution.stderr, 600),
            )
            return emptyList()
        }

        if (debugMediaSessions) {
            logger.info(
                "media-gateway: query success exit={} stdout-length={} stderr-length={} raw='{}'",
                execution.exitCode,
                execution.stdout.length,
                execution.stderr.length,
                shorten(execution.stdout, 700),
            )
            if (execution.stderr.isNotBlank()) {
                logger.info("media-gateway: helper-debug='{}'", shorten(execution.stderr, 6000))
            }
        }

        return parseSessionSnapshots(execution.stdout)
    }

    override fun control(sourceQuery: String, action: PlaybackAction): Boolean {
        if (!isWindows()) return false

        val helperPath = ensureHelperBinary() ?: return false
        val command = buildList {
            add(helperPath.toAbsolutePath().toString())
            add("control")
            if (sourceQuery.isNotBlank()) {
                add("--source")
                add(sourceQuery)
            }
            add("--action")
            add(action.id)
            if (debugMediaSessions) add("--debug")
        }

        val execution = executeProcess(command, timeoutMs = 4_500L)
        val launched = execution.launched
        val timedOut = execution.timedOut
        val exitCode = execution.exitCode
        val softSuccess = launched && !timedOut && (exitCode == 0 || exitCode == 2)

        if (!softSuccess) {
            logExecutionFailure("control:${action.id}", command, execution)
            return false
        }

        val helperOk = parseControlOk(execution.stdout)

        if (debugMediaSessions) {
            if (execution.stdout.isNotBlank()) {
                logger.info("media-gateway: control stdout='{}'", shorten(execution.stdout, 400))
            }
            if (execution.stderr.isNotBlank()) {
                logger.info("media-gateway: control stderr='{}'", shorten(execution.stderr, 1000))
            }
            logger.info(
                "media-gateway: control parsed action='{}' source='{}' exit={} helperOk={}",
                action.id,
                if (sourceQuery.isBlank()) "<none>" else shorten(sourceQuery, 96),
                execution.exitCode,
                helperOk,
            )
        }

        return helperOk
    }

    private fun ensureHelperBinary(): Path? {
        cachedHelperPath.get()?.let { path ->
            if (Files.exists(path)) return path
        }

        return synchronized(this) {
            cachedHelperPath.get()?.let { path ->
                if (Files.exists(path)) return@synchronized path
            }

            val helperBytes = try {
                javaClass.classLoader.getResourceAsStream(HELPER_RESOURCE_PATH)?.use { input -> input.readBytes() }
            } catch (throwable: Throwable) {
                logger.warn("media-gateway: failed to read helper resource '{}'", HELPER_RESOURCE_PATH, throwable)
                null
            }

            if (helperBytes == null || helperBytes.isEmpty()) {
                logger.warn(
                    "media-gateway: helper resource '{}' not found in mod resources; bridge unavailable",
                    HELPER_RESOURCE_PATH,
                )
                return@synchronized null
            }

            val baseDir = resolveNativeBridgeDir()
            val helperHash = sha1Hex(helperBytes).take(12)
            val targetPath = baseDir.resolve("${HELPER_FILE_NAME_PREFIX}-$helperHash.exe")

            try {
                Files.createDirectories(baseDir)
                val needsWrite = !Files.exists(targetPath) || runCatching { Files.size(targetPath) }.getOrDefault(-1L) != helperBytes.size.toLong()

                if (needsWrite) {
                    Files.write(
                        targetPath,
                        helperBytes,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    )
                }

                cachedHelperPath.set(targetPath)
                logger.info(
                    "media-gateway: helper ready path='{}' hash='{}' size={} wroteNew={}",
                    targetPath,
                    helperHash,
                    helperBytes.size,
                    needsWrite,
                )
                targetPath
            } catch (throwable: Throwable) {
                logger.warn("media-gateway: failed to extract helper binary", throwable)
                null
            }
        }
    }

    private fun executeProcess(command: List<String>, timeoutMs: Long): ProcessExecutionResult {
        val startedAt = System.currentTimeMillis()
        if (debugMediaSessions) {
            logger.info("media-gateway: execute command={} timeoutMs={}", formatCommand(command), timeoutMs)
        }

        return try {
            val process = ProcessBuilder(command).start()

            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()

            val stdoutReader = spawnStreamPump(process.inputStream, stdoutBuffer)
            val stderrReader = spawnStreamPump(process.errorStream, stderrBuffer)

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                stdoutReader.join(300)
                stderrReader.join(300)

                return ProcessExecutionResult(
                    launched = true,
                    timedOut = true,
                    exitCode = null,
                    stdout = stdoutBuffer.toString(StandardCharsets.UTF_8).trim(),
                    stderr = stderrBuffer.toString(StandardCharsets.UTF_8).trim(),
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            }

            stdoutReader.join(300)
            stderrReader.join(300)

            ProcessExecutionResult(
                launched = true,
                timedOut = false,
                exitCode = process.exitValue(),
                stdout = stdoutBuffer.toString(StandardCharsets.UTF_8).trim(),
                stderr = stderrBuffer.toString(StandardCharsets.UTF_8).trim(),
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (throwable: Throwable) {
            ProcessExecutionResult(
                launched = false,
                timedOut = false,
                exitCode = null,
                stdout = "",
                stderr = "",
                durationMs = System.currentTimeMillis() - startedAt,
                launchError = throwable,
            )
        }
    }

    private fun spawnStreamPump(stream: InputStream, out: ByteArrayOutputStream): Thread {
        return Thread {
            stream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun parseSessionSnapshots(json: String): List<SessionSnapshot> {
        return try {
            val root = JsonParser.parseString(json)
            when {
                root.isJsonArray -> root.asJsonArray.mapNotNull(::toSessionSnapshot)
                root.isJsonObject && root.asJsonObject.has("sessions") -> {
                    root.asJsonObject.getAsJsonArray("sessions").mapNotNull(::toSessionSnapshot)
                }
                root.isJsonObject -> listOfNotNull(toSessionSnapshot(root))
                else -> emptyList()
            }
        } catch (throwable: Throwable) {
            logger.warn(
                "media-gateway: json-parse-failed length={} raw='{}'",
                json.length,
                shorten(json, 700),
                throwable,
            )
            emptyList()
        }
    }

    private fun toSessionSnapshot(element: JsonElement): SessionSnapshot? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject

        return SessionSnapshot(
            source = obj.get("source")?.asString?.trim().orEmpty(),
            title = obj.get("title")?.asString?.trim().orEmpty(),
            artist = obj.get("artist")?.asString?.trim().orEmpty(),
            album = obj.get("album")?.asString?.trim().orEmpty(),
            subtitle = obj.get("subtitle")?.asString?.trim().orEmpty(),
            status = obj.get("status")?.asString?.trim().orEmpty(),
            positionSeconds = (obj.get("position")?.asFloat ?: 0f).coerceAtLeast(0f),
            durationSeconds = (obj.get("duration")?.asFloat ?: 0f).coerceAtLeast(0f),
            artworkPath = obj.get("artworkPath")?.asString?.trim().orEmpty().ifBlank { null },
            isCurrent = obj.get("isCurrent")?.asBoolean ?: false,
            playbackError = obj.get("playbackError")?.asString?.trim().orEmpty().ifBlank { null },
            timelineError = obj.get("timelineError")?.asString?.trim().orEmpty().ifBlank { null },
            mediaError = obj.get("mediaError")?.asString?.trim().orEmpty().ifBlank { null },
            error = obj.get("error")?.asString?.trim().orEmpty().ifBlank { null },
        )
    }

    private fun resolveNativeBridgeDir(): Path {
        val localAppData = System.getenv("LOCALAPPDATA")
        return if (!localAppData.isNullOrBlank()) {
            Path.of(localAppData, "VisualClient", "native")
        } else {
            Path.of(System.getProperty("java.io.tmpdir"), "visualclient", "native")
        }
    }

    private fun logExecutionFailure(
        operation: String,
        command: List<String>,
        execution: ProcessExecutionResult,
    ) {
        logger.warn(
            "media-gateway: {} failed launched={} timeout={} exit={} durationMs={} stdoutLen={} stderrLen={} command={}",
            operation,
            execution.launched,
            execution.timedOut,
            execution.exitCode,
            execution.durationMs,
            execution.stdout.length,
            execution.stderr.length,
            formatCommand(command),
        )

        if (execution.stdout.isNotBlank()) {
            logger.warn("media-gateway: {} stdout='{}'", operation, shorten(execution.stdout, 700))
        }
        if (execution.stderr.isNotBlank()) {
            logger.warn("media-gateway: {} stderr='{}'", operation, shorten(execution.stderr, 700))
        }
        if (execution.launchError != null) {
            logger.warn("media-gateway: {} launch-exception", operation, execution.launchError)
        }
    }

    private fun formatCommand(command: List<String>): String {
        return command.joinToString(" ") { arg ->
            if (arg.contains(' ')) "\"$arg\"" else arg
        }
    }

    private fun parseControlOk(stdout: String): Boolean {
        if (stdout.isBlank()) {
            logger.warn("media-gateway: control parse failed, stdout is blank")
            return false
        }

        return try {
            val root = JsonParser.parseString(stdout)
            if (root.isJsonObject && root.asJsonObject.has("ok")) {
                root.asJsonObject.get("ok").asBoolean
            } else {
                logger.warn("media-gateway: control parse failed, missing 'ok' in stdout='{}'", shorten(stdout, 240))
                false
            }
        } catch (throwable: Throwable) {
            logger.warn("media-gateway: control parse failed, stdout='{}'", shorten(stdout, 240), throwable)
            false
        }
    }

    private fun sha1Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")
    }

    private fun shorten(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.substring(0, max.coerceAtLeast(4) - 3) + "..."
    }

    companion object {
        private const val HELPER_RESOURCE_PATH = "native/win-x64/visual-media-bridge.exe"
        private const val HELPER_FILE_NAME_PREFIX = "visual-media-bridge"
    }
}

internal class SpotifySoundCloudMusicProvider(
    private val gateway: MediaSessionGateway = WindowsNativeBridgeGateway(),
) : WatermarkMusicProvider, WatermarkPlaybackController {

    private data class MatchEvaluation(
        val session: SessionSnapshot,
        val accepted: Boolean,
        val reason: String,
    )

    private val logger = LoggerFactory.getLogger("visualclient-watermark-media")
    private val debugMediaSessions = System.getProperty("visualclient.media.debug", "true").toBoolean()

    private val poller = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "visualclient-media-poller").apply { isDaemon = true }
    }
    private val pollLock = Any()

    private val cachedTrackRef = AtomicReference<WatermarkTrackInfo?>(null)
    private val controlQueryRef = AtomicReference<String?>(null)
    private var mainPollFuture: ScheduledFuture<*>? = null

    private var lastKnownTrackIdentity: String? = null
    private var recentTrackChangedAtMs: Long = 0L
    private var lastManualControlAtMs: Long = 0L

    init {
        scheduleMainPoll(delayMs = 0L, reason = "init", replaceExisting = true)
    }

    override fun currentTrack(client: Minecraft): WatermarkTrackInfo? {
        if (client.player == null) return null
        return cachedTrackRef.get()
    }

    override fun playbackController(client: Minecraft): WatermarkPlaybackController? {
        return if (cachedTrackRef.get() != null) this else null
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
        val query = controlQueryRef.get().orEmpty()

        logger.info(
            "media-control: click action='{}' source='{}' hasSource={}",
            action.id,
            if (query.isBlank()) "<none>" else shorten(query, 96),
            query.isNotBlank(),
        )
        if (action == PlaybackAction.TOGGLE) {
            cachedTrackRef.updateAndGet { track ->
                track?.copy(
                    playbackState = if (track.playbackState == WatermarkPlaybackState.PLAYING) {
                        WatermarkPlaybackState.PAUSED
                    } else {
                        WatermarkPlaybackState.PLAYING
                    }
                )
            }
        }

        poller.execute {
            logger.info(
                "media-control: dispatch action='{}' source='{}' thread='{}'",
                action.id,
                if (query.isBlank()) "<none>" else shorten(query, 96),
                Thread.currentThread().name,
            )
            val ok = gateway.control(query, action)
            logger.info(
                "media-control: result action='{}' source='{}' ok={}",
                action.id,
                if (query.isBlank()) "<none>" else shorten(query, 96),
                ok,
            )

            scheduleManualControlRefreshBurst(action)
        }
    }

    private fun scheduleMainPoll(delayMs: Long, reason: String, replaceExisting: Boolean) {
        synchronized(pollLock) {
            if (replaceExisting) {
                mainPollFuture?.cancel(false)
                mainPollFuture = null
            } else if (mainPollFuture?.isDone == false) {
                return
            }

            val boundedDelay = delayMs.coerceIn(0L, MAX_MAIN_POLL_DELAY_MS)
            if (debugMediaSessions) {
                logger.info("media-poll: schedule-main delayMs={} reason='{}' replace={}", boundedDelay, reason, replaceExisting)
            }
            mainPollFuture = poller.schedule(
                { safeRefresh(reason) },
                boundedDelay,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun scheduleSupplementalPoll(delayMs: Long, reason: String) {
        val boundedDelay = delayMs.coerceIn(0L, MAX_MAIN_POLL_DELAY_MS)
        if (debugMediaSessions) {
            logger.info("media-poll: schedule-supplemental delayMs={} reason='{}'", boundedDelay, reason)
        }
        poller.schedule(
            { safeRefresh("supplemental:$reason:+${boundedDelay}ms") },
            boundedDelay,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleManualControlRefreshBurst(action: PlaybackAction) {
        lastManualControlAtMs = System.currentTimeMillis()
        val reason = "manual-control:${action.id}"
        scheduleMainPoll(delayMs = 0L, reason = "$reason:immediate", replaceExisting = true)
        scheduleSupplementalPoll(delayMs = 300L, reason = reason)
        scheduleSupplementalPoll(delayMs = 900L, reason = reason)
    }

    private fun safeRefresh(reason: String) {
        try {
            refreshFromGateway()
        } catch (throwable: Throwable) {
            logger.warn("Media poll failed: {}", throwable.message)
        } finally {
            val now = System.currentTimeMillis()
            val nextDelay = computeAdaptiveDelayMs(cachedTrackRef.get(), now)
            scheduleMainPoll(
                delayMs = nextDelay,
                reason = "adaptive-next(after='$reason')",
                replaceExisting = true,
            )
        }
    }

    private fun refreshFromGateway() {
        val now = System.currentTimeMillis()
        val sessions = gateway.querySessions()
        val evaluations = sessions.map(::evaluateSession)
        val best = evaluations.firstOrNull { it.accepted && it.session.isCurrent }
            ?: evaluations.firstOrNull { it.accepted }

        if (best == null) {
            cachedTrackRef.set(null)
            controlQueryRef.set(null)
            lastKnownTrackIdentity = null
        } else {
            val liveArtworkPath = resolveLiveArtworkPath(best.session)
            val normalizedSource = when {
                isSpotifySession(best.session) -> "spotify"
                isSoundCloudSession(best.session) -> "soundcloud"
                else -> "unknown"
            }
            val playbackState = if (isPlaying(best.session.status)) {
                WatermarkPlaybackState.PLAYING
            } else {
                WatermarkPlaybackState.PAUSED
            }
            val updatedTrack = WatermarkTrackInfo(
                title = best.session.title,
                artist = best.session.artist.ifBlank { null },
                source = normalizedSource,
                positionSeconds = best.session.positionSeconds,
                durationSeconds = best.session.durationSeconds,
                playbackState = playbackState,
                artworkPath = liveArtworkPath,
                artworkTexture = null,
            )
            cachedTrackRef.set(updatedTrack)
            controlQueryRef.set(best.session.source.ifBlank { null })

            val newIdentity = trackIdentity(updatedTrack)
            if (newIdentity != null && newIdentity != lastKnownTrackIdentity) {
                recentTrackChangedAtMs = now
                if (debugMediaSessions) {
                    logger.info(
                        "media-poll: track-changed old='{}' new='{}' => stabilization {}ms",
                        lastKnownTrackIdentity ?: "<none>",
                        newIdentity,
                        TRACK_CHANGE_STABILIZATION_MS,
                    )
                }
            }
            lastKnownTrackIdentity = newIdentity

            if (debugMediaSessions) {
                logger.info(
                    "media-control: active session source='{}' current={} status='{}'",
                    shorten(best.session.source, 96),
                    best.session.isCurrent,
                    best.session.status,
                )
            }

            if (debugMediaSessions) {
                val artworkPath = liveArtworkPath
                val artworkFilePath = artworkPath?.let { raw -> runCatching { Path.of(raw) }.getOrNull() }
                val artworkExists = artworkFilePath?.let { runCatching { Files.exists(it) }.getOrDefault(false) } ?: false
                val artworkSize = artworkFilePath?.let { runCatching { Files.size(it) }.getOrDefault(-1L) } ?: -1L
                val artworkModified = artworkFilePath?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(-1L) } ?: -1L
                logger.info(
                    "media-artwork: selected source='{}' title='{}' artworkPath='{}' exists={} size={} modifiedMs={} stablePathPreferred={}",
                    shorten(best.session.source, 72),
                    shorten(best.session.title, 60),
                    artworkPath ?: "<null>",
                    artworkExists,
                    artworkSize,
                    artworkModified,
                    best.session.isCurrent,
                )
            }
        }

        if (debugMediaSessions) {
            logDebugEvaluations(evaluations, best)
        }
    }

    private fun computeAdaptiveDelayMs(track: WatermarkTrackInfo?, nowMs: Long): Long {
        if (track == null) {
            return NO_TRACK_POLL_MS
        }

        val recentlyControlled = nowMs - lastManualControlAtMs <= MANUAL_BURST_WINDOW_MS
        if (recentlyControlled) {
            return FAST_POLL_MS
        }

        val recentlyChanged = recentTrackChangedAtMs > 0L && (nowMs - recentTrackChangedAtMs) <= TRACK_CHANGE_STABILIZATION_MS
        if (recentlyChanged) {
            return FAST_POLL_MS
        }

        if (track.playbackState == WatermarkPlaybackState.PAUSED) {
            return PAUSED_POLL_MS
        }

        val remaining = (track.durationSeconds - track.positionSeconds).coerceAtLeast(0f)
        if (track.durationSeconds > 0f && remaining <= END_OF_TRACK_SECONDS) {
            return FAST_POLL_MS
        }

        val stableHash = trackIdentity(track)?.hashCode()?.absoluteValue ?: 0
        return STABLE_PLAYING_MIN_MS + (stableHash % (STABLE_PLAYING_MAX_MS - STABLE_PLAYING_MIN_MS + 1))
    }

    private fun trackIdentity(track: WatermarkTrackInfo?): String? {
        if (track == null) return null
        return "${track.source}|${track.title}|${track.artist.orEmpty()}"
    }

    private fun evaluateSession(session: SessionSnapshot): MatchEvaluation {
        val active = isPlaying(session.status) || isPaused(session.status)
        val titlePresent = session.title.isNotBlank()
        val spotify = isSpotifySession(session)
        val soundCloud = isSoundCloudSession(session)

        val accepted = active && titlePresent && (spotify || soundCloud)
        val reason = when {
            accepted && spotify -> "accepted: spotify session with valid metadata"
            accepted && soundCloud -> "accepted: soundcloud session with valid metadata"
            !active -> "rejected: status is not active (${session.status})"
            !titlePresent -> "rejected: title is empty"
            !spotify && !soundCloud -> "rejected: source is not spotify/soundcloud"
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

    private fun isPaused(status: String): Boolean {
        val normalized = status.trim().lowercase(Locale.ROOT)
        return normalized == "paused" || normalized == "5"
    }

    private fun isSpotifySession(session: SessionSnapshot): Boolean {
        val haystack = listOf(session.source, session.title, session.artist, session.album, session.subtitle)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return "spotify" in haystack
    }

    private fun isSoundCloudSession(session: SessionSnapshot): Boolean {
        val source = session.source.lowercase(Locale.ROOT)
        val metadata = listOf(session.title, session.artist, session.album, session.subtitle)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        if ("soundcloud" in source) return true
        val browserSession = BROWSER_MARKERS.any { marker -> marker in source }
        return browserSession && "soundcloud" in metadata
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
                "media-debug: session#{} {} source='{}' status='{}' current={} title='{}' artist='{}' reason='{}' stepErrors='{}'",
                index,
                if (eval.accepted) "ACCEPT" else "REJECT",
                shorten(eval.session.source, 72),
                eval.session.status,
                eval.session.isCurrent,
                shorten(eval.session.title, 60),
                shorten(eval.session.artist, 40),
                eval.reason,
                shorten(eval.session.error.orEmpty(), 220),
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

    private fun resolveLiveArtworkPath(session: SessionSnapshot): String? {
        if (!session.isCurrent) return session.artworkPath

        val stablePath = buildStableCoverPath()
        val stableFile = runCatching { Path.of(stablePath) }.getOrNull()
        if (stableFile != null) {
            val exists = runCatching { Files.exists(stableFile) }.getOrDefault(false)
            val size = runCatching { Files.size(stableFile) }.getOrDefault(0L)
            if (exists && size > 0L) {
                return stablePath
            }
        }
        return session.artworkPath
    }

    private fun buildStableCoverPath(): String {
        val localAppData = System.getenv("LOCALAPPDATA")
        return if (!localAppData.isNullOrBlank()) {
            Path.of(localAppData, "VisualClient", "buffer", "current_cover.png").toString()
        } else {
            Path.of(System.getProperty("java.io.tmpdir"), "visualclient", "buffer", "current_cover.png").toString()
        }
    }

    companion object {
        private val BROWSER_MARKERS = listOf("chrome", "msedge", "edge", "firefox", "opera", "brave", "vivaldi", "arc")
        private const val STABLE_PLAYING_MIN_MS = 6_000L
        private const val STABLE_PLAYING_MAX_MS = 8_000L
        private const val FAST_POLL_MS = 900L
        private const val PAUSED_POLL_MS = 3_000L
        private const val NO_TRACK_POLL_MS = 2_500L
        private const val END_OF_TRACK_SECONDS = 10f
        private const val TRACK_CHANGE_STABILIZATION_MS = 2_500L
        private const val MANUAL_BURST_WINDOW_MS = 1_500L
        private const val MAX_MAIN_POLL_DELAY_MS = 30_000L
    }
}
