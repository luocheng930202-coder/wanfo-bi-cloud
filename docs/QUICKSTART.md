# 从干净目录启动

开发验证环境：Windows + PowerShell、Python 3、JDK 17、Maven 3.9、Node.js 24、npm、Docker Compose。前端可运行于 Linux；当前自动启动脚本针对 Windows，生产部署参见 [DEPLOYMENT.md](DEPLOYMENT.md)。

## 1. 获取源码并构建

```powershell
git clone https://github.com/luocheng930202-coder/wanfo-bi-cloud.git
cd wanfo-bi-cloud
mvn -B -ntp -f Wanfo-Cloud/pom.xml verify
cd Wanfo-Cloud-Vue
npm ci
node --test scripts/*.test.mjs
npm run typecheck:bi
npm run build:prod
cd ..
```

如果本机 Maven 指向不可访问的私服，请显式使用可用 settings.xml，例如 `mvn -gs <settings.xml路径> ...`，不要把个人仓库地址或凭据写入项目。`typecheck:bi` 只约束 BI 范围；原模板还有既有全项目类型诊断，不把它表述为全站类型检查通过。

## 2. 创建本地配置和基础服务

```powershell
powershell -ExecutionPolicy Bypass -File scripts/init-local.ps1
docker compose --env-file deploy/.env -f deploy/compose.infra.yml up -d --wait
python scripts/init-db.py
```

初始化会生成被忽略的 `deploy/.env`、`.local/config/application-dev.yml` 和 `.local/config/ruoyi-gateway-dev.yml`，并为数据库、Redis、JWT 生成随机密钥，不会覆盖已有本地配置。

数据库初始化脚本只接受空数据库，先检查是否存在表；发现已有数据就停止，避免初始化 SQL 的 DROP TABLE 覆盖业务数据。数据库默认 `wanfo_bi`，开发端口 MySQL `13306`、Redis `16379`，仅绑定本机。BI 菜单显式初始化；BI 表的 V1 及后续迁移由 BI 服务首次启动的 Flyway 执行。

此开发配置使用本地文件、静态网关路由和 SimpleDiscoveryClient，不要求先搭建 Nacos/Sentinel；没有关闭系统登录或资源权限。原有 Nacos 集群部署仍可使用，见部署文档。

## 3. 启动后端

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1 -CheckOnly
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

默认端口：网关 `8080`、认证 `9200`、系统 `9201`、BI `9206`。脚本会在启动前检查所有端口，冲突时不启动任何服务；需要并行验证时可传 `-GatewayPort 18080 -AuthPort 19200 -SystemPort 19201 -BiPort 19206`。

日志与 PID 记录位于 `.local/runtime/`。启动通常需要几十秒，请确认日志中的 `Started ...Application`，不能只看进程已经创建。停止本脚本启动的服务：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1
```

## 4. 启动前端

```powershell
Copy-Item Wanfo-Cloud-Vue/.env.development.local.example Wanfo-Cloud-Vue/.env.development.local
cd Wanfo-Cloud-Vue
npm run dev
```

打开 `http://localhost:5173`，进入管理后台后点击“BI 工作台”。首次初始化的演示管理员为 `admin`，初始密码 `admin123`，上线前必须修改；登录页不再预填凭据。

如果使用了其他后端端口，在前端 `.env.development.local` 中调整 `VITE_GATEWAY_API_TARGET` 和 `VITE_BI_API_TARGET`。前端开发请求 `/dev-api/bi` 到 BI，其余 `/dev-api` 到网关；生产请求统一走 `/prod-api`。

## 5. 验收主要功能

1. 登录、加载菜单，确认后台仅显示“BI 工作台”统一入口。
2. 配置一个测试数据源并测试连接，创建表数据集。
3. 保存图表，检查图表管理卡片与对应图表一致。
4. 保存栅格看板或自由大屏，返回列表检查对应保存版本的真实布局缩略图。
5. 发布看板后，通过具备相应消费权限的用户检查预览和数据权限。

全新数据库没有业务数据源或看板是正常状态。零售演示 SQL 和操作脚本在 `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/deploy/`，如需导入，请使用独立测试数据源并显式提供演示用户凭据，不要对现有业务库直接执行。

初始化脚本为开发数据库账号授予 `performance_schema.user_variables_by_thread` 单表 SELECT，供 Flyway 检测 MySQL 会话变量；生产迁移账号也需要此权限。不会授予整个 performance_schema 的访问权限。

元数据库使用 MySQL 8.4 LTS；现有 BI 迁移包含 MySQL 8 的排序规则，不能使用 MySQL 5.7。`WANFO_MYSQL_IMAGE` 可覆盖镜像来源。
