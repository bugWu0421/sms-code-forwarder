package com.wj.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 长短信会拆成多条 pdu，按顺序拼接
        val from = messages.first().originatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis

        SmsHandler.process(context, from, body, receivedAt, goAsync())
    }
}
