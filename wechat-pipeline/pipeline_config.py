#!/usr/bin/env python3
"""夜间流水线共享配置。

优先级：环境变量 PIPELINE_CONFIG 指定的文件 → 仓库根目录的
``pipeline.local.json``（本机私有，不进 Git）→ 内置通用默认值。

字段说明见 ``pipeline.example.json``。所有脚本（mine_v2 / build_daily_brief /
send_push / pull_nightly.sh）统一从这里读配置，公开仓库里不携带任何
账号、群名、个人目录等本机信息。
"""
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))

# 通用默认值：不含任何个人信息，开箱即可用（全部落到仓库外的输出目录）
DEFAULTS = {
    "chatlog_dir": "~/tools/wechat-chatlog",  # wechat-chatlog 工具链检出位置
    "wx_account": "",        # 留空 = 由 clone_data.py 自动发现唯一账号目录
    "topic_dir": "~/wechat-chatlog-output/mining",
    "brief_dir": "~/wechat-chatlog-output/daily",
    "rules_file": "",        # send_push.py 从该 markdown 里解析 open_id（可留空）
    "recording_dir": "",     # 日报"新转写录音"一节的数据目录，留空则跳过
    "lark_mcp_bin": "",      # 日报检测飞书令牌用的 lark-mcp 可执行文件，留空则跳过
    "exclude_pat": "",       # 会话名排除正则，留空 = 一个都不排除
    "identity_patterns": [], # [["身份名", "会话名匹配正则"], ...]
    "identity_order": [],    # 报告小节顺序，留空 = identity_patterns 顺序 + 私聊/其他
}


def config_path() -> str:
    return os.environ.get("PIPELINE_CONFIG",
                          os.path.join(ROOT, "pipeline.local.json"))


def load() -> dict:
    cfg = dict(DEFAULTS)
    path = config_path()
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            cfg.update(json.load(fh))
    return cfg


def expand(path: str) -> str:
    """配置里的 ~ 展开为家目录；空值原样返回。"""
    return os.path.expanduser(path) if path else path


def chatlog_dir() -> str:
    """wechat-chatlog 工具链根目录（导出/密钥/虚拟环境都在它下面）。"""
    return expand(load()["chatlog_dir"])
