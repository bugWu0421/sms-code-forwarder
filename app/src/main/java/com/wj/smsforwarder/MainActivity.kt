package com.wj.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var rgMode: RadioGroup
    private lateinit var rbFeishu: RadioButton
    private lateinit var rbCustom: RadioButton
    private lateinit var etUrl: EditText
    private lateinit var etSecret: EditText
    private lateinit var swOnlyCode: Switch
    private lateinit var swEnabled: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvPerm: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rgMode = findViewById(R.id.rgMode)
        rbFeishu = findViewById(R.id.rbFeishu)
        rbCustom = findViewById(R.id.rbCustom)
        etUrl = findViewById(R.id.etUrl)
        etSecret = findViewById(R.id.etSecret)
        swOnlyCode = findViewById(R.id.swOnlyCode)
        swEnabled = findViewById(R.id.swEnabled)
        tvStatus = findViewById(R.id.tvStatus)
        tvPerm = findViewById(R.id.tvPerm)

        val s = SettingsStore.load(this)
        rbFeishu.isChecked = s.mode == SettingsStore.MODE_FEISHU
        rbCustom.isChecked = s.mode == SettingsStore.MODE_CUSTOM
        etUrl.setText(s.url)
        etSecret.setText(s.secret)
        swOnlyCode.isChecked = s.onlyVerification
        swEnabled.isChecked = s.enabled

        rgMode.setOnCheckedChangeListener { _, _ -> updateHints() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { test() }
        findViewById<Button>(R.id.btnPerm).setOnClickListener { requestPerms() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBattery() }
        // 按本机品牌（小米/华为/荣耀/OPPO/vivo/三星/魅族）显示对应文案并直达设置页
        val btnKeepAlive = findViewById<Button>(R.id.btnKeepAlive)
        btnKeepAlive.text = KeepAliveHelper.buttonLabel()
        btnKeepAlive.setOnClickListener { toast(KeepAliveHelper.open(this)) }

        requestPerms()   // 首次打开自动申请权限，减少配置遗漏
        updateHints()
        startForwardService()
    }

    /** 拉起常驻前台服务，防止息屏/清理后进程被冻结收不到短信 */
    private fun startForwardService() {
        val svc = Intent(this, ForwardService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
    }

    override fun onResume() {
        super.onResume()
        refreshPermText()
        showPersistedState("打开 App，以下为存储中实际生效的配置")
        startForwardService()
    }

    /**
     * 离开界面时自动保存（URL 合法时）。
     * 从结构上杜绝"改了配置但没点保存"导致测试通过、真实短信却不转发的问题。
     */
    override fun onPause() {
        super.onPause()
        val s = currentSettings()
        if (s.url.startsWith("http")) SettingsStore.save(this, s)
    }

    private fun currentSettings() = Settings(
        enabled = swEnabled.isChecked,
        mode = if (rbFeishu.isChecked) SettingsStore.MODE_FEISHU else SettingsStore.MODE_CUSTOM,
        url = etUrl.text.toString().trim(),
        secret = etSecret.text.toString().trim(),
        onlyVerification = swOnlyCode.isChecked
    )

    private fun save() {
        val s = currentSettings()
        if (!s.url.startsWith("http")) {
            toast("请先填写正确的 URL")
            return
        }
        // 同步写盘 + 读回验证，把"实际生效"直接摆在用户面前
        val written = SettingsStore.save(this, s)
        val back = SettingsStore.load(this)
        val verified = written && back.enabled == s.enabled &&
                back.mode == s.mode && back.url == s.url
        if (verified) {
            showPersistedState("✅ 已保存，并已从存储读回验证")
            toast("已保存并验证 ✅")
        } else {
            tvStatus.text = "❌ 保存异常：写入未确认，请重试或重启 App"
            toast("保存异常 ❌")
        }
    }

    private fun test() {
        val s = currentSettings()
        if (!s.url.startsWith("http")) {
            toast("请先填写 URL")
            return
        }
        // 测试前先落盘，保证"测试通过的环境"和"真实转发的环境"是同一份配置
        SettingsStore.save(this, s)
        val btn = findViewById<Button>(R.id.btnTest)
        btn.isEnabled = false
        tvStatus.text = "正在发送测试消息…"
        thread {
            val r = Forwarder.sendTest(s)
            runOnUiThread {
                btn.isEnabled = true
                showPersistedState("测试结果: ${r.detail}")
                toast(if (r.success) "发送成功 ✅" else "发送失败 ❌ 看下方详情")
            }
        }
    }

    /**
     * 状态区展示"从存储读出的真实配置"——所见即所生效。
     */
    private fun showPersistedState(headline: String) {
        val s = SettingsStore.load(this)
        val urlShown = when {
            s.url.isEmpty() -> "未设置 ⚠️"
            s.url.length > 40 -> s.url.take(36) + "…"
            else -> s.url
        }
        tvStatus.text = buildString {
            appendLine(headline)
            appendLine()
            appendLine("—— 当前生效配置（从存储实测）——")
            appendLine("模式: ${if (s.mode == SettingsStore.MODE_FEISHU) "飞书机器人" else "自定义接口"}")
            appendLine("总开关: ${if (s.enabled) "开 ✅" else "关 ⚠️ 未开启不会转发"}")
            appendLine("仅转发验证码: ${if (s.onlyVerification) "开" else "关(转发所有短信)"}")
            appendLine("URL: $urlShown")
            appendLine("密钥: ${if (s.secret.isNotEmpty()) "已设置" else "未设置"}")
            appendLine()
            appendLine("—— 最近一次转发记录 ——")
            append(SettingsStore.lastResult(this@MainActivity))
        }
    }

    private fun requestPerms() {
        val wanted = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = wanted.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            refreshPermText()
            return
        }
        requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermText()
    }

    private fun refreshPermText() {
        val smsOk = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifOk = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        tvPerm.text = buildString {
            appendLine(if (smsOk) "✅ 短信权限已授予" else "❌ 短信权限未授予，请点「授权短信权限」")
            append(if (batteryOk) "✅ 已忽略电池优化" else "⚠️ 未加入电池优化白名单（点下方②）")
            if (!notifOk) appendLine("　⚠️ 通知权限未授予")
        }
    }

    private fun requestBattery() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("已在电池优化白名单中 ✅")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                toast("请到系统设置中手动关闭本应用的电池优化")
            }
        }
    }

    private fun updateHints() {
        if (rbFeishu.isChecked) {
            etUrl.hint = "https://open.feishu.cn/open-apis/bot/v2/hook/xxxx"
            etSecret.hint = "加签密钥（没开启签名校验就留空）"
        } else {
            etUrl.hint = "https://your.server.com/api/sms"
            etSecret.hint = "接口密钥（留空则不带 Authorization 头）"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
