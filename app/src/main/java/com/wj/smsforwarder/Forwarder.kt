package com.wj.smsforwarder

import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 网络转发。支持两种模式：
 * 1. feishu  —— 飞书自定义机器人 webhook（免费，可选加签）
 * 2. custom  —— 自定义接口，POST JSON，密钥非空时带 Authorization: Bearer 头
 */
object Forwarder {

    data class HttpResult(
        val success: Boolean,
        val detail: String,
        /** 网络类失败（超时/断网/5xx）才重试；配置类失败（4xx/签名错）重试必然同样失败 */
        val retryable: Boolean = true
    )

    fun send(
        s: Settings,
        displayText: String,
        from: String,
        code: String?,
        body: String,
        receivedAt: Long
    ): HttpResult {
        val payload = if (s.mode == SettingsStore.MODE_FEISHU) {
            buildFeishuPayload(displayText, s.secret)
        } else {
            buildCustomPayload(from, code, body, receivedAt)
        }
        return post(s, payload.toString())
    }

    fun sendTest(s: Settings): HttpResult {
        val payload = if (s.mode == SettingsStore.MODE_FEISHU) {
            buildFeishuPayload("【短信转发】这是一条测试消息，收到说明配置成功 ✅", s.secret)
        } else {
            buildCustomPayload("test", null, "这是一条测试消息（来自短信转发App）", System.currentTimeMillis())
        }
        return post(s, payload.toString())
    }

    private fun buildFeishuPayload(text: String, secret: String): JSONObject {
        val o = JSONObject()
        if (secret.isNotEmpty()) {
            val ts = (System.currentTimeMillis() / 1000).toString()
            o.put("timestamp", ts)
            o.put("sign", feishuSign(ts, secret))
        }
        o.put("msg_type", "text")
        o.put("content", JSONObject().put("text", text))
        return o
    }

    /**
     * 飞书自定义机器人加签算法：
     * key = timestamp + "\n" + secret，对空内容做 HmacSHA256，再 Base64。
     */
    private fun feishuSign(timestamp: String, secret: String): String {
        val stringToSign = "$timestamp\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(stringToSign.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(ByteArray(0)), Base64.NO_WRAP)
    }

    private fun buildCustomPayload(from: String, code: String?, body: String, receivedAt: Long): JSONObject {
        val o = JSONObject()
        o.put("type", "sms")
        o.put("from", from)
        if (code != null) o.put("code", code)
        o.put("body", body)
        o.put("receivedAt", receivedAt)
        o.put("receivedAtText", formatTime(receivedAt))
        o.put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        return o
    }

    /**
     * 带重试的发送：弱网/息屏瞬间网络未就绪是转发失败的主因，
     * 网络类失败间隔 2 秒重试，最多 3 次；配置类失败立即返回。
     * 只在后台线程调用（SmsHandler / 测试线程）。
     */
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L

    private fun post(s: Settings, payload: String): HttpResult {
        var last = HttpResult(false, "未发起请求")
        for (attempt in 1..ATTEMPTS) {
            last = postOnce(s, payload)
            if (last.success || !last.retryable || attempt == ATTEMPTS) {
                if (attempt > 1) last = HttpResult(last.success, "[第${attempt}次尝试] ${last.detail}", last.retryable)
                return last
            }
            try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { }
        }
        return last
    }

    private fun postOnce(s: Settings, payload: String): HttpResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(s.url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (s.mode == SettingsStore.MODE_CUSTOM && s.secret.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer ${s.secret}")
            }
            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }?.take(500).orEmpty()

            var ok = status in 200..299
            if (ok && s.mode == SettingsStore.MODE_FEISHU) {
                // 飞书即使出错也返回 HTTP 200，需要看业务码
                ok = try { JSONObject(resp).optInt("code", -1) == 0 } catch (_: Exception) { false }
            }
            // 4xx 属于配置/请求问题，重试无意义；5xx 与异常都可能自愈，可重试
            val retryable = status >= 500 || status <= 0
            HttpResult(ok, "HTTP $status ${resp.ifBlank { "(空响应)" }}", retryable)
        } catch (e: Exception) {
            HttpResult(false, "请求失败: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    fun formatTime(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(ts))
}
