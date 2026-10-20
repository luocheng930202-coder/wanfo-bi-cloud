# Retail demo on datasource 58: datasets + chart library + screen draft (template or full validation layout).
param(
  [string]$BiBase = 'http://127.0.0.1:9206',
  [string]$AuthBase = 'http://127.0.0.1:8080',
  [int]$DatasourceId = 58,
  [ValidateSet('full', 'template')]
  [string]$Profile = 'full',
  [switch]$SkipSeed,
  [switch]$ForceNewDashboard
)
$ErrorActionPreference = 'Stop'
$Utf8 = [System.Text.Encoding]::UTF8
$TinyLogoPng = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='

function Read-Login {
  $candidates = @(
    (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent | Split-Path -Parent) '.local-login.json'),
    'D:\datacenter\Wanfo-Cloud\.local-login.json'
  )
  foreach ($p in $candidates) {
    if (Test-Path $p) {
      $j = Get-Content $p -Raw | ConvertFrom-Json
      return @{ username = $j.username; password = $j.password }
    }
  }
  if (-not $env:WANFO_DEMO_USERNAME -or -not $env:WANFO_DEMO_PASSWORD) { throw 'Provide WANFO_DEMO_USERNAME and WANFO_DEMO_PASSWORD or an ignored .local-login.json.' }
  return @{ username = $env:WANFO_DEMO_USERNAME; password = $env:WANFO_DEMO_PASSWORD }
}

function Invoke-Bi {
  param([string]$Method, [string]$Uri, [hashtable]$Headers, $Body)
  $args = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 24 -Compress
    $args.ContentType = 'application/json; charset=utf-8'
    $args.Body = $Utf8.GetBytes($json)
  }
  $res = Invoke-RestMethod @args
  if ($null -ne $res.code -and [int]$res.code -ne 200) {
    throw "BI API $($Method) $Uri failed: $($res.msg)"
  }
  return $res
}

function New-PanelKey { return [guid]::NewGuid().ToString() }

function Ensure-RetailView {
  $sql = @"
CREATE OR REPLACE VIEW v_retail_wide AS
SELECT o.order_id, o.order_date, o.store_id, o.product_id, o.channel, o.qty, o.amount,
  s.store_name, s.province, s.city, p.product_name, p.category, p.list_price
FROM fact_order o
LEFT JOIN dim_store s ON o.store_id = s.store_id
LEFT JOIN dim_product p ON o.product_id = p.product_id;
"@
  docker exec mysql-dev mysql -uroot -proot --default-character-set=utf8mb4 bi_phase_a_test -e $sql | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Failed to create v_retail_wide (is mysql-dev running?)' }
}

function Find-DatasetByName($list, [string]$name) {
  foreach ($row in $list) {
    if ($row.name -eq $name) { return [long]$row.id }
  }
  return $null
}

function Ensure-TableDataset($hdr, $list, [string]$name, [string]$table, [string[]]$fields) {
  $existing = Find-DatasetByName $list $name
  if ($existing) {
    Write-Host "Dataset exists: $name id=$existing"
    return $existing
  }
  $body = @{
    name = $name
    datasourceId = $DatasourceId
    sourceTable = $table
    cacheEnabled = '0'
    fields = $fields
  }
  $res = Invoke-Bi -Method Post -Uri "$BiBase/bi/dataset/table" -Headers $hdr -Body $body
  $id = [long]$res.data.id
  Write-Host "Created dataset: $name id=$id table=$table"
  return $id
}

function Ensure-OrderJoin($hdr, [long]$orderDatasetId) {
  $model = Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/$orderDatasetId/model" -Headers $hdr
  if ($model.data.join) {
    Write-Host 'ds_order join already configured'
    return
  }
  $join = @{
    joinType = 'LEFT'
    rightTable = 'dim_store'
    leftKey = 'store_id'
    rightKey = 'store_id'
    rightFields = @('store_name', 'province', 'city')
  }
  Invoke-Bi -Method Post -Uri "$BiBase/bi/dataset/$orderDatasetId/model/join" -Headers $hdr -Body $join | Out-Null
  Write-Host 'Applied ds_order LEFT JOIN dim_store (store_name, province, city)'
}

