package com.visualproject.client.hud.shared

import com.sun.management.OperatingSystemMXBean
import net.minecraft.client.Minecraft
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt
import kotlin.math.sqrt

object HudRuntimeStats {

    data class Snapshot(
        val serverLabel: String,
        val pingMs: Int,
        val ramPercent: Int,
        val cpuPercent: Float,
        val tps: Float,
    )

    private val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean

    private var lastMetricsUpdateMs = 0L
    private var cachedPingMs = 0
    private var cachedRamPercent = 0
    private var cachedCpuPercent = 0f

    private var lastTimeUpdatePacketMs = 0L
    private var smoothedMultiplayerTps = 20f

    @JvmStatic
    fun onTimeUpdatePacketReceived() {
        val now = System.currentTimeMillis()
        if (lastTimeUpdatePacketMs > 0L) {
            val intervalMs = (now - lastTimeUpdatePacketMs).coerceAtLeast(1L)
            val sampleTps = (20_000f / intervalMs.toFloat()).coerceIn(0f, 20f)
            smoothedMultiplayerTps = if (smoothedMultiplayerTps <= 0f) {
                sampleTps
            } else {
                smoothedMultiplayerTps + ((sampleTps - smoothedMultiplayerTps) * 0.25f)
            }
        }
        lastTimeUpdatePacketMs = now
    }

    fun snapshot(client: Minecraft): Snapshot {
        updateMetrics(client)
        return Snapshot(
            serverLabel = serverLabel(client),
            pingMs = cachedPingMs,
            ramPercent = cachedRamPercent,
            cpuPercent = cachedCpuPercent,
            tps = currentTps(client),
        )
    }

    fun serverLabel(client: Minecraft): String {
        if (client.hasSingleplayerServer()) {
            return "Singleplayer"
        }
        return client.currentServer?.ip?.takeIf { it.isNotBlank() } ?: "Multiplayer"
    }

    fun currentBps(client: Minecraft): Float {
        val player = client.player ?: return 0f
        val motion = player.deltaMovement
        val horizontalSpeed = sqrt((motion.x * motion.x + motion.z * motion.z).toFloat())
        return (horizontalSpeed * 20f).coerceAtLeast(0f)
    }

    fun currentCoords(client: Minecraft): Triple<Int, Int, Int> {
        val player = client.player ?: return Triple(0, 0, 0)
        return Triple(player.x.roundToInt(), player.y.roundToInt(), player.z.roundToInt())
    }

    fun currentTps(client: Minecraft): Float {
        if (client.hasSingleplayerServer()) {
            val server = client.singleplayerServer ?: return 20f
            val tickRateManager = server.tickRateManager()
            if (tickRateManager.isFrozen) {
                return 0f
            }

            val averageTickTimeNanos = server.averageTickTimeNanos.toFloat()
            if (averageTickTimeNanos > 0f) {
                return (1_000_000_000f / averageTickTimeNanos).coerceIn(0f, 20f)
            }

            val millisecondsPerTick = tickRateManager.millisecondsPerTick()
            if (millisecondsPerTick > 0f) {
                return (1_000f / millisecondsPerTick).coerceIn(0f, 20f)
            }

            return tickRateManager.tickrate().coerceIn(0f, 20f)
        }

        val lastPacketAt = lastTimeUpdatePacketMs
        if (lastPacketAt <= 0L) return 20f
        if (System.currentTimeMillis() - lastPacketAt > 5_000L) return 20f
        return smoothedMultiplayerTps.coerceIn(0f, 20f)
    }

    private fun updateMetrics(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (now - lastMetricsUpdateMs < 500L) {
            return
        }
        lastMetricsUpdateMs = now

        val player = client.player
        cachedPingMs = if (player != null) {
            client.connection?.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
        } else {
            0
        }

        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory().coerceAtLeast(1L)
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        cachedRamPercent = ((usedMemory.toDouble() / maxMemory.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        val processCpuLoad = osBean?.processCpuLoad?.toFloat() ?: -1f
        if (processCpuLoad >= 0f) {
            val sampleCpuPercent = (processCpuLoad * 100f).coerceIn(0f, 100f)
            cachedCpuPercent = if (cachedCpuPercent <= 0f) {
                sampleCpuPercent
            } else {
                cachedCpuPercent + ((sampleCpuPercent - cachedCpuPercent) * 0.25f)
            }
        }
    }
}
