package com.hcyun.app.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Event
import com.hcyun.app.net.Db
import com.hcyun.app.net.EventImport
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

/**
 * 设置弹窗（v1.4.0：从 SettingsActivity 抽出，改为全屏 Dialog）。
 * 孕产设置 / 添加新事件 / 提醒设置 / 导入导出 / 所有事件（重复日程合并堆叠）。
 */
object SettingsDialog {

    fun show(
        act: AppCompatActivity, uid: Int,
        importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    ) {
        val dlg = Dialog(act)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setCancelable(true)

        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        val density = act.resources.displayMetrics.density
        val radius = 28f * density
        val rootBg = android.graphics.drawable.GradientDrawable()
        rootBg.setColor(Theme.bg)
        rootBg.cornerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, 0f, 0f)
        root.background = rootBg
        root.setPadding(Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 20f))

        // ---- 顶栏 ----
        val topBar = LinearLayout(act)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        root.addView(topBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(act)
        title.text = "⚙️ 设置"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val close = TextView(act)
        close.text = "✕"
        close.textSize = 18f
        close.gravity = Gravity.CENTER
        close.setTextColor(Theme.textSub)
        close.setPadding(Ui.dp2px(act, 12f), 0, 0, 0)
        Ui.bindPress(close)
        close.setOnClickListener { dlg.dismiss() }
        topBar.addView(close, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 内容滚动 ----
        val scroll = ScrollView(act)
        scroll.isFillViewport = false
        scroll.setPadding(0, 0, 0, Ui.dp2px(act, 16f))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })
        val content = LinearLayout(act)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ============ 状态 ============
        var pickedLmp = ""
        var pickedEvDate = todayStr(act)
        lateinit var lmpValue: TextView
        lateinit var evDateValue: TextView
        val allBox = LinearLayout(act)
        allBox.orientation = LinearLayout.VERTICAL
        var allEvents: List<Event> = emptyList()
        val expandedGroups = HashMap<String, Boolean>()

        fun sectionTitle(text: String): TextView {
            val t = TextView(act)
            t.text = text
            t.textSize = 15f
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(Theme.text)
            t.setPadding(0, 0, 0, Ui.dp2px(act, 10f))
            return t
        }

        fun primaryBtn(text: String): TextView {
            val b = TextView(act)
            b.text = text
            b.textSize = 14f
            b.setTypeface(null, Typeface.BOLD)
            b.gravity = Gravity.CENTER
            b.setTextColor(Color.WHITE)
            b.background = Ui.gradientBg(act, Theme.primaryStart, Theme.primaryEnd, 20f)
            Ui.shadowRound(b, 3f, 20f)
            Ui.bindPress(b)
            return b
        }

        fun secondaryBtn(text: String): TextView {
            val b = TextView(act)
            b.text = text
            b.textSize = 14f
            b.setTypeface(null, Typeface.BOLD)
            b.gravity = Gravity.CENTER
            b.setTextColor(Theme.primary)
            b.background = Ui.roundedBg(act, Color.WHITE, 20f, Color.parseColor("#E4E7EB"), 1.5f)
            Ui.bindPress(b)
            return b
        }

        // ============ ① 孕产设置 ============
        val card1 = LinearLayout(act)
        card1.orientation = LinearLayout.VERTICAL
        card1.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(card1, 3f, 22f)
        card1.setPadding(Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f))
        content.addView(card1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card1.addView(sectionTitle("🤰 孕产设置"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lmpRow = LinearLayout(act)
        lmpRow.orientation = LinearLayout.HORIZONTAL
        lmpRow.gravity = Gravity.CENTER_VERTICAL
        card1.addView(lmpRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 6f), 0, 0)
        })
        val lmpLabel = TextView(act)
        lmpLabel.text = "末次月经 (LMP)："
        lmpLabel.textSize = 14f
        lmpLabel.setTextColor(Theme.text)
        lmpRow.addView(lmpLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        lmpValue = TextView(act)
        lmpValue.text = "加载中..."
        lmpValue.textSize = 14f
        lmpValue.setTypeface(null, Typeface.BOLD)
        lmpValue.setTextColor(Theme.primary)
        lmpRow.addView(lmpValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val lmpPickBtn = TextView(act)
        lmpPickBtn.text = "选择日期"
        lmpPickBtn.textSize = 13f
        lmpPickBtn.setTextColor(Color.WHITE)
        lmpPickBtn.gravity = Gravity.CENTER
        lmpPickBtn.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 8f), Ui.dp2px(act, 14f), Ui.dp2px(act, 8f))
        lmpPickBtn.background = Ui.gradientBg(act, Theme.primaryStart, Theme.primaryEnd, 20f)
        Ui.shadowRound(lmpPickBtn, 3f, 20f)
        Ui.bindPress(lmpPickBtn)
        lmpPickBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            pickedLmp.split("-").let {
                if (it.size == 3) cal.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt())
            }
            DatePickerDialog(act,
                { _, y, m, d -> pickedLmp = "%04d-%02d-%02d".format(y, m + 1, d); lmpValue.text = pickedLmp },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        lmpRow.addView(lmpPickBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val calcRow = LinearLayout(act)
        calcRow.orientation = LinearLayout.HORIZONTAL
        calcRow.gravity = Gravity.CENTER_VERTICAL
        card1.addView(calcRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 12f), 0, 0)
        })
        val weeksEt = EditText(act)
        weeksEt.hint = "孕周"
        weeksEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        weeksEt.textSize = 14f
        weeksEt.gravity = Gravity.CENTER
        weeksEt.background = Ui.roundedBg(act, Theme.inputBg, 14f, Theme.line, 1.5f)
        calcRow.addView(weeksEt, LinearLayout.LayoutParams(0, Ui.dp2px(act, 46f), 1f))
        val plus = TextView(act)
        plus.text = "+"
        plus.textSize = 18f
        plus.gravity = Gravity.CENTER
        plus.setTextColor(Theme.textSub)
        calcRow.addView(plus, LinearLayout.LayoutParams(Ui.dp2px(act, 36f), ViewGroup.LayoutParams.WRAP_CONTENT))
        val daysEt = EditText(act)
        daysEt.hint = "天"
        daysEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        daysEt.textSize = 14f
        daysEt.gravity = Gravity.CENTER
        daysEt.background = Ui.roundedBg(act, Theme.inputBg, 14f, Theme.line, 1.5f)
        calcRow.addView(daysEt, LinearLayout.LayoutParams(0, Ui.dp2px(act, 46f), 1f))
        val calcBtn = TextView(act)
        calcBtn.text = "推算 LMP"
        calcBtn.textSize = 13f
        calcBtn.setTextColor(Theme.primary)
        calcBtn.gravity = Gravity.CENTER
        calcBtn.background = Ui.roundedBg(act, Color.WHITE, 20f, Color.parseColor("#E4E7EB"), 1.5f)
        Ui.bindPress(calcBtn)
        calcBtn.setOnClickListener {
            val w = weeksEt.text.toString().toIntOrNull() ?: -1
            val d = daysEt.text.toString().toIntOrNull() ?: 0
            if (w !in 0..42 || d !in 0..6) {
                Ui.toast(act, "请输入有效的孕周（0-42）和天数（0-6）"); return@setOnClickListener
            }
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -(w * 7 + d))
            pickedLmp = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            lmpValue.text = pickedLmp
        }
        calcRow.addView(calcBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp2px(act, 44f)).apply {
            setMargins(Ui.dp2px(act, 8f), 0, 0, 0)
        })

        val saveLmpBtn = primaryBtn("保存末次月经")
        saveLmpBtn.setOnClickListener {
            Thread {
                try {
                    Db.updateLmp(act, uid, pickedLmp)
                    act.runOnUiThread { Ui.toast(act, "末次月经已保存") }
                } catch (e: Exception) {
                    act.runOnUiThread { Ui.toast(act, "保存失败：${e.message}") }
                }
            }.start()
        }
        card1.addView(saveLmpBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })

        // ============ ② 添加新事件 ============
        val card2 = LinearLayout(act)
        card2.orientation = LinearLayout.VERTICAL
        card2.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(card2, 3f, 22f)
        card2.setPadding(Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f))
        content.addView(card2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })
        card2.addView(sectionTitle("➕ 添加新事件"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val evDateRow = LinearLayout(act)
        evDateRow.orientation = LinearLayout.HORIZONTAL
        evDateRow.gravity = Gravity.CENTER_VERTICAL
        card2.addView(evDateRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 6f), 0, 0)
        })
        val evDateLabel = TextView(act)
        evDateLabel.text = "日期："
        evDateLabel.textSize = 14f
        evDateLabel.setTextColor(Theme.text)
        evDateRow.addView(evDateLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        evDateValue = TextView(act)
        evDateValue.text = pickedEvDate
        evDateValue.textSize = 14f
        evDateValue.setTypeface(null, Typeface.BOLD)
        evDateValue.setTextColor(Theme.primary)
        evDateRow.addView(evDateValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val evDatePick = TextView(act)
        evDatePick.text = "选择"
        evDatePick.textSize = 13f
        evDatePick.setTextColor(Color.WHITE)
        evDatePick.gravity = Gravity.CENTER
        evDatePick.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 8f), Ui.dp2px(act, 14f), Ui.dp2px(act, 8f))
        evDatePick.background = Ui.gradientBg(act, Theme.primaryStart, Theme.primaryEnd, 20f)
        Ui.shadowRound(evDatePick, 3f, 20f)
        Ui.bindPress(evDatePick)
        evDatePick.setOnClickListener {
            val cal = Calendar.getInstance()
            pickedEvDate.split("-").let {
                if (it.size == 3) cal.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt())
            }
            DatePickerDialog(act,
                { _, y, m, d -> pickedEvDate = "%04d-%02d-%02d".format(y, m + 1, d); evDateValue.text = pickedEvDate },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        evDateRow.addView(evDatePick, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val spinner = Spinner(act)
        val periods = arrayOf("🌅 早", "☀️ 中", "🌙 晚")
        val periodCodes = arrayOf("morning", "afternoon", "evening")
        spinner.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_item, periods).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        card2.addView(spinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)).apply {
            setMargins(0, Ui.dp2px(act, 10f), 0, 0)
        })

        val evTitle = EditText(act)
        evTitle.hint = "事件标题"
        evTitle.textSize = 15f
        Ui.styleInput(evTitle, 50f)
        card2.addView(evTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 50f)).apply {
            setMargins(0, Ui.dp2px(act, 12f), 0, 0)
        })

        val evDesc = EditText(act)
        evDesc.hint = "描述（可选）"
        evDesc.textSize = 14f
        evDesc.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 10f), Ui.dp2px(act, 14f), Ui.dp2px(act, 10f))
        evDesc.background = Ui.roundedBg(act, Theme.inputBg, 16f, Theme.line, 1.5f)
        card2.addView(evDesc, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 12f), 0, 0)
        })

        val addEvBtn = primaryBtn("添加事件")
        addEvBtn.setOnClickListener {
            val t = evTitle.text.toString().trim()
            if (t.isEmpty()) { Ui.toast(act, "请填写事件标题"); return@setOnClickListener }
            Thread {
                try {
                    Db.addEvent(act, uid, pickedEvDate, t, evDesc.text.toString().trim(),
                        periodCodes[spinner.selectedItemPosition])
                    act.runOnUiThread {
                        Ui.toast(act, "已添加")
                        evTitle.text.clear()
                        evDesc.text.clear()
                        loadEvents(act, uid, allBox, allEvents, expandedGroups) { }
                    }
                } catch (e: Exception) {
                    act.runOnUiThread { Ui.toast(act, "添加失败：${e.message}") }
                }
            }.start()
        }
        card2.addView(addEvBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)).apply {
            setMargins(0, Ui.dp2px(act, 12f), 0, 0)
        })

        // ============ ③ 所有事件（合并堆叠） ============
        val card3 = LinearLayout(act)
        card3.orientation = LinearLayout.VERTICAL
        card3.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(card3, 3f, 22f)
        card3.setPadding(Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f))
        content.addView(card3, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })
        card3.addView(sectionTitle("📋 所有事件"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card3.addView(allBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ============ ④ 导入导出 ============
        val card4 = LinearLayout(act)
        card4.orientation = LinearLayout.VERTICAL
        card4.background = Ui.glassCard(act, 22f)
        Ui.shadowRound(card4, 3f, 22f)
        card4.setPadding(Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f), Ui.dp2px(act, 16f))
        content.addView(card4, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })
        card4.addView(sectionTitle("📦 导入导出"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val btnRow = LinearLayout(act)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER
        card4.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val exportBtn = secondaryBtn("📤 导出全部")
        exportBtn.setOnClickListener { doExport(act, uid) }
        btnRow.addView(exportBtn, LinearLayout.LayoutParams(0, Ui.dp2px(act, 46f), 1f))
        val importBtn = primaryBtn("📥 导入 TXT")
        importBtn.setOnClickListener {
            if (importLauncher == null) {
                Ui.toast(act, "文件选择器未就绪，请重试")
            } else {
                importLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
            }
        }
        btnRow.addView(importBtn, LinearLayout.LayoutParams(0, Ui.dp2px(act, 46f), 1f).apply {
            setMargins(Ui.dp2px(act, 10f), 0, 0, 0)
        })
        val ieNote = TextView(act)
        ieNote.text = "导出文件保存到「下载」目录；格式：日期,阶段(0早/1中/2晚),标题,描述,状态(0未完成/1已完成)"
        ieNote.textSize = 11f
        ieNote.setTextColor(Theme.textLight)
        ieNote.setLineSpacing(0f, 1.2f)
        ieNote.setPadding(0, Ui.dp2px(act, 10f), 0, 0)
        card4.addView(ieNote, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 初始数据加载 ----
        Thread {
            try {
                val lmp = Db.getPregnancy(act, uid).first
                val events = Db.getAllEvents(act, uid)
                act.runOnUiThread {
                    pickedLmp = lmp ?: todayStr(act)
                    lmpValue.text = lmp ?: "未设置"
                    allEvents = events
                    renderAllEvents(act, allBox, allEvents, expandedGroups, uid)
                }
            } catch (e: Exception) {
                act.runOnUiThread { Ui.toast(act, "加载失败：${e.message}") }
            }
        }.start()

        dlg.setContentView(root)
        val dm = act.resources.displayMetrics
        val wlp = dlg.window!!.attributes
        wlp.width = ViewGroup.LayoutParams.MATCH_PARENT
        wlp.height = ViewGroup.LayoutParams.MATCH_PARENT
        wlp.gravity = Gravity.BOTTOM
        wlp.dimAmount = 0.5f
        dlg.window!!.attributes = wlp
        dlg.window!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dlg.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // dismiss 后主动刷新主页面数据（Dialog 不触发 Activity.onResume）
        dlg.setOnDismissListener {
            (act as? MainActivity)?.refreshAllAfterDialog()
        }

        Ui.dialogShow(dlg)
    }

    // ==================== 所有事件渲染（合并堆叠） ====================

    private fun loadEvents(
        act: AppCompatActivity, uid: Int, allBox: LinearLayout,
        allEvents: List<Event>, expanded: HashMap<String, Boolean>,
        onLoaded: (List<Event>) -> Unit
    ) {
        Thread {
            try {
                val events = Db.getAllEvents(act, uid)
                act.runOnUiThread {
                    renderAllEvents(act, allBox, events, expanded, uid)
                    onLoaded(events)
                }
            } catch (e: Exception) {
                act.runOnUiThread { Ui.toast(act, "加载事件失败：${e.message}") }
            }
        }.start()
    }

    private fun renderAllEvents(
        act: AppCompatActivity, allBox: LinearLayout,
        allEvents: List<Event>, expanded: HashMap<String, Boolean>, uid: Int
    ) {
        allBox.removeAllViews()
        if (allEvents.isEmpty()) {
            val empty = TextView(act)
            empty.text = "暂无事件，可在上方添加"
            empty.textSize = 13f
            empty.setTextColor(Theme.textLight)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp2px(act, 14f), 0, Ui.dp2px(act, 14f))
            allBox.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        val groups = allEvents.groupBy { it.title + "\u0001" + it.period }
            .values
            .map { it.sortedByDescending { ev -> ev.eventDate } }
            .sortedWith(
                compareByDescending<List<Event>> { g -> g.any { it.status == 0 } }
                    .thenByDescending { g -> g.first().eventDate }
            )
        var shownPendingHeader = false
        var shownDoneHeader = false
        groups.forEach { g ->
            val hasPending = g.any { it.status == 0 }
            if (hasPending && !shownPendingHeader) {
                allBox.addView(groupHeader(act, "🔄 进行中 · 重复日程已合并"), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                shownPendingHeader = true
            }
            if (!hasPending && !shownDoneHeader) {
                allBox.addView(groupHeader(act, "✅ 已完成 · 重复日程已合并"), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                shownDoneHeader = true
            }
            buildGroupRow(act, g, expanded, uid, allBox)
        }
    }

    private fun groupHeader(act: AppCompatActivity, text: String): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = 13f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Theme.primary)
        t.setPadding(0, Ui.dp2px(act, 6f), 0, Ui.dp2px(act, 8f))
        return t
    }

    private fun periodIcon(p: String): String = when (p) {
        "morning" -> "🌅"
        "afternoon" -> "☀️"
        else -> "🌙"
    }

    private fun buildGroupRow(
        act: AppCompatActivity, group: List<Event>,
        expanded: HashMap<String, Boolean>, uid: Int, allBox: LinearLayout
    ) {
        val key = group[0].title + "\u0001" + group[0].period
        val isExpanded = expanded[key] == true
        val hasPending = group.any { it.status == 0 }
        val latestPending = group.firstOrNull { it.status == 0 } ?: group.first()
        val pendingCount = group.count { it.status == 0 }
        val doneCount = group.size - pendingCount
        val single = group.size == 1

        val wrap = LinearLayout(act)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.background = Ui.roundedBg(act, Theme.inputBg, 14f, Theme.line, 1f)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp2px(act, 8f))
        wrap.layoutParams = lp

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.dp2px(act, 12f), Ui.dp2px(act, 10f), Ui.dp2px(act, 8f), Ui.dp2px(act, 10f))
        Ui.bindPress(head)
        head.setOnClickListener {
            expanded[key] = !isExpanded
            renderAllEvents(act, allBox, group, expanded, uid)
            // 重新拉全部以刷新
            Thread {
                try {
                    val all = Db.getAllEvents(act, uid)
                    act.runOnUiThread { renderAllEvents(act, allBox, all, expanded, uid) }
                } catch (_: Exception) {}
            }.start()
        }
        wrap.addView(head, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val info = TextView(act)
        val infoText = if (single) {
            val ev = group[0]
            "${ev.eventDate} ${periodIcon(ev.period)} ${ev.title}${if (ev.status == 1) " ✅" else ""}"
        } else {
            val stateIcon = if (hasPending) "🔄" else "✅"
            val extra = if (pendingCount > 0 && doneCount > 0) "（未完成 $pendingCount · 已完成 $doneCount）" else ""
            "$stateIcon ${periodIcon(group[0].period)} ${group[0].title}\n" +
                "最近 ${latestPending.eventDate} · 共 ${group.size} 条$extra" +
                if (isExpanded) "\n▼ 点击收起" else "\n▶ 点击展开全部"
        }
        info.text = infoText
        info.textSize = 13f
        info.setTextColor(Theme.text)
        info.setLineSpacing(0f, 1.15f)
        head.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val del = TextView(act)
        del.text = "✕"
        del.textSize = 16f
        del.gravity = Gravity.CENTER
        del.setTextColor(Theme.primary)
        del.setPadding(Ui.dp2px(act, 8f), 0, Ui.dp2px(act, 4f), 0)
        Ui.bindPress(del)
        del.setOnClickListener {
            val dates = group.sortedBy { it.eventDate }
            val scope = if (single) "「${group[0].title}」" else
                "「${group[0].title}」全部 ${group.size} 条（${dates.first().eventDate} 至 ${dates.last().eventDate}）"
            AlertDialog.Builder(act)
                .setTitle("删除$scope？")
                .setPositiveButton("删除") { _, _ ->
                    Thread {
                        try {
                            group.forEach { Db.deleteEvent(act, uid, it.id) }
                            act.runOnUiThread {
                                Ui.toast(act, if (single) "已删除" else "已删除 ${group.size} 条")
                                Thread {
                                    val all = Db.getAllEvents(act, uid)
                                    act.runOnUiThread { renderAllEvents(act, allBox, all, expanded, uid) }
                                }.start()
                            }
                        } catch (e: Exception) {
                            act.runOnUiThread { Ui.toast(act, "删除失败：${e.message}") }
                        }
                    }.start()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        head.addView(del, LinearLayout.LayoutParams(Ui.dp2px(act, 34f), Ui.dp2px(act, 36f)))

        if (isExpanded) {
            group.sortedByDescending { it.eventDate }.forEach { ev ->
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 8f), Ui.dp2px(act, 8f), Ui.dp2px(act, 8f))
                val detail = TextView(act)
                detail.text = "${ev.eventDate} ${periodIcon(ev.period)} ${ev.title}${if (ev.status == 1) " ✅" else ""}"
                detail.textSize = 12f
                detail.setTextColor(Theme.textSub)
                row.addView(detail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val ddel = TextView(act)
                ddel.text = "✕"
                ddel.textSize = 14f
                ddel.gravity = Gravity.CENTER
                ddel.setTextColor(Theme.textSub)
                ddel.setPadding(Ui.dp2px(act, 8f), 0, Ui.dp2px(act, 4f), 0)
                Ui.bindPress(ddel)
                ddel.setOnClickListener {
                    AlertDialog.Builder(act)
                        .setTitle("删除「${ev.title}」？")
                        .setPositiveButton("删除") { _, _ ->
                            Thread {
                                try {
                                    Db.deleteEvent(act, uid, ev.id)
                                    act.runOnUiThread {
                                        Ui.toast(act, "已删除")
                                        Thread {
                                            val all = Db.getAllEvents(act, uid)
                                            act.runOnUiThread { renderAllEvents(act, allBox, all, expanded, uid) }
                                        }.start()
                                    }
                                } catch (e: Exception) {
                                    act.runOnUiThread { Ui.toast(act, "删除失败：${e.message}") }
                                }
                            }.start()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                row.addView(ddel, LinearLayout.LayoutParams(Ui.dp2px(act, 34f), Ui.dp2px(act, 32f)))
                wrap.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        allBox.addView(wrap)
    }

    /**
     * 导入 TXT（v1.4.13 恢复功能）：
     * 格式与导出一致：每行 `日期,阶段(0早/1中/2晚),标题,描述,状态(0/1)`
     * 描述/标题内的逗号需写成 `\,` 转义；`#` 开头为注释，空行跳过。
     * 同一日期已有事件会被该日期的新数据整体替换（与网页版一致）。
     */
    fun importTxt(act: AppCompatActivity, uid: Int, uri: android.net.Uri) {
        Thread {
            try {
                val rows = ArrayList<EventImport>()
                act.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader(Charsets.UTF_8).readText()
                    text.lines().forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val parts = line.split(Regex("""(?<!\\),"""))
                        if (parts.size < 3) return@forEach
                        val date = parts[0].trim()
                        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return@forEach
                        val periodCode = parts[1].trim()
                        val period = when (periodCode) {
                            "0" -> "morning"
                            "1" -> "afternoon"
                            "2" -> "evening"
                            else -> return@forEach
                        }
                        val title = parts[2].trim().replace("\\,", ",")
                        val desc = if (parts.size >= 4) parts[3].trim().replace("\\,", ",") else ""
                        val status = if (parts.size >= 5 && parts[4].trim() == "1") 1 else 0
                        rows.add(EventImport(date, period, title, desc, status))
                    }
                } ?: throw Exception("无法打开所选文件")
                if (rows.isEmpty()) {
                    act.runOnUiThread { Ui.toast(act, "未解析到有效日程数据") }
                    return@Thread
                }
                val n = Db.importEvents(act, uid, rows)
                act.runOnUiThread {
                    Ui.toast(act, "导入成功：$n 条（同日期旧日程已替换）")
                    (act as? MainActivity)?.refreshAllAfterDialog()
                }
            } catch (e: Exception) {
                act.runOnUiThread { Ui.toast(act, "导入失败：${e.message}") }
            }
        }.start()
    }

    private fun doExport(act: AppCompatActivity, uid: Int) {
        Thread {
            try {
                val events = Db.getAllEventsForExport(act, uid)
                val periodCode = mapOf("morning" to "0", "afternoon" to "1", "evening" to "2")
                val sb = StringBuilder()
                sb.appendLine("# 格式: 日期, 阶段(0=早,1=中,2=晚), 标题, 描述, 状态(0=未完成,1=已完成)")
                events.forEach {
                    val t = it.title.replace(",", "\\,")
                    val d = it.description.replace(",", "\\,")
                    sb.appendLine("${it.eventDate},${periodCode[it.period] ?: "0"},$t,$d,${it.status}")
                }
                val fileName = "calendar_export_${todayCompact(act)}.txt"
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                val uri = act.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("无法创建导出文件")
                act.contentResolver.openOutputStream(uri)?.use {
                    it.write(sb.toString().toByteArray(Charsets.UTF_8))
                } ?: throw Exception("无法写入导出文件")
                act.runOnUiThread {
                    Ui.toast(act, "已导出 ${events.size} 条日程 → 下载/$fileName")
                }
            } catch (e: Exception) {
                act.runOnUiThread { Ui.toast(act, "导出失败：${e.message}") }
            }
        }.start()
    }

    private fun todayStr(act: AppCompatActivity): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun todayCompact(act: AppCompatActivity): String {
        val c = Calendar.getInstance()
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
