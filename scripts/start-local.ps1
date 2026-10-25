param(
  [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent),
  [int]$GatewayPort = 8080, [int]$AuthPort = 9200, [int]$SystemPort = 9201, [int]$BiPort = 9206,
  [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$envFile = Join-Path $root 'deploy/.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Run scripts/init-local.ps1 first.' }
foreach ($line in Get-Content -LiteralPath $envFile) {
  if ($line -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
foreach ($name in @('WANFO_DB_USER','WANFO_DB_PASSWORD','WANFO_REDIS_PASSWORD','WANFO_JWT_SECRET','WANFO_ACTUATOR_USERNAME','WANFO_ACTUATOR_PASSWORD')) {
  if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing required variable: $name" }
}
if ([Convert]::FromBase64String($env:WANFO_JWT_SECRET).Length -lt 64) { throw 'WANFO_JWT_SECRET needs 64 random bytes in base64.' }
$env:WANFO_DB_HOST = '127.0.0.1'
$env:WANFO_DB_PORT = $env:WANFO_MYSQL_PORT
$env:WANFO_SYSTEM_URL = "http://127.0.0.1:$SystemPort"
$env:WANFO_AUTH_URL = "http://127.0.0.1:$AuthPort"
$env:WANFO_BI_URL = "http://127.0.0.1:$BiPort"
$configDir = (Resolve-Path -LiteralPath (Join-Path $root '.local/config')).Path.Replace('\','/')
foreach ($config in @('application-dev.yml','ruoyi-gateway-dev.yml')) {
  if (-not (Test-Path -LiteralPath "$configDir/$config")) { throw "Missing local config: $config" }
}
$services = @(
  @{ Name='auth'; Port=$AuthPort; Jar='ruoyi-auth/target/ruoyi-auth.jar' },
  @{ Name='system'; Port=$SystemPort; Jar='ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar' },
  @{ Name='gateway'; Port=$GatewayPort; Jar='ruoyi-gateway/target/ruoyi-gateway.jar' },
  @{ Name='bi'; Port=$BiPort; Jar='ruoyi-modules/ruoyi-bi/target/ruoyi-modules-bi.jar' }
)
$tracingEnabled = $env:WANFO_TRACING_ENABLED -eq 'true'
$agentPath = Join-Path $root '.local/otel/opentelemetry-javaagent.jar'
if ($tracingEnabled -and -not (Test-Path -LiteralPath $agentPath)) {
  throw "Tracing is enabled, but the OpenTelemetry Java Agent is missing: $agentPath. Run scripts/install-otel-agent.ps1 first."
}
foreach ($service in $services) {
  $jar = Join-Path $root "Wanfo-Cloud/$($service.Jar)"
  if (-not (Test-Path -LiteralPath $jar)) { throw "Build the backend first. Missing: $jar" }
  if (Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue) { throw "Port already in use: $($service.Port). Nothing was started." }
}
if ($CheckOnly) { Write-Output 'Local configuration, secret lengths, JARs and ports validated.'; exit 0 }
$logDir = Join-Path $root '.local/runtime'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$java = (Get-Command java.exe -ErrorAction Stop).Source
$records = @()
foreach ($service in $services) {
  $jar = Join-Path $root "Wanfo-Cloud/$($service.Jar)"
  $locations = "optional:file:$configDir/"
  if ($service.Name -eq 'gateway') { $locations += ",optional:file:$configDir/ruoyi-gateway-dev.yml" }
  $arguments = @('-Xmx384m','-jar',('"' + $jar + '"'),"--server.port=$($service.Port)","--spring.config.import=optional:file:$configDir/application-dev.yml","--spring.config.additional-location=$locations")
  if ($tracingEnabled) {
    $arguments = @('-javaagent:' + ('"' + $agentPath + '"')) + $arguments
    $env:OTEL_SERVICE_NAME = "ruoyi-$($service.Name)"
    $env:OTEL_RESOURCE_ATTRIBUTES = 'service.namespace=wanfo,deployment.environment=local'
    $env:OTEL_EXPORTER_OTLP_PROTOCOL = 'http/protobuf'
    $env:OTEL_EXPORTER_OTLP_ENDPOINT = 'http://127.0.0.1:4318'
    $env:OTEL_TRACES_EXPORTER = 'otlp'
    $env:OTEL_METRICS_EXPORTER = 'none'
    $env:OTEL_LOGS_EXPORTER = 'none'
    $env:OTEL_TRACES_SAMPLER = 'parentbased_traceidratio'
    $env:OTEL_TRACES_SAMPLER_ARG = if ($env:WANFO_OTEL_SAMPLE_PROBABILITY) { $env:WANFO_OTEL_SAMPLE_PROBABILITY } else { '1.0' }
    $env:OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST = ''
    $env:OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_CLIENT_REQUEST = ''
  }
  $arguments += if ($service.Name -eq 'bi') { '--spring.flyway.enabled=true' } else { '--spring.flyway.enabled=false' }
  $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $root 'Wanfo-Cloud') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir "$($service.Name).stdout.log") -RedirectStandardError (Join-Path $logDir "$($service.Name).stderr.log") -PassThru
  $records += @{ name=$service.Name; pid=$process.Id; jar=$jar; port=$service.Port }
  Write-Output "$($service.Name): PID $($process.Id), port $($service.Port)"
}
$records | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $logDir 'processes.json')
