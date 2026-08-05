#!/usr/bin/env bash
# dev-up.sh — 一键启动全栈（基础设施 + 6 服务 + 前端）
# 用法：./scripts/dev-up.sh
set -euo pipefail

BASE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$BASE"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

SERVICES=('sc-auth-service' 'sc-supplier-service' 'sc-purchase-service' 'sc-inventory-service' 'sc-payment-service' 'sc-gateway-service')
PORTS=(8081 8082 8083 8084 8085 8080)
PIDS=()

echo -e "${CYAN}== 1/5 构建服务 jar（缺则构建）==${NC}"
NEED_BUILD=false
for svc in "${SERVICES[@]}"; do
  if [[ ! -f "$BASE/$svc/target/${svc}-0.0.1-SNAPSHOT-exec.jar" ]]; then
    NEED_BUILD=true
    break
  fi
done
if $NEED_BUILD; then
  MVN_REPO="${HOME}/.m2/repository"
  mvn -Dmaven.repo.local="$MVN_REPO" -pl sc-auth-service,sc-supplier-service,sc-purchase-service,sc-inventory-service,sc-payment-service,sc-gateway-service -am clean package -DskipTests
else
  echo "  所有 jar 已存在，跳过构建"
fi

echo -e "${CYAN}== 2/5 启动基础设施（MySQL/Nacos/Kafka/Zipkin/Seata）==${NC}"
docker compose up -d
echo "等待 MySQL/Nacos 就绪..."
for i in $(seq 1 40); do
  sleep 5
  MYSQL_OK=$(docker exec sc-mysql mysqladmin ping -h localhost -uroot -proot@123! 2>/dev/null | grep -c 'alive' || true)
  NACOS_CODE=$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://localhost:9090/ 2>/dev/null || echo '000')
  if [[ "$MYSQL_OK" -ge 1 && "$NACOS_CODE" == "200" ]]; then
    echo "  就绪（第 $i 轮）"
    break
  fi
done

echo -e "${CYAN}== 3/5 导入 Nacos 配置 ==${NC}"
bash "$BASE/nacos-config/import-to-nacos.sh"

echo -e "${CYAN}== 4/5 创建 Kafka topics（幂等）==${NC}"
docker exec sc-kafka sh -c '
  for t in __consumer_offsets sc.inventory.stock-in.completed sc.purchase.order.cancelled sc.payment.settlement.completed; do
    /opt/kafka/bin/kafka-topics.sh --create --if-not-exists --topic $t --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092 >/dev/null 2>&1
  done
  echo "  topics ok"
'

echo -e "${CYAN}== 5/5 启动 6 个服务 + 前端 ==${NC}"
mkdir -p "$BASE/logs"
for i in "${!SERVICES[@]}"; do
  svc="${SERVICES[$i]}"
  jar="$BASE/$svc/target/${svc}-0.0.1-SNAPSHOT-exec.jar"
  log="$BASE/logs/${svc}.log"
  nohup java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "$jar" > "$log" 2>&1 &
  PIDS+=($!)
  echo "  ${svc} 启动 (pid ${PIDS[-1]})"
done

# 启动前端
nohup npm run dev -- --host > "$BASE/logs/frontend-dev.log" 2>&1 &
FRONTEND_PID=$!
echo "  前端启动 (pid ${FRONTEND_PID})"

echo -e "\n${CYAN}等待服务健康（约 1-2 分钟）...${NC}"
sleep 60
for i in "${!SERVICES[@]}"; do
  svc="${SERVICES[$i]}"
  port="${PORTS[$i]}"
  code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health" 2>/dev/null || echo '---')
  echo "  ${svc} : ${port} => ${code}"
done

echo -e "\n${GREEN}✅ 完成${NC}"
echo "  前端    : http://localhost:5173  (账号 admin / Passw0rd，或注册后由管理员分配角色)"
echo "  网关    : http://localhost:8080"
echo "  Nacos   : http://localhost:9090"
echo "  Zipkin  : http://localhost:9411"
echo "  Seata   : http://localhost:7091 (seata/seata)"
