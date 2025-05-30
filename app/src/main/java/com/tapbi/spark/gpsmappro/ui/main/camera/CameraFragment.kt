package com.tapbi.spark.gpsmappro.ui.main.camera

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.location.Geocoder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.drawToBitmap
import androidx.core.view.setMargins
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCameraBinding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_1
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import com.tapbi.spark.gpsmappro.ui.base.BaseActivity
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainActivity
import com.tapbi.spark.gpsmappro.ui.main.MainActivity.Companion.ACCESS_FINE_LOCATION_REQUEST_CODE
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.MediaUtil
import com.tapbi.spark.gpsmappro.utils.SimpleLocationManager
import com.tapbi.spark.gpsmappro.utils.Utils.dpToPx
import com.tapbi.spark.gpsmappro.utils.Utils.mergeBitmaps
import com.tapbi.spark.gpsmappro.utils.afterMeasured
import com.tapbi.spark.gpsmappro.utils.checkLocationPermission
import com.tapbi.spark.gpsmappro.utils.clearAllConstraints
import com.tapbi.spark.gpsmappro.utils.correctOrientation
import com.tapbi.spark.gpsmappro.utils.mirrorHorizontally
import com.tapbi.spark.gpsmappro.utils.saveToGalleryWithLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : BaseBindingFragment<FragmentCameraBinding, MainViewModel>(),
    OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    var rotation = 0f
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable {
//        loadMapRotation(rotation)
//        binding.llMap.animate()
//            .rotation(rotation)
//            .setDuration(300)
//            .start()
    }
    private var imageCapture: ImageCapture? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var cameraControl: CameraControl
    private lateinit var cameraInfo: CameraInfo
    private var isFrontCamera = false
    private var recording: Recording? = null
    private var isBindToLifecycleQRScan = false
    val margin = dpToPx(10)

    private var selectedAspectRatio = AspectRatio.RATIO_16_9
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val REQUEST_CODE_PERMISSIONS = 10
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private var simpleLocationManager: SimpleLocationManager? = null
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var cameraExecutor: ExecutorService
    private var overlayBitmap: Bitmap? = null

    private lateinit var cameraProvider: ProcessCameraProvider

    private var currentRotation = 0
    private var lastRotation = -1
    private var rotatedOverlay: Bitmap? = null

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initGoogleMap()
//        binding.customImahe.setWidthHeight(300, 300)
        barcodeScanner = BarcodeScanning.getClient()

        cameraExecutor = Executors.newSingleThreadExecutor()
        initButtons()
        initLocation()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        initChangeRotation()
        binding.btnMap.setOnClickListener {
            (activity as MainActivity).navigate(R.id.action_cameraFragment_to_googleMapFragment)
        }