function Ensure-UnitPriceField($hdr, [long]$datasetId) {
  $detail = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/$datasetId" -Headers $hdr).data
  foreach ($f in $detail.fields) {
    if ($f.originName -eq 'unit_price') {
      Write-Host 'Calculated field unit_price already on ds_retail_wide'
      return
    }
  }
  Invoke-Bi -Method Post -Uri "$BiBase/bi/dataset/$datasetId/fields/calculated" -Headers $hdr -Body @{
    originName = 'unit_price'
    showName = '单价'
    formula = 'amount / qty'
    dimFlag = '0'
  } | Out-Null
  Write-Host 'Added calculated field unit_price on ds_retail_wide'
}

function Get-ChartCatalog($hdr) {
  return @((Invoke-Bi -Method Get -Uri "$BiBase/bi/chart/saved" -Headers $hdr).data)
}

function Find-ChartByName($catalog, [string]$name) {
  foreach ($c in $catalog) {
    if ($c.name -eq $name) { return [long]$c.id }
  }
  return $null
}

function Ensure-SavedChart($hdr, [ref]$catalog, [string]$name, $definition, [bool]$templateLease = $false) {
  $id = Find-ChartByName $catalog.Value $name
  if ($id) {
    Write-Host "Chart exists: $name id=$id"
    return $id
  }
  $body = @{ definition = $definition }
  if ($templateLease) { $body.templateLease = $true }
  $res = Invoke-Bi -Method Post -Uri "$BiBase/bi/chart/saved" -Headers $hdr -Body $body
  $id = [long]$res.data.id
  if ($id -le 0) { throw "Chart create returned invalid id for $name" }
  Write-Host "Chart created: $name id=$id"
  $catalog.Value = Get-ChartCatalog $hdr
  return $id
}

function Panel-Config($type, $dims, $metrics, $orderBys, $limit) {
  return @{
    type = $type
    dimensions = $dims
    metrics = $metrics
    orderBys = $orderBys
    limit = $limit
  }
}

function Build-MarketDashboard($marketContent, [string]$dashName, $bindings) {
  $keyMap = @{}
  foreach ($p in $marketContent.panels) { $keyMap[$p.key] = New-PanelKey }
  $panels = @()
  foreach ($p in $marketContent.panels) {
    $panel = [ordered]@{
      key = $keyMap[$p.key]
      title = $p.title
      componentType = $p.componentType
      x = $p.x; y = $p.y; w = $p.w; h = $p.h
      style = $p.style
      locked = $false
      hidden = $false
    }
    if ($p.componentType -eq 'chart' -or $p.componentType -eq 'kpi') {
      $b = $bindings[$p.key]
      if (-not $b) { throw "Missing binding for slot $($p.key)" }
      $panel.chartId = $b.chartId
      $panel.chartConfig = $b.config
    } elseif ($p.content) {
      $panel.content = $p.content
    }
    $panels += $panel
  }
  return @{
    schemaVersion = 1
    name = $dashName
    kind = 'screen'
    layout = 'absolute'
    scene = $marketContent.scene
    appearance = $marketContent.appearance
    panels = $panels
    interaction = @{ controls = @(); links = @() }
  }
}

function Chart-Panel($key, $title, $type, $chartId, $x, $y, $w, $h, $parentKey = $null) {
  $p = @{
    key = $key; title = $title; componentType = $type
    x = $x; y = $y; w = $w; h = $h
    style = @{ showTitle = $true; titleAlign = 'left' }
    locked = $false; hidden = $false; chartId = $chartId
  }
  if ($parentKey) { $p.parentKey = $parentKey }
  return $p
}

