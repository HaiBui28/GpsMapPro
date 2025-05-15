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
import android.location.Geocoder
import android.media.ExifInterface
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
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
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCameraBinding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.afterMeasured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : BaseBindingFragment<FragmentCameraBinding, MainViewModel>(),
    OnMapReadyCallback {
    private var googleMap: GoogleMap? = null

    private var imageCapture: ImageCapture? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var cameraControl: CameraControl
    private lateinit var cameraInfo: CameraInfo
    private var isFrontCamera = false
    private var recording: Recording? = null
    private var isBindToLifecycleQRScan = false

    private var selectedAspectRatio = AspectRatio.RATIO_16_9
    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )
    private val REQUEST_CODE_PERMISSIONS = 10
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var cameraExecutor: ExecutorService


    private lateinit var cameraProvider: ProcessCameraProvider

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initGoogleMap()
        barcodeScanner = BarcodeScanning.getClient()

        cameraExecutor = Executors.newSingleThreadExecutor()
        initButtons()

        if (allPermissionsGranted()) {

            startCamera()

        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
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
            takePhoto()
        }

        binding.btnVideo.setOnClickListener {
            if (recording != null) stopRecording() else startRecording()
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
        val videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = if (isFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider?.unbindAll()

        try {
            val camera = cameraProvider?.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )


            cameraControl = camera!!.cameraControl
            cameraInfo = camera.cameraInfo

            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            // Flip nếu là camera trước
            binding.previewView.scaleX = if (isFrontCamera) -1f else 1f

        } catch (e: Exception) {
            Log.e("Camera", "Use case binding failed", e)
        }
    }


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

                    val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
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
                                        val finalBitmap = mergeBitmaps(bitmapCamera, bitmapOverlay)
                                        finalBitmap.saveToGallery(requireContext())

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Đã lưu ảnh với overlay", Toast.LENGTH_SHORT).show()

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
                    Toast.makeText(context, "Lỗi chụp ảnh: ${exception.message}", Toast.LENGTH_SHORT).show()
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







    private fun mergeBitmaps(cameraBitmap: Bitmap, overlayBitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(
            cameraBitmap.width,
            cameraBitmap.height,
            cameraBitmap.config
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)

        // Scale overlay nếu cần cho vừa khung
        val scaledOverlay = Bitmap.createScaledBitmap(
            overlayBitmap,
            cameraBitmap.width,
            overlayBitmap.height * cameraBitmap.width / overlayBitmap.width,
            true
        )

        // Gắn overlay xuống dưới cùng
        val topOffset = cameraBitmap.height - scaledOverlay.height
        canvas.drawBitmap(scaledOverlay, 0f, topOffset.toFloat(), null)

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

    fun Bitmap.mirrorHorizontally(): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1f, 1f) // Lật ngang
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    fun Bitmap.saveToGallery(context: Context) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mirrored_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraXDemo")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                compress(Bitmap.CompressFormat.JPEG, 100, out)
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
        val file = File(requireContext().cacheDir, "video_${System.currentTimeMillis()}.mp4")
        val output = FileOutputOptions.Builder(file).build()

        recording = videoCapture.output
            .prepareRecording(requireContext(), output)
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Finalize) {
                    Toast.makeText(context, "Video saved: ${file.name}", Toast.LENGTH_SHORT).show()
                    recording = null
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun moveToCurrentLocation(googleMap: GoogleMap) {
        Log.d("Haibq", "moveToCurrentLocation: 11")
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        Log.d("Haibq", "moveToCurrentLocation: 1111")
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d("Haibq", "moveToCurrentLocation: " + (location == null))
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    Log.d(
                        "Haibq",
                        "moveToCurrentLocation: " + location.latitude + " " + location.longitude
                    )
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Vị trí mặc định")
                    )
                    try {
                        val geocoder = Geocoder(requireActivity(), Locale.getDefault())
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        Log.d("Haibq", "moveToCurrentLocation: " + addresses!![0].getAddressLine(0))
                        getAddressFromLocation(location.latitude,location.longitude)
                        binding.tvLocation.text = addresses!![0].getAddressLine(0)
//                        if (!addresses.isNullOrEmpty()) {
//                            val city = addresses[0].getAddressLine(0).split(",").run {
//                                if (size >= 2) this[size - 2] else this[0]
//                            }.trim { it <= ' ' }
//                            val cityCut = getFormattedCityName(city)
//                            Hawk.put(Constant.CURRENT_CITY, cityCut)
//                            getWeather(context, isLoadCity, cityCut, dataWeather)
//                        }
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
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isZoomGesturesEnabled = false
        map.uiSettings.isCompassEnabled = true
        Log.d("Haibq", "onMapReady: 111")
        moveToCurrentLocation(map)
        map.setOnCameraIdleListener {

        }
    }


}
