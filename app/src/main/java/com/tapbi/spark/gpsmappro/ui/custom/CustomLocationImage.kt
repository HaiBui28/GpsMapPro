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
        canvas.drawCircle(width/2f, height/2f, width/2f, paint)
    }

    private fun drawTriangle(canvas: Canvas) {
        val path = Path()

        val topLeft = PointF(100f, 100f)
        val topRight = PointF(200f, 100f)
        val bottom = PointF(150f, 200f)

        val radius = 10f // độ bo tròn

        path.moveTo(topLeft.x, topLeft.y)
        path.lineTo(topRight.x, topRight.y)

        // Đi tới điểm trước góc dưới
        path.lineTo(bottom.x + radius, bottom.y - radius)

        // Bo góc bằng cubicTo (vào góc dưới rồi ra khỏi góc)
        path.cubicTo(
            150f + radius, 200f,     // Control point 1
            150f - radius, 200f,     // Control point 2
            150f - radius, 200f - radius  // Điểm kết thúc cong
        )

        path.lineTo(topLeft.x, topLeft.y)

        path.close()

        canvas.drawPath(path, paint)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
//        drawBackgroundCircle(canvas)
        drawTriangle(canvas)
        Timber.e("asasasasasasasaasa")
    }

}