function Build-FullRetailDashboard($marketContent, [string]$dashName, $charts, $keys) {
  $appearance = $marketContent.appearance
  $scene = @{
    width = 1920
    height = 1080
    fit = 'contain'
    refreshSeconds = 30
  }
  $k = $keys
  $c = $charts
  $styleTitle = @{ showTitle = $true; titleAlign = 'left' }
  $styleHide = @{ showTitle = $false; titleAlign = 'left' }

  $panels = @(
    @{
      key = $k.hero; title = $dashName; componentType = 'text'; x = 40; y = 16; w = 1680; h = 72
      style = $styleTitle; locked = $false; hidden = $false
      content = @{ text = '全渠道零售运营指挥 · 图表类型 / 主题 / 筛选联动 / 钻取 / 容器装饰 全量验证草稿' }
    },
    @{
      key = $k.logo; title = 'LOGO'; componentType = 'image'; x = 1780; y = 20; w = 100; h = 64
      style = $styleHide; locked = $false; hidden = $false
      content = @{ image = $TinyLogoPng }
    },
    @{
      key = $k.kpiBox; title = '指标区'; componentType = 'container'; x = 40; y = 96; w = 520; h = 500
      style = $styleHide; locked = $false; hidden = $false
      content = @{ container = @{ background = '#102238'; borderColor = '#38BDF8'; stroke = 2; radius = 12; padding = 12 } }
    },
    (Chart-Panel $k.metricA 'GMV' 'kpi' $c.metricA 52 108 496 220 $k.kpiBox),
    (Chart-Panel $k.metricB '销售件数' 'kpi' $c.metricB 52 344 496 220 $k.kpiBox),
    (Chart-Panel $k.barDrill '区域销售（下钻）' 'chart' $c.bar 580 96 640 300),
    (Chart-Panel $k.line '日销售趋势' 'chart' $c.line 1240 96 640 300),
    (Chart-Panel $k.pie '品类占比（联动来源）' 'chart' $c.pie 580 412 400 260),
    (Chart-Panel $k.funnel '渠道漏斗' 'chart' $c.funnel 1000 412 400 260),
    (Chart-Panel $k.map '省级销售地图' 'chart' $c.map 1420 412 460 260),
    (Chart-Panel $k.pivot '品类×省份透视' 'chart' $c.pivot 580 688 620 372),
    (Chart-Panel $k.gauge 'GMV 仪表盘' 'chart' $c.gauge 1220 688 300 372),
    (Chart-Panel $k.table '订单明细（联动目标）' 'chart' $c.table 40 688 520 372),
    @{
      key = $k.frame; title = '边框'; componentType = 'border'; x = 8; y = 8; w = 1904; h = 1064
      style = $styleHide; locked = $true; hidden = $false
      content = @{ decoration = @{ color = '#38BDF8'; stroke = 2; pattern = 'dashed'; radius = 8; direction = 'horizontal' } }
    },
    @{
      key = $k.divider; title = '分隔'; componentType = 'line'; x = 568; y = 96; w = 20; h = 964
      style = $styleHide; locked = $true; hidden = $false
      content = @{ decoration = @{ color = '#284361'; stroke = 2; pattern = 'solid'; radius = 0; direction = 'vertical' } }
    }
  )

  $dataPanels = @($k.metricA, $k.metricB, $k.barDrill, $k.line, $k.pie, $k.funnel, $k.map, $k.pivot, $k.gauge, $k.table)
  $targetsCity = $dataPanels | ForEach-Object { @{ panel = $_; field = 'city' } }
  $targetsChannel = $dataPanels | ForEach-Object { @{ panel = $_; field = 'channel' } }
  $targetsAmount = $dataPanels | ForEach-Object { @{ panel = $_; field = 'amount' } }
  $targetsDate = $dataPanels | ForEach-Object { @{ panel = $_; field = 'order_date' } }

  $interaction = @{
    controls = @(
      @{ key = 'flt_city'; label = '城市'; type = 'text'; defaultValue = $null; targets = $targetsCity }
      @{ key = 'flt_channel'; label = '渠道'; type = 'enum'; defaultValue = $null; targets = $targetsChannel }
      @{ key = 'flt_amount'; label = '金额'; type = 'number'; defaultValue = $null; targets = $targetsAmount }
      @{ key = 'flt_order_date'; label = '订单日期'; type = 'date'; defaultValue = $null; targets = $targetsDate }
    )
    links = @(
      @{
        key = 'lnk_category'
        sourcePanel = $k.pie
        sourceField = 'category'
        targets = @(@{ panel = $k.table; field = 'category' })
      }
    )
  }

  return @{
    schemaVersion = 1
    name = $dashName
    kind = 'screen'
    layout = 'absolute'
    scene = $scene
    appearance = $appearance
    panels = $panels
    interaction = $interaction
  }
}

