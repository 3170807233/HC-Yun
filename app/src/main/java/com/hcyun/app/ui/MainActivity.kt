package com.hcyun.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
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
import com.hcyun.app.model.PregnancyInfo
import com.hcyun.app.net.Db
import com.hcyun.app.notify.Notifier
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    var uid = 0
    private var year = Calendar.getInstance().get(Calendar.YEAR)
    private var month = Calendar.getInstance().get(Calendar.MONTH) + 1
    var selectedDate: String? = null

    var allEvents: List<Event> = emptyList()
    var allIndicators: List<Indicator> = emptyList()
    var pregnancyInfo: PregnancyInfo? = null

    private lateinit var calendarView: CalendarView
    private lateinit var monthLabel: TextView
    private lateinit var pregnancyText: TextView
    private lateinit var userLabel: TextView
    private lateinit var dateLabel: TextView
    private lateinit var eventListBox: LinearLayout
    private lateinit var rootLayout: LinearLayout
    private lateinit var notifyBanner: TextView
    private lateinit var scroll: ScrollView

    private var homeAnchor: View? = null
    private var memoAnchor: View? = null
    private var chartAnchor: View? = null

    private lateinit var railController: com.hcyun.app.util.Ui.RailController
    private lateinit var importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var memoSection: MemoSection
    private lateinit var chartSection: ChartSection

    private var lastSyncTime = 0L
    private lateinit var statusHandler: android.os.Handler
    private val statusProbe = object : Runnable {
        override fun run() {
            syncAllData(force = true)
            statusHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        uid = Prefs.getUid(this)
        if (uid <= 0) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        importLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { SettingsDialog.importTxt(this, uid, it) }
        }
        buildUi()
        if (selectedDate == null) selectedDate = todayStr()
        loadAllData()
        requestNotifyPermission()
        Notifier.createChannel(this)
        Notifier.scheduleDaily(this)
        statusHandler = android.os.Handler(mainLooper)
        syncAllData(force = true)
        statusHandler.postDelayed(statusProbe, 30_000)
    }

    private fun requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun updateNotifyBanner() {
        if (!::notifyBanner.isInitialized) return
        val denied = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        notifyBanner.visibility = if (denied) View.VISIBLE else View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateNotifyBanner()
    }

    override fun onResume() {
        super.onResume()
        loadAllData()
        if (::memoSection.isInitialized) memoSection.loadNotes()
        if (::chartSection.isInitialized) chartSection.loadData()
        updateNotifyBanner()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_GOTO_TODAY, false) == true) {
            goToday()
        }
    }

    private fun buildUi() {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Theme.bg)

        scroll = ScrollView(this)
        scroll.isFillViewport = false
        scroll.setBackgroundColor(Theme.bg)
        frame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.setPadding(
            Ui.dp2px(this, 14f), Ui.dp2px(this, 12f),
            Ui.dp2px(this, 80f), Ui.dp2px(this, 24f))
        scroll.addView(rootLayout, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.background = Ui.glassCard(this, 22f)
        topBar.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 10f), Ui.dp2px(this, 12f), Ui.dp2px(this, 10f))
        rootLayout.addView(topBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@MainActivity, 14f))
        })

        notifyBanner = TextView(this)
        notifyBanner.text = "通知未开启，点击开启（日程到点提醒）"
        notifyBanner.textSize = 13f
        notifyBanner.gravity = Gravity.CENTER
        notifyBanner.setTypeface(null, Typeface.BOLD)
        notifyBanner.setTextColor(Theme.primary)
        notifyBanner.setPadding(0, Ui.dp2px(this, 11f), 0, Ui.dp2px(this, 11f))
        notifyBanner.background = Ui.roundedBg(this, Theme.primarySoft, 15f, Theme.primary, 1f)
        notifyBanner.setOnClickListener {
            try {
                val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(i)
            } catch (e: Exception) {
                Ui.toast(this, "请在系统设置中开启通知权限")
            }
        }
        rootLayout.addView(notifyBanner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@MainActivity, 14f))
        })
        updateNotifyBanner()

        val avatar = AvatarView(this)
        topBar.addView(avatar, LinearLayout.LayoutParams(
            Ui.dp2px(this, 40f), Ui.dp2px(this, 40f)))

        userLabel = TextView(this)
        userLabel.text = Prefs.displayName(this)
        userLabel.textSize = 14.5f
        userLabel.setTypeface(null, Typeface.BOLD)
        userLabel.setTextColor(Theme.text)
        userLabel.setPadding(Ui.dp2px(this, 10f), 0, 0, 0)
        userLabel.gravity = Gravity.CENTER_VERTICAL
        userLabel.setOnClickListener { showRenameDialog() }
        topBar.addView(userLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val logoutBtn = TextView(this)
        logoutBtn.text = "退出"
        logoutBtn.textSize = 13f
        logoutBtn.setTextColor(Theme.primary)
        logoutBtn.gravity = Gravity.CENTER
        logoutBtn.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 7f), Ui.dp2px(this, 16f), Ui.dp2px(this, 7f))
        logoutBtn.background = Ui.roundedBg(this, Theme.primarySoft, 18f)
        Ui.bindPress(logoutBtn)
        logoutBtn.setOnClickListener {
            Prefs.clearUser(this)
            Prefs.setMode(this, false)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        topBar.addView(logoutBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val pregCard = LinearLayout(this)
        pregCard.orientation = LinearLayout.VERTICAL
        pregCard.gravity = Gravity.CENTER_VERTICAL
        pregCard.background = Ui.glassCard(this, 24f)
        Ui.shadow(pregCard, 3f)
        pregCard.setPadding(Ui.dp2px(this, 18f), Ui.dp2px(this, 18f), Ui.dp2px(this, 18f), Ui.dp2px(this, 18f))
        rootLayout.addView(pregCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@MainActivity, 16f))
        })
        pregnancyText = TextView(this)
        pregnancyText.text = "加载中..."
        pregnancyText.textSize = 16f
        pregnancyText.setTypeface(null, Typeface.BOLD)
        pregnancyText.setTextColor(Theme.text)
        pregnancyText.gravity = Gravity.CENTER
        pregnancyText.setLineSpacing(0f, 1.25f)
        pregCard.addView(pregnancyText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val calCard = LinearLayout(this)
        calCard.orientation = LinearLayout.VERTICAL
        calCard.background = Ui.glassCard(this, 24f)
        Ui.shadow(calCard, 3f)
        calCard.setPadding(Ui.dp2px(this, 10f), Ui.dp2px(this, 6f), Ui.dp2px(this, 10f), Ui.dp2px(this, 8f))
        homeAnchor = calCard
        rootLayout.addView(calCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.gravity = Gravity.CENTER_VERTICAL
        navBar.setPadding(Ui.dp2px(this, 4f), Ui.dp2px(this, 2f), Ui.dp2px(this, 4f), Ui.dp2px(this, 2f))
        calCard.addView(navBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val prevBtn = makeNavBtn("◀")
        Ui.bindPress(prevBtn)
        prevBtn.setOnClickListener { changeMonth(-1) }
        navBar.addView(prevBtn, LinearLayout.LayoutParams(Ui.dp2px(this, 42f), Ui.dp2px(this, 42f)))

        val monthWrap = LinearLayout(this)
        monthWrap.orientation = LinearLayout.HORIZONTAL
        monthWrap.gravity = Gravity.CENTER
        navBar.addView(monthWrap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        monthLabel = TextView(this)
        monthLabel.text = "2026年9月"
        monthLabel.textSize = 20f
        monthLabel.setTypeface(null, Typeface.BOLD)
        monthLabel.setTextColor(Theme.text)
        monthWrap.addView(monthLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val nextBtn = makeNavBtn("▶")
        Ui.bindPress(nextBtn)
        nextBtn.setOnClickListener { changeMonth(1) }
        navBar.addView(nextBtn, LinearLayout.LayoutParams(Ui.dp2px(this, 42f), Ui.dp2px(this, 42f)))

        calendarView = CalendarView(this)
        calendarView.year = year
        calendarView.month = month
        calendarView.onDateClick = { dateStr, y, m, d ->
            if (dateStr == selectedDate) {
                showIndicatorDialog(dateStr)
            } else {
                selectDate(dateStr)
            }
        }
        calCard.addView(calendarView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp2px(this@MainActivity, 2f)
        })

        dateLabel = TextView(this)
        dateLabel.text = "📅 请选择日期查看日程"
        dateLabel.textSize = 14f
        dateLabel.setTypeface(null, Typeface.BOLD)
        dateLabel.setTextColor(Theme.text)
        rootLayout.addView(dateLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(Ui.dp2px(this@MainActivity, 4f), Ui.dp2px(this@MainActivity, 18f), 0, Ui.dp2px(this@MainActivity, 10f))
        })

        eventListBox = LinearLayout(this)
        eventListBox.orientation = LinearLayout.VERTICAL
        rootLayout.addView(eventListBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val chartAnchorBox = LinearLayout(this)
        chartAnchorBox.orientation = LinearLayout.VERTICAL
        chartSection = ChartSection(this, uid)
        chartSection.build(chartAnchorBox)
        chartAnchor = chartAnchorBox
        rootLayout.addView(chartAnchorBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@MainActivity, 28f), 0, 0)
        })

        val memoAnchorBox = LinearLayout(this)
        memoAnchorBox.orientation = LinearLayout.VERTICAL
        memoSection = MemoSection(this, uid)
        memoSection.build(memoAnchorBox)
        memoAnchor = memoAnchorBox
        rootLayout.addView(memoAnchorBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@MainActivity, 28f), 0, 0)
        })

        railController = Ui.attachSideRail(
            this, frame,
            onHome = { onHomeClick() },
            onMemo = { scrollToAnchor(memoAnchor) },
            onChart = { scrollToAnchor(chartAnchor) },
            onSettings = { SettingsDialog.show(this, uid, importLauncher) },
            initialOnLeft = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("side_on_left", false),
            onSideChanged = { onLeft ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("side_on_left", onLeft).apply()
                val left = Ui.dp2px(this, if (onLeft) 80f else 14f)
                val right = Ui.dp2px(this, if (onLeft) 14f else 80f)
                rootLayout.setPadding(left, Ui.dp2px(this, 12f), right, Ui.dp2px(this, 24f))
            }
        )

        setContentView(frame)

        // 每次点击界面静默同步（30 秒节流，不打断操作）
        frame.setOnTouchListener { _, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                syncAllData(force = false)
            }
            false
        }

        Ui.animateIn(topBar, 16f, 260)
        Ui.animateIn(pregCard, 20f, 320, 60L)
        Ui.animateIn(calCard, 22f, 320, 120L)
    }

    private fun scrollToAnchor(anchor: View?) {
        anchor ?: return
        val y = IntArray(2)
        anchor.getLocationInWindow(y)
        val targetScrollY = scroll.scrollY + y[1] - Ui.dp2px(this, 20f)
        scroll.smoothScrollTo(0, targetScrollY)
    }

    /** 点「首页」：未在顶则回顶；已在顶则回到今日 */
    private fun onHomeClick() {
        val topThreshold = Ui.dp2px(this, 24f)
        if (scroll.scrollY > topThreshold) {
            scroll.smoothScrollTo(0, 0)
        } else {
            goToday()
        }
    }

    private fun makeNavBtn(text: String): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.setTextColor(Theme.text)
        b.background = Ui.roundedBg(this, Theme.inputBg, 20f)
        return b
    }

    private fun todayStr(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun loadAllData() {
        // 1) 先用本地缓存渲染（立即显示，不阻塞）
        val cachedEvs = com.hcyun.app.net.LocalCache.loadEvents(this, uid)
        val cachedInds = com.hcyun.app.net.LocalCache.loadIndicators(this, uid)
        allEvents = cachedEvs
        allIndicators = cachedInds
        renderCalendar()
        renderPregnancy()
        selectedDate?.let { renderEventsFor(it) }
        if (::memoSection.isInitialized) memoSection.loadNotes()
        if (::chartSection.isInitialized) chartSection.loadData()

        // 2) 后台拉 MySQL 刷新缓存（统一走 syncAllData）
        syncAllData(force = true)
    }

    fun reloadEventsAndIndicators() {
        Thread {
            try {
                val evs = Db.getAllEvents(this, uid)
                val inds = Db.getAllIndicators(this, uid)
                runOnUiThread {
                    allEvents = evs
                    allIndicators = inds
                    renderCalendar()
                    selectedDate?.let { renderEventsFor(it) }
                    if (::chartSection.isInitialized) chartSection.applyData(inds, evs)
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "刷新失败：${e.message}") }
            }
        }.start()
    }

    fun renderCalendar() {
        calendarView.year = year
        calendarView.month = month
        calendarView.selectedDate = selectedDate
        val map = HashMap<String, MutableList<Event>>()
        allEvents.filter { it.eventDate.startsWith("%04d-%02d".format(year, month)) }
            .forEach { map.getOrPut(it.eventDate) { mutableListOf() }.add(it) }
        calendarView.eventsByDate = map

        val indMap = HashMap<String, Indicator>()
        allIndicators.filter { it.date.startsWith("%04d-%02d".format(year, month)) }
            .forEach { indMap[it.date] = it }
        calendarView.indicatorsByDate = indMap
        calendarView.refresh()
        monthLabel.text = "${year}年${month}月"
    }

    private fun changeMonth(delta: Int) {
        var m = month + delta
        var y = year
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        year = y
        month = m
        renderCalendar()
    }

    fun refreshUserLabel() {
        userLabel.text = Prefs.displayName(this)
    }

    private fun goToday() {
        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1
        renderCalendar()
        selectDate(todayStr())
    }

    private fun selectDate(dateStr: String) {
        selectedDate = dateStr
        renderCalendar()
        renderEventsFor(dateStr)
    }

    fun renderEventsFor(dateStr: String) {
        val d = dateStr
        dateLabel.text = "📅 $d 的日程"
        eventListBox.removeAllViews()
        val events = allEvents.filter { it.eventDate == d }
            .sortedBy { periodOrder(it.period) }
        if (events.isEmpty()) {
            val empty = TextView(this)
            empty.text = "当天暂无日程"
            empty.textSize = 13f
            empty.setTextColor(Theme.textLight)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f))
            eventListBox.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        events.forEach { eventListBox.addView(buildEventRow(it)) }
    }

    fun periodOrder(p: String): Int = when (p) {
        "morning" -> 0
        "afternoon" -> 1
        else -> 2
    }

    private fun buildEventRow(ev: Event): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.glassCard(this, 16f)
        row.setPadding(Ui.dp2px(this, 12f), Ui.dp2px(this, 12f), Ui.dp2px(this, 10f), Ui.dp2px(this, 12f))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp2px(this, 10f))
        row.layoutParams = lp

        val (bg, fg) = when (ev.period) {
            "morning" -> Color.parseColor("#FFF1E3") to Color.parseColor("#C47A36")
            "afternoon" -> Color.parseColor("#FFF7DC") to Color.parseColor("#B08A1E")
            else -> Color.parseColor("#EFEAFB") to Color.parseColor("#6A5ACD")
        }
        val periodLabel = when (ev.period) {
            "morning" -> "🌅 早"
            "afternoon" -> "☀️ 中"
            else -> "🌙 晚"
        }
        val badge = TextView(this)
        badge.text = periodLabel
        badge.textSize = 12f
        badge.setTypeface(null, Typeface.BOLD)
        badge.setTextColor(fg)
        badge.gravity = Gravity.CENTER
        badge.background = Ui.roundedBg(this, bg, 30f)
        badge.setPadding(Ui.dp2px(this, 10f), Ui.dp2px(this, 6f), Ui.dp2px(this, 10f), Ui.dp2px(this, 6f))
        row.addView(badge, LinearLayout.LayoutParams(
            Ui.dp2px(this, 62f), ViewGroup.LayoutParams.WRAP_CONTENT))

        val infoBox = LinearLayout(this)
        infoBox.orientation = LinearLayout.VERTICAL
        infoBox.setPadding(Ui.dp2px(this, 10f), 0, Ui.dp2px(this, 6f), 0)
        row.addView(infoBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val title = TextView(this)
        title.text = ev.title
        title.textSize = 14.5f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        title.setLineSpacing(0f, 1.1f)
        infoBox.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (ev.description.isNotEmpty()) {
            val desc = TextView(this)
            desc.text = ev.description
            desc.textSize = 12f
            desc.setTextColor(Theme.textSub)
            infoBox.addView(desc, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val done = ev.status == 1
        if (done) {
            title.paintFlags = title.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            title.alpha = 0.6f
        }

        val toggle = TextView(this)
        toggle.text = if (done) "✅" else "☐"
        toggle.textSize = 22f
        toggle.gravity = Gravity.CENTER
        Ui.bindPress(toggle)
        toggle.setOnClickListener {
            if (ev.eventDate > java.time.LocalDate.now().toString()) {
                Ui.toast(this, "未来日期不能标记完成")
                return@setOnClickListener
            }
            val newStatus = if (done) 0 else 1
            toggle.isEnabled = false
            Thread {
                try {
                    Db.updateStatus(this, uid, ev.id, newStatus)
                    runOnUiThread {
                        toggle.isEnabled = true
                        val idx = allEvents.indexOfFirst { it.id == ev.id }
                        if (idx >= 0) {
                            allEvents = allEvents.toMutableList().also {
                                it[idx] = it[idx].copy(status = newStatus)
                            }
                        }
                        com.hcyun.app.net.LocalCache.saveEvents(this@MainActivity, uid, allEvents)
                        selectedDate?.let { renderEventsFor(it) }
                        renderCalendar()
                        if (::chartSection.isInitialized) chartSection.applyData(allIndicators, allEvents)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        toggle.isEnabled = true
                        Ui.toast(this, "状态更新失败：${e.message}")
                    }
                }
            }.start()
        }
        row.addView(toggle, LinearLayout.LayoutParams(Ui.dp2px(this, 40f), Ui.dp2px(this, 40f)))

        row.setOnLongClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确定删除此事件？")
                .setMessage("「${ev.title}」将被删除")
                .setPositiveButton("删除") { _, _ ->
                    Thread {
                        try {
                            Db.deleteEvent(this, uid, ev.id)
                            runOnUiThread {
                                allEvents = allEvents.filterNot { it.id == ev.id }
                                com.hcyun.app.net.LocalCache.saveEvents(this@MainActivity, uid, allEvents)
                                selectedDate?.let { renderEventsFor(it) }
                                renderCalendar()
                                if (::chartSection.isInitialized) chartSection.applyData(allIndicators, allEvents)
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Ui.toast(this, "删除失败：${e.message}") }
                        }
                    }.start()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        return row
    }

    private fun renderPregnancy() {
        val info = pregnancyInfo
        if (info != null) {
            val text = if (info.isOverdue) {
                "🤰 孕周：${info.weeks}+${info.days}周 | 预产期：${info.dueDate} | 已过预产期 ${kotlin.math.abs(info.daysRemaining)} 天"
            } else {
                "🤰 孕周：${info.weeks}+${info.days}周 | 预产期：${info.dueDate} | 距离预产期还有 ${info.daysRemaining} 天"
            }
            pregnancyText.text = text
        } else {
            pregnancyText.text = "⚙️ 点击右侧「设置」配置孕产信息（末次月经 LMP）"
        }
    }

    /** 设置弹窗关闭后全量刷新（事件/孕产/备忘录/图表） */
    fun refreshAllAfterDialog() {
        loadAllData()
        if (::memoSection.isInitialized) memoSection.loadNotes()
        if (::chartSection.isInitialized) chartSection.loadData()
    }

    fun refreshAfterMutation() {
        reloadEventsAndIndicators()
        Thread {
            try {
                val preg = Db.getPregnancy(this, uid).second
                runOnUiThread { pregnancyInfo = preg; renderPregnancy() }
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * 统一静默同步（v1.4.12）：
     * - 拉取全部数据（日程/指标/备忘录/孕产）→ 写本地缓存 → 刷新全部界面
     * - 每 30 秒定时强制同步；每次点击界面节流同步（距上次成功 < 30 秒跳过）
     * - 网站改动数据库后，App 最迟 30 秒内自动反映到界面
     */
    private fun syncAllData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTime < 30_000) return
        Thread {
            try {
                val evs = com.hcyun.app.net.Db.getAllEvents(this, uid)
                val inds = com.hcyun.app.net.Db.getAllIndicators(this, uid)
                val notes = com.hcyun.app.net.Db.getNotes(this, uid)
                val preg = com.hcyun.app.net.Db.getPregnancy(this, uid)
                com.hcyun.app.net.LocalCache.saveEvents(this, uid, evs)
                com.hcyun.app.net.LocalCache.saveIndicators(this, uid, inds)
                com.hcyun.app.net.LocalCache.saveNotes(this, uid, notes)
                com.hcyun.app.net.LocalCache.saveLmp(this, uid, preg.first)
                lastSyncTime = System.currentTimeMillis()
                runOnUiThread {
                    railController.updateStatus(true)
                    allEvents = evs
                    allIndicators = inds
                    pregnancyInfo = preg.second
                    renderCalendar()
                    renderPregnancy()
                    selectedDate?.let { renderEventsFor(it) }
                    if (::memoSection.isInitialized) memoSection.applyNotes(notes)
                    if (::chartSection.isInitialized) chartSection.applyData(inds, evs)
                }
            } catch (_: Exception) {
                runOnUiThread { railController.updateStatus(false) }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::statusHandler.isInitialized) {
            statusHandler.removeCallbacks(statusProbe)
        }
    }

    companion object {
        const val EXTRA_GOTO_TODAY = "goto_today"
    }
}