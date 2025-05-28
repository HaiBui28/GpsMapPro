package com.tapbi.spark.gpsmappro.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import timber.log.Timber

class CustomLocationImage : View {
    val paint : Paint = Paint()
    val path : Path = Path()
    private var width = 100
    private var height = 100
    private var uriImage: String =""
    fun setImageUrl(uri: String){
        this.uriImage = uri
    }
    fun setWidthHeight(width: Int, height: Int){
        this.width = width
        this.height = height
    }
    constructor(context: Context?) : super(context){
        initData()
    }
    constructor(context: Context?, attributeView: AttributeSet?) : super(context, attributeView){
        initData()
    }
    constructor(context: Context?, attributeView: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attributeView,
        defStyleAttr
    ){
        initData()
    }
    private fun initData(){
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.strokeWidth = 10f
    }

    private fun drawBackgroundCircle(canvas: Canvas){
        canvas.drawCircle(width/2f, height/2f - 100, width/2f, paint)
    }

    private fun drawTriangle(canvas: Canvas) {
        val path = Path()
        val radius = 20f

        val centerX = width / 2f
        val topY = 0f
        val bottomY = height.toFloat()

        val leftX = centerX - width / 6f
        val rightX = centerX + width / 6f

        // Bắt đầu từ đáy bên trái
        path.moveTo(leftX + radius, topY)

        // Vẽ cạnh trái lên đỉnh
        path.lineTo(centerX, bottomY)

        // Vẽ cạnh phải về lại đáy phải
        path.lineTo(rightX - radius, topY)

        // Bo góc phải
        path.cubicTo(
            rightX, topY,             // Control point 1
            rightX, topY + radius,    // Control point 2
            rightX - radius, topY + radius // Kết thúc bo
        )

        // Vẽ đáy → trái
        path.lineTo(leftX + radius, topY + radius)

        // Bo góc trái
        path.cubicTo(
            leftX, topY + radius,     // Control point 1
            leftX, topY,              // Control point 2
            leftX + radius, topY      // Kết thúc bo
        )

        path.close()
        canvas.drawPath(path, paint)
    }



    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        drawBackgroundCircle(canvas)
        drawTriangle(canvas)
        Timber.e("asasasasasasasaasa")
    }

}