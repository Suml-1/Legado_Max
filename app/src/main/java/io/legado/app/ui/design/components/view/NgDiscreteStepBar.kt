package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/**
 * Reading NG 通用离散档位条。
 *
 * [stepCount] 表示包含首尾在内的总档位数；拖动和点击都会吸附到最近档位。
 */
class NgDiscreteStepBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var stepCount: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            selectedIndex = selectedIndex.coerceIn(0, (field - 1).coerceAtLeast(0))
            invalidate()
        }

    var selectedIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
            invalidate()
        }

    @ColorInt
    var stepColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            activePaint.color = value
            selectedPaint.color = value
            tickPaint.color = ColorUtils.setAlphaComponent(value, 190)
            inactivePaint.color = ColorUtils.setAlphaComponent(value, 38)
            invalidate()
        }

    var onSelectedIndexChanged: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val tickRadius = 4.5f * density
    private val selectedRadius = 6f * density
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * density
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * density
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stepCount <= 1 || width <= 0 || height <= 0) return
        val y = height / 2f
        repeat(stepCount - 1) { index ->
            val leftX = stepCenterX(index) + stepRadius(index)
            val rightX = stepCenterX(index + 1) - stepRadius(index + 1)
            canvas.drawLine(
                leftX,
                y,
                rightX,
                y,
                if (index < selectedIndex) activePaint else inactivePaint,
            )
        }
        repeat(stepCount) { index ->
            val x = stepCenterX(index)
            if (index == selectedIndex) {
                canvas.drawCircle(x, y, selectedRadius, selectedPaint)
            } else {
                canvas.drawCircle(x, y, tickRadius, tickPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || stepCount <= 1) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateSelection(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateSelection(event.x)
                return true
            }

            MotionEvent.ACTION_UP -> {
                updateSelection(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateSelection(x: Float) {
        val index = nearestStepIndex(x)
        if (index != selectedIndex) {
            selectedIndex = index
            onSelectedIndexChanged?.invoke(index)
        }
    }

    private fun nearestStepIndex(x: Float): Int {
        val startX = stepCenterX(0)
        val endX = stepCenterX(stepCount - 1)
        if (endX <= startX) return 0
        val progress = ((x.coerceIn(startX, endX) - startX) / (endX - startX))
            .coerceIn(0f, 1f)
        return (progress * (stepCount - 1)).roundToInt()
            .coerceIn(0, stepCount - 1)
    }

    private fun stepCenterX(index: Int): Float {
        val segmentWidth = width.toFloat() / stepCount
        return segmentWidth * index + segmentWidth / 2f
    }

    private fun stepRadius(index: Int): Float {
        return if (index == selectedIndex) selectedRadius else tickRadius
    }
}
