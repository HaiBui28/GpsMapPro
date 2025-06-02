package com.tapbi.spark.gpsmappro.ui.main.camera5

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.media.Image
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
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
import com.google.zxing.BinaryBitmap
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.FileCallback
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.VideoResult
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Grid
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.frame.FrameProcessor
import com.otaliastudios.cameraview.overlay.OverlayDrawer.markOverlayDirty
import com.tapbi.spark.gpsmappro.utils.Utils.safeDelay
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera5Binding
import com.tapbi.spark.gpsmappro.feature.BalanceBarView
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import com.tapbi.spark.gpsmappro.utils.PlanarYUVLuminanceSource
import com.tapbi.spark.gpsmappro.utils.Utils.dpToPx
import com.tapbi.spark.gpsmappro.utils.clearAllConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera5Fragment :BaseBindingFragment<FragmentCamera5Binding, MainViewModel>(),OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private var currentLocation : Location? = null
    var rotation = 0f
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable {
        loadMapRotation(rotation)
        binding.llMap.animate()
            .rotation(rotation)
            .setDuration(300)
            .start()
    }
    val loadMapImageRunnable = Runnable {
        loadBitmapLocation(){}
    }
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera_5

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initCamera()
        initListener()
        initGoogleMap()
        initChangeRotation()
    }
    private fun initGoogleMap() {
        activity?.let {
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync(this)
        }
    }
    private fun initListener() {
        binding.fabVideo.setOnClickListener { captureVideoSnapshot() }
        binding.fabPicture.setOnClickListener { capturePictureSnapshot() }
        binding.fabFront.setOnClickListener {toggleCamera()}
        binding.fabGrid.setOnClickListener {
            binding.camera.grid = Grid.DRAW_3X3
        }
    }
    private fun initChangeRotation() {
        binding.balanceBarView.setRotationListener(object : BalanceBarView.RotationListener {
            override fun onRotationChanged(rotation: Float) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                    Log.e("NVQ", "NVQ 23456789 rotation: $rotation")
                    this@Camera5Fragment.rotation = rotation
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
                        it.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }

                    Rotation_3 -> {
                        translationX =
                            ((binding.llMap.width / 2) - (binding.llMap.height / 2)).toFloat()
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }

                    Rotation_4 -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp, dpToPx(10) + App.statusBarHeight, m10dp, m10dp)
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    }

                    else -> {
                        translationX = 0f
                        translationY = 0f
                        it.setMargins(m10dp)
                        it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
                layoutParams = it
                safeDelay(600){
                    markOverlayDirty()
                }
            }
        }
    }
    private var mQrReader: QRCodeReader? = null
    private var lastAnalyzedTime = 0L
    private val analyzeInterval = 1000L
    private fun loadQrCode(){
        mQrReader = QRCodeReader()
        binding.camera.addFrameProcessor(object : FrameProcessor {
            @SuppressLint("SuspiciousIndentation")
            @WorkerThread
            override fun process(frame: Frame) {
               if (frame.getDataClass() === Image::class.java && !binding.camera.isTakingVideo) {
                   val currentTime = SystemClock.elapsedRealtime()
                   if (currentTime - lastAnalyzedTime < analyzeInterval) return
                   lastAnalyzedTime = currentTime
                   val img: Image? = frame.getData()
                       Log.e("NVQ","1235435185745321+++++")
                       var rawResult: com.google.zxing.Result? = null
                       try {
                           if (img == null) throw NullPointerException("cannot be null")
                           val buffer = img.planes[0].buffer
                           val data = ByteArray(buffer.remaining())
                           buffer.get(data)
                           val width = img.width
                           val height = img.height
                           val source = PlanarYUVLuminanceSource(data, width, height)
                           val bitmap = BinaryBitmap(HybridBinarizer(source))

                           rawResult = mQrReader?.decode(bitmap)
                               onQRCodeRead(rawResult?.getText())

                       } catch (ignored: Exception) { } finally {
                           mQrReader?.reset()
                           img?.close()
                       }
                }
            }
        })
    }
    fun onQRCodeRead(text: String?) {
        Log.e("NVQ","text+++++++++: $text")
    }
    private var isSaveOrigin = false
    private fun initCamera() {
        binding.camera.setLifecycleOwner(viewLifecycleOwner)
        binding.camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.US)
                val currentTimeStamp = dateFormat.format(Date())
                val path =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .toString() + File.separator + "GpsMapPro"
                if (isSaveOrigin){
                    val originDir = File(path)
                    originDir.mkdirs()
                    val origin = File(path + File.separator + currentTimeStamp + "_origin.jpg")

                    result.originToFile (origin){
                        MediaScannerConnection.scanFile(
                            activity,
                            arrayOf<String>(it.toString()), null,
                            MediaScannerConnection.OnScanCompletedListener { filePath: String?, uri: Uri? -> })
                    }
                }
                val outputDir = File(path)
                outputDir.mkdirs()
                val saveTo = File(path + File.separator + currentTimeStamp + ".jpg")

                result.toFile(saveTo, FileCallback { file: File? ->
                        if (file != null) {
                            if (currentLocation != null) {
                                try {
                                    val exif = ExifInterface(file.absolutePath)
                                    exif.setGpsInfo(currentLocation)
                                    exif.saveAttributes()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            Toast.makeText(
                                activity,
                                "Picture saved to " + file.getPath(),
                                Toast.LENGTH_LONG
                            ).show()

                            // refresh gallery
                            MediaScannerConnection.scanFile(
                                activity,
                                arrayOf<String>(file.toString()), null,
                                MediaScannerConnection.OnScanCompletedListener { filePath: String?, uri: Uri? -> })
                        }
                    })

            }

            override fun onVideoTaken(result: VideoResult) {
                super.onVideoTaken(result)
                Toast.makeText(
                    activity,
                    "Picture saved to " + result.getFile().getPath(),
                    Toast.LENGTH_LONG
                ).show()
                // refresh gallery
                MediaScannerConnection.scanFile(
                    activity,
                    arrayOf<String>(result.getFile().toString()), null,
                    MediaScannerConnection.OnScanCompletedListener { filePath: String?, uri: Uri? -> })
            }
        })
        binding.camera.snapshotMaxHeight = binding.camera.height
        loadQrCode()

        //tỉ lệ khung hình
