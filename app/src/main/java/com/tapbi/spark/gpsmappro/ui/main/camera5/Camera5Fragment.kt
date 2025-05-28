package com.tapbi.spark.gpsmappro.ui.main.camera5

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.FileCallback
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.VideoResult
import com.otaliastudios.cameraview.controls.Facing
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera5Binding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera5Fragment :BaseBindingFragment<FragmentCamera5Binding, MainViewModel>(),OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera_5

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initCamera()
        initListener()
        initGoogleMap()
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
        binding.fabFront.setOnClickListener { toggleCamera() }
    }

    private fun initCamera() {
        binding.camera.setLifecycleOwner(viewLifecycleOwner)
        binding.camera.setVideoMaxDuration(3600 * 1000)
        binding.camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.US)
                val currentTimeStamp = dateFormat.format(Date())

                val path =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .toString() + File.separator + "CameraViewFreeDrawing"
                val outputDir = File(path)
                outputDir.mkdirs()
                val saveTo = File(path + File.separator + currentTimeStamp + ".jpg")

                result.toFile(saveTo, FileCallback { file: File? ->
                        if (file != null) {
                            Toast.makeText(
                                activity,
                                "Picture saved to " + file.getPath(),
                                Toast.LENGTH_LONG
                            ).show()

                            // refresh gallery
                            MediaScannerConnection.scanFile(
                                activity,
                                arrayOf<String>(file.toString()), null,
                                MediaScannerConnection.OnScanCompletedListener { filePath: String?, uri: Uri? ->
                                    Log.i("ExternalStorage", "Scanned " + filePath + ":")
                                    Log.i("ExternalStorage", "-> uri=" + uri)
                                })
                        }
                    })

            }

            override fun onVideoTaken(result: VideoResult) {
                super.onVideoTaken(result)

                // refresh gallery
                MediaScannerConnection.scanFile(
                    activity,
                    arrayOf<String>(result.getFile().toString()), null,
                    MediaScannerConnection.OnScanCompletedListener { filePath: String?, uri: Uri? ->
                        Log.i("ExternalStorage", "Scanned " + filePath + ":")
                        Log.i("ExternalStorage", "-> uri=" + uri)
                    })
            }
        })
        binding.camera.snapshotMaxHeight = binding.camera.height

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
        if (binding.camera.isTakingVideo()) {
            binding.camera.stopVideo()
            binding.fabVideo.setImageResource(R.drawable.ic_videocam_black_24dp)
            return
        }
        val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.US)
        val currentTimeStamp = dateFormat.format(Date())

        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + File.separator + "CameraViewFreeDrawing"
        val outputDir = File(path)
        outputDir.mkdirs()
        val saveTo = File(path + File.separator + currentTimeStamp + ".mp4")
        binding.camera.takeVideoSnapshot(saveTo)

        binding.fabVideo.setImageResource(R.drawable.ic_stop_black_24dp)
    }

    fun capturePictureSnapshot() {
        if (binding.camera.isTakingVideo()) {
            Toast.makeText(activity, "Already taking video.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.camera.takePictureSnapshot()
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
}