package com.hcyun.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui

/**
 * 日程完成率环形图（v1.2.25）：
 * 展示两个环形进度——本月完成率 + 昨日完成率
 * （环中心百分比 + 完成弧 + 下方时段标签）。
 */
class MonthlyCompletionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 单时段完成率 */
    data class MonthRate(val label: String, val done: Int, val total: Int) {
        val rate: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    var months: List<MonthRate> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** 绘制进度 0f~1f：进入动画时完成弧逐渐生长（v1.2.26） */
    var drawProgress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = Ui.dp2px(context, 150f)
        setMeasuredDimension(
            View.resolveSize(Ui.dp2px(context, 320f), widthMeasureSpec),
            View.resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val n = months.size
        if (n == 0) {
            paint.color = Theme.textLight
            paint.textSize = Ui.dp2px(context, 13f).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无日程数据", w / 2, h / 2 - Ui.dp2px(context, 6f).toFloat(), paint)
            paint.textSize = Ui.dp2px(context, 10f).toFloat()
            canvas.drawText("在设置页添加日程后，这里显示完成率", w / 2, h / 2 + Ui.dp2px(context, 14f).toFloat(), paint)
            return
        }

        val gap = Ui.dp2px(context, 56f).toFloat()
        val avail = w - gap * (n - 1)
        val ringD = Math.min(avail / n, Ui.dp2px(context, 81f).toFloat())
        val ringR = ringD / 2
        val ringW = Ui.dp2px(context, 11f).toFloat()
        val cy = h / 2 - Ui.dp2px(context, 12f).toFloat()
        val cx0 = ringR + (w - (ringD * n + gap * (n - 1))) / 2

        months.forEachIndexed { i, m ->
            val cx = cx0 + i * (ringD + gap)
            val rect = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)

            // 底环（浅灰）
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ringW
            paint.color = Theme.inputBg
            canvas.drawCircle(cx, cy, ringR - ringW / 2, paint)

            // 完成率弧（玫瑰粉，从 12 点方向顺时针，随进入动画生长）
            if (m.rate > 0f && drawProgress > 0f) {
                paint.color = Theme.primary
                canvas.drawArc(rect, -90f, 360f * m.rate * drawProgress, false, paint)
            }

            // 中心百分比
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = Ui.dp2px(context, 16f).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.color = if (m.rate > 0f) Theme.primary else Theme.textSub
            canvas.drawText("${(m.rate * 100).toInt()}%", cx, cy + Ui.dp2px(context, 5f).toFloat(), paint)

            // 时段标签
            paint.textSize = Ui.dp2px(context, 12f).toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.color = Theme.textSub
            canvas.drawText(m.label, cx, cy + ringR + Ui.dp2px(context, 26f).toFloat(), paint)
        }
    }
}
