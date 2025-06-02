package com.tapbi.spark.gpsmappro.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.android.datatransport.runtime.ExecutionModule_ExecutorFactory
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraCustomView {
    val context: Context?=null
    var glRenderer : GLRenderer?=null
    val glSurfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2) // OpenGL ES 2.0
        glRenderer = GLRenderer(context)
        setRenderer(glRenderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

// Thêm glSurfaceView vào layout nếu cần:
// yourLayout.addView(glSurfaceView)


// ============================
// STEP 1: Setup CameraX with SurfaceTexture from OpenGL
// ============================

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context!!)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()

        // SurfaceTexture được tạo từ OpenGL để nhận frame từ camera
        val surface = Surface(glRenderer.cameraTexture)
        val surfaceProvider = Preview.SurfaceProvider { request ->
            request.provideSurface(surface, ExecutionModule_ExecutorFactory.executor()) {}
        }
        preview.setSurfaceProvider(surfaceProvider)

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }, ContextCompat.getMainExecutor(context))


// ============================
// STEP 2: OpenGL Renderer dùng GLSurfaceView
// ============================

    class GLRenderer(val context: Context) : GLSurfaceView.Renderer {
        lateinit var cameraTexture: SurfaceTexture
        private var cameraTextureId: Int = -1
        private var overlayBitmap: Bitmap? = null

        fun updateOverlay(bitmap: Bitmap) {
            overlayBitmap = bitmap
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            cameraTextureId = createOESTexture()
            cameraTexture = SurfaceTexture(cameraTextureId)
            cameraTexture.setOnFrameAvailableListener {
                // Trigger render mỗi khi có frame mới từ camera
                glSurfaceView.requestRender()
            }
            // TODO: setup shader, texture cho OES và overlay tại đây
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            cameraTexture.updateTexImage()

            // Vẽ camera texture (OES)
            drawCameraTexture(cameraTextureId)

            // Vẽ overlay nếu có
            overlayBitmap?.let {
                drawOverlayTexture(it)
            }
        }

        private fun createOESTexture(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            return textures[0]
        }

        private fun drawCameraTexture(textureId: Int) {
            // TODO: implement GLSL shader để vẽ texture OES
        }

        private fun drawOverlayTexture(bitmap: Bitmap) {
            // TODO: convert bitmap to GL texture và vẽ lên full screen
        }
    }


// ============================
// STEP 3: Convert FrameLayout (overlay) thành Bitmap real-time
// ============================

    fun getViewBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }


// ============================
// STEP 4: Cập nhật overlay liên tục vào OpenGL
// ============================

    val handler = Handler(Looper.getMainLooper())
    val updateRunnable = object : Runnable {
        override fun run() {
            val bitmap = getViewBitmap(overlayFrameLayout)
            glRenderer?.updateOverlay(bitmap)
            handler.postDelayed(this, 100) // cập nhật mỗi 100ms
        }
    }
    handler.post(updateRunnable)


// ============================
// STEP 5: (Tuỳ chọn) Ghi video bằng MediaCodec + EGL
// ============================
// - Tạo MediaCodec encoder với MIME_TYPE video/avc
// - Lấy Surface từ createInputSurface()
// - Tạo EGLContext từ GLSurfaceView
// - Tạo EGLSurface từ surface trên
// - Dùng eglSwapBuffers mỗi khi vẽ xong frame để ghi vào video
}