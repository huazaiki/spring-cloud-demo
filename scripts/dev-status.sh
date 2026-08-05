#!/usr/bin/env bash
# dev-status.sh — 查看全栈运行状态
# 用法：./scripts/dev-status.sh
set -u

BASE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$BASE"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ok()  { echo -e "  ${GREEN}●${NC} $1"; }
bad() { echo -e "  ${RED}●${NC} $1"; }

echo -e "${CYAN}=== Docker 基础设施 ===${NC}"
CONTAINERS=('sc-mysql' 'sc-nacos' 'sc-kafka' 'sc-zipkin' 'sc-seata-server')
all_up=true
for c in "${CONTAINERS[@]}"; do
  status=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo 'not found')
  if [[ "$status" == "running" ]]; then
    ok "$c — running"
  else
    bad "$c — $status"
    all_up=false
  fi
done
$all_up && echo -e "  ${GREEN}基础设施全部在线${NC}" || echo -e "  ${RED}部分基础设施未启动${NC}"

echo ""
echo -e "${CYAN}=== 6 个微服务 (java) ===${NC}"
SERVICES=('sc-auth-service:8081' 'sc-supplier-service:8082' 'sc-purchase-service:8083' 'sc-inventory-service:8084' 'sc-payment-service:8085' 'sc-gateway-service:8080')
all_up=true
for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  port="${entry##*:}"
  # 检查进程是否存在
  pid=$(ps -eo pid,args | grep "[j]ava.*${svc}" | awk '{print $1}' | head -1 || true)
  if [[ -n "$pid" ]]; then
    # 检查健康端点
    health=$(curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health" 2>/dev/null || echo '---')
    if [[ "$health" == "200" ]]; then
      ok "${svc} (pid ${pid}, port ${port}) — UP"
    else
      echo -e "  ${YELLOW}●${NC} ${svc} (pid ${pid}, port ${port}) — STARTING (health: ${health})"
    fi
  else
    bad "${svc} (port ${port}) — DOWN"
    all_up=false
  fi
done
$all_up && echo -e "  ${GREEN}所有微服务在线${NC}" || echo -e "  ${RED}部分微服务未启动${NC}"

echo ""
echo -e "${CYAN}=== 前端 (vite) ===${NC}"
pid=$(ps -eo pid,args | grep -E '[v]ite.*frontend-demo' | awk '{print $1}' | head -1 || true)
if [[ -n "$pid" ]]; then
  vite_code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' http://localhost:5173/ 2>/dev/null || echo '---')
  if [[ "$vite_code" =~ ^(200|301|302|304)$ ]]; then
    ok "前端 vite (pid ${pid}) — http://localhost:5173"
  else
    echo -e "  ${YELLOW}●${NC} 前端 vite (pid ${pid}) — STARTING (HTTP: ${vite_code})"
  fi
else
  bad "前端 — DOWN"
fi

echo ""
echo -e "${CYAN}=== 关键端口 ==="
echo "  前端    : http://localhost:5173"
echo "  网关    : http://localhost:8080"
echo "  Nacos   : http://localhost:9090 (控制台) / http://localhost:8848 (gRPC API)"
echo "  Zipkin  : http://localhost:9411"
echo "  Seata   : http://localhost:7091 (seata/seata)"
echo "  MySQL   : localhost:3306 (root/root@123!)"
echo "  Kafka   : localhost:9092"
