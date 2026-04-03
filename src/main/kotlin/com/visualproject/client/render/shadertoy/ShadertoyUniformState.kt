package com.visualproject.client.render.shadertoy

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShadertoyUniformState : AutoCloseable {
    companion object {
        private const val VEC4_BYTES = 16
        const val GLOBALS_BYTES = VEC4_BYTES * 7
    }

    private var globalsBuffer: GpuBuffer? = null
    private var globalsUploadBuffer: ByteBuffer? = null

    fun uploadGlobals(
        device: GpuDevice,
        encoder: CommandEncoder,
        width: Int,
        height: Int,
        timeSeconds: Float,
        timeDeltaSeconds: Float,
        frameIndex: Int,
        mouseX: Float,
        mouseY: Float,
        mouseClickX: Float,
        mouseClickY: Float,
        channels: List<ShadertoyChannels.ChannelBinding>,
    ): GpuBuffer {
        val buffer = ensureGlobalsBuffer(device)
        encoder.writeToBuffer(
            buffer.slice(),
            createGlobalsData(
                width = width,
                height = height,
                timeSeconds = timeSeconds,
                timeDeltaSeconds = timeDeltaSeconds,
                frameIndex = frameIndex,
                mouseX = mouseX,
                mouseY = mouseY,
                mouseClickX = mouseClickX,
                mouseClickY = mouseClickY,
                channels = channels,
            ),
        )
        return buffer
    }

    override fun close() {
        globalsBuffer?.close()
        globalsBuffer = null
        globalsUploadBuffer = null
    }

    private fun ensureGlobalsBuffer(device: GpuDevice): GpuBuffer {
        val existing = globalsBuffer
        if (existing != null && !existing.isClosed) {
            return existing
        }

        return device.createBuffer(
            { "visualclient_shadertoy_globals" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            GLOBALS_BYTES.toLong(),
        ).also { created ->
            globalsBuffer = created
        }
    }

    private fun createGlobalsData(
        width: Int,
        height: Int,
        timeSeconds: Float,
        timeDeltaSeconds: Float,
        frameIndex: Int,
        mouseX: Float,
        mouseY: Float,
        mouseClickX: Float,
        mouseClickY: Float,
        channels: List<ShadertoyChannels.ChannelBinding>,
    ): ByteBuffer {
        val byteBuffer = globalsUploadBuffer ?: ByteBuffer.allocateDirect(GLOBALS_BYTES)
            .order(ByteOrder.nativeOrder())
            .also { globalsUploadBuffer = it }
        byteBuffer.clear()
        byteBuffer.putVec4(width.toFloat(), height.toFloat(), 1f, 0f)
        byteBuffer.putVec4(mouseX, mouseY, mouseClickX, mouseClickY)
        byteBuffer.putVec4(timeSeconds, timeDeltaSeconds, frameIndex.toFloat(), 0f)
        repeat(4) { index ->
            val channel = channels.getOrNull(index)
            byteBuffer.putVec4(
                channel?.width?.toFloat() ?: 1f,
                channel?.height?.toFloat() ?: 1f,
                1f,
                0f,
            )
        }
        byteBuffer.flip()
        return byteBuffer
    }

    private fun ByteBuffer.putVec4(x: Float, y: Float, z: Float, w: Float) {
        putFloat(x)
        putFloat(y)
        putFloat(z)
        putFloat(w)
    }
}
