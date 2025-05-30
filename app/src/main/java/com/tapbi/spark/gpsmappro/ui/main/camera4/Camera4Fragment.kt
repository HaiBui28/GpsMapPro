package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera4Binding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import org.checkerframework.checker.units.qual.h
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera4Fragment : BaseBindingFragment<FragmentCamera4Binding, MainViewModel>() {
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var recording: Recording? = null

    private var media3Effect: Media3Effect? = null

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private var imageCapture: ImageCapture? = null

    private lateinit var videoCapture: VideoCapture<Recorder>

    override val layoutId: Int
        get() = R.layout.fragment_camera_4

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initChangeRotation()
        if (allPermissionsGranted()) {
            startCamera()
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

                        Toast.makeText(requireContext(), "Lưu ảnh thành công!", Toast.LENGTH_SHORT)
                            .show()
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


    var rotation = 0f
    var isChangeEffect = false

    private fun initChangeRotation() {
        binding.balanceBarView.setRotationListener(object : BalanceBarView.RotationListener {
            @OptIn(UnstableApi::class)
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                    if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                        if (rotation != this@Camera4Fragment.rotation) {
                            this@Camera4Fragment.rotation = rotation
                            val effect = ImmutableList.Builder<Effect>()
                            val overlay = createOverlayEffect()
                            overlay?.let { effect.add(it) }
                            media3Effect?.setEffects(effect.build())
                        }

                    }

                }
            }
        })
    }

    private fun getPositionByRotation(rotation: Float): String {
        val normalizedRotation = ((rotation % 360) + 360) % 360

        return when {
            normalizedRotation in 315f..360f || normalizedRotation < 45f -> "bottom"
            normalizedRotation in 45f..135f -> "left"
            normalizedRotation in 135f..225f -> "top"
            normalizedRotation in 225f..315f -> "right"
            else -> "bottom"
        }
    }


    @OptIn(UnstableApi::class)
    private fun startCamera() {
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
                 CameraEffect.VIDEO_CAPTURE ,
                cameraExecutor
            ) {

            }
            val effect = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffect()
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


    @UnstableApi
    private fun createOverlayEffect(): androidx.media3.effect.OverlayEffect? {

        val overlayBuilder = ImmutableList.Builder<TextureOverlay>()
        val settings = StaticOverlaySettings.Builder().setRotationDegrees(90f)
            .setAlphaScale(1f)
            .build()
        val bitmapOverlay =
            BitmapOverlay.createStaticBitmapOverlay(
                createHelloTextBitmap(
                    200,
                    300
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.GREEN) // nền trong suốt

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            textSize = height / 10f // scale theo chiều cao bitmap
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        // Vị trí text cách đáy một chút (padding 10 pixels)
        val yPos = height.toFloat()
        canvas.drawText("Hẹ hẹ hẹ", width/2f , yPos, paint)

        return bitmap
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

}