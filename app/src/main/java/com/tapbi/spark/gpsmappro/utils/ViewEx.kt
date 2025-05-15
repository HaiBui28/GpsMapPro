package com.tapbi.spark.gpsmappro.utils

import android.view.View
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout

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