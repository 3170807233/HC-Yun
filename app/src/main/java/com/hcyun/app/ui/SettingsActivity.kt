package com.hcyun.app.ui

import android.app.DatePickerDialog
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Event
import com.hcyun.app.net.Db
import com.hcyun.app.net.EventImport
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.Calendar

/**
 * 设置独立页面（v1.2.5）：
 * 顶栏返回 + 标题；白色玻璃卡分区——
 * ① 孕产设置（LMP 日期选择 / 孕周反推 / 保存）
 * ② 添加新事件（日期 / 阶段 / 标题 / 描述）
 * ③ 所有事件列表（按日期倒序，可删除）
 */
class SettingsActivity : AppCompatActivity() {

    private var uid = 0
    private lateinit var lmpValue: TextView
    private var pickedLmp = ""
    private lateinit var evDateValue: TextView
    private var pickedEvDate = ""
    private lateinit var allBox: LinearLayout
    private var allEvents: List<Event> = emptyList()
    private lateinit var notifyStatus: TextView
    private lateinit var exactStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        uid = Prefs.getUid(this)
        if (uid <= 0) {
            finish()
            return
        }
        pickedEvDate = todayStr()
        buildUi()
        loadData()
        updateRemindCard()
    }

    /** 从其他页面返回时刷新提醒设置状态 */
    override fun onResume() {
        super.onResume()
        if (::notifyStatus.isInitialized) updateRemindCard()
    }

    /** v1.2.18：刷新通知权限 / 精确闹钟状态显示 */
    private fun updateRemindCard() {
        if (::notifyStatus.isInitialized) {
            val notifOk = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            notifyStatus.text = if (notifOk) "已开启" else "未开启"
            notifyStatus.setTextColor(if (notifOk) Theme.success else Theme.primary)
        }
        if (::exactStatus.isInitialized) {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            val ok = am.canScheduleExactAlarms()
            exactStatus.text = if (ok) "已开启" else "未开启（可能延迟）"
            exactStatus.setTextColor(if (ok) Theme.success else Theme.primary)
        }
    }

    private fun buildUi() {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Theme.bg)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Theme.bg)
        // v1.3.1：底部留白移到滚动 ScrollView 上，内容可滚进底栏后方（透过效果）
        root.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 12f), Ui.dp2px(this, 16f), 0)
        frame.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---- 顶栏（仅标题，返回交给悬浮底栏） ----
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        root.addView(topBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(this)
        title.text = "⚙️ 设置"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // ---- 内容滚动（底部留白 104dp：悬浮底栏 82dp + 底部间距 18dp + 余量，
        // 内容可滚进底栏后方产生"透过"效果，与首页/图表一致） ----
        val scroll = ScrollView(this)
        scroll.isFillViewport = false
        scroll.setPadding(0, 0, 0, Ui.dp2px(this, 110f))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ============ ① 孕产设置（玻璃卡） ============
        val card1 = LinearLayout(this)
        card1.orientation = LinearLayout.VERTICAL
        card1.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(card1, 3f, 22f)
        card1.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f))
        content.addView(card1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sec1 = sectionTitle("🤰 孕产设置")
        card1.addView(sec1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lmpRow = LinearLayout(this)
        lmpRow.orientation = LinearLayout.HORIZONTAL
        lmpRow.gravity = Gravity.CENTER_VERTICAL
        card1.addView(lmpRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 6f), 0, 0)
        })

        val lmpLabel = TextView(this)
        lmpLabel.text = "末次月经 (LMP)："
        lmpLabel.textSize = 14f
        lmpLabel.setTextColor(Theme.text)
        lmpRow.addView(lmpLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        lmpValue = TextView(this)
        lmpValue.text = "加载中..."
        lmpValue.textSize = 14f
        lmpValue.setTypeface(null, Typeface.BOLD)
        lmpValue.setTextColor(Theme.primary)
        lmpRow.addView(lmpValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val lmpPickBtn = TextView(this)
        lmpPickBtn.text = "选择日期"
        lmpPickBtn.textSize = 13f
        lmpPickBtn.setTextColor(Color.WHITE)
        lmpPickBtn.gravity = Gravity.CENTER
        lmpPickBtn.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 8f), Ui.dp2px(this, 14f), Ui.dp2px(this, 8f))
        lmpPickBtn.background = Ui.gradientBg(this, Theme.primaryStart, Theme.primaryEnd, 20f)
        Ui.shadowRound(lmpPickBtn, 3f, 20f)
        Ui.bindPress(lmpPickBtn)
        lmpPickBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            pickedLmp.split("-").let {
                if (it.size == 3) cal.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt())
            }
            DatePickerDialog(
                this,
                { _, y, m, d -> pickedLmp = "%04d-%02d-%02d".format(y, m + 1, d); lmpValue.text = pickedLmp },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        lmpRow.addView(lmpPickBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 孕周反推
        val calcRow = LinearLayout(this)
        calcRow.orientation = LinearLayout.HORIZONTAL
        calcRow.gravity = Gravity.CENTER_VERTICAL
        card1.addView(calcRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 12f), 0, 0)
        })

        val weeksEt = EditText(this)
        weeksEt.hint = "孕周"
        weeksEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        weeksEt.textSize = 14f
        weeksEt.gravity = Gravity.CENTER
        weeksEt.background = Ui.roundedBg(this, Theme.inputBg, 14f, Theme.line, 1.5f)
        calcRow.addView(weeksEt, LinearLayout.LayoutParams(0, Ui.dp2px(this, 46f), 1f))

        val plus = TextView(this)
        plus.text = "+"
        plus.textSize = 18f
        plus.gravity = Gravity.CENTER
        plus.setTextColor(Theme.textSub)
        calcRow.addView(plus, LinearLayout.LayoutParams(Ui.dp2px(this, 36f), ViewGroup.LayoutParams.WRAP_CONTENT))

        val daysEt = EditText(this)
        daysEt.hint = "天"
        daysEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        daysEt.textSize = 14f
        daysEt.gravity = Gravity.CENTER
        daysEt.background = Ui.roundedBg(this, Theme.inputBg, 14f, Theme.line, 1.5f)
        calcRow.addView(daysEt, LinearLayout.LayoutParams(0, Ui.dp2px(this, 46f), 1f))

        val calcBtn = TextView(this)
        calcBtn.text = "推算 LMP"
        calcBtn.textSize = 13f
        calcBtn.setTextColor(Theme.primary)
        calcBtn.gravity = Gravity.CENTER
        calcBtn.background = Ui.roundedBg(this, Color.WHITE, 20f, Color.parseColor("#E4E7EB"), 1.5f)
        Ui.bindPress(calcBtn)
        calcBtn.setOnClickListener {
            val w = weeksEt.text.toString().toIntOrNull() ?: -1
            val d = daysEt.text.toString().toIntOrNull() ?: 0
            if (w !in 0..42 || d !in 0..6) {
                Ui.toast(this, "请输入有效的孕周（0-42）和天数（0-6）")
                return@setOnClickListener
            }
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -(w * 7 + d))
            pickedLmp = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            lmpValue.text = pickedLmp
        }
        calcRow.addView(calcBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp2px(this, 44f)).apply {
            setMargins(Ui.dp2px(this@SettingsActivity, 8f), 0, 0, 0)
        })

        val saveLmpBtn = primaryBtn("保存末次月经")
        saveLmpBtn.setOnClickListener {
            Thread {
                try {
                    Db.updateLmp(this, uid, pickedLmp)
                    runOnUiThread { Ui.toast(this, "末次月经已保存") }
                } catch (e: Exception) {
                    runOnUiThread { Ui.toast(this, "保存失败：${e.message}") }
                }
            }.start()
        }
        card1.addView(saveLmpBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })

        // ============ ② 添加新事件（玻璃卡） ============
        val card2 = LinearLayout(this)
        card2.orientation = LinearLayout.VERTICAL
        card2.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(card2, 3f, 22f)
        card2.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f))
        content.addView(card2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })

        card2.addView(sectionTitle("➕ 添加新事件"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val evDateRow = LinearLayout(this)
        evDateRow.orientation = LinearLayout.HORIZONTAL
        evDateRow.gravity = Gravity.CENTER_VERTICAL
        card2.addView(evDateRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 6f), 0, 0)
        })

        val evDateLabel = TextView(this)
        evDateLabel.text = "日期："
        evDateLabel.textSize = 14f
        evDateLabel.setTextColor(Theme.text)
        evDateRow.addView(evDateLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        evDateValue = TextView(this)
        evDateValue.text = pickedEvDate
        evDateValue.textSize = 14f
        evDateValue.setTypeface(null, Typeface.BOLD)
        evDateValue.setTextColor(Theme.primary)
        evDateRow.addView(evDateValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val evDatePick = TextView(this)
        evDatePick.text = "选择"
        evDatePick.textSize = 13f
        evDatePick.setTextColor(Color.WHITE)
        evDatePick.gravity = Gravity.CENTER
        evDatePick.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 8f), Ui.dp2px(this, 14f), Ui.dp2px(this, 8f))
        evDatePick.background = Ui.gradientBg(this, Theme.primaryStart, Theme.primaryEnd, 20f)
        Ui.shadowRound(evDatePick, 3f, 20f)
        Ui.bindPress(evDatePick)
        evDatePick.setOnClickListener {
            val cal = Calendar.getInstance()
            pickedEvDate.split("-").let {
                if (it.size == 3) cal.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt())
            }
            DatePickerDialog(
                this,
                { _, y, m, d -> pickedEvDate = "%04d-%02d-%02d".format(y, m + 1, d); evDateValue.text = pickedEvDate },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        evDateRow.addView(evDatePick, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val spinner = Spinner(this)
        val periods = arrayOf("🌅 早", "☀️ 中", "🌙 晚")
        val periodCodes = arrayOf("morning", "afternoon", "evening")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        card2.addView(spinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 10f), 0, 0)
        })

        val evTitle = EditText(this)
        evTitle.hint = "事件标题"
        evTitle.textSize = 15f
        Ui.styleInput(evTitle, 50f)
        card2.addView(evTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 50f)).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 12f), 0, 0)
        })

        val evDesc = EditText(this)
        evDesc.hint = "描述（可选）"
        evDesc.textSize = 14f
        evDesc.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 10f), Ui.dp2px(this, 14f), Ui.dp2px(this, 10f))
        evDesc.background = Ui.roundedBg(this, Theme.inputBg, 16f, Theme.line, 1.5f)
        card2.addView(evDesc, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 12f), 0, 0)
        })

        val addEvBtn = primaryBtn("添加事件")
        addEvBtn.setOnClickListener {
            val t = evTitle.text.toString().trim()
            if (t.isEmpty()) {
                Ui.toast(this, "请填写事件标题")
                return@setOnClickListener
            }
            Thread {
                try {
                    Db.addEvent(this, uid, pickedEvDate, t, evDesc.text.toString().trim(), periodCodes[spinner.selectedItemPosition])
                    runOnUiThread {
                        Ui.toast(this, "已添加")
                        evTitle.text.clear()
                        evDesc.text.clear()
                        loadEvents()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Ui.toast(this, "添加失败：${e.message}") }
                }
            }.start()
        }
        card2.addView(addEvBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 12f), 0, 0)
        })

        // ============ ⑤ 提醒设置（玻璃卡，v1.2.18） ============
        // 通知权限 / 精确闹钟（Android 12+）/ 国产 ROM 后台保护引导：
        // 解决部分机型「通知不显示、应用未启动收不到提醒」
        val card5 = LinearLayout(this)
        card5.orientation = LinearLayout.VERTICAL
        card5.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(card5, 3f, 22f)
        card5.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f))
        content.addView(card5, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })

        card5.addView(sectionTitle("🔔 提醒设置"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 通知权限行
        val notifRow = LinearLayout(this)
        notifRow.orientation = LinearLayout.HORIZONTAL
        notifRow.gravity = Gravity.CENTER_VERTICAL
        card5.addView(notifRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 10f), 0, 0)
        })
        val notifLabel = TextView(this)
        notifLabel.text = "通知权限"
        notifLabel.textSize = 14f
        notifLabel.setTextColor(Theme.text)
        notifRow.addView(notifLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        notifyStatus = TextView(this)
        notifyStatus.textSize = 13f
        notifyStatus.setTextColor(Theme.primary)
        notifRow.addView(notifyStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.END
        })
        val notifBtn = secondaryBtn("去开启")
        notifBtn.setOnClickListener {
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                )
            } catch (e: Exception) {
                Ui.toast(this, "请在系统设置 → 应用 → HC孕 → 通知 中开启")
            }
        }
        notifRow.addView(notifBtn, LinearLayout.LayoutParams(
            Ui.dp2px(this, 76f), Ui.dp2px(this, 36f)))

        // 精确闹钟行（Android 12+ 才有；保证后台/灭屏时提醒准时）
        if (Build.VERSION.SDK_INT >= 31) {
            val exactRow = LinearLayout(this)
            exactRow.orientation = LinearLayout.HORIZONTAL
            exactRow.gravity = Gravity.CENTER_VERTICAL
            card5.addView(exactRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp2px(this@SettingsActivity, 10f), 0, 0)
            })
            val exactLabel = TextView(this)
            exactLabel.text = "后台精确提醒"
            exactLabel.textSize = 14f
            exactLabel.setTextColor(Theme.text)
            exactRow.addView(exactLabel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            exactStatus = TextView(this)
            exactStatus.textSize = 13f
            exactStatus.setTextColor(Theme.primary)
            exactRow.addView(exactStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            })
            val exactBtn = secondaryBtn("去开启")
            exactBtn.setOnClickListener {
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    Ui.toast(this, "请在系统设置 → 应用 → HC孕 → 闹钟与提醒 中开启")
                }
            }
            exactRow.addView(exactBtn, LinearLayout.LayoutParams(
                Ui.dp2px(this, 76f), Ui.dp2px(this, 36f)))
        }

        val remindNote = TextView(this)
        remindNote.text = "· 首次进入 App 已申请通知权限；如之前拒绝过，请在上方重新开启\n" +
            "· 部分手机（小米/OPPO/一加等）默认限制后台运行，请到系统设置 → 应用 → HC孕 允许「自启动 / 后台运行 / 电池不限制」，否则应用被杀后可能收不到提醒\n" +
            "· 手机重启后无需打开 App，日程提醒会自动恢复（开机自启重排）"
        remindNote.textSize = 11f
        remindNote.setTextColor(Theme.textLight)
        remindNote.setLineSpacing(0f, 1.25f)
        remindNote.setPadding(0, Ui.dp2px(this, 10f), 0, 0)
        card5.addView(remindNote, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ============ ④ 导入导出（玻璃卡，移植网页版 TXT 格式） ============
        val card4 = LinearLayout(this)
        card4.orientation = LinearLayout.VERTICAL
        card4.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(card4, 3f, 22f)
        card4.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f))
        content.addView(card4, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })

        card4.addView(sectionTitle("📦 导入导出"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER_VERTICAL
        card4.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val exportBtn = secondaryBtn("📤 导出全部")
        exportBtn.setOnClickListener { doExport() }
        btnRow.addView(exportBtn, LinearLayout.LayoutParams(0, Ui.dp2px(this, 46f), 1f))

        val importBtn = primaryBtn("📥 导入 TXT")
        importBtn.setOnClickListener { openImportPicker() }
        btnRow.addView(importBtn, LinearLayout.LayoutParams(0, Ui.dp2px(this, 46f), 1f).apply {
            setMargins(Ui.dp2px(this@SettingsActivity, 10f), 0, 0, 0)
        })

        val ieNote = TextView(this)
        ieNote.text = "导出文件保存到「下载」目录；导入将按日期覆盖当天全部事件（与网页版一致）。\n格式：日期,阶段(0早/1中/2晚),标题,描述,状态(0未完成/1已完成)"
        ieNote.textSize = 11f
        ieNote.setTextColor(Theme.textLight)
        ieNote.setLineSpacing(0f, 1.2f)
        ieNote.setPadding(0, Ui.dp2px(this, 10f), 0, 0)
        card4.addView(ieNote, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ============ ③ 所有事件（玻璃卡） ============
        val card3 = LinearLayout(this)
        card3.orientation = LinearLayout.VERTICAL
        card3.background = Ui.glassCard(this, 22f)
        Ui.shadowRound(card3, 3f, 22f)
        card3.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f), Ui.dp2px(this, 16f))
        content.addView(card3, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@SettingsActivity, 14f), 0, 0)
        })

        card3.addView(sectionTitle("📋 所有事件"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        allBox = LinearLayout(this)
        allBox.orientation = LinearLayout.VERTICAL
        card3.addView(allBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 悬浮底栏（首页/备忘录/图表/设置，无图标） ----
        Ui.attachFloatingBar(this, frame, "settings")

        setContentView(frame)

        Ui.animateIn(topBar, 14f, 260)
        Ui.animateIn(card1, 18f, 300, 60L)
        Ui.animateIn(card2, 20f, 320, 120L)
        Ui.animateIn(card4, 22f, 320, 180L)
        Ui.animateIn(card3, 24f, 320, 240L)
    }

    // ==================== 数据 ====================

    private fun loadData() {
        Thread {
            try {
                val lmp = Db.getPregnancy(this, uid).first
                val events = Db.getAllEvents(this, uid)
                runOnUiThread {
                    pickedLmp = lmp ?: todayStr()
                    lmpValue.text = lmp ?: "未设置"
                    allEvents = events
                    renderAllEvents()
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "加载失败：${e.message}") }
            }
        }.start()
    }

    private fun loadEvents() {
        Thread {
            try {
                val events = Db.getAllEvents(this, uid)
                runOnUiThread {
                    allEvents = events
                    renderAllEvents()
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "加载事件失败：${e.message}") }
            }
        }.start()
    }

    // v1.2.14：重复日程合并堆叠（同标题 + 同时段视为同一重复日程），
    // 每组只显示最近的一个日程；进行中 / 已完成分区；点组行展开明细
    private val expandedGroups = HashMap<String, Boolean>()

    private fun renderAllEvents() {
        allBox.removeAllViews()
        if (allEvents.isEmpty()) {
            val empty = TextView(this)
            empty.text = "暂无事件，可在上方添加"
            empty.textSize = 13f
            empty.setTextColor(Theme.textLight)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f))
            allBox.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        // 按「标题 + 时段」归并重复日程，组内日期倒序；有未完成的组排前
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
                allBox.addView(groupHeader("🔄 进行中 · 重复日程已合并"), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                shownPendingHeader = true
            }
            if (!hasPending && !shownDoneHeader) {
                allBox.addView(groupHeader("✅ 已完成 · 重复日程已合并"), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                shownDoneHeader = true
            }
            buildGroupRow(g)
        }
    }

    private fun groupHeader(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Theme.primary)
        t.setPadding(0, Ui.dp2px(this, 6f), 0, Ui.dp2px(this, 8f))
        return t
    }

    private fun buildGroupRow(group: List<Event>) {
        val key = group[0].title + "\u0001" + group[0].period
        val expanded = expandedGroups[key] == true
        val hasPending = group.any { it.status == 0 }
        val latestPending = group.firstOrNull { it.status == 0 } ?: group.first()
        val pendingCount = group.count { it.status == 0 }
        val doneCount = group.size - pendingCount
        val single = group.size == 1

        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.background = Ui.roundedBg(this, Theme.inputBg, 14f, Theme.line, 1f)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp2px(this, 8f))
        wrap.layoutParams = lp

        // ---- 头部行：最近一个日程 + 数量（点击展开/收起，✕ 删除整组） ----
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.dp2px(this, 12f), Ui.dp2px(this, 10f), Ui.dp2px(this, 8f), Ui.dp2px(this, 10f))
        Ui.bindPress(head)
        head.setOnClickListener {
            expandedGroups[key] = !expanded
            loadEvents()
        }
        wrap.addView(head, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val info = TextView(this)
        val infoText = if (single) {
            val ev = group[0]
            "${ev.eventDate} ${periodIcon(ev.period)} ${ev.title}${if (ev.status == 1) " ✅" else ""}"
        } else {
            val stateIcon = if (hasPending) "🔄" else "✅"
            val extra = if (pendingCount > 0 && doneCount > 0) "（未完成 $pendingCount · 已完成 $doneCount）" else ""
            "$stateIcon ${periodIcon(group[0].period)} ${group[0].title}\n" +
                "最近 ${latestPending.eventDate} · 共 ${group.size} 条$extra" +
                if (expanded) "\n▼ 点击收起" else "\n▶ 点击展开全部"
        }
        info.text = infoText
        info.textSize = 13f
        info.setTextColor(Theme.text)
        info.setLineSpacing(0f, 1.15f)
        head.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val del = TextView(this)
        del.text = "✕"
        del.textSize = 16f
        del.gravity = Gravity.CENTER
        del.setTextColor(Theme.primary)
        del.setPadding(Ui.dp2px(this, 8f), 0, Ui.dp2px(this, 4f), 0)
        Ui.bindPress(del)
        del.setOnClickListener {
            val dates = group.sortedBy { it.eventDate }
            val scope = if (single) "「${group[0].title}」" else
                "「${group[0].title}」全部 ${group.size} 条（${dates.first().eventDate} 至 ${dates.last().eventDate}）"
            AlertDialog.Builder(this)
                .setTitle("删除$scope？")
                .setPositiveButton("删除") { _, _ ->
                    Thread {
                        try {
                            group.forEach { Db.deleteEvent(this, uid, it.id) }
                            runOnUiThread {
                                Ui.toast(this, if (single) "已删除" else "已删除 ${group.size} 条")
                                loadEvents()
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Ui.toast(this, "删除失败：${e.message}") }
                        }
                    }.start()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        head.addView(del, LinearLayout.LayoutParams(Ui.dp2px(this, 34f), Ui.dp2px(this, 36f)))

        // ---- 明细行（展开时）：每条日期 + 状态 + 单独删除 ----
        if (expanded) {
            group.sortedByDescending { it.eventDate }.forEach { ev ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 8f), Ui.dp2px(this, 8f), Ui.dp2px(this, 8f))

                val detail = TextView(this)
                detail.text = "${ev.eventDate} ${periodIcon(ev.period)} ${ev.title}${if (ev.status == 1) " ✅" else ""}"
                detail.textSize = 12f
                detail.setTextColor(Theme.textSub)
                row.addView(detail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val ddel = TextView(this)
                ddel.text = "✕"
                ddel.textSize = 14f
                ddel.gravity = Gravity.CENTER
                ddel.setTextColor(Theme.textSub)
                ddel.setPadding(Ui.dp2px(this, 8f), 0, Ui.dp2px(this, 4f), 0)
                Ui.bindPress(ddel)
                ddel.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("删除「${ev.title}」？")
                        .setPositiveButton("删除") { _, _ ->
                            Thread {
                                try {
                                    Db.deleteEvent(this, uid, ev.id)
                                    runOnUiThread {
                                        Ui.toast(this, "已删除")
                                        loadEvents()
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread { Ui.toast(this, "删除失败：${e.message}") }
                                }
                            }.start()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                row.addView(ddel, LinearLayout.LayoutParams(Ui.dp2px(this, 34f), Ui.dp2px(this, 32f)))
                wrap.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        allBox.addView(wrap)
    }

    // ==================== 工具 ====================

    private fun sectionTitle(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Theme.text)
        t.setPadding(0, 0, 0, Ui.dp2px(this, 10f))
        return t
    }

    private fun primaryBtn(text: String): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 14f
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.background = Ui.gradientBg(this, Theme.primaryStart, Theme.primaryEnd, 20f)
        Ui.shadowRound(b, 3f, 20f)
        Ui.bindPress(b)
        return b
    }

    private fun secondaryBtn(text: String): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 14f
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setTextColor(Theme.primary)
        b.background = Ui.roundedBg(this, Color.WHITE, 20f, Color.parseColor("#E4E7EB"), 1.5f)
        Ui.bindPress(b)
        return b
    }

    // ==================== 导入导出（移植网页版 TXT 格式） ====================

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readAndConfirmImport(uri)
    }

    private fun openImportPicker() {
        importPicker.launch(arrayOf("text/plain", "text/*"))
    }

    private fun readAndConfirmImport(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        } catch (e: Exception) {
            Ui.toast(this, "读取文件失败：${e.message}")
            return
        }
        val (rows, errors) = parseImportText(text)
        if (rows.isEmpty()) {
            val msg = if (errors.isEmpty()) "文件中没有可导入的事件" else "没有可导入的事件：\n${errors.take(5).joinToString("\n")}"
            AlertDialog.Builder(this)
                .setTitle("导入失败")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()
            return
        }
        val summary = "共解析出 ${rows.size} 条事件${if (errors.isEmpty()) "" else "，忽略 ${errors.size} 条错误行"}\n" +
            "将按日期覆盖这些日期下的全部原有事件，是否继续？"
        AlertDialog.Builder(this)
            .setTitle("确认导入")
            .setMessage(summary)
            .setPositiveButton("导入") { _, _ -> doImport(rows, errors) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseImportText(text: String): Pair<List<EventImport>, List<String>> {
        val rows = ArrayList<EventImport>()
        val errors = ArrayList<String>()
        val periodMap = mapOf("0" to "morning", "1" to "afternoon", "2" to "evening")
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(",").map { it.trim() }
            if (parts.size != 5) {
                errors.add("无效行: $line")
                return@forEach
            }
            val date = parts[0]
            val code = parts[1]
            val title = parts[2]
            val desc = parts[3]
            val status = parts[4]
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
                errors.add("无效日期: $date")
                return@forEach
            }
            val period = periodMap[code]
            if (period == null) {
                errors.add("无效阶段代码: $code")
                return@forEach
            }
            val st = status.toIntOrNull()
            if (st == null || st !in 0..1) {
                errors.add("无效状态: $status")
                return@forEach
            }
            rows.add(EventImport(
                date = date,
                period = period,
                title = title.replace("\\,", ","),
                desc = desc.replace("\\,", ","),
                status = st
            ))
        }
        return rows to errors
    }

    private fun doImport(rows: List<EventImport>, parseErrors: List<String>) {
        Thread {
            try {
                val n = Db.importEvents(this, uid, rows)
                runOnUiThread {
                    val msg = buildString {
                        append("导入完成：成功 $n 条")
                        if (parseErrors.isNotEmpty()) {
                            append("\n\n忽略错误行 ${parseErrors.size} 条：")
                            append("\n${parseErrors.take(5).joinToString("\n")}")
                        }
                    }
                    AlertDialog.Builder(this)
                        .setTitle("导入结果")
                        .setMessage(msg)
                        .setPositiveButton("确定", null)
                        .show()
                    loadEvents()
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "导入失败：${e.message}") }
            }
        }.start()
    }

    private fun doExport() {
        Thread {
            try {
                val events = Db.getAllEventsForExport(this, uid)
                val periodCode = mapOf("morning" to "0", "afternoon" to "1", "evening" to "2")
                val sb = StringBuilder()
                sb.appendLine("# 格式: 日期, 阶段(0=早,1=中,2=晚), 标题, 描述, 状态(0=未完成,1=已完成)")
                sb.appendLine("# 示例: 2026-08-24,0,开会,项目讨论,0")
                events.forEach {
                    val t = it.title.replace(",", "\\,")
                    val d = it.description.replace(",", "\\,")
                    sb.appendLine("${it.eventDate},${periodCode[it.period] ?: "0"},$t,$d,${it.status}")
                }
                val fileName = "calendar_export_${todayCompact()}.txt"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("无法创建导出文件")
                contentResolver.openOutputStream(uri)?.use {
                    it.write(sb.toString().toByteArray(Charsets.UTF_8))
                } ?: throw Exception("无法写入导出文件")
                runOnUiThread {
                    Ui.toast(this, "已导出 ${events.size} 条日程 → 下载/$fileName")
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "导出失败：${e.message}") }
            }
        }.start()
    }

    private fun todayCompact(): String {
        val c = Calendar.getInstance()
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun todayStr(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }


    private fun periodIcon(p: String): String = when (p) {
        "morning" -> "🌅"
        "afternoon" -> "☀️"
        else -> "🌙"
    }
}
