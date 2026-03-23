package com.visualproject.client.audio

import com.visualproject.client.VisualClientMod
import javazoom.jl.decoder.JavaLayerException
import javazoom.jl.player.JavaSoundAudioDevice
import javazoom.jl.player.Player
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

object CustomSoundPlayer {

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "visualclient-custom-sound").apply {
            isDaemon = true
        }
    }

    fun play(rawSoundName: String, volume: Float = 1f) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        if (clampedVolume <= 0f) {
            VisualClientMod.LOGGER.info("custom-sound: skip sound='{}' reason='volume-zero'", rawSoundName)
            return
        }
        val bytes = CustomSoundRegistry.loadBytes(rawSoundName)
        if (bytes == null) {
            VisualClientMod.LOGGER.warn("custom-sound: missing sound='{}'", rawSoundName)
            return
        }
        executor.execute {
            try {
                val player = Player(ByteArrayInputStream(bytes), VolumeAudioDevice(clampedVolume))
                try {
                    player.play()
                } finally {
                    runCatching { player.close() }
                }
            } catch (throwable: Throwable) {
                VisualClientMod.LOGGER.warn("custom-sound: playback failed for '{}'", rawSoundName, throwable)
            }
        }
    }

    private class VolumeAudioDevice(private val volume: Float) : JavaSoundAudioDevice() {
        @Throws(JavaLayerException::class)
        override fun writeImpl(samples: ShortArray, offs: Int, len: Int) {
            if (volume >= 0.999f) {
                super.writeImpl(samples, offs, len)
                return
            }

            val scaled = ShortArray(len)
            for (index in 0 until len) {
                val sample = samples[offs + index]
                scaled[index] = (sample * volume)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            super.writeImpl(scaled, 0, len)
        }
    }
}
