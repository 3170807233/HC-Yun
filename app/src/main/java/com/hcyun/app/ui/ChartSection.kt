package com.hcyun.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.net.Db
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

/**
 * 图表分区（v1.4.0：从 ChartActivity 抽出，嵌入 MainActivity 单页滚动）。
 * - 白色摘要卡（最新 HCG / 孕酮 / 记录天数）
 * - 每月日程完成率环形图（本月 + 昨日）
 * - HCG 柱状图（独立）
 * - 孕酮折线图（独立）
 */
class ChartSection(private val act: AppCompatActivity, private val uid: Int) {

    private lateinit var hcgChart: TrendChartView
    private lateinit var progChart: TrendChartView
    private lateinit var completionView: MonthlyCompletionView
    private lateinit var hcgVal: TextView
    private lateinit var progVal: TextView
    private lateinit var countVal: TextView
    private lateinit var emptyHint: TextView

    fun build(container: LinearLayout) {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL

        // ---- 分区标题 ----
        val title = TextView(act)
        title.text = "📈 指标与完成率"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 摘要卡 ----
        val summary = LinearLayout(act)
        summary.orientation = LinearLayout.HORIZONTAL
        summary.gravity = Gravity.CENTER
        summary.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(summary, 3f, 22f)
        summary.setPadding(Ui.dp2px(act, 10f), Ui.dp2px(act, 14f), Ui.dp2px(act, 10f), Ui.dp2px(act, 14f))
        root.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 16f), 0, 0)
        })

        fun metricItem(color: Int, label: String): Pair<TextView, TextView> {
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setPadding(Ui.dp2px(act, 8f), 0, Ui.dp2px(act, 8f), 0)
            summary.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = TextView(act)
            v.text = "--"
            v.textSize = 20f
            v.setTypeface(null, Typeface.BOLD)
            v.setTextColor(color)
            v.gravity = Gravity.CENTER
            box.addView(v, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val l = TextView(act)
            l.text = label
            l.textSize = 11f
            l.setTextColor(Theme.textSub)
            l.gravity = Gravity.CENTER
            box.addView(l, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp2px(act, 4f), 0, 0)
            })
            return v to l
        }
        hcgVal = metricItem(Theme.hcg, "最新 HCG").first
        progVal = metricItem(Theme.prog, "最新孕酮").first
        countVal = metricItem(Theme.primary, "记录天数").first

        // ---- 完成率环形图 ----
        val compCard = LinearLayout(act)
        compCard.orientation = LinearLayout.VERTICAL
        compCard.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(compCard, 3f, 22f)
        compCard.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 12f), Ui.dp2px(act, 14f), Ui.dp2px(act, 6f))
        root.addView(compCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 16f), 0, 0)
        })

        val compTitle = TextView(act)
        compTitle.text = "📅 日程完成率（本月 / 昨日）"
        compTitle.textSize = 14f
        compTitle.setTypeface(null, Typeface.BOLD)
        compTitle.setTextColor(Theme.text)
        compCard.addView(compTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        completionView = MonthlyCompletionView(act)
        compCard.addView(completionView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 150f)).apply {
            topMargin = Ui.dp2px(act, 6f)
        })

        // ---- HCG 柱状图 ----
        val hcgCard = LinearLayout(act)
        hcgCard.orientation = LinearLayout.VERTICAL
        hcgCard.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(hcgCard, 3f, 22f)
        hcgCard.setPadding(Ui.dp2px(act, 10f), Ui.dp2px(act, 10f), Ui.dp2px(act, 10f), Ui.dp2px(act, 6f))
        root.addView(hcgCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 16f), 0, 0)
        })

        val hcgTitle = TextView(act)
        hcgTitle.text = "🔴 HCG 柱状图"
        hcgTitle.textSize = 14f
        hcgTitle.setTypeface(null, Typeface.BOLD)
        hcgTitle.setTextColor(Theme.text)
        hcgCard.addView(hcgTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        hcgChart = TrendChartView(act).apply { mode = TrendChartView.Mode.HCG }
        hcgCard.addView(hcgChart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 230f)).apply {
            topMargin = Ui.dp2px(act, 4f)
        })

        // ---- 孕酮折线图 ----
        val progCard = LinearLayout(act)
        progCard.orientation = LinearLayout.VERTICAL
        progCard.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(progCard, 3f, 22f)
        progCard.setPadding(Ui.dp2px(act, 10f), Ui.dp2px(act, 10f), Ui.dp2px(act, 10f), Ui.dp2px(act, 6f))
        root.addView(progCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 16f), 0, 0)
        })

        val progTitle = TextView(act)
        progTitle.text = "🔵 孕酮折线图"
        progTitle.textSize = 14f
        progTitle.setTypeface(null, Typeface.BOLD)
        progTitle.setTextColor(Theme.text)
        progCard.addView(progTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progChart = TrendChartView(act).apply { mode = TrendChartView.Mode.PROG }
        progCard.addView(progChart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 230f)).apply {
            topMargin = Ui.dp2px(act, 4f)
        })

        // ---- 提示 ----
        val note = TextView(act)
        note.text = "点击首页日历中的日期两次可录入 HCG / 孕酮；完成率 = 已完成的日程 ÷ 该时段全部日程"
        note.textSize = 12f
        note.gravity = Gravity.CENTER
        note.setTextColor(Theme.textSub)
        note.setPadding(0, Ui.dp2px(act, 14f), 0, 0)
        root.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        emptyHint = TextView(act)
        emptyHint.text = ""
        emptyHint.textSize = 13f
        emptyHint.gravity = Gravity.CENTER
        emptyHint.setTextColor(Theme.textLight)
        emptyHint.setPadding(0, Ui.dp2px(act, 12f), 0, Ui.dp2px(act, 20f))
        root.addView(emptyHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        container.addView(root)
    }

    /** 读本地缓存渲染（数据由 MainActivity.syncAllData 统一从服务器拉取并写入缓存） */
    fun loadData() {
        val inds = com.hcyun.app.net.LocalCache.loadIndicators(act, uid)
        val evs = com.hcyun.app.net.LocalCache.loadEvents(act, uid)
        applyData(inds, evs)
    }

    /** 直接使用最新数据渲染（MainActivity 同步完成后调用） */
    fun applyData(inds: List<Indicator>, evs: List<Event>) {
        act.runOnUiThread {
            hcgChart.data = inds.sortedBy { it.date }
            progChart.data = inds.sortedBy { it.date }
            completionView.months = calcCompletion(evs)
            renderSummary(inds, evs)
            if (inds.isEmpty()) emptyHint.text = "暂无指标数据，点击日历日期两次录入 HCG/孕酮" else emptyHint.text = ""
        }
    }

    private fun startChartAnim() {
        hcgChart.drawProgress = 0f
        progChart.drawProgress = 0f
        completionView.drawProgress = 0f
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 700
        anim.addUpdateListener {
            val v = it.animatedValue as Float
            hcgChart.drawProgress = v
            progChart.drawProgress = v
            completionView.drawProgress = v
        }
        anim.start()
    }

    private fun calcCompletion(evs: List<Event>): List<MonthlyCompletionView.MonthRate> {
        val list = ArrayList<MonthlyCompletionView.MonthRate>()
        val now = Calendar.getInstance()
        val ym = "%04d-%02d".format(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
        val monthEvents = evs.filter { it.eventDate.startsWith(ym) }
        list.add(MonthlyCompletionView.MonthRate(
            label = "本月",
            done = monthEvents.count { it.status == 1 },
            total = monthEvents.size
        ))
        val y = Calendar.getInstance()
        y.add(Calendar.DAY_OF_MONTH, -1)
        val ds = "%04d-%02d-%02d".format(
            y.get(Calendar.YEAR), y.get(Calendar.MONTH) + 1, y.get(Calendar.DAY_OF_MONTH))
        val dayEvents = evs.filter { it.eventDate == ds }
        list.add(MonthlyCompletionView.MonthRate(
            label = "昨日",
            done = dayEvents.count { it.status == 1 },
            total = dayEvents.size
        ))
        return list
    }

    private fun renderSummary(inds: List<Indicator>, evs: List<Event>) {
        if (inds.isEmpty() && evs.isEmpty()) {
            hcgVal.text = "--"
            progVal.text = "--"
            countVal.text = "0"
            emptyHint.text = "暂无数据，请在首页日历中录入日程与 HCG / 孕酮"
            return
        }
        countVal.text = inds.size.toString()
        val lastHcg = inds.lastOrNull { it.hcg != null }?.hcg
        val lastProg = inds.lastOrNull { it.progesterone != null }?.progesterone
        hcgVal.text = lastHcg?.let { fmt(it) } ?: "--"
        progVal.text = lastProg?.let { fmt(it) } ?: "--"
    }

    private fun fmt(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else "%.1f".format(v)
}
