package com.tapbi.spark.gpsmappro.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getDoubleOrNull
import com.google.android.gms.maps.model.LatLng
import com.tapbi.spark.gpsmappro.common.model.FolderPhotoModel
import com.tapbi.spark.gpsmappro.common.model.PhotoModel
import java.io.IOException
import java.util.Locale


object MediaUtil {
    fun getDevicePhotosByFolder(context: Context): List<FolderPhotoModel> {
        val foldersMap = ArrayList<FolderPhotoModel>()
        val listPhoto = ArrayList<PhotoModel>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
        )
        cursor?.use {
            Log.d("Haibq", "getDevicePhotosByFolder: 2")
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                try {
                    val inputStream = context.contentResolver.openInputStream(contentUri)
//                    getPathFromUri(context, contentUri)
                    val exifInterface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ExifInterface(inputStream!!, ExifInterface.STREAM_TYPE_FULL_IMAGE_DATA)
                        } else {
                            TODO("VERSION.SDK_INT < R")
                        }
                    } else {
                        TODO("VERSION.SDK_INT < N")
                    }
                    val latLong = FloatArray(2)
                    if (exifInterface.getLatLong(latLong)) {
                        Log.d("Haibq", "getDevicePhotosByFolder: true rêrtertrtrtrtrrt")
                        val latStr = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                        val lonStr = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                        Log.d("Haibq", "getDevicePhotosByFolder19: "+ latStr+"////"+ lonStr+"/+++"+ latLong[0]+"////"+ latLong[1])
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(latLong[0].toDouble(), latLong[1].toDouble(), 1)
                        val location = addresses?.get(0)?.getAddressLine(0)?:" "
                        Log.d("Haibq", "getDevicePhotosByFolder: "+ location)
                          val photo =  PhotoModel(
                                uri = contentUri.toString(),
                                latLng = LatLng(latLong[0].toDouble(), latLong[1].toDouble()),
                                location = location
                            )
                      listPhoto.add(photo)
                    }
                } catch (e: Exception) {
                    Log.d("c", "getDevicePhotosByFolder: " + e)
                }
            }
            for (i in listPhoto) {
                val existingFolder = foldersMap.find { it.location.equals(i.location, ignoreCase = true) }

                if (existingFolder != null) {
                    existingFolder.listPhoto.add(i)
                } else {
                    foldersMap.add(FolderPhotoModel(i.uri, arrayListOf(i), i.location.toString(),i.latLng))
                }
            }
        }
        return foldersMap
    }

    fun getPathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            if (cursor.moveToFirst()) {
                return cursor.getString(columnIndex)
            }
        }
        return null
    }

    fun Cursor.getLongOrNull(columnIndex: Int): Long? =
        if (isNull(columnIndex)) null else getLong(columnIndex)

    fun Cursor.getIntOrNull(columnIndex: Int): Int? =
        if (isNull(columnIndex)) null else getInt(columnIndex)
}