package com.tapbi.spark.gpsmappro.utils

import android.content.Context
import android.content.res.Resources
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
}