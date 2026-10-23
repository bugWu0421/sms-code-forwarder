package com.wj.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import kotlin.concurrent.thread

/** 真实短信与模拟短信共用的处理链路：过滤 → 去重 → 组装 → 异步转发 */
object SmsHandler {

    fun process(
        context: Context,
        from: String,
        body: String,
        receivedAt: Long,
        pending: BroadcastReceiver.PendingResult? = null
    ) {
        thread(name = "sms-forward") {
            try {
                val settings = SettingsStore.load(context)
                if (!settings.enabled || settings.url.isBlank()) return@thread
                if (body.isBlank()) return@thread

                val isCode = SmsParser.isVerification(body)
                if (settings.onlyVerification && !isCode) return@thread

                if (SettingsStore.isDuplicate(context, "$from|$body")) return@thread

                val code = if (isCode) SmsParser.extractCode(body) else null
                val displayText = buildString {
                    appendLine(if (isCode) "📩 收到验证码短信" else "📩 收到短信")
                    if (code != null) appendLine("验证码: $code")
                    appendLine("发件人: $from")
                    appendLine("内容: $body")
                    append("时间: ${Forwarder.formatTime(receivedAt)}")
                }

                val r = Forwarder.send(settings, displayText, from, code, body, receivedAt)
                SettingsStore.saveLastResult(
                    context,
                    "${Forwarder.formatTime(System.currentTimeMillis())}  $from\n${r.detail}"
                )
            } finally {
                pending?.finish()
            }
        }
    }
}
