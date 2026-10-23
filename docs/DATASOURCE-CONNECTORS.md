# 数据源接入与验收

## 支持范围

| 类型 | 接入方式 | 建模范围 | 本批验证 |
| --- | --- | --- | --- |
| MySQL / Doris / StarRocks | MySQL JDBC | 连接串指定的数据库 | 原有能力回归；本地 MySQL 验收 |
| PostgreSQL | 随包提供 PostgreSQL 驱动 | 当前数据库内指定 schema，默认连接当前 schema，通常为 public | 驱动加载、范围隔离、元数据、兼容模式查询 |
| SQL Server | 随包提供 Microsoft JDBC 驱动 | 当前数据库内指定 schema，通常为 dbo | 驱动加载、TOP 分页、标识符引用、兼容模式查询 |
| Oracle | 随包提供 Oracle JDBC 驱动 | 指定 schema，默认连接用户 | 驱动加载、DUAL 检测、ROWNUM 分页、NUMBER 类型、兼容模式查询 |
| ClickHouse | clickhouse-jdbc 0.9.8 all，默认 V2 驱动 | 连接当前数据库；驱动通过 schema 提供数据库名称 | 驱动加载、schema 范围、预览方言和字段类型适配 |
| JSON API | HTTP(S) GET，手动创建/刷新持久快照 | 虚拟库 snapshot 下的 data 表 | 本地 HTTP 自动化、权限、失败保留、并发版本检查、Calcite 查询 |
| CSV | UTF-8 文件上传，支持 BOM、引号、逗号和换行 | 同上 | 解析、类型、异常输入、Calcite 聚合 |
| Excel | XLS / XLSX，指定工作表或默认首表 | 同上 | 工作表解析、公式拒绝、持久化、查询 |

**PostgreSQL、SQL Server、Oracle、ClickHouse 尚未连接用户真实实例验收。** H2 兼容模式用于检查 JDBC 建模和 Calcite 查询链路，不能替代相应服务器的版本、权限、TLS、时区及复杂 SQL 联调。当前工作不扩展业务填报的跨数据库写入能力。

## JDBC 配置

在“数据源 → 新增数据源”选择类型。Schema 别名是 BI 中的逻辑名称，创建后保持不变；新增的“物理 Schema”决定实际可建模范围，与别名可以不同。

| 类型 | JDBC 地址示例 | 物理 Schema 示例 |
| --- | --- | --- |
| PostgreSQL | `jdbc:postgresql://host:5432/business` | `public` 或 `sales` |
| SQL Server | `jdbc:sqlserver://host:1433;databaseName=business;encrypt=true` | `dbo` 或 `sales` |
| Oracle | `jdbc:oracle:thin:@//host:1521/service` | `SALES`，通常使用实际 schema 的大小写 |
| ClickHouse | `jdbc:clickhouse://host:8123/business` | 留空使用连接数据库，或填写实际数据库 |

不关闭证书验证来规避 TLS 配置错误。创建后选择数据源 → 建数据集 → 物理表和字段 → 保存，再在图表中使用。

系统库与系统 schema 被拒绝；库表接口不允许通过请求参数切到未配置的范围。字段来自 JDBC 元数据，不接受任意标识符拼接。预览限定 1～100 行并设置查询超时。Oracle 检测使用 `SELECT 1 FROM DUAL`，连接池保活同样使用相应语句。

驱动提供默认连接/读取超时；连接 URL 中显式提供的驱动选项仍按驱动自身优先级生效。服务端查询治理继续执行并发限制、超时和取消，不将网络读取超时当作完整的查询取消保证。

## API 快照

服务端必须先在 BI 服务配置中允许具体 API 来源，例如：

```yaml
bi:
  external-source:
    allowed-origins: https://api.example.com,http://internal-api.example:8080
```

只按精确协议、主机、端口匹配，不支持通配符。内网地址也必须显式允许；不自动跟随重定向、不转发到其他来源，不允许 userinfo、Host 覆盖或换行请求头。

