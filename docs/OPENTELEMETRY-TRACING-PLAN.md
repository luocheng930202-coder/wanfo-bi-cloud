# OpenTelemetry 链路追踪接入计划与使用说明

## 目标与当前状态

本文记录 Wanfo BI Cloud 的跨服务分布式链路追踪方案、当前接入状态和日常使用方式。2026-10-25 已接入本机开发链路：OpenTelemetry Java Agent 2.33.0、Collector Contrib 0.154.0、Tempo 2.10.10 和 Grafana 12.4.13。生产环境仍需另行完成 TLS、访问控制、容量规划和采样策略评估。

当前核心服务为 Java 17 / Spring Boot 微服务：网关 `ruoyi-gateway`（8080）、认证 `ruoyi-auth`（9200）、系统 `ruoyi-system`（9201）和 BI `ruoyi-bi`（9206）。已有 Spring Boot Admin（本机 9205）查看服务状态和 JVM，Prometheus（本机 9090，来自独立监控栈）采集指标。BI 查询治理另有面向 BI 查询的 `requestId`，但它不是统一的跨服务 Trace ID。若依操作日志记录业务操作，也不具备调用链瀑布图。

本次选用 **OpenTelemetry Java Agent + OpenTelemetry Collector + Grafana Tempo + Grafana**：Agent 自动埋点，Collector 统一接收和转发，Tempo 保存与查询 Trace，Grafana 展示 Trace 并可关联现有 Prometheus 指标。OpenTelemetry 提供中立的遥测采集和传输能力；Collector 将遥测通过接收器、处理器和导出器组成流水线转发给后端。[Java Agent 文档](https://opentelemetry.io/docs/zero-code/java/agent/)，[Collector 与 Tempo 接入说明](https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/)

当前仓库没有 ELK 部署定义，因此本计划不把 Elasticsearch/Kibana 作为追踪后端。若环境另有 ELK，后续可在结构化日志中加入相同的 `traceId`、`spanId`，从日志跳到 Trace，或反向检索日志。

## 目标架构

```text
浏览器请求
    │
    ▼
ruoyi-gateway ──► auth / system / BI ──► 数据库或外部数据源
    │                    │
    └──── traceparent / baggage 上下文贯穿服务边界 ────┘
                         │ OTLP/HTTP
                         ▼
                 OpenTelemetry Collector
                         │ OTLP
                         ▼
                       Tempo ◄── Grafana Explore / Trace 页面
                                      │
                         Prometheus 指标数据源（已有）
```

本机开发阶段使用 OTLP/HTTP：应用向 Collector `4318` 端口发送数据。Collector 只接收来自本机或受信任网络的流量，内部将 Trace 写入 Tempo；Tempo 查询 API `3200` 和 Grafana UI `3000` 均只绑定本机。生产环境须加 TLS、认证、网络隔离和容量规划，不能直接公开 OTLP 接收端口。

如果 Grafana 与现有 `zl-prometheus` 位于不同 Compose 网络，Grafana 可通过 `host.docker.internal:9090` 访问宿主机已映射的 Prometheus；实际部署时要确认 Prometheus 端口仅对可信网络开放。追踪组件使用独立 Compose 文件，避免改动现有 Prometheus、Alertmanager 和业务基础设施的生命周期。

## 接入状态与后续工作

| 阶段 | 工作内容 | 完成标准 |
|---|---|---|
| 0. 基线与边界 | 固定 Agent、Collector、Tempo、Grafana 版本；明确本机采样与敏感属性清理 | 已完成。生产版本仍需安全评审 |
| 1. 本机追踪栈 | 独立 Compose、Collector/Tempo 配置、Grafana Tempo 与 Prometheus 数据源 | 已完成；Collector 健康检查和 Grafana API 已验证 |
| 2. Java Agent 接入 | Agent 保存在 Git 外；通用本地启动脚本可选挂载；Nacos 运行方式提供保留原参数的重启脚本 | 已完成；用下文命令启用后会重启四个业务服务 |
| 3. 服务间上下文 | 验证网关到认证/系统/BI 的 `traceparent`、HTTP、Feign 和 JDBC Span | 网关 → 认证已验证；本次补验系统与 BI 入口，具体验收结果见本文末“本次回归记录” |
| 4. 日志和指标关联 | 让应用日志输出 Trace ID/Span ID，并在 Grafana 建立 Tempo 到 Prometheus 的指标跳转 | 四个服务日志格式已输出 `trace_id`、`span_id`、`trace_flags`；Prometheus 数据源已预置 |
| 5. 安全与容量 | 生产采样率、保留期限、限流、缓冲和资源开销评估 | 开发环境使用 100% 采样和 24 小时保留；生产建议与计算方法见“生产部署评估” |
| 6. 验收与运维 | 启停、健康检查、运维说明和回滚操作 | Agent 关闭及追踪栈停止回归已执行，结果见本文末“本次回归记录” |

### 阶段 1 组件建议

- **OpenTelemetry Java Agent**：以启动参数方式注入，不把 Agent JAR 提交到仓库；版本固定并记录 SHA-256。
- **OpenTelemetry Collector**：本地通过 OTLP/HTTP `4318` 接收；用 OTLP/gRPC 写入 Tempo。配置 `memory_limiter`、`batch` 和属性处理器过滤敏感字段。
- **Tempo**：本机单实例和本地数据卷先用于开发验证；设置保留期限和磁盘空间上限。生产环境另做持久化、备份、高可用和访问控制设计。
- **Grafana**：加入 Tempo 数据源；可选加入现有 Prometheus 数据源。Grafana 只负责查询和展示，不替代 Collector 或 Tempo。

## 配置约定

实施阶段建议采用以下服务标识和环境变量：

```text
OTEL_SERVICE_NAME=ruoyi-gateway | ruoyi-auth | ruoyi-system | ruoyi-bi
OTEL_RESOURCE_ATTRIBUTES=service.namespace=wanfo,deployment.environment=local
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318
OTEL_TRACES_EXPORTER=otlp
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

Windows 主机上运行的 Java 进程使用 `127.0.0.1:4318`；若应用也放在同一个 Docker Compose 网络中，则 endpoint 改为 `http://otel-collector:4318`。Collector 必须明确绑定容器可访问的网卡地址。OpenTelemetry Java Agent 2.x 默认使用 OTLP/HTTP；显式设置 protocol，避免 Agent 和 Collector 的协议配置不一致。[Agent 配置说明](https://opentelemetry.io/docs/zero-code/java/agent/configuration/)

上面将指标和日志导出关闭，是因为当前已有 Prometheus 指标链路，且暂不建设 OTel Logs 流水线。应用自动埋点仍会产生 Traces。四个 Java 服务的控制台日志格式已输出 Agent 注入的 MDC 字段 `trace_id`、`span_id`、`trace_flags`；日志后端仍需将这些字段解析为结构化字段，才能实现 Grafana/Loki/ELK 的日志跳转。若未来要统一采集 metrics 或 logs，应先确定与 Prometheus/ELK 的数据边界，避免重复上报和重复存储。

Agent 按 `-javaagent:<本地 Agent JAR>` 加载。实施时将由 `scripts/start-local.ps1` 按 `.env` 中的 `WANFO_TRACING_ENABLED` 开关决定是否追加此参数；关闭追踪时不传 `-javaagent` 即可，不需要改业务配置。Agent JAR、Grafana 初始凭据和运行数据都放 Git 外。

## 接入完成后的日常使用说明

### 启动追踪

首次本机启用按以下步骤操作。Agent 已由安装脚本下载到 Git 忽略的 `.local/otel/`：

```powershell
# 1. 需要重新安装或升级 Agent 时运行（默认使用 deploy/.env 中固定的版本）
powershell -ExecutionPolicy Bypass -File scripts/install-otel-agent.ps1

# 2. deploy/.env 中设置 WANFO_TRACING_ENABLED=true；Grafana 初始密码由 init-local.ps1 生成
# 3. 启动追踪组件
docker compose --env-file deploy/.env -f deploy/compose.tracing.yml up -d

# 4a. 使用仓库单机配置启动服务
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1

# 4b. 若四个服务当前通过 Nacos 配置运行，保留其原有启动参数并挂载 Agent
powershell -ExecutionPolicy Bypass -File scripts/restart-current-services-with-tracing.ps1
```

打开 `http://localhost:3000`，使用本机 `deploy/.env` 中的 Grafana 用户名和密码，进入 **Explore → Tempo**，按 `service.name` 搜索 `ruoyi-gateway`、`ruoyi-auth`、`ruoyi-system` 或 `ruoyi-bi`。发起一次登录、查询图表或读取看板请求后，等待数秒让 Collector 批量发送数据。`scripts/restart-current-services-with-tracing.ps1 -Disable` 可关闭 Agent 并重启服务。

### 排查一次请求

1. 从 Grafana Tempo 按服务名、时间范围、HTTP 路径、状态码或耗时搜索 Trace。
2. 打开 Trace 瀑布图，查看每个服务的 Span、耗时、父子关系和错误标记，找出最慢或失败的调用。
3. 点击 Trace 中的服务/Span，查看相关属性；从服务 Span 跳到 Prometheus 指标，判断该时间点是否伴随 CPU、内存、请求量或错误率异常。
4. 复制 Trace ID，在对应服务日志中搜索同一 ID，查看具体异常信息。Trace 说明“请求走过哪里、每段耗时多少”，日志用于补充代码错误和业务上下文。
5. 对 BI 查询，保留当前 BI `requestId` 作为 BI 查询治理编号；Trace ID 负责跨服务关联，两种编号职责不同，后续可在日志和 Span 中建立对应关系。

### 停止和关闭追踪

通用单机启动脚本读取 `WANFO_TRACING_ENABLED`；设为 `false` 并重启服务即可关闭 Agent。Nacos 本地运行方式执行 `scripts/restart-current-services-with-tracing.ps1 -Disable`。停止追踪组件但保留本地数据：

```powershell
docker compose --env-file deploy/.env -f deploy/compose.tracing.yml down
```

不要加 `-v`，否则会删除 Tempo 本地追踪数据。若 Agent 未启用，服务正常运行但 Grafana 不会出现新的 Trace。

## 常见问题

| 现象 | 检查项 |
|---|---|
| Tempo 没有 Trace | `WANFO_TRACING_ENABLED` 是否打开；Agent 是否真正加载；Collector `4318` 是否可达；endpoint 是否按主机/容器网络选对；Collector 和 Agent 是否都使用 `http/protobuf` |
| Trace 只到网关 | 检查网关转发是否保留 `traceparent`；Feign/HTTP 客户端是否被 Agent 自动埋点；服务名和服务发现地址是否正确 |
| Trace 中有服务但无 JDBC Span | 确认对应 JDBC 驱动受 Agent 版本支持；查 Agent 启动日志；核对连接是否绕过常见 JDBC 埋点路径 |
| 查询不到日志关联 | 日志格式和采集配置是否输出 Trace ID/Span ID；日志系统中字段名是否一致；请求是否经过独立线程池或异步执行而丢失上下文 |
| 本地磁盘增长过快 | 检查 Tempo 保留时间和数据卷大小；缩短本地保留期；生产环境使用容量规划后的持久化后端 |
| 指标端点仍正常但看不到追踪 | Prometheus/Spring Boot Admin 是指标监控；Trace 必须经 Agent → Collector → Tempo 的追踪流水线，三个数据源各自独立 |

## 安全与生产注意事项

- 不采集密码、JWT、Authorization、Cookie、验证码、完整 SQL 参数、用户隐私或数据源连接串。数据库语句采集按需启用，并优先只保留语句类型/耗时，不保留绑定参数。
- 不把 Trace ID 当成认证凭证；不要把用户令牌写入 baggage 或 Span 属性。
- Collector OTLP 接收端仅开放给受信任网络；Grafana 需设置独立强口令。生产环境需配置 TLS/认证并限制查询权限。
- 生产采样率不直接沿用本地 100%。先压测并观察 Agent、Collector、Tempo 的 CPU、内存、网络、存储和丢弃计数，再按请求量和保留目标设定采样策略。
- Java Agent 会改变运行时字节码并增加开销。先在开发环境验证登录、Feign、WebFlux、JDBC 和 BI 查询的兼容性，再推广至正式环境。

## 生产部署评估

本机 Compose 仅供开发验证：OTLP、Tempo、Grafana 端口绑定回环地址，Agent 使用 100% `parentbased_traceidratio` 采样，Tempo 保留期为 24 小时；回环流量不配置 TLS。此配置不适用于生产网络。

生产建议：

- OTLP 接收端放在私有网络，仅允许业务服务访问；跨主机传输使用 TLS，跨不完全可信网络优先启用 mTLS，并配合网络 ACL/认证。Grafana 使用企业 OIDC/SSO、最小权限和 TLS。不要把 OTLP 或 Tempo 查询端口直接暴露公网。
- 初期采用 `parentbased_traceidratio` 的 5% head sampling 作为压测起点，按业务量、错误率、Agent/Collector 开销和存储预算调整。该策略无法保证保留所有错误或慢请求；如有此要求，评估 Collector tail sampling（错误及耗时策略），并为 decision wait、队列和 trace 缓冲单独预留内存。
- 生产存储优先使用对象存储并配置保留期、Compactor、生命周期和容量告警；避免单机本地卷成为唯一副本。Collector 配置队列、重试、批处理和内存限制，监控丢弃量、导出失败、队列积压、磁盘/对象存储增长。

估算原始存储量可用：`请求数/秒 × 每请求平均 Span 数 × 平均压缩后字节/Span × 保留秒数 × 采样率 × 安全系数`。例如 100 RPS、每请求 10 spans、平均压缩后 500 B/span、保留 7 天、采样率 5%、安全系数 1.3，约为 19.7 GB；10 RPS 相同条件约 2 GB。该例仅用于初始规划，最终需用生产压测得到的请求量、span 数分布、实际压缩率、错误保留策略和保留天数复算，并额外计入索引、复制、峰值写入与增长余量。

## 本次回归记录（2026-10-25）

- 四个服务日志输出已加入 `trace_id`、`span_id`、`trace_flags`。Java Agent 的 Logback MDC instrumentation 会把当前 Span 上下文写入 MDC；对应格式 token 见 [Java Agent Logback MDC 配置](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-mdc-1.0/javaagent/README.md)。
- 后端 auth、gateway、system、BI 模块成功重新打包（`-DskipTests`，未执行测试套件），并以 Agent 启动。
- Agent 关闭回归：四个服务无 `-javaagent` 参数时，Actuator health 均返回 200；随后恢复 Agent。
- 追踪栈停止回归：停止独立 tracing Compose 栈且不删除数据卷期间，业务依赖容器继续运行，四个业务服务的健康检查仍为 200；之后已恢复 tracing 栈。
- 登录后的真实页面验收：系统首页路由请求在 Tempo 中包含 Gateway → `ruoyi-system` 的 `GET /menu/getRouters` → `sys_menu` SQL Span；BI 工作台请求成功，在 `ruoyi-bi` Trace 中看到 `/bi/workbench/summary`、`/bi/workbench/recent` 等控制器 Span 和对应 SQL Span（summary Trace 共 54 个 BI/JDBC spans）。开发环境 `vite.config.ts` 明确把 `/dev-api/bi` 直接代理到 `127.0.0.1:9206`，所以浏览器 BI Trace 不含 Gateway Span 符合当前开发代理配置；生产环境按 [快速开始说明](QUICKSTART.md) 统一走 `/prod-api`，需在真实部署拓扑再验 Gateway → BI。对应样例 Trace ID：System `53c2cdc1c9e5655297636e1dd39a8cdd`，BI summary `ffcf3844f9db066a0603484fdde709f1`。
- 日志格式经过构建检查；日志 MDC 值随有效 Span 上下文填充，非追踪日志中的字段可能为空。

## 验收清单

- [x] 本地 OTLP 接收端、Collector、Tempo、Grafana 均健康，所有映射端口仅绑定本机。
- [x] 四个服务均已加载 Java Agent。
- [x] 执行关闭/重新启用 Agent 的操作回归，并确认关闭期间业务请求仍可用。
- [x] 经过网关到认证的请求生成同一 Trace ID 的三段式 Trace。
- [x] 登录后验证 System 只读请求：Gateway、system controller 与 SQL Span 在同一 Trace。
- [x] 登录后验证 BI 工作台只读请求：BI controller 与 SQL Span 在同一 Trace。
- [ ] 在生产或与生产一致的 Gateway 代理拓扑验证 Gateway → BI 跨服务传播；本机 Vite 开发代理按配置直接访问 BI 服务。
- [ ] Feign、响应式 HTTP、JDBC 的 Span 经过抽样业务请求验证。
- [ ] 出错请求的 Span 有错误标记；慢请求能按耗时筛选。
- [x] BI `requestId` 与 Trace ID 含义区分清楚；四个服务日志格式已输出 Trace ID/Span ID。
- [x] 验证样例 Trace 不包含 Authorization、Cookie、URL 查询串和 SQL 文本属性。
- [x] 执行 Compose 停止回归，确认数据库、Redis、Prometheus 和业务服务仍运行。
- [x] 文档包含开发启动、服务查询、日志关联、关闭、故障排查及生产 TLS、采样、容量要求。
