package com.hcyun.app.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hcyun.app.net.ApiException
import com.hcyun.app.net.Db
import com.hcyun.app.util.Prefs
import com.hcyun.app.util.Theme
import com.hcyun.app.util.Ui

/** 登录 / 注册页（对应 Web 端 login.php / register.php） */
class LoginActivity : AppCompatActivity() {

    private lateinit var loginForm: LinearLayout
    private lateinit var registerForm: LinearLayout
    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var msgView: TextView
    private lateinit var btnAction: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        // 已登录则直接进入主页
        if (Prefs.getUid(this) > 0) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        buildUi()
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.background = Ui.gradientBg(this, Color.WHITE, Color.parseColor("#F4F5F7"), 0f)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER_HORIZONTAL
        wrap.setPadding(Ui.dp2px(this, 26f), Ui.dp2px(this, 52f), Ui.dp2px(this, 26f), Ui.dp2px(this, 40f))
        scroll.addView(wrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 品牌区：圆形 Logo 徽标 + 标题 ----
        val logo = TextView(this)
        logo.text = "🤰"
        logo.textSize = 30f
        logo.gravity = Gravity.CENTER
        logo.background = Ui.gradientBg(this, Theme.primaryStart, Theme.primaryEnd, 42f)
        Ui.shadow(logo, 6f)
        val logoLp = LinearLayout.LayoutParams(Ui.dp2px(this, 84f), Ui.dp2px(this, 84f))
        logoLp.gravity = Gravity.CENTER_HORIZONTAL
        wrap.addView(logo, logoLp)

        val title = TextView(this)
        title.text = "HC孕"
        title.textSize = 28f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        title.gravity = Gravity.CENTER
        title.setPadding(0, Ui.dp2px(this, 14f), 0, 0)
        wrap.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sub = TextView(this)
        sub.text = "孕产日历 · 日程 / 孕周 / HCG / 孕酮"
        sub.textSize = 12.5f
        sub.setTextColor(Theme.textSub)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, Ui.dp2px(this, 4f), 0, Ui.dp2px(this, 26f))
        wrap.addView(sub, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 主卡片（液态玻璃） ----
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.glassCard(this, 28f)
        card.setPadding(Ui.dp2px(this, 22f), Ui.dp2px(this, 20f), Ui.dp2px(this, 22f), Ui.dp2px(this, 22f))
        wrap.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 分段 Tab 控件 ----
        val tabSeg = LinearLayout(this)
        tabSeg.orientation = LinearLayout.HORIZONTAL
        tabSeg.background = Ui.roundedBg(this, Color.parseColor("#F1F2F4"), 24f)
        tabSeg.setPadding(Ui.dp2px(this, 4f), Ui.dp2px(this, 4f), Ui.dp2px(this, 4f), Ui.dp2px(this, 4f))
        card.addView(tabSeg, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 48f)))

        tabLogin = makeTab("登 录")
        tabRegister = makeTab("注 册")
        tabSeg.addView(tabLogin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        tabSeg.addView(tabRegister, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // 提示信息
        msgView = TextView(this)
        msgView.textSize = 13f
        msgView.gravity = Gravity.CENTER
        msgView.setPadding(0, Ui.dp2px(this, 12f), 0, Ui.dp2px(this, 4f))
        card.addView(msgView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 登录表单
        loginForm = LinearLayout(this)
        loginForm.orientation = LinearLayout.VERTICAL
        card.addView(loginForm, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val userInput = makeInput("用户名", false)
        val passInput = makeInput("密码", true)
        loginForm.addView(userInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@LoginActivity, 6f), 0, Ui.dp2px(this@LoginActivity, 14f))
        })
        loginForm.addView(passInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@LoginActivity, 20f))
        })

        btnAction = Ui.primaryBtn(this, "登 录", 52f, 16f)
        Ui.bindPress(btnAction)
        loginForm.addView(btnAction, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 52f)))

        btnAction.setOnClickListener {
            val u = userInput.second.text.toString().trim()
            val p = passInput.second.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                showMsg("请填写完整信息", true)
                return@setOnClickListener
            }
            setBusy(true, "登录中...")
            Thread {
                try {
                    val user = Db.login(this, u, p)
                    Prefs.saveUser(this, user.userId, user.username)
                    runOnUiThread {
                        startActivity(Intent(this, MainActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                } catch (e: ApiException) {
                    runOnUiThread {
                        setBusy(false, "")
                        showMsg(e.message ?: "登录失败", true)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        setBusy(false, "")
                        showMsg("登录失败：${e.message}", true)
                    }
                }
            }.start()
        }

        // 注册表单
        registerForm = LinearLayout(this)
        registerForm.orientation = LinearLayout.VERTICAL
        registerForm.visibility = View.GONE
        card.addView(registerForm, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val regUser = makeInput("用户名", false)
        val regPass = makeInput("密码（至少 6 位）", true)
        val regConfirm = makeInput("确认密码", true)
        registerForm.addView(regUser.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, Ui.dp2px(this@LoginActivity, 6f), 0, Ui.dp2px(this@LoginActivity, 14f))
        })
        registerForm.addView(regPass.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@LoginActivity, 14f))
        })
        registerForm.addView(regConfirm.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, Ui.dp2px(this@LoginActivity, 20f))
        })

        val btnReg = Ui.primaryBtn(this, "注 册", 52f, 16f)
        Ui.bindPress(btnReg)
        registerForm.addView(btnReg, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 52f)))

        btnReg.setOnClickListener {
            val u = regUser.second.text.toString().trim()
            val p = regPass.second.text.toString()
            val c = regConfirm.second.text.toString()
            when {
                u.isEmpty() || p.isEmpty() || c.isEmpty() -> showMsg("请填写所有字段", true)
                p != c -> showMsg("两次密码输入不一致", true)
                p.length < 6 -> showMsg("密码至少 6 位", true)
                else -> {
                    setBusy(true, "注册中...")
                    Thread {
                        try {
                            Db.register(this, u, p)
                            runOnUiThread {
                                setBusy(false, "")
                                switchTab(0)
                                showMsg("注册成功，请切换到登录", false)
                            }
                        } catch (e: ApiException) {
                            runOnUiThread {
                                setBusy(false, "")
                                showMsg(e.message ?: "注册失败", true)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                setBusy(false, "")
                                showMsg("注册失败：${e.message}", true)
                            }
                        }
                    }.start()
                }
            }
        }

        // 模式切换（描边胶囊链接，v1.2.28：原「服务器设置」）
        val serverLink = TextView(this)
        serverLink.text = "🔄 模式切换"
        serverLink.textSize = 13f
        serverLink.gravity = Gravity.CENTER
        serverLink.setPadding(Ui.dp2px(this, 18f), Ui.dp2px(this, 10f), Ui.dp2px(this, 18f), Ui.dp2px(this, 10f))
        serverLink.setTextColor(Theme.primary)
        serverLink.background = Ui.roundedBg(this, Color.WHITE, 20f, Color.parseColor("#E4E7EB"), 1.5f)
        Ui.bindPress(serverLink)
        val serverLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        serverLp.setMargins(0, Ui.dp2px(this, 18f), 0, 0)
        serverLink.setOnClickListener { showServerDialog() }
        card.addView(serverLink, serverLp)

        switchTab(0)

        // 登录页整体入场动画
        Ui.animateIn(wrap, 26f, 380)

        setContentView(root)
    }

    private fun makeTab(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.gravity = Gravity.CENTER
        t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, 0, 0, 0)
        Ui.bindPress(t)
        t.setOnClickListener {
            switchTab(if (t === tabLogin) 0 else 1)
        }
        return t
    }

    private fun switchTab(index: Int) {
        val loginActive = index == 0
        applyTabStyle(tabLogin, loginActive)
        applyTabStyle(tabRegister, !loginActive)
        val show = if (loginActive) loginForm else registerForm
        val hide = if (loginActive) registerForm else loginForm
        hide.visibility = View.GONE
        show.visibility = View.VISIBLE
        Ui.fadeIn(show, 220)
        msgView.text = ""
    }

    private fun applyTabStyle(tab: TextView, active: Boolean) {
        if (active) {
            tab.setTextColor(Color.WHITE)
            tab.background = Ui.gradientBg(this, Theme.primaryStart, Theme.primaryEnd, 20f)
            Ui.shadow(tab, 3f)
        } else {
            tab.setTextColor(Theme.textSub)
            tab.background = null
            tab.elevation = 0f
        }
    }

    private fun makeInput(hint: String, isPassword: Boolean): Pair<LinearLayout, EditText> {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val et = EditText(this)
        et.hint = hint
        et.isSingleLine = true
        if (isPassword) et.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        Ui.styleInput(et, 52f)
        box.addView(et)
        return box to et
    }

    private fun showMsg(text: String, isError: Boolean) {
        msgView.text = text
        msgView.setTextColor(if (isError) Theme.error else Theme.success)
    }

    private fun setBusy(busy: Boolean, text: String) {
        btnAction.text = if (busy) text else "登 录"
        btnAction.isEnabled = !busy
        btnAction.alpha = if (busy) 0.7f else 1f
    }

    // ==================== 服务器设置弹窗 ====================

    private fun showServerDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setCancelable(true)

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Ui.glassCard(this, 26f)
        panel.setPadding(Ui.dp2px(this, 22f), Ui.dp2px(this, 20f), Ui.dp2px(this, 22f), Ui.dp2px(this, 18f))

        // 标题行
        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "🔄 模式切换"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Theme.text)
        titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(this)
        close.text = "✕"
        close.textSize = 16f
        close.setTextColor(Theme.textLight)
        close.setPadding(Ui.dp2px(this, 12f), 0, 0, 0)
        Ui.bindPress(close)
        close.setOnClickListener { dlg.dismiss() }
        titleRow.addView(close)
        panel.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sub = TextView(this)
        sub.text = "选择运行模式：联网模式需填写数据库连接信息；单机模式离线运行、免登录"
        sub.textSize = 12f
        sub.setTextColor(Theme.textSub)
        sub.setLineSpacing(0f, 1.3f)
        sub.setPadding(0, Ui.dp2px(this, 2f), 0, Ui.dp2px(this, 12f))
        panel.addView(sub, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun sectionTitle(text: String): TextView {
            val tv = Ui.sectionTitle(this, text, 13f)
            tv.setPadding(0, Ui.dp2px(this, 6f), 0, Ui.dp2px(this, 6f))
            return tv
        }

        fun field(label: String, hint: String, value: String): EditText {
            val lab = TextView(this)
            lab.text = label
            lab.textSize = 12f
            lab.setTextColor(Theme.textSub)
            panel.addView(lab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val et = EditText(this)
            et.hint = hint
            et.setText(value)
            if (value.isNotEmpty()) et.setSelection(value.length)
            et.setTextSize(14f)
            et.isSingleLine = true
            et.setPadding(Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f), 0)
            et.background = Ui.roundedBg(this, Theme.inputBg, 14f, Theme.line, 1.5f)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 46f))
            lp.setMargins(0, 0, 0, Ui.dp2px(this, 12f))
            panel.addView(et, lp)
            return et
        }

        panel.addView(sectionTitle("🗄️ 联网模式 · 数据库连接"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val etHost = field("数据库地址（IP 或域名）", "例如 8.154.35.197", Prefs.getDbHost(this))
        val etPort = field("端口", "默认 3306", Prefs.getDbPort(this))
        val etDbName = field("数据库名", "例如 yunfu", Prefs.getDbName(this))
        val etDbUser = field("数据库账户", "例如 yunfu", Prefs.getDbUser(this))

        // 密码行（带可见性切换）
        val passLab = TextView(this)
        passLab.text = "数据库密码"
        passLab.textSize = 12f
        passLab.setTextColor(Theme.textSub)
        panel.addView(passLab, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val passRow = LinearLayout(this)
        passRow.orientation = LinearLayout.HORIZONTAL
        passRow.gravity = Gravity.CENTER_VERTICAL
        val etDbPass = EditText(this)
        etDbPass.hint = "数据库密码"
        etDbPass.setText(Prefs.getDbPassword(this) ?: "")
        if (etDbPass.text.isNotEmpty()) etDbPass.setSelection(etDbPass.text.length)
        etDbPass.textSize = 14f
        etDbPass.isSingleLine = true
        etDbPass.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        etDbPass.setPadding(Ui.dp2px(this, 14f), 0, Ui.dp2px(this, 14f), 0)
        etDbPass.background = Ui.roundedBg(this, Theme.inputBg, 14f, Theme.line, 1.5f)
        passRow.addView(etDbPass, LinearLayout.LayoutParams(0, Ui.dp2px(this, 46f), 1f))
        val eye = TextView(this)
        eye.text = "👁"
        eye.textSize = 16f
        eye.gravity = Gravity.CENTER
        eye.setTextColor(Theme.textSub)
        val eyeLp = LinearLayout.LayoutParams(Ui.dp2px(this, 46f), Ui.dp2px(this, 46f))
        eyeLp.setMargins(Ui.dp2px(this, 8f), 0, 0, 0)
        passRow.addView(eye, eyeLp)
        var passVisible = false
        eye.setOnClickListener {
            passVisible = !passVisible
            etDbPass.inputType = if (passVisible) android.text.InputType.TYPE_CLASS_TEXT
                else android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etDbPass.setSelection(etDbPass.text.length)
        }
        val passRowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        passRowLp.setMargins(0, 0, 0, Ui.dp2px(this, 8f))
        panel.addView(passRow, passRowLp)

        // 测试结果区
        val result = TextView(this)
        result.visibility = View.GONE
        result.textSize = 13f
        result.setLineSpacing(0f, 1.35f)
        result.background = Ui.roundedBg(this, Color.WHITE, 12f, Color.parseColor("#E4E7EB"), 1.5f)
        result.setPadding(Ui.dp2px(this, 12f), Ui.dp2px(this, 10f), Ui.dp2px(this, 12f), Ui.dp2px(this, 10f))
        panel.addView(result, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 按钮行
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.gravity = Gravity.CENTER_VERTICAL
        val btnH = Ui.dp2px(this, 44f)

        val btnTest = Ui.outlineBtn(this, "测试连接", 44f, 14f)
        Ui.bindPress(btnTest)
        btnRow.addView(btnTest, LinearLayout.LayoutParams(0, btnH, 1f))

        val btnSave = Ui.primaryBtn(this, "保 存", 44f, 14f)
        Ui.bindPress(btnSave)
        val saveLp = LinearLayout.LayoutParams(0, btnH, 1f)
        saveLp.setMargins(Ui.dp2px(this, 10f), 0, 0, 0)
        btnRow.addView(btnSave, saveLp)

        val btnCancel = TextView(this)
        btnCancel.text = "取消"
        btnCancel.textSize = 13f
        btnCancel.gravity = Gravity.CENTER
        btnCancel.setTextColor(Theme.textSub)
        Ui.bindPress(btnCancel)
        val cancelLp = LinearLayout.LayoutParams(0, btnH, 1f)
        cancelLp.setMargins(Ui.dp2px(this, 10f), 0, 0, 0)
        btnRow.addView(btnCancel, cancelLp)

        val btnRowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        btnRowLp.setMargins(0, Ui.dp2px(this, 16f), 0, 0)
        panel.addView(btnRow, btnRowLp)

        // ---- 单机模式（v1.2.28：本地运行免登录，直接进入主界面） ----
        val btnLocal = Ui.primaryBtn(this, "📱 单机模式（本地运行 · 免登录）", 46f, 14f)
        Ui.bindPress(btnLocal)
        val localLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp2px(this, 46f))
        localLp.setMargins(0, Ui.dp2px(this, 12f), 0, 0)
        panel.addView(btnLocal, localLp)
        btnLocal.setOnClickListener {
            Prefs.setMode(this, true)
            Prefs.saveUser(this, Prefs.LOCAL_UID, "本地模式")
            dlg.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        btnTest.setOnClickListener {
            testConnection(etHost.text.toString().trim(), etPort.text.toString().trim(),
                etDbName.text.toString().trim(), etDbUser.text.toString().trim(),
                etDbPass.text.toString(), result)
        }
        btnSave.setOnClickListener {
            Prefs.saveDbConfig(this, etHost.text.toString().trim(), etPort.text.toString().trim(),
                etDbName.text.toString().trim(), etDbUser.text.toString().trim(), etDbPass.text.toString())
            Prefs.setMode(this, false)
            Ui.toast(this, "已切换为联网模式，数据库设置已保存")
            dlg.dismiss()
        }
        btnCancel.setOnClickListener { dlg.dismiss() }

        dlg.setContentView(panel)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(Ui.dp2px(this, 340f), ViewGroup.LayoutParams.WRAP_CONTENT)
        Ui.dialogShow(dlg)
    }

    /** 测试数据库连接（后台线程执行 JDBC 直连验证，可完整验证账号密码） */
    private fun testConnection(
        host: String, port: String, db: String, user: String, pass: String, result: TextView
    ) {
        if (host.isBlank() || db.isBlank() || user.isBlank()) {
            result.visibility = View.VISIBLE
            result.setTextColor(Theme.error)
            result.text = "请填写数据库地址、库名与账户"
            return
        }
        result.visibility = View.VISIBLE
        result.setTextColor(Theme.warn)
        result.text = "⏳ 正在测试数据库连接..."
        Thread {
            val r = Db.testConnection(host, port, db, user, pass)
            runOnUiThread {
                result.text = r
                result.setTextColor(if (r.startsWith("✅")) Theme.success else Theme.error)
            }
        }.start()
    }
}