页面填写名称、别名、URL、数组路径（如 `data.rows`，根数组留空）和可选请求头 JSON。响应应是固定字段的扁平对象数组，例如：

```json
{"data":{"rows":[{"region":"北京","amount":125.50},{"region":"上海","amount":210.00}]}}
```

当前仅支持完整数据 GET 接口，不自动翻页、定时同步、POST 参数、OAuth 刷新或嵌套对象展开。识别到常见 `total` / `totalCount` 大于数组行数会拒绝导入；其他分页协议应先在上游整理为完整数据接口，不能把单页当成全量数据。

请求头配置使用现有服务端凭据加密组件保存，管理接口仅返回是否设置，不返回原文或密文。前端关闭该请求的通用重复提交缓存，避免把请求头写入 sessionStorage。编辑时请求头留空表示保留，`{}` 表示清空；修改 URL 时必须重新提供请求头。

## 文件与快照约束

- 单次输入最多 5 MB、200 列、20000 行，每个单元格最多 2000 字符；拒绝超限，不静默截断。
- CSV 必须是 UTF-8；第一行为唯一字段名。Excel 第一行为表头，公式和错误值需先转换为普通值。
- 自动识别文本、十进制、布尔、日期和本地日期时间。JSON 数字按任意精度解析后转换，避免先经浮点数损失精度。CSV 的 `001` 保留为文本；混合类型保持文本；超过安全十进制精度的标识符保持文本。空值保持 SQL NULL。
- Excel 数字按数值导入；需要保留数字显示格式、前导零或超长编号时，请在源文件中明确使用文本列。
- 快照作为共享元数据保存在 `bi_source_snapshot`，无需共享本地文件路径。查询读取已保存快照，不实时请求 API，不访问上传后的本地文件。
- API 手动点击“刷新”；CSV / Excel 点击“更新快照”重新上传。同一数据源不能换类型；更新须保持字段名称、顺序和既有类型兼容，失败保留上一版。更新成功影响后续查询，不改变正在执行的查询快照。
- API / 文件暂不支持关联建模和首次空数据导入；可在上游整理为完整单表，或者为结构变化创建新数据源。
- 快照沿用数据源授权、数据集权限、行列权限、图表、看板、导出和查询治理链路。快照存在不等于远程 API 当前可连接。

## 迁移、回归与后续实库验收

Flyway `V21__bi_source_snapshots.sql` 为 `bi_datasource` 添加 `source_schema`，并新建快照表，不修改已有数据源凭据及业务数据。发布前按部署规范备份元数据库；回滚应用不应删除已经导入的快照。

自动化覆盖驱动加载及 URL 识别、schema 隔离、原生分页、H2 三种兼容模式、CSV / Excel / JSON、HTTP 域名与重定向、权限先于外部请求、刷新类型校验、版本冲突、迁移与 MyBatis 读写、快照建模及参数化聚合。

拿到各真实实例后还需验证：最小权限账号、跨 schema 拒绝、日期/时区、小数与大整数、空值、视图、中文及保留字字段、关联、TopN、透视表、日历计算、慢查询取消、TLS 和保存后缩略图。未完成这些检查前，不将某个具体服务器版本标为“生产验收通过”。

参考驱动文档：[PostgreSQL](https://jdbc.postgresql.org/documentation/use/)、[SQL Server 超时](https://learn.microsoft.com/en-us/sql/connect/jdbc/understand-timeouts)、[ClickHouse JDBC](https://clickhouse.com/docs/integrations/language-clients/java/jdbc)。


本批本地验收：BI 后端 427 项测试、前端 155 项测试全部通过；BI 范围类型诊断 0 条，宿主原有 268 条诊断保留；前端生产构建通过。真实本地服务完成 CSV / Excel 上传、元数据、创建数据集、图表聚合、重新上传后的取数、失败更新保留、API 默认拒绝外部请求及原地图查询回归。临时数据源和数据集已从有效列表清理。四种新增数据库仍按上表的自动化适配级别标记，等待真实实例联调。
