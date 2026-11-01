# S3-06: dump platform metadata DB (ry_vue) to .local/backups. No restore.
# Usage: powershell -ExecutionPolicy Bypass -File D:\datacenter\scripts\backup-metadata.ps1
param(
    [string]$Container = 'mysql-dev',
    [string]$Database = 'ry_vue',
    [string]$DbUser = 'root',
    [string]$Password = 'root',
    [string]$OutDir = 'D:\datacenter\.local\backups'
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile = Join-Path $OutDir ("metadata-{0}-{1}.sql" -f $Database, $stamp)

Write-Host "Dumping $Database from container $Container -> $outFile"
# MYSQL_PWD avoids CLI password warning; keep ErrorAction soft around docker stderr noise.
$cmd = "MYSQL_PWD='$Password' mysqldump -u$DbUser --single-transaction --routines --triggers --databases $Database"
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$sql = docker exec $Container sh -c $cmd 2>$null
$exit = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($exit -ne 0 -or [string]::IsNullOrWhiteSpace($sql)) {
    throw "mysqldump failed (exit=$exit). Check container name/credentials."
}
Set-Content -Path $outFile -Value $sql -Encoding utf8
$size = (Get-Item $outFile).Length
if ($size -lt 1000) {
    throw "dump file too small ($size bytes); dump likely failed"
}
Write-Host "OK size_bytes=$size"
Write-Host "Next: restore into an isolated replica, then POST /bi/ops/backup-drills with artifactPath=$outFile"
