package com.tapbi.spark.editorphoto.feature

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView

@SuppressLint("ClickableViewAccessibility", "AppCompatCustomView")
class CustomImage : ImageView {
    private var currentColor = Color.BLACK
    private val paths = mutableListOf<Pair<Path, Paint>>()
    private val path = Path()
    private var lastX = 0f
    private var lastY = 0f

    private var checkDraw: Boolean = false

    constructor(context: Context?) : super(context, null) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs, 0) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private val paintConfig = Paint().apply {
        isAntiAlias = true
        strokeWidth = 15f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND

    }

    private fun init() {
        setOnTouchListener { _, event ->
            if (checkDraw) {
                val x = event.x
                val y = event.y

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startDrawing(x, y)
                    MotionEvent.ACTION_MOVE -> continueDrawing(x, y)
                    MotionEvent.ACTION_UP -> stopDrawing()
                }
                invalidate()
                true
            } else {
                false
            }
        }
    }

    private fun startDrawing(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
        lastX = x
        lastY = y

    }

    private fun continueDrawing(x: Float, y: Float) {
        path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
        lastX = x
        lastY = y

    }

    private fun stopDrawing() {
        path.lineTo(lastX, lastY)
        val paint = Paint(paintConfig)
        paint.color = currentColor
        paths.add(Pair((Path(path)), paint))
        path.reset()
    }

}