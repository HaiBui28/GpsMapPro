package com.tapbi.spark.gpsmappro.ui.main.camera

import VideoEncoder
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera3Binding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Camera3Fragment : BaseBindingFragment<FragmentCamera3Binding, MainViewModel>() {


    private lateinit var cameraExecutor: ExecutorService

    private var videoEncoder: VideoEncoder? = null
    private lateinit var overlayBitmap: Bitmap

    private var isRecording = false
    private var isEncoderInitialized = false
    private var isEncoderReleased = false

    private var imageAnalysis: ImageAnalysis? = null

    val aspectRatio = AspectRatio.RATIO_16_9
    private var videoOutputFile: File? = null


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera_3

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()

        overlayBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.RED
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(100f, 100f, 100f, paint)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.btnStart.setOnClickListener {
            startRecording()
        }

        binding.btnStop.setOnClickListener {
            stopRecording()
        }
    }

    var lastFrameTimestamp = 0L
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().setTargetAspectRatio(aspectRatio)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            imageAnalysis = ImageAnalysis.Builder()
                // Không set target resolution để lấy mặc định
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis!!.setAnalyzer(cameraExecutor) { imageProxy ->
                Log.d("chungvv", "setAnalyzer: ")
                val currentTimestamp = System.nanoTime()

                if (!isEncoderInitialized && isRecording) {
                    // Lấy kích thước chưa xoay
                    val rawWidth = imageProxy.width
                    val rawHeight = imageProxy.height
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                    // Xác định kích thước sau xoay (video cần đúng orientation)
                    val (videoWidth, videoHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                        rawHeight to rawWidth // đổi chiều khi xoay 90 hoặc 270 độ
                    } else {
                        rawWidth to rawHeight
                    }

                    val videoOutputFile = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
                    )

                    // Khởi tạo VideoEncoder với kích thước sau xoay
                    videoEncoder = VideoEncoder(videoWidth, videoHeight, videoOutputFile)
                    videoEncoder?.startAudioRecording()
                    isEncoderInitialized = true
                }

                if (isRecording && isEncoderReleased && isEncoderInitialized) {
                    processImage(imageProxy)
                } else {
                    imageProxy.close()
                }
            }


            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun getSupportedResolutions(cameraSelector: CameraSelector): List<Size> {
        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Lấy cameraId tương ứng với CameraSelector
        val cameraId = cameraManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            // So sánh với hướng camera mong muốn
            when {
                cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA ->
                    lensFacing == CameraCharacteristics.LENS_FACING_FRONT

                cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA ->
                    lensFacing == CameraCharacteristics.LENS_FACING_BACK

                else -> true // Nếu là CameraSelector tùy chỉnh
            }
        } ?: return emptyList()

        // Lấy danh sách độ phân giải hỗ trợ
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        return streamConfigurationMap?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.map { Size(it.width, it.height) }
            ?.distinct()
            ?.sortedByDescending { it.width * it.height }
            ?: emptyList()
    }


    private fun startRecording() {
        if (isRecording) return

        isRecording = true
        isEncoderReleased = true
        isEncoderInitialized = false

        Toast.makeText(requireContext(), "Recording started", Toast.LENGTH_SHORT).show()
        isRecording = true
    }

    fun getHighestSupportedVideoSize(context: Context): Size? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(MediaRecorder::class.java)

                return sizes?.maxByOrNull { it.width * it.height }
            }
        }
        return null
    }


    private fun stopRecording() {
        if (!isRecording) return
        Toast.makeText(requireContext(), "Recording stopRecording", Toast.LENGTH_SHORT).show()
        isEncoderReleased = false
        isRecording = false
        videoEncoder?.stop()
        videoEncoder = null

    }

    fun notifyMediaScanner(context: Context, videoFile: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(videoFile.absolutePath),
            arrayOf("video/mp4"),
            null
        )
    }

    private fun processImage(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val cameraBitmap = imageProxy.toBitmap().rotate(rotationDegrees) // hàm rotate bitmap

        videoEncoder?.let {
            drawBitmapToSurface(it.inputSurface, cameraBitmap, overlayBitmap)
            it.drainVideoEncoder()
        }

        imageProxy.close()
    }

    fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun drawBitmapToSurface(surface: Surface, cameraFrame: Bitmap, overlay: Bitmap) {
        try {
            Log.d("chungvv", "drawBitmapToSurface: ")
            val canvas = surface.lockCanvas(null)

            // Vẽ camera frame trước (làm nền)
            canvas.drawBitmap(cameraFrame, null, Rect(0, 0, canvas.width, canvas.height), null)

            // Vẽ overlay lên trên
            val overlaySize = 200
            val left = (canvas.width - overlaySize) / 2
            val top = (canvas.height - overlaySize) / 2
            canvas.drawBitmap(
                overlay,
                null,
                Rect(left, top, left + overlaySize, top + overlaySize),
                null
            )

            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                activity?.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (isRecording) {
            videoEncoder?.stop()
        }
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }
}