package com.hcyun.app.net

import android.content.Context
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.model.Note
import com.hcyun.app.model.PregnancyInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地数据缓存（v1.4.3）：
 * - UI 先读本地缓存立即渲染，不阻塞
 * - 后台拉 MySQL 成功后更新缓存
 * - 写操作先更新缓存（离线可改），后台再同步到 MySQL
 */
object LocalCache {

    private const val PREF = "hcyun_local_cache"
    private fun pref(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- Event ----
    fun saveEvents(ctx: Context, uid: Int, list: List<Event>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("event_date", e.eventDate)
            o.put("title", e.title)
            o.put("description", e.description)
            o.put("status", e.status)
            o.put("period", e.period)
            arr.put(o)
        }
        pref(ctx).edit().putString("events_$uid", arr.toString()).apply()
    }

    fun loadEvents(ctx: Context, uid: Int): List<Event> {
        val raw = pref(ctx).getString("events_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Event.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    // ---- Indicator ----
    fun saveIndicators(ctx: Context, uid: Int, list: List<Indicator>) {
        val arr = JSONArray()
        list.forEach { i ->
            val o = JSONObject()
            o.put("date", i.date)
            if (i.hcg != null) o.put("hcg", i.hcg) else o.put("hcg", JSONObject.NULL)
            if (i.progesterone != null) o.put("progesterone", i.progesterone) else o.put("progesterone", JSONObject.NULL)
            arr.put(o)
        }
        pref(ctx).edit().putString("indicators_$uid", arr.toString()).apply()
    }

    fun loadIndicators(ctx: Context, uid: Int): List<Indicator> {
        val raw = pref(ctx).getString("indicators_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Indicator.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    // ---- Note ----
    fun saveNotes(ctx: Context, uid: Int, list: List<Note>) {
        val arr = JSONArray()
        list.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("content", n.content)
            o.put("created_at", n.createdAt)
            arr.put(o)
        }
        pref(ctx).edit().putString("notes_$uid", arr.toString()).apply()
    }

    fun loadNotes(ctx: Context, uid: Int): List<Note> {
        val raw = pref(ctx).getString("notes_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Note.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    // ---- Pregnancy / LMP ----
    fun saveLmp(ctx: Context, uid: Int, lmp: String?) {
        pref(ctx).edit().putString("lmp_$uid", lmp).apply()
    }

    fun loadLmp(ctx: Context, uid: Int): String? {
        return pref(ctx).getString("lmp_$uid", null)
    }

    // ---- 待同步操作队列 ----
    data class PendingOp(
        val type: String,
        val payload: Map<String, String>
    )

    fun loadPendingOps(ctx: Context): List<PendingOp> {
        val raw = pref(ctx).getString("pending_ops", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val type = o.getString("type")
                val p = o.getJSONObject("payload")
                val map = HashMap<String, String>()
                p.keys().forEach { k -> map[k] = p.getString(k) }
                PendingOp(type, map)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun addPendingOp(ctx: Context, op: PendingOp) {
        val list = loadPendingOps(ctx).toMutableList()
        list.add(op)
        val arr = JSONArray()
        list.forEach { po ->
            val o = JSONObject()
            o.put("type", po.type)
            val p = JSONObject()
            po.payload.forEach { (k, v) -> p.put(k, v) }
            o.put("payload", p)
            arr.put(o)
        }
        pref(ctx).edit().putString("pending_ops", arr.toString()).apply()
    }

    fun clearPendingOps(ctx: Context) {
        pref(ctx).edit().remove("pending_ops").apply()
    }
}