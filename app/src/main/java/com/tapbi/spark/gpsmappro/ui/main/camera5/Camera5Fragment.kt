package com.tapbi.spark.gpsmappro.ui.main.camera5

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera5Fragment :BaseBindingFragment<FragmentCamera5Binding, MainViewModel>() {
    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_camera_5

    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {
        initCamera()
        initListener()
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
}