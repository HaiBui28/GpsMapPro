package com.tapbi.spark.gpsmappro.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Build
import android.provider.MediaStore
//import android.service.credentials.PermissionUtils.hasPermission
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
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
fun Bitmap.saveToGalleryWithLocation(context: Context, location: Location?, rotation: Float) {
    val filename = "mirrored_${System.currentTimeMillis()}.jpg"

    val matrix = Matrix().apply { postRotate(-rotation) }
    // 1. Xoay ảnh nếu cần
    val finalBitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    // 2. Tạo file tạm trong bộ nhớ cache
    val tempFile = File.createTempFile("temp_image", ".jpg", context.cacheDir)
    FileOutputStream(tempFile).use { out ->
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }

    // 3. Ghi vị trí vào EXIF nếu có
    location?.let {
        val exif = ExifInterface(tempFile.absolutePath)
        exif.setGpsInfo(it)
        exif.saveAttributes()
    }

    // 4. Ghi vào MediaStore
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
            FileInputStream(tempFile).copyTo(out)
        }
    }

    // 5. Xoá file tạm
    tempFile.delete()
}
fun Bitmap.correctOrientation(filePath: String): Bitmap {
    val exif = android.media.ExifInterface(filePath)
    val orientation = exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL
    )

    val matrix = Matrix()

    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }

    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }

    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
fun viewToBitmap(view: View): Bitmap {
    val bitmap = Bitmap.createBitmap(
        view.width,
        view.height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}


