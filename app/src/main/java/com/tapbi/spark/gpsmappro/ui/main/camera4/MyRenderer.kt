package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

class MyRenderer(private val glSurfaceView: GLSurfaceView) : GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {

    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = -1

    private val vertexCoords = floatArrayOf(
        -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
    )

    private val textureCoords = floatArrayOf(
        0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f
    )

    private val transformMatrix = FloatArray(16)
    private val flipMatrix = FloatArray(16)  // Ma trận flip 180 độ

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    private var program = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0
    private var uTextureSampler = 0

    private var listener: OnSurfaceTextureReadyListener? = null

    fun setOnSurfaceTextureReadyListener(l: OnSurfaceTextureReadyListener) {
        listener = l
    }

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        textureId = createOESTexture()
        surfaceTexture = SurfaceTexture(textureId)
        if (surfaceTexture != null) {
            listener?.onSurfaceTextureReady(surfaceTexture!!)
        }
        surfaceTexture?.setOnFrameAvailableListener(this)

        vertexBuffer =
            ByteBuffer.allocateDirect(vertexCoords.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        vertexBuffer.put(vertexCoords).position(0)

        textureBuffer =
            ByteBuffer.allocateDirect(textureCoords.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        textureBuffer.put(textureCoords).position(0)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTextureSampler = GLES20.glGetUniformLocation(program, "uTexture")

        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Khởi tạo flipMatrix để flip 180 độ (scale X và Y = -1)
        Matrix.setIdentityM(flipMatrix, 0)
        Matrix.scaleM(flipMatrix, 0, -1f, -1f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        surfaceTexture?.updateTexImage()

        val rawMatrix = FloatArray(16)
        surfaceTexture?.getTransformMatrix(rawMatrix)

        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, flipMatrix, 0, rawMatrix, 0)

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureHandle)
        GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureSampler, 0)

        val uTransformMatrixHandle = GLES20.glGetUniformLocation(program, "uTransformMatrix")
        GLES20.glUniformMatrix4fv(uTransformMatrixHandle, 1, false, finalMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTextureHandle)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        glSurfaceView.requestRender()
    }

    fun getSurfaceTexture(): SurfaceTexture? = surfaceTexture

    private fun createOESTexture(): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        return textureIds[0]
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform mat4 uTransformMatrix;
            varying vec2 vTextureCoord;
            void main() {
                vec2 transformed = (uTransformMatrix * vec4(vTextureCoord, 0.0, 1.0)).xy;
                gl_FragColor = texture2D(uTexture, transformed);
            }
        """
    }

    interface OnSurfaceTextureReadyListener {
        fun onSurfaceTextureReady(surfaceTexture: SurfaceTexture)
    }
}

