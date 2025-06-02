package com.tapbi.spark.gpsmappro.ui.camera6

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera6Binding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Camera6Fragment : BaseBindingFragment<FragmentCamera6Binding, Camera6ViewModel>(),OnMapReadyCallback {
    private var service: ExecutorService? = null
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraFacing: Int = CameraSelector.LENS_FACING_BACK
    private val activityResultLauncher = registerForActivityResult<String, Boolean>(
        RequestPermission()
    ) { _: Boolean? ->
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera(cameraFacing)
        }
    }

    override fun getViewModel(): Class<Camera6ViewModel> {
        return Camera6ViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera6

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        binding.capture.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.CAMERA)
            } else if (ActivityCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                captureVideo()
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startCamera(cameraFacing)
        }

        binding.flipCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera(cameraFacing)
        }

        service = Executors.newSingleThreadExecutor()
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }

    private fun captureVideo() {
        binding.capture.setImageResource(R.drawable.round_stop_circle_24)
        val recording1 = recording
        if (recording1 != null) {
            recording1.stop()
            recording = null
            return
        }
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(
            System.currentTimeMillis()
        )
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")

        val options = MediaStoreOutputOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues).build()

        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        recording =
            videoCapture!!.output.prepareRecording(requireActivity(), options).withAudioEnabled()
                .start(
                    ContextCompat.getMainExecutor(
                        requireActivity()
                    )
                ) { videoRecordEvent: VideoRecordEvent? ->
                    if (videoRecordEvent is VideoRecordEvent.Start) {
                        binding.capture.isEnabled = true
                    } else if (videoRecordEvent is VideoRecordEvent.Finalize) {
                        if (!videoRecordEvent.hasError()) {
                            val msg =
                                "Video capture succeeded: " + videoRecordEvent.outputResults.outputUri
                            Toast.makeText(
                                requireActivity(),
                                msg,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            recording!!.close()
                            recording = null
                            val msg =
                                "Error: " + videoRecordEvent.error
                            Toast.makeText(
                                requireActivity(),
                                msg,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        binding.capture.setImageResource(R.drawable.round_fiber_manual_record_24)
                    }
                }
    }

    fun startCamera(cameraFacing: Int) {
        val processCameraProvider = ProcessCameraProvider.getInstance(requireActivity())

        processCameraProvider.addListener({
            try {
                val cameraProvider = processCameraProvider.get()
                val preview = Preview.Builder().build()
                preview.surfaceProvider = binding.viewFinder.getSurfaceProvider()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing).build()

                val camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)

                binding.toggleFlash.setOnClickListener(View.OnClickListener { view: View? ->
                    toggleFlash(
                        camera
                    )
                })
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireActivity()))
    }


    private fun toggleFlash(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                binding.toggleFlash.setImageResource(R.drawable.round_flash_off_24)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.toggleFlash.setImageResource(R.drawable.round_flash_on_24)
            }
        } else {
            requireActivity().runOnUiThread {
                Toast.makeText(
                    requireActivity(),
                    "Flash is not available currently",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service!!.shutdown()
    }

    override fun onMapReady(p0: GoogleMap) {

    }

}
//
//class GLRenderer(val context: Context) : GLSurfaceView.Renderer {
//    lateinit var cameraTexture: SurfaceTexture
//    private var cameraTextureId: Int = -1
//    private var overlayBitmap: Bitmap? = null
//    val handler = Handler(Looper.getMainLooper())
//    val updateRunnable = object : Runnable {
//        override fun run() {
//            val bitmap = getViewBitmap(overlayFrameLayout)
//            glRenderer.updateOverlay(bitmap)
//            handler.postDelayed(this, 100) // cập nhật mỗi 100ms
//        }
//    }
//    fun updateOverlay(bitmap: Bitmap) {
//        overlayBitmap = bitmap
//    }
//
//    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//        cameraTextureId = createOESTexture()
//        cameraTexture = SurfaceTexture(cameraTextureId)
//        cameraTexture.setOnFrameAvailableListener {
//            // Trigger render mỗi khi có frame mới từ camera
//            glSurfaceView.requestRender()
//        }
//        // TODO: setup shader, texture cho OES và overlay tại đây
//    }
//
//    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
//        GLES20.glViewport(0, 0, width, height)
//    }
//
//    override fun onDrawFrame(gl: GL10?) {
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
//
//        cameraTexture.updateTexImage()
//
//        // Vẽ camera texture (OES)
//        drawCameraTexture(cameraTextureId)
//
//        // Vẽ overlay nếu có
//        overlayBitmap?.let {
//            drawOverlayTexture(it)
//        }
//    }
//    private fun createOESTexture(): Int {
//        val textures = IntArray(1)
//        GLES20.glGenTextures(1, textures, 0)
//        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
//        return textures[0]
//    }
//
//    private fun drawCameraTexture(textureId: Int) {
//        // TODO: implement GLSL shader để vẽ texture OES
//    }
//
//    private fun drawOverlayTexture(bitmap: Bitmap) {
//        // TODO: convert bitmap to GL texture và vẽ lên full screen
//    }
//    fun getViewBitmap(view: View): Bitmap {
//        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bitmap)
//        view.draw(canvas)
//        return bitmap
//    }
//

//}
