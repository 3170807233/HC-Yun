package com.hcyun.app.net

import android.content.Context
import com.hcyun.app.model.Event
import com.hcyun.app.model.Indicator
import com.hcyun.app.model.Note
import com.hcyun.app.model.PregnancyInfo
import com.hcyun.app.model.UserInfo
import com.hcyun.app.util.Prefs
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 业务异常，message 可直接展示给用户 */
class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 导入用的事件数据行（与网页版 TXT 格式一致：日期/阶段/标题/描述/状态） */
data class EventImport(
    val date: String,
    val period: String,
    val title: String,
    val desc: String,
    val status: Int
)

/**
 * 数据库直连层：App 直接连接 MySQL（mysql-connector-java 5.1.49 驱动），
 * 取代原 HTTP app_api.php 对接方式，业务 SQL 与后端 functions.php 保持一致。
 * 连接配置在登录页「服务器设置」中维护（主机 / 端口 / 库名 / 账户 / 密码）。
 *
 * 驱动说明：曾用 mariadb-java-client 2.7.12，但其 Driver 实现 java.sql.DriverAction，
 * Android 运行时无此类 → Class.forName 即 NoClassDefFoundError 闪退；
 * 故换用不依赖 DriverAction、Android 兼容性经过长期验证的 mysql-connector-java 5.1.49。
 */
object Db {

    private const val DRIVER = "com.mysql.jdbc.Driver"

    private fun jdbcUrl(host: String, port: String, db: String): String =
        "jdbc:mysql://$host:$port/$db?connectTimeout=5000&socketTimeout=10000&useSSL=false&characterEncoding=utf8&autoReconnect=true"

    private fun connect(ctx: Context): Connection {
        val host = Prefs.resolvedDbHost(ctx)
        val port = Prefs.resolvedDbPort(ctx)
        val db = Prefs.resolvedDbName(ctx)
        val user = Prefs.resolvedDbUser(ctx)
        val pass = Prefs.resolvedDbPassword(ctx)
        if (host.isBlank() || db.isBlank() || user.isBlank()) {
            throw ApiException("数据库配置不完整，请先在登录页「模式切换」中填写")
        }
        // 驱动加载放入 try，且用 Throwable 兜底：驱动类初始化失败（如 Android 缺类）只报错不闪退
        try {
            Class.forName(DRIVER)
        } catch (e: Throwable) {
            throw ApiException("数据库驱动加载失败：${e.javaClass.simpleName} ${e.message ?: ""}".trim(), e)
        }
        return try {
            DriverManager.getConnection(jdbcUrl(host, port, db), user, pass)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("数据库连接失败：${friendly(e)}", e)
        }
    }

