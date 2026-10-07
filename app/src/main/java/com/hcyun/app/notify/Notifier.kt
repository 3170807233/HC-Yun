package com.hcyun.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hcyun.app.R
import com.hcyun.app.model.Event
import com.hcyun.app.ui.MainActivity
import java.util.Calendar

/**
 * 日程通知（v1.2.15）：
 * - 每日 7:00 / 11:00 / 17:00 对应 早/中/晚 三档主闹钟（重复），
 *   到点查询当天该时段未完成日程并通知（标题=日程标题，内容=描述）
 * - 未完成时每 15 分钟补发一次，共 4 次（15/30/45/60 分钟后），
 *   之后不再补发；期间若该时段日程全部完成则立即停止补发
 * - 通知点击打开主页面
 */
object Notifier {

    const val CHANNEL_ID = "hcyun_events"
    const val ACTION_MAIN = "com.hcyun.app.action.EVENT_ALARM"
    const val ACTION_FOLLOW = "com.hcyun.app.action.EVENT_FOLLOW"
    const val EXTRA_PERIOD = "period"
    const val EXTRA_ROUND = "round"
    const val MAX_FOLLOWS = 4          // 补发次数上限（15/30/45/60 分钟）
    const val FOLLOW_MINUTES = 15      // 补发间隔

    /** 早/中/晚 对应的整点小时（用户约定：早 7 点 / 中 11 点 / 晚 17 点） */
    val PERIOD_HOURS = mapOf(
        "morning" to 7,
        "afternoon" to 11,
        "evening" to 17
    )

    /**
     * 安排闹钟：优先精确闹钟（Android 12+ 声明 SCHEDULE_EXACT_ALARM 后
     * 系统默认授予，无需用户手动批准；可保证 App 进入后台/Standby 时
     * 提醒仍准时），不可用时降级为 setWindow 窗口闹钟
     */
    private fun setAlarm(ctx: Context, triggerAtMillis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 2 * 60 * 1000L, pi)
        }
    }

    /** 建通知渠道（Android 8+ 必需），幂等 */
    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "早/中/晚日程到点提醒与未完成补发提醒"
        nm.createNotificationChannel(ch)
    }

    /** 安排每日三档主闹钟（每次调用覆盖旧闹钟，幂等） */
    fun scheduleDaily(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        PERIOD_HOURS.forEach { (period, hour) ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            val pi = PendingIntent.getBroadcast(
                ctx, reqCode(period, 0),
                Intent(ctx, EventAlarmReceiver::class.java)
                    .setAction(ACTION_MAIN)
                    .putExtra(EXTRA_PERIOD, period),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // setExactAndAllowWhileIdle / setWindow（降级）：提醒准时可靠
            setAlarm(ctx, cal.timeInMillis, pi)
        }
    }

    /** 安排补发闹钟：距当前 15 分钟后触发（round=1..4 计数，round>4 停止不再续排） */
    fun scheduleFollowUp(ctx: Context, period: String, round: Int) {
        if (round > MAX_FOLLOWS) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + FOLLOW_MINUTES * 60 * 1000L
        val pi = PendingIntent.getBroadcast(
            ctx, reqCode(period, round),
            Intent(ctx, EventAlarmReceiver::class.java)
                .setAction(ACTION_FOLLOW)
                .putExtra(EXTRA_PERIOD, period)
                .putExtra(EXTRA_ROUND, round),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(ctx, trigger, pi)
    }

    /** 发通知：标题=日程标题，内容=描述（空描述给默认文案）；同一条事件只占用一个通知位 */
    fun notifyEvent(ctx: Context, uid: Int, ev: Event) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val body = if (ev.description.isNotBlank()) ev.description else "该时段日程提醒"
        val pi = PendingIntent.getActivity(
            ctx, ev.id,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ev.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // v1.2.15：通知着色为品牌玫瑰粉（Android 8-11 生效；
            // Android 12+ 通知外观由系统统一接管，背景色无法自定义）
            .setColor(0xFFE85D7E.toInt())
            .setColorized(true)
            .build()
        try {
            nm.notify(uid * 10000 + ev.id, n)
        } catch (e: Exception) {
            // 通知权限被拒等场景静默失败，不影响主流程
        }
    }

    private fun reqCode(period: String, round: Int): Int =
        when (period) {
            "morning" -> 100 + round
            "afternoon" -> 200 + round
            else -> 300 + round
        }
}
