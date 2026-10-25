#!/usr/bin/env python3
"""飞书推送脚本：从标准输入读取文本，发送给配置的本人 open_id。

用法：
  echo "消息内容" | python3 send_push.py

open_id 解析顺序：
  1. 环境变量 FEISHU_OPEN_ID
  2. pipeline.local.json 的 rules_file 指向的 markdown（匹配 `open_id：\`ou_…\``）
凭证解析顺序：
  1. 环境变量 FEISHU_APP_ID / FEISHU_APP_SECRET
  2. ~/.zcode/cli/config.json（本机 lark-mcp 同源配置，不落盘、不进命令行）
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipeline_config  # noqa: E402

HOME = os.path.expanduser("~")

def resolve_open_id():
    oid = os.environ.get("FEISHU_OPEN_ID", "").strip()
    if oid:
        return oid
    rules = pipeline_config.expand(pipeline_config.load().get("rules_file") or "")
    if rules and os.path.isfile(rules):
        m = re.search(r"open_id[：:]\s*`(ou_[0-9a-f]+)`", open(rules, encoding="utf-8").read())
        if m:
            return m.group(1)
    return None

def resolve_credentials():
    app_id = os.environ.get("FEISHU_APP_ID", "").strip()
    app_secret = os.environ.get("FEISHU_APP_SECRET", "").strip()
    if app_id and app_secret:
        return app_id, app_secret
    cfg_path = os.path.join(HOME, ".zcode/cli/config.json")
    if os.path.isfile(cfg_path):
        cfg = open(cfg_path, encoding="utf-8").read()
        m_id = re.search(r'"-a",\s*"(cli_[^"]+)"', cfg)
        m_sec = re.search(r'"-s",\s*"([^"]+)"', cfg)
        if m_id and m_sec:
            return m_id.group(1), m_sec.group(1)
    return None, None

def main():
    text = sys.stdin.read().strip()
    if not text:
        print("EMPTY_TEXT")
        return 1
    open_id = resolve_open_id()
    if not open_id:
        print("NO_OPEN_ID")
        return 1
    app_id, app_secret = resolve_credentials()
    if not app_id:
        print("NO_CREDENTIALS")
        return 1

    req = urllib.request.Request(
        "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
        data=json.dumps({"app_id": app_id, "app_secret": app_secret}).encode(),
        headers={"Content-Type": "application/json"})
    tok = json.load(urllib.request.urlopen(req, timeout=15))["tenant_access_token"]

    body = json.dumps({
        "receive_id": open_id,
        "msg_type": "text",
        "content": json.dumps({"text": text}, ensure_ascii=False),
    }).encode()
    req2 = urllib.request.Request(
        "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id",
        data=body,
        headers={"Content-Type": "application/json",
                 "Authorization": "Bearer " + tok})
    r = json.load(urllib.request.urlopen(req2, timeout=15))
    print("SEND_OK" if r.get("code") == 0 else "SEND_FAIL %s" % r.get("msg"))
    return 0 if r.get("code") == 0 else 1

if __name__ == "__main__":
    sys.exit(main())
