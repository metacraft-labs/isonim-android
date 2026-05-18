package com.metacraft.isonim.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup

/**
 * M-EVP-14 Wave W-3: a custom-drawn switch widget that paints its
 * own track + thumb in IsoNim brand colors directly on a Canvas.
 *
 * Samsung's One UI does not tint MaterialSwitch / SwitchCompat with
 * our indigo accent correctly — the system theme overrides the
 * tint attributes and the result is the default green/blue track.
 * This subclass bypasses Material's theme entirely: the on-state
 * track and the thumb are painted by hand inside onDraw, so the
 * captured screenshots match the cross-renderer IsoNim aesthetic.
 *
 * Wire-up: NimBridge.createView maps the lowercase "custom-switch"
 * tag (and re-routes the lowercase "switch" tag the settings_app
 * leaves emit) to this class. The renderer pushes state through the
 * standard setAttribute path:
 *   - setAttribute(handle, "checked", "true"|"false")
 *   - setAttribute(handle, "data-on-color", "#RRGGBB") (optional)
 *
 * Both attribute updates call `invalidate()` so the view repaints
 * with the next frame.
 */
class CustomSwitchView(context: Context) : View(context) {

    /** Whether the switch is in the on-state. Setter repaints. */
    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Track colour when the switch is on. Defaults to IsoNim indigo. */
    private var onTrackColor: Int = Color.parseColor("#7c7aed")
    /** Track colour when the switch is off. Muted dark to read on
     *  dark surfaces without competing for attention. */
    private val offTrackColor: Int = Color.parseColor("#1a1a22")
    /** Thumb colour (constant across on/off; matches Material spec). */
    private val thumbColor: Int = Color.parseColor("#ffffff")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = thumbColor
    }
    private val trackRect = RectF()

    init {
        // Pin a sensible default size when the parent layout passes
        // WRAP_CONTENT — the cocoa-side leaves emit data-fixed-width
        // 44 dp / data-fixed-height 24 dp, so match that ballpark.
        val lp = layoutParams
        if (lp == null) {
            layoutParams = ViewGroup.LayoutParams(
                dp(44), dp(24)
            )
        }
    }

    /** Replace the on-state track colour. Triggers a repaint. */
    fun setOnTintColor(color: Int) {
        if (onTrackColor != color) {
            onTrackColor = color
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Honour explicit dimensions from the parent; fall back to the
        // 44 x 24 dp default if the spec is UNSPECIFIED.
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> dp(44)
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> dp(24)
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Track: rounded-rectangle pill filling the view.
        trackRect.set(0f, 0f, w, h)
        val radius = h / 2f
        trackPaint.color = if (isChecked) onTrackColor else offTrackColor
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        // Thumb: circle centred vertically, 2 dp inset from the
        // track's leading or trailing edge.
        val pad = dp(2).toFloat()
        val thumbR = (h / 2f) - pad
        val cy = h / 2f
        val cx = if (isChecked) (w - pad - thumbR) else (pad + thumbR)
        canvas.drawCircle(cx, cy, thumbR, thumbPaint)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
