package com.vrunity.vrapk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VertexBuffer(vertices: FloatArray) {
    private val name = IntArray(1)
    private val count = vertices.size / 6

    init {
        check(vertices.size % 6 == 0) { "Invalid vertex data." }
        val data = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(vertices).position(0)
        GLES20.glGenBuffers(1, name, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, name[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, data, GLES20.GL_STATIC_DRAW)
        val error = GLES20.glGetError()
        check(name[0] != 0 && error == GLES20.GL_NO_ERROR) { "Vertex upload failed (OpenGL 0x" + Integer.toHexString(error) + ")." }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(position: Int, normal: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, name[0])
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}
