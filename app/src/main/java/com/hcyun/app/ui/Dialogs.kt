package com.hcyun.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.hcyun.app.net.Db
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui

/** 给 AlertDialog 设置统一白色卡片背景 + 入场动画 */
private fun AlertDialog.applyCardStyle(ctx: android.content.Context) {
    window?.setBackgroundDrawable(
        Ui.roundedBg(ctx, android.graphics.Color.WHITE, 26f,
            android.graphics.Color.parseColor("#E4E7EB"), 1.5f)
    )
}

// ==================== HCG / 孕酮录入（主界面日历双击选中日弹出） ====================

fun MainActivity.showIndicatorDialog(dateStr: String) {
    // v1.2.13：未来日期不能填写 HCG / 孕酮
    val today = java.time.LocalDate.now().toString()
    if (dateStr > today) {
        Ui.toast(this, "未来日期不能填写 HCG / 孕酮")
        return
    }
    val cur = allIndicators.find { it.date == dateStr }

    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(Ui.dp2px(this, 20f), Ui.dp2px(this, 4f), Ui.dp2px(this, 20f), 0)

    val hcgEt = EditText(this)
    hcgEt.hint = "HCG 值"
    hcgEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    hcgEt.setText(cur?.hcg?.let { trimNum(it) } ?: "")
    Ui.styleInput(hcgEt, 50f)
    box.addView(hcgEt, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 50f)))

    val progEt = EditText(this)
    progEt.hint = "孕酮 (P) 值"
    progEt.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    progEt.setText(cur?.progesterone?.let { trimNum(it) } ?: "")
    Ui.styleInput(progEt, 50f)
    box.addView(progEt, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 50f)).apply {
        setMargins(0, Ui.dp2px(this@showIndicatorDialog, 14f), 0, 0)
    })

    val dlg = AlertDialog.Builder(this)
        .setTitle("📊 设置 HCG 和孕酮（$dateStr）")
        .setMessage("可只填写一项，留空的将清除该指标；两项都留空则删除该日记录。")
        .setView(box)
        .setPositiveButton("保存") { _, _ ->
            val hcg = hcgEt.text.toString().trim()
            val prog = progEt.text.toString().trim()
            if (hcg.isEmpty() && prog.isEmpty()) {
                // v1.2.13：两项都留空 = 删除该日指标记录（若存在）
                Thread {
                    try {
                        Db.updateIndicator(this, uid, dateStr, null, null)
                        runOnUiThread {
                            Ui.toast(this, if (cur == null) "当日没有指标记录" else "已删除")
                            refreshAfterMutation()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Ui.toast(this, "删除失败：${e.message}") }
                    }
                }.start()
                return@setPositiveButton
            }
            Thread {
                try {
                    Db.updateIndicator(this, uid, dateStr, hcg.toDoubleOrNull(), prog.toDoubleOrNull())
                    runOnUiThread {
                        Ui.toast(this, "已保存")
                        refreshAfterMutation()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Ui.toast(this, "保存失败：${e.message}") }
                }
            }.start()
        }
        .setNegativeButton("取消", null)
        .create()
    dlg.applyCardStyle(this)
    Ui.dialogShow(dlg)
}

// ==================== 修改本地昵称（主界面点击账号弹出，不上传服务器） ====================

fun MainActivity.showRenameDialog() {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(Ui.dp2px(this, 20f), Ui.dp2px(this, 4f), Ui.dp2px(this, 20f), 0)

    val nameEt = EditText(this)
    nameEt.hint = "输入新名字"
    nameEt.setText(Prefs.displayName(this))
    Ui.styleInput(nameEt, 50f)
    box.addView(nameEt, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 50f)))

    val dlg = AlertDialog.Builder(this)
        .setTitle("✏️ 修改名字")
        .setView(box)
        .setPositiveButton("保存") { _, _ ->
            val name = nameEt.text.toString().trim()
            if (name.isEmpty()) {
                Ui.toast(this, "名字不能为空")
                return@setPositiveButton
            }
            Prefs.saveNickname(this, name)
            Ui.toast(this, "已修改")
            refreshUserLabel()
        }
        .setNegativeButton("取消", null)
        .create()
    dlg.applyCardStyle(this)
    Ui.dialogShow(dlg)
}

private fun trimNum(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else "%.2f".format(v)

// 说明：备忘录 / 图表 / 设置（孕产+事件管理）已从弹窗升级为独立页面，
// 见 MemoActivity.kt / ChartActivity.kt / SettingsActivity.kt。
