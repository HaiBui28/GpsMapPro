package com.tapbi.spark.gpsmappro.ui.main.googlemap

import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentGoogleMapBinding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.custom.CustomLocationImage
import com.tapbi.spark.gpsmappro.utils.Utils
import java.io.IOException

class GoogleMapFragment : BaseBindingFragment<FragmentGoogleMapBinding, GoogleMapViewModel>(),
    OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private var latLng = LatLng(0.0, 0.0)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    override fun getViewModel(): Class<GoogleMapViewModel> {
        return GoogleMapViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_google_map

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initGoogleMap()
        startListeningLocationUpdates()
    }

    private fun initGoogleMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.FrMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
        binding.imTypeMap.setOnClickListener {
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(R.layout.type_map)
            dialog.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            dialog.findViewById<Button>(R.id.btnNormal)?.setOnClickListener {
                googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
            dialog.findViewById<Button>(R.id.btnTerrain)?.setOnClickListener {
                googleMap?.mapType = GoogleMap.MAP_TYPE_TERRAIN
            }
            dialog.findViewById<Button>(R.id.btnHybrid)?.setOnClickListener {
                googleMap?.mapType = GoogleMap.MAP_TYPE_HYBRID
            }
            dialog.findViewById<Button>(R.id.btnSateLLite)?.setOnClickListener {
                googleMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            }
            dialog.show()
        }
        binding.imgLocation.setOnClickListener {
            googleMap?.let { it1 -> moveToCurrentLocation(it1) }
        }
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }

    private fun moveToCurrentLocation(googleMap: GoogleMap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d("Haibq", "moveToCurrentLocation: " + location)
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    this.latLng = latLng
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
//                        googleMap.addMarker(
//                            MarkerOptions()
//                                .position(latLng)
//                                .title("Vị trí mặc định")
//                        )
                    try {
//                            val geocoder = Geocoder(requireActivity(), Locale.getDefault())
//                            val addresses =
//                                geocoder.getFromLocation(location.latitude, location.longitude, 1)
//                            getAddressFromLocation(location.latitude,location.longitude)
//                            binding.tvLocation.text = addresses!![0].getAddressLine(0)
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
        }
    }

    private fun startListeningLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val locationRequest = LocationRequest.create().apply {
            interval = 5000 // thời gian lặp: 5s
            fastestInterval = 2000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)
                Log.d("Haibq", "onLocationResult: ưee")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = true
        addMarker(map)
        moveToCurrentLocation(map)
        var circle: Circle? = null
        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            if (circle == null) {
                val circleOptions = CircleOptions()
                circleOptions.fillColor(Color.parseColor("#c8cfc5"))
                circleOptions.center(center)
                circleOptions.radius(100.0)
                circleOptions.strokeWidth(1f)
                circle = map.addCircle(circleOptions)
            } else {
                circle?.center = center
            }
            val a = isPointInCircle(center, 100.0, latLng)
            Log.d("Haibq", "onMapReady: " + a)
        }
        map.setOnMarkerClickListener { marker ->

            // Xử lý sự kiện click ở đây
            Log.d("MarkerClick", "Clicked on: ${marker.title}")

            // Trả về true nếu bạn tự xử lý sự kiện và không muốn hiển thị info window
            // Trả về false nếu muốn hiển thị info window mặc định
            true
        }
    }

    fun isPointInCircle(center: LatLng, radiusInMeters: Double, point: LatLng): Boolean {
        val result = FloatArray(1)
        Location.distanceBetween(
            center.latitude, center.longitude,
            point.latitude, point.longitude,
            result
        )
        return result[0] <= radiusInMeters
    }

    private fun addMarker(googleMap: GoogleMap) {
        val context = requireContext()
        val screenSize = Utils.getWidthScreen() / 3
        val sampleView = CustomLocationImage(context)
        sampleView.setWidthHeight(screenSize, screenSize)
        val defaultBitmap = Utils.getViewBitmap(sampleView, screenSize, screenSize)
        val descriptor = defaultBitmap?.let { BitmapDescriptorFactory.fromBitmap(it) }
        Log.d("Haibq", "addMarker: "+ Thread.currentThread().name)
        App.instance?.foldersMap?.forEach { folder ->
            folder.latLng?.let { latLng ->
                googleMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .icon(descriptor)
                        .title("Vị trí mặc định")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Haibq", "onDestroy: sdas")
    }
}