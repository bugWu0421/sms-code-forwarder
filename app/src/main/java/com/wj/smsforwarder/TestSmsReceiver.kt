package com.wj.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process

/**
 * 联调用的"模拟短信"入口，走与真实短信完全相同的处理与转发链路
 * （同样受总开关/验证码过滤/去重约束）：
 *
 * adb shell am broadcast -a com.wj.smsforwarder.SIMULATE_SMS \
 *   --es from 10086 --es body "【测试】您的验证码是654321，10分钟内有效"
 *
 * receiver 必须 exported 才能被 adb 命中，这里校验调用方 uid：
 * 只接受 adb shell(root/shell)、系统进程和本应用自己，防止第三方 App
 * 伪造广播向用户的 Webhook 塞入任意内容。
 */
class TestSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val uid = Binder.getCallingUid()
        val allowed = uid == 0 ||              // root（su 下 adb）
                uid == 2000 ||                 // adb shell
                uid == Process.SYSTEM_UID ||   // 系统
                uid == Process.myUid()         // 本应用
        if (!allowed) return

        val from = intent.getStringExtra("from") ?: "10086"
        val body = intent.getStringExtra("body")
            ?: "【测试】您的验证码是654321，10分钟内有效"
        SmsHandler.process(context, from, body, System.currentTimeMillis())
    }

    companion object {
        const val ACTION = "com.wj.smsforwarder.SIMULATE_SMS"
    }
}
