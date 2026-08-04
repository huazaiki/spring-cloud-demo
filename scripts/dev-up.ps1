# dev-up.ps1 — 一键启动全栈（基础设施 + 6 服务 + 前端）
# 用法：powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $PSScriptRoot
Set-Location $base

Write-Host "== 1/5 构建服务 jar（缺则构建）==" -ForegroundColor Cyan
$needBuild = $false
$services = @('sc-auth-service','sc-supplier-service','sc-purchase-service','sc-inventory-service','sc-payment-service','sc-gateway-service')
foreach ($svc in $services) {
  if (-not (Test-Path "$base\$svc\target\$svc-0.0.1-SNAPSHOT-exec.jar")) { $needBuild = $true }
}
if ($needBuild) {
  mvn "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -pl sc-auth-service,sc-supplier-service,sc-purchase-service,sc-inventory-service,sc-payment-service,sc-gateway-service -am clean package -DskipTests
}

Write-Host "== 2/5 启动基础设施（MySQL/Nacos/Kafka/Zipkin/Seata）==" -ForegroundColor Cyan
docker compose up -d
Write-Host "等待 MySQL/Nacos 就绪..."
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 5
  $mysqlOk = docker exec sc-mysql mysqladmin ping -h localhost -uroot -proot@123! 2>$null | Select-String 'alive'
  $nacosOk = curl -s -m 3 -o /dev/null -w '%{http_code}' http://localhost:9090/ 2>$null
  if ($mysqlOk -and $nacosOk -eq '200') { Write-Host "  就绪（第 $i 轮）"; break }
}

Write-Host "== 3/5 导入 Nacos 配置 ==" -ForegroundColor Cyan
Get-ChildItem "$base\nacos-config\*.yml" | ForEach-Object {
  $resp = curl -s -m 15 -X POST "http://localhost:8848/nacos/v1/cs/configs" `
    --data-urlencode "dataId=$($_.Name)" --data-urlencode "group=DEFAULT_GROUP" `
    --data-urlencode "type=yaml" --data-urlencode "content@$($_.FullName)"
  Write-Host "  $($_.Name) -> $resp"
}

Write-Host "== 4/5 创建 Kafka topics（幂等）==" -ForegroundColor Cyan
docker exec sc-kafka sh -c '
  for t in __consumer_offsets sc.inventory.stock-in.completed sc.purchase.order.cancelled sc.payment.settlement.completed; do
    /opt/kafka/bin/kafka-topics.sh --create --if-not-exists --topic $t --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092 >/dev/null 2>&1
  done
  echo "  topics ok"
'

Write-Host "== 5/5 启动 6 个服务 + 前端 ==" -ForegroundColor Cyan
$ports = @{ 'sc-gateway-service'=8080; 'sc-auth-service'=8081; 'sc-supplier-service'=8082; 'sc-purchase-service'=8083; 'sc-inventory-service'=8084; 'sc-payment-service'=8085 }
New-Item -ItemType Directory -Path "$base\logs" -Force | Out-Null
foreach ($svc in @('sc-auth-service','sc-supplier-service','sc-purchase-service','sc-inventory-service','sc-payment-service','sc-gateway-service')) {
  $jar = "$base\$svc\target\$svc-0.0.1-SNAPSHOT-exec.jar"
  $log = "$base\logs\$svc.log"
  Start-Process -FilePath 'java' -ArgumentList @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-jar',$jar) `
    -WorkingDirectory $base -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden
  Write-Host "  $svc 启动"
}
Start-Process -FilePath 'npm.cmd' -ArgumentList @('run','dev','--','--host') `
  -WorkingDirectory "$base\frontend-demo" -RedirectStandardOutput "$base\logs\frontend-dev.log" `
  -RedirectStandardError "$base\logs\frontend-dev.log.err" -WindowStyle Hidden
Write-Host "  前端启动"

Write-Host "`n等待服务健康（约 1-2 分钟）..." -ForegroundColor Cyan
Start-Sleep -Seconds 60
foreach ($svc in $ports.Keys) {
  $code = curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:$($ports[$svc])/actuator/health" 2>$null
  Write-Host "  $svc : $($ports[$svc]) => $code"
}

Write-Host "`n✅ 完成" -ForegroundColor Green
Write-Host "  前端    : http://localhost:5173  (账号 admin / Passw0rd，或注册后由管理员分配角色)"
Write-Host "  网关    : http://localhost:8080"
Write-Host "  Nacos   : http://localhost:9090"
Write-Host "  Zipkin  : http://localhost:9411"
Write-Host "  Seata   : http://localhost:7091 (seata/seata)"