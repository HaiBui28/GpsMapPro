package com.tapbi.spark.gpsmappro.ui.main.homecamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.location.Geocoder
import android.location.Location
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import androidx.core.view.setMargins
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.common.collect.ImmutableList
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.common.Constant
import com.tapbi.spark.gpsmappro.common.Constant.CAMERA_INDEX
import com.tapbi.spark.gpsmappro.common.Constant.QUALITY_INDEX
import com.tapbi.spark.gpsmappro.data.local.SharedPreferenceHelper
import com.tapbi.spark.gpsmappro.databinding.FragmentHomeCameraBinding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.Utils
import com.tapbi.spark.gpsmappro.utils.Utils.dpToPx
import com.tapbi.spark.gpsmappro.utils.Utils.getSizeForQuality
import com.tapbi.spark.gpsmappro.utils.Utils.getVideoSize
import com.tapbi.spark.gpsmappro.utils.clearAllConstraints
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeCameraFragment : BaseBindingFragment<FragmentHomeCameraBinding, MainViewModel>(),
    OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private var currentLocation: Location? = null
    var rotation = 0f
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable {
        loadMapRotation(rotation)
        binding.llMap.rotation = rotation
        updateOverlay()
    }
    val loadMapImageRunnable = Runnable {
        loadBitmapLocation() {
            updateOverlay()
        }
    }
    private var cameraIndex: Int
        get(): Int = SharedPreferenceHelper.getInt(CAMERA_INDEX, 0)
        set(index) = SharedPreferenceHelper.putInt(CAMERA_INDEX, index)
    private var qualityIndex: Int
        get(): Int = SharedPreferenceHelper.getInt(QUALITY_INDEX, 0)
        set(index) = SharedPreferenceHelper.putInt(QUALITY_INDEX, index)
    private var imageCapture: ImageCapture? = null
    private var media3Effect: Media3Effect? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private val cameraCapabilities = mutableListOf<CameraCapability>()
    private var enumerationDeferred: Deferred<Unit>? = null
    private var recording: Recording? = null
    private val cameraExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    var camera: Camera? = null

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_home_camera

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initView()
        initListener()
        initGoogleMap()
        initChangeRotation()
    }
    var isPausing = false
    @SuppressLint("ClickableViewAccessibility")
    private fun initListener() {
        binding.fabFront.setOnClickListener {
            cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
            lifecycleScope.launch {
                startCamera()
            }
        }
        binding.fabVideo.setOnClickListener {
            if (recording != null) stopRecording() else {
                if (SharedPreferenceHelper.containKey(Constant.VIDEO_WIDTH)){
                    startRecording()
                }
            }
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
                cameraExecutor,
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
                        Timber.e("Camera Image capture failed $exception")
                    }
                }
            )
        }
        binding.fabRatio.setOnClickListener {
            if (isPausing){
                recording?.let {
                    isPausing = false
                    it.resume()
                }
            } else {
                recording?.let {
                    isPausing = true
                    it.pause()
                }
            }

        }
        binding.previewView.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

            val factory = binding.previewView.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)

            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(5, TimeUnit.SECONDS) // tự bỏ focus sau 5s
                .build()

            camera?.cameraControl?.startFocusAndMetering(action)
            return@setOnTouchListener true
        }
    }


    private fun initChangeRotation() {
        binding.balanceBarView.setRotationListener(object : BalanceBarView.RotationListener {
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                    Timber.e("NVQ NVQ 23456789 rotation: $rotation")
                    this@HomeCameraFragment.rotation = rotation
                    handler.removeCallbacks(runnable)
                    handler.postDelayed(runnable, 300)
                }
            }
        })
    }

    private fun initGoogleMap() {
        activity?.let {
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(this)
        }
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
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            @Suppress("DEPRECATION")
                            QualitySelector
                                .getSupportedQualities(camera.cameraInfo)
                                .filter { quality ->
                                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                                        .contains(quality)
                                }.also {
                                    Timber.e("NVQ Camera Face++++ ${it}")
                                    cameraCapabilities.add(CameraCapability(camSelector, it))
                                }
                        }
                    } catch (exc: java.lang.Exception) {
                        Timber.e("NVQ Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }

    private fun initView() {
        lifecycleScope.launch {
            Timber.e("NVQ NVQ getsizevideo ++++++++++1111")
            startCamera()
        }
        binding.previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            if (state == PreviewView.StreamState.STREAMING) {
                Utils.safeDelay(1050) {
                    loadVideoSize()
                }
            }
        }

    }

    var onLoadingVideoSize: Boolean = false
    fun loadVideoSize() {
        if (!SharedPreferenceHelper.containKey(Constant.VIDEO_WIDTH)) {
            Timber.e("NVQ NVQ getsizevideo ++++++++++")
            onLoadingVideoSize = true
            val videoOutputFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "videoSize.mp4"
            )
            val output = FileOutputOptions.Builder(videoOutputFile).build()
            recording = videoCapture.output
                .prepareRecording(requireContext(), output)
                .start(cameraExecutor) { recordEvent ->
                    if (recordEvent is VideoRecordEvent.Finalize) {
                        val size = getVideoSize(output.file.absolutePath)
                        size?.let {
                            SharedPreferenceHelper.putInt(Constant.VIDEO_WIDTH, it.width)
                            SharedPreferenceHelper.putInt(Constant.VIDEO_HEIGHT, it.height)
                        }
                        Timber.e("NVQ NVQ getsizevideo $size")
                        onLoadingVideoSize = false
                        recording = null
                    } else if (recordEvent is VideoRecordEvent.Start) {
                        Utils.safeDelay(1050) {
                            recording?.stop()
                            recording = null
                        }
                    }
                }
        }

    }


    override fun onResume() {
        super.onResume()
    }


    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {
    }

    override fun onBackPressed() {
    }

    //+++++++++++++++++++++++++++++++++++++ Camera +++++++++++++++++++++++++++++++
    data class CameraCapability(val camSelector: CameraSelector, val qualities: List<Quality>)

    @OptIn(ExperimentalCamera2Interop::class)
    private suspend fun startCamera() {
        Timber.e("NVQ startCamera+++1")
        enumerationDeferred?.await()
        Timber.e("NVQ startCamera+++2")
        val aspectRatioStrategy =
            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        binding.previewView.apply {
            layoutParams = layoutParams.apply {
                if (this is ConstraintLayout.LayoutParams) {
                    dimensionRatio = "9:16"
                }

            }
        }
        val resolutionSelector =
            ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatioStrategy).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            Timber.e("NVQ startCamera+++3")
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = getCameraSelector()
            val recorder = Recorder.Builder()
                .setQualitySelector(getQuality())
                .setAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture!!)
                .addUseCase(videoCapture)

            media3Effect = Media3Effect(
                requireContext(),
                CameraEffect.VIDEO_CAPTURE,
                cameraExecutor
            ) {}
            useCaseGroup.addEffect(media3Effect!!)
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    useCaseGroup.build()
                )
                val camera2Control = Camera2CameraControl.from(camera!!.cameraControl)
                val camera2Info = Camera2CameraInfo.from(camera!!.cameraInfo)
                val supportedFpsRanges = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
                )
                if (supportedFpsRanges != null) {
                    for (range in supportedFpsRanges) {
                        Timber.e("NVQ supportedFpsRanges++++ $range")
                    }
                }
                camera2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, supportedFpsRanges!!.last())
                        .build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, cameraExecutor)
    }

    @OptIn(UnstableApi::class)
    fun updateOverlay() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            while (onLoadingVideoSize || !SharedPreferenceHelper.containKey(Constant.VIDEO_WIDTH)) {
                delay(50)
            }
            delay(300)
            val effect = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffect()
            withContext(Dispatchers.Main){
                overlay?.let { effect.add(it) }
                media3Effect?.setEffects(effect.build())
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
            .start(cameraExecutor) { recordEvent ->
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
                    binding.fabVideo.setImageResource(R.drawable.ic_videocam_black_24dp)
                } else if (recordEvent is VideoRecordEvent.Start){
                    binding.fabVideo.setImageResource(R.drawable.ic_stop_black_24dp)
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    @UnstableApi
    private fun createOverlayEffect(): OverlayEffect? {
        Timber.e("NVQ createOverlayEffect++++")
        val overlayBuilder = ImmutableList.Builder<TextureOverlay>()
        val settings = StaticOverlaySettings.Builder()
            .setAlphaScale(1f)
            .build()
        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(
            createHelloTextBitmap(
                SharedPreferenceHelper.getInt(Constant.VIDEO_WIDTH),
                SharedPreferenceHelper.getInt(Constant.VIDEO_HEIGHT)
            ), settings
        )
        overlayBuilder.add(bitmapOverlay)
        val overlay = overlayBuilder.build()
        return if (overlay.isEmpty()) null else OverlayEffect(overlay)
    }

    private fun getCameraSelector(): CameraSelector {
        return (cameraCapabilities[cameraIndex % cameraCapabilities.size].camSelector)
    }

    private fun getQuality(): QualitySelector {
        val quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
        Timber.e("NVQ getQuality++++ $quality")
        val qualitySelector = QualitySelector.from(
            quality,
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
        )
        return qualitySelector
    }

    fun createHelloTextBitmap(width: Int, height: Int): Bitmap {
        val mapBitmap = binding.layoutOverlay.drawToBitmap()
        val scaledBitmap = mapBitmap.scale(width, height)
        return scaledBitmap
    }


    //+++++++++++++++++++++++++++++++++++++ Map +++++++++++++++++++++++++++++++
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

        handler.removeCallbacks(loadMapImageRunnable)
        handler.postDelayed(loadMapImageRunnable, 5000)
    }

    fun moveToCurrentLocation(googleMap: GoogleMap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Timber.e("NVQ moveToCurrentLocation++++ $location")
                    currentLocation = location
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
                        handler.removeCallbacks(loadMapImageRunnable)
                        handler.postDelayed(loadMapImageRunnable, 5000)
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

    fun loadBitmapLocation(onCompletion: () -> Unit) {
        try {
            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                googleMap?.snapshot { mapBitmap ->
                    if (mapBitmap != null) {
                        binding.imMapSnapshot.visibility = View.VISIBLE
                        binding.imMapSnapshot.setImageBitmap(mapBitmap)
                        Timber.e("NVQ loadBitmapLocation11111")
                        onCompletion()
                    }
                }
            }
        } catch (e: Exception) {
        }
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
                        it.setMargins(m10dp, dpToPx(10), m10dp, m10dp)
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

    companion object {
        private val CAMERA_LENS =
            arrayOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK)
    }
}