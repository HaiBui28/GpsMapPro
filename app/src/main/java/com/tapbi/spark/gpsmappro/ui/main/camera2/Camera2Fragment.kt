package com.tapbi.spark.gpsmappro.ui.main.camera2

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.GLException
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.daasuu.gpuv.camerarecorder.CameraRecordListener
import com.daasuu.gpuv.camerarecorder.GPUCameraRecorder
import com.daasuu.gpuv.camerarecorder.GPUCameraRecorderBuilder
import com.daasuu.gpuv.camerarecorder.LensFacing
import com.daasuu.gpuv.data.AspectRatioType
import com.daasuu.gpuv.egl.filter.GlFilter
import com.daasuu.gpuv.egl.filter.GlWatermarkFilter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera2Binding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.CameraGLView
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10


class Camera2Fragment : BaseBindingFragment<FragmentCamera2Binding, MainViewModel>() {
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    private var googleMap: GoogleMap? = null
    private var gpuCameraRecorder: GPUCameraRecorder? = null

    private var currentAspect: AspectRatioType = AspectRatioType.RATIO_16_9

    private var glFilter: GlFilter? = null

    private var filepath: String? = null

    private var isRecord = false

    private var bitmapMap: Bitmap? = null

    private var lensFacing = LensFacing.BACK

    private var cameraGLView: CameraGLView? = null

    override val layoutId: Int
        get() = R.layout.fragment_camera2


