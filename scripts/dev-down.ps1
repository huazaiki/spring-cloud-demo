# dev-down.ps1 — 停止全栈（前端 + 6 服务；可选 -Infra 停基础设施）
# 用法：powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1
#       powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1 -Infra   # 连 docker compose down 一起
param([switch]$Infra)
$ErrorActionPreference = 'SilentlyContinue'
$base = Split-Path -Parent $PSScriptRoot

Write-Host "== 1/3 停止前端 (vite) ==" -ForegroundColor Cyan
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object {
  $_.CommandLine -match 'vite' -and $_.CommandLine -match 'frontend-demo'
} | ForEach-Object {
  Stop-Process -Id $_.ProcessId -Force
  Write-Host "  已停止 node pid $($_.ProcessId)"
}
# npm.cmd 外壳进程
Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | Where-Object {
  $_.CommandLine -match 'vite' -and $_.CommandLine -match 'frontend-demo'
} | ForEach-Object {
  Stop-Process -Id $_.ProcessId -Force
  Write-Host "  已停止 cmd pid $($_.ProcessId)"
}

Write-Host "== 2/3 停止 6 个服务 (java) ==" -ForegroundColor Cyan
$services = @('sc-auth-service','sc-supplier-service','sc-purchase-service','sc-inventory-service','sc-payment-service','sc-gateway-service')
$stopped = 0
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $cmd = $_.CommandLine
  foreach ($s in $services) {
    if ($cmd -match [regex]::Escape($s)) {
      Stop-Process -Id $_.ProcessId -Force
      Write-Host "  已停止 java pid $($_.ProcessId) ($s)"
      $stopped++
      break
    }
  }
}
if ($stopped -eq 0) { Write-Host "  未发现运行中的服务进程" }

if ($Infra) {
  Write-Host "== 3/3 停止基础设施（保留数据卷）==" -ForegroundColor Cyan
  Set-Location $base
  docker compose down
}

Write-Host "`n✅ 已停止" -ForegroundColor Green
Write-Host "重启：powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1"
Write-Host "注意：本脚本只停本项目进程（vite + 6 个服务 jar），不会误杀其他 node/java 进程。"