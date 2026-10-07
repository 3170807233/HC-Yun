package com.hcyun.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hcyun.app.util.Prefs

/**
 * 开机 / 时间变更 / 应用更新后重排每日日程闹钟（v1.2.18）：
 * 手机重启后应用无需手动启动，也能在 7:00 / 11:00 / 17:00 收到日程提醒。
 * 未登录（uid<=0）时不排；登录态存在则静默重排（不弹任何界面）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        if (Prefs.getUid(context) <= 0) return
        Notifier.createChannel(context)
        Notifier.scheduleDaily(context)
    }
}
