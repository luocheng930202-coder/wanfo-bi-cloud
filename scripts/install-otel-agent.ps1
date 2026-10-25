param(
  [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent),
  [string]$Version
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$envFile = Join-Path $root 'deploy/.env'
if (-not $Version -and (Test-Path -LiteralPath $envFile)) {
  $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^WANFO_OTEL_AGENT_VERSION=' } | Select-Object -First 1
  if ($line) { $Version = $line.Substring($line.IndexOf('=') + 1) }
}
if (-not $Version) { $Version = '2.33.0' }
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version must be a pinned semantic version, for example 2.33.0.' }
$directory = Join-Path $root '.local/otel'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$destination = Join-Path $directory 'opentelemetry-javaagent.jar'
$download = Join-Path $directory "opentelemetry-javaagent.$PID.download"
$uri = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$Version/opentelemetry-javaagent.jar"
Invoke-WebRequest -Uri $uri -OutFile $download -Headers @{ 'User-Agent' = 'WanfoTracingSetup' }
$java = (Get-Command jar.exe -ErrorAction Stop).Source
& $java tf $download *> $null
if ($LASTEXITCODE -ne 0) { Remove-Item -LiteralPath $download -Force; throw 'Downloaded agent failed JAR integrity validation.' }
Move-Item -LiteralPath $download -Destination $destination -Force
$hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath (Join-Path $directory 'opentelemetry-javaagent.sha256') -Value "$hash  opentelemetry-javaagent.jar" -Encoding ascii
Write-Output "Installed OpenTelemetry Java Agent $Version to ignored .local/otel/. SHA-256: $hash"
