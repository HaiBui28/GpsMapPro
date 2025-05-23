package com.tapbi.spark.gpsmappro.common.model

import com.google.android.gms.maps.model.LatLng

data class FolderPhotoModel (
    val uriPreview:String?,
    val listPhoto:ArrayList<PhotoModel>,
    val location: String,
    val latLng: LatLng?
)