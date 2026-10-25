param(
  [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent),
  [switch]$Disable
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$agent = Join-Path $root '.local/otel/opentelemetry-javaagent.jar'
if (-not $Disable -and -not (Test-Path -LiteralPath $agent)) { throw 'Run scripts/install-otel-agent.ps1 first.' }
$envFile = Join-Path $root 'deploy/.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'deploy/.env is missing; no services were stopped.' }
foreach ($line in Get-Content -LiteralPath $envFile) {
  if ($line -match '^(WANFO_ACTUATOR_USERNAME|WANFO_ACTUATOR_PASSWORD)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$jwtPath = Join-Path $root '.local/runtime/wanfo-jwt-secret.txt'
if (-not (Test-Path -LiteralPath $jwtPath)) { throw 'Local JWT secret file is missing. No services were stopped.' }
$env:WANFO_JWT_SECRET = [IO.File]::ReadAllText($jwtPath).Trim()
if ([Convert]::FromBase64String($env:WANFO_JWT_SECRET).Length -lt 64) { throw 'WANFO_JWT_SECRET needs at least 64 random bytes. No services were stopped.' }
$specs = @(
  @{ Name='ruoyi-gateway'; Port=8080; Jar='ruoyi-gateway/target/ruoyi-gateway.jar' },
  @{ Name='ruoyi-auth'; Port=9200; Jar='ruoyi-auth/target/ruoyi-auth.jar' },
  @{ Name='ruoyi-system'; Port=9201; Jar='ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar' },
  @{ Name='ruoyi-bi'; Port=9206; Jar='ruoyi-modules/ruoyi-bi/target/ruoyi-modules-bi.jar' }
)
$java = (Get-Command java.exe -ErrorAction Stop).Source
$processes = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'")
$targets = @()
foreach ($spec in $specs) {
  $jarPath = Join-Path $root "Wanfo-Cloud/$($spec.Jar)"
  $match = @($processes | Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($jarPath, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
  if ($match.Count -ne 1) { throw "Expected exactly one running Java process for $($spec.Name); found $($match.Count). No processes were stopped." }
  $hasAgent = $match[0].CommandLine.Contains('-javaagent:')
  $remainder = $match[0].CommandLine -replace '^\s*(?:"[^"]+"|\S+)\s+',''
  if ($hasAgent) { $remainder = $remainder -replace '-javaagent:(?:"[^"]+"|\S+)\s*','' }
  if (-not $remainder) { throw "Unable to preserve startup arguments for $($spec.Name). No processes were stopped." }
  $targets += [pscustomobject]@{ Spec=$spec; Process=$match[0]; JarPath=$jarPath; Arguments=$remainder }
}
$runDir = Join-Path $root '.local/runtime'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
if (-not $Disable) {
  if (-not (Get-NetTCPConnection -LocalPort 13133 -State Listen -ErrorAction SilentlyContinue)) { throw 'OTel Collector is not listening at 127.0.0.1:13133. Start deploy/compose.tracing.yml first. No services were stopped.' }
}
foreach ($entry in $targets) { Stop-Process -Id $entry.Process.ProcessId -Force }
$deadline = (Get-Date).AddSeconds(30)
$busy = @()
do {
  $busy = @($targets | Where-Object { Get-NetTCPConnection -LocalPort $_.Spec.Port -State Listen -ErrorAction SilentlyContinue })
  if ($busy.Count -eq 0) { break }
  Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)
if ($busy.Count -gt 0) { throw "Service ports did not close within 30 seconds: $(($busy | ForEach-Object { $_.Spec.Port }) -join ', '). Processes were stopped; restart them with their previous command lines." }
$previous = @{}
foreach ($name in @('OTEL_SERVICE_NAME','OTEL_RESOURCE_ATTRIBUTES','OTEL_EXPORTER_OTLP_PROTOCOL','OTEL_EXPORTER_OTLP_ENDPOINT','OTEL_TRACES_EXPORTER','OTEL_METRICS_EXPORTER','OTEL_LOGS_EXPORTER','OTEL_TRACES_SAMPLER','OTEL_TRACES_SAMPLER_ARG')) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$records = @()
try {
  foreach ($entry in $targets) {
    $serviceName = $entry.Spec.Name
    if (-not $Disable) {
      $env:OTEL_SERVICE_NAME = $serviceName
      $env:OTEL_RESOURCE_ATTRIBUTES = 'service.namespace=wanfo,deployment.environment=local'
      $env:OTEL_EXPORTER_OTLP_PROTOCOL = 'http/protobuf'
      $env:OTEL_EXPORTER_OTLP_ENDPOINT = 'http://127.0.0.1:4318'
      $env:OTEL_TRACES_EXPORTER = 'otlp'
      $env:OTEL_METRICS_EXPORTER = 'none'
      $env:OTEL_LOGS_EXPORTER = 'none'
      $env:OTEL_TRACES_SAMPLER = 'parentbased_traceidratio'
      $env:OTEL_TRACES_SAMPLER_ARG = '1.0'
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $arguments = if ($Disable) { $entry.Arguments } else { '-javaagent:"' + $agent + '" ' + $entry.Arguments }
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $root 'Wanfo-Cloud') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDir "$serviceName-tracing-$stamp.stdout.log") -RedirectStandardError (Join-Path $runDir "$serviceName-tracing-$stamp.stderr.log") -PassThru
    $records += [pscustomobject]@{ name=$serviceName; pid=$process.Id; port=$entry.Spec.Port; jar=$entry.JarPath }
    Write-Output "Started $serviceName (tracing enabled=$(-not $Disable)): PID $($process.Id), port $($entry.Spec.Port)"
  }
} finally {
  foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
$records | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $runDir 'tracing-processes.json')
