param([string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$envFile = Join-Path $root 'deploy/.env'
if (Test-Path -LiteralPath $envFile) { throw 'deploy/.env already exists; refusing to overwrite local secrets.' }
function New-RandomBase64([int]$length) {
  $bytes = New-Object byte[] $length
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  return [Convert]::ToBase64String($bytes)
}
$values = @{
  'WANFO_DB_PASSWORD' = (New-RandomBase64 24)
  'MYSQL_ROOT_PASSWORD' = (New-RandomBase64 24)
  'WANFO_REDIS_PASSWORD' = (New-RandomBase64 24)
  'WANFO_JWT_SECRET' = (New-RandomBase64 64)
}
$lines = Get-Content -LiteralPath (Join-Path $root 'deploy/.env.example') | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=$' -and $values.ContainsKey($Matches[1])) { $Matches[1] + '=' + $values[$Matches[1]] } else { $_ }
}
[IO.File]::WriteAllLines($envFile, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
$configDir = Join-Path $root '.local/config'
New-Item -ItemType Directory -Force -Path $configDir | Out-Null
foreach ($name in @('application-dev.yml','ruoyi-gateway-dev.yml')) {
  $target = Join-Path $configDir $name
  if (Test-Path -LiteralPath $target) { throw "Local config already exists: $target" }
  Copy-Item -LiteralPath (Join-Path $root "deploy/config/$name.example") -Destination $target
}
Write-Output 'Generated ignored deploy/.env and .local/config. Real secrets were not printed.'
