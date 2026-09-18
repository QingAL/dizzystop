package com.qingal.dizzystop

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

class MotionCueOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val preferences = MotionCueSettings.preferences(context)
    private var settings = MotionCueSettings.read(preferences)
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
            settings = MotionCueSettings.read(sharedPreferences)
            updatePaintColors()
            postInvalidateOnAnimation()
        }

    private var targetX = 0f
    private var targetY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var lastFrameNanos = 0L
    private var released = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        updatePaintColors()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setAcceleration(screenX: Float, screenYUp: Float) {
        if (released) return

        val maxOffset = settings.maxOffsetDp * density
        targetX = (-screenX * settings.sensitivityDp * density)
            .coerceIn(-maxOffset, maxOffset)
        targetY = (screenYUp * settings.sensitivityDp * density)
            .coerceIn(-maxOffset, maxOffset)
        postInvalidateOnAnimation()
    }

    fun release() {
        released = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val frameNanos = System.nanoTime()
        val frameSeconds = if (lastFrameNanos == 0L) {
            REFERENCE_FRAME_SECONDS
        } else {
            ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND)
                .coerceIn(MIN_FRAME_SECONDS, MAX_FRAME_SECONDS)
        }
        lastFrameNanos = frameNanos

        // Convert the user-facing per-frame response into a time constant so
        // motion feels the same on 60 Hz, 90 Hz and 120 Hz displays.
        val timeConstant = -REFERENCE_FRAME_SECONDS / ln(1f - settings.smoothing)
        val frameAlpha = 1f - exp(-frameSeconds / timeConstant)
        currentX += (targetX - currentX) * frameAlpha
        currentY += (targetY - currentY) * frameAlpha

        val edge = settings.edgeMarginDp * density
        val innerRadius = settings.dotRadiusDp * density
        val outerRadius = innerRadius + 1.25f * density

        repeat(settings.dotCount) { index ->
            val fraction = (index + 1f) / (settings.dotCount + 1f)
            val y = height * fraction + currentY
            drawDot(canvas, edge + currentX, y, outerRadius, innerRadius)
            drawDot(canvas, width - edge + currentX, y, outerRadius, innerRadius)
        }

        if (
            !released &&
            (abs(targetX - currentX) > SETTLE_THRESHOLD_PX ||
                abs(targetY - currentY) > SETTLE_THRESHOLD_PX)
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        outerRadius: Float,
        innerRadius: Float
    ) {
        canvas.drawCircle(x, y, outerRadius, outerPaint)
        canvas.drawCircle(x, y, innerRadius, innerPaint)
    }

    private fun updatePaintColors() {
        val innerChannel = if (settings.darkDots) 0 else 255
        val outerChannel = if (settings.darkDots) 255 else 0
        val innerAlpha = (settings.opacity * 255).toInt()
        val outerAlpha = (settings.opacity * 90).toInt()

        innerPaint.color = Color.argb(
            innerAlpha,
            innerChannel,
            innerChannel,
            innerChannel
        )
        outerPaint.color = Color.argb(
            outerAlpha,
            outerChannel,
            outerChannel,
            outerChannel
        )
    }

    companion object {
        private const val SETTLE_THRESHOLD_PX = 0.1f
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val REFERENCE_FRAME_SECONDS = 1f / 60f
        private const val MIN_FRAME_SECONDS = 1f / 240f
        private const val MAX_FRAME_SECONDS = 0.05f
    }
}
