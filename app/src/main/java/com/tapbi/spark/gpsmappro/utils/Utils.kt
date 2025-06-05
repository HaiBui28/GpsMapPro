package com.tapbi.spark.gpsmappro.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
import androidx.camera.core.ImageProxy
import androidx.camera.video.Quality
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object Utils {
    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).roundToInt()
    }

    fun getStatusBarHeight(context: Context): Int {
        try {
            val resourceId =
                context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                return context.resources.getDimensionPixelSize(resourceId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }
    fun getWidthScreen(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }

    fun getHeightScreen(): Int {
        return Resources.getSystem().displayMetrics.heightPixels
    }
    fun getViewBitmap(view: View, targetWidth: Int, targetHeight: Int): Bitmap {
        // Đo và layout với kích thước cụ thể
        val widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, targetWidth, targetHeight)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    fun getNavigationBarHeight(context: Context): Int {
        val isNavigationBar = context.resources.getBoolean(
            context.resources.getIdentifier(
                "config_showNavigationBar", "bool", "android"
            )
        )
        val resourceId =
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0 && isNavigationBar) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return 0
    }

//    fun mergeBitmaps(cameraBitmap: Bitmap, overlayBitmap: Bitmap, rotation: Float): Bitmap {
//        val result = Bitmap.createBitmap(
//            cameraBitmap.width, cameraBitmap.height, cameraBitmap.config
//        )
//        val canvas = Canvas(result)
//        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)
//        val margin = dpToPx(10).toFloat()
//
//        // Scale overlay cho vừa chiều rộng
//        val availableWidth = cameraBitmap.width - 2 * margin
//        val scale = availableWidth / overlayBitmap.width.toFloat()
//
//        val newOverlayWidth = (overlayBitmap.width * scale).toInt()
//        val newOverlayHeight = (overlayBitmap.height * scale).toInt()
//
//        val scaledOverlay = Bitmap.createScaledBitmap(
//            overlayBitmap, newOverlayWidth, newOverlayHeight, true
//        )
//
//        // Tạo matrix xoay quanh tâm ảnh overlay
//        val matrix = Matrix()
//        matrix.postScale(1f, 1f) // scale giữ nguyên
//        matrix.postRotate(rotation, newOverlayWidth / 2f, newOverlayHeight / 2f)
//
//        val rotatedOverlay = Bitmap.createBitmap(
//            scaledOverlay, 0, 0, newOverlayWidth, newOverlayHeight, matrix, true
//        )
//
//        // Vẽ rotatedOverlay vào vị trí thích hợp
//        val left: Float
//        val top: Float
//        when (rotation) {
//            Rotation_2 -> {
//                left = margin
//                top = cameraBitmap.height.toFloat() / 2 - rotatedOverlay.height.toFloat() / 2
//            }
//
//            Rotation_3 -> {
//                left = cameraBitmap.width.toFloat() - rotatedOverlay.width.toFloat() - margin
//                top = cameraBitmap.height.toFloat() / 2 - rotatedOverlay.height.toFloat() / 2
//            }
//
//            Rotation_4 -> {
//                left = margin
//                top = margin
//            }
//
//            else -> {
//                left = margin
//                top = cameraBitmap.height - rotatedOverlay.height - margin
//            }
//        }
//
//
//        canvas.drawBitmap(rotatedOverlay, left, top, null)
//
//        return result
//    }


    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val yuvByteArray = out.toByteArray()

        return BitmapFactory.decodeByteArray(yuvByteArray, 0, yuvByteArray.size)
    }

    fun overlayBitmapOnFrame(
        frameBitmap: Bitmap,
        overlayBitmap: Bitmap,
        x: Float,
        y: Float
    ): Bitmap {
        val resultBitmap = frameBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(overlayBitmap, x, y, null)
        return resultBitmap
    }
    fun getSizeForQuality(quality: Quality): Size {
        return when (quality) {
            Quality.UHD -> Size(3840, 2160)
            Quality.FHD -> Size(1920, 1080)
            Quality.HD  -> Size(1280, 720)
            Quality.SD  -> Size(720, 480)
            else -> Size(1280, 720)
        }
    }
    fun safeDelay(delayMillis: Long = 0, action: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                action()
            } catch (e: java.lang.Exception) {
                Timber.e("safeDelay: $e")
            }
        }, delayMillis)
    }
}