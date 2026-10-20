# 使用 D:\Java 自带 Maven（避免用户 ~/.m2 中不可达 Nexus 配置）
# 编译前请先停止占用 ruoyi-modules-bi.jar 的 Java 进程（端口 9206）
$Maven = 'D:\Java\apache-maven-3.9.9\bin\mvn.cmd'
$Settings = 'D:\Java\apache-maven-3.9.9\conf\settings.xml'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')

Push-Location $Root
& $Maven -gs $Settings -f pom.xml -pl ruoyi-modules/ruoyi-bi -am package -DskipTests
$code = $LASTEXITCODE
Pop-Location
exit $code
