# wanfo-bi-cloud

Wanfo BI Cloud 可视化报表平台，基于 RuoYi-Cloud 二次开发。Java 包前缀 `com.wanfo`，前后端在同一个仓库维护。

| 项目 | 目录 | 标识 |
| --- | --- | --- |
| 后端 | [Wanfo-Cloud](./Wanfo-Cloud/) | Java 包前缀 `com.wanfo`，根 Maven 坐标 `com.wanfo:wanfo-cloud:3.6.8` |
| 前端 | [Wanfo-Cloud-Vue](./Wanfo-Cloud-Vue/) | npm 包名 `wanfo-cloud-vue` |

仓库地址：https://github.com/luocheng930202-coder/wanfo-bi-cloud 。原仓库历史已本地备份，新仓库从清理后的源码快照开始维护。

内部 `ruoyi-*` 模块目录、微服务名称、接口权限码和数据库表名保持兼容。项目基于 RuoYi-Cloud 二次开发，原始许可证和来源说明保留在两个工程中。

包名迁移后的运行注意事项见 [迁移说明](./Wanfo-Cloud/doc/WANFO-NAMING-MIGRATION.md)。本机的 `.local`、旧目录残留、日志及 `ruoyi-nacos-config` 是本地运行资料，不作为发布源码。

## 功能

- 数据源分类、连接测试、库表浏览和资源授权。
- 表数据集、字段配置、查询筛选和行列权限。
- 图表编辑、聚合查询、透视表、地图及导出。
- 栅格看板、自由大屏、组件配置、图层、撤销重做和发布消费。
- 图表、看板及大屏管理卡片按保存版本动态生成真实布局预览，沿用查看者的数据权限。
- 本地模板、模板市场接入、数据指标、填报和导出任务管理。
- 后台保留“BI 工作台”统一入口，工具内提供完整 BI 导航。

## 开始使用

核心环境：JDK 17、Maven 3.9、Node.js 24、npm、MySQL 和 Redis。Windows 开发验证另需 PowerShell、Python 3、Docker Compose。完整步骤见 [QUICKSTART](docs/QUICKSTART.md)。

```powershell
mvn -B -ntp -f Wanfo-Cloud/pom.xml verify
npm ci --prefix Wanfo-Cloud-Vue
powershell -ExecutionPolicy Bypass -File scripts/init-local.ps1
docker compose --env-file deploy/.env -f deploy/compose.infra.yml up -d --wait
python scripts/init-db.py
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

随后按 QUICKSTART 复制前端本地配置并执行 `npm run dev`，示例地址 `http://localhost:5173`。全新数据库没有业务数据源/看板属于正常状态，按需连接独立测试数据源。

## 开发检查

```powershell
python scripts/check-release.py
mvn -B -ntp -f Wanfo-Cloud/pom.xml verify
cd Wanfo-Cloud-Vue
npm ci
node --test scripts/*.test.mjs
npm run typecheck:bi
npm run build:prod
```

`typecheck:bi` 只约束 BI 范围，并保留原模板的全项目类型诊断，不能等同于全站类型检查通过。发布准备和验证范围见 [RELEASE-PREP](docs/RELEASE-PREP.md)。

## 配置与文档

真实 `.env`、Nacos 本地配置、登录调试文件、数据库备份、日志、上传和构建产物不入库。`WANFO_JWT_SECRET` 必须由环境注入，至少为 64 个随机字节的 Base64。前端只记住账号，不保存密码或包含私钥。

- [部署说明](docs/DEPLOYMENT.md)
- [安全与凭据处理](SECURITY.md)
- [上游来源与许可](NOTICE.md)
- [发布准备](docs/RELEASE-PREP.md)

保留原始 MIT 许可及版权声明，详见 [LICENSE](LICENSE) 和子工程 LICENSE。

元数据库使用 MySQL 8.4 LTS；现有 BI 迁移包含 MySQL 8 的排序规则，不能使用 MySQL 5.7。`WANFO_MYSQL_IMAGE` 可覆盖镜像来源。
