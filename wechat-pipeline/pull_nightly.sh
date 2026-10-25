#!/bin/bash
# 夜间数据拉取：
#   退出原版微信(单实例锁) → 启动工具链的重签副本 → 免密sudo克隆 → 退副本
#   → root发布失败时自动用用户身份发布 → 重新打开原版微信
# 副本微信无论是否登录都可附加（克隆只需进程在跑，不需要登录态）。
# 工具链位置：环境变量 CHATLOG_DIR 或 pipeline.local.json 的 chatlog_dir；
# 账号目录同样从 pipeline.local.json 读取（留空则由 clone_data.py 自动发现）。
set -u
SELF="$(cd "$(dirname "$0")" && pwd)"
TOOL="${CHATLOG_DIR:-$HOME/tools/wechat-chatlog}"
LOG="$TOOL/.runtime/nightly.log"
APP="$TOOL/.runtime/WeChatRun.app"
ORIG="/Applications/WeChat.app"
DATA="$TOOL/data"
DB="$DATA/db_storage/message/message_0.db"

echo "=== $(date '+%F %T') nightly pull start ===" >> "$LOG"

# 0. 记录数据旧时间戳，用于判断本次是否更新成功
OLD_MT=$(stat -f %m "$DB" 2>/dev/null || echo 0)

# 1. 原版微信在跑会占住单实例锁，先温和退出
ORIG_WAS_RUNNING=0
if pgrep -x WeChat >/dev/null 2>&1; then
  ORIG_WAS_RUNNING=1
  osascript -e 'tell application "WeChat" to quit' >/dev/null 2>&1
  for i in $(seq 1 10); do pgrep -x WeChat >/dev/null || break; sleep 1; done
  pgrep -x WeChat >/dev/null && pkill -TERM -x WeChat && sleep 3
  echo "已退出原版微信（结束后会重新打开）" >> "$LOG"
fi

# 2. 启动副本
open -n -a "$APP" 2>>"$LOG"
sleep 8
PID=""
for i in 1 2 3 4 5; do
  PID=$(pgrep -x WeChat | while read -r p; do
    ps -p "$p" -o command= 2>/dev/null | grep -q WeChatRun && echo "$p" && break
  done)
  [ -n "$PID" ] && break
  sleep 3
done

if [ -z "$PID" ]; then
  echo "PULL_FAIL: 副本微信未能启动（可能被微信更新破坏，需重新签名）" >> "$LOG"
  [ "$ORIG_WAS_RUNNING" = 1 ] && open -a "$ORIG"
  echo "PULL_FAIL"
  exit 1
fi

# 3. 免密克隆（脚本内部含密钥校验与发布）；账号来自 pipeline.local.json
WX_ACCOUNT=$(python3 -c \
  "import sys; sys.path.insert(0, '$SELF'); import pipeline_config as p; print(p.load().get('wx_account', ''))" \
  2>/dev/null || true)
ACCT_ARGS=()
if [ -n "${WX_ACCOUNT:-}" ]; then
  ACCT_ARGS=(--account "$WX_ACCOUNT")
fi
sudo -n "$TOOL/.venv/bin/python" \
  "$TOOL/scripts/clone_data.py" \
  --db-only "${ACCT_ARGS[@]}" >>"$LOG" 2>&1
RC=$?
pkill -TERM -f "WeChatRun.app/Contents/MacOS/WeChat" 2>/dev/null

# 4. 判断数据是否真的更新了
NEW_MT=$(stat -f %m "$DB" 2>/dev/null || echo 0)
if [ "$RC" = 0 ] && [ "$NEW_MT" -gt "$OLD_MT" ]; then
  echo "=== $(date '+%F %T') PULL_OK (root) ===" >> "$LOG"
  [ "$ORIG_WAS_RUNNING" = 1 ] && open -a "$ORIG"
  echo "PULL_OK"
  exit 0
fi

# 5. 兜底：root 发布被 TCC 拦住时，改用用户身份发布
STAGE=$(ls -dt "$HOME/Downloads/wx_clone_stage-"* 2>/dev/null | head -1)
if [ -n "$STAGE" ] && [ -d "$STAGE/db_storage" ]; then
  echo "root 发布失败，改用用户身份发布: $STAGE" >> "$LOG"
  mkdir -p "$DATA"
  rsync -a --delete "$STAGE/db_storage/" "$DATA/db_storage/" 2>>"$LOG" \
    && { [ -f "$STAGE/_snapshot.json" ] && cp "$STAGE/_snapshot.json" "$DATA/_snapshot.json"; } \
    && rm -rf "$STAGE"
  NEW_MT=$(stat -f %m "$DB" 2>/dev/null || echo 0)
  if [ "$NEW_MT" -gt "$OLD_MT" ]; then
    echo "=== $(date '+%F %T') PULL_OK (user fallback) ===" >> "$LOG"
    [ "$ORIG_WAS_RUNNING" = 1 ] && open -a "$ORIG"
    echo "PULL_OK"
    exit 0
  fi
fi

echo "=== $(date '+%F %T') PULL_FAIL rc=$RC ===" >> "$LOG"
[ "$ORIG_WAS_RUNNING" = 1 ] && open -a "$ORIG"
echo "PULL_FAIL"
exit 1