function Find-DashboardByName($hdr, [string]$name) {
  foreach ($b in (Invoke-Bi -Method Get -Uri "$BiBase/bi/dashboard" -Headers $hdr).data) {
    if ($b.name -eq $name) { return $b }
  }
  return $null
}

function Save-Dashboard($hdr, $definition, $existing) {
  Invoke-Bi -Method Post -Uri "$BiBase/bi/dashboard/preview" -Headers $hdr -Body @{
    definition = $definition
    input = @{ filters = @{}; links = @{} }
  } | Out-Null
  Write-Host 'Dashboard preview OK'
  if ($existing -and -not $ForceNewDashboard) {
    $draft = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dashboard/$($existing.id)/draft" -Headers $hdr).data
    $ver = [int]$draft.version
    $saved = Invoke-Bi -Method Put -Uri "$BiBase/bi/dashboard/$($existing.id)" -Headers $hdr -Body @{
      definition = $definition
      version = $ver
    }
    return @{ id = [long]$existing.id; version = [int]$saved.data.version; updated = $true }
  }
  $saved = Invoke-Bi -Method Post -Uri "$BiBase/bi/dashboard" -Headers $hdr -Body @{ definition = $definition }
  $newId = [long]$saved.data.id
  if ($newId -le 0) { throw 'Dashboard create returned invalid id' }
  return @{ id = $newId; version = [int]$saved.data.version; updated = $false }
}

# --- main ---
if (-not $SkipSeed) {
  $seed = Join-Path $PSScriptRoot 'seed-retail-screen.ps1'
  if (Test-Path $seed) { & $seed }
}
Ensure-RetailView

$login = Read-Login | ConvertTo-Json -Compress
$token = (Invoke-RestMethod -Uri "$AuthBase/auth/login" -Method Post -ContentType 'application/json' -Body $login).data.access_token
$hdr = @{ Authorization = "Bearer $token" }
Write-Host "Login OK (profile=$Profile)"

$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsStore = Ensure-TableDataset $hdr $dsList 'ds_store' 'dim_store' @('store_id', 'store_name', 'province', 'city', 'open_date')
$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsProduct = Ensure-TableDataset $hdr $dsList 'ds_product' 'dim_product' @('product_id', 'product_name', 'category', 'list_price')
$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsOrder = Ensure-TableDataset $hdr $dsList 'ds_order' 'fact_order' @('order_id', 'order_date', 'store_id', 'product_id', 'channel', 'qty', 'amount')
$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsTarget = Ensure-TableDataset $hdr $dsList 'ds_target' 'dim_target' @('month_key', 'province', 'target_amount')
$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsNote = Ensure-TableDataset $hdr $dsList 'ds_ops_note' 'ops_retail_note' @('note_date', 'province', 'channel', 'content')
$dsList = (Invoke-Bi -Method Get -Uri "$BiBase/bi/dataset/list" -Headers $hdr).data
$dsWide = Ensure-TableDataset $hdr $dsList 'ds_retail_wide' 'v_retail_wide' @(
  'order_id', 'order_date', 'store_id', 'product_id', 'channel', 'qty', 'amount',
  'store_name', 'province', 'city', 'product_name', 'category', 'list_price'
)
Ensure-OrderJoin $hdr $dsOrder
Ensure-UnitPriceField $hdr $dsWide

