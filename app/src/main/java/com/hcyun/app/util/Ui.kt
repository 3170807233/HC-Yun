package com.hcyun.app.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.hcyun.app.ui.ChartActivity
import com.hcyun.app.ui.MainActivity
import com.hcyun.app.ui.MemoActivity
import com.hcyun.app.ui.SettingsActivity

/**
 * 统一主题：Material 3 浅色风格——浅灰页面背景 + 纯白卡片元素，
 * 玫瑰粉仅作品牌点缀（孕产暖粉）。
 */
object Theme {
    val bg = Color.parseColor("#F2F3F5")          // 页面主背景（浅灰，M3 surface）
    val bgDeep = Color.parseColor("#E9EBEE")      // 深一档背景（输入框底/卡片内分隔）
    val card = Color.WHITE                         // 卡片（纯白）
    val text = Color.parseColor("#1F2329")         // 主文字（深灰黑）
    val textSub = Color.parseColor("#8A9099")      // 次要文字（中性灰）
    val textLight = Color.parseColor("#B8BEC7")    // 弱文字（浅灰）
    val primary = Color.parseColor("#E85D7E")      // 品牌主色（玫瑰粉，点缀）
    val primaryStart = Color.parseColor("#F58CA8") // 主渐变起始
    val primaryEnd = Color.parseColor("#E85D7E")   // 主渐变结束
    val primarySoft = Color.parseColor("#FDEBF0")  // 主色浅底（极浅粉）
    val line = Color.parseColor("#E8EAED")         // 分割线（浅灰）
    val inputBg = Color.parseColor("#F5F6F7")      // 输入框底色（浅灰白）
    val success = Color.parseColor("#43A047")
    val warn = Color.parseColor("#FB8C00")
    val error = Color.parseColor("#E53935")
    val hcg = Color.parseColor("#EF6C6C")          // HCG 暖红
    val prog = Color.parseColor("#5C8DD6")         // 孕酮蓝
    val todayStart = Color.parseColor("#FFB3A7")   // 今天圆渐变起
    val todayEnd = Color.parseColor("#F67B6E")     // 今天圆渐变止
}

/** 通用 UI 工具 */
object Ui {

    fun dp2px(ctx: Context, dp: Float): Int = (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    /** 生成圆角矩形背景（radiusDp/strokeDp 单位 dp） */
    fun roundedBg(ctx: Context, color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 0f): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp2px(ctx, radiusDp).toFloat()
        d.setColor(color)
        if (strokeColor != null) {
            d.setStroke(dp2px(ctx, strokeDp), strokeColor)
        }
        return d
    }

