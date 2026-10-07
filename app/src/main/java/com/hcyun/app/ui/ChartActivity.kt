package com.hcyun.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.net.Db
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

/**
 * 趋势图表独立页面（v1.2.24 增强）：
 * - 顶栏标题 + 白色摘要卡（最新 HCG / 孕酮 / 记录天数）
 * - 每月日程完成率环形图（最近 6 个月，圆形饼图）
 * - HCG 柱状图（独立）
 * - 孕酮折线图（独立）
 * - 图例说明 + 悬浮底栏
 */
class ChartActivity : AppCompatActivity() {

    private var uid = 0
    private lateinit var hcgChart: TrendChartView
    private lateinit var progChart: TrendChartView
    private lateinit var completionView: MonthlyCompletionView
    private lateinit var hcgVal: TextView
    private lateinit var progVal: TextView
    private lateinit var countVal: TextView
    private lateinit var emptyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        uid = Prefs.getUid(this)
        if (uid <= 0) {
            finish()
            return
        }
        buildUi()
        loadData()
    }

    private fun buildUi() {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Theme.bg)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.bg)
        frame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 12f), Ui.dp2px(this, 16f), Ui.dp2px(this, 110f))
        scroll.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 顶栏（仅标题，返回交给悬浮底栏） ----
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        root.addView(topBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(this)
        title.text = "📈 指标与完成率"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // ---- 摘要卡（白色玻璃） ----
        val summary = LinearLayout(this)
        summary.orientation = LinearLayout.HORIZONTAL
        summary.gravity = Gravity.CENTER
        summary.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(summary, 3f, 22f)
        summary.setPadding(Ui.dp2px(this, 10f), Ui.dp2px(this, 14f), Ui.dp2px(this, 10f), Ui.dp2px(this, 14f))
        root.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@ChartActivity, 16f), 0, 0)
        })

        fun metricItem(color: Int, label: String): Pair<TextView, TextView> {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setPadding(Ui.dp2px(this, 8f), 0, Ui.dp2px(this, 8f), 0)
            summary.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = TextView(this)
            v.text = "--"
            v.textSize = 20f
            v.setTypeface(null, Typeface.BOLD)
            v.setTextColor(color)
            v.gravity = Gravity.CENTER
            box.addView(v, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val l = TextView(this)
            l.text = label
            l.textSize = 11f
            l.setTextColor(Theme.textSub)
            l.gravity = Gravity.CENTER
            box.addView(l, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp2px(this@ChartActivity, 4f), 0, 0)
            })
            return v to l
        }
        hcgVal = metricItem(Theme.hcg, "最新 HCG").first
        progVal = metricItem(Theme.prog, "最新孕酮").first
        countVal = metricItem(Theme.primary, "记录天数").first

        // ---- 每月日程完成率（环形饼图，v1.2.24） ----
        val compCard = LinearLayout(this)
        compCard.orientation = LinearLayout.VERTICAL
        compCard.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(compCard, 3f, 22f)
        compCard.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 12f), Ui.dp2px(this, 14f), Ui.dp2px(this, 6f))
        root.addView(compCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@ChartActivity, 16f), 0, 0)
        })

        val compTitle = TextView(this)
        compTitle.text = "📅 日程完成率（本月 / 昨日）"
        compTitle.textSize = 14f
        compTitle.setTypeface(null, Typeface.BOLD)
        compTitle.setTextColor(Theme.text)
        compCard.addView(compTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        completionView = MonthlyCompletionView(this)
        compCard.addView(completionView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 150f)).apply {
            topMargin = Ui.dp2px(this@ChartActivity, 6f)
        })

        // ---- HCG 柱状图（独立） ----
        val hcgCard = LinearLayout(this)
        hcgCard.orientation = LinearLayout.VERTICAL
        hcgCard.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(hcgCard, 3f, 22f)
        hcgCard.setPadding(Ui.dp2px(this, 10f), Ui.dp2px(this, 10f), Ui.dp2px(this, 10f), Ui.dp2px(this, 6f))
        root.addView(hcgCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@ChartActivity, 16f), 0, 0)
        })

        val hcgTitle = TextView(this)
        hcgTitle.text = "🔴 HCG 柱状图"
        hcgTitle.textSize = 14f
        hcgTitle.setTypeface(null, Typeface.BOLD)
        hcgTitle.setTextColor(Theme.text)
        hcgCard.addView(hcgTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        hcgChart = TrendChartView(this).apply { mode = TrendChartView.Mode.HCG }
        hcgCard.addView(hcgChart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 230f)).apply {
            topMargin = Ui.dp2px(this@ChartActivity, 4f)
        })

        // ---- 孕酮折线图（独立） ----
        val progCard = LinearLayout(this)
        progCard.orientation = LinearLayout.VERTICAL
        progCard.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(progCard, 3f, 22f)
        progCard.setPadding(Ui.dp2px(this, 10f), Ui.dp2px(this, 10f), Ui.dp2px(this, 10f), Ui.dp2px(this, 6f))
        root.addView(progCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@ChartActivity, 16f), 0, 0)
        })

        val progTitle = TextView(this)
        progTitle.text = "🔵 孕酮折线图"
        progTitle.textSize = 14f
        progTitle.setTypeface(null, Typeface.BOLD)
        progTitle.setTextColor(Theme.text)
        progCard.addView(progTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progChart = TrendChartView(this).apply { mode = TrendChartView.Mode.PROG }
        progCard.addView(progChart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 230f)).apply {
            topMargin = Ui.dp2px(this@ChartActivity, 4f)
        })

        // ---- 提示 ----
        val note = TextView(this)
        note.text = "点击首页日历中的日期两次可录入 HCG / 孕酮；完成率 = 已完成的日程 ÷ 该时段全部日程"
        note.textSize = 12f
        note.gravity = Gravity.CENTER
        note.setTextColor(Theme.textSub)
        note.setPadding(0, Ui.dp2px(this, 14f), 0, 0)
        root.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        emptyHint = TextView(this)
        emptyHint.text = ""
        emptyHint.textSize = 13f
        emptyHint.gravity = Gravity.CENTER
        emptyHint.setTextColor(Theme.textLight)
        emptyHint.setPadding(0, Ui.dp2px(this, 12f), 0, 0)
        root.addView(emptyHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 悬浮底栏 ----
        Ui.attachFloatingBar(this, frame, "chart")

        setContentView(frame)

        Ui.animateIn(topBar, 14f, 260)
        Ui.animateIn(summary, 18f, 300, 60L)
        Ui.animateIn(compCard, 20f, 320, 120L)
        Ui.animateIn(hcgCard, 20f, 340, 160L)
        Ui.animateIn(progCard, 20f, 360, 200L)
        Ui.animateIn(note, 14f, 360, 240L)
    }

    /** 图表进入动画：柱/线/环从左到右、从下到上生长（v1.2.26） */
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

    private fun loadData() {
        Thread {
            try {
                val inds = Db.getAllIndicators(this, uid)
                val evs = Db.getAllEvents(this, uid)
                runOnUiThread {
                    hcgChart.data = inds.sortedBy { it.date }
                    progChart.data = inds.sortedBy { it.date }
                    completionView.months = calcCompletion(evs)
                    renderSummary(inds, evs)
                    startChartAnim()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    emptyHint.text = "加载失败：${e.message}"
                    Ui.toast(this, "加载图表失败：${e.message}")
                }
            }
        }.start()
    }

    /** 完成率：本月 + 昨日（前一天），无日程时段显示 0% */
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