//        Log.d("Haibq", "onCreatedView: "+ MediaUtil.getDevicePhotosByFolder(requireActivity()).size)
        App.instance?.let { viewModel.getListLocationPhoto(it) }
    }

    fun initLocation() {
        if (simpleLocationManager == null) {
            (activity as? BaseActivity)?.let {
                simpleLocationManager = SimpleLocationManager(it)
            }
        }
        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        (activity as? BaseActivity)?.apply {
            if (checkLocationPermission()) {
                simpleLocationManager?.requestLocationUpdates()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    ACCESS_FINE_LOCATION_REQUEST_CODE
                )
            }
        }
    }

    private fun initChangeRotation() {
        binding.balanceBarView.setRotationListener(object : BalanceBarView.RotationListener {
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                    Log.e("NVQ", "NVQ 23456789 rotation: $rotation")
                    this@CameraFragment.rotation = rotation
                    handler.removeCallbacks(runnable)
                    handler.postDelayed(runnable, 300)
                }
            }
        })
    }


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
                        it.topToTop = binding.previewView.id
                        it.bottomToBottom = binding.previewView.id
                    }

                    Rotation_3 -> {
                        translationX =
                            ((binding.llMap.width / 2) - (binding.llMap.height / 2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = binding.previewView.id
                        it.bottomToBottom = binding.previewView.id
                    }

                    Rotation_4 -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp, dpToPx(10) + App.statusBarHeight, m10dp, m10dp)
                        params.topToTop = binding.previewView.id
                    }

                    else -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.bottomToBottom = binding.previewView.id
                    }
                }
                layoutParams = it
            }
        }
    }

    private fun initGoogleMap() {
        activity?.let {
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(this)
        }
    }


    private fun animateAspectRatioChange(aspectRatio: Int) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val targetHeight = when (aspectRatio) {
            AspectRatio.RATIO_4_3 -> (screenWidth / 3f * 4).toInt()
            AspectRatio.RATIO_16_9 -> (screenWidth / 9f * 16).toInt()
            else -> screenWidth
        }

        val currentHeight = binding.previewView.height
        val valueAnimator = ValueAnimator.ofInt(currentHeight, targetHeight)
        valueAnimator.duration = 400 // ms
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.addUpdateListener {
            val newHeight = it.animatedValue as Int
            val params = binding.previewView.layoutParams
            params.height = newHeight
            binding.previewView.layoutParams = params
        }
        valueAnimator.start()
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {
        viewModel.listLocationPhoto.observe(viewLifecycleOwner) {
            Log.d("Haibq", "observerLiveData: "+ it.size)
            if (App.instance?.foldersMap?.isEmpty() == true){
                App.instance?.foldersMap?.addAll(it)
            }
            Log.d("Haibq", "observerLiveData: "+ it.size)
        }
    }

    override fun onBackPressed() {

    }


    @SuppressLint("ClickableViewAccessibility")
    private fun initButtons() {

        binding.btnChangeAspectRatio.setOnClickListener {
            selectedAspectRatio = if (selectedAspectRatio == AspectRatio.RATIO_16_9) {
                AspectRatio.RATIO_4_3
            } else {
                AspectRatio.RATIO_16_9
            }
            startCamera()
            animateAspectRatioChange(selectedAspectRatio)

        }

        binding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = binding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)

                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS) // Tự động hủy sau 3s
                    .build()

                cameraControl.startFocusAndMetering(action)

                // (Optional) Hiển thị hiệu ứng vòng tròn tại điểm chạm
                showFocusRing(event.x, event.y)
            }
            true
        }

        binding.btnCapture.setOnClickListener {
//            takePhoto()
        }

        binding.btnVideo.setOnClickListener {
            if (recording != null) stopRecording() else startRecording()
//            binding.llMap.translationX -= 10
//            Log.e("NVQ","NVQ 123456789 ${binding.llMap.translationX}")
        }

        binding.btnFlash.setOnClickListener {
            cameraControl.enableTorch(true)
        }

        binding.btnSwitchCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }

        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                cameraControl.setLinearZoom(value / 100f)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }


    private fun showFocusRing(x: Float, y: Float) {
        val focusView = binding.focusView
        focusView.translationX = x - focusView.width / 2
        focusView.translationY = y - focusView.height / 2
        focusView.visibility = View.VISIBLE
        focusView.animate().alpha(0f).setDuration(800).withEndAction {
            focusView.visibility = View.GONE
            focusView.alpha = 1f
        }.start()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            Log.d("chungvv", "cameraProviderFuture: ")
            cameraProvider = cameraProviderFuture.get()

            binding.previewView.doOnLayout {
                bindCameraUseCases()
                animateAspectRatioChange(selectedAspectRatio)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("RestrictedApi")
    private fun bindCameraUseCases() {
        Log.d("chungvv", "bindCameraUseCases: ")

        val aspectRatioStrategy =
            AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(selectedAspectRatio)
            .build()
        Log.d("chungvv", "imageCapture : $imageCapture")
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val handlerThread = HandlerThread("OverlayEffectThread").apply { start() }
        val handler = Handler(handlerThread.looper)

        val overlayEffect = OverlayEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            0,
            handler
        ) { throwable ->
            throwable.printStackTrace()
        }.apply {
            setOnDrawListener { frame ->
                val canvas = frame.overlayCanvas.apply {
                    drawColor(Color.TRANSPARENT)
                }

                // 1. Tạo transformation matrix thủ công
                val matrix = Matrix()
                val viewRatio = binding.previewView.width.toFloat() / binding.previewView.height.toFloat()
                val bufferRatio = frame.size.width.toFloat() / frame.size.height.toFloat()

                if (bufferRatio > viewRatio) {
                    // Crop theo chiều ngang
                    val scale = binding.previewView.height.toFloat() / frame.size.height.toFloat()
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(
                        (binding.previewView.width - frame.size.width * scale) / 2f,
                        0f
                    )
                } else {
                    // Crop theo chiều dọc
                    val scale = binding.previewView.width.toFloat() / frame.size.width.toFloat()
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(
                        0f,
                        (binding.previewView.height - frame.size.height * scale) / 2f
                    )
                }

                // 2. Áp dụng cho tọa độ
                val squareSize = 600f
                val marginBottom = 100f
                val points = floatArrayOf(
                    frame.size.width / 2f - squareSize/2,  // left
                    frame.size.height - marginBottom - squareSize, // top
                    frame.size.width / 2f + squareSize/2,  // right
                    frame.size.height - marginBottom       // bottom
                )
                matrix.mapPoints(points)

                // 3. Vẽ hình vuông đã transform
                Paint().apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 20f
                    canvas.drawRect(
                        points[0], points[1],  // left, top
                        points[2], points[3],  // right, bottom
                        this
                    )
                }

                // 4. Debug bằng điểm màu xanh
                Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.FILL
                    val center = floatArrayOf(frame.size.width/2f, frame.size.height - marginBottom)
                    matrix.mapPoints(center)
                    canvas.drawCircle(center[0], center[1], 30f, this)
                }

                true
            }
        }
        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(videoCapture)
            .addEffect(overlayEffect)
            .build()


        val cameraSelector = if (isFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider?.unbindAll()

        try {
            val camera = cameraProvider?.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                useCaseGroup
            )


            cameraControl = camera!!.cameraControl
            cameraInfo = camera.cameraInfo
            binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            // Flip nếu là camera trước
            binding.previewView.scaleX = if (isFrontCamera) -1f else 1f

        } catch (e: Exception) {
            Log.e("Camera", "Use case binding failed", e)
        }
    }

    fun createGreenBitmap( width : Int = 300,
                           height : Int = 300): Bitmap {

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }


    fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private data class Quad(
        val rotatedBitmap: Bitmap,
        val drawWidth: Int,
        val drawHeight: Int,
        val left: Int,
        val top: Int
    )


    private fun startQRScanning() {

        if (!isBindToLifecycleQRScan) {
            isBindToLifecycleQRScan = true
            val analysisUseCase = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .build()

            analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                analysisUseCase
            )
        }

    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image  // Access the image from the proxy
        if (mediaImage != null) {
            // Use the rotation degrees from the image info
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Create an InputImage with the media image and its rotation degrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // Process the image for QR codes
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.displayValue
                        if (rawValue != null) {
                            // Handle the QR code data (e.g., show it in a Toast or log it)
                            Log.d("QR Code", "Scanned value: $rawValue")
                            Toast.makeText(context, "QR Code: $rawValue", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QR Code", "QR code scanning failed: ${e.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()  // Close the image to avoid memory leaks
                }
        }
    }


    private fun startAutoFocusCenter() {
//        val factory = binding.previewView.meteringPointFactory
//        val point = factory.createPoint(0.5f, 0.5f) // giữa màn hình
//
//        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
//            .disableAutoCancel() // Giữ lấy nét liên tục
//            .build()
//
//        cameraControl.startFocusAndMetering(action)


        binding.previewView.afterMeasured {
            val autoFocusPoint = SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(.5f, .5f)
            try {
                val autoFocusAction = FocusMeteringAction.Builder(
                    autoFocusPoint,
                    FocusMeteringAction.FLAG_AF
                ).apply {
                    //start auto-focusing after 2 seconds
                    setAutoCancelDuration(2, TimeUnit.SECONDS)
                }.build()
                cameraControl.startFocusAndMetering(autoFocusAction)
            } catch (e: CameraInfoUnavailableException) {
                Timber.e("hachung CameraInfoUnavailableException: $e")
            }
        }
    }


    private fun takePhoto() {
        val file = File(requireContext().cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        animateFlash()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val filePath = file.absolutePath
                    val bitmapCamera = BitmapFactory.decodeFile(filePath)
                        .correctOrientation(filePath)
                        .let { if (isFrontCamera) it.mirrorHorizontally() else it }

                    val mapFragment =
                        childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                    mapFragment.getMapAsync { googleMap ->
                        googleMap.snapshot { mapBitmap ->
                            if (mapBitmap != null) {
                                // 👉 Gán mapBitmap vào ImageView, ẩn fragment
                                binding.imMapSnapshot.setImageBitmap(mapBitmap)
                                binding.imMapSnapshot.visibility = View.VISIBLE
                                mapFragment.requireView().visibility = View.GONE

                                // 👉 Chờ 1 frame để hệ thống render lại
                                binding.llMap.postDelayed({
                                    val bitmapOverlay = binding.llMap.drawToBitmap()

                                    // 👉 Gộp và lưu ảnh ở background
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val finalBitmap =
                                            mergeBitmaps(bitmapCamera, bitmapOverlay, rotation)
                                        finalBitmap.saveToGalleryWithLocation(
                                            requireContext(),
                                            simpleLocationManager?.getLocation(),
                                            rotation
                                        )

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Đã lưu ảnh với overlay",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            // 👉 Khôi phục MapFragment sau khi chụp xong (optional)
                                            binding.imMapSnapshot.visibility = View.GONE
                                            mapFragment.requireView().visibility = View.VISIBLE
                                        }
                                    }
                                }, 80) // delay nhỏ để đảm bảo ảnh đã render
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        context,
                        "Lỗi chụp ảnh: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }


    fun replaceMapFragmentWithSnapshot(mapBitmap: Bitmap) {
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(mapBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val cardView = binding.cardView // cardView chứa fragment
        cardView.removeAllViews()
        cardView.addView(imageView)
    }


    private fun mergeBitmaps2(
        cameraBitmap: Bitmap,
        overlayBitmap: Bitmap,
        rotation: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(
            cameraBitmap.width,
            cameraBitmap.height,
            cameraBitmap.config
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)
        val m10dp = dpToPx(10)

        // ✅ Sử dụng Float để tránh chia lấy phần nguyên
        val availableWidth = cameraBitmap.width - 2 * m10dp
        val scale = availableWidth.toFloat() / overlayBitmap.width.toFloat()

        val newOverlayWidth = (overlayBitmap.width * scale).toInt()
        val newOverlayHeight = (overlayBitmap.height * scale).toInt()

        val scaledOverlay = Bitmap.createScaledBitmap(
            overlayBitmap,
            newOverlayWidth,
            newOverlayHeight,
            true
        )
        val topOffset = cameraBitmap.height - newOverlayHeight - m10dp
        canvas.drawBitmap(scaledOverlay, dpToPx(10).toFloat(), topOffset.toFloat(), null)

        return result
    }

    private fun mergeBitmaps(cameraBitmap: Bitmap, overlayBitmap: Bitmap, rotation: Float): Bitmap {
        val result = Bitmap.createBitmap(
            cameraBitmap.width,
            cameraBitmap.height,
            cameraBitmap.config
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)
        val margin = dpToPx(10).toFloat()

        // Scale overlay cho vừa chiều rộng
        val availableWidth = cameraBitmap.width - 2 * margin
        val scale = availableWidth / overlayBitmap.width.toFloat()

        val newOverlayWidth = (overlayBitmap.width * scale).toInt()
        val newOverlayHeight = (overlayBitmap.height * scale).toInt()

        val scaledOverlay = Bitmap.createScaledBitmap(
            overlayBitmap,
            newOverlayWidth,
            newOverlayHeight,
            true
        )

        // Tạo matrix xoay quanh tâm ảnh overlay
        val matrix = Matrix()
        matrix.postScale(1f, 1f) // scale giữ nguyên
        matrix.postRotate(rotation, newOverlayWidth / 2f, newOverlayHeight / 2f)

        val rotatedOverlay = Bitmap.createBitmap(
            scaledOverlay,
            0,
            0,
            newOverlayWidth,
            newOverlayHeight,
            matrix,
            true
        )

        // Vẽ rotatedOverlay vào vị trí thích hợp
        val left: Float
        val top: Float
        when (rotation) {
            Rotation_2 -> {
                left = margin
                top = cameraBitmap.height.toFloat() / 2 - rotatedOverlay.height.toFloat() / 2
            }

            Rotation_3 -> {
                left = cameraBitmap.width.toFloat() - rotatedOverlay.width.toFloat() - margin
                top = cameraBitmap.height.toFloat() / 2 - rotatedOverlay.height.toFloat() / 2
            }

            Rotation_4 -> {
                left = margin
                top = margin
            }

            else -> {
                left = margin
                top = cameraBitmap.height - rotatedOverlay.height - margin
            }
        }


        canvas.drawBitmap(rotatedOverlay, left, top, null)

        return result
    }

    fun Bitmap.correctOrientation(filePath: String): Bitmap {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }


    fun saveFileToGallery(context: Context, file: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraXDemo")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                file.inputStream().copyTo(out)
            }
        }
    }


    private fun animateFlash() {
        val flashOverlay = View(requireContext()).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        (requireActivity().window.decorView as ViewGroup).addView(flashOverlay)

        flashOverlay.animate()
            .alpha(0.6f)
            .setDuration(50)
            .withEndAction {
                flashOverlay.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        (requireActivity().window.decorView as ViewGroup).removeView(flashOverlay)
                    }
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
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
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

    fun moveToCurrentLocation(googleMap: GoogleMap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
//            googleMap?.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d("Haibq", "moveToCurrentLocation: 111111")
                if (location != null) {
                    Log.d("Haibq", "moveToCurrentLocation: ")
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Vị trí mặc định")
                    )
                    try {
                        val geocoder = Geocoder(requireActivity(), Locale.getDefault())
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        getAddressFromLocation(location.latitude, location.longitude)
                        binding.tvLocation.text = addresses!![0].getAddressLine(0)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.d("Haibq", "moveToCurrentLocation: 1")
        }
    }

    fun getAddressFromLocation(lat: Double, lon: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val fullAddress = address.getAddressLine(0) // địa chỉ đầy đủ
                val street = address.thoroughfare            // tên đường
                val district = address.subLocality           // phường
                val city = address.locality                  // thành phố
                val province = address.adminArea             // tỉnh/thành
                val country = address.countryName            // quốc gia

                Log.d("Haibq", "Địa chỉ: $fullAddress")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(context, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_HYBRID
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isZoomGesturesEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        val zoomLevel = 5f
        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
        Log.d("Haibq", "onMapReady: 111")
        moveToCurrentLocation(map)
        map.setOnCameraIdleListener {
        }
        loadBitmapLocation()
    }

    fun loadBitmapLocation() {
//        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
//        mapFragment.getMapAsync { googleMap ->
//            googleMap.snapshot { mapBitmap ->
//                if (mapBitmap != null) {
//                    // 👉 Gán mapBitmap vào ImageView, ẩn fragment
//                    binding.imMapSnapshot.setImageBitmap(mapBitmap)
//                    binding.imMapSnapshot.visibility = View.VISIBLE
//                    mapFragment.requireView().visibility = View.GONE
//
//                    // 👉 Chờ 1 frame để hệ thống render lại
//                    binding.llMap.postDelayed({
//                        binding.llMap.visibility=View.INVISIBLE
//                        overlayBitmap = binding.llMap.drawToBitmap()
//
//                    }, 80) // delay nhỏ để đảm bảo ảnh đã render
//                }
//            }
//        }
    }


}
