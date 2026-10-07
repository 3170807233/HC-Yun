package com.hcyun.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

/**
 * 自绘月历视图（与 Web 端功能一致）：
 * - 月历网格，其他月日期淡显
 * - 今天 / 选中日期：主题色描边圆角矩形（选中多一层浅粉填充），
 *   框住日期数字、指标小字与事件点，文字与圆点主题色加粗高对比
 * - 指示框宽高按格子比例（86%×86%）自适应屏幕分辨率，圆角固定 dp
 * - 日程指示（日期下方）：单日程 = 一个点；多日程 = 数量数字；
 *   有日程且当日有 HCG / 孕酮数据 = “+”号
 * - HCG / 孕酮指标小字直显
 */
class CalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // 关键：必须标记可点击，否则 onTouchEvent 对 ACTION_DOWN 返回 false，
        // 整个触摸序列被外层 ScrollView 接管，ACTION_UP 到不了本视图 → 点击日期无响应
        isClickable = true
        isFocusable = true
    }

    var year: Int = Calendar.getInstance().get(Calendar.YEAR)
    var month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    var selectedDate: String? = null
    var eventsByDate: Map<String, List<Event>> = emptyMap()
    var indicatorsByDate: Map<String, Indicator> = emptyMap()
    var onDateClick: ((dateStr: String, y: Int, m: Int, d: Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val colorText = Theme.text
    private val colorMuted = Theme.textSub
    private val colorPrimary = Theme.primary
    private val colorOther = 0x52A6AFBA.toInt()

    private val weekLabel = listOf("日", "一", "二", "三", "四", "五", "六")

    fun refresh() {
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val w = width / 7f
            val header = headerHeight()
            val cellH = cellHeight()
            if (event.y > header) {
                val col = (event.x / w).toInt()
                val row = ((event.y - header) / cellH).toInt()
                val index = row * 7 + col
                val (y, m, d) = dateForIndex(index)
                onDateClick?.invoke(dateStr(y, m, d), y, m, d)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun headerHeight(): Float = Ui.dp2px(context, 30f).toFloat()

    private fun cellHeight(): Float {
        val rows = rowsCount()
        return (height - headerHeight()) / rows
    }

    private fun rowsCount(): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val firstW = cal.get(Calendar.DAY_OF_WEEK) - 1
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return Math.ceil((firstW + days) / 7.0).toInt()
    }

    private fun dateStr(y: Int, m: Int, d: Int): String =
        "%04d-%02d-%02d".format(y, m, d)

    private fun dateForIndex(index: Int): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val firstW = cal.get(Calendar.DAY_OF_WEEK) - 1
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return when {
            index < firstW -> {
                val prev = Calendar.getInstance().apply { set(year, month - 1, 1) }
                prev.add(Calendar.DAY_OF_MONTH, index - firstW)
                Triple(prev.get(Calendar.YEAR), prev.get(Calendar.MONTH) + 1, prev.get(Calendar.DAY_OF_MONTH))
            }
            index >= firstW + days -> {
                val next = Calendar.getInstance().apply { set(year, month - 1, 1) }
                next.add(Calendar.DAY_OF_MONTH, index - firstW + 1)
                Triple(next.get(Calendar.YEAR), next.get(Calendar.MONTH) + 1, next.get(Calendar.DAY_OF_MONTH))
            }
            else -> Triple(year, month, index - firstW + 1)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = rowsCount()
        val cellH = Ui.dp2px(context, 56f)
        setMeasuredDimension(w, (headerHeight() + rows * cellH).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width / 7f
        val header = headerHeight()
        val todayStr = dateStr(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )

        // 星期头
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = false
        for (i in 0 until 7) {
            paint.color = colorMuted
            paint.textSize = Ui.dp2px(context, 13f).toFloat()
            val cx = w * i + w / 2
            val cy = header / 2 + paint.textSize / 3
            canvas.drawText(weekLabel[i], cx, cy, paint)
        }

        // 日期格
        val rows = rowsCount()
        val cellH = (height - header) / rows
        val total = rows * 7

        for (index in 0 until total) {
            val (y, m, d) = dateForIndex(index)
            val isOther = y != year || m != month
            val ds = dateStr(y, m, d)
            val cx = w * (index % 7) + w / 2
            val top = header + (index / 7) * cellH
            val cy = top + cellH / 2
            val numCy = top + cellH * 0.30f

            val isToday = ds == todayStr && !isOther
            val isSelected = ds == selectedDate && !isOther

            // 日期背景：今天 / 选中（精致圆角矩形：主题色描边 + 浅粉底，
            // 包住数字 + 指标小字 + 事件点；选中比今天多一层浅粉填充）
            // 尺寸自适应：宽 = 格宽 86%、高 = 格高 86%、圆角固定 dp
            if (isToday || isSelected) {
                val rw = w * 0.86f
                val rh = cellH * 0.86f
                val left = cx - rw / 2
                val topR = top + (cellH - rh) / 2
                val right = cx + rw / 2
                val bottom = topR + rh
                val corner = Ui.dp2px(context, 16f).toFloat()
                if (isSelected) {
                    paint.style = Paint.Style.FILL
                    paint.color = Theme.primarySoft
                    canvas.drawRoundRect(left, topR, right, bottom, corner, corner, paint)
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Ui.dp2px(context, 2f).toFloat()
                paint.color = Theme.primary
                canvas.drawRoundRect(left, topR, right, bottom, corner, corner, paint)
                paint.style = Paint.Style.FILL
            }

            // 日期数字
            paint.textSize = Ui.dp2px(context, if (isToday || isSelected) 15f else 14f).toFloat()
            paint.isFakeBoldText = isToday || isSelected
            paint.color = when {
                isToday || isSelected -> Theme.primary
                isOther -> colorOther
                else -> colorText
            }
            val baseline = numCy + paint.textSize * 0.36f
            canvas.drawText(d.toString(), cx, baseline, paint)
            paint.isFakeBoldText = false

            // 指标小字（仅当月）；今天/选中框内用主题色加粗高对比
            if (!isOther) {
                val ind = indicatorsByDate[ds]
                val onHighlight = isToday || isSelected
                var iy = numCy + cellH * 0.26f
                paint.textSize = Ui.dp2px(context, 8f).toFloat()
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = onHighlight
                if (ind?.hcg != null) {
                    paint.color = if (onHighlight) Theme.primary else colorMuted
                    canvas.drawText("HCG:${fmtNum(ind.hcg)}", cx, iy, paint)
                    iy += Ui.dp2px(context, 9f)
                }
                if (ind?.progesterone != null) {
                    paint.color = if (onHighlight) Theme.primary else colorMuted
                    canvas.drawText("P:${fmtNum(ind.progesterone)}", cx, iy, paint)
                }
                paint.isFakeBoldText = false
            }

            // 日程指示（日期下方）：仅一个日程 = 一个点；多个日程 = 数量数字；
            // 有日程且当日有 HCG / 孕酮数据 = “+”号（数据本身仍以数值直显）
            val evs = if (!isOther) eventsByDate[ds] else null
            if (!evs.isNullOrEmpty()) {
                val hasInd = indicatorsByDate[ds]?.let { it.hcg != null || it.progesterone != null } ?: false
                val dy = top + cellH * 0.78f
                paint.color = colorPrimary
                if (hasInd) {
                    paint.textSize = Ui.dp2px(context, 12f).toFloat()
                    paint.isFakeBoldText = true
                    canvas.drawText("+", cx, dy + Ui.dp2px(context, 4f), paint)
                    paint.isFakeBoldText = false
                } else if (evs.size == 1) {
                    val dotR = Ui.dp2px(context, 2.5f).toFloat()
                    canvas.drawCircle(cx, dy, dotR, paint)
                } else {
                    paint.textSize = Ui.dp2px(context, 10f).toFloat()
                    paint.isFakeBoldText = true
                    canvas.drawText(evs.size.toString(), cx, dy + Ui.dp2px(context, 4f), paint)
                    paint.isFakeBoldText = false
                }
            }
        }
    }

    private fun fmtNum(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else "%.1f".format(v)
}
