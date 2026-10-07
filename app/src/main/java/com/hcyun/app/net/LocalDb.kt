package com.hcyun.app.net

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.model.Note

/**
 * 单机模式本地数据库（v1.2.28）：
 * 与服务器 MySQL 相同的业务表结构（events / daily_indicators / notes），
 * 使用 Android 内置 SQLite，免登录、离线可用。
 * 孕产信息（LMP）存 Prefs，不建 users 表。
 */
class LocalDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, "hcyun_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "event_date TEXT NOT NULL," +
                "title TEXT," +
                "description TEXT," +
                "status INTEGER DEFAULT 0," +
                "period TEXT DEFAULT 'morning')")
        db.execSQL(
            "CREATE TABLE daily_indicators(" +
                "date TEXT PRIMARY KEY," +
                "hcg REAL," +
                "progesterone REAL)")
        db.execSQL(
            "CREATE TABLE notes(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "content TEXT," +
                "created_at TEXT DEFAULT (datetime('now','localtime')))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    companion object {
        @Volatile
        private var instance: LocalDb? = null

        fun get(ctx: Context): LocalDb =
            instance ?: synchronized(this) {
                instance ?: LocalDb(ctx.applicationContext).also { instance = it }
            }
    }
}

// ==================== 本地数据操作（供 Db 层单机分支调用） ====================

fun localGetEvents(ctx: Context, date: String): List<Event> {
    val db = LocalDb.get(ctx).readableDatabase
    val out = ArrayList<Event>()
    db.rawQuery(
        "SELECT id, event_date, title, description, status, period FROM events " +
            "WHERE event_date = ? ORDER BY " +
            "CASE period WHEN 'morning' THEN 0 WHEN 'afternoon' THEN 1 ELSE 2 END, id",
        arrayOf(date)).use { c ->
        while (c.moveToNext()) {
            out.add(Event(c.getInt(0), c.getString(1), c.getString(2) ?: "",
                c.getString(3) ?: "", c.getInt(4), c.getString(5) ?: "morning"))
        }
    }
    return out
}

fun localGetAllEvents(ctx: Context): List<Event> {
    val db = LocalDb.get(ctx).readableDatabase
    val out = ArrayList<Event>()
    db.rawQuery(
        "SELECT id, event_date, title, description, status, period FROM events " +
            "ORDER BY event_date DESC, " +
            "CASE period WHEN 'morning' THEN 0 WHEN 'afternoon' THEN 1 ELSE 2 END, id", null).use { c ->
        while (c.moveToNext()) {
            out.add(Event(c.getInt(0), c.getString(1), c.getString(2) ?: "",
                c.getString(3) ?: "", c.getInt(4), c.getString(5) ?: "morning"))
        }
    }
    return out
}

fun localAddEvent(ctx: Context, date: String, title: String, desc: String, period: String) {
    val db = LocalDb.get(ctx).writableDatabase
    val p = period.let { if (it in arrayOf("morning", "afternoon", "evening")) it else "morning" }
    db.execSQL("INSERT INTO events (event_date, title, description, period) VALUES (?, ?, ?, ?)",
        arrayOf(date, title, desc, p))
}

fun localDeleteEvent(ctx: Context, id: Int) {
    LocalDb.get(ctx).writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
}

fun localUpdateStatus(ctx: Context, id: Int, status: Int) {
    val db = LocalDb.get(ctx).writableDatabase
    db.execSQL("UPDATE events SET status = ? WHERE id = ?", arrayOf(status, id))
}

fun localImportEvents(ctx: Context, rows: List<EventImport>): Int {
    if (rows.isEmpty()) return 0
    val db = LocalDb.get(ctx).writableDatabase
    db.beginTransaction()
    try {
        val deleted = HashSet<String>()
        for (r in rows) {
            if (deleted.add(r.date)) {
                db.delete("events", "event_date = ?", arrayOf(r.date))
            }
            db.execSQL(
                "INSERT INTO events (event_date, period, title, description, status) VALUES (?, ?, ?, ?, ?)",
                arrayOf(r.date, r.period, r.title, r.desc, r.status))
        }
        db.setTransactionSuccessful()
        return rows.size
    } finally {
        db.endTransaction()
    }
}

fun localGetNotes(ctx: Context): List<Note> {
    val db = LocalDb.get(ctx).readableDatabase
    val out = ArrayList<Note>()
    db.rawQuery("SELECT id, content, created_at FROM notes ORDER BY created_at DESC, id DESC", null).use { c ->
        while (c.moveToNext()) {
            out.add(Note(c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: ""))
        }
    }
    return out
}

fun localAddNote(ctx: Context, content: String) {
    val db = LocalDb.get(ctx).writableDatabase
    db.execSQL("INSERT INTO notes (content) VALUES (?)", arrayOf(content))
}

fun localEditNote(ctx: Context, id: Int, content: String) {
    val db = LocalDb.get(ctx).writableDatabase
    db.execSQL("UPDATE notes SET content = ? WHERE id = ?", arrayOf(content, id))
}

fun localDeleteNote(ctx: Context, id: Int) {
    LocalDb.get(ctx).writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
}

fun localGetIndicator(ctx: Context, date: String): Indicator? {
    val db = LocalDb.get(ctx).readableDatabase
    db.rawQuery("SELECT hcg, progesterone FROM daily_indicators WHERE date = ?", arrayOf(date)).use { c ->
        if (c.moveToNext()) {
            val h = if (c.isNull(0)) null else c.getDouble(0)
            val p = if (c.isNull(1)) null else c.getDouble(1)
            return Indicator(date, h, p)
        }
    }
    return null
}

fun localUpdateIndicator(ctx: Context, date: String, hcg: Double?, prog: Double?) {
    val db = LocalDb.get(ctx).writableDatabase
    if (hcg == null && prog == null) {
        db.delete("daily_indicators", "date = ?", arrayOf(date))
        return
    }
    db.execSQL(
        "INSERT INTO daily_indicators (date, hcg, progesterone) VALUES (?, ?, ?) " +
            "ON CONFLICT(date) DO UPDATE SET hcg = excluded.hcg, progesterone = excluded.progesterone",
        arrayOf(date, hcg, prog))
}

fun localGetAllIndicators(ctx: Context): List<Indicator> {
    val db = LocalDb.get(ctx).readableDatabase
    val out = ArrayList<Indicator>()
    db.rawQuery(
        "SELECT date, hcg, progesterone FROM daily_indicators " +
            "WHERE hcg IS NOT NULL OR progesterone IS NOT NULL ORDER BY date ASC", null).use { c ->
        while (c.moveToNext()) {
            val h = if (c.isNull(1)) null else c.getDouble(1)
            val p = if (c.isNull(2)) null else c.getDouble(2)
            out.add(Indicator(c.getString(0), h, p))
        }
    }
    return out
}
