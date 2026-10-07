package com.hcyun.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.model.Note
import com.hcyun.app.net.Db
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui
import java.util.LinkedHashMap

/**
 * 备忘录分区（v1.4.0：从 MemoActivity 抽出，嵌入 MainActivity 单页滚动）。
 * 顶栏标题、玻璃搜索框实时过滤、搜索框下「＋ 添加备忘录」按钮、
 * 按日期分组的玻璃卡片列表（点击行编辑、✕ 删除）。
 */
class MemoSection(private val act: AppCompatActivity, private val uid: Int) {

    private lateinit var searchEt: EditText
    private lateinit var listBox: LinearLayout
    private var allNotes: List<Note> = emptyList()

    fun build(container: LinearLayout) {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL

        // ---- 分区标题 ----
        val title = TextView(act)
        title.text = "📝 备忘录"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 搜索框（玻璃圆角） ----
        searchEt = EditText(act)
        searchEt.hint = "🔍 搜索备忘录..."
        searchEt.textSize = 14f
        searchEt.setTextColor(Theme.text)
        searchEt.setHintTextColor(Theme.textLight)
        searchEt.setPadding(Ui.dp2px(act, 16f), 0, Ui.dp2px(act, 16f), 0)
        searchEt.background = Ui.glassCard(act, 22f)
        root.addView(searchEt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)).apply {
            setMargins(0, Ui.dp2px(act, 14f), 0, 0)
        })
        searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                renderList(s?.toString() ?: "")
            }
        })

        // ---- 添加按钮（与搜索栏同款玻璃圆角条，置于搜索栏下方） ----
        val addBtn = TextView(act)
        addBtn.text = "＋ 添加备忘录"
        addBtn.textSize = 14f
        addBtn.gravity = Gravity.CENTER
        addBtn.setTextColor(Theme.primary)
        addBtn.setTypeface(null, Typeface.BOLD)
        addBtn.background = Ui.glassCard(act, 22f)
        Ui.bindPress(addBtn)
        addBtn.setOnClickListener { showAddDialog() }
        root.addView(addBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 46f)).apply {
            setMargins(0, Ui.dp2px(act, 10f), 0, 0)
        })

        // ---- 列表 ----
        listBox = LinearLayout(act)
        listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(act, 10f), 0, 0)
        })

        container.addView(root)
    }

    fun loadNotes() {
        val notes = com.hcyun.app.net.LocalCache.loadNotes(act, uid)
        applyNotes(notes)
    }

    /** 写操作后从服务器拉最新并渲染 */
    fun refreshFromServer() {
        Thread {
            try {
                val notes = Db.getNotes(act, uid)
                com.hcyun.app.net.LocalCache.saveNotes(act, uid, notes)
                applyNotes(notes)
            } catch (_: Exception) {
            }
        }.start()
    }

    /** 直接使用最新数据渲染（MainActivity 同步完成后调用） */
    fun applyNotes(notes: List<Note>) {
        act.runOnUiThread {
            allNotes = notes
            renderList(searchEt.text.toString())
        }
    }

    private fun renderList(filter: String) {
        listBox.removeAllViews()
        val kw = filter.trim().lowercase()
        val shown = if (kw.isEmpty()) allNotes else allNotes.filter { it.content.lowercase().contains(kw) }
        if (shown.isEmpty()) {
            val empty = TextView(act)
            empty.text = if (kw.isEmpty()) "暂无备忘录，点下方 ＋ 添加" else "未找到匹配的备忘录"
            empty.textSize = 13f
            empty.setTextColor(Theme.textLight)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp2px(act, 28f), 0, Ui.dp2px(act, 28f))
            listBox.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        val groups = LinkedHashMap<String, MutableList<Note>>()
        shown.sortedByDescending { it.createdAt }.forEach {
            val date = it.createdAt.substringBefore(" ")
            groups.getOrPut(date) { mutableListOf() }.add(it)
        }
        groups.forEach { (date, notes) ->
            val dateLabel = TextView(act)
            dateLabel.text = date
            dateLabel.textSize = 12f
            dateLabel.setTextColor(Theme.primary)
            dateLabel.setTypeface(null, Typeface.BOLD)
            listBox.addView(dateLabel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, Ui.dp2px(act, 12f), 0, Ui.dp2px(act, 6f))
            })
            notes.forEach { note -> listBox.addView(buildNoteRow(note)) }
        }
    }

    private fun buildNoteRow(note: Note): View {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.glassCard(act, 16f)
        row.setPadding(Ui.dp2px(act, 14f), Ui.dp2px(act, 12f), Ui.dp2px(act, 8f), Ui.dp2px(act, 12f))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, Ui.dp2px(act, 10f))
        row.layoutParams = lp
        Ui.bindPress(row)

        val content = TextView(act)
        content.text = note.content
        content.textSize = 14f
        content.setTextColor(Theme.text)
        content.setLineSpacing(0f, 1.15f)
        row.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val time = TextView(act)
        time.text = note.createdAt.substringAfter(" ")
        time.textSize = 11f
        time.setTextColor(Theme.textLight)
        row.addView(time, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, Ui.dp2px(act, 6f), 0)
        })

        val edit = TextView(act)
        edit.text = "✏️"
        edit.textSize = 16f
        edit.gravity = Gravity.CENTER
        edit.setPadding(Ui.dp2px(act, 10f), 0, Ui.dp2px(act, 10f), 0)
        Ui.bindPress(edit)
        edit.setOnClickListener { showEditDialog(note) }
        row.addView(edit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val del = TextView(act)
        del.text = "✕"
        del.textSize = 16f
        del.gravity = Gravity.CENTER
        del.setTextColor(Theme.primary)
        del.setPadding(Ui.dp2px(act, 10f), 0, Ui.dp2px(act, 6f), 0)
        Ui.bindPress(del)
        del.setOnClickListener { confirmDelete(note) }
        row.addView(del, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    private fun showAddDialog() {
        val et = EditText(act)
        et.hint = "写一条新备忘录..."
        et.textSize = 14f
        et.setTextColor(Theme.text)
        et.setHintTextColor(Theme.textLight)
        et.setPadding(Ui.dp2px(act, 14f), 0, Ui.dp2px(act, 14f), 0)
        et.background = Ui.roundedBg(act, Theme.inputBg, 16f, Theme.line, 1.5f)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp2px(act, 20f), Ui.dp2px(act, 6f), Ui.dp2px(act, 20f), 0)
        box.addView(et, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)))
        val dlg = AlertDialog.Builder(act)
            .setTitle("📝 添加备忘录")
            .setView(box)
            .setPositiveButton("添加") { _, _ ->
                val c = et.text.toString().trim()
                if (c.isEmpty()) { Ui.toast(act, "请输入内容"); return@setPositiveButton }
                Thread {
                    try {
                        Db.addNote(act, uid, c)
                        act.runOnUiThread { Ui.toast(act, "已添加"); loadNotes()
 }
                    } catch (e: Exception) {
                        act.runOnUiThread { Ui.toast(act, "添加失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(act, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }

    private fun showEditDialog(note: Note) {
        val et = EditText(act)
        et.setText(note.content)
        et.textSize = 14f
        et.setTextColor(Theme.text)
        et.setPadding(Ui.dp2px(act, 14f), 0, Ui.dp2px(act, 14f), 0)
        et.background = Ui.roundedBg(act, Theme.inputBg, 16f, Theme.line, 1.5f)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp2px(act, 20f), Ui.dp2px(act, 6f), Ui.dp2px(act, 20f), 0)
        box.addView(et, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(act, 48f)))
        val dlg = AlertDialog.Builder(act)
            .setTitle("✏️ 编辑备忘录")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val newText = et.text.toString().trim()
                if (newText.isEmpty()) { Ui.toast(act, "内容不能为空"); return@setPositiveButton }
                Thread {
                    try {
                        Db.editNote(act, uid, note.id, newText)
                        act.runOnUiThread { Ui.toast(act, "已保存"); loadNotes()
 }
                    } catch (e: Exception) {
                        act.runOnUiThread { Ui.toast(act, "编辑失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(act, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }

    private fun confirmDelete(note: Note) {
        val dlg = AlertDialog.Builder(act)
            .setTitle("删除此备忘录？")
            .setMessage("「${note.content.take(20)}${if (note.content.length > 20) "…" else ""}」将被删除")
            .setPositiveButton("删除") { _, _ ->
                Thread {
                    try {
                        Db.deleteNote(act, uid, note.id)
                        act.runOnUiThread { Ui.toast(act, "已删除"); loadNotes()
 }
                    } catch (e: Exception) {
                        act.runOnUiThread { Ui.toast(act, "删除失败：${e.message}") }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.window?.setBackgroundDrawable(Ui.roundedBg(act, Color.parseColor("#FFFFFFFF"), 26f,
            Color.parseColor("#E4E7EB"), 1.5f))
        Ui.dialogShow(dlg)
    }
}
