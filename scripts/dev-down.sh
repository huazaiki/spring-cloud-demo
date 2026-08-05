#!/usr/bin/env bash
# dev-down.sh — 停止全栈（前端 + 6 服务；可选 -i 停基础设施）
# 用法：./scripts/dev-down.sh        # 只停服务和前端
#       ./scripts/dev-down.sh -i     # 连 docker compose down 一起
set -euo pipefail

BASE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$BASE"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

INFRA=false
if [[ "${1:-}" == "-i" ]]; then
  INFRA=true
fi

SERVICES=('sc-auth-service' 'sc-supplier-service' 'sc-purchase-service' 'sc-inventory-service' 'sc-payment-service' 'sc-gateway-service')

echo -e "${CYAN}== 1/3 停止前端 (vite) ==${NC}"
stopped=0
# 通过进程命令行匹配 vite + frontend-demo 路径
while IFS= read -r pid; do
  kill "$pid" 2>/dev/null && echo "  已停止 vite pid $pid" && stopped=$((stopped + 1))
done < <(ps -eo pid,args | grep -E '[v]ite.*frontend-demo' | awk '{print $1}' || true)
if [[ $stopped -eq 0 ]]; then
  echo "  未发现运行中的前端进程"
fi

echo -e "${CYAN}== 2/3 停止 6 个服务 (java) ==${NC}"
stopped=0
for svc in "${SERVICES[@]}"; do
  # 查找命令行中包含该服务目录名的 java 进程
  while IFS= read -r pid; do
    kill "$pid" 2>/dev/null && echo "  已停止 java pid $pid ($svc)" && stopped=$((stopped + 1))
  done < <(ps -eo pid,args | grep "[j]ava.*${svc}" | awk '{print $1}' || true)
done
if [[ $stopped -eq 0 ]]; then
  echo "  未发现运行中的服务进程"
fi

if $INFRA; then
  echo -e "${CYAN}== 3/3 停止基础设施（保留数据卷）==${NC}"
  docker compose down
else
  echo -e "\n${CYAN}提示：使用 ./scripts/dev-down.sh -i 同时停止 Docker 基础设施${NC}"
fi

echo -e "\n${GREEN}✅ 已停止${NC}"
echo "重启：./scripts/dev-up.sh"
echo "注意：本脚本只停本项目进程（vite + 6 个服务 jar），不会误杀其他 node/java 进程。"
