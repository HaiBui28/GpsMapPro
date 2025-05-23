package com.tapbi.spark.gpsmappro.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
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

    fun getNavigationBarHeight(context: Context): Int {
        val isNavigationBar = context.resources.getBoolean(
            context.resources.getIdentifier(
                "config_showNavigationBar",
                "bool",
                "android"
            )
        )
        val resourceId =
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0 && isNavigationBar) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return 0
    }


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

    fun overlayBitmapOnFrame(frameBitmap: Bitmap, overlayBitmap: Bitmap, x: Float, y: Float): Bitmap {
        val resultBitmap = frameBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(overlayBitmap, x, y, null)
        return resultBitmap
    }
}