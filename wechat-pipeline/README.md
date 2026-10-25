# 微信聊天记录夜间流水线 wechat-chatlog-pipeline

在你的 Mac 上，**每天凌晨自动**完成：拉取微信聊天数据快照 → 解密导出 → 按身份分组挖掘（对方提问 / 待办线索 / 活跃会话）→ 生成每日简报 → 可选飞书推送。

适合"副机/主力机收消息，第二天早上用 1 分钟了解所有工作信号"的场景。

```
每天 03:30（cron / launchd / 任务编排器）
  ① pull_nightly.sh     退微信 → 启动重签副本 → 免密 sudo 克隆最新数据库 → 恢复微信
  ② export_all.py       （工具链提供）SQLCipher 离线解密，导出 CSV/HTML
  ③ mine_v2.py          会话名 → 身份分组；提取对方提问与待办线索
  ④ build_daily_brief.py  昨日消息统计 / 待办 / 待归类群 / 信息源状态 → 每日简报
  （次日 07:00，可选）send_push.py  简报要点推送到飞书
```

## 特点

- **全程离线**：挖掘与日报只读本机导出的 CSV，不发起任何网络请求；`send_push.py` 是唯一外发点，且只发送你主动喂给它的文本。
- **零隐私内置**：账号、输出目录、身份规则全部放 `pipeline.local.json`（不进 Git），克隆下来先复制 `pipeline.example.json` 改成自己的。
- **鲁棒**：数据拉取失败不影响后续步骤（用最近一次快照继续）；日报自动标注数据新鲜度；root 发布被 TCC 拦截时自动降级用户身份发布。

## 依赖

- [tcsn-xy/wechat-chatlog](https://github.com/tcsn-xy/wechat-chatlog)（MIT）：负责密钥抓取、快照克隆与解密导出。本仓库只做它之上的**流水线编排与数据挖掘**，不包含其任何代码。
- 已在该工具链完成一次密钥抓取（`keys/capture.jsonl` 存在），详见其仓库文档。
- macOS + 免密 sudo 规则（仅限工具链的 `clone_data.py`，见下文"定时执行"）。

## 快速开始

```bash
# 本目录是 sms-code-forwarder 仓库的组件，克隆整个仓库后进入本目录：
git clone https://github.com/bugWu0421/sms-code-forwarder.git
cd sms-code-forwarder/wechat-pipeline
cp pipeline.example.json pipeline.local.json   # 改成你的目录与身份规则

# 四步流水线（TOOL = pipeline.local.json 里的 chatlog_dir）：
TOOL=~/tools/wechat-chatlog
bash pull_nightly.sh                                        # ① 拉快照
"$TOOL/.venv/bin/python" "$TOOL/scripts/export_all.py"      # ② 解密导出
python3 mine_v2.py                                          # ③ 身份挖掘
python3 build_daily_brief.py                                # ④ 每日简报
```

完整配置字段说明、定时任务接入、故障排查见 **[docs/夜间流水线.md](docs/夜间流水线.md)**。

## 隐私与边界

- 只处理**本人账号在本人设备**上的数据；请遵守当地法律与微信服务条款，不要用它导出或传播他人聊天记录。
- 身份规则、微信账号目录、输出目录全部在 `pipeline.local.json`，本仓库（含 Git 历史）不含任何个人信息。
- `export/`（聊天导出）、`data/`（快照）、密钥等敏感产物属于工具链的私有目录，永远不要提交或外发。

## 许可

[MIT](../LICENSE) © 2026 bugWu0421。依赖的 [wechat-chatlog](https://github.com/tcsn-xy/wechat-chatlog) 同为 MIT，感谢原作者。
