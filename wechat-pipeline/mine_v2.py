#!/usr/bin/env python3
"""挖掘 v2：按身份分组的工作信号提取。

身份映射与排除规则放在 ``pipeline.local.json``（本机私有，不进 Git；
字段见 ``pipeline.example.json``），把会话名按关键词映射到身份，
其余归入「私聊/其他」。排除的会话不进入统计与报告。

数据来源：wechat-chatlog 工具链（chatlog_dir）导出的 ``export/`` 目录。
输出：<topic_dir>/挖掘v2-按身份-<日期>.md
"""
import csv
import os
import re
from collections import Counter, defaultdict
from datetime import date

import pipeline_config

EXPORT = os.path.join(pipeline_config.chatlog_dir(), "export")

_cfg = pipeline_config.load()
TODAY = date.today().isoformat()
OUT_DIR = pipeline_config.expand(_cfg["topic_dir"])
OUT_FILE = os.path.join(OUT_DIR, f"挖掘v2-按身份-{TODAY}.md")

# ---- 排除正则（空字符串必须当 None，re.compile("") 会匹配一切）----
EXCLUDE_PAT = re.compile(_cfg["exclude_pat"]) if _cfg["exclude_pat"] else None

# ---- 身份映射：[["身份名", "会话名匹配正则"], ...] ----
IDENTITY_PATTERNS = [(name, re.compile(pat)) for name, pat in _cfg["identity_patterns"]]

# 报告小节顺序：配置指定，否则按 identity_patterns 顺序 + 私聊/其他
IDENTITY_ORDER = _cfg["identity_order"] or [name for name, _ in IDENTITY_PATTERNS] + ["私聊/其他"]

Q_WORDS = ("怎么", "怎样", "为什么", "啥", "能不能", "可不可以", "有没有",
           "是不是", "哪个", "多少", "什么", "请问", "怎么办", "咋", "何时")
TODO_WORDS = ("记得", "别忘了", "别忘", "尽快", "今天之内", "明天", "下午", "上午",
              "deadline", "截止", "之前要", "提前", "确认一下", "麻烦", "下周", "本周")

def classify(name: str):
    if EXCLUDE_PAT and EXCLUDE_PAT.search(name):
        return None
    for ident, pat in IDENTITY_PATTERNS:
        if pat.search(name):
            return ident
    return "私聊/其他"

def is_question(t: str) -> bool:
    if not (4 <= len(t) <= 300):
        return False
    if t.startswith(("http", "【", "[")):
        return False
    if re.fullmatch(r"[\W_]+", t or ""):
        return False
    if "？" in t or "?" in t:
        return True
    return t.startswith(Q_WORDS) or any(w in t[:6] for w in Q_WORDS)

def is_todo(t: str) -> bool:
    return any(w in t for w in TODO_WORDS) and 4 <= len(t) <= 300

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    overview = os.path.join(EXPORT, "总览.csv")
    chats = list(csv.DictReader(open(overview, encoding="utf-8-sig")))

    ident_msgs = Counter()          # 身份 -> 对方消息数
    ident_qs = defaultdict(list)    # 身份 -> [(会话, 时间, 文本)]
    ident_todos = defaultdict(list) # 身份 -> [(会话, 时间, 文本)]
    ident_chats = defaultdict(list) # 身份 -> [(会话名, 消息数)]
    excluded = []
    total_msgs = 0

    for c in chats:
        total_msgs += int(c["消息数"])
        name = c["会话名"]
        ident = classify(name)
        if ident is None:
            excluded.append((name, int(c["消息数"])))
            continue
        ident_chats[ident].append((name, int(c["消息数"])))
        csv_path = os.path.join(EXPORT, c["目录"], "聊天记录.csv")
        if not os.path.isfile(csv_path):
            continue
        with open(csv_path, encoding="utf-8-sig") as fh:
            for row in csv.DictReader(fh):
                if row.get("类型") != "text":
                    continue
                sender = (row.get("发送者") or "").strip()
                text = (row.get("内容") or "").strip()
                if not text or sender in ("我", "系统", ""):
                    continue
                ident_msgs[ident] += 1
                t = row.get("时间", "")
                if is_question(text) and len(ident_qs[ident]) < 500:
                    ident_qs[ident].append((name, t, text))
                if is_todo(text) and len(ident_todos[ident]) < 300:
                    ident_todos[ident].append((name, t, text))

    L = []
    ap = L.append
    ap(f"# 挖掘 v2 · 按身份分组（{TODAY}）\n")
    ap("> 总量：%d 会话 / %s 条消息；已排除 %d 个会话（%s 条消息）\n" %
       (len(chats), f"{total_msgs:,}", len(excluded),
        f"{sum(n for _, n in excluded):,}"))

    for ident in IDENTITY_ORDER:
        ap(f"\n## {ident}")
        cs = sorted(ident_chats.get(ident, []), key=lambda x: -x[1])
        ap("\n会话（%d 个，按活跃度）：" % len(cs))
        for name, n in cs[:20]:
            ap("- %s（%d 条）" % (name, n))
        qs = ident_qs.get(ident, [])
        ap("\n对方提问样本（%d 条中前 30）：\n" % len(qs))
        seen = set()
        shown = 0
        for name, t, text in qs:
            k = text[:18]
            if k in seen:
                continue
            seen.add(k)
            ap("- (%s · %s) %s" % (name, t[:16], text.replace("\n", " ")[:150]))
            shown += 1
            if shown >= 30:
                break
        todos = ident_todos.get(ident, [])
        ap("\n待办线索（%d 条中前 20）：\n" % len(todos))
        seen = set()
        shown = 0
        for name, t, text in todos:
            k = (name, text[:18])
            if k in seen:
                continue
            seen.add(k)
            ap("- (%s · %s) %s" % (name, t[:16], text.replace("\n", " ")[:150]))
            shown += 1
            if shown >= 20:
                break
        ap("")

    ap("\n## 被排除的会话（%d 个）\n" % len(excluded))
    for name, n in sorted(excluded, key=lambda x: -x[1])[:15]:
        ap("- %s（%d 条）" % (name, n))

    open(OUT_FILE, "w", encoding="utf-8").write("\n".join(L))
    print("身份消息量:", dict(ident_msgs))
    print("提问量:", {k: len(v) for k, v in ident_qs.items()})
    print("排除群数:", len(excluded))
    print("输出:", OUT_FILE)

if __name__ == "__main__":
    main()
