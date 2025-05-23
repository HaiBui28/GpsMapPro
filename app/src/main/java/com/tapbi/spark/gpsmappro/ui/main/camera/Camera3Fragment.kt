package com.tapbi.spark.gpsmappro.ui.main.camera

import VideoEncoder
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.AspectRatio.RATIO_DEFAULT
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.setMargins
import androidx.lifecycle.Lifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera3Binding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.Utils.dpToPx
import com.tapbi.spark.gpsmappro.utils.clearAllConstraints
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Camera3Fragment : BaseBindingFragment<FragmentCamera3Binding, MainViewModel>(),
    OnMapReadyCallback  {
    private var googleMap: GoogleMap? = null
    private lateinit var cameraExecutor: ExecutorService

    private var videoEncoder: VideoEncoder? = null
    private lateinit var overlayBitmap: Bitmap

    private var isRecording = false
    private var isEncoderInitialized = false
    private var isEncoderReleased = false

    private var imageAnalysis: ImageAnalysis? = null

    val aspectRatio = AspectRatio.RATIO_16_9
    private var videoOutputFile: File? = null
    var rotation = 0f
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable {
        loadMapRotation(rotation)
        binding.llMap.animate()
            .rotation(rotation)
            .setDuration(300)
            .start()
    }
    fun loadMapRotation(rotation: Float){
        binding.llMap.apply {
            val m10dp = dpToPx(10)
            val params = layoutParams as? ConstraintLayout.LayoutParams
            params?.let {
                it.clearAllConstraints()
                when(rotation){
                    Rotation_2 -> {
                        translationX = -((binding.llMap.width/2) - (binding.llMap.height/2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = binding.viewFinder.id
                        it.bottomToBottom = binding.viewFinder.id
                    }
                    Rotation_3 -> {
                        translationX = ((binding.llMap.width/2) - (binding.llMap.height/2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = binding.viewFinder.id
                        it.bottomToBottom = binding.viewFinder.id
                    }
                    Rotation_4 -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp,dpToPx(10)+ App.statusBarHeight,m10dp,m10dp)
                        params.topToTop = binding.viewFinder.id
                    }
                    else ->{
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.bottomToBottom = binding.viewFinder.id
                    }
                }
                layoutParams = it
            }
        }
    }


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
        initGoogleMap()
        cameraExecutor = Executors.newSingleThreadExecutor()
        initChangeRotation()
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
    private fun initChangeRotation(){
        binding.balanceBarView.setRotationListener(object :BalanceBarView.RotationListener{
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED){
                    Log.e("NVQ","NVQ 23456789 rotation: $rotation")
                    this@Camera3Fragment.rotation = rotation
                    handler.removeCallbacks(runnable)
                    handler.postDelayed(runnable, 300)
                }
            }
        })
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
                    Log.e("NVQ","123456852 ${videoWidth} // ${videoHeight}")

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
            val margin = dpToPx(10)
            val scale = (canvas.width.toFloat() - 2*margin)/overlay.width.toFloat()
            var left = 0
            var top = 0
            val w =(overlay.width*scale).toInt()
            val h =(overlay.height*scale).toInt()
            var rotatedOverlay = overlay
            when (rotation) {
                Rotation_2 -> {
                    rotatedOverlay = rotateBitmap(overlay, 90f)
                    left = margin
                    top = (canvas.height - w)/2
                }
                Rotation_3 -> {
                    rotatedOverlay = rotateBitmap(overlay, -90f)
                    left = canvas.width - margin - h
                    top = (canvas.height - w)/2
                }
                Rotation_4 -> {
                    rotatedOverlay = rotateBitmap(overlay, 180f)
                    left = margin
                    top = margin
                }
                else -> {
                    left = margin
                    top = canvas.height - h - margin
                }
            }
            canvas.drawBitmap(
                rotatedOverlay,
                null,
                Rect(left, top, left + if (rotation in listOf(Rotation_2, Rotation_3)) h else w
                    , top + if (rotation in listOf(Rotation_2, Rotation_3)) w else h),
                null
            )
            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
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
    private fun initGoogleMap() {
        activity?.let {
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(this)
        }
    }

    fun loadBitmapLocation(){
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
                        overlayBitmap = binding.llMap.drawToBitmap()
                    }, 80) // delay nhỏ để đảm bảo ảnh đã render
                }
            }
        }
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

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
    fun moveToCurrentLocation(googleMap: GoogleMap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
//            googleMap?.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
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
}