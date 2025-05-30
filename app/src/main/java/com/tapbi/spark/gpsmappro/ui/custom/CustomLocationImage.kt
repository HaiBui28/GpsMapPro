package com.tapbi.spark.gpsmappro.ui.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.scale
import com.tapbi.spark.gpsmappro.R
import timber.log.Timber
import kotlin.math.roundToInt

class CustomLocationImage : View {
    val paint: Paint = Paint()
    val path: Path = Path()

        private var width = 100
    private var height = 100
    private var uriImage: String = ""
    fun setImageUrl(uri: String) {
        this.uriImage = uri
    }

        fun setWidthHeight(width: Int, height: Int){
        this.width = width
        this.height = height
    }
    constructor(context: Context?) : super(context) {
        initData()
    }

    constructor(context: Context?, attributeView: AttributeSet?) : super(context, attributeView) {
        initData()
    }

    constructor(context: Context?, attributeView: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attributeView,
        defStyleAttr
    ) {
        initData()
    }

    private fun initData() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.strokeWidth = 10f
    }

    private fun drawBackgroundCircle(canvas: Canvas) {
        canvas.save()
        paint.color = Color.WHITE
        canvas.drawCircle(width / 2f, height / 2f, width / 4f, paint)
        drawBitmap(canvas, (width / 4f).roundToInt(), (width / 4f).roundToInt())
        paint.color = Color.BLACK
        canvas.drawCircle(width * 2.95f / 4f, height / 3f, width / 12f, paint)
        canvas.restore()
    }

    @SuppressLint("UseKtx")
    fun drawBitmap(canvas: Canvas, widthCanvas: Int, heightCanvas: Int) {
        val rawBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.s1)

        val centerX = width / 2f
        val centerY = width / 2f
        val outerRadius = width / 4f

        val imageSize = (outerRadius * 2 * 0.8f).toInt()
        val scaledBitmap = rawBitmap.scale(imageSize, imageSize)

        val output = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path()
        path.addCircle(imageSize / 2f, imageSize / 2f, imageSize / 2f, Path.Direction.CCW)
        tempCanvas.clipPath(path)
        tempCanvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        val left = centerX - imageSize / 2f
        val top = centerY - imageSize / 2f
        canvas.drawBitmap(output, left, top, null)
    }

    private fun drawTriangle(canvas: Canvas) {
        canvas.save()
        val path = Path()
        path.moveTo(width / 3.5f, height / 2f)
        path.lineTo((width - width / 3.5f), height / 2f)
        path.cubicTo(
            width / 2f + 20f,
            height.toFloat() * 3 / 4f - 30f,
            width / 2f,
            height.toFloat() * 3 / 4f,
            width / 2f - 20f,
            height.toFloat() * 3 / 4f - 30f
        )
        canvas.translate(0f, width / 8f)
        path.close()
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {

    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(width, height)
    }
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        Log.d("Haibq", "draw: " + width)
        drawTriangle(canvas)
        drawBackgroundCircle(canvas)
        Timber.e("asasasasasasasaasa")
    }

}