    /** 线性渐变背景 */
    fun gradientBg(ctx: Context, start: Int, end: Int, radiusDp: Float): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end))
        d.cornerRadius = dp2px(ctx, radiusDp).toFloat()
        return d
    }

    /** 半透明颜色 */
    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** 给圆角背景的 View 加柔和阴影（Android 5.0+ elevation + outline） */
    fun shadow(view: View, elevationDp: Float) {
        view.elevation = dp2px(view.context, elevationDp).toFloat()
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        // API 28+ 将投影改为中性浅灰，避免黑色重影（白色主题下更柔和）
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                view.outlineAmbientShadowColor = Color.parseColor("#338F959E")
                view.outlineSpotShadowColor = Color.parseColor("#228F959E")
            } catch (_: Exception) {
            }
        }
    }

    /** 给 LayerDrawable 等无背景 outline 的圆角 View 加悬浮投影（显式圆角 outline） */
    fun shadowRound(view: View, elevationDp: Float, radiusDp: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, dp2px(v.context, radiusDp).toFloat())
            }
        }
        view.elevation = dp2px(view.context, elevationDp).toFloat()
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                view.outlineAmbientShadowColor = Color.parseColor("#338F959E")
                view.outlineSpotShadowColor = Color.parseColor("#228F959E")
            } catch (_: Exception) {
            }
        }
    }


    /**
     * 苹果液态玻璃卡片：
     * 纯白半透明渐变底 + 左上高光 + 浅灰细描边 + 轻投影，无黑色阴影。
     * 浅灰背景上呈现通透纯白卡片质感（Material 3 白卡片 + 玻璃高光）。
     */
    fun glassCard(ctx: Context, radiusDp: Float): LayerDrawable {
        val r = dp2px(ctx, radiusDp).toFloat()
        val base = GradientDrawable()
        base.cornerRadius = r
        base.setColor(Color.parseColor("#FFFFFFFF"))
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#8FFFFFFF"), Color.parseColor("#24FFFFFF"), Color.parseColor("#00FFFFFF"))
        )
        sheen.cornerRadius = r
        val border = GradientDrawable()
        border.cornerRadius = r
        border.setColor(Color.TRANSPARENT)
        border.setStroke(dp2px(ctx, 1f), Color.parseColor("#5CE4E7EB"))
        return LayerDrawable(arrayOf(base, sheen, border))
    }

    /** 品牌主按钮（渐变 + 圆角 + 轻投影），返回 TextView（LayoutParams 由调用方 addView 指定） */
    fun primaryBtn(ctx: Context, text: String, heightDp: Float = 50f, textSizeSp: Float = 16f): TextView {
        val b = TextView(ctx)
        b.text = text
        b.textSize = textSizeSp
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.background = gradientBg(ctx, Theme.primaryStart, Theme.primaryEnd, heightDp / 2)
        shadow(b, 3f)
        return b
    }

    /** 描边按钮（次要操作），返回 TextView */
    fun outlineBtn(ctx: Context, text: String, heightDp: Float = 44f, textSizeSp: Float = 14f): TextView {
        val b = TextView(ctx)
        b.text = text
        b.textSize = textSizeSp
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setTextColor(Theme.primary)
        b.background = roundedBg(ctx, Color.TRANSPARENT, heightDp / 2, Theme.primary, 1.5f)
        return b
    }

    /** 统一样式输入框（圆角 + 浅底 + 描边） */
    fun styleInput(et: android.widget.EditText, heightDp: Float = 50f) {
        et.setTextSize(15f)
        et.setTextColor(Theme.text)
        et.setHintTextColor(Theme.textLight)
        et.setPadding(dp2px(et.context, 16f), 0, dp2px(et.context, 16f), 0)
        et.background = roundedBg(et.context, Theme.inputBg, heightDp / 2, Theme.line, 1.5f)
        et.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp2px(et.context, heightDp))
    }

    /** 分区小标题 */
    fun sectionTitle(ctx: Context, text: String, sizeSp: Float = 14f): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = sizeSp
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(Theme.primary)
        return t
    }


    // ==================== 悬浮底栏（v1.3.5 纯白静态） ====================

    /**
     * 底部悬浮底栏（四入口：首页 / 备忘录 / 图表 / 设置，图标 + 文字）。
     * v1.3.5 改为纯白圆角静态卡片（零渲染开销、绝对流畅）；
     * 结构：内容层之上 = 白色 frost 卡片 + 透明 bar（tabs），
     * 悬浮定位（左右 18dp / 底部 22dp，高度 66dp，elevation 阴影）。
     * 四页共用本函数，与首页完全一致的底栏代码。
     */
    fun attachFloatingBar(activity: Activity, frame: FrameLayout, current: String, onHome: () -> Unit = {}) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * d + 0.5f).toInt()

        val barH = dp(66f)

        // 透明 bar 容器（先创建，仅承载 tabs；磨砂背景由下方 frost 层提供；
        // v1.2.23 起移除选中胶囊指示器 pill——用户反馈选中框显示异常，直接删除；
        // 选中态仅以图标+文字变色 + 字重区分）
        val bar = FrameLayout(activity)
        bar.setPadding(dp(6f), 0, dp(6f), 0)
        val barLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, barH)
        barLp.gravity = Gravity.BOTTOM
        barLp.setMargins(dp(18f), 0, dp(18f), dp(22f))

        // 磨砂玻璃层（位于内容之上、bar 之下）
        val frost = FrostedBarBg(activity)
        val frostLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, barH)
        frostLp.gravity = Gravity.BOTTOM
        frostLp.setMargins(dp(18f), 0, dp(18f), dp(22f))
        frame.addView(frost, frostLp)
        shadowRound(frost, 10f, 26f)

        // tabs 透明容器盖在磨砂层上（elevation 11dp 确保 z 轴高于磨砂层
        // 的 10dp 投影，绘制在磨砂之上；透明无 outline 不产生阴影）
        frame.addView(bar, barLp)
        bar.elevation = dp2px(activity, 11f).toFloat()



        // 四入口 tab（图标 + 文字）
        val tabs = LinearLayout(activity)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.gravity = Gravity.CENTER
        tabs.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, barH)
        bar.addView(tabs)

        data class Tab(val id: String, val label: String, val iconRes: Int)

        val list = listOf(
            Tab("home", "首页", com.hcyun.app.R.drawable.ic_home),
            Tab("memo", "备忘录", com.hcyun.app.R.drawable.ic_memo),
            Tab("chart", "图表", com.hcyun.app.R.drawable.ic_chart),
            Tab("settings", "设置", com.hcyun.app.R.drawable.ic_settings)
        )

        val icons = HashMap<String, android.widget.ImageView>()
        val labels = HashMap<String, TextView>()

        fun buildTab(t: Tab): LinearLayout {
            val item = LinearLayout(activity)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, dp(6f), 0, dp(6f))
            val icon = android.widget.ImageView(activity)
            icon.setImageResource(t.iconRes)
            icon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            item.addView(icon, LinearLayout.LayoutParams(dp(22f), dp(22f)))
            val label = TextView(activity)
            label.text = t.label
            label.textSize = 10.5f
            label.gravity = Gravity.CENTER
            label.includeFontPadding = false
            item.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14f)).apply {
                topMargin = dp(2f)
            })
            icons[t.id] = icon
            labels[t.id] = label
            return item
        }

        val items = HashMap<String, LinearLayout>()
        list.forEach { t ->
            val item = buildTab(t)
            items[t.id] = item
            bindPress(item)
            tabs.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

        // 颜色
        val gray = Theme.textSub
        val pink = Theme.primary

        fun setActive(id: String) {
            list.forEach { t ->
                val on = t.id == id
                val c = if (on) pink else gray
                icons[t.id]?.setColorFilter(c)
                labels[t.id]?.setTextColor(c)
                labels[t.id]?.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        setActive(current)

        // v1.2.24：底栏不加入任何过渡动画效果（无弹跳/滑动动画），
        // 切换仅改变图标与文字颜色/字重

        // v1.2.24：页面切换改为左右滑动过渡（取消淡入淡出"一闪"）
        fun slideTo(intent: Intent) {
            activity.startActivity(intent)
            activity.overridePendingTransition(
                com.hcyun.app.R.anim.slide_in_right,
                com.hcyun.app.R.anim.slide_out_left)
        }

        items["home"]!!.setOnClickListener {
            if (current == "home") onHome() else {
                slideTo(
                    Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_GOTO_TODAY, true)
                )
            }
        }
        items["memo"]!!.setOnClickListener {
            if (current != "memo") {
                slideTo(Intent(activity, MemoActivity::class.java))
            }
        }
        items["chart"]!!.setOnClickListener {
            if (current != "chart") {
                slideTo(Intent(activity, ChartActivity::class.java))
            }
        }
        items["settings"]!!.setOnClickListener {
            if (current != "settings") {
                slideTo(Intent(activity, SettingsActivity::class.java))
            }
        }

        animateIn(bar, 26f, 320, 180L)
    }

    /** 侧边栏控制器：外部可更新连接状态点颜色 */
    class RailController(private val statusDot: View) {
        fun updateStatus(connected: Boolean) {
            statusDot.background = if (connected) {
                statusDot.context.let {
                    val g = android.graphics.drawable.GradientDrawable()
                    g.shape = android.graphics.drawable.GradientDrawable.OVAL
                    g.setColor(Color.parseColor("#4CAF50"))
                    g
                }
            } else {
                val g = android.graphics.drawable.GradientDrawable()
                g.shape = android.graphics.drawable.GradientDrawable.OVAL
                g.setColor(Color.parseColor("#F44336"))
                g
            }
        }
    }

    /**
     * 悬浮竖栏（v1.4.6）：
     * - 顶部：数据库连接状态点（绿=已连接 / 红=未连接）
     * - 中间：首页 / 图表 / 备忘录 / 设置
     * - 底部：双向箭头切换按钮（左右互换）
     * - 默认贴右，点击切换到左，再点切回右；onSideChanged 回调通知内容区 padding 调整
     */
    fun attachSideRail(
        activity: Activity,
        frame: FrameLayout,
        onHome: () -> Unit,
        onMemo: () -> Unit,
        onChart: () -> Unit,
        onSettings: () -> Unit,
        onSideChanged: (onLeft: Boolean) -> Unit = {},
        initialOnLeft: Boolean = false
    ): RailController {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * d + 0.5f).toInt()

        val railW = dp(56f)
        val itemH = dp(60f)
        val railPad = dp(8f)
        val margin = dp(12f)
        val railH = (activity.resources.displayMetrics.heightPixels * 0.62f).toInt()

        // 白色背景胶囊
        val railBg = object : View(activity) {
            override fun onDraw(canvas: android.graphics.Canvas) {}
        }
        railBg.background = roundedBg(activity, Color.WHITE, 28f, Color.parseColor("#5CE4E7EB"), 1f)
        val bgLp = FrameLayout.LayoutParams(railW, railH)
        bgLp.gravity = if (initialOnLeft) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
        if (initialOnLeft) bgLp.setMargins(margin, 0, 0, 0) else bgLp.setMargins(0, 0, margin, 0)
        frame.addView(railBg, bgLp)
        shadowRound(railBg, 10f, 28f)

        // 透明容器
        val rail = LinearLayout(activity)
        rail.orientation = LinearLayout.VERTICAL
        rail.setPadding(0, railPad, 0, railPad)
        val railLp = FrameLayout.LayoutParams(railW, railH)
        railLp.gravity = if (initialOnLeft) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
        if (initialOnLeft) railLp.setMargins(margin, 0, 0, 0) else railLp.setMargins(0, 0, margin, 0)
        rail.elevation = dp2px(activity, 11f).toFloat()

        // ---- 顶部状态点 ----
        val statusDot = View(activity)
        val dotG = android.graphics.drawable.GradientDrawable()
        dotG.shape = android.graphics.drawable.GradientDrawable.OVAL
        dotG.setColor(Color.parseColor("#F44336"))
        statusDot.background = dotG
        val dotBox = LinearLayout(activity)
        dotBox.gravity = Gravity.CENTER
        dotBox.setPadding(0, dp(4f), 0, dp(4f))
        dotBox.addView(statusDot, LinearLayout.LayoutParams(dp(10f), dp(10f)))
        rail.addView(dotBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(20f)))

        data class RailItem(val id: String, val label: String, val iconRes: Int, val cb: () -> Unit)
        val items = listOf(
            RailItem("home", "首页", com.hcyun.app.R.drawable.ic_home, onHome),
            RailItem("chart", "图表", com.hcyun.app.R.drawable.ic_chart, onChart),
            RailItem("memo", "备忘录", com.hcyun.app.R.drawable.ic_memo, onMemo),
            RailItem("settings", "设置", com.hcyun.app.R.drawable.ic_settings, onSettings)
        )

        val icons = HashMap<String, android.widget.ImageView>()
        val labels = HashMap<String, TextView>()

        items.forEach { it ->
            val cell = LinearLayout(activity)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, dp(4f), 0, dp(4f))
            val icon = android.widget.ImageView(activity)
            icon.setImageResource(it.iconRes)
            icon.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            cell.addView(icon, LinearLayout.LayoutParams(dp(20f), dp(20f)))
            val label = TextView(activity)
            label.text = it.label
            label.textSize = 9f
            label.gravity = Gravity.CENTER
            label.includeFontPadding = false
            cell.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12f)).apply { topMargin = dp(1f) })
            icons[it.id] = icon
            labels[it.id] = label
            bindPress(cell)
            val idRef = it.id
            val cbRef = it.cb
            cell.setOnClickListener { cbRef() }
            rail.addView(cell, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        // ---- 底部双向箭头切换按钮 ----
        val toggleBtn = TextView(activity)
        toggleBtn.text = "⇄"
        toggleBtn.textSize = 18f
        toggleBtn.gravity = Gravity.CENTER
        toggleBtn.setTextColor(Theme.textSub)
        toggleBtn.setPadding(0, dp(6f), 0, dp(6f))
        bindPress(toggleBtn)
        var onLeft = initialOnLeft
        toggleBtn.setOnClickListener {
            onLeft = !onLeft
            val g = if (onLeft) Gravity.START or Gravity.CENTER_VERTICAL
                    else Gravity.END or Gravity.CENTER_VERTICAL
            bgLp.gravity = g
            railLp.gravity = g
            if (onLeft) {
                bgLp.setMargins(margin, 0, 0, 0)
                railLp.setMargins(margin, 0, 0, 0)
            } else {
                bgLp.setMargins(0, 0, margin, 0)
                railLp.setMargins(0, 0, margin, 0)
            }
            frame.updateViewLayout(railBg, bgLp)
            frame.updateViewLayout(rail, railLp)
            onSideChanged(onLeft)
        }
        rail.addView(toggleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36f)))

        // 默认灰色图标
        items.forEach {
            icons[it.id]?.setColorFilter(Theme.textSub)
            labels[it.id]?.setTextColor(Theme.textSub)
        }

        frame.addView(rail, railLp)
        // 初始化时同步一次内容区 padding
        onSideChanged(onLeft)
        animateIn(rail, 26f, 320, 180L)

        return RailController(statusDot)
    }

    // ==================== 悬浮底栏背景（v1.3.5 纯白静态） ====================

    /**
     * 悬浮底栏背景：纯白圆角卡片 + 浅灰描边 + 柔和投影。
     * 纯静态绘制，无每帧 Bitmap 抓取/模糊，零渲染开销，绝对流畅。
     */
    class FrostedBarBg(ctx: Context) : View(ctx) {
        init {
            background = roundedBg(ctx, Color.WHITE, 26f, Color.parseColor("#5CE4E7EB"), 1f)
        }
    }

    // ==================== 动画 ====================

    /** 点击按压动画：按下轻微缩放 + 变淡，抬起回弹 */
    fun bindPress(view: View, scale: Float = 0.95f) {
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(scale).scaleY(scale).alpha(0.88f)
                        .setDuration(110).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(180).setInterpolator(DecelerateInterpolator()).start()
                }
            }
            false // 不消费事件，保证 onClick / 滚动正常
        }
    }

    /** 入场动画：淡入 + 上移 + 轻微放大（过渡动画） */
    fun animateIn(view: View, dyDp: Float = 20f, duration: Long = 300, delay: Long = 0L) {
        view.alpha = 0f
        view.translationY = dp2px(view.context, dyDp).toFloat()
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 淡入（用于切换/刷新时内容重现） */
    fun fadeIn(view: View, duration: Long = 240) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(duration).start()
    }

    /** 弹窗带过渡动画显示（缩放 + 淡入 + 上移） */
    fun dialogShow(dlg: Dialog) {
        dlg.show()
        dlg.window?.decorView?.let { animateIn(it, 26f, 280) }
    }
}
