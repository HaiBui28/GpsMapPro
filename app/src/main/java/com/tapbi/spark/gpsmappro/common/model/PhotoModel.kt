package com.tapbi.spark.gpsmappro.common.model

import com.google.android.gms.maps.model.LatLng

data class PhotoModel(
    val uri: String,
    val latLng: LatLng?,
    val location: String?
)