    fun getVideoFilePath(): String {
        return getAndroidMoviesFolder().absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss").format(
            Date()
        ) + "GPUCameraRecorder.mp4"
    }

    fun getAndroidMoviesFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }


    fun scanFileMedia(context: Context, path: String) {
//        Timber.e("hoangld path: ${path}")
        MediaScannerConnection.scanFile(
            context, arrayOf(path), null
        ) { mPath, uri ->
            if (uri == null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                val values = ContentValues()
                values.put(MediaStore.Video.Media.DATA, mPath)
                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                )
            }
        }
    }

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {

        binding.btnChangeAspectRatio.setOnClickListener {
            toggleAspectRatio()

        }
        binding.btnVideo.setOnClickListener {
            if (!isRecord) {
                isRecord = true
                filepath = getVideoFilePath()
                gpuCameraRecorder?.start(filepath)
            } else {
                isRecord = false
                gpuCameraRecorder?.stop()
            }
            Toast.makeText(requireContext(), "isRecord $isRecord", Toast.LENGTH_SHORT).show()

        }
        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == LensFacing.FRONT) {
                LensFacing.BACK
            } else {
                LensFacing.FRONT
            }
            restartCameraRecorder()
        }
        binding.btnCapture.setOnClickListener {
            captureBitmap(object : BitmapReadyCallbacks {
                override fun onBitmapReady(bitmap: Bitmap) {
                    Log.e("chungvv", "onBitmapReady: $bitmap")
                    val imagePath: String = getImageFilePath()
                    saveAsPngImage(bitmap, imagePath)
                    exportPngToGallery(context!!, imagePath)
                }

            })
        }
    }

    private fun exportPngToGallery(context: Context, filePath: String) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val f = File(filePath)
        val contentUri = Uri.fromFile(f)
        mediaScanIntent.setData(contentUri)
        context.sendBroadcast(mediaScanIntent)
    }


    fun saveAsPngImage(bitmap: Bitmap, filePath: String?) {
        try {
            val file = File(filePath)
            val outStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    fun getImageFilePath(): String {
        return getAndroidImageFolder().absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss").format(
            Date()
        ) + "GPUCameraRecorder.png"
    }

    fun getAndroidImageFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    }


    private fun captureBitmap(bitmapReadyCallbacks: BitmapReadyCallbacks) {
        Log.e("chungvv", "captureBitmap: ")
        cameraGLView?.let {
            it.queueEvent {
                val egl = EGLContext.getEGL() as EGL10
                val gl = egl.eglGetCurrentContext().gl as GL10
                Log.e("chungvv", "GLSurfaceView size: ${it.measuredWidth} x ${it.measuredHeight}")
                val snapshotBitmap: Bitmap? = createBitmapFromGLSurface(
                    it.measuredWidth, it.measuredHeight, gl
                )

                if (snapshotBitmap != null) {
                    activity?.runOnUiThread {
                        bitmapReadyCallbacks.onBitmapReady(snapshotBitmap)
                    }
                }

            }
        }
    }


    private fun createBitmapFromGLSurface(w: Int, h: Int, gl: GL10): Bitmap? {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        try {
            gl.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            var texturePixel: Int
            var blue: Int
            var red: Int
            var pixel: Int
            for (i in 0 until h) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0 until w) {
                    texturePixel = bitmapBuffer[offset1 + j]
                    blue = (texturePixel shr 16) and 0xff
                    red = (texturePixel shl 16) and 0x00ff0000
                    pixel = (texturePixel and -0xff0100) or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            return null
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun initGoogleMap() {
        activity?.let {
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment?.getMapAsync { map ->
                googleMap = map
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
                map.snapshot {
                    binding.imMapSnapshot.setImageBitmap(it)
                    mapFragment.requireView().visibility = View.GONE
                    binding.imMapSnapshot.visibility = View.VISIBLE
                    binding.llMap.postDelayed({
                        val bitmapOverlay = binding.llMap.drawToBitmap()
                        glFilter = GlWatermarkFilter(
                            bitmapOverlay
                        )
                        gpuCameraRecorder?.setFilter(glFilter)
                        binding.llMap.visibility = View.GONE
                    }, 100) // delay nhỏ để đảm bảo ảnh đã render
                }
            }
        }
    }


    private fun setupCamera() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestPermissionLauncherCamera.launch(Manifest.permission.CAMERA)
        }

    }

    private val requestPermissionLauncherCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {

            } else {
                openCamera()
            }
        }


    private fun getResolutionFromAspect(aspect: AspectRatioType): Pair<Int, Int> {
        return when (aspect) {
            AspectRatioType.RATIO_4_3 -> Pair(640, 480)  // 4:3
            AspectRatioType.RATIO_16_9 -> Pair(1280, 720) // 16:9
        }
    }


    private fun openCamera() {
        cameraGLView = null
        cameraGLView = CameraGLView(context)
        binding.previewView.removeAllViews()
        binding.previewView.addView(cameraGLView)
        gpuCameraRecorder = GPUCameraRecorderBuilder(activity, cameraGLView)

            //.recordNoFilter(true)
            .cameraRecordListener(object : CameraRecordListener {
                override fun onGetFlashSupport(flashSupport: Boolean) {

                }

                override fun onRecordComplete() {
                    context?.let { context ->
                        filepath?.let { scanFileMedia(context, it) }
                    }

                }

                override fun onRecordStart() {

                }

                override fun onError(exception: Exception?) {

                }

                override fun onCameraThreadFinish() {

                }

                override fun onVideoFileReady() {

                }
            })
//                    .videoSize(videoWidth, videoHeight)
//                    .cameraSize(cameraWidth, cameraHeight)
            .lensFacing(lensFacing).build()
        initGoogleMap()

    }

    override fun onStop() {
        super.onStop()
        releaseCamera()
    }

    override fun onResume() {
        super.onResume()
        setupCamera()
    }


    private fun releaseCamera() {
        cameraGLView?.let {
            it.onPause()
            binding.previewView.removeView(it)
            cameraGLView = null
        }
        gpuCameraRecorder?.stop()
        gpuCameraRecorder?.release()
        gpuCameraRecorder = null
    }


    private fun toggleAspectRatio() {
        val radio: String
        if (currentAspect == AspectRatioType.RATIO_4_3) {
            currentAspect = AspectRatioType.RATIO_16_9
            radio = "9:16"
        } else {
            currentAspect = AspectRatioType.RATIO_4_3
            radio = "3:4"
        }

        val params = binding.previewView.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = radio
        binding.previewView.layoutParams = params

        restartCameraRecorder()
    }

    private fun restartCameraRecorder() {
        if (gpuCameraRecorder != null) {
            gpuCameraRecorder!!.stop()
            gpuCameraRecorder!!.release()
            gpuCameraRecorder = null
        }

        openCamera()
    }


    fun moveToCurrentLocation(googleMap: GoogleMap) {
        Log.d("Haibq", "moveToCurrentLocation: 11")
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        Log.d("Haibq", "moveToCurrentLocation: 1111")
        if (ActivityCompat.checkSelfPermission(
                requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
//            googleMap?.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d("Haibq", "moveToCurrentLocation: " + (location == null))
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    Log.d(
                        "Haibq",
                        "moveToCurrentLocation: " + location.latitude + " " + location.longitude
                    )
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    googleMap.addMarker(
                        MarkerOptions().position(latLng).title("Vị trí mặc định")
                    )
                    try {
                        val geocoder = Geocoder(requireActivity(), Locale.getDefault())
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        Log.d("Haibq", "moveToCurrentLocation: " + addresses!![0].getAddressLine(0))
                        getAddressFromLocation(location.latitude, location.longitude)
                        binding.tvLocation.text = addresses[0].getAddressLine(0)
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

//    fun processVideoWithOverlay(
//        context: Context, inputPath: String, outputPath: String, overlayBitmap: Bitmap
//    ) {
//        // 1. Chuẩn bị MediaExtractor để đọc video gốc
//        val extractor = MediaExtractor()
//        extractor.setDataSource(inputPath)
//        val videoTrackIndex = (0 until extractor.trackCount).first {
//                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
//                    ?.startsWith("video/") == true
//            }
//        extractor.selectTrack(videoTrackIndex)
//        val inputFormat = extractor.getTrackFormat(videoTrackIndex)
//        // 2. Chuẩn bị MediaCodec decoder
//        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
//        val decoder = MediaCodec.createDecoderByType(mime)
//        val surfaceTextureWrapper = CodecOutputSurface(inputFormat, overlayBitmap)
//        decoder.configure(inputFormat, surfaceTextureWrapper.surface, null, 0)
//        decoder.start()
//        // 3. Chuẩn bị encoder + MediaMuxer
//        val outputFormat = MediaFormat.createVideoFormat(
//            "video/avc\", inputFormat . getInteger (MediaFormat.KEY_WIDTH),
//            inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
//        ).apply {
//            setInteger(
//                MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
//            )
//            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
//            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
//            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
//        }
//        val encoder = MediaCodec.createEncoderByType("video/avc")
//        val inputSurface = EncoderSurface(encoder, outputFormat)
//        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//        // 4. Loop giải mã → render → mã hóa
//        var isEOS = false
//        var trackIndex = -1
//        val bufferInfo = MediaCodec.BufferInfo()
//        while (!isEOS) {
//            // 4.1 Đọc frame từ extractor
//            val inputBufferId = decoder.dequeueInputBuffer(10_000)
//            if (inputBufferId >= 0) {
//                val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
//                val sampleSize = extractor.readSampleData(inputBuffer, 0)
//                if (sampleSize > 0) {
//                    val presentationTime = extractor.sampleTime
//                    decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTime, 0)
//                    extractor.advance()
//                } else {
//                    decoder.queueInputBuffer(
//                        inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
//                    )
//                    isEOS = true
//                }
//            }
//            // 4.2 Lấy frame giải mã xong, vẽ lên OpenGL (kèm overlay)
//            val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
//            if (outputBufferId >= 0) {
//                surfaceTextureWrapper.awaitNewImage()
//                surfaceTextureWrapper.drawImageWithOverlay()
//                inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
//                inputSurface.swapBuffers()
//                decoder.releaseOutputBuffer(outputBufferId, true)
//            }
//            // 4.3 Ghi dữ liệu từ encoder ra file
//            while (true) {
//                val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 0)
//                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                    break
//                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                    trackIndex = muxer.addTrack(encoder.outputFormat)
//                    muxer.start()
//                } else if (encoderStatus >= 0) {
//                    val encodedData = encoder.getOutputBuffer(encoderStatus) ?: continue
//                    if (bufferInfo.size > 0) {
//                        encodedData.position(bufferInfo.offset)
//                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
//                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
//                    }
//                    encoder.releaseOutputBuffer(encoderStatus, false)
//                }
//            }
//        }
//        // 5. Kết thúc
//        decoder.stop()
//        decoder.release()
//        encoder.stop()
//        encoder.release()
//        muxer.stop()
//        muxer.release()
//        surfaceTextureWrapper.release()
//        inputSurface.release()
//    }


    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }

    private interface BitmapReadyCallbacks {
        fun onBitmapReady(bitmap: Bitmap)
    }
}


