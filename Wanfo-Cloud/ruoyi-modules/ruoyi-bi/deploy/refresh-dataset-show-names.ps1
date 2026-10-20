# 1) Optional: apply retail COLUMN_COMMENT on business DB
# 2) Sync ry_vue.bi_dataset_field.show_name from information_schema (table datasets)
param(
  [string]$Docker = 'mysql-dev',
  [string]$MetaDb = 'ry_vue',
  [string]$BusinessDb = 'bi_phase_a_test',
  [int]$DatasourceId = 58,
  [switch]$AllDatasources,
  [switch]$SkipRetailComments,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot

function Invoke-MysqlText([string]$Db, [string]$SqlText) {
  $tmp = Join-Path $env:TEMP "bi-mysql-$(Get-Random).sql"
  Set-Content -Path $tmp -Value $SqlText -Encoding UTF8
  $remote = "/tmp/$(Split-Path $tmp -Leaf)"
  docker cp $tmp "${Docker}:$remote" | Out-Null
  docker exec $Docker sh -c "mysql -uroot -proot --default-character-set=utf8mb4 $Db < $remote"
  if ($LASTEXITCODE -ne 0) { throw "mysql failed on $Db" }
  Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}

function Invoke-MysqlFile([string]$Db, [string]$File, [string]$Prefix = '') {
  $body = if ($Prefix) { "$Prefix`n$(Get-Content $File -Raw)" } else { Get-Content $File -Raw }
  Invoke-MysqlText $Db $body
}

if (-not $SkipRetailComments) {
  $comments = Join-Path $Root 'demo-retail-column-comments.sql'
  if (Test-Path $comments) {
    Write-Host "Applying retail column comments -> $BusinessDb" -ForegroundColor Cyan
    Invoke-MysqlFile $BusinessDb $comments
  }
}

$sql = Join-Path $Root 'refresh-dataset-show-names.sql'
if ($DryRun) {
  $preview = @"
SET NAMES utf8mb4;
USE $MetaDb;
SET @datasource_id := $(if ($AllDatasources) { 'NULL' } else { $DatasourceId });
SELECT f.id, t.name AS dataset_name, f.origin_name, f.show_name AS old_show,
       TRIM(c.COLUMN_COMMENT) AS new_show, t.source_catalog, t.source_table
FROM bi_dataset_field f
INNER JOIN bi_dataset_table t ON f.dataset_id = t.id AND t.del_flag = '0' AND t.type = 'table'
INNER JOIN bi_datasource d ON t.datasource_id = d.id AND d.del_flag = '0'
INNER JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = IFNULL(NULLIF(TRIM(t.source_catalog), ''), '$BusinessDb')
 AND c.TABLE_NAME = t.source_table
 AND c.COLUMN_NAME = IF(f.origin_name LIKE 'r\_%' AND f.ext_flag = '0', SUBSTRING(f.origin_name, 3), f.origin_name)
WHERE f.del_flag = '0' AND f.ext_flag = '0'
  AND (@datasource_id IS NULL OR d.id = @datasource_id)
  AND CHAR_LENGTH(TRIM(c.COLUMN_COMMENT)) > 0
  AND (f.show_name IS NULL OR f.show_name <> LEFT(TRIM(c.COLUMN_COMMENT), 200));
"@
  $tmp = Join-Path $env:TEMP 'bi-show-name-preview.sql'
  Set-Content -Path $tmp -Value $preview -Encoding UTF8
  Write-Host 'Dry-run: rows that would change' -ForegroundColor Yellow
  Invoke-MysqlText $MetaDb (Get-Content $tmp -Raw)
  exit 0
}

Write-Host "Refreshing show_name in $MetaDb (datasource=$(if ($AllDatasources) { 'ALL' } else { $DatasourceId }))" -ForegroundColor Cyan
$dsVar = if ($AllDatasources) { 'NULL' } else { "$DatasourceId" }
Invoke-MysqlFile $MetaDb $sql "SET @datasource_id := $dsVar;`nSET @business_schema := '$BusinessDb';"
Write-Host 'Done. Re-open charts/dashboards or hard-refresh browser to see labels.' -ForegroundColor Green
