package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/** Chhota gol arc-reactor icon. mode: 0 idle, 1 sun raha, 2 soch raha, 3 bol raha. */
class BubbleView(ctx: Context) : View(ctx) {
    var mode = 0
        set(v) { field = v; invalidate() }

    private val cyan = 0xFF6AF4FF.toInt()
    private val gold = 0xFFF0B44C.toInt()
    private val navy = 0xFF0B1220.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var angle = 0f
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 6f
        val coreColor = when (mode) { 1 -> 0xFFFFFFFF.toInt(); 3 -> gold; else -> cyan }
        val spin = if (mode == 2) angle * 4 else angle
        val pulse = if (mode == 0) 1f else 1f + 0.10f * sin(Math.toRadians((angle * 6).toDouble())).toFloat()

        paint.pathEffect = null
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        paint.color = navy
        paint.setShadowLayer(10f, 0f, 0f, coreColor)
        c.drawCircle(cx, cy, r, paint)
        paint.clearShadowLayer()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = gold
        c.drawCircle(cx, cy, r - 2f, paint)

        paint.strokeWidth = 3f
        paint.color = cyan
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        c.save()
        c.rotate(spin, cx, cy)
        c.drawCircle(cx, cy, r * 0.74f, paint)
        c.restore()
        paint.pathEffect = null

        paint.strokeWidth = 2f
        c.drawCircle(cx, cy, r * 0.5f, paint)

        val s = r * pulse
        val path = Path().apply {
            moveTo(cx, cy - s * 0.34f)
            lineTo(cx + s * 0.30f, cy + s * 0.22f)
            lineTo(cx - s * 0.30f, cy + s * 0.22f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = coreColor
        paint.setShadowLayer(14f, 0f, 0f, coreColor)
        c.drawPath(path, paint)
    }
}
