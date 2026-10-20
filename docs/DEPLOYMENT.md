# 部署说明

## 构建和服务关系

生产前先在干净目录执行 README 中的构建和测试。核心运行组件为 MySQL、Redis、网关、认证、系统和 BI；文件上传、代码生成、调度、监控按需部署。Java 17；前端 Node 24 构建后只需静态文件服务。

| 服务 | 构建产物（Wanfo-Cloud 下） | 默认端口 |
| --- | --- | --- |
| 认证 | ruoyi-auth/target/ruoyi-auth.jar | 9200 |
| 系统 | ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar | 9201 |
| 网关 | ruoyi-gateway/target/ruoyi-gateway.jar | 8080 |
| BI | ruoyi-modules/ruoyi-bi/target/ruoyi-modules-bi.jar | 9206 |

`deploy/compose.infra.yml` 是隔离开发基础设施示例，不是整套生产一键部署。它不包含应用镜像、TLS、备份、数据库高可用或监控。已有 `Wanfo-Cloud/docker/` 是上游部署参考，也不应直接套用到生产；凭据需从环境变量注入。

## 配置与密钥

从 `deploy/config/*.example` 生成环境专属配置，存放在 Git 外，统一配置数据库、Redis 和 `com.wanfo` MyBatis 扫描。所有后端服务注入相同 `WANFO_JWT_SECRET`，内容为至少 64 个随机字节的 Base64；不要复用代码中旧的公开示例密钥。密钥仅由部署系统或服务器环境管理。

单机文件配置模式可参考 `scripts/start-local.ps1` 中的参数：

```bash
export WANFO_JWT_SECRET='<由密钥系统注入>'
export WANFO_DB_USER='<数据库用户>'
export WANFO_DB_PASSWORD='<由密钥系统注入>'
export WANFO_REDIS_PASSWORD='<由密钥系统注入>'
java -jar <服务.jar> \
  --spring.config.import=optional:file:/etc/wanfo/application-dev.yml \
  --spring.config.additional-location=optional:file:/etc/wanfo/
```

网关还需显式加载 `ruoyi-gateway-dev.yml`；BI 额外指定 `--spring.flyway.enabled=true`，其他服务不启用 BI Flyway。设置生产监听地址和服务 URI；内部认证、系统、BI、Redis 和数据库端口不直接对公网开放。

使用 Nacos 服务发现/配置中心时，应提供各个服务对应的 Data ID，保留 `ruoyi-*` 标识，显式启用 Nacos 组件，并替换单机的静态路由及 simple-discovery 配置。配置中的旧包名前缀需调整为 `com.wanfo`。Nacos 配置本身及认证凭据不入库。代码生成、MyBatis、定时任务的历史全限定类名迁移见 [命名迁移说明](../Wanfo-Cloud/doc/WANFO-NAMING-MIGRATION.md)。

## 数据库

- 全新系统：在空系统数据库运行 `Wanfo-Cloud/sql/ry_20260417.sql`，再运行 `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/deploy/ruoyi-menu.sql`，之后启动 BI 执行 Flyway。
- 已有系统：先备份，不重跑包含 DROP TABLE 的初始化脚本；核对已执行的 Flyway 版本，升级 BI 并执行经审核的菜单更新。
- `Wanfo-Cloud/sql/ry_config_20260918.sql` 是历史 Nacos 配置库示例，不是业务库初始化脚本，单机文件模式不需要导入它。
- 演示 SQL 是可选测试数据，不能用生产数据备份代替初始化资料。
- 修改管理员默认密码，设置角色、菜单及 BI 资源授权，再录入真实数据源。

## 前端和反向代理

执行 `npm ci && npm run build:prod`，发布 `Wanfo-Cloud-Vue/dist/`。参考 `deploy/nginx.conf.example`：路由使用 SPA fallback；`/prod-api/` 转发到网关且去掉该前缀；网关中的 `/bi/**` 保留 `/bi` 前缀。文件中 `gateway` 为示例内部服务名，请替换成实际地址。配置正式域名、TLS 和适当超时。

## 上线验收与回滚

核对服务启动、登录、菜单、数据源连接、数据集查询、图表保存、看板发布、数据权限及缩略图更新。缩略图按保存定义动态渲染，读取当前用户有权访问的数据，不是共享的静态截图。

保留上个版本制品、配置与数据库备份。回滚代码前确认数据库迁移兼容；不要直接删除 Flyway 历史或回退 SQL。JWT 密钥或包名变化可能使旧会话失效，应重新登录。

初始化脚本为开发数据库账号授予 `performance_schema.user_variables_by_thread` 单表 SELECT，供 Flyway 检测 MySQL 会话变量；生产迁移账号也需要此权限。不会授予整个 performance_schema 的访问权限。

元数据库使用 MySQL 8.4 LTS；现有 BI 迁移包含 MySQL 8 的排序规则，不能使用 MySQL 5.7。`WANFO_MYSQL_IMAGE` 可覆盖镜像来源。
