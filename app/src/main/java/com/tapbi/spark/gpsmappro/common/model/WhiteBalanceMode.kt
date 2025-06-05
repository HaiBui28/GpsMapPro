package com.tapbi.spark.gpsmappro.common.model

import android.hardware.camera2.CaptureRequest

enum class WhiteBalanceMode(val awbMode: Int, val displayName: String) {
    AUTO(CaptureRequest.CONTROL_AWB_MODE_AUTO, "Auto"),
    INCANDESCENT(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, "Incandescent"),
    FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, "Fluorescent"),
    DAYLIGHT(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "Daylight"),
    CLOUDY(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "CloudyDaylight"),
    SHADE(CaptureRequest.CONTROL_AWB_MODE_SHADE, "Shade"),
    TWILIGHT(CaptureRequest.CONTROL_AWB_MODE_TWILIGHT, "Twilight"),
    WARM_FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT, "WarmFluorescent")
}
