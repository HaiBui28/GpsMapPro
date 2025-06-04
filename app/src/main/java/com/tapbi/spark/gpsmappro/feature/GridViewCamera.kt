package com.tapbi.spark.gpsmappro.feature

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridViewCamera : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val gridPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var typeGrid = 3

    private val goldenRatio = 1.618f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (typeGrid) {
            1 -> drawPhiGrid(canvas)
            2 -> drawGrid(canvas, 3)
            3 -> drawGrid(canvas, 4)
        }
    }

    fun drawGrid(canvas: Canvas, rows: Int) {
        val widthCell = width.toFloat() / rows
        val heightCell = height.toFloat() / rows

        for (i in 1 until rows) {
            val x = i * widthCell
            val y = i * heightCell

            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
    }

    fun drawPhiGrid(canvas: Canvas) {
        val totalWidth = width.toFloat()
        val totalHeight = height.toFloat()

        val phiValue = 1f / goldenRatio
        val totalRatio = 1f + phiValue + 1f

        val firstVerticalLine = totalWidth * (1f / totalRatio)
        val secondVerticalLine = totalWidth * ((1f + phiValue) / totalRatio)

        val firstHorizontalLine = totalHeight * (1f / totalRatio)
        val secondHorizontalLine = totalHeight * ((1f + phiValue) / totalRatio)

        canvas.drawLine(firstVerticalLine, 0f, firstVerticalLine, totalHeight, gridPaint)
        canvas.drawLine(secondVerticalLine, 0f, secondVerticalLine, totalHeight, gridPaint)

        canvas.drawLine(0f, firstHorizontalLine, totalWidth, firstHorizontalLine, gridPaint)
        canvas.drawLine(0f, secondHorizontalLine, totalWidth, secondHorizontalLine, gridPaint)
    }

    fun setGridView(typeGrid: Int) {
        this.typeGrid = typeGrid
    }
}