//        binding.camera.apply {
//            layoutParams = layoutParams.apply {
//                if (this is ConstraintLayout.LayoutParams) {
//                    dimensionRatio = "3:4"
//                }
//
//            }
//        }
    }

    fun captureVideoSnapshot() {
        if (binding.camera.isTakingVideo) {
            binding.camera.stopVideo()
            binding.fabVideo.setImageResource(R.drawable.ic_videocam_black_24dp)
            return
        }
       loadBitmapLocation(){
           val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.US)
           val currentTimeStamp = dateFormat.format(Date())

           val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
               .toString() + File.separator + "GpsMapPro"
           val outputDir = File(path)
           outputDir.mkdirs()
           val saveTo = File(path + File.separator + currentTimeStamp + ".mp4")
           binding.camera.takeVideoSnapshot(saveTo)

           binding.fabVideo.setImageResource(R.drawable.ic_stop_black_24dp)
       }

    }

    fun capturePictureSnapshot() {
        if (binding.camera.isTakingVideo) {
            Toast.makeText(activity, "Already taking video.", Toast.LENGTH_SHORT).show()
            return
        }
        loadBitmapLocation(){
            binding.camera.takePictureSnapshot(isSaveOrigin)
        }

    }
    fun captureOriginPicture() {

    }

    fun toggleCamera() {
        if (binding.camera.isTakingPicture() || binding.camera.isTakingVideo()) return
        when (binding.camera.toggleFacing()) {
            Facing.BACK -> binding.fabFront.setImageResource(R.drawable.ic_camera_front_black_24dp)
            Facing.FRONT -> binding.fabFront.setImageResource(R.drawable.ic_camera_rear_black_24dp)
        }
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }

    override fun onPause() {
        if (binding.camera.isTakingVideo) {
            binding.camera.stopVideo()
            binding.fabVideo.setImageResource(R.drawable.ic_videocam_black_24dp)
        }
        super.onPause()
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
//            googleMap?.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    binding.camera.location = location
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Vị trí mặc định")
                    )
                    try {
                        val geocoder = Geocoder(requireActivity(), Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
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

    fun loadBitmapLocation(onCompletion: () -> Unit) {
            googleMap?.snapshot { mapBitmap ->
                if (mapBitmap != null) {
                    binding.imMapSnapshot.visibility = View.VISIBLE
                    binding.imMapSnapshot.setImageBitmap(mapBitmap)
                    markOverlayDirty()
                    Log.e("NVQ","loadBitmapLocation11111")
                    onCompletion()
                }
            }
    }
}