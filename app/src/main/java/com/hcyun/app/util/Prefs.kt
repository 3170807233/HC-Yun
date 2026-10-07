package com.hcyun.app.util

import android.content.Context

/** 登录会话与本地配置存储 */
object Prefs {
    private const val PREFS = "hcyun_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_USERNAME = "username"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_DB_HOST = "db_host"
    private const val KEY_DB_PORT = "db_port"
    private const val KEY_DB_NAME = "db_name"
    private const val KEY_DB_USER = "db_user"
    private const val KEY_DB_PASSWORD = "db_password"
    private const val KEY_MODE = "mode"            // server / local（v1.2.28 单机模式）
    private const val KEY_LMP_LOCAL = "lmp_local"  // 单机模式孕产信息

    /** 单机模式固定本地用户 id（不落库，仅用于区分会话） */
    const val LOCAL_UID = 1

    // 数据库内置默认连接配置（与后端 config.php 一致；输入框不预填，仅内部回退使用）
    const val DEFAULT_DB_HOST = "8.154.35.197"
    const val DEFAULT_DB_PORT = "3306"
    const val DEFAULT_DB_NAME = "yunfu"
    const val DEFAULT_DB_USER = "yunfu"
    const val DEFAULT_DB_PASSWORD = "Jt6zbdestfcWwzTt"

    // ============ 运行模式（v1.2.28） ============

    fun setMode(ctx: Context, local: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, if (local) "local" else "server")
            .apply()
    }

    fun isLocalMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "server") == "local"

    // ============ 会话 ============

    fun saveUser(ctx: Context, uid: Int, username: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_UID, uid)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    /** 单机模式固定返回本地 uid（>0，登录页据此直接进入主界面） */
    fun getUid(ctx: Context): Int =
        if (isLocalMode(ctx)) LOCAL_UID
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_UID, 0)

    fun getUsername(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, null)

    // 本地昵称（仅本机显示，不上传服务器）
    fun saveNickname(ctx: Context, nickname: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NICKNAME, nickname)
            .apply()
    }

    fun getNickname(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NICKNAME, null)

    /** 展示名：优先本地昵称，未设置时回退登录用户名 */
    fun displayName(ctx: Context): String {
        val nick = getNickname(ctx)
        if (!nick.isNullOrBlank()) return nick
        val user = getUsername(ctx)
        return user ?: ""
    }

    fun clearUser(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_UID).remove(KEY_USERNAME).remove(KEY_NICKNAME).apply()
    }

    // ============ 服务器数据库配置（v1.2.28 起输入框不预填默认值） ============

    fun saveDbConfig(ctx: Context, host: String, port: String, name: String, user: String, password: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DB_HOST, host)
            .putString(KEY_DB_PORT, port)
            .putString(KEY_DB_NAME, name)
            .putString(KEY_DB_USER, user)
            .putString(KEY_DB_PASSWORD, password)
            .apply()
    }

    /** 仅返回用户已保存的配置；未保存返回空串（界面用 hint 提示，不预填） */
    fun getDbHost(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DB_HOST, "") ?: ""

    fun getDbPort(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DB_PORT, "") ?: ""

    fun getDbName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DB_NAME, "") ?: ""

    fun getDbUser(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DB_USER, "") ?: ""

    fun getDbPassword(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DB_PASSWORD, "") ?: ""

    /** 解析后的连接参数：空则回退内置默认（供 Db 层使用） */
    fun resolvedDbHost(ctx: Context): String = getDbHost(ctx).ifBlank { DEFAULT_DB_HOST }
    fun resolvedDbPort(ctx: Context): String = getDbPort(ctx).ifBlank { DEFAULT_DB_PORT }
    fun resolvedDbName(ctx: Context): String = getDbName(ctx).ifBlank { DEFAULT_DB_NAME }
    fun resolvedDbUser(ctx: Context): String = getDbUser(ctx).ifBlank { DEFAULT_DB_USER }
    fun resolvedDbPassword(ctx: Context): String = getDbPassword(ctx).ifBlank { DEFAULT_DB_PASSWORD }

    // ============ 单机模式孕产信息 ============

    fun saveLmpLocal(ctx: Context, date: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LMP_LOCAL, date)
            .apply()
    }

    fun getLmpLocal(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LMP_LOCAL, null)
}
