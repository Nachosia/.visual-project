package com.visualproject.client.render.sdf

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.GpuDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SdfQuadRenderer {
    private const val FLOAT_BYTES = 4
    private const val POSITION_COMPONENTS = 3
    private const val VERTEX_COUNT = 4
    private const val BUFFER_BYTES = FLOAT_BYTES * POSITION_COMPONENTS * VERTEX_COUNT

    fun createQuadBuffer(
        device: GpuDevice,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ): GpuBuffer {
        val data = ByteBuffer.allocateDirect(BUFFER_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                putVertex(x, y, 0f)
                putVertex(x, y + height, 0f)
                putVertex(x + width, y, 0f)
                putVertex(x + width, y + height, 0f)
                flip()
            }

        return device.createBuffer({ "visualclient_sdf_panel_quad" }, GpuBuffer.USAGE_VERTEX, data)
    }

    private fun ByteBuffer.putVertex(x: Float, y: Float, z: Float) {
        putFloat(x)
        putFloat(y)
        putFloat(z)
    }
}
