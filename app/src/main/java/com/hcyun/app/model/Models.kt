package com.hcyun.app.model

import org.json.JSONObject

/** 日程事件（对应 events 表） */
data class Event(
    val id: Int,
    val eventDate: String,
    val title: String,
    val description: String,
    val status: Int,
    val period: String
) {
    companion object {
        fun fromJson(o: JSONObject): Event = Event(
            id = o.optInt("id"),
            eventDate = o.optString("event_date"),
            title = o.optString("title"),
            description = o.optString("description"),
            status = o.optInt("status"),
            period = o.optString("period")
        )
    }
}

/** 备忘录（对应 notes 表） */
data class Note(
    val id: Int,
    val content: String,
    val createdAt: String
) {
    companion object {
        fun fromJson(o: JSONObject): Note = Note(
            id = o.optInt("id"),
            content = o.optString("content"),
            createdAt = o.optString("created_at")
        )
    }
}

/** 每日指标：HCG / 孕酮（对应 daily_indicators 表） */
data class Indicator(
    val date: String,
    val hcg: Double?,
    val progesterone: Double?
) {
    companion object {
        fun fromJson(o: JSONObject): Indicator = Indicator(
            date = o.optString("date"),
            hcg = if (o.isNull("hcg")) null else o.optDouble("hcg"),
            progesterone = if (o.isNull("progesterone")) null else o.optDouble("progesterone")
        )
    }
}

/** 孕产信息（由末次月经推算，280 天预产期，医学标准） */
data class PregnancyInfo(
    val lmpDate: String?,
    val weeks: Int,
    val days: Int,
    val dueDate: String,
    val daysRemaining: Int,
    val isOverdue: Boolean
)

/** 登录用户信息 */
data class UserInfo(
    val userId: Int,
    val username: String,
    val lmpDate: String?
)
