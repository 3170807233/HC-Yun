package com.hcyun.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.hcyun.app.model.Indicator
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui

/**
 * 自绘 HCG/孕酮趋势图（与 Web 端 Chart.js 双轴图功能一致）：
 * - mode = BOTH：HCG 柱状（暖红渐变，左 Y 轴）+ 孕酮折线（蓝色，右 Y 轴）叠加
 * - mode = HCG ：仅 HCG 柱状图（独立左轴）
 * - mode = PROG：仅孕酮折线图（独立左轴）
 * - 无数据时显示引导文案
 */
class TrendChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { BOTH, HCG, PROG }

    var mode = Mode.BOTH
        set(value) {
            field = value
            invalidate()
        }

    var data: List<Indicator> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** 绘制进度 0f~1f：用于页面进入动画（柱子逐根长高、折线从左向右出现，v1.2.26） */
    var drawProgress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorText = Theme.text
    private val colorGrid = 0x14000000.toInt()
    private val colorHcg = Theme.hcg
    private val colorProg = Theme.prog

    private val showHcg get() = mode != Mode.PROG
    private val showProg get() = mode != Mode.HCG

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (data.isEmpty()) {
            paint.color = Theme.textLight
            paint.textSize = Ui.dp2px(context, 15f).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无数据，请先录入 HCG 和孕酮", w / 2, h / 2, paint)
            return
        }

        val padL = Ui.dp2px(context, 44f).toFloat()
        val padR = if (mode == Mode.BOTH) Ui.dp2px(context, 44f).toFloat()
                   else Ui.dp2px(context, 14f).toFloat()
        val padT = Ui.dp2px(context, 14f).toFloat()
        val padB = Ui.dp2px(context, 30f).toFloat()
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        if (chartW <= 0 || chartH <= 0) return

        // 值域
        var maxHcg = 0.0
        var maxProg = 0.0
        data.forEach {
            if (it.hcg != null && it.hcg > maxHcg) maxHcg = it.hcg
            if (it.progesterone != null && it.progesterone > maxProg) maxProg = it.progesterone
        }
        val hcgTop = niceCeil(maxHcg)
        val progTop = niceCeil(maxProg)

        // 虚线网格 + 轴刻度
        val n = 4
        for (i in 0..n) {
            val y = padT + chartH * i / n
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = colorGrid
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(
                Ui.dp2px(context, 3f).toFloat(), Ui.dp2px(context, 3f).toFloat()), 0f)
            canvas.drawLine(padL, y, w - padR, y, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.textSize = Ui.dp2px(context, 9f).toFloat()
            if (showHcg) {
                // 左轴（HCG）
                paint.textAlign = Paint.Align.RIGHT
                paint.color = colorHcg
                canvas.drawText(fmt(hcgTop * (n - i) / n),
                    padL - Ui.dp2px(context, 5f).toFloat(), y + Ui.dp2px(context, 3f).toFloat(), paint)
            }
            if (showProg) {
                if (mode == Mode.BOTH) {
                    // 右轴（孕酮，双轴叠加时）
                    paint.textAlign = Paint.Align.LEFT
                    paint.color = colorProg
                    canvas.drawText(fmt(progTop * (n - i) / n),
                        w - padR + Ui.dp2px(context, 5f).toFloat(), y + Ui.dp2px(context, 3f).toFloat(), paint)
                } else {
                    // 左轴（孕酮，独立折线图：v1.2.26 修复拆分后无坐标问题）
                    paint.textAlign = Paint.Align.RIGHT
                    paint.color = colorProg
                    canvas.drawText(fmt(progTop * (n - i) / n),
                        padL - Ui.dp2px(context, 5f).toFloat(), y + Ui.dp2px(context, 3f).toFloat(), paint)
                }
            }
        }

        val size = data.size
        val slot = chartW / size

        // 进入动画：从左向右逐步出现（v1.2.26）
        val visibleCount = (size * drawProgress).toInt().coerceIn(0, size)

        // HCG 柱状图（渐变填充，柱高随进度生长）
        if (showHcg) {
            data.forEachIndexed { i, ind ->
                val hcg = ind.hcg
                if (hcg != null && hcgTop > 0 && i < visibleCount) {
                    val barW = slot * 0.5f
                    val bh = (hcg / hcgTop * chartH).toFloat() * drawProgress
                    val x = padL + slot * i + (slot - barW) / 2
                    val y0 = padT + chartH - bh
                    val yBase = padT + chartH
                    val grad = LinearGradient(
                        x, y0, x, yBase,
                        Ui.withAlpha(colorHcg, 255), Ui.withAlpha(colorHcg, 150),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = grad
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(x, y0, x + barW, yBase,
                        Ui.dp2px(context, 4f).toFloat(), Ui.dp2px(context, 4f).toFloat(), paint)
                    paint.shader = null
                    paint.textSize = Ui.dp2px(context, 8.5f).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = colorHcg
                    canvas.drawText(fmt(hcg), x + barW / 2, y0 - Ui.dp2px(context, 2f).toFloat(), paint)
                }
            }
        }

        // 孕酮折线 + 渐变填充下方（从左向右逐步出现）
        if (showProg) {
            val linePath = Path()
            var started = false
            data.forEachIndexed { i, ind ->
                val p = ind.progesterone
                if (p != null && progTop > 0 && i < visibleCount) {
                    val x = padL + slot * i + slot / 2
                    val y = padT + chartH - (p / progTop * chartH).toFloat()
                    if (!started) {
                        linePath.moveTo(x, y)
                        started = true
                    } else {
                        linePath.lineTo(x, y)
                    }
                }
            }
            // 填充区域
            if (started) {
                val fillPath = Path(linePath)
                var lastX = padL + slot * (size - 1) + slot / 2
                for (i in Math.min(size - 1, visibleCount - 1) downTo 0) {
                    val p = data[i].progesterone
                    if (p != null && progTop > 0) {
                        lastX = padL + slot * i + slot / 2
                        break
                    }
                }
                fillPath.lineTo(lastX, padT + chartH)
                fillPath.lineTo(padL + slot * 0 + slot / 2, padT + chartH)
                fillPath.close()
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(
                    0f, padT, 0f, padT + chartH,
                    Ui.withAlpha(colorProg, 70), Ui.withAlpha(colorProg, 8),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(fillPath, paint)
                paint.shader = null
            }
            // 折线本体
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Ui.dp2px(context, 2.5f).toFloat()
            paint.color = colorProg
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(linePath, paint)
            paint.strokeJoin = Paint.Join.MITER
            paint.strokeCap = Paint.Cap.BUTT

            // 孕酮数据点
            paint.style = Paint.Style.FILL
            data.forEachIndexed { i, ind ->
                val p = ind.progesterone
                if (p != null && progTop > 0 && i < visibleCount) {
                    val x = padL + slot * i + slot / 2
                    val y = padT + chartH - (p / progTop * chartH).toFloat()
                    paint.color = Color_White
                    canvas.drawCircle(x, y, Ui.dp2px(context, 5f).toFloat(), paint)
                    paint.color = colorProg
                    canvas.drawCircle(x, y, Ui.dp2px(context, 3.5f).toFloat(), paint)
                }
            }
        }

        // X 轴日期（仅显示部分，防重叠）
        paint.textSize = Ui.dp2px(context, 9f).toFloat()
        paint.textAlign = Paint.Align.CENTER
        paint.color = Theme.textSub
        val step = Math.max(1, size / 6)
        data.forEachIndexed { i, ind ->
            if (i % step == 0 || i == size - 1) {
                val x = padL + slot * i + slot / 2
                canvas.drawText(ind.date.substring(5), x, padT + chartH + Ui.dp2px(context, 16f).toFloat(), paint)
            }
        }
    }

    private fun fmt(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else "%.1f".format(v)

    /** 取不小于 v 的"整齐"上限值（1/2/5 × 10^n） */
    private fun niceCeil(v: Double): Double {
        if (v <= 0) return 10.0
        val m = Math.pow(10.0, Math.floor(Math.log10(v)))
        val norm = v / m
        val nice = when {
            norm <= 1 -> 1.0
            norm <= 2 -> 2.0
            norm <= 5 -> 5.0
            else -> 10.0
        }
        return nice * m
    }

    private companion object {
        val Color_White: Int = 0xFFFFFFFF.toInt()
    }
}
