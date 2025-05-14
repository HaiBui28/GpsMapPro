package com.tapbi.spark.gpsmappro.data

import androidx.camera.core.AspectRatio

enum class CameraRatio(val ratio: Int, val dimension: String) {
    RATIO_4_3(AspectRatio.RATIO_4_3, "3:4"),
    RATIO_16_9(AspectRatio.RATIO_16_9, "9:16")
}