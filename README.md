# wanfo-bi-cloud

**Wanfo = 企业自助 BI + 可信批处理入仓与轻量治理。** 基于 RuoYi-Cloud 二次开发；Java 包前缀 `com.wanfo`，前后端同仓维护。

| 项目 | 目录 | 标识 |
| --- | --- | --- |
| 后端 | [Wanfo-Cloud](./Wanfo-Cloud/) | Java 包前缀 `com.wanfo`，根 Maven 坐标 `com.wanfo:wanfo-cloud:3.6.8` |
| 前端 | [Wanfo-Cloud-Vue](./Wanfo-Cloud-Vue/) | npm 包名 `wanfo-cloud-vue` |

仓库地址：https://github.com/luocheng930202-coder/wanfo-bi-cloud 。原仓库历史已本地备份，新仓库从清理后的源码快照开始维护。

内部 `ruoyi-*` 模块目录、微服务名称、接口权限码和数据库表名保持兼容。项目基于 RuoYi-Cloud 二次开发，原始许可证和来源说明保留在两个工程中。

包名迁移后的运行注意事项见 [迁移说明](./Wanfo-Cloud/doc/WANFO-NAMING-MIGRATION.md)。本机的 `.local`、旧目录残留、日志及 `ruoyi-nacos-config` 是本地运行资料，不作为发布源码。

## 功能

定位边界见 [产品定位与底座](docs/PRODUCT-POSITIONING-AND-FOUNDATION.md)。**不是** DataWorks 平替、湖仓一体或内嵌 AI Copilot（后置可选，不在当前交付范围）。

### 分析侧

- 数据源分类、连接测试、库表浏览与资源授权。
- 表/SQL 数据集、字段配置、关联建模、行列策略与查询治理。
- 图表（聚合、透视、地图）、栅格看板、自由大屏、模板、填报、订阅与导出。
- 工作台 **分析 | 数据中心** 双域壳；三套可切换 UI 主题。

### 语义与治理

- 标准指标：原子 / 派生 / 复合（schema v2），定义与查询分离，发布冻结与口径取数。
- 术语、字段标准、资产目录与血缘、分级脱敏与访问申请。

### 加工与仓

- 有界装载与对账、对象/CDC 编排入口、版本化管道 DAG、质量门禁、任务泳道与查询监控。

### 数据服务

- 受控 Data API：Key、配额、版本族、契约门禁、金丝雀与调用审计。

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

- [产品定位与底座](docs/PRODUCT-POSITIONING-AND-FOUNDATION.md)（是什么 / 不是什么）
- [系统功能设计原理](docs/SYSTEM-DESIGN.md)（实现级数据流 / 表 / 门禁）
- [商业价值开发清单](docs/COMMERCIAL-VALUE-BACKLOG.md)（后续排期，非当前冲刺）
- [本地快速开始](docs/QUICKSTART.md)
- [部署说明](docs/DEPLOYMENT.md)
- [安全与凭据处理](SECURITY.md)
- [上游来源与许可](NOTICE.md)
- [发布准备](docs/RELEASE-PREP.md)

保留原始 MIT 许可及版权声明，详见 [LICENSE](LICENSE) 和子工程 LICENSE。

元数据库使用 MySQL 8.4 LTS；现有 BI 迁移包含 MySQL 8 的排序规则，不能使用 MySQL 5.7。`WANFO_MYSQL_IMAGE` 可覆盖镜像来源。
