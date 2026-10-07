package com.hcyun.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Note
import com.hcyun.app.net.Db
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.LinkedHashMap

/**
 * 备忘录独立页面（v1.2.5，v1.2.8 起添加按钮改为搜索栏同款样式并置于其下）：
 * 顶栏标题、玻璃搜索框实时过滤、搜索框下「＋ 添加备忘录」按钮、
 * 按日期分组的玻璃卡片列表（点击行编辑、✕ 删除）。
 */
class MemoActivity : AppCompatActivity() {

    private var uid = 0
    private lateinit var searchEt: EditText
    private lateinit var listBox: LinearLayout
    private var allNotes: List<Note> = emptyList()

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
        loadNotes()
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Theme.bg)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        // v1.3.1：底部留白改到列表 ScrollView 上（下方），否则外层容器 padding
        // 只压缩列表高度、内容永远滚不到底栏后方，看起来像被底栏"挡住"
        content.setPadding(Ui.dp2px(this, 16f), Ui.dp2px(this, 12f), Ui.dp2px(this, 16f), 0)
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---- 顶栏（仅标题，返回交给悬浮底栏） ----
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        content.addView(topBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(this)
        title.text = "📝 备忘录"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        title.gravity = Gravity.CENTER_VERTICAL
        topBar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // ---- 搜索框（玻璃圆角） ----
        searchEt = EditText(this)
        searchEt.hint = "🔍 搜索备忘录..."
        searchEt.textSize = 14f
        searchEt.setTextColor(Theme.text)
        searchEt.setHintTextColor(Theme.textLight)
        searchEt.setPadding(Ui.dp2px(this, 16f), 0, Ui.dp2px(this, 16f), 0)
        searchEt.background = Ui.glassCard(this, 22f)
        content.addView(searchEt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)).apply {
            setMargins(0, Ui.dp2px(this@MemoActivity, 14f), 0, 0)
        })
        searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                renderList(s?.toString() ?: "")
            }
        })

        // ---- 添加按钮（与搜索栏同款玻璃圆角条，置于搜索栏下方，不再悬浮避免被底栏遮挡） ----
        val addBtn = TextView(this)
        addBtn.text = "＋ 添加备忘录"
        addBtn.textSize = 14f
        addBtn.gravity = Gravity.CENTER
        addBtn.setTextColor(Theme.primary)
        addBtn.setTypeface(null, Typeface.BOLD)
        addBtn.background = Ui.glassCard(this, 22f)
        Ui.bindPress(addBtn)
        addBtn.setOnClickListener { showAddDialog() }
        content.addView(addBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 46f)).apply {
            setMargins(0, Ui.dp2px(this@MemoActivity, 10f), 0, 0)
        })

        // ---- 列表（底部留白 104dp：悬浮底栏占位 82dp + 底部间距 18dp + 余量，
        // 让内容可滚进底栏后方产生"透过"效果，与其他页面一致） ----
        val listScroll = ScrollView(this)
        listScroll.isFillViewport = false
        listScroll.setPadding(0, 0, 0, Ui.dp2px(this, 110f))
        content.addView(listScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, Ui.dp2px(this@MemoActivity, 10f), 0, 0)
        })
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listScroll.addView(listBox, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 悬浮底栏（首页/备忘录/图表/设置，无图标） ----
        Ui.attachFloatingBar(this, root, "memo")

        setContentView(root)

        Ui.animateIn(topBar, 14f, 260)
        Ui.animateIn(searchEt, 16f, 300, 60L)
        Ui.animateIn(addBtn, 18f, 300, 120L)
        Ui.animateIn(listScroll, 20f, 320, 180L)
    }

    private fun loadNotes() {
        Thread {
            try {
                val notes = Db.getNotes(this, uid)
                runOnUiThread {
                    allNotes = notes
                    renderList(searchEt.text.toString())
                }
            } catch (e: Exception) {
                runOnUiThread { Ui.toast(this, "加载备忘录失败：${e.message}") }
            }
        }.start()
    }

    private fun renderList(filter: String) {
        listBox.removeAllViews()
        val kw = filter.trim().lowercase()
        val shown = if (kw.isEmpty()) allNotes else allNotes.filter { it.content.lowercase().contains(kw) }
        if (shown.isEmpty()) {
            val empty = TextView(this)
            empty.text = if (kw.isEmpty()) "暂无备忘录，点下方 ＋ 添加" else "未找到匹配的备忘录"
            empty.textSize = 13f
            empty.setTextColor(Theme.textLight)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp2px(this, 28f), 0, Ui.dp2px(this, 28f))
            listBox.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        // 按日期分组
        val groups = LinkedHashMap<String, MutableList<Note>>()
        shown.sortedByDescending { it.createdAt }.forEach {
            val date = it.createdAt.substringBefore(" ")
            groups.getOrPut(date) { mutableListOf() }.add(it)
        }
        groups.forEach { (date, notes) ->
            val dateLabel = TextView(this)
            dateLabel.text = date
            dateLabel.textSize = 12f
            dateLabel.setTextColor(Theme.primary)
            dateLabel.setTypeface(null, Typeface.BOLD)
            listBox.addView(dateLabel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp2px(this@MemoActivity, 12f), 0, Ui.dp2px(this@MemoActivity, 6f))
            })
            notes.forEach { note ->
                listBox.addView(buildNoteRow(note))
            }
        }
    }

    private fun buildNoteRow(note: Note): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.glassCard(this, 16f)
        row.setPadding(Ui.dp2px(this, 14f), Ui.dp2px(this, 12f), Ui.dp2px(this, 8f), Ui.dp2px(this, 12f))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp2px(this, 10f))
        row.layoutParams = lp
        Ui.bindPress(row)

        val content = TextView(this)
        content.text = note.content
        content.textSize = 14f
        content.setTextColor(Theme.text)
        content.setLineSpacing(0f, 1.15f)
        row.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val time = TextView(this)
        time.text = note.createdAt.substringAfter(" ")
        time.textSize = 11f
        time.setTextColor(Theme.textLight)
        row.addView(time, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, Ui.dp2px(this@MemoActivity, 6f), 0)
        })

        val edit = TextView(this)
        edit.text = "✏️"
        edit.textSize = 16f
        edit.gravity = Gravity.CENTER
        edit.setPadding(Ui.dp2px(this, 10f), 0, Ui.dp2px(this, 10f), 0)
        Ui.bindPress(edit)
        edit.setOnClickListener { showEditDialog(note) }
        row.addView(edit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val del = TextView(this)
        del.text = "✕"
        del.textSize = 16f
        del.gravity = Gravity.CENTER
        del.setTextColor(Theme.primary)
        del.setPadding(Ui.dp2px(this, 10f), 0, Ui.dp2px(this, 6f), 0)
        Ui.bindPress(del)
        del.setOnClickListener { confirmDelete(note) }
        row.addView(del, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    // ==================== 添加 / 编辑 / 删除 ====================

    private fun showAddDialog() {
        val et = EditText(this)
        et.hint = "写一条新备忘录..."
        et.textSize = 14f
        et.setTextColor(Theme.text)
        et.setHintTextColor(Theme.textLight)
        et.setPadding(Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f), 0)
        et.background = Ui.roundedBg(this, Theme.inputBg, 16f, Theme.line, 1.5f)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp2px(this, 20f), Ui.dp2px(this, 6f), Ui.dp2px(this, 20f), 0)
        box.addView(et, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)))
        val dlg = AlertDialog.Builder(this)
            .setTitle("📝 添加备忘录")
            .setView(box)
            .setPositiveButton("添加") { _, _ ->
                val c = et.text.toString().trim()
                if (c.isEmpty()) {
                    Ui.toast(this, "请输入内容")
                    return@setPositiveButton
                }
                Thread {
                    try {
                        Db.addNote(this, uid, c)
                        runOnUiThread {
                            Ui.toast(this, "已添加")
                            loadNotes()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Ui.toast(this, "添加失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(this, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }

    private fun showEditDialog(note: Note) {
        val et = EditText(this)
        et.setText(note.content)
        et.textSize = 14f
        et.setTextColor(Theme.text)
        et.setPadding(Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f), 0)
        et.background = Ui.roundedBg(this, Theme.inputBg, 16f, Theme.line, 1.5f)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp2px(this, 20f), Ui.dp2px(this, 6f), Ui.dp2px(this, 20f), 0)
        box.addView(et, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)))
        val dlg = AlertDialog.Builder(this)
            .setTitle("✏️ 编辑备忘录")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val newText = et.text.toString().trim()
                if (newText.isEmpty()) {
                    Ui.toast(this, "内容不能为空")
                    return@setPositiveButton
                }
                Thread {
                    try {
                        Db.editNote(this, uid, note.id, newText)
                        runOnUiThread {
                            Ui.toast(this, "已保存")
                            loadNotes()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Ui.toast(this, "编辑失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(this, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }

    private fun confirmDelete(note: Note) {
        val dlg = AlertDialog.Builder(this)
            .setTitle("删除此备忘录？")
            .setMessage("「${note.content.take(20)}${if (note.content.length > 20) "…" else ""}」将被删除")
            .setPositiveButton("删除") { _, _ ->
                Thread {
                    try {
                        Db.deleteNote(this, uid, note.id)
                        runOnUiThread {
                            Ui.toast(this, "已删除")
                            loadNotes()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Ui.toast(this, "删除失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(this, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }
}
