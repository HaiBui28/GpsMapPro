package com.tapbi.spark.gpsmappro.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

inline fun View.afterMeasured(crossinline block: () -> Unit) {
    if (measuredWidth > 0 && measuredHeight > 0) {
        block()
    } else {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            }
        })
    }
}
fun Context.hasPermission(per :String) = ContextCompat.checkSelfPermission(this, per) == PackageManager.PERMISSION_GRANTED
fun Context.checkLocationPermission(): Boolean {
    return hasPermission(if (isQPlus()) Manifest.permission.ACCESS_MEDIA_LOCATION else "") || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
}
fun isQPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

fun ConstraintLayout.LayoutParams.clearAllConstraints() {
    topToTop = ConstraintLayout.LayoutParams.UNSET
    topToBottom = ConstraintLayout.LayoutParams.UNSET
    bottomToTop = ConstraintLayout.LayoutParams.UNSET
    bottomToBottom = ConstraintLayout.LayoutParams.UNSET
    startToStart = ConstraintLayout.LayoutParams.UNSET
    startToEnd = ConstraintLayout.LayoutParams.UNSET
    endToStart = ConstraintLayout.LayoutParams.UNSET
    endToEnd = ConstraintLayout.LayoutParams.UNSET
}
fun Bitmap.mirrorHorizontally(): Bitmap {
    val matrix = Matrix().apply {
        preScale(-1f, 1f) // Lật ngang
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.saveToGallery(context: Context) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "mirrored_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraXDemo")
    }

    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { out ->
            compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
    }
}
fun Bitmap.saveToGalleryWithLocation(context: Context, location: Location?) {
    val filename = "mirrored_${System.currentTimeMillis()}.jpg"

    // 1. Tạo file tạm trong bộ nhớ cache
    val tempFile = File.createTempFile("temp_image", ".jpg", context.cacheDir)
    FileOutputStream(tempFile).use { out ->
        compress(Bitmap.CompressFormat.JPEG, 100, out)
    }

    // 2. Ghi vị trí vào EXIF nếu có
    location?.let {
        val exif = ExifInterface(tempFile.absolutePath)
        exif.setGpsInfo(it)
        exif.saveAttributes()
    }

    // 3. Ghi vào MediaStore
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraXDemo")
    }

    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )

    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { out ->
            FileInputStream(tempFile).copyTo(out) // copy từ file tạm đã có EXIF
        }
    }

    // 4. Xoá file tạm nếu muốn
    tempFile.delete()
}
