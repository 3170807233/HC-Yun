package com.hcyun.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hcyun.app.net.Db
import com.hcyun.app.util.Prefs
import java.util.Calendar

/**
 * 日程闹钟接收器（v1.4.11 重写）：
 * - 主闹钟（ACTION_MAIN，每日 7/11/17 点）：通知当天该时段未完成日程，
 *   存在未完成则安排第 1 次补发（15 分钟后）
 * - 补发闹钟（ACTION_FOLLOW，round=1..4）：仍存在未完成则再通知并续排
 *   下一轮；全部完成则停止；round 达到上限后不再补发
 * - 可靠性修复（v1.4.11）：
 *   ① goAsync() 延长广播生命周期——进程被系统冷启动拉起时，后台线程
 *      不会被系统提前杀死，网络查询有充足时间完成
 *   ② 本地缓存兜底——数据库查询失败（断网/冷启动连库慢）时，直接读取
 *      LocalCache 全量缓存中的当天未完成日程通知，不再"时有时无"
 *   ③ 未登录（uid<=0）直接忽略
 */
class EventAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val period = intent.getStringExtra(Notifier.EXTRA_PERIOD) ?: return
        val round = intent.getIntExtra(Notifier.EXTRA_ROUND, 0)
        val uid = Prefs.getUid(context)
        if (uid <= 0) return
        // 应用进程未启动时由系统拉起（后台/开机后），必须先建通知渠道，
        // 否则 Android 8+ 无渠道会导致通知不显示
        Notifier.createChannel(context)

        // goAsync：延长广播生命周期到后台线程完成，防止冷启动进程被杀
        val pendingResult = goAsync()
        Thread {
            try {
                val today = todayStr()
                var pending = try {
                    Db.getEvents(context, uid, today)
                        .filter { it.period == period && it.status == 0 }
                } catch (e: Exception) {
                    emptyList()   // 数据库失败 → 用缓存兜底
                }

                if (pending.isEmpty()) {
                    // 数据库无数据/失败：读本地缓存（上轮已同步的全量数据）
                    pending = com.hcyun.app.net.LocalCache.loadEvents(context, uid)
                        .filter { it.eventDate == today && it.period == period && it.status == 0 }
                }

                if (pending.isEmpty()) return@Thread   // 当天该时段无未完成日程：不通知也不续排

                pending.forEach { Notifier.notifyEvent(context, uid, it) }
                if (round < Notifier.MAX_FOLLOWS) {
                    Notifier.scheduleFollowUp(context, period, round + 1)
                }
            } catch (e: Exception) {
                // 全部路径失败则静默，下一轮主闹钟会重试
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun todayStr(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
