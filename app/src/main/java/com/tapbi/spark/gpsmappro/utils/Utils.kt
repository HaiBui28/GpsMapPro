package com.tapbi.spark.gpsmappro.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_2
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_3
import com.tapbi.spark.gpsmappro.feature.BalanceBarView.Companion.Rotation_4
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
    fun mergeBitmaps(cameraBitmap: Bitmap, overlayBitmap: Bitmap, rotation: Float): Bitmap {
        val result = Bitmap.createBitmap(
            cameraBitmap.width,
            cameraBitmap.height,
            cameraBitmap.config
        )
        val canvas = Canvas(result)
        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)
        val margin = dpToPx(10).toFloat()

        // Scale overlay cho vừa chiều rộng
        val availableWidth = cameraBitmap.width - 2 * margin
        val scale = availableWidth / overlayBitmap.width.toFloat()

        val newOverlayWidth = (overlayBitmap.width * scale).toInt()
        val newOverlayHeight = (overlayBitmap.height * scale).toInt()

        val scaledOverlay = Bitmap.createScaledBitmap(
            overlayBitmap,
            newOverlayWidth,
            newOverlayHeight,
            true
        )

        // Tạo matrix xoay quanh tâm ảnh overlay
        val matrix = Matrix()
        matrix.postScale(1f, 1f) // scale giữ nguyên
        matrix.postRotate(rotation, newOverlayWidth / 2f, newOverlayHeight / 2f)

        val rotatedOverlay = Bitmap.createBitmap(
            scaledOverlay,
            0,
            0,
            newOverlayWidth,
            newOverlayHeight,
            matrix,
            true
        )

        // Vẽ rotatedOverlay vào vị trí thích hợp
        val left : Float
        val top : Float
        when(rotation){
            Rotation_2 -> {
                left = margin
                top = cameraBitmap.height.toFloat()/2 - rotatedOverlay.height.toFloat()/2
            }
            Rotation_3 -> {
                left = cameraBitmap.width.toFloat() - rotatedOverlay.width.toFloat() - margin
                top = cameraBitmap.height.toFloat()/2 - rotatedOverlay.height.toFloat()/2
            }
            Rotation_4 -> {
                left = margin
                top = margin
            }
            else ->{
                left = margin
                top = cameraBitmap.height - rotatedOverlay.height - margin
            }
        }


        canvas.drawBitmap(rotatedOverlay, left, top, null)

        return result
    }
}