$dashName = if ($Profile -eq 'full') { '全渠道零售运营指挥大屏' } else { '全渠道零售运营指挥大屏·模板' }
$existingDash = if (-not $ForceNewDashboard) { Find-DashboardByName $hdr $dashName } else { $null }
if ($Profile -eq 'template' -and $existingDash) {
  Write-Host "`nScreen already exists: id=$($existingDash.id) — use -Profile full or -ForceNewDashboard"
  exit 0
}

$wideId = $dsWide
$chartCatalog = [ref](Get-ChartCatalog $hdr)

$chartMetricA = Ensure-SavedChart $hdr $chartCatalog '零售-KPI-GMV' @{
  schemaVersion = 1; name = '零售-KPI-GMV'; type = 'table'
  query = @{ datasetId = $wideId; dimensions = @(); metrics = @(@{ field = 'amount'; agg = 'SUM' }); orderBys = @(); limit = 1 }
}
$chartMetricB = Ensure-SavedChart $hdr $chartCatalog '零售-KPI-件数' @{
  schemaVersion = 1; name = '零售-KPI-件数'; type = 'table'
  query = @{ datasetId = $wideId; dimensions = @(); metrics = @(@{ field = 'qty'; agg = 'SUM' }); orderBys = @(); limit = 1 }
}
$chartBar = Ensure-SavedChart $hdr $chartCatalog '零售-区域柱图' @{
  schemaVersion = 1; name = '零售-区域柱图'; type = 'bar'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'province' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @(@{ field = 'province'; order = 'asc' })
    limit = 100
  }
  advanced = @{ nullMode = 'keep'; smoothLine = $true; drillFields = @('province', 'city', 'store_name') }
}
$chartLine = Ensure-SavedChart $hdr $chartCatalog '零售-日趋势' @{
  schemaVersion = 1; name = '零售-日趋势'; type = 'line'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'order_date' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @(@{ field = 'order_date'; order = 'asc' })
    limit = 365
  }
  advanced = @{ nullMode = 'keep'; smoothLine = $true }
}
$chartPie = Ensure-SavedChart $hdr $chartCatalog '零售-品类饼图' @{
  schemaVersion = 1; name = '零售-品类饼图'; type = 'pie'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'category' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @()
    limit = 20
  }
}
$chartFunnel = Ensure-SavedChart $hdr $chartCatalog '零售-渠道漏斗' @{
  schemaVersion = 1; name = '零售-渠道漏斗'; type = 'funnel'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'channel' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @()
    limit = 10
  }
}
$chartMap = Ensure-SavedChart $hdr $chartCatalog '零售-省级地图' @{
  schemaVersion = 1; name = '零售-省级地图'; type = 'map'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'province' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @()
    limit = 50
  }
  advanced = @{ mapScope = 'china-provinces' }
}
$chartPivot = Ensure-SavedChart $hdr $chartCatalog '零售-品类区域透视' @{
  schemaVersion = 1; name = '零售-品类区域透视'; type = 'pivot'
  query = @{
    datasetId = $wideId
    dimensions = @(@{ field = 'category' }, @{ field = 'province' })
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @()
    limit = 500
  }
  advanced = @{ pivotRowFields = @('category'); pivotColFields = @('province') }
}
$chartGauge = Ensure-SavedChart $hdr $chartCatalog '零售-达成率仪表' @{
  schemaVersion = 1; name = '零售-达成率仪表'; type = 'gauge'
  query = @{
    datasetId = $wideId
    dimensions = @()
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @()
    limit = 1
  }
}
$chartTable = Ensure-SavedChart $hdr $chartCatalog '零售-订单明细' @{
  schemaVersion = 1; name = '零售-订单明细'; type = 'table'
  query = @{
    datasetId = $wideId
    dimensions = @(
      @{ field = 'order_date' },
      @{ field = 'store_name' },
      @{ field = 'channel' },
      @{ field = 'category' }
    )
    metrics = @(@{ field = 'amount'; agg = 'SUM' })
    orderBys = @(@{ field = 'order_date'; order = 'desc' })
    limit = 200
  }
}

