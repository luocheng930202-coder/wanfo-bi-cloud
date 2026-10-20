# DataEase v3 架构与代码全面分析

> **分析对象**：`D:\bi-tools\dataease`（Git 分支 `dev-v3`，commit `4330aca`，2026-10-14）
> **分析方式**：源码逐层实测，所有行数、类名、路径均来自实际扫描，非估算。
> **分析日期**：2026-10-15　|　**版本对应**：v3.1.0 官方稳定版 / 源码 dev-v3 最新

---

## 一、总体架构

### 1.1 工程规模（实测）

| 一级模块 | 文件数 | 说明 |
|---|---:|---|
| `core/` | 1,765 | 核心应用（后端 + 前端） |
| `sdk/` | 734 | 对外 SDK（API 契约 + 插件 SPI） |
| `tests/` | 4 | 测试（**几乎为零**，见 1.5） |
| `installer/` | 1 | 安装器 |
| `de-xpack/` | **0** | 商业版模块，**空目录** |
| `drivers/` | 0 | JDBC 驱动（运行时挂载） |
| `mapFiles/` | 0 | 地图底图数据 |
| `staticResource/` | 0 | 静态资源（运行时） |

**后端 Java 代码量**（`io.dataease` 包下 26 个子包）：

| 包 | 文件 | 行数 | 职责 |
|---|---:|---:|---|
| `chart` | 58 | 10,519 | 图表取数、数据转换、Handler |
| `datasource` | 61 | 9,856 | 数据源、Calcite 联邦引擎 |
| `visualization` | 77 | 7,449 | 大屏、联动、跳转、导出 |
| `dataset` | 26 | 4,833 | 数据集、SQL 生成 |
| `listener` | 18 | 4,796 | 启动初始化、事件监听 |
| `engine` | 16 | 2,841 | SQL AST 构造、过滤树转换 |
| `commons` | 14 | 2,317 | 通用工具、注解、枚举 |
| `exportCenter` | 10 | 1,655 | 导出中心 |
| `share` | 17 | 1,643 | 分享体系 |
| `template` | 14 | 1,225 | 模板市场 |
| `job` | 12 | 997 | 调度任务执行器 |
| `map` | 13 | 989 | 地图服务 |
| `system` | 9 | 664 | 系统级权限 |
| `config` | 11 | 614 | 配置类 |
| `qrtz` | 22 | 530 | Quartz 调度表实体 |
| `font` | 5 | 493 | 字体管理 |
| `websocket` | 10 | 294 | WebSocket 推送 |
| 其余 10 个小包（menu / operation / substitute / home / resource / license / startup / ai / defeign / msgCenter） | 23 | 约 1,010 | 菜单、操作日志、权限降级、初始化、AI、Feign |
| **合计** | **423** | **约 52,457** | |

### 1.2 技术栈

| 层 | 选型 | 版本 | 实测依据 |
|---|---|---|---|
| 语言 | Java | **21** | 根 pom.xml 中 `java.version=21` |
| 框架 | Spring Boot | **3.5.15** | spring-boot-starter-parent |
| 微服务 | Spring Cloud Alibaba | BOM 托管 | spring-cloud-alibaba-dependencies + Nacos |
| ORM | **JPA + MyBatis Plus 双轨** | 随 SB | @EnableJpaRepositories 与 mybatis.mapper-locations 并存 |
| SQL 引擎 | **Apache Calcite** | 随依赖 | org.apache.calcite.sql.SqlParser / SqlNode / SqlDialect |
| 缓存 | EhCache（JCache）+ Redis | — | spring.cache.type=jcache 指向 classpath:ehcache/ehcache.xml |
| 调度 | Quartz（自管） | 随 SB | exclude = QuartzAutoConfiguration.class + quartz.scheduler-name: deSyncJob |
| 配置加密 | jasypt | — | @EnableEncryptableProperties（com.ulisesbocchio） |
| 数据库迁移 | **Flyway 已禁用** | — | 所有 Profile 均 flyway.enabled: false，改用 ddl-auto: update |
| API 文档 | Knife4j（已排除） | — | spring.autoconfigure.exclude 中列出 |
| Web 容器 | Tomcat（内嵌） | 随 SB | max-http-request-header-size: 500KB（大 SQL 查询需要） |

**前端**：Vue 3.5.35 + Vite 6.4.3 + TypeScript 4.9.3 + Pinia 2.0.32 + Vue Router 4.1.3 + Vue I18n 9.2.2；图表库 **ECharts 5.5.1 + AntV G2 5.4.8 + AntV S2 2.5.0 + AntV L7 / L7Plot**（地图）；UI 库使用 **`element-plus-secondary`**（DataEase 自维护的定制版，**不是**社区版 element-plus）；编辑器 vue3-ace-editor + vue-codemirror。

### 1.3 三种部署形态

从 `core-backend/src/main/resources/application-*.yml` 实测：

| Profile | 元数据库 | 服务发现 | 用途 |
|---|---|---|---|
| `standalone` | **MariaDB/MySQL**，jdbc:mariadb://localhost:3306/dataeasev3 | 无 | 单机部署（**当前使用**） |
| `distributed` | MariaDB de_manage + 动态数据源 official-ds | **Nacos** 127.0.0.1:8848 | 集群部署 |
| `desktop` | **H2 文件库** /opt/dataease3.0/desktop，MODE=MySQL | 无 | 桌面版，零外部依赖 |

`desktop` Profile 值得注意：H2 以 MySQL 兼容模式运行并开启 `CASE_INSENSITIVE_IDENTIFIERS`，说明产品目标是「单机桌面即可跑，无需 DBA 介入」；该 Profile 绑定 `server.address: 127.0.0.1` 且开启 H2 控制台 `/h2-console`。

### 1.4 架构分层

