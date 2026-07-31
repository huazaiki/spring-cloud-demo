#!/usr/bin/env bash
#
# 将 nacos-config/*.yml 批量发布到 Nacos 配置中心。
#
# 用法:
#   ./import-to-nacos.sh                       # 默认 localhost:8848, 无鉴权
#   ./import-to-nacos.sh 192.168.1.10:8848     # 指定 Nacos 地址
#   NACOS_USER=nacos NACOS_PASSWORD=nacos ./import-to-nacos.sh   # 开启鉴权时
#
set -euo pipefail

NACOS_ADDR="${1:-localhost:8848}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
TYPE="yaml"
DIR="$(cd "$(dirname "$0")" && pwd)"

# 鉴权（docker-compose 默认 NACOS_AUTH_ENABLE=false，可跳过）
QUERY=""
if [[ -n "${NACOS_USER:-}" && -n "${NACOS_PASSWORD:-}" ]]; then
  TOKEN=$(curl -fsS -X POST "http://${NACOS_ADDR}/nacos/v1/auth/login" \
    --data-urlencode "username=${NACOS_USER}" \
    --data-urlencode "password=${NACOS_PASSWORD}" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  [[ -z "${TOKEN}" ]] && { echo "登录失败，请检查 NACOS_USER/NACOS_PASSWORD"; exit 1; }
  QUERY="?accessToken=${TOKEN}"
fi

echo "发布配置到 http://${NACOS_ADDR}  (group=${GROUP})"
echo "----------------------------------------"

shopt -s nullglob
count=0
for file in "$DIR"/*.yml; do
  dataId="$(basename "$file")"
  printf '  -> %-24s ... ' "${dataId}"
  resp=$(curl -fsS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs${QUERY}" \
    --data-urlencode "dataId=${dataId}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "type=${TYPE}" \
    --data-urlencode "content@${file}")
  if [[ "${resp}" == "true" ]]; then
    echo "OK"
    count=$((count + 1))
  else
    echo "FAILED: ${resp}"
    exit 1
  fi
done

echo "----------------------------------------"
echo "完成，共发布 ${count} 个配置。"
echo "控制台核对: http://${NACOS_ADDR%%:*}:9090  (docker-compose 将 9090 映射到 Nacos 控制台 8080)"
echo "OpenAPI:    http://${NACOS_ADDR}/nacos"