$cfgMetricA = Panel-Config 'table' @() @(@{ field = 'amount'; agg = 'SUM' }) @() 1
$cfgMetricB = Panel-Config 'table' @() @(@{ field = 'qty'; agg = 'SUM' }) @() 1
$cfgBar = Panel-Config 'bar' @(@{ field = 'province' }) @(@{ field = 'amount'; agg = 'SUM' }) @(@{ field = 'province'; order = 'asc' }) 100
$cfgLine = Panel-Config 'line' @(@{ field = 'order_date' }) @(@{ field = 'amount'; agg = 'SUM' }) @(@{ field = 'order_date'; order = 'asc' }) 365
$cfgPie = Panel-Config 'pie' @(@{ field = 'category' }) @(@{ field = 'amount'; agg = 'SUM' }) @() 20
$cfgFunnel = Panel-Config 'funnel' @(@{ field = 'channel' }) @(@{ field = 'amount'; agg = 'SUM' }) @() 10
$cfgMap = Panel-Config 'map' @(@{ field = 'province' }) @(@{ field = 'amount'; agg = 'SUM' }) @() 50
$cfgPivot = Panel-Config 'pivot' @(@{ field = 'category' }, @{ field = 'province' }) @(@{ field = 'amount'; agg = 'SUM' }) @() 500
$cfgGauge = Panel-Config 'gauge' @() @(@{ field = 'amount'; agg = 'SUM' }) @() 1
$cfgTable = Panel-Config 'table' @(
  @{ field = 'order_date' }, @{ field = 'store_name' }, @{ field = 'channel' }, @{ field = 'category' }
) @(@{ field = 'amount'; agg = 'SUM' }) @(@{ field = 'order_date'; order = 'desc' }) 200

if ($Profile -eq 'template') {
  $bindings = @{
    'metric-a' = @{ chartId = $chartMetricA; config = $cfgMetricA }
    'metric-b' = @{ chartId = $chartMetricB; config = $cfgMetricB }
    'analysis-a' = @{ chartId = $chartBar; config = $cfgBar }
    'analysis-b' = @{ chartId = $chartTable; config = $cfgTable }
  }
  $market = (Invoke-Bi -Method Get -Uri "$BiBase/bi/template/market/market-screen-ops" -Headers $hdr).data
  $definition = Build-MarketDashboard $market.content $dashName $bindings
} else {
  $market = (Invoke-Bi -Method Get -Uri "$BiBase/bi/template/market/market-screen-ops" -Headers $hdr).data
  $keys = @{
    hero = New-PanelKey; logo = New-PanelKey; kpiBox = New-PanelKey
    metricA = New-PanelKey; metricB = New-PanelKey
    barDrill = New-PanelKey; line = New-PanelKey; pie = New-PanelKey; funnel = New-PanelKey
    map = New-PanelKey; pivot = New-PanelKey; gauge = New-PanelKey; table = New-PanelKey
    frame = New-PanelKey; divider = New-PanelKey
  }
  $chartBag = @{
    metricA = $chartMetricA; metricB = $chartMetricB; bar = $chartBar; line = $chartLine
    pie = $chartPie; funnel = $chartFunnel; map = $chartMap; pivot = $chartPivot
    gauge = $chartGauge; table = $chartTable
  }
  $definition = Build-FullRetailDashboard $market.content $dashName $chartBag $keys
}

$result = Save-Dashboard $hdr $definition $existingDash
$dashId = $result.id
$version = $result.version

Write-Host "`n=== Done (profile=$Profile, charts=10, panels=$($definition.panels.Count)) ==="
Write-Host "Screen id=$dashId version=$version updated=$($result.updated)"
Write-Host "Designer: http://127.0.0.1/bi-designer/$dashId"
Write-Host "Datasets: wide=$dsWide order=$dsOrder store=$dsStore product=$dsProduct"
Write-Host "Chart ids: GMV=$chartMetricA qty=$chartMetricB bar=$chartBar line=$chartLine pie=$chartPie funnel=$chartFunnel map=$chartMap pivot=$chartPivot gauge=$chartGauge table=$chartTable"
