#!/usr/bin/env bash
# 端到端验收：登录 → 建环境 → 建开关 → 配规则 → 按 IP 设值 → SDK 分流 → 改配置秒级生效 → 历史与回滚
# 前置：MySQL 与 liveconfig-server 已启动（scripts/run-all.sh）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SERVER="${LIVECONFIG_SERVER:-http://localhost:8080}"
PASS=0
FAIL=0

check() {  # check <说明> <期望> <实际>
  if [ "$2" = "$3" ]; then
    printf "  \033[32m✓\033[0m %-46s %s\n" "$1" "$3"; PASS=$((PASS+1))
  else
    printf "  \033[31m✗\033[0m %-46s 期望 %s / 实际 %s\n" "$1" "$2" "$3"; FAIL=$((FAIL+1))
  fi
}

jget() { python3 -c "import sys,json;d=json.load(sys.stdin)
for k in sys.argv[1].split('.'):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)" "$1"; }

echo "== 1. 认证"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$SERVER/api/apps")
check "未登录访问管理接口" "401" "$CODE"
TOKEN=$(curl -s -X POST "$SERVER/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"zhangsan@example.com","password":"demo1234"}' | jget "data.token")
AUTH="Authorization: Bearer $TOKEN"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SERVER/api/auth/login" \
  -H 'Content-Type: application/json' -d '{"email":"zhangsan@example.com","password":"wrong"}')
check "口令错误" "401" "$CODE"

echo "== 2. 环境与 SDK Key"
EKEY="e2e-$(date +%s)"
CREATED=$(curl -s -X POST "$SERVER/api/apps/order-service/envs" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"envKey\":\"$EKEY\",\"color\":\"#0ea5e9\",\"highRisk\":false}")
SDK_KEY=$(echo "$CREATED" | jget "data.serverKeyPlain")
check "创建环境返回明文 Key" "true" "$([ -n "$SDK_KEY" ] && echo true || echo false)"
MASKED=$(echo "$CREATED" | jget "data.env.serverKeyMasked")
check "列表里只给掩码（含省略号）" "true" "$(echo "$MASKED" | grep -q '…' && echo true || echo false)"

echo "== 3. 开关与类型校验"
FID=$(curl -s -X POST "$SERVER/api/flags" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"appKey\":\"order-service\",\"envKey\":\"$EKEY\",\"flagKey\":\"e2e_checkout\",\"valueType\":\"BOOLEAN\",\"defaultValue\":\"false\",\"name\":\"E2E 结算页\"}" \
  | jget "data.id")
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SERVER/api/flags" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"appKey\":\"order-service\",\"envKey\":\"$EKEY\",\"flagKey\":\"e2e_bad\",\"valueType\":\"JSON\",\"defaultValue\":\"true\"}")
check "JSON 开关填标量被拒" "400" "$CODE"

curl -s -o /dev/null -X PUT "$SERVER/api/flags/$FID/rules" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"rules":[{"kind":"WHITELIST","priority":0,"config":"{\"userIds\":[\"e2e_vip\"]}","value":"true"},{"kind":"ROLLOUT","priority":0,"config":"{\"percent\":40,\"bucketBy\":\"USER_ID\"}","value":"true"}]}'

echo "== 4. SDK 分流"
hit() { curl -s -X POST "$SERVER/sdk/v1/evaluate" -H "X-SDK-Key: $SDK_KEY" -H 'Content-Type: application/json' \
  -d "{\"flagKey\":\"e2e_checkout\",\"userId\":\"$1\"}" | jget "data.hitRule"; }
check "白名单用户" "WHITELIST" "$(hit e2e_vip)"
check "无 userId 也不报错" "DEFAULT" "$(hit '')"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SERVER/sdk/v1/evaluate" \
  -H "X-SDK-Key: bad-key" -H 'Content-Type: application/json' -d '{"flagKey":"e2e_checkout"}')
check "错误 SDK Key" "401" "$CODE"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$SERVER/api/flags?appKey=order-service&envKey=$EKEY" -H "X-SDK-Key: $SDK_KEY")
check "SDK Key 不能访问管理接口" "401" "$CODE"

echo "== 5. 服务器定向 + 秒级生效"
curl -s -o /dev/null -X PUT "$SERVER/api/flags/$FID/instances" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"privateIp":"10.99.99.99","publicIp":"47.96.99.99","value":"true"}'
RULE=$(curl -s -X POST "$SERVER/sdk/v1/evaluate" -H "X-SDK-Key: $SDK_KEY" -H 'Content-Type: application/json' \
  -d '{"flagKey":"e2e_checkout","userId":"nobody","instance":{"privateIp":"10.99.99.99"}}' | jget "data.hitRule")
check "定向 IP 命中最高优先级" "INSTANCE_OVERRIDE" "$RULE"

# 另建一个不带任何规则的开关，专门验证「默认值改动即刻生效」：
# 带 ROLLOUT 的开关即使命中也返回规则值，看不出默认值变化
DFLAG="e2e_default_$(date +%s)"
DFID=$(curl -s -X POST "$SERVER/api/flags" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"appKey\":\"order-service\",\"envKey\":\"$EKEY\",\"flagKey\":\"$DFLAG\",\"valueType\":\"BOOLEAN\",\"defaultValue\":\"false\",\"name\":\"E2E 默认值\"}" \
  | jget "data.id")
dval() { curl -s -X POST "$SERVER/sdk/v1/evaluate" -H "X-SDK-Key: $SDK_KEY" -H 'Content-Type: application/json' \
  -d "{\"flagKey\":\"$DFLAG\",\"userId\":\"nobody\"}" | jget "data.value"; }
BEFORE=$(dval)
curl -s -o /dev/null -X PUT "$SERVER/api/flags/$DFID/default" -H "$AUTH" -H 'Content-Type: application/json' -d '{"value":"true"}'
AFTER=$(dval)
check "改默认值后立即生效（调用方未重启）" "false→true" "$BEFORE→$AFTER"
curl -s -o /dev/null -X PUT "$SERVER/api/flags/$DFID/default" -H "$AUTH" -H 'Content-Type: application/json' -d '{"value":"false"}'
check "改回 false 同样即刻生效" "false" "$(dval)"

echo "== 6. 历史与回滚"
# 取「值为 true」那条历史作为回滚目标（records 按时间倒序，第 2 条是改成 true 的那次）
LOG_ID=$(curl -s "$SERVER/api/history?appKey=order-service&envKey=$EKEY&flagKey=$DFLAG&pageSize=5" \
  -H "$AUTH" | jget data.records.1.id)
ACTION=$(curl -s -X POST "$SERVER/api/history/$LOG_ID/rollback" -H "$AUTH" | jget "data.action")
check "回滚追加一条 ROLLBACK 记录" "ROLLBACK" "$ACTION"
check "回滚后值回到上一条快照" "true" "$(dval)"
TOTAL=$(curl -s "$SERVER/api/history?appKey=order-service&envKey=$EKEY&flagKey=$DFLAG" -H "$AUTH" | jget "data.total")
check "历史条数（创建+两次改值+回滚）" "4" "$TOTAL"

echo
echo "通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ]
