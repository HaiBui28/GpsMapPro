package com.tapbi.spark.gpsmappro.feature

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class BalanceBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), SensorEventListener {

    interface RotationListener {
        fun onRotationChanged(rotation: Float)
    }

    private var rotationListener: RotationListener? = null
    fun setRotationListener(listener: RotationListener?) {
        rotationListener = listener
    }

    private val paintMid = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val paintEnds = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val paintCircle = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val paintPlus = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val paintMovingPlus = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var smoothedRotation = 0f
    private var endsRotation = 0f
    private var lastNotifiedRotation = Float.NaN
    private var isFlat = false
    private var pitch = 0f
    private var roll = 0f

    private val snapThreshold = 60f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val norm = sqrt(ax * ax + ay * ay + az * az)
        val normAx = ax / norm
        val normAy = ay / norm
        val normAz = az / norm

        pitch = atan2(-normAx, sqrt(normAy * normAy + normAz * normAz)) * (180f / PI).toFloat()
        roll = atan2(normAy, normAz) * (180f / PI).toFloat()

        isFlat = abs(normAz) > 0.95f

        if (!isFlat) {
            val newRotation = -Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
            val delta = angleDifference(newRotation, smoothedRotation)
            smoothedRotation += 0.5f * delta
            smoothedRotation = normalizeAngle(smoothedRotation)

            val newEndsRotation = when {
                isInRange(smoothedRotation, Rotation_1, snapThreshold) -> Rotation_1
                isInRange(smoothedRotation, Rotation_2, snapThreshold) -> Rotation_3
                isInRange(smoothedRotation, Rotation_4, snapThreshold) ||
                        isInRange(smoothedRotation, -Rotation_4, snapThreshold) -> Rotation_4
                isInRange(smoothedRotation, Rotation_3, snapThreshold) -> Rotation_2
                else -> endsRotation
            }

            if (newEndsRotation != endsRotation) {
                endsRotation = newEndsRotation
                if (endsRotation != lastNotifiedRotation) {
                    lastNotifiedRotation = endsRotation
                    rotationListener?.onRotationChanged(endsRotation)
                }
            }
        }

        invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f

        if (isFlat) {
            drawFlatMode(canvas, centerX, centerY)
        } else {
            drawNormalMode(canvas, centerX, centerY)
        }
    }

    private fun drawNormalMode(canvas: Canvas, centerX: Float, centerY: Float) {
        val barLength = width.toFloat()
        val edgeBarLength = barLength * 0.2f

        val aligned = isInRange(angleDifference(smoothedRotation, endsRotation), 0f, 1f) ||
                isInRange(angleDifference(smoothedRotation, endsRotation), 180f, 1f)

        paintMid.color = if (aligned) Color.YELLOW else Color.GREEN
        paintEnds.color = if (aligned) Color.YELLOW else Color.RED

        val rotationToDrawMid = if (aligned) endsRotation else smoothedRotation

        canvas.save()
        canvas.rotate(-rotationToDrawMid, centerX, centerY)
        canvas.drawLine(
            edgeBarLength, centerY,
            barLength - edgeBarLength, centerY,
            paintMid
        )
        canvas.restore()

        canvas.save()
        canvas.rotate(-endsRotation, centerX, centerY)
        canvas.drawLine(0f, centerY, edgeBarLength, centerY, paintEnds)
        canvas.drawLine(barLength - edgeBarLength, centerY, barLength, centerY, paintEnds)
        canvas.restore()
    }

    private fun drawFlatMode(canvas: Canvas, centerX: Float, centerY: Float) {
        val radius = width / 10f
        val plusSize = radius / 2.5f

        canvas.drawCircle(centerX, centerY, radius, paintCircle)

        val dx = -pitch / 30f * radius  // trái/phải
        val dy = roll / 30f * radius    // lên/xuống
        val isInside = hypot(dx, dy) <= plusSize / 4

        val plusMovingPaint = if (isInside) paintPlus else paintMovingPlus

        if (!isInside){
            canvas.drawLine(centerX + dx - plusSize / 2, centerY + dy, centerX + dx + plusSize / 2, centerY + dy, plusMovingPaint)
            canvas.drawLine(centerX + dx, centerY + dy - plusSize / 2, centerX + dx, centerY + dy + plusSize / 2, plusMovingPaint)
        }

        paintPlus.color = Color.YELLOW
        canvas.drawLine(centerX - plusSize / 2, centerY, centerX + plusSize / 2, centerY, paintPlus)
        canvas.drawLine(centerX, centerY - plusSize / 2, centerX, centerY + plusSize / 2, paintPlus)
    }

    private fun angleDifference(a: Float, b: Float): Float {
        var diff = a - b
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    private fun normalizeAngle(angle: Float): Float {
        var result = angle
        while (result > 180) result -= 360
        while (result < -180) result += 360
        return result
    }

    private fun isInRange(value: Float, target: Float, threshold: Float = 1f): Boolean {
        return abs(angleDifference(value, target)) <= threshold
    }

    override fun onTouchEvent(event: MotionEvent?) = false
    override fun dispatchTouchEvent(ev: MotionEvent?) = false

    companion object {
        const val Rotation_1 = 0f
        const val Rotation_2 = 90f
        const val Rotation_3 = -90f
        const val Rotation_4 = 180f
    }
}
