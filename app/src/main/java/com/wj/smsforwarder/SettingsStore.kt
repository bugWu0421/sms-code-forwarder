package com.wj.smsforwarder

import android.content.Context

data class Settings(
    val enabled: Boolean,
    val mode: String,
    val url: String,
    val secret: String,
    val onlyVerification: Boolean
)

object SettingsStore {
    const val MODE_FEISHU = "feishu"
    const val MODE_CUSTOM = "custom"
    private const val PREFS = "forwarder"

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = p.getBoolean("enabled", false),
            mode = p.getString("mode", MODE_FEISHU) ?: MODE_FEISHU,
            url = p.getString("url", "") ?: "",
            // 优先按密文解密；非 enc: 前缀（旧版明文）原样返回，下次保存自动升级为密文
            secret = p.getString("secret", "")?.let { SecretCrypto.decrypt(it) ?: it } ?: "",
            onlyVerification = p.getBoolean("only_verification", true)
        )
    }

    /** 同步写盘并返回写入结果，保证调用方读回时是磁盘上的真实数据 */
    fun save(context: Context, s: Settings): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putString("mode", s.mode)
            .putString("url", s.url.trim())
            .putString("secret", SecretCrypto.encrypt(s.secret.trim()) ?: s.secret.trim())
            .putBoolean("only_verification", s.onlyVerification)
            .commit()

    fun saveLastResult(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_result", text).apply()
    }

    fun lastResult(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("last_result", "（还没有记录）") ?: "（还没有记录）"

    /**
     * 运营商/双卡可能导致同一条短信在几十秒内投递多次，这里做去重：
     * 同一 (发件人+内容) 在 10 分钟内只转发一次。返回 true 表示重复。
     */
    @Synchronized
    fun isDuplicate(context: Context, key: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val fresh = mutableListOf<String>()
        var dup = false
        for (line in (p.getString("recent", "") ?: "").split('\n')) {
            val idx = line.indexOf('\t')
            if (idx <= 0) continue
            val t = line.substring(0, idx).toLongOrNull() ?: continue
            val k = line.substring(idx + 1)
            if (now - t < 10 * 60_000L) {
                if (k == key) dup = true
                fresh += line
            }
        }
        if (!dup) fresh += "$now\t$key"
        while (fresh.size > 100) fresh.removeAt(0)
        p.edit().putString("recent", fresh.joinToString("\n")).apply()
        return dup
    }
}
