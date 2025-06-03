package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import androidx.core.view.setMargins
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.work.await
import com.google.android.gms.location.LocationServices
import com.google.common.collect.ImmutableList
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera4Binding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.Utils
import com.tapbi.spark.gpsmappro.utils.Utils.dpToPx
import com.tapbi.spark.gpsmappro.utils.clearAllConstraints
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera4Fragment : BaseBindingFragment<FragmentCamera4Binding, MainViewModel>() {
    private var cameraIndex = 0
    private var qualityIndex = 0
    private var audioEnabled = false
    private val cameraCapabilities = mutableListOf<CameraCapability>()
    private var enumerationDeferred: Deferred<Unit>? = null
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()

                provider.unbindAll()
                for (camSelector in arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )) {
                    try {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            @Suppress("DEPRECATION")
                            QualitySelector
                                .getSupportedQualities(camera.cameraInfo)
                                .filter { quality ->
                                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                                        .contains(quality)
                                }.also {
                                    cameraCapabilities.add(CameraCapability(camSelector, it))
                                }
                        }
                    } catch (exc: java.lang.Exception) {
                        Log.e("NVQ", "Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }


    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    var rotation = 0f
    var isChangeEffect = false
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable {
        loadMapRotation(rotation)
        binding.llMap.animate()
            .rotation(rotation)
            .setDuration(300)
            .start()
        updateOverlay()
    }
    private var recording: Recording? = null

    private var media3Effect: Media3Effect? = null

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private var imageCapture: ImageCapture? = null

    private lateinit var videoCapture: VideoCapture<Recorder>

    override val layoutId: Int
        get() = R.layout.fragment_camera_4

    fun loadMapRotation(rotation: Float) {
        binding.llMap.apply {
            val m10dp = dpToPx(10)
            val params = layoutParams as? ConstraintLayout.LayoutParams
            params?.let {
                it.clearAllConstraints()
                when (rotation) {
                    Rotation_2 -> {
                        translationX =
                            -((binding.llMap.width / 2) - (binding.llMap.height / 2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = ConstraintSet.PARENT_ID
                        it.bottomToBottom = ConstraintSet.PARENT_ID
                    }

                    Rotation_3 -> {
                        translationX =
                            ((binding.llMap.width / 2) - (binding.llMap.height / 2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = ConstraintSet.PARENT_ID
                        it.bottomToBottom = ConstraintSet.PARENT_ID
                    }

                    Rotation_4 -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp, dpToPx(10) + App.statusBarHeight, m10dp, m10dp)
                        params.topToTop = ConstraintSet.PARENT_ID
                    }

                    else -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.bottomToBottom = ConstraintSet.PARENT_ID
                    }
                }
                layoutParams = it
            }
        }
    }

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initChangeRotation()
        if (allPermissionsGranted()) {
            lifecycleScope.launch {
                startCamera()
            }
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.fabPicture.setOnClickListener {
            val photoFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            )

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val filePath = photoFile.absolutePath
                        val bitmapCamera = BitmapFactory.decodeFile(filePath)
                        val bitmapOverlay =
                            createHelloTextBitmap(bitmapCamera.width, bitmapCamera.height)
                        val bitmapMerged = mergeBitmap(bitmapCamera, bitmapOverlay)
                        val bitmapResult = bitmapMerged.correctOrientation(-rotation)
                        try {
                            val fos = FileOutputStream(photoFile)
                            bitmapResult.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                            fos.flush()
                            fos.close()
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    try {
                                        val exif = ExifInterface(filePath)
                                        exif.setGpsInfo(location)
                                        exif.saveAttributes()
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            Toast.makeText(
                                requireContext(),
                                "Lưu ảnh thành công!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(
                                requireContext(),
                                "Lỗi lưu ảnh sau merge!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(
                            requireContext(),
                            "Lỗi lưu ảnh: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("Camera", "Image capture failed", exception)
                    }
                }
            )
        }
        binding.fabVideo.setOnClickListener {
            if (recording != null) stopRecording() else startRecording()
        }
    }


    fun Bitmap.correctOrientation(rotation: Float): Bitmap {
        if (rotation == 0f) return this

        val matrix = Matrix()
        matrix.postRotate(rotation)

        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }


    private fun startRecording() {

        val videoOutputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "VID_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date())
            }.mp4"
        )
        val output = FileOutputOptions.Builder(videoOutputFile).build()
        recording = videoCapture.output
            .prepareRecording(requireContext(), output)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Finalize) {
                    Toast.makeText(context, "Video saved: ${output.file}", Toast.LENGTH_SHORT)
                        .show()
                    MediaScannerConnection.scanFile(
                        requireContext(),
                        arrayOf(output.file.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    recording = null
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun initChangeRotation() {
        binding.balanceBarView.setRotationListener(object : BalanceBarView.RotationListener {
            @OptIn(UnstableApi::class)
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                        if (rotation != this@Camera4Fragment.rotation) {
                            this@Camera4Fragment.rotation = rotation
                            handler.removeCallbacks(runnable)
                            handler.postDelayed(runnable, 300)
                        }

                }
            }
        })
    }

    @OptIn(UnstableApi::class)
    fun updateOverlay(){
        Utils.safeDelay(500){
            val effect = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffect(getSizeForQuality(Quality.UHD))
            overlay?.let { effect.add(it) }
            media3Effect?.setEffects(effect.build())
        }
    }



    @OptIn(UnstableApi::class)
    private suspend fun startCamera() {
        enumerationDeferred?.await()
        val aspectRatioStrategry = AspectRatioStrategy(
            AspectRatio.RATIO_16_9,
            AspectRatioStrategy.FALLBACK_RULE_NONE
        )
        val resolutionSelector =
            ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatioStrategry).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture!!)
                .addUseCase(videoCapture)

            media3Effect = Media3Effect(
                requireContext(),
                CameraEffect.VIDEO_CAPTURE,
                cameraExecutor
            ) {}
            val effect = ImmutableList.Builder<Effect>()
            val videoSize = getSizeForQuality(Quality.UHD)

            val overlay = createOverlayEffect(videoSize)
            overlay?.let { effect.add(it) }
            media3Effect?.setEffects(effect.build())
            useCaseGroup.addEffect(media3Effect!!)
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    useCaseGroup.build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, cameraExecutor)
    }
    fun getSizeForQuality(quality: Quality): Size {
        return when (quality) {
            Quality.UHD -> Size(3840, 2160)
            Quality.FHD -> Size(1920, 1080)
            Quality.HD -> Size(1280, 720)
            Quality.SD -> Size(720, 480)
            else -> Size(1280, 720) // fallback
        }
    }

    fun mergeBitmap(bmp1: Bitmap, bmp2: Bitmap): Bitmap {
        val bmOverlay =createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig()!!)
        val canvas = Canvas(bmOverlay)
        canvas.drawBitmap(bmp1, Matrix(), null)
        canvas.drawBitmap(bmp2, 0f, 0f, null)
        bmp1.recycle()
        bmp2.recycle()
        return bmOverlay
    }

    @UnstableApi
    private fun createOverlayEffect(videoSize: Size): androidx.media3.effect.OverlayEffect? {
        Log.e("NVQ", "createOverlayEffect++++")
        val overlayBuilder = ImmutableList.Builder<TextureOverlay>()
        val settings = StaticOverlaySettings.Builder()
            .setAlphaScale(1f)
            .build()
        val bitmapOverlay =
            BitmapOverlay.createStaticBitmapOverlay(
                createHelloTextBitmap(
                    videoSize.height,
                    videoSize.width
                ), settings
            )
        overlayBuilder.add(bitmapOverlay)
        val overlay = overlayBuilder.build()
        return if (overlay.isEmpty()) null else androidx.media3.effect.OverlayEffect(overlay)
    }


    fun createHelloTextBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT) // hoặc Color.WHITE nếu muốn nền trắng

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            textSize = size / 5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        // Căn chữ theo chiều cao
        val yPos = size / 2 - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("Hello", size / 2f, yPos, paint)

        return bitmap
    }


    fun createHelloTextBitmap(width: Int, height: Int): Bitmap {
        // Vẽ View llMap ra bitmap gốc
        val mapBitmap = binding.layoutOverlay.drawToBitmap()

        // Tạo bitmap mới có đúng size mong muốn
        val scaledBitmap = mapBitmap.scale(width, height)

        return scaledBitmap
    }


    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {
        super.onPause()

    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }
    data class CameraCapability(val camSelector: CameraSelector, val qualities:List<Quality>)
}