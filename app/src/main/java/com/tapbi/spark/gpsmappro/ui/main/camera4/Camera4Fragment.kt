package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CaptureRequest
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera4Fragment : BaseBindingFragment<FragmentCamera4Binding, MainViewModel>() {
    private var cameraIndex = 0
    private var qualityIndex = 0
    private var audioEnabled = false
    private val cameraCapabilities = mutableListOf<CameraCapability>()
    private var enumerationDeferred: Deferred<Unit>? = null
    private var isAspectRatio16_9 = true

    private lateinit var cameraControl: CameraControl
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
            Log.d(
                "chungvv",
                "hachung binding.previewView.display.rotation: ${binding.previewView.display.rotation}, /rotation: $rotation"
            )
            imageCapture?.setTargetRotation(getRotationCamera())
            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    @SuppressLint("RestrictedApi")
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri ?: return

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val inputStream =
                                    requireContext().contentResolver.openInputStream(savedUri)
                                var originalBitmap = BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()

                                if (originalBitmap == null) return@launch
                                originalBitmap = rotateBitmapIfRequired(
                                    originalBitmap,
                                    savedUri,
                                    requireContext()
                                )
                                // Tạo bitmap từ view llMap scale đúng với ảnh gốc


                                // Merge 2 bitmap
                                val mergedBitmap = mergeBitmapAtBottomWithMargin(
                                    originalBitmap,
                                    getBitmapFromView(binding.llMap),
                                    marginBottom = 20
                                )

                                // Ghi đè lại ảnh gốc
                                requireContext().contentResolver.openOutputStream(savedUri)
                                    ?.use { outStream ->
                                        mergedBitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            100,
                                            outStream
                                        )
                                    }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Ảnh đã được lưu với overlay",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                MediaScannerConnection.scanFile(
                                    requireContext(),
                                    arrayOf(outputFileResults.savedUri?.path),
                                    arrayOf("*/jpg"),
                                    null
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Lỗi: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {

                    }
                }
            )
        }
        binding.fabVideo.setOnClickListener {
            if (recording != null) stopRecording() else startRecording()
        }

        binding.fabFront.setOnClickListener {
            toggleAspectRatio()
        }
    }


    private fun toggleAspectRatio() {
        isAspectRatio16_9 = !isAspectRatio16_9
        lifecycleScope.launch {
            restartCameraWithNewAspectRatio()
        }

        val aspectRatioText = if (isAspectRatio16_9) "16:9" else "4:3"
        Toast.makeText(requireContext(), "Chuyển sang tỷ lệ $aspectRatioText", Toast.LENGTH_SHORT)
            .show()
    }

    private suspend fun restartCameraWithNewAspectRatio() {
        try {
            withContext(Dispatchers.Main) {
                val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()
                cameraProvider.unbindAll()
            }
            startCamera()
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Lỗi khi chuyển đổi: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    fun rotateBitmapIfRequired(bitmap: Bitmap, imageUri: Uri, context: Context): Bitmap {
        val inputStream = context.contentResolver.openInputStream(imageUri) ?: return bitmap
        val exif = ExifInterface(inputStream)

        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        inputStream.close()

        val matrix = Matrix()
        Log.d("chungvv", "rotateBitmapIfRequired: $orientation")
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap // Không cần xoay
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    fun getBitmapFromView(view: View): Bitmap {
        val width = view.width
        val height = view.height

        if (width == 0 || height == 0) {
            // View chưa đo/layout xong, bạn có thể ép đo lại nếu cần
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    fun mergeBitmapAtBottomWithMargin(
        originalBitmap: Bitmap,
        overlayBitmap: Bitmap,
        marginBottom: Int,
        maxOverlayHeightRatio: Float = 0.33f
    ): Bitmap {
        val result = createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            originalBitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        // Vẽ ảnh gốc
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        val targetWidth = originalBitmap.width.toFloat()
        val maxHeight = originalBitmap.height * maxOverlayHeightRatio

        // Scale theo width
        var scale = targetWidth / overlayBitmap.width.toFloat()
        var scaledHeight = overlayBitmap.height * scale

        // Nếu chiều cao sau scale > maxHeight thì scale theo chiều cao
        if (scaledHeight > maxHeight) {
            scale = maxHeight / overlayBitmap.height.toFloat()
            scaledHeight = maxHeight
        }

        val scaledOverlay =
            overlayBitmap.scale((overlayBitmap.width * scale).toInt(), scaledHeight.toInt())

        val left = (originalBitmap.width - scaledOverlay.width) / 2f
        val top = originalBitmap.height - scaledHeight - marginBottom

        canvas.drawBitmap(scaledOverlay, left, top, null)

        return result
    }

    fun getRotationCamera(): Int {
        return when (rotation.toInt()) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270, (-90) -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }
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
    fun updateOverlay() {
        Utils.safeDelay(500) {
            val effect = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffect(getSizeForQuality(Quality.UHD))
            overlay?.let { effect.add(it) }
            media3Effect?.setEffects(effect.build())
        }
    }


    @OptIn(UnstableApi::class, androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private suspend fun startCamera() {
        enumerationDeferred?.await()
        val targetAspectRatio = if (isAspectRatio16_9) {
            AspectRatio.RATIO_16_9
        } else {
            AspectRatio.RATIO_4_3
        }

        changeLayoutParams()

        val aspectRatioStrategy = AspectRatioStrategy(
            targetAspectRatio,
            AspectRatioStrategy.FALLBACK_RULE_AUTO
        )

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // --- Cân bằng trắng: bạn có thể thay đổi sang AUTO, INCANDESCENT, etc.
            val awbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO

            // --- Preview Builder + WB ---
            val previewBuilder = Preview.Builder()
                .setResolutionSelector(resolutionSelector)

            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)

            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // --- ImageCapture Builder + WB ---
            val imageCaptureBuilder = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(binding.previewView.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            Camera2Interop.Extender(imageCaptureBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)

            imageCapture = imageCaptureBuilder.build()

            // --- VideoCapture với aspect ratio tương ứng ---
            val videoQuality =
                if (isAspectRatio16_9) Quality.UHD else Quality.FHD // 4:3 thường dùng FHD
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(videoQuality))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            // --- UseCaseGroup ---
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .addUseCase(videoCapture)

            // --- Media3 Effects ---
            val videoSize = getSizeForQuality(videoQuality)
            val effectBuilder = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffect(videoSize)

            media3Effect = Media3Effect(
                requireContext(),
                CameraEffect.VIDEO_CAPTURE,
                cameraExecutor
            ) {}

            overlay?.let { effectBuilder.add(it) }
            media3Effect?.setEffects(effectBuilder.build())
            useCaseGroup.addEffect(media3Effect!!)

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCaseGroup.build()
                )

                cameraControl = camera.cameraControl
                val cameraInfo = camera.cameraInfo

                // --- Exposure control ---
                val exposureState = cameraInfo.exposureState
                val minExposure = exposureState.exposureCompensationRange.lower
                val maxExposure = exposureState.exposureCompensationRange.upper
                val currentExposure = exposureState.exposureCompensationIndex

                binding.SeekBarAs.max = maxExposure - minExposure
                binding.SeekBarAs.progress = currentExposure - minExposure

                binding.SeekBarAs.setOnSeekBarChangeListener(object :
                    SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        val exposureValue = progress + minExposure
                        cameraControl.setExposureCompensationIndex(exposureValue)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                // --- Zoom control ---
                cameraInfo.zoomState.observe(viewLifecycleOwner) { zoomState ->
                    val minZoom = zoomState.minZoomRatio
                    val maxZoom = zoomState.maxZoomRatio
                    val currentZoom = zoomState.zoomRatio

                    val progress = ((currentZoom - minZoom) / (maxZoom - minZoom) * 100).toInt()
                    if (binding.zoomSeekBar.progress != progress) {
                        binding.zoomSeekBar.progress = progress
                    }

                    binding.zoomSeekBar.setOnSeekBarChangeListener(object :
                        SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (fromUser) {
                                val targetZoom = minZoom + (progress / 100f) * (maxZoom - minZoom)
                                cameraControl.setZoomRatio(targetZoom)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, cameraExecutor)
    }

    private fun changeLayoutParams() {
        binding.previewView.apply {
            val params: ConstraintLayout.LayoutParams =
                layoutParams as ConstraintLayout.LayoutParams
            val parameter = if (isAspectRatio16_9) {
                "9:16"
            } else {
                "3:4"
            }
            params.dimensionRatio = parameter
            layoutParams = params
        }
    }

    fun getSizeForQuality(quality: Quality): Size {
        return if (isAspectRatio16_9) {
            when (quality) {
                Quality.UHD -> Size(3840, 2160)
                Quality.FHD -> Size(1920, 1080)
                Quality.HD -> Size(1280, 720)
                Quality.SD -> Size(720, 480)
                else -> Size(1280, 720)
            }
        } else {
            when (quality) {
                Quality.UHD -> Size(2880, 2160)
                Quality.FHD -> Size(1440, 1080)
                Quality.HD -> Size(960, 720)
                Quality.SD -> Size(640, 480)
                else -> Size(960, 720)
            }
        }
    }

    @UnstableApi
    private fun createOverlayEffect(videoSize: Size): androidx.media3.effect.OverlayEffect? {
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

    data class CameraCapability(val camSelector: CameraSelector, val qualities: List<Quality>)
}