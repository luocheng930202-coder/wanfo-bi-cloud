# Local drill-down API verification. Supply credentials through environment variables.
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:9206'
if (-not $env:WANFO_DEMO_USERNAME -or -not $env:WANFO_DEMO_PASSWORD) { throw 'Set WANFO_DEMO_USERNAME and WANFO_DEMO_PASSWORD first.' }
$Login = @{ username = $env:WANFO_DEMO_USERNAME; password = $env:WANFO_DEMO_PASSWORD } | ConvertTo-Json -Compress
$token = (Invoke-RestMethod -Uri 'http://127.0.0.1:8080/auth/login' -Method Post -ContentType 'application/json' -Body $Login).data.access_token
$hdr = @{ Authorization = "Bearer $token" }
Write-Host "Login OK"

$dsName = "钻取验证数据集-$(Get-Date -Format 'HHmmss')"
$dsBody = @{
  name = $dsName
  datasourceId = 58
  sourceTable = 'demo_drill_sales'
  cacheEnabled = '0'
  fields = @('region', 'city', 'store', 'amount', 'order_date')
} | ConvertTo-Json -Depth 5
$ds = Invoke-RestMethod -Uri "$Base/bi/dataset/table" -Method Post -Headers $hdr -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($dsBody))
$datasetId = [long]$ds.data.id
Write-Host "Dataset id=$datasetId"

$chartDef = @{
  schemaVersion = 1
  name = "钻取验证柱图"
  type = 'bar'
  query = @{
    datasetId = $datasetId
    dimensions = @(@{ field = 'region' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @(@{ field = 'region'; order = 'asc' })
    limit = 100
  }
  advanced = @{
    nullMode = 'keep'
    smoothLine = $true
    drillFields = @('region', 'city', 'store')
  }
}
$chartReq = @{ definition = $chartDef } | ConvertTo-Json -Depth 8 -Compress
$chart = Invoke-RestMethod -Uri "$Base/bi/chart/saved" -Method Post -Headers $hdr -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($chartReq))
$chartId = [long]$chart.data.id
Write-Host "Chart id=$chartId"

$panelKey = 'drill-bar'
$dashDef = @{
  schemaVersion = 1
  name = "钻取验证看板-$(Get-Date -Format 'HHmmss')"
  kind = 'dashboard'
  layout = 'grid'
  panels = @(@{
      key = $panelKey
      chartId = $chartId
      title = '区域下钻验证'
      x = 0; y = 0; w = 12; h = 8
      componentType = 'chart'
    })
  interaction = @{ controls = @(); links = @() }
}
$dashReq = @{ definition = $dashDef } | ConvertTo-Json -Depth 8 -Compress
$dash = Invoke-RestMethod -Uri "$Base/bi/dashboard" -Method Post -Headers $hdr -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($dashReq))
$dashId = [long]$dash.data.id
$version = [int]$dash.data.version
Write-Host "Dashboard id=$dashId version=$version"

$pub = @{ version = $version } | ConvertTo-Json -Compress
$pubRes = Invoke-RestMethod -Uri "$Base/bi/dashboard/$dashId/publish" -Method Post -Headers $hdr -ContentType 'application/json' -Body $pub
$revision = [int]$pubRes.data.revision
Write-Host "Published revision=$revision"

$topJson = "{`"filters`":{},`"links`":{},`"drills`":{`"$panelKey`":[]}}"
$top = Invoke-RestMethod -Uri "$Base/bi/dashboard/$dashId/panels/$panelKey/data?revision=$revision" -Method Post -Headers $hdr -ContentType 'application/json' -Body $topJson
Write-Host "`n=== Top level (region) ==="
Write-Host "drillLevel=$($top.data.presentation.drillLevel) canDrillDown=$($top.data.presentation.canDrillDown)"
$top.data.data.rows | ForEach-Object { Write-Host "  $($_.region) amount=$($_.sum_amount)" }

$drillJson = "{`"filters`":{},`"links`":{},`"drills`":{`"$panelKey`":[{`"field`":`"region`",`"value`":`"East`"}]}}"
$drill = Invoke-RestMethod -Uri "$Base/bi/dashboard/$dashId/panels/$panelKey/data?revision=$revision" -Method Post -Headers $hdr -ContentType 'application/json' -Body $drillJson
Write-Host "`n=== After drill East (city) ==="
Write-Host "drillLevel=$($drill.data.presentation.drillLevel) canDrillDown=$($drill.data.presentation.canDrillDown)"
$drill.data.data.rows | ForEach-Object { Write-Host "  $($_.city) amount=$($_.sum_amount)" }

$ok = ($top.data.presentation.canDrillDown -eq $true) -and ($drill.data.presentation.drillLevel -eq 1) -and ($drill.data.data.rows.Count -ge 1) -and ($drill.data.presentation.drillPath[0].value -eq 'East')
if ($ok) { Write-Host "`nPASS: drill API verification succeeded. Open BI dashboard id=$dashId in UI." -ForegroundColor Green }
else { Write-Host "`nFAIL: unexpected drill response" -ForegroundColor Red; exit 1 }
