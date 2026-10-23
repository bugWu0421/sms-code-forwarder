# 隐私说明 / Privacy

本应用（短信验证码转发）**不采集、不上传、不包含任何统计或广告 SDK**。

## 数据流向（全部可自查）

| 数据 | 存在哪 | 去向 |
|---|---|---|
| 短信发件人 / 内容 / 时间 | 仅内存处理 | 按你的配置，原文发往**你自己填写的** Webhook / 接口（见下） |
| Webhook 地址、密钥 | 仅本机 SharedPreferences（密钥经 Android Keystore AES-256 加密） | 不发送到任何第三方 |
| 去重缓存（近 10 分钟短信摘要） | 仅本机 | 不出设备 |

## 权限用途

- `RECEIVE_SMS` / `READ_SMS`：接收新短信广播，这是本应用的核心功能，没有它无法工作
- `INTERNET` / `ACCESS_NETWORK_STATE`：把短信内容发到你配置的地址
- `POST_NOTIFICATIONS`：显示"转发服务运行中"常驻通知（Android 13+）
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` / 前台服务 / `RECEIVE_BOOT_COMPLETED`：防止系统冻结进程导致漏转发、重启后自动恢复

## 你需要知道的风险面

1. **短信内容会离开手机**——目的地完全由你配置。请只填自己控制的飞书机器人或 HTTPS 接口
2. 飞书 Webhook 地址泄露 = 任何人都可向你的群发消息（读不到你的消息），请勿公开
3. 自定义接口请务必校验 `Authorization` 头并使用 HTTPS，否则公网上任何人可向其伪造数据
4. `allowBackup=false`：换机导出**不会**带走配置（含密钥），需重新填写——这是有意为之
5. ADB 模拟短信入口（`SIMULATE_SMS`）已做调用方校验，仅 adb/系统/本应用可触发

## 卸载

直接卸载即清除全部本地数据，无任何残留。
