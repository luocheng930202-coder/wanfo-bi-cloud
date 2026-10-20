param([string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$recordFile = Join-Path $root '.local/runtime/processes.json'
if (-not (Test-Path -LiteralPath $recordFile)) { Write-Output 'No recorded local services.'; exit 0 }
$records = Get-Content -LiteralPath $recordFile -Raw | ConvertFrom-Json
foreach ($record in $records) {
  $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
  if ($process -and $process.Name -eq 'java.exe' -and $process.CommandLine.Contains($record.jar)) {
    Stop-Process -Id $record.pid -ErrorAction Stop
    Write-Output "Stopped $($record.name)"
  }
}