~~~text
+--------------------------------------------------+
|  前端 SPA (Vue3 + Vite)，编译产物内嵌进 Spring Boot    |
+--------------------------------------------------+
|  REST / WebSocket   (/de2api/**, /websocket/**)   |
+--------------------------------------------------+
|  Server 层  (*Server.java)  参数校验、DTO、事务     |
+--------------------------------------------------+
|  Manage 层  (*Manage.java)  业务编排、缓存、权限注入  |
+--------------------------------------------------+
|  Provider 层  数据源抽象 + Calcite 联邦查询 + 方言转换 |
+--------------------------------------------------+
|  SDK  api-base / api-permissions / api-sync / ext    |
|  接口契约在此，具体实现可落在 core 或 xpack           |
+--------------------------------------------------+
|  元数据库(MySQL) + Redis + EhCache + Quartz(qrtz_*)  |
+--------------------------------------------------+
~~~

分层命名高度一致：`Server`（Web 接口）→ `Manage`（业务）→ `Provider / Utils`（基础能力）。数据库实体统一以 `Core` 开头（如 `CoreDatasource`、`CoreVisualization`），与 X-Pack 可替换的增强实体在命名上形成呼应。

### 1.5 值得注意的工程现状

| 现象 | 实测 | 影响 |
|---|---|---|
| **单元测试近乎为零** | `tests/` 仅 4 个文件 | 上万行核心逻辑无回归保障，二次开发风险高 |
| **数据库迁移未使用 Flyway** | 所有 Profile flyway.enabled: false + ddl-auto: update | 依赖 Hibernate 自动改表，跨大版本升级有风险 |
| **商业版为空目录** | `de-xpack/` 0 文件 | 所有 xpack 能力在社区版直接降级 |
| **i18n 含空壳 bundle** | basename: i18n/lic,i18n/core,i18n/permissions,i18n/xpack,i18n/sync | permissions / xpack 在社区版无内容 |
| **启动逻辑异常重** | `listener/CoreDataInit.java` 单文件 **3,960 行** | 首次启动导入大量演示数据，是启动期最大风险点 |


---

## 二、后端架构

### 2.1 启动流程

入口类 `io.dataease.CoreApplication`：

~~~java
@SpringBootApplication(exclude = {QuartzAutoConfiguration.class})
@EnableCaching
@EnableScheduling
@EnableJpaRepositories(basePackages = "io.dataease")
@EnableEncryptableProperties
public class CoreApplication {
    public static void main(String[] args) {
        System.setProperty("AMAZON_REDSHIFT_JDBC_INI_FILE", "null");
        System.setProperty("java.io.tmpdir", "null");
        SpringApplication context = new SpringApplication(CoreApplication.class);
        context.addInitializers(new EhCacheStartListener());
        context.run(args);
    }
}
~~~

逐行解读：

1. **exclude = QuartzAutoConfiguration**：不让 Spring Boot 自动装配 Quartz 调度器，改为自管（避免误用内置内存 JobStore）。
2. **@EnableScheduling**：开启 Spring 原生定时，与 Quartz 并存——轻量巡检用 Spring，重量级任务用 Quartz。
3. **@EnableJpaRepositories("io.dataease")**：JPA Repository 扫描。
4. **@EnableEncryptableProperties**：jasypt，配置文件中的 ENC(...) 可存密文。
5. **AMAZON_REDSHIFT_JDBC_INI_FILE=null**：Redshift 驱动会尝试读 ini 文件并抛异常，置 null 规避。
6. **java.io.tmpdir=null**：同类兼容性规避手段（部分驱动用 temp 文件判定运行环境）。
7. **EhCacheStartListener**：EhCache 需早于 Spring 初始化，作为 ApplicationContextInitializer 注入。

### 2.2 启动期监听器（listener 包）

| 类 | 行数 | 职责 |
|---|---:|---|
| `CoreDataInit` | **3,960** | 演示数据、内置数据集、模板、Excel 示例初始化 |
| `DemoDataInitUtils` | 279 | 演示数据工具 |
| `InitSqlListener` | 110 | 内置 SQL 初始化 |
| `DataSourceInitStartListener` | 76 | 内置数据源初始化 |
| `XpackTaskStarter` | 42 | 启动 X-Pack 后台任务（社区版空转） |
| `DemoTeaOrder` / `DemoTeaMaterial` | 50 / 33 | 内置演示业务（茶点订单、物料） |
| `DatasetCrossListener` | 38 | 跨源数据集变更事件 |
| `ChartFilterMergeListener` | 38 | 图表过滤器合并 |
| `ChartFilterDynamicListener` | 38 | 图表动态过滤器 |
| `CoreDataVersion3` | 31 | V3 版本数据迁移 |
| `TemplateInitListener` | 24 | 模板初始化 |

`CoreDataInit` 单文件近 4,000 行，是启动期性能与稳定性的主要风险点。

### 2.3 双 ORM 并存

~~~yaml
# application.yml
mybatis:
  configuration:
    map-underscore-to-camel-case: true

# 各 application-*.yml
mybatis-plus:
  mapper-locations: classpath:mybatis/*.xml
spring:
  jpa:
    hibernate:
      naming:
        physical-strategy: io.dataease.config.DynamicCaseNamingStrategy
      ddl-auto: update
~~~

- **JPA Repository**：实测存在 `CoreDatasetGroupRepository`、`CoreExportTaskRepository`、`CoreExportDownloadTaskRepository`、`CoreDatasetTableSqlLogRepository` 等
- **MyBatis**：classpath:mybatis/*.xml 承载复杂查询
- **DynamicCaseNamingStrategy**：自定义物理命名策略，说明表名/列名大小写规则非标准默认（跨数据库兼容需要）

**二次开发影响**：改数据访问层前需先判断目标表走 JPA Repository 还是 MyBatis Mapper，两套并存会明显增加排查成本。

### 2.4 缓存体系

- **本地缓存**：`ehcache/ehcache.xml`，由 `EhCacheStartListener` 预初始化
- **Redis**：`127.0.0.1:6379`，承载用户语言偏好（`CacheConstant.UserCacheConstant.USER_COMMUNITY_LANGUAGE`）等跨实例状态
- 代码中大量使用静态工具 `CacheUtils.get / put`（见 `SubstituteUserServer`）

**注意**：`management.health.redis.enabled: false` —— 健康检查不探活 Redis，故障要到运行时才暴露。

### 2.5 调度体系（Quartz + Spring 双轨）

`qrtz` 包下 22 个实体（`QrtzTriggers`、`QrtzCronTriggers`、`QrtzSimpleTriggers`、`QrtzBlobTrigger`、`QrtzSimpropTrigger`、`QrtzLock`、`QrtzSchedulerState` 等）是 **Quartz 官方 QRTZ_* 表的 JPA 映射**，用于集群模式下加锁，避免同一任务被多实例重复执行。

| 类 | 行数 | 职责 | 归属 |
|---|---:|---|---|
| `ScheduleManager` | 503 | 调度总控 | core |
| `DeTaskExecutor` | 114 | 内置任务执行 | core |
| `DeDataFillingTaskExecutor` | 96 | 数据填充任务（智能问数） | core |
| `DeXpackDataSyncTaskExecutor` | 91 | **数据同步任务** | **xpack** |
| `DeXpackDataSyncTaskScheduleJob` | 32 | 同步调度作业 | **xpack** |
| `DeXpackScheduleJob` | 28 | X-Pack 作业 | **xpack** |
| `DeXpackDataFillingScheduleJob` | 27 | X-Pack 数据填充作业 | **xpack** |
| `CheckDsStatusJob` | 28 | 数据源状态巡检 | core |
| `CleanScheduler` | 26 | 调度器清理 | core |
| `ExtractDataJob` | 16 | 数据抽取 | core |

`scheduler-name: deSyncJob` 是集群内调度器标识。集群部署依赖 Quartz 的 `QRTZ_LOCK` 表保证互斥。

---

## 三、SDK 与插件体系

### 3.1 SDK 模块结构（734 文件）

| 模块 | 文件数 | 职责 |
|---|---:|---|
| `sdk/api/api-base` | 323 | 基础 API 契约（数据源、数据集、图表、可视化、分享、日历…） |
| `sdk/common` | 134 | 公共工具、常量、枚举 |
| `sdk/api/api-permissions` | 130 | **权限契约（17 个 Api 接口，零实现）** |
| `sdk/extensions/extensions-view` | 51 | 视图/图表扩展 |
| `sdk/extensions/extensions-datasource` | 33 | **数据源插件 SPI** |
| `sdk/extensions/extensions-sync` | 29 | 数据同步扩展 |
| `sdk/extensions/extensions-datafilling` | 10 | 数据填充（智能问数） |

SDK 采用「**契约先行**」设计：接口与 DTO 全部定义在 `sdk/api`，实现可以落在 `core`（社区能力）或 X-Pack（商业能力）。这也解释了为什么社区版能把接口全部公开而功能留空。

### 3.2 数据源插件 SPI：Provider 抽象类

`sdk/extensions/extensions-datasource/.../provider/Provider.java` 是整个数据接入层的核心契约。

**抽象方法（7 个，所有数据源必须实现）**：

| 方法 | 返回 | 用途 |
|---|---|---|
| `getSchema` | List of String | 列出 schema（库） |
| `getTables` | List of DatasetTableDTO | 列出表 |
| `getConnection` | ConnectionObj | 建立连接 |
| `checkStatus` | String | 健康检查（返回 Error 表示不可用） |
| `fetchResultField` | Map | **执行查询并返回数据** |
| `fetchTableField` | List of TableField | 获取表字段 |
| `hidePW` | void | 脱敏密码（返回给前端时） |

**默认方法（10 个，通用能力，插件可选覆写）**：

| 方法 | 作用 |
|---|---|
| `exec / execDDL / executeUpdate` | 执行写操作（默认空实现，说明多数数据源按只读处理） |
| `supportsWindowFunctions` | **是否支持窗口函数**（箱线图等统计图需要），默认 false → 走 Java 回退 |
| `rebuildSQL` | Calcite SQL → 目标方言 SQL |
| `transSqlDialect` | **方言转换核心**（见 3.3） |
| `replaceTablePlaceHolder` | 表占位符替换 |
| `getDialect` | 数据源类型 → Calcite SqlDialect |
| `startSshSession / initSession` | **SSH 隧道**（JSch） |

### 3.3 Calcite 方言转换机制

这是 DataEase 能同时支持十几种数据库且允许跨源 JOIN 的技术基础。

~~~java
public String transSqlDialect(String sql, Map<Long, DatasourceSchemaDTO> dsMap, boolean forSqlbot) {
    // 1. 取连接拿数据库版本（方言微调需要）
    try (ConnectionObj connection = getConnection(value)) {
        if (connection != null) {
            value.setDsVersion(connection.getConnection().getMetaData().getDatabaseMajorVersion());
        }
    }
    // 2. Calcite 解析
    SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT.withLex(Lex.JAVA));
    SqlNode sqlNode = parser.parseStmt();
    // 3. 按目标方言重新输出
    String dialect = sqlNode.toSqlString(getDialect(value)).toString();
    // 4. SQL Server 特例修正
    if (StringUtils.equalsIgnoreCase(value.getType(), "sqlServer")) {
        dialect = dialect.replaceAll("\\[CONCAT]", "CONCAT");
    }
    return dialect;
}
~~~

**方言映射表**（getDialect 实测）：

| DataEase 类型 | Calcite SqlDialect |
|---|---|
| mysql / mongo / StarRocks / TiDB / mariadb | MysqlSqlDialect.DEFAULT |
| **doris** | DorisSqlDialect.DEFAULT（Calcite 内置） |
| impala | ImpalaSqlDialect.DEFAULT |
| sqlServer | MssqlSqlDialect（**带 version**，SQL Server 2012+ 语法差异大） |
| oracle | OracleSqlDialect（**带 version**） |
| db2 | Db2SqlDialect.DEFAULT |
| pg / kingbase / gaussdb | PostgresqlSqlDialect.DEFAULT |
| redshift | RedshiftSqlDialect.DEFAULT |
| ck | ClickHouseSqlDialect（**带 version**） |
| h2 | H2SqlDialect.DEFAULT |
| es | EsSqlDialect.DEFAULT（自定义方言） |
| 默认 | MysqlSqlDialect.DEFAULT |

**两个关键的字段级方言适配**：

~~~java
// 1) MSSQL 中文 N 前缀：脱敏值里的中文字符串需加 N 前缀，否则乱码
public String replaceMssqlN(String s) {
    Pattern compile = Pattern.compile("'-DENS-.*?'");
    Matcher matcher = compile.matcher(s);
    while (matcher.find()) {
        String v = matcher.group();
        s = s.replace(v, "N" + v.replace("-DENS-", ""));
    }
    return s;
}

// 2) 计算字段占位符替换：把占位符 KEY 换成各方言表达式
public String replaceCalcFieldPlaceHolder(String s, SQLMeta sqlMeta) {
    // 收集 xFieldsDialect / yFieldsDialect / customWheresDialect
    // / extWheresDialect / whereTreesDialect 五类映射后统一替换
}
~~~

### 3.4 热插拔插件机制

`DataEaseDatasourcePlugin.java` 继承 `Provider` 并实现 `DataEasePlugin`，负责插件生命周期：

| 生命周期 | 实现要点 |
|---|---|
| **加载** loadPlugin() | ① ProviderFactory.loadPlugin(type, this) 注册 Provider；② loadDriver() 将插件 jar 内嵌的驱动 jar 解压到 /opt/dataease3.0/drivers/plugin/类型/ |
| **卸载** unloadPlugin() | 遍历 jar 条目，删除已解压的驱动文件 |
| **元数据** | DatasourcePluginInfoLoader.load(this) 读取插件描述（类型、图标、配置 JSON） |

**安全设计（Zip Slip 防护）** —— 代码实现相当严谨：

~~~java
private static final Pattern SAFE_DRIVER_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.jar");

private Path resolveDriverPath(Path localPath, JarEntry entry) {
    String fileName = extractSafeDriverFileName(entry);
    Path targetFile = localPath.resolve(fileName).normalize();
    if (!targetFile.startsWith(localPath)) {
        DEException.throwException("Invalid driver file path");   // 路径穿越校验
    }
    return targetFile;
}

private String extractSafeDriverFileName(JarEntry entry) {
    String entryName = entry.getName();
    if (StringUtils.contains(entryName, "..")
            || StringUtils.startsWith(entryName, "/")
            || StringUtils.startsWith(entryName, "\\\\")
            || StringUtils.contains(entryName, ":")) {
        DEException.throwException("Invalid driver entry path");
    }
    ...
}
~~~

三重防护：拒绝双点、拒绝绝对路径、拒绝盘符、文件名白名单正则、最终路径必须仍在目标目录内。这是**值得学习的安全实现**。

**路径兼容处理**：`resolveDriverDirectory()` 会把历史元数据中的 `/opt/dataease3.0/drivers/plugin` 映射到当前 `dataease.path.driver/plugin` 配置项，避免「从新目录加载却误删旧目录文件」。

### 3.5 JDBC 驱动隔离

`ExtendedJdbcClassLoader` + `DriverShim`：每个数据源插件的驱动装在**独立 ClassLoader** 中，解决不同厂商驱动版本冲突、以及驱动依赖互相污染的问题。这是多数据源 BI 的必要设计。

### 3.6 SSH 隧道

`Provider.startSshSession()` + `initSession()`，基于 JSch：

- 支持**密码**与**公钥**（PreferredAuthentications=publickey + addIdentity）两种认证
- 端口转发：本地端口在 **10000~20000** 区间动态分配（getLport() + isPortAvailable() 探活）
- 会话复用：sessions Map 按数据源 ID 缓存，断开时自动重连
- `StrictHostKeyChecking = "no"`（**安全性妥协**，跳过主机密钥校验）

**因此 DataEase 可以访问内网数据库，不需要数据库暴露公网** —— 对内网部署场景价值很大。


---

## 四、数据接入层（datasource，61 文件 / 9,856 行）

### 4.1 核心类清单

| 类 | 行数 | 职责 |
|---|---:|---|
| **CalciteProvider** | **2,104** | **联邦查询引擎：所有 JDBC 数据源的统一入口** |
| DatasourceServer | 1,594 | REST 接口层（最大 Server 类） |
| ApiUtils | 912 | API 数据源处理（JSON / CSV 等外部接口） |
| ExcelUtils | 888 | Excel 数据源处理 |
| EngineManage | 533 | 内置引擎管理（H2，Excel / API 数据源的落地载体） |
| DatasourceSyncManage | 385 | 数据源同步 |
| DataSourceManage | 231 | 数据源 CRUD |
| JdbcUrlSecurityPolicy | 227 | **JDBC URL 安全策略**（防 SSRF / 注入） |
| EsProvider | 189 | Elasticsearch 数据源 |
| DatasourceTaskServer | 173 | 数据源任务（校验等） |
| SqlServerEngineProvider | 157 | SQL Server 引擎 |
| PgEngineProvider | 144 | PostgreSQL 引擎 |
| MysqlEngineProvider | 140 | MySQL 引擎 |
| OracleEngineProvider | 136 | Oracle 引擎 |
| DmEngineProvider | 136 | 达梦引擎 |
| H2EngineProvider | 118 | H2 内置引擎 |
| CK.java | 118 | ClickHouse |
| StarRocksStreamLoadUtil | 116 | **StarRocks Stream Load 写入** |

**关键认识**：`CalciteProvider` 用 2,104 行承载了所有标准 JDBC 数据源的核心逻辑，其余 `XxxEngineProvider` 只覆盖各数据库的**差异特性**。这是典型的「模板方法 + 方言钩子」结构，新增数据库的成本被大幅摊薄。

### 4.2 JDBC URL 安全策略

`JdbcUrlSecurityPolicy.java`（227 行）是 v3 新增的安全组件，对 JDBC URL 做白名单校验，防止通过数据源配置实现 **SSRF**（连内网任意端口）或 **JDBC 攻击**（部分驱动支持特定 URL 形式触发反序列化）。这是 B 端平台必须具备的能力，也是 Datart 所欠缺的。

### 4.3 内置引擎与外部数据源

Excel 和 API 数据源无法直接被 JDBC 查询，DataEase 的做法是先落到内置引擎，再走同一条查询链路：

~~~text
Excel 文件 / 外部 API
        |
        v  ExcelUtils / ApiUtils 解析
    EngineManage（H2 内嵌引擎，MySQL 兼容模式）
        |
        v  之后与普通 JDBC 数据源走同一条 Calcite 链路
    CalciteProvider --> 统一查询
~~~

这个设计让**所有数据源在查询层被统一抽象**，代价是 Excel / API 数据有落地延迟，不适合高频写入场景。

---

## 五、数据集层（dataset，26 文件 / 4,833 行）

### 5.1 核心类

| 类 | 行数 | 职责 |
|---|---:|---|
| **DatasetDataManage** | **1,129** | **数据集取数核心**：预览、枚举值、字段值树 |
| DatasetSQLBotManage | 701 | AI SQL Bot（自然语言转 SQL） |
| DatasetGroupManage | 654 | 数据集组管理 |
| DatasetSQLManage | 576 | SQL 数据集生成、union SQL |
| DatasetTableFieldManage | 330 | 字段管理 |
| PermissionManage | 321 | **数据集行列权限** |
| DatasetCacheManage | 86 | 数据集缓存 |
| DatasetUtils / SqlUtils / FieldUtils / TableUtils | 134 / 106 / 79 / 52 | 工具类 |
| DatasetTreeServer | 103 | 目录树接口 |

### 5.2 数据集类型

| 类型 | 说明 | 实现载体 |
|---|---|---|
| 数据库表 | 直接选表 | Provider 直连 |
| **SQL 数据集** | 手写 SQL | Calcite 解析 → 方言转换 |
| API 数据集 | 外部 HTTP 接口 | ApiUtils → 落到 H2 |
| Excel 数据集 | 上传表格 | ExcelUtils → 落到 H2 |
| 跨源数据集 | 多库 JOIN | isCross=true + Calcite 联邦 |

### 5.3 预览执行完整链路（previewSql 源码级解读）

`DatasetDataManage.previewSql()` 的 10 个步骤：

| 步 | 操作 | 说明 |
|---|---|---|
| 1 | 取 CoreDatasource，判断类型 | API / Excel 用内置引擎 schema，其余用数据源本身 |
| 2 | 校验 status 不等于 Error | 数据源不可用直接抛错 |
| 3 | 设置 **schemaAlias** | 按数据源 ID 生成，跨源关联用 |
| 4 | ProviderFactory.getProvider(type) | 取得对应 Provider |
| 5 | Base64 解码用户 SQL，replaceComment 去注释 | 前端传的是 Base64 |
| 6 | handleVariableDefaultValueWithPreparedParams | **SQL 变量替换 + 默认值 + 预处理参数** |
| 7 | 判断 isCross 分两支 | 跨源走 putObj2Map，单源走占位符替换 |
| 8 | Utils.isNeedOrder(dsList) | **Oracle / SQL Server 等必须 ORDER BY 才能 LIMIT**，需先取字段再加 order |
| 9 | provider.transSqlDialect | Calcite 方言转换 |
| 10 | replaceTablePlaceHolder → fetchResultField | 把用户 SQL 塞进标准骨架后执行 |

最终 SQL 形如：

~~~sql
SELECT * FROM ( <用户SQL> ) AS <schemaAlias> ORDER BY 首字段 ASC LIMIT 100
~~~

返回时把 SQL 再 Base64 编码回前端 —— **前端能看到 DataEase 实际执行的 SQL**，这对排查问题、以及「导出带 SQL 的分析结果」功能很重要。

### 5.4 SQL 变量机制

`handleVariableDefaultValueWithPreparedParams` 支持在 SQL 中使用变量（写法为美元符号加大括号的占位形式），变量值可来自大屏外部参数、URL 参数或默认值。这是 BI 工具做参数化查询的标准能力，也是嵌入式集成的关键路径：外部参数 → SQL 变量 → 过滤数据。

---

## 六、SQL 引擎（engine，16 文件 / 2,841 行）

### 6.1 AST 构造器模式

`engine` 包全部围绕「把 JSON 图表定义翻译成 SQL」：

| 类 | 行数 | 职责 |
|---|---:|---|
| Utils | 598 | 引擎工具 |
| CustomWhere2Str | 427 | **自定义过滤器** → SQL 条件 |
| ExtWhere2Str | 412 | **扩展过滤器** → SQL 条件 |
| WhereTree2Str | 316 | **过滤树** → SQL 条件（**权限注入也走这里**） |
| SQLProvider | 178 | SQL 生成总入口 |
| Quota2SQLObj | 172 | 指标（度量）→ SQL 片段 |
| Dimension2SQLObj | 158 | 维度 → SQL 片段 |
| Field2SQLObj | 157 | 字段 → SQL 片段 |
| Order2SQLObj | 122 | 排序 → SQL 片段 |
| SqlTemplate | 82 | SQL 模板 |
| DatasetOrder2SQLObj | 37 | 数据集级排序 |
| Table2SQLObj | 24 | 表 → SQL 片段 |

**设计要点**：`*2SQLObj` 命名表示「X 转换为 SQL 对象」，维度 / 度量 / 排序 / 过滤器各有独立转换器，组合成完整查询。这让新增图表类型时只需复用这些转换器，而不必重写 SQL 生成逻辑。

### 6.2 三个过滤转换器的区别

| 转换器 | 输入 | 用途 |
|---|---|---|
| `WhereTree2Str` | 过滤树（WhereTreeObj） | **可视化配置的筛选器 + 行级权限**，结构化树 |
| `CustomWhere2Str` | 自定义 SQL 片段 | 用户手写的自定义条件 |
| `ExtWhere2Str` | 扩展条件 | 扩展过滤逻辑 |

三者最终都产出 SQL 片段并合并到 `SQLMeta`，再由 `replaceCalcFieldPlaceHolder` 做方言映射。

### 6.3 占位符机制

`SqlPlaceholderConstants` 定义三类占位符：

| 常量 | 用途 |
|---|---|
| TABLE_PLACEHOLDER / TABLE_PLACEHOLDER_REGEX | 表名占位（用户 SQL 内联点） |
| KEYWORD_PREFIX_REGEX | 字段方言映射前缀 |
| KEYWORD_SUFFIX_REGEX | 字段方言映射后缀 |

完整流程：

~~~text
JSON 图表定义
   |
   v  Dimension2SQLObj / Quota2SQLObj / Order2SQLObj
   v  WhereTree2Str / CustomWhere2Str / ExtWhere2Str
带占位符的标准 SQL（Calcite 方言）
   |
   v  replaceCalcFieldPlaceHolder   注入各方言表达式
   v  transSqlDialect                整体转为目标数据库方言
最终可执行 SQL
~~~

---

## 七、图表引擎（chart，58 文件 / 10,519 行）

### 7.1 核心类

| 类 | 行数 | 职责 |
|---|---:|---|
| **ChartDataBuild**（chart/utils/） | **1,840** | **数据转换：把原始行数据变成各图表库需要的数据结构** |
| ChartDataServer | 1,166 | 图表数据 REST 接口 |
| DefaultChartHandler | 956 | 默认图表处理器 |
| ChartDataManage | 892 | 图表取数编排 + 权限注入 |
| ChartViewThresholdManage | 648 | 阈值告警 |
| ChartViewManege | 627 | 图表视图管理 |
| BoxPlotHandler | 360 | 箱线图 |
| TablePivotHandler | 351 | 透视表 |
| TableNormalHandler | 336 | 普通表格 |
| TableInfoHandler | 237 | 明细表 |
| MixHandler | 232 | 混合图表（多坐标系） |
| BoxPlotSqlBuilder | 209 | 箱线图 SQL 构建 |

### 7.2 取数链路（ChartDataManage.sqlData，源码级解读）

这是 Dashboard 点击「刷新」时走的完整链路：

| 步 | 操作 | 关键代码 |
|---|---|---|
| 1 | 取图表全部字段 | getAllChartFields(view) |
| 2 | 按图表类型合并 xAxis | table-pivot / group / antv-line / flow-map / t-heatmap / sankey / box-plot 需把 xAxisExt 并入 xAxis |
| 3 | 特殊类型清空轴 | label / table-info 清 yAxis；indicator / gauge / liquid 清 xAxis |
| 4 | **取数据集并校验权限** | datasetGroupManage.getDatasetGroupInfoDTO + permissionManage.getRowPermissionsTree |
| 5 | 处理图表级过滤条件 | chartFilterTreeService.searchFieldAndSet(fieldCustomFilter) |
| 6 | **获取 union SQL** | datasetSQLManage.getUnionSQLForEdit(table, null) 返回 sql 与 dsMap |
| 7 | 判断是否需强制 ORDER | Utils.isNeedOrder(dsList) |
| 8 | **权限注入 WHERE** | WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap) |
| 9 | 列权限裁剪 + 脱敏 | permissionManage.filterColumnPermissions(allFields, ...) |
| 10 | 数据转换 | ChartDataBuild.transXxx() |

**取数链路示意**：

~~~text
ChartViewDTO（图表定义）
   |
   v  getAllChartFields
字段列表
   |
   v  datasetGroupManage.getDatasetGroupInfoDTO      <-- 权限校验点 1
数据集组
   |
   v  permissionManage.getRowPermissionsTree         <-- 权限校验点 2（行级）
行权限树
   |
   v  datasetSQLManage.getUnionSQLForEdit           <-- 生成基础 SQL + 跨源信息
基础 SQL + dsMap
   |
   v  WhereTree2Str.transFilterTrees                <-- 行权限注入 WHERE 条件
   v  filterColumnPermissions                       <-- 列裁剪 / 脱敏
   v  provider.transSqlDialect                      <-- Calcite 转目标方言
最终 SQL
   |
   v  provider.fetchResultField
原始行数据（String 数组的列表）
   |
   v  ChartDataBuild.transXxx                      <-- 按图表类型转换
前端图表库数据结构（AntV G2 / S2 / ECharts）
~~~

**值得强调**：第 8、9 步是数据权限隔离的落地点，**且这两个步骤所在的代码（WhereTree2Str、PermissionManage、ChartDataBuild）全部在开源 core 中**。缺的是 `permissionManage` 依赖的 xpack API 实现，不是执行引擎。

### 7.3 图表类型支持矩阵（ChartDataBuild 实测）

每个图表类型基本有 **AntV 版**（方法名带 AntV 后缀）和**传统版**两套转换逻辑：

| 转换方法 | 图表类型 |
|---|---|
| transChartData / transChartDataAntV | 基础柱线图 |
| transHeatMapChartDataAntV | 热力图 |
| transBaseGroupDataAntV / transGroupStackDataAntV | 分组堆叠 |
| transStackChartData / transMixChartStackDataAntV | 堆叠 |
| transScatterData / transMultiScatterDataAntV | 散点 / 多维散点 |
| transQuadrantData / transQuadrantDataAntV | 象限图 |
| transRadarChartData / transRadarChartDataAntV | 雷达图 |
| transMixChartData / transMixChartDataAntV | 混合图（折线 + 柱状双轴） |
| transLabelChartData | 标签图 |
| transNormalChartData | 普通图表 |
| transTableNormal / transTableNormalWithDetail | 表格 / 带明细的表格 |
| transBarRangeDataAntV | 区间条图（Gantt 类） |
| desensitizationValue | **脱敏值转换** |

**双引擎并存的原因**：v2 用 ECharts，v3 部分图表迁移到 AntV（G2 5.x 声明式 API 更适合复杂图表），S2 用于透视表，L7 / L7Plot 用于地理可视化。迁移期保留双实现，属技术债，但保证了向后兼容。

### 7.4 Handler 体系

`DefaultChartHandler`（956 行）是默认处理器，其余 Handler 按需覆盖特定图表类型：

| Handler | 图表 | 特殊处理 |
|---|---|---|
| BoxPlotHandler + BoxPlotSqlBuilder | 箱线图 | **Provider.supportsWindowFunctions() 判断**：支持则数据库侧算分位数，不支持则取原始数据在 Java 侧算 |
| TablePivotHandler | 透视表 | 行列维度交叉 + 小计合计 |
| TableNormalHandler | 普通表格 | 结果缓存 |
| TableInfoHandler | 明细表 | 分页下钻 |
| MixHandler | 混合图表 | 多 y 轴各自聚合 |
| SymbolicMapHandler | 符号地图 | 地图数据合并 |

箱线图这个设计值得注意：**能力探测 → 数据库侧计算优先，不支持则 Java 回退**，保证 11 种数据库都能画箱线图，性能又不失优化。这是很典型的工程权衡。

### 7.5 动态值机制

`getSizeField` / `getDynamicField` / `resetDynamicField` / `buildDynamicValue` / `buildMultiScatterDynamicValue` —— 支持气泡大小、象限点大小等由数据动态决定的视觉编码。


---

## 八、可视化与大屏（visualization，77 文件 / 7,449 行）

| 类 | 行数 | 职责 |
|---|---:|---|
| DataVisualizationServer | 1,180 | 大屏 REST 接口 |
| CoreVisualizationManage | 971 | 大屏核心管理（画布、组件、样式） |
| VisualizationLinkJumpManage | 500 | **跳转**（图表间钻取跳转） |
| VisualizationOuterParamsService | 417 | **外部参数**（嵌入式集成基础） |
| VisualizationLinkageManage | 360 | **联动**（图表间筛选联动） |
| VisualizationLinkJumpService | 290 | 跳转服务 |
| VisualizationOuterParamsManage | 268 | 外部参数管理 |
| CoreVisualizationExportManage | 229 | 大屏导出 |
| StaticResourceServer | 153 | 静态资源 |
| VisualizationStoreManage | 150 | 大屏存储 |
| SnapshotCoreChartView | 147 | 快照 |
| VisualizationRecentManage | 140 | 最近访问 |

**大屏四大能力**：

| 能力 | 实现类 | 说明 |
|---|---|---|
| 画布组件编排 | CoreVisualizationManage | 组件树、布局、样式、主题 |
| **联动** | VisualizationLinkageManage | 一个图表的筛选结果驱动其他图表刷新 |
| **跳转** | VisualizationLinkJumpManage | 点击图表跳到另一大屏 / 明细页，可带参数 |
| **外部参数** | VisualizationOuterParamsService | 从 URL / 主系统接收参数，注入图表查询 |

外部参数 + 跳转 + 联动这三件套，正是「把大屏嵌入主系统」所需的核心能力，且这部分代码在开源 core 中。

---

## 九、横切能力

### 9.1 导出中心（exportCenter，10 文件 / 1,655 行）

| 类 | 行数 | 职责 |
|---|---:|---|
| ExportCenterDownLoadManage | 918 | 导出下载管理（含大文件分片 / 打包） |
| ExportCenterManage | 501 | 导出任务管理（创建、队列、状态） |
| ExportCenterServer | 56 | REST 接口 |
| ExportTaskFileTarget | 56 | 导出目标定义 |
| ExportCenterLimitManage | 17 | 导出限制（并发数 / 大小） |

采用「**任务队列 + 异步导出 + 完成后下载**」的模型，避免大报表导出阻塞请求。

### 9.2 分享体系（share，17 文件 / 1,643 行）

| 类 | 行数 | 归属 | 职责 |
|---|---:|---|---|
| XpackShareManage | 426 | **xpack** | 分享核心逻辑 |
| ShareTicketManage | 216 | core | 分享票据 |
| DeLinkAop | 144 | core | 分享链接 AOP 拦截 |
| LinkTokenValidationManage | 121 | core | 链接令牌校验 |
| XpackShareServer | 121 | **xpack** | 分享接口 |
| ShareSecretManage | 101 | core | 分享密钥 |
| ShareAuthorizationManage | 89 | core | 分享授权 |
| **ShareQueryScopeAop** | 80 | core | **分享数据范围 AOP（关键安全点）** |
| XpackShare | 65 | **xpack** | 分享数据模型 |
| ShareVisitorPermissionManage | 54 | core | 访客权限 |

`ShareQueryScopeAop` + `ShareVisitorPermissionManage` 说明**分享场景下的数据范围控制在 core 有实现**，但访客权限模型依赖 xpack。

### 9.3 权限体系（社区版现状，已源码级验证）

**这是本次分析最重要的结论之一。**

社区版的权限由三个「降级替代类」构成，全在 `io.dataease.substitute.permissions` 包：

| 类 | 关键代码 | 实际行为 |
|---|---|---|
| SubstituleLoginServer | if (!StringUtils.equals("admin", name)) throw "仅admin账号可用" | **只能登录 admin 一个账号** |
| SubstituteUserServer | put("id","1") + setModel("lose")（注释：**当前模式为无XPack**） | 所有人登录后都是同一个 uid=1 的「管理员」 |
| SubstituleOrgServer | setId(1L) + setName("超级管理员") | 只有一个硬编码组织 |

真正的实现在一个**独立的远程服务**里（Feign 客户端指向不存在的 xpack 服务）：

~~~java
@DeFeign(value = "xpack-permissions", path = "/auth")
public interface PermissionFeignService extends AuthApi {}

@DeFeign(value = "xpack-permissions", path = "/interactive")
public interface InteractiveAuthFeignService extends InteractiveAuthApi {}

@DeFeign(value = "xpack-permissions", path = "/user")
public interface UserFeignService extends UserApi {}
~~~

而最关键的一行 —— 系统级权限在社区版**无条件放行**：

~~~java
// core/system/manage/CorePermissionManage.java
import io.dataease.license.config.XpackInteract;

@Component
public class CorePermissionManage {

    @XpackInteract(value = "corePermissionManage", replace = true)
    public boolean checkAuth(BusiPerCheckDTO dto) {
        return true;    // 社区版：任何权限检查恒通过
    }
}
~~~

**sdk/api/api-permissions 中的 17 个接口全部零实现**：

| 分类 | 接口 | 社区版 |
|---|---|---|
| 登录认证 | LoginApi / AuthApi / InteractiveAuthApi / PlatformBindApi | 无实现 |
| 用户组织 | UserApi / SysOrgApi / OrgApi / RelationApi / RoleApi | 降级占位 |
| 资源授权 | ResourceAuthApi / PerSettingApi | 无实现 |
| **数据权限** | RowPermissionsApi / ColumnPermissionsApi / RowColPermissionApi | 无实现 |
| 集成 | ApiKeyApi / EmbeddedApi / SysVariablesApi | 无实现 |

**但扩展点是现成的**（这是好消息）：

| 机制 | 位置 | 作用 |
|---|---|---|
| @ConditionalOnMissingBean(name = loginServer / orgServer / userServer) | 3 处 | 提供同名 bean 即可让降级实现失效 |
| @XpackInteract(value=..., replace = true) | 多处 | 提供同名 bean 即可替换核心实现 |
| PermissionManage / WhereTree2Str | core 内，**开源** | 行权限注入 SQL 的引擎已在社区版 |
| ColumnPermissionsApi 的 DTO | sdk 内，**开源** | 列权限数据结构公开 |
| ChartDataBuild.desensitizationValue | core 内，**开源** | 脱敏算法已在社区版 |

结论：**自研权限后端在技术上可行**，工作量约 4~5.5 人月。关键前提是 xpack API 的 DTO 契约已全部公开，不需要逆向。

### 9.4 数据同步与 AI

| 能力 | 后端归属 | 前端 API | 社区版可用 |
|---|---|---|---|
| 数据同步任务 | **xpack**（DeXpackDataSyncTaskExecutor） | sync/syncTask.ts 等 4 个 | 界面在，功能不可用 |
| AI SQL Bot | core（DatasetSQLBotManage，701 行） | aiSqlBot.ts | 部分可用，但 AES 密钥硬编码 |
| 数据填充（智能问数） | core + xpack 混合 | — | 部分可用 |
| 模板市场 | core（template，1,225 行） | templateMarket.ts | 可用 |

---

## 十、前端架构

### 10.1 目录结构（实测）

| 目录 | 文件数 | 职责 |
|---|---:|---|
| assets | 964 | 图标、字体、地图等静态资源 |
| views | 854 | 页面（路由级） |
| **custom-component** | **151** | **自定义组件库（最有二次开发价值的部分）** |
| components | 142 | 通用组件 |
| utils | 51 | 工具函数 |
| api | 33 | API 定义层 |
| store | 23 | Pinia 状态 |
| layout | 20 | 布局 |
| pages | 14 | 页面容器 |
| hooks | 12 | 组合式函数 |
| models | 8 | 类型模型 |
| config | 5 | 配置 |
| style | 4 | 样式 |
| router | 4 | 路由 |
| locales | 3 | 国际化 |
| directive | 3 | 指令（含权限指令） |
| plugins | 3 | 插件 |
| websocket | 1 | WS 客户端 |

### 10.2 API 层（33 个模块）

~~~text
about.ts            aiComponent.ts      aiSqlBot.ts       auth.ts
chart.ts            common.ts           dataset.ts        datasource.ts
font.ts             login.ts            map.ts            msg.ts
org.ts              plugin.ts           share.ts          staticResource.ts
template.ts         templateMarket.ts   user.ts           variable.ts
watermark.ts        relation/index.ts   setting/sysParameter.ts
sync/syncDatasource.ts   sync/syncSummary.ts   sync/syncTask.ts   sync/syncTaskLog.ts
visualization/dataVisualization.ts   visualization/linkage.ts
visualization/linkJump.ts            visualization/outerParams.ts
visualization/pdfTemplate.ts         visualization/visualizationBackground.ts
~~~

**关键观察：前端 API 层没有 permissions 相关模块**（无 role / menu permission / 数据权限 API），与后端权限缺失完全一致 —— 这说明前端也被裁剪过，不是单纯的「后端没实现」。

值得关注的模块：

| 模块 | 用途 |
|---|---|
| `visualization/outerParams.ts` | 外部参数，嵌入主系统的基础 |
| `sync/`（4 个） | 数据同步 UI（后端 xpack） |
| `watermark.ts` | 水印（社区版有，商用常需） |
| `aiSqlBot.ts` / `aiComponent.ts` | AI 能力 |
| `variable.ts` | 变量管理 |

### 10.3 库模式构建

`package.json` 中存在：

~~~json
"build:lib": "vite build --mode lib"
~~~

说明前端支持**编译为可嵌入的 JS 库**（配合后端 `EmbeddedApi`），这是「把 DataEase 嵌进主系统」的前端基础。但后端 `EmbeddedApi` 在社区版无实现，需要自研。

### 10.4 前端值得肯定的地方

- **组件库完整**：151 个 custom-component，图表控件、筛选器、布局器齐全
- **双图表引擎并存**：ECharts 兼容旧图表 + AntV 支撑新图表
- **i18n 完备**：`locales/` + vue-i18n 9
- **构建优化**：vite-plugin-compression（gzip/brotli）、style-import、svg-icons 雪碧图

---

## 十一、安全性问题清单

| # | 问题 | 位置 | 严重度 | 说明 |
|---|---|---|---|---|
| 1 | **AI SQL Bot AES 密钥硬编码** | application.yml 的 dataease.sqlbot.aes-key | 高 | 密钥入库且开源，任何人可见；AI SQL 功能涉及凭据解密 |
| 2 | **权限校验恒为 true** | CorePermissionManage.checkAuth() | 严重（功能） | 社区版无任何资源级鉴权 |
| 3 | **Flyway 禁用 + ddl-auto: update** | 所有 Profile | 中 | 升级时 Hibernate 自动改表，字段变更不可控 |
| 4 | **SSH StrictHostKeyChecking=no** | Provider.initSession() | 中 | 跳过主机密钥校验，易受中间人攻击 |
| 5 | **Redis 不参与健康检查** | management.health.redis.enabled: false | 低 | Redis 故障不可感知 |
| 6 | **单账号硬编码** | SubstituleLoginServer | 中（设计） | 社区版无法多用户，不满足企业场景 |
| 7 | **登录超时过长** | dataease.login_timeout: 2880 | 中 | 2880 分钟 = **48 小时**会话有效期 |
| 8 | **测试覆盖率近零** | tests/ 仅 4 文件 | 中 | 安全与功能回归均无保障 |

**做得好的地方**（值得学习）：

| 实践 | 位置 |
|---|---|
| Zip Slip 三重防护 | DataEaseDatasourcePlugin.extractSafeDriverFileName + resolveDriverPath |
| JDBC URL 白名单防 SSRF | JdbcUrlSecurityPolicy |
| 密码强制脱敏 | Provider.hidePW |
| 脱敏在服务端完成 | ChartDataBuild.desensitizationValue |
| SSH 支持密钥认证 | Provider.initSession |

---

## 十二、二次开发评估

### 12.1 可用的扩展点

| 扩展点 | 机制 | 可做什么 |
|---|---|---|
| 数据源插件 | 继承 DataEaseDatasourcePlugin | 新增数据库支持（热插拔，含驱动自动部署与卸载） |
| **权限后端** | 提供 loginServer / userServer / orgServer / corePermissionManage bean | **替代社区版权权降级**（3 个 @ConditionalOnMissingBean + @XpackInteract） |
| **行列权限** | 实现 RowPermissionsApi / ColumnPermissionsApi | **数据权限隔离**（SQL 注入引擎 WhereTree2Str 已开源） |
| 嵌入式集成 | 实现 EmbeddedApi / ApiKeyApi + vite build --mode lib | 大屏嵌入主系统 |
| WebSocket | websocket 包 | 实时数据推送 |
| 前端组件 | custom-component/ 151 个 | 复用 / 扩展 UI |
| 定时任务 | job + qrtz | 自定义调度 |

### 12.2 主要风险

| 风险 | 等级 | 说明 |
|---|---|---|
| **GPLv3 传染** | 严重 | 对客户分发或 SaaS 必须开源衍生作品（README 明写「二次开发后的衍生作品必须遵守 GPL V3 的开源义务」） |
| **LOGO 不可移除** | 严重 | 产品始终带飞致云 logo 与版权声明（FIT2CLOUD 附加条款） |
| **协议可被单方变更** | 高 | LICENSE 原文：The producer can adjust the open-source agreement to be more strict or relaxed as deemed necessary |
| **测试覆盖近零** | 高 | 5 万行代码改动无回归网 |
| **升级路径断裂** | 高 | 官网仍写专业版授权「v2.0 → 任意 v2.x，不能升 v3.x」，而 V3 已于 2026-08-31 发布 |
| **X-Pack 依赖硬编码** | 中 | @DeFeign("xpack-permissions") 需自建同名服务或改 core 代码 |
| **无版本承诺** | 中 | LICENSE 中未声明可商用的具体条款版本，协议可随时变 |
| **依赖升级停滞** | 低 | 社区版 DataEase 仍活跃（2026-10-14 提交），风险低于 Datart（停更 20 个月） |

### 12.3 与 Datart 的对比结论

| 维度 | DataEase 社区版 | Datart |
|---|---|---|
| 授权费 | ￥6 万/套（专业版） | **￥0** |
| 协议 | GPLv3 变形，传染 | **Apache-2.0，不传染** |
| 闭源商用 | 需购买 | **允许** |
| LOGO 限制 | **不可移除** | 无 |
| 协议可单方变更 | **可以** | 不可以（标准 Apache-2.0） |
| 社区活跃度 | 活跃（2026-10-14） | 停更（2025-02-10） |
| 权限 / 行列隔离 | **社区版无** | 社区版**全功能**（Shiro + 行列权限） |
| 嵌入主系统 | 需购买 X-Pack | 自研对接（约 1~2 人月） |
| 数据源广度 | **Calcite 联邦 + 跨源 JOIN + SSH 隧道** | 常规 JDBC 直连 |
| 升级风险 | 低 | 中（停更，需自养） |

**若核心诉求是「闭源 + 给客户用 + 预算可控」，Datart 的法律与许可成本显著更低；若需要开箱即用的企业级治理能力且愿付费，DataEase 专业版更省事。**

**混合方案值得考虑**：用 Datart 做对外交付的 BI 底座（Apache-2.0，闭源无忧），把 DataEase 的 Calcite 跨源联邦查询思路作为参考自研能力。

---

## 附录 A：关键类速查表

| 需求 | 看这个类 |
|---|---|
| 数据源接入 | Provider、CalciteProvider、DataSourceManage |
| 数据集与 SQL 生成 | DatasetSQLManage、DatasetDataManage、engine/SQLProvider |
| 图表取数链路 | ChartDataManage.sqlData |
| 图表数据转换（30+ 图表） | ChartDataBuild（chart/utils） |
| 行列权限落地点 | PermissionManage、WhereTree2Str |
| 插件开发（数据源） | DataEaseDatasourcePlugin、ProviderFactory |
| 大屏画布 | CoreVisualizationManage |
| 联动 / 跳转 / 外部参数 | VisualizationLinkageManage / LinkJumpManage / OuterParamsService |
| 定时调度 | ScheduleManager、qrtz 包 |
| **社区版权限真相** | substitute/permissions 下三个类 + CorePermissionManage |
| 启动初始化（最复杂） | listener/CoreDataInit（3,960 行） |
| 配置与部署形态 | core-backend/src/main/resources/application-*.yml |

## 附录 B：分析过程中的一手发现（易被忽略）

1. **`de-xpack/` 是空目录** —— 不是「找不到」，是刻意留空给商业版 jar。
2. **容器内 `BOOT-INF/classes/io/dataease/xpack` 不存在**，而 `dataease-license-sdk-3.1.0.jar` 含 Allatori 混淆授权类（LicSp / LicSj / LicSw / LicSg 等 96 个类）—— 说明官方**预留了授权校验机制**，插 license 即可解锁企业功能。
3. **全盘 `find / -name "*.lic"` 结果为 0** —— 未授权状态可一键验证。
4. **前端无 permissions API 文件** —— 印证前端也被裁剪。
5. **`installer/LICENSE`（35,823 字节）是完整 GPLv3 文本**，而根目录 `LICENSE` 仅 863 字节并以链接代替全文，严格说不满足 GPLv3 分发时附许可证副本的要求。
6. **Datart 容器跑的是 1.0.0-rc.3**（JDK 8 构建，镜像 2023-10-16），而其源码 master 为 2025-02-10 —— 落后约 16 个月，源码与运行版本不一致。

---

*本文档基于 dev-v3 分支 commit 4330aca 实测生成。DataEase 更新频繁，引用具体行数时请核对版本。*

