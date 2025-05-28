package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentCamera4Binding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel

class Camera4Fragment : BaseBindingFragment<FragmentCamera4Binding, MainViewModel>() {
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }
    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera_4

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        binding.myGlSurfaceView.setOnSurfaceTextureReadyListener(object :MyRenderer.OnSurfaceTextureReadyListener{
            override fun onSurfaceTextureReady(surfaceTexture: SurfaceTexture) {
                activity?.runOnUiThread {
                    Log.d("chungvv", "startCamera: ")
                    startCamera(surfaceTexture)
                }
            }

        })
    }

    override fun onResume() {
        super.onResume()
        binding.myGlSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.myGlSurfaceView.onPause()
    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }
    fun getRotationDegrees(rotation: Int): Int {
        return when(rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun startCamera(surfaceTexture: SurfaceTexture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            surfaceTexture.setDefaultBufferSize(1280, 720)
            val surface = Surface(surfaceTexture)

            preview.setSurfaceProvider { request ->
                request.provideSurface(surface, cameraExecutor) { }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            } catch (e: Exception) {
                Log.e("CameraX+OpenGL", "Bind camera thất bại: $e")
            }

        }, cameraExecutor)
    }
}