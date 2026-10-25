#!/usr/bin/env python3
"""生成每日简报（纯本地脚本，无任何网络请求，聊天内容不离开本机）。

数据来源：wechat-chatlog 工具链（chatlog_dir）的导出与快照。
输出：<brief_dir>/YYYY-MM-DD.md（目录见 pipeline.local.json）

内容：数据状态 / 昨日按身份消息统计 / 昨日对方提问 / 待办线索。
"""
import csv
import os
import sys
from collections import Counter
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipeline_config  # noqa: E402
from mine_v2 import classify, is_question, is_todo, EXPORT  # noqa: E402

_cfg = pipeline_config.load()
DATA_DB = os.path.join(pipeline_config.chatlog_dir(),
                       "data/db_storage/message/message_0.db")
BRIEF_DIR = pipeline_config.expand(_cfg["brief_dir"])
RECORDING_DIR = pipeline_config.expand(_cfg.get("recording_dir") or "")
LARK_MCP_BIN = pipeline_config.expand(_cfg.get("lark_mcp_bin") or "")

def main():
    target = (date.today() - timedelta(days=1)).isoformat()  # 昨天
    os.makedirs(BRIEF_DIR, exist_ok=True)
    out = os.path.join(BRIEF_DIR, f"{date.today().isoformat()}.md")

    # 数据新鲜度
    age_days = None
    if os.path.exists(DATA_DB):
        mtime = datetime.fromtimestamp(os.path.getmtime(DATA_DB))
        age_days = (datetime.now() - mtime).days
        data_line = "数据快照：{}（{}天前拉取）".format(
            mtime.strftime("%Y-%m-%d %H:%M"), age_days)
    else:
        data_line = "数据快照：无"

    overview = os.path.join(EXPORT, "总览.csv")
    if not os.path.isfile(overview):
        open(out, "w", encoding="utf-8").write(
            "# 每日简报 %s\n\n⚠️ 尚无导出数据，请先运行 export_all.py\n" % date.today())
        print("no export")
        return

    chats = list(csv.DictReader(open(overview, encoding="utf-8-sig")))
    ident_msgs = Counter()
    qs, todos = [], []
    for c in chats:
        ident = classify(c["会话名"])
        if ident is None:
            continue
        p = os.path.join(EXPORT, c["目录"], "聊天记录.csv")
        if not os.path.isfile(p):
            continue
        with open(p, encoding="utf-8-sig") as fh:
            for row in csv.DictReader(fh):
                t = row.get("时间", "")
                if not t.startswith(target):
                    continue
                if row.get("类型") != "text":
                    continue
                sender = (row.get("发送者") or "").strip()
                text = (row.get("内容") or "").strip()
                if not text or sender in ("我", "系统", ""):
                    continue
                ident_msgs[ident] += 1
                rec = (c["会话名"], t[11:16], text)
                if is_question(text):
                    qs.append((ident, *rec))
                if is_todo(text):
                    todos.append((ident, *rec))

    L = []
    ap = L.append
    ap("# 每日简报 · %s\n" % date.today().isoformat())
    ap("- 覆盖日期：%s\n" % target)
    ap("- %s" % data_line)
    if age_days and age_days >= 2:
        ap("- ⚠️ 快照已 %d 天未更新：夜间拉取可能失败（副本微信被更新/会话失效），"
           "可手动重跑 `bash pull_nightly.sh`\n" % age_days)
    else:
        ap("")
    ap("## 昨日消息（按身份，仅统计对方发来的）\n")
    ap("| 身份 | 对方消息数 |")
    ap("|---|---|")
    for k, v in ident_msgs.most_common():
        ap("| %s | %d |" % (k, v))
    if not ident_msgs:
        ap("| （昨日无对方消息） | 0 |")

    ap("\n## 待办线索（%d 条，按时间正序）\n" % len(todos))
    for ident, name, hm, text in sorted(todos, key=lambda x: x[2])[:40]:
        ap("- **[%s]** %s %s —— %s" % (ident, name, hm, text.replace("\n", " ")[:120]))
    if not todos:
        ap("（无）")

    ap("\n## 昨日对方提问（%d 条）\n" % len(qs))
    for ident, name, hm, text in sorted(qs, key=lambda x: (x[0], x[2]))[:60]:
        ap("- **[%s]** %s %s —— %s" % (ident, name, hm, text.replace("\n", " ")[:120]))
    if not qs:
        ap("（无）")

    ap("\n## 改进措施与待归类\n")
    un = [(c["会话名"], int(c["消息数"])) for c in chats
          if c["类型"] == "群聊" and classify(c["会话名"]) == "私聊/其他"]
    un.sort(key=lambda x: -x[1])
    if un:
        ap("以下群当前归入「私聊/其他」，若是工作/业务群请告知，我会调整归类规则：")
        for name, n in un[:10]:
            ap("- %s（%d 条）" % (name, n))
    else:
        ap("（无待归类群）")
    if _cfg.get("rules_file"):
        ap("- 归类规则变更记录：见 %s" % _cfg["rules_file"])

    ap("\n## 昨日新转写录音\n")
    if RECORDING_DIR and os.path.isdir(RECORDING_DIR):
        found = []
        for fn in sorted(os.listdir(RECORDING_DIR)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(RECORDING_DIR, fn)
            mt = datetime.fromtimestamp(os.path.getmtime(fp))
            if mt.strftime("%Y-%m-%d") == target:
                found.append("%s（%s 转写完成）" % (fn[:-3], mt.strftime("%H:%M")))
        for it in found:
            ap("- %s" % it)
        if not found:
            ap("（无）")
    else:
        ap("（未配置转写目录）")

    ap("\n## 信息源状态\n")
    ap("- 微信：%s" % ("已接入" if os.path.exists(DATA_DB) else "未接入"))
    if LARK_MCP_BIN:
        try:
            import subprocess
            r = subprocess.run([LARK_MCP_BIN, "whoami"],
                               capture_output=True, text=True, timeout=30)
            expired = "Expired: true" in (r.stdout + r.stderr)
            ap("- 飞书用户令牌：%s" % ("已过期，重跑 lark-mcp login 即可" if expired
                                     else "有效"))
        except Exception:
            ap("- 飞书用户令牌：无法检测")
    else:
        ap("- 飞书用户令牌：未配置 lark_mcp_bin，跳过检测")

    open(out, "w", encoding="utf-8").write("\n".join(L))
    print("brief ->", out)
    print("昨日对方消息:", dict(ident_msgs))

if __name__ == "__main__":
    main()
