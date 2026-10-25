# 短信验证码转发 SmsCodeForwarder

把安卓手机新收到的**验证码短信**实时转发到 **飞书群机器人**（推荐，免费无需服务器）或**你自己的 HTTP 接口**。副机收验证码、主业手机/电脑秒收的场景而生。

> **本仓库包含两个独立组件**
> - **SmsCodeForwarder**（Android App，本 README）—— 验证码短信实时转发
> - **[wechat-pipeline/](wechat-pipeline/README.md)**（macOS 脚本）—— 微信聊天记录夜间流水线：每天凌晨自动拉快照 → 按身份分组挖掘（对方提问/待办线索）→ 生成每日简报 → 可选飞书推送。全程离线，个人规则放 `pipeline.local.json` 不进 Git

[English](README_EN.md) · [使用手册](docs/App使用手册.md) · [飞书接入手册](docs/飞书接入手册.md) · [微信夜间流水线](wechat-pipeline/README.md) · [隐私说明](PRIVACY.md) · [更新日志](CHANGELOG.md)

```
📩 收到验证码短信
验证码: 123456
发件人: 1069012345678
内容: 【京东】您的验证码为123456，10分钟内有效…
时间: 2026-10-19 17:05:32
```

## 为什么是它

- **零第三方依赖**：全部代码基于 Android 自带 API，约 800KB，任何人可审计全部行为
- **零数据采集**：无统计、无广告、无 SDK；配置只存本机，密钥经 Android Keystore 加密
- **全厂商适配**：自动识别小米/华为/荣耀/OPPO/一加/真我/vivo/iQOO/三星/魅族，一键直达该品牌的自启动/后台管理设置页——短信转发类 App 的头号问题（息屏后收不到）就是后台被杀
- **工程细节**：长短信拼接、10 分钟去重、弱网自动重试、配置"写盘读回验证"、adb 免话费联调

仅支持 Android 7.0+（iOS 不开放短信读取，无法实现）。

## 快速开始

1. 构建或获取 APK（见下）
2. 手机安装后依次点 ①授权短信权限 ②电池白名单 ③允许自启动（按品牌自动跳转）
3. 模式选「飞书机器人」→ 粘贴 Webhook → 点「发送测试」→ 群里收到消息 → 开总开关。飞书机器人 5 分钟配置教程见[飞书接入手册](docs/飞书接入手册.md)

## 构建

普通用户直接到 [Releases](https://github.com/bugWu0421/sms-code-forwarder/releases) 下载最新 APK 安装（debug 签名）。

自行构建需要 JDK 17+（项目含 Gradle Wrapper，无需预装 Gradle）：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 打开直接编译。仓库每次 push 会由 GitHub Actions 自动构建 APK（Actions → Build APK → Artifacts 下载）；打 `v*` 标签会自动创建 Release 并附上 APK。

## 各品牌手机必做设置（v1.4 自动跳转）

App 第③个按钮会识别本机品牌并直达设置页；这里给出完整路径备查：

| 品牌 | 路径 |
|---|---|
| 小米/红米 | 设置 → 应用设置 → 应用管理 → 本应用 → 允许「自启动」；省电策略 → 无限制；最近任务下拉「锁定」 |
| 华为/荣耀 | 设置 → 应用和服务 → 应用启动管理 → 本应用 → 关闭「自动管理」→ 自启动/关联启动/后台活动 三个全开 |
| OPPO/一加/真我 | 设置 → 电池 → 更多设置/应用速冻 → 关闭本应用的「智能速冻」；允许自启动、后台运行 |
| vivo/iQOO | 设置 → 电池 → 后台高耗电 → 允许本应用；应用管理 → 权限 → 关联启动允许 |
| 三星 | 设置 → 电池 → 后台使用限制 → 把本应用从"深度睡眠"列表排除 |
| 原生/Pixel | 无需额外设置 |

**通用红线**：不要「强制停止」本应用——强停后系统不再派发短信广播，重新打开一次 App 即恢复。

## 自定义接口协议

```
POST <你填的URL>
Content-Type: application/json
Authorization: Bearer <你填的密钥>    ← 密钥留空则不带此头
```

```json
{
  "type": "sms",
  "from": "1069012345678",
  "code": "123456",
  "body": "【京东】您的验证码为123456，10分钟内有效",
  "receivedAt": 1760000000000,
  "receivedAtText": "2026-10-19 12:30:45",
  "device": "Xiaomi 2210132C"
}
```

`code` 仅在识别为验证码类短信时存在。识别规则与提取优先级见[使用手册](docs/App使用手册.md)。服务端**必须**校验 Authorization 头（最小 Flask 示例见使用手册）。

## 开发联调

不花话费模拟一条短信（走与真实短信完全相同的过滤/去重/转发链路，v1.4 起仅 adb/系统/本应用可触发）：

```bash
adb shell am broadcast -a com.wj.smsforwarder.SIMULATE_SMS \
  --es from 10086 --es body "【测试】您的验证码是123456，10分钟内有效"
```

## 安全与隐私

- 验证码 = 账号资产的钥匙，**只转发到自己控制的群/接口**；飞书 Webhook 不要多人共用
- 密钥经 Android Keystore（AES-256-GCM）加密落盘；`allowBackup=false` 防止配置随系统备份导出
- 详细说明见 [PRIVACY.md](PRIVACY.md)

## 目录结构

```
wechat-pipeline/      # 微信聊天夜间流水线（macOS，独立脚本，见其 README）
app/src/main/java/com/wj/smsforwarder/
├── SmsReceiver.kt        # 系统短信广播入口（BROADCAST_SMS 权限防伪造）
├── SmsHandler.kt         # 共用处理链路：过滤→去重→组装→异步转发
├── SmsParser.kt          # 验证码识别与提取
├── Forwarder.kt          # HTTP 转发（飞书加签 + Bearer + 失败重试）
├── SettingsStore.kt      # 配置存储(同步写盘) + 去重缓存
├── SecretCrypto.kt       # Android Keystore AES-GCM 密钥加密
├── KeepAliveHelper.kt    # 各厂商自启动/后台设置页适配
├── ForwardService.kt     # 常驻前台服务(specialUse)：防息屏冻结
├── BootReceiver.kt       # 开机自启拉起服务
├── TestSmsReceiver.kt    # adb 模拟短信联调入口(uid 校验)
└── MainActivity.kt       # 设置界面(自动保存/读回验证/品牌跳转)
```

## Roadmap

- [ ] 多通道：钉钉 / 企业微信 / Telegram / Bark
- [ ] 按 Sender 白名单/黑名单过滤
- [ ] 转发历史本地记录页
- [ ] targetSdk 35 + edge-to-edge 适配

## 致谢

功能更全的同类项目（支持飞书/钉钉/企微/TG 等）：[pppscn/SmsForwarder](https://github.com/pppscn/SmsForwarder)。本项目适合"代码可控、功能刚好够用、可完整审计"的场景。

## License

[MIT](LICENSE)
