# SmsCodeForwarder

Forwards incoming **verification-code SMS** on Android to a **Lark/Feishu group bot webhook** (recommended, free, no server needed) or **your own HTTP endpoint**. Built for the "second phone receives the OTP, you read it on your primary phone/desktop" scenario.

[中文文档](README.md) · [Privacy](PRIVACY.md) · [Changelog](CHANGELOG.md)

## Highlights

- **Zero third-party dependencies** — pure Android SDK, ~800KB APK, fully auditable
- **Zero data collection** — no analytics, no ads, no SDKs; settings stored locally, webhook secret encrypted with Android Keystore (AES-256-GCM)
- **Chinese OEM keep-alive adaptation** — detects Xiaomi/Huawei/Honor/OPPO/OnePlus/Realme/vivo/iQOO/Samsung/Meizu and deep-links to each vendor's autostart/background settings page (the #1 cause of missed forwards is background process freezing)
- **Engineering details** — long-SMS concatenation, 10-min dedup, weak-network retry (3 attempts), write-and-read-back config verification, ADB-based SMS simulation for testing

Android 7.0+ only (iOS does not expose SMS to third parties).

## Build

JDK 17+ required; the repo ships a Gradle wrapper:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Every push also builds an APK artifact via GitHub Actions.

## Custom endpoint protocol

```
POST <your-url>
Content-Type: application/json
Authorization: Bearer <your-secret>    (omitted if empty)
```

```json
{
  "type": "sms",
  "from": "1069012345678",
  "code": "123456",
  "body": "...",
  "receivedAt": 1760000000000,
  "receivedAtText": "2026-10-19 12:30:45",
  "device": "Xiaomi 2210132C"
}
```

`code` is present only when the message is recognized as a verification SMS. Always validate the Authorization header server-side.

## Development

Simulate an SMS without costing a real message (goes through the exact same filter/dedup/forward pipeline; only ADB/system/the app itself can trigger it since v1.4):

```bash
adb shell am broadcast -a com.wj.smsforwarder.SIMULATE_SMS \
  --es from 10086 --es body "[TEST] Your code is 123456"
```

## Acknowledgments

For a feature-richer alternative (DingTalk/WeCom/Telegram/etc.) see [pppscn/SmsForwarder](https://github.com/pppscn/SmsForwarder). This project targets the "auditable, just-enough-features" niche.

## License

[MIT](LICENSE)
