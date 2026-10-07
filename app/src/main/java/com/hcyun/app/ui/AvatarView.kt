package com.hcyun.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui

/**
 * 用户头像（v1.2.27）：粉色渐变圆底 + ⚤（♂ 圆环箭头 + ♀ 下方十字）
 * 白线图形。纯 Canvas 绘制，避免 emoji/字符在部分机型字体缺失。
 */
class AvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = 0xFFFFFFFF.toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = Ui.dp2px(context, 40f)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = Math.min(w, h) / 2f

        // 圆形渐变底（玫瑰粉 → 浅粉）
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            Theme.primary, Theme.primarySoft, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        val u = r / 20f // 单位长度

        // ⚤ 图形（白色线条）
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * u
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = white

        // ♀ 圆环
        canvas.drawCircle(cx, cy + 1f * u, 7f * u, paint)

        // ♂ 箭头（圆环右上）
        val ax0x = cx + 6f * u
        val ax0y = cy - 4f * u
        val ax1x = cx + 11f * u
        val ax1y = cy - 9f * u
        canvas.drawLine(ax0x, ax0y, ax1x, ax1y, paint)
        canvas.drawLine(ax1x, ax1y, ax1x - 4f * u, ax1y + 1f * u, paint)
        canvas.drawLine(ax1x, ax1y, ax1x - 2f * u, ax1y - 3.2f * u, paint)

        // ♀ 十字（圆环下方）
        val by = cy + 12f * u
        canvas.drawLine(cx, cy + 8f * u, cx, by, paint)
        canvas.drawLine(cx - 3.4f * u, by - 1.4f * u, cx + 3.4f * u, by - 1.4f * u, paint)
    }
}