    /** 把底层数据库异常转成用户可读的中文提示 */
    private fun friendly(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            m.contains("Access denied", true) -> "账号或密码错误"
            m.contains("Unknown database", true) -> "数据库不存在"
            m.contains("Unknown host", true) || m.contains("UnknownHostException", true) -> "数据库地址无法解析"
            m.contains("Connect timed out", true) || m.contains("Communications link failure", true)
                || m.contains("Connection refused", true) || m.contains("timed out", true) ->
                "无法连接数据库服务器，请检查地址与端口"
            m.isBlank() -> e.javaClass.simpleName
            else -> m
        }
    }

    /**
     * v1.2.12 修复备忘录中文变「?」：
     * mysql-connector-java 5.1.49 在 Android 运行时 PreparedStatement.setString
     * 的字符编码转换失效，中文会被替换成 '?'（0x3F）写入；
     * 显式用 UTF-8 字节写入（setBytes）可绕过该缺陷，
     * 数据库 utf8mb4 列按字节存储，读取仍按 utf8 解码，中文正常。
     */
    private fun java.sql.PreparedStatement.setUtf8(index: Int, value: String) {
        setBytes(index, value.toByteArray(Charsets.UTF_8))
    }

    // ============ 认证 ============

    fun login(ctx: Context, username: String, password: String): UserInfo {
        if (Prefs.isLocalMode(ctx)) throw ApiException("单机模式无需登录")
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT id, username, password, lmp_date FROM users WHERE username = ?")
            ps.setUtf8(1, username)
            val rs = ps.executeQuery()
            if (!rs.next()) throw ApiException("用户名或密码错误")
            val hash = rs.getString("password")
            // PHP password_hash 生成 $2y$ 前缀，jBCrypt 需归一化为 $2a$（算法等价）
            val norm = if (hash.startsWith("\$2y\$")) "\$2a\$" + hash.substring(4) else hash
            if (!BCrypt.checkpw(password, norm)) throw ApiException("用户名或密码错误")
            val lmp = rs.getString("lmp_date")
            return UserInfo(rs.getInt("id"), rs.getString("username"), lmp)
        } finally {
            c.close()
        }
    }

    fun register(ctx: Context, username: String, password: String) {
        if (Prefs.isLocalMode(ctx)) throw ApiException("单机模式无需注册")
        if (password.length < 6) throw ApiException("密码至少 6 位")
        val c = connect(ctx)
        try {
            val dup = c.prepareStatement("SELECT id FROM users WHERE username = ?")
            dup.setUtf8(1, username)
            if (dup.executeQuery().next()) throw ApiException("用户名已被占用")
            val hash = BCrypt.hashpw(password, BCrypt.gensalt(10))
            val ins = c.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")
            ins.setUtf8(1, username)
            ins.setUtf8(2, hash)
            ins.executeUpdate()
        } finally {
            c.close()
        }
    }

    // ============ 事件 ============

    fun getEvents(ctx: Context, uid: Int, date: String): List<Event> {
        if (Prefs.isLocalMode(ctx)) return localGetEvents(ctx, date)
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT id, event_date, title, description, status, period FROM events " +
                    "WHERE user_id = ? AND event_date = ? " +
                    "ORDER BY FIELD(period, 'morning','afternoon','evening'), id")
            ps.setInt(1, uid)
            ps.setUtf8(2, date)
            return rsToEvents(ps.executeQuery())
        } finally {
            c.close()
        }
    }

    fun getAllEvents(ctx: Context, uid: Int): List<Event> {
        if (Prefs.isLocalMode(ctx)) return localGetAllEvents(ctx)
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT id, event_date, title, description, status, period FROM events " +
                    "WHERE user_id = ? ORDER BY event_date DESC, " +
                    "FIELD(period,'morning','afternoon','evening'), id")
            ps.setInt(1, uid)
            return rsToEvents(ps.executeQuery())
        } finally {
            c.close()
        }
    }

    fun addEvent(ctx: Context, uid: Int, date: String, title: String, desc: String, period: String) {
        if (Prefs.isLocalMode(ctx)) {
            localAddEvent(ctx, date, title, desc, period)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "INSERT INTO events (user_id, event_date, title, description, period) VALUES (?, ?, ?, ?, ?)")
            ps.setInt(1, uid)
            ps.setUtf8(2, date)
            ps.setUtf8(3, title)
            ps.setUtf8(4, desc)
            ps.setUtf8(5, if (period in arrayOf("morning", "afternoon", "evening")) period else "morning")
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    fun deleteEvent(ctx: Context, uid: Int, id: Int) {
        if (Prefs.isLocalMode(ctx)) {
            localDeleteEvent(ctx, id)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("DELETE FROM events WHERE id = ? AND user_id = ?")
            ps.setInt(1, id)
            ps.setInt(2, uid)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    fun updateStatus(ctx: Context, uid: Int, id: Int, status: Int) {
        if (Prefs.isLocalMode(ctx)) {
            localUpdateStatus(ctx, id, status)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("UPDATE events SET status = ? WHERE id = ? AND user_id = ?")
            ps.setInt(1, status)
            ps.setInt(2, id)
            ps.setInt(3, uid)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    /** 导出用：全部事件按日期升序 + 早/中/晚排序（与网页版 export_events 一致） */
    fun getAllEventsForExport(ctx: Context, uid: Int): List<Event> {
        if (Prefs.isLocalMode(ctx)) {
            // 本地导出与服务器一致：日期升序 + 早/中/晚
            return localGetAllEvents(ctx).sortedWith(
                compareBy<Event> { it.eventDate }
                    .thenBy { periodOrder(it.period) }
                    .thenBy { it.id })
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT id, event_date, title, description, status, period FROM events " +
                    "WHERE user_id = ? ORDER BY event_date ASC, " +
                    "FIELD(period,'morning','afternoon','evening'), id")
            ps.setInt(1, uid)
            return rsToEvents(ps.executeQuery())
        } finally {
            c.close()
        }
    }

    /**
     * 批量导入事件（与网页版 import_events 一致）：
     * 同一日期首次出现时先删除该日期全部旧事件，再逐条插入；整体一个事务。
     * 返回成功插入条数。
     */
    fun importEvents(ctx: Context, uid: Int, rows: List<EventImport>): Int {
        if (rows.isEmpty()) return 0
        if (Prefs.isLocalMode(ctx)) return localImportEvents(ctx, rows)
        val c = connect(ctx)
        var committed = false
        try {
            c.autoCommit = false
            val deleted = HashSet<String>()
            for (r in rows) {
                if (deleted.add(r.date)) {
                    val del = c.prepareStatement("DELETE FROM events WHERE user_id = ? AND event_date = ?")
                    del.setInt(1, uid)
                    del.setUtf8(2, r.date)
                    del.executeUpdate()
                }
                val ins = c.prepareStatement(
                    "INSERT INTO events (user_id, event_date, period, title, description, status) VALUES (?, ?, ?, ?, ?, ?)")
                ins.setInt(1, uid)
                ins.setUtf8(2, r.date)
                ins.setUtf8(3, r.period)
                ins.setUtf8(4, r.title)
                ins.setUtf8(5, r.desc)
                ins.setInt(6, r.status)
                ins.executeUpdate()
            }
            c.commit()
            committed = true
            return rows.size
        } catch (e: Exception) {
            if (!committed) {
                try { c.rollback() } catch (_: Exception) { }
            }
            throw e
        } finally {
            try { c.autoCommit = true } catch (_: Exception) { }
            c.close()
        }
    }

    // ============ 孕产 ============

    fun getPregnancy(ctx: Context, uid: Int): Pair<String?, PregnancyInfo?> {
        if (Prefs.isLocalMode(ctx)) {
            val lmp = Prefs.getLmpLocal(ctx)
            return lmp to (lmp?.let { calcPregnancy(it) })
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("SELECT lmp_date FROM users WHERE id = ?")
            ps.setInt(1, uid)
            val rs = ps.executeQuery()
            val lmp = if (rs.next()) rs.getString("lmp_date") else null
            return lmp to (lmp?.let { calcPregnancy(it) })
        } finally {
            c.close()
        }
    }

    fun updateLmp(ctx: Context, uid: Int, date: String) {
        if (Prefs.isLocalMode(ctx)) {
            Prefs.saveLmpLocal(ctx, date)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("UPDATE users SET lmp_date = ? WHERE id = ?")
            ps.setUtf8(1, date)
            ps.setInt(2, uid)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    /** 复刻 functions.php calculatePregnancyInfo：末次月经 + 280 天 = 预产期 */
    fun calcPregnancy(lmpDate: String): PregnancyInfo? {
        return try {
            val lmp = LocalDate.parse(lmpDate)
            val today = LocalDate.now()
            if (lmp.isAfter(today)) return null
            val daysSince = ChronoUnit.DAYS.between(lmp, today).toInt()
            val due = lmp.plusDays(280)
            val daysRemaining = ChronoUnit.DAYS.between(today, due).toInt()
            PregnancyInfo(
                lmpDate = lmpDate,
                weeks = daysSince / 7,
                days = daysSince % 7,
                dueDate = due.toString(),
                daysRemaining = daysRemaining,
                isOverdue = daysRemaining < 0
            )
        } catch (e: Exception) {
            null
        }
    }

    // ============ 备忘录 ============

    fun getNotes(ctx: Context, uid: Int): List<Note> {
        if (Prefs.isLocalMode(ctx)) return localGetNotes(ctx)
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT id, content, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at FROM notes " +
                    "WHERE user_id = ? ORDER BY created_at DESC")
            ps.setInt(1, uid)
            val rs = ps.executeQuery()
            val out = ArrayList<Note>()
            while (rs.next()) {
                out.add(Note(rs.getInt("id"), rs.getString("content") ?: "", rs.getString("created_at") ?: ""))
            }
            return out
        } finally {
            c.close()
        }
    }

    fun addNote(ctx: Context, uid: Int, content: String) {
        if (Prefs.isLocalMode(ctx)) {
            localAddNote(ctx, content)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("INSERT INTO notes (user_id, content) VALUES (?, ?)")
            ps.setInt(1, uid)
            ps.setUtf8(2, content)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    fun editNote(ctx: Context, uid: Int, id: Int, content: String) {
        if (Prefs.isLocalMode(ctx)) {
            localEditNote(ctx, id, content)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("UPDATE notes SET content = ? WHERE id = ? AND user_id = ?")
            ps.setUtf8(1, content)
            ps.setInt(2, id)
            ps.setInt(3, uid)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    fun deleteNote(ctx: Context, uid: Int, id: Int) {
        if (Prefs.isLocalMode(ctx)) {
            localDeleteNote(ctx, id)
            return
        }
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement("DELETE FROM notes WHERE id = ? AND user_id = ?")
            ps.setInt(1, id)
            ps.setInt(2, uid)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    // ============ 日常指标（HCG / 孕酮） ============

    fun getIndicator(ctx: Context, uid: Int, date: String): Indicator? {
        if (Prefs.isLocalMode(ctx)) return localGetIndicator(ctx, date)
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT hcg, progesterone FROM daily_indicators WHERE user_id = ? AND date = ?")
            ps.setInt(1, uid)
            ps.setUtf8(2, date)
            val rs = ps.executeQuery()
            return if (rs.next()) {
                Indicator(date, nullableDouble(rs, "hcg"), nullableDouble(rs, "progesterone"))
            } else null
        } finally {
            c.close()
        }
    }

    fun updateIndicator(ctx: Context, uid: Int, date: String, hcg: Double?, prog: Double?) {
        if (Prefs.isLocalMode(ctx)) {
            localUpdateIndicator(ctx, date, hcg, prog)
            return
        }
        val c = connect(ctx)
        try {
            // v1.2.13：两项都为空视为“删除该日指标记录”（写空清除全 null 行）
            if (hcg == null && prog == null) {
                val del = c.prepareStatement(
                    "DELETE FROM daily_indicators WHERE user_id = ? AND date = ?")
                del.setInt(1, uid)
                del.setUtf8(2, date)
                del.executeUpdate()
                return
            }
            val ps = c.prepareStatement(
                "INSERT INTO daily_indicators (user_id, date, hcg, progesterone) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE hcg = VALUES(hcg), progesterone = VALUES(progesterone)")
            ps.setInt(1, uid)
            ps.setUtf8(2, date)
            ps.setObject(3, hcg)
            ps.setObject(4, prog)
            ps.executeUpdate()
        } finally {
            c.close()
        }
    }

    fun getAllIndicators(ctx: Context, uid: Int): List<Indicator> {
        if (Prefs.isLocalMode(ctx)) return localGetAllIndicators(ctx)
        val c = connect(ctx)
        try {
            val ps = c.prepareStatement(
                "SELECT date, hcg, progesterone FROM daily_indicators " +
                    "WHERE user_id = ? AND (hcg IS NOT NULL OR progesterone IS NOT NULL) ORDER BY date ASC")
            ps.setInt(1, uid)
            val rs = ps.executeQuery()
            val out = ArrayList<Indicator>()
            while (rs.next()) {
                out.add(Indicator(rs.getString("date"), nullableDouble(rs, "hcg"), nullableDouble(rs, "progesterone")))
            }
            return out
        } finally {
            c.close()
        }
    }

    // ============ 工具 ============

    private fun periodOrder(p: String): Int = when (p) {
        "morning" -> 0
        "afternoon" -> 1
        else -> 2
    }

    private fun rsToEvents(rs: ResultSet): List<Event> {
        val out = ArrayList<Event>()
        while (rs.next()) {
            out.add(Event(
                id = rs.getInt("id"),
                eventDate = rs.getString("event_date") ?: "",
                title = rs.getString("title") ?: "",
                description = rs.getString("description") ?: "",
                status = rs.getInt("status"),
                period = rs.getString("period") ?: "morning"
            ))
        }
        return out
    }

    private fun nullableDouble(rs: ResultSet, col: String): Double? {
        val v = rs.getObject(col) ?: return null
        return (v as? Number)?.toDouble()
    }

    /** 测试数据库连接（登录页设置弹窗用），返回多行结果文本 */
    fun testConnection(host: String, port: String, db: String, user: String, pass: String): String {
        if (host.isBlank() || db.isBlank() || user.isBlank()) {
            return "❌ 请填写数据库地址、库名与账户"
        }
        return try {
            // 驱动加载与连接均用 Throwable 兜底：任何底层错误只转提示、绝不闪退
            Class.forName(DRIVER)
            DriverManager.getConnection(jdbcUrl(host, port, db), user, pass).use { c ->
                val rs = c.createStatement().executeQuery("SELECT VERSION()")
                val ver = if (rs.next()) rs.getString(1) else "未知"
                val t = c.createStatement().executeQuery("SHOW TABLES")
                var tables = 0
                while (t.next()) tables++
                "✅ 数据库连接成功（MySQL $ver）\n✅ 库内共 $tables 张表\nℹ️ 该配置将用于 App 全部数据读写"
            }
        } catch (e: Throwable) {
            "❌ 连接失败：${friendly(e)}"
        }
    }
}
