# DataEase 全功能地图与架构学习记录

调研日期：2026-10-16。目的：在继续自研 BI 前，先建立参考产品的完整功能视图、核心调用链和差距依据。本轮是功能与架构调研，不是全功能测试报告，不修改自研业务代码。

## 1. 版本、范围与证据

本地运行实例“关于”显示 **DataEase 3.1.0 社区版**，不是企业版。此前报告中的“运行版本尚未核验”已由本次界面证据补齐。本地源码根 AGENTS.md 标明 V3，但不据版本名称假定运行实例与源码逐提交一致。

`D:/bi-tools/dataease/de-xpack` 子模块未初始化，`git submodule status` 显示 `-8b42e8… de-xpack`。因此企业模块可以从现有前端、公开 SDK 和官方文档了解功能及契约，不能声称已经阅读缺失的私有实现或完成企业版实测。本轮不拉取子模块、不更换版本、不改变授权。

证据层级：

- **U：本轮界面观察**。只证明打开的页面、配置与结果存在，不替代保存/执行/权限回归。
- **P：前两轮界面记录**。详见 [第一轮](BI参考项目-DataEase体验与实施建议.md) 和 [第二轮](BI参考项目-第二轮功能对标与验收清单.md)，不伪装为本轮新验证。
- **S：源码阅读**。核心实现或前端行为已定位；标为“契约”时仅表示接口/DTO/页面存在。
- **D：官方文档**。用于理解产品语义和扩展范围；不将 V2 页面视为 V3 实现依据。

本轮清点了 `sdk/api` 下 75 个 `*Api.java` 文件，包含 496 条操作摘要和 609 处 HTTP 映射声明，以及前端 `views/pages` 下 39 个 Vue 文件。它们是**静态清单数量**，不是去重后的可调用接口数量、测试用例数或完成度。清单脚本和结果位于 `D:/datacenter/.local/bi-reference/full/inventory.py` 与 `source-inventory.json`；仅包含源码路径和公开契约元信息，不含运行账号密码、Token 或业务查询结果。

## 2. 功能全景

### 2.1 工作台与资源管理

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-01 | 工作台 | 资源概览、快速创建、模板推荐、收藏、最近使用、分享；扩展版可包含填报/电子表格等入口。统计和列表应跟随身份与组织 | U/P；workbranch/index.vue；D1 |
| DE-02 | 资源目录 | 数据源、数据集、仪表板、大屏等各自的目录树、搜索、排序、重命名、移动、复制及删除检测；目录不能仅作为 UI 分组处理 | U/P；DatasetTreeApi、DataVisualizationApi、InteractiveAuthApi |
| DE-03 | 个人入口 | 个人信息、语言、组织上下文、第三方账号绑定和 MFA 等；社区实例只显示其支持的子集 | U；UserApi、LoginApi 契约 |

### 2.2 数据接入和准备

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-04 | 数据库与湖仓连接 | 按 OLTP/OLAP/数据湖分类；主机或 JDBC 连接、schema、有效性校验、表字段和数据预览；不同类型的驱动/方言能力不同 | U；DatasourceApi、datasource/provider |
| DE-05 | 连接运行配置 | SSH、连接池初始/最小/最大连接数、查询超时及周期巡检；配置有效与业务查询正确是两种验收 | U；Doris 空白表单高级配置 |
| DE-06 | 文件数据源 | 本地/远程 Excel、CSV、多 Sheet、类型识别、替换与追加；文件解析、暂存、同步和查询需分别理解 | S：ExcelFileGuard/ExcelUtils/远程文件前端；D2 |
| DE-07 | API 与扩展连接 | API 请求配置、校验和同步；特定平台连接通过扩展驱动处理。连接插件与普通 JDBC 类型不能混为一谈 | S：DatasourceApi、ApiHttpRequestForm、PluginManageApi；D2 |
| DE-08 | 表/SQL 数据集 | 从数据源选表或编写 SQL；预览、总量、字段和 SQL 参数；数据集是图表消费的模型层，不是原始库表的别名列表 | P；DatasetDataApi、DatasetTreeApi、DatasetGroupManage |
| DE-09 | 多表与跨源建模 | 左/右/内/全连接、字段选择与重复表引用；跨源需相应引擎/方言和开关；不等于直接拼接两条任意 SQL | P/S：DatasetUnion、engine；D3 |
| DE-10 | 字段治理 | 维度/指标、类型、显示名、复制/隐藏、批量管理、分组字段及计算字段；表达式可引用字段/函数并校验 | P/S：CalcFieldEdit、DatasetTableApi；D3 |
| DE-11 | 模型依赖与变更 | 数据源→数据集→图表/载体；删除检测、引用关系和替换数据集均涉及资源与字段稳定性 | S：relation API、InteractiveAuthApi、DatasetTreeApi |

### 2.3 查询和图表分析

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-12 | 查询执行 | 图表模型→身份/资源和行列策略→字段轴/过滤/聚合→SQL/Provider→结果构造；模板示例取数有单独分支 | S：ChartDataServer、ChartDataManage |
| DE-13 | 图表类型 | 指标卡、明细/汇总/透视表、柱线面积、饼环、散点、组合、地图、漏斗、仪表盘及其他统计图。每类有自己的字段槽和处理器约束 | S：chart-list-empty、chart/charts；D4；P 中部分类型 |
| DE-14 | 图表配置 | 维度/指标、聚合、排序、日期粒度、数量限制、过滤、格式、标题、图例/坐标轴/Tooltip、空值、辅助线和条件样式 | P/S；D4、D5 |
| DE-15 | 查询组件 | 关联图表/字段，文本/数值/时间等类型，候选来源、默认值、必填、查询/清空/重置、即时查询、级联及顶部固定 | P/S：v-query、QueryConditionConfiguration；D6 |
| DE-16 | 点击联动 | 从点击维度提取值，按映射影响目标图表，显示/清除局部或全部联动；可与钻取组合触发 | S：LinkageSet、views/index.vue、ChartExtRequest；D5 |
| DE-17 | 钻取 | 按配置维度层级追加路径过滤并切换展示轴，可返回上级；不是修改整个数据集结构 | S：chartClick/drillJump、ChartDataManage 的 drill 处理；D5 |
| DE-18 | 跳转与外部参数 | 载体间跳转/外链、关联目标字段或查询条件、打开方式及传值。参数编码不是加密或身份授权 | S：linkJump/outerParams、views/index.vue；D5/D7 |

### 2.4 仪表板、大屏和组件

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-19 | 仪表板 | 矩阵/栅格布局、主题、组件间距、全局样式与局部覆盖；偏向业务分析和自适应查看 | P；DbCanvasAttr、dashboard 编辑入口；D7 |
| DE-20 | 数据大屏 | 自由坐标、设计宽高、标尺/缩放、图层/锁定/对齐/组合、背景素材、弹窗区域与刷新；偏向固定展示尺寸 | P；CanvasAttr、DeCanvas；D8 |
| DE-21 | 通用展示组件 | 富文本、图片/图片组、视频/流媒体/网页、时间/图标/装饰、Tab/容器及事件。嵌套组件不是只有单层图表列表 | P/S：custom-component、组件工具栏；D5 |
| DE-22 | 编辑状态与操作 | 选择、复制/剪切/粘贴、批量操作、撤销重做、脏状态、缓存恢复；只需重绘与需要重新取数应分开 | S：snapshot.ts、dvMain.ts、CanvasCacheDialog；P |
| DE-23 | 草稿、发布及恢复 | 保存草稿、发布、取消发布、恢复到当前发布稿；源码中 draft/snapshot 与 core 有复制/恢复关系。不能据此宣称具备任意历史版本时间线 | S：DataVisualizationServer.updatePublishStatus/recoverToPublished；D7 |
| DE-24 | 移动端 | 仪表板有独立组件选择、布局和样式同步；移动页面/分享入口独立。官方说明大屏不按相同方式支持移动查看 | S：router/mobile.ts、移动预览；D9 |

### 2.5 模板与报表载体

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-25 | 模板中心和管理 | 在线来源/本地来源、分类、预览、应用、导入与模板管理；网络不可用和本地空列表是需要设计的状态 | U/P；template-market、TemplateManageApi |
| DE-26 | 样式模板 | 保留样式与组件，实际使用需要绑定业务数据集；模板示例效果不等于业务数据取数成功 | P；decompression 清空 tableId、标记 TEMPLATE；D8 |
| DE-27 | 应用模板 | 还携带应用配置与数据资源关系，创建时需配置目标连接并生成/迁移依赖；不宜让模板无提示创建业务数据库对象 | S：appData、export2AppCheck、模板 DTO；D8 |
| DE-28 | 数据集替换与兼容 | 组件/全局替换、ID 和字段映射、类型和被引用字段兼容；模板、电子表格和普通图表有不同接入方式 | P（仅入口）、S：DatasetReplacementPlugin、模板历史适配 |
| DE-29 | 电子表格 | Univer 单元格能力，加上数据集绑定的明细/透视对象、查询控件、替换数据集、预览和发布；它与图表里的“透视表”是不同层级 | S：SpreadsheetApi/DataApi、spreadsheet/plugins；D10/D11；本实例无入口 |
| DE-30 | 数据填报 | 表单设计→目标表/字段→任务与填报人→校验提交→数据回写及记录/导入导出。是写入业务，不属于 BI 只读查询 | S：DataFillingApi/前端 form；D12；私有实现缺失 |

### 2.6 权限、分享与集成

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-31 | 用户/组织/角色 | 用户启停/导入、组织成员与角色、组织切换和资源上下文；系统级与组织级管理分工。不能直接套为若依部门模型 | S：User/Org/SysOrg/RoleApi 契约；D13 |
| DE-32 | 功能/资源权限 | 按用户或资源视角授权，菜单与资源操作分开，查看/使用/导出/管理/授权有区分；继承和多角色实际规则需私有实现确认 | S：AuthApi/InteractiveAuthApi、AuthEnum、授权前端；D13 |
| DE-33 | 行列权限和变量 | 行规则、列禁用/脱敏、规则白名单、系统内置/自定义用户变量；查询、枚举和计算字段同样要受控 | S：PermissionManage、Row/ColumnPermissionsApi、SysVariablesApi；D14 |
| DE-34 | 公共分享 | 开关、有效期、密码、Ticket、链接身份和访客操作；内部查看权限与分享权限不是同一入口 | S：XpackShareApi、ShareTicketApi、LinkTokenValidationManage、ShareVisitorPermissionManage；D15 |
| DE-35 | 嵌入式 | 应用/密钥/域名/Token、DIV 或 iframe、整板/单图/设计器/模块嵌入、双向传参。前端嵌入库不能代替服务端验证 | S：EmbeddedApi、router/embedded、嵌入页面；D16 |
| DE-36 | 登录与平台对接 | 本地/第三方认证、续期、MFA；CAS/OIDC/LDAP/OAuth2/SAML2；企微/钉钉/飞书的免登、账号绑定和通知通道 | S：LoginApi、认证/平台前端及 SDK；D17 |

### 2.7 运营与系统治理

| ID | 功能与用途 | 关键操作及约束 | 证据 |
|---|---|---|---|
| DE-37 | 导出中心 | 排队/执行/成功/失败、进度、重试、取消、下载及过期清理；图表/数据集/其他资源的任务上下文不同 | U（空列表）、S：ExportCenterManage/DownLoadManage；D18 |
| DE-38 | 定时报告 | 按周期渲染选定资源，携带过滤、格式、水印、收件对象、重试与执行记录；发送人的数据范围与收件人规则必须明确 | S：ReportApi/ReportCreator；D18；私有执行实现缺失 |
| DE-39 | 阈值告警 | 绑定资源/图表、调度、规则、重复发送、消息内容、启停和记录；“图表辅助线”不是后台告警 | S：ThresholdApi/ThresholdCreator、前端；D18；私有执行实现缺失 |
| DE-40 | 数据同步 | 来源/目标、字段和主键策略、任务调度/启停/一次执行、日志和终止；与数据源连通性巡检不同 | S：api-sync、syncTask 页面、extensions-sync；实现缺失 |
| DE-41 | 血缘与日志 | 资源引用列表/关系图、节点跳转权限、操作日志和导出；公开说明是资源关系，不据此推断 SQL 字段级血缘 | S：relation/index.ts、association 页面、LogApi；D18 |
| DE-42 | 系统运行和扩展 | 请求超时、保留周期、巡检、分享策略、地图/字体/引擎、外观、水印、安全、插件、API Key、Webhook/邮件/消息、AI 组件与 SQLBot 集成 | U（参数/字体）、S：各 SDK/前端入口；D19/D20；企业子集未实测 |

DE-42 的子项分别有独立边界：插件含安装/更新/卸载及数据源/图表扩展契约；API Key/HMAC 属于系统集成身份；Webhook/邮件是外发通道；AIComponentApi.findTargetUrl 与 sqlbot/dataset 是集成入口，不证明 DataEase 自身实现了完整自然语言 SQL 引擎。

## 3. 已理解的核心调用链

### 3.1 数据模型和取数

```text
数据源配置/驱动 → 表/字段元数据 → 表/SQL/关联模型 → 数据集字段
  → 图表类型与轴配置 → /chartData/getData
  → ChartDataManage.calcData
  → 数据集资格 + 当前用户行列规则 + 组件/联动/参数/钻取过滤
  → Handler/SQLMeta/方言与 Provider → 查询结果 → 图表后处理与渲染
```

`ChartDataServer` 区分模板示例与真实数据分支；`calcData` 会将可信当前登录用户用于查询上下文，并取得数据集资格和行列规则，进一步处理轴和附加过滤。`getFieldData/sqlData` 也是取数入口，不能只保护最终图表接口。这里描述已读代码的职责，不声称整条链路经过安全审计。

多表关联和计算字段改变了权限注入的位置和可用字段集合；时间钻取还涉及区间过滤。不同方言、数值精度、空值和时区需分别验证，不能只复用一条 MySQL SQL 字符串。

### 3.2 编辑与发布

文档包含布局/组件树、画布样式、图表定义、交互配置及移动配置；编辑快照记录临时操作，发布副本承载消费者可见状态。参考源码提供“恢复到发布稿”，不把它误认为多版本审计历史。组件复制、模板导入和发布都可能涉及关联 ID，不是只复制 JSON 外观。

### 3.3 筛选、联动、钻取、跳转

查询组件绑定字段及目标图表；联动通过点击维度和值影响目标；钻取保留维度路径并切换轴；跳转/外部参数跨载体传值。前端将这些状态聚合进附加查询请求，后端再组合查询。我们自主实现时只接受发布允许的绑定与类型化值，不照搬客户端 user 字段作为身份，也不允许任意 QueryModel 覆盖发布内容。

### 3.4 模板生命周期

预览→解析→适配历史版本→生成新 ID→组件/图表关系重写→样式或应用绑定→实例保存。`decompression()` 在保存载体前已预写扩展数据并存静态资源，这解释了取消创建也可能留存暂存资源。应用模板与样式模板必须分开处理。

### 3.5 导出、报告、告警、同步

导出将资源和参数登记为任务，记录发起人，执行、生成文件、颁发下载凭据并过期清理；现有源码的下载入口校验任务归属。报告/告警/同步则有各自资源选择、调度、重试和日志契约。它们共享调度基础设施，但不是同一个业务任务。报告的用户身份、权限变化后的行为以及重复发送策略，仍需企业实现或运行环境才能最终确认。

### 3.6 分享与嵌入身份

公开链接有独立验证上下文、有效期/密码/Ticket，访客能力还可受分享者当下能力约束；嵌入有应用身份与来源限制。它们不等于若依用户登录。Base64 只是传输编码，不能承担保密或授权。跳转目标、Tab 内嵌子资源、明细与导出都不能因外层可见而默认放行。

## 4. 架构层面的区别与可借鉴点

- 主程序、SDK 契约、数据源/图表/同步扩展、权限扩展分别承担不同职责。我们借鉴依赖方向和产品行为，保持 RuoYi 适配器与 BI 核心分离，不引入 DataEase 私有实现。
- 参考项目数据持久化使用 JPA/QueryDSL；已有非空更新切面，不能把 null 当作通用清空语义。启动通过版本化 SqlBlock 执行迁移，因此只读学习不启动/重建参考后端。
- 图表类型处理器、SQL 转换和数据源 Provider 分工，值得用于我们后续图表/方言扩展；无需为复用其概念迁移 ORM 或替换现有查询链。
- `DatasetCacheManage` 观察到的是表/字段元数据缓存，不能称为完整查询结果缓存；JCache 和 Redis 的过期语义不同。自研结果缓存必须明确主体、权限版本、发布版本和过滤参数维度。
- 社区/desktop 有条件替补身份实现，不能作为我们的生产权限方案。若依身份/菜单/角色继续复用，BI 资源 ACL、行列策略、参数和输出规则由 BI 核心负责，S11 再实现独立身份和部署。
- 部署依赖包括元数据库、分析数据源、缓存/调度及按功能启用的外围服务；截图渲染、同步和通知需要单独运维。不能用“Spring Boot 能启动”代替独立产品部署验收。

## 5. 对我们项目的完整差距归纳

| 能力层 | 当前已有 | 尚缺或需深化 |
|---|---|---|
| 身份权限 | 若依可信登录、功能码、主体映射、资源 ACL、行交集/列隐藏/常量掩码，现有消费与探索分离 | 目录继承、属性映射、更多脱敏、所有新增出口、独立身份与部署包 |
| 数据接入 | MySQL 兼容协议下的连接测试、目录元数据、Doris 连接登记 | Doris 业务场景完整验收、其他方言、池治理、文件/API/远程文件/扩展驱动 |
| 模型查询 | 单表数据集、基础聚合/排序、类型检查、参数化 AND/OR 过滤及复杂度限制 | 关联/跨源、计算/分组字段、完整 SQL 编辑与参数、结果类型/时间语义、治理和缓存 |
| 图表 | 柱线饼表、保存重开、预览、资源权限 | 指标卡、透视/组合/地图等类型及完整样式与交互 |
| 载体编辑 | 看板 12 列栅格、拖拽/数值布局、保存发布/下线、CAS、当前发布快照、引用保护 | 主题/组件协议、容器、编辑命令栈、筛选联动、自由大屏、移动独立编排、历史治理 |
| 模板与分析扩展 | 调研和 WBS 已分拆 | 样式模板、应用模板、市场、电子表格、填报均未形成自研闭环 |
| 交付与集成 | 若依环境下已测基础业务链 | 导出、公共分享/嵌入、报告/告警/同步、工作台、血缘界面、监控恢复和独立产品运维 |

完整了解参考功能不等于全部纳入自研范围。原 MVP 与 S11/S12 的边界保持；任何新增产品范围都需要明确需求与验收，不能把 DataEase 企业版清单直接加入当前工期。

## 6. 本轮界面记录

1. **系统参数：可用**。看到请求超时、文件保留、巡检、分享开关/密码要求、排序、打开方式和跨源限制，未编辑。
2. **字体管理：可用入口**。看到内置默认字体和上传/添加入口，未上传或修改。
3. **数据源类型与 Doris 配置：可用**。看到分类、主机/JDBC、SSH、连接池和超时；取消空白表单，未提交或测试新连接。
4. **模板管理：可用空列表**。当前没有本地模板，看到分类/导入入口，未导入。
5. **导出中心：可用空列表**。可见等待/执行/成功/失败状态，未创建导出任务或下载业务数据。
6. **关于：可用**。明确社区版 3.1.0；未修改许可证。

![社区版版本证据](../../.local/bi-reference/full/06-runtime-edition.png)

![导出中心状态](../../.local/bi-reference/full/05-export-center.png)

![系统参数](../../.local/bi-reference/full/01-system-parameters.png)

其他截图位于 `D:/datacenter/.local/bi-reference/full/`。本轮截图已与实际界面核对；未执行完整读屏、键盘或对比度测试，不能声称无障碍合规。部分配置区需滚动，截图没有展示的内容以可访问性树和源码观察说明，不做像素布局结论。

## 7. 仍无法下结论的部分

| 未确认项 | 原因 | 当前已知与后续验证条件 |
|---|---|---|
| 企业权限继承、合并优先级及失效实现 | X-Pack 未初始化，社区实例没有对应页面 | 已读公开契约/核心调用和文档；需合法可用实现或相应运行环境核验，不能改用默认放行 |
| 企业报告/告警/同步实际执行 | 缺执行模块、渠道和样例任务 | 已理解任务/规则/接收人/日志数据结构；需隔离环境和授权测试收件端，不能擅自发送消息 |
| 填报数据库写回及任务权限 | 社区实例未开放，属于写入操作 | 已读 API/表单/文档；需测试目标库与任务对象，不能对现有业务库试写 |
| 电子表格完整操作与计算兼容 | 本实例无菜单，后端扩展缺失 | 已读插件分工和查询/发布契约及文档；需相应模块才能验完整行为 |
| 所有联动/钻取/参数组合及模型方言 | 本轮未创建专用业务场景 | 已定位核心处理链，不能宣称所有数据库/图表组合正确 |
| 所有导入兼容、性能、恢复与安全性 | 静态阅读和空列表不能证明 | 必须另有数据规模、升级版本、故障/撤权矩阵及测试环境 |

当前达到的是**全功能目录覆盖与关键架构链路理解**，不是“所有实现细节已知”或“全功能验证通过”。缺失处已明确定位，避免以后开发时继续依赖记忆猜测。

## 8. 官方资料索引

下列链接支持相应产品语义；正文抓取部分超时，本轮使用了可访问的官方索引/检索内容，并与现有源码对照。文档可能随版本更新，执行细节以目标版本实现和验证为准。

- D1 [工作台](https://docs.fit2cloud.com/dataease/user_manual/homepage/)
- D2 [数据源](https://docs.fit2cloud.com/dataease/user_manual/datasource_description/)
- D3 [数据集](https://docs.fit2cloud.com/dataease/user_manual/dataset_description/)
- D4 [图表图库](https://docs.fit2cloud.com/dataease/user_manual/view_module/view_gallery/)
- D5 [组件基础功能](https://docs.fit2cloud.com/dataease/user_manual/module_basicfunctions/)
- D6 [查询组件](https://docs.fit2cloud.com/dataease/user_manual/filter_module/filtert_module/)
- D7 [仪表板基础功能](https://docs.fit2cloud.com/dataease/user_manual/dashboard_basicfunctions/)
- D8 [创建数据大屏与模板类型](https://docs.fit2cloud.com/dataease/user_manual/panel_create/)
- D9 [移动端](https://docs.fit2cloud.com/dataease/user_manual/app_description/)
- D10 [电子表格概述](https://docs.fit2cloud.com/dataease/xpack/spreadsheet_description/)
- D11 [电子表格特殊功能](https://docs.fit2cloud.com/dataease/xpack/spreadsheet_special/)
- D12 [数据填报](https://docs.fit2cloud.com/dataease/xpack/data_filing/)
- D13 [企业扩展范围](https://docs.fit2cloud.com/dataease/faq/enterprise_faq/)，具体角色合并规则仍待实现核验。
- D14 [数据集行列权限](https://docs.fit2cloud.com/dataease/xpack/dataset/)
- D15 [3.1.0 公共链接访客能力说明](https://docs.fit2cloud.com/dataease/change-v3-1-0/)
- D16 [嵌入式管理](https://docs.fit2cloud.com/dataease/xpack/multidimensional_embedding/)
- D17 [企业扩展及平台对接范围](https://docs.fit2cloud.com/dataease/faq/enterprise_faq/)
- D18 [功能总表：报告、告警、血缘与导出](https://docs.fit2cloud.com/dataease/changelog/)，本报告仅将其作为模块范围说明，执行细节来自上述契约或标为未知。
- D19 [系统参数](https://docs.fit2cloud.com/dataease/user_manual/system_management/param/)
- D20 [系统架构](https://docs.fit2cloud.com/dataease/system_arch/)

## 9. 源码定位索引

均相对 `D:/bi-tools/dataease`：

- 前端入口：`core/core-frontend/src/router/{index,establish,mobile,embedded}.ts`、`src/views/pages/`。
- 数据接入：`sdk/api/api-base/.../api/ds/`、`core/core-backend/.../datasource/provider/`、前端 `views/visualized/data/datasource/`。
- 数据集：SDK `api/dataset/`、后端 `dataset/manage/`、前端 `views/visualized/data/dataset/form/`。
- 查询：`core/core-backend/src/main/java/io/dataease/chart/{server/ChartDataServer,manage/ChartDataManage}.java`、`engine/`、`chart/charts/`。
- 编辑器：前端 `views/dashboard/`、`views/data-visualization/`、`components/dashboard/`、`components/data-visualization/`、`store/modules/data-visualization/`。
- 交互：前端 `custom-component/v-query/`、`api/visualization/{linkage,linkJump,outerParams}.ts`、`views/chart/components/views/index.vue`。
- 模板：前端 `views/template/`、`views/template-market/`、`utils/canvasUtils.ts`；后端 `visualization/server/DataVisualizationServer.java`。
- 权限契约：`sdk/api/api-permissions/`；核心调用 `dataset/manage/PermissionManage.java`；扩展实现 `de-xpack` 本地缺失。
- 分享：后端 `share/`、SDK `api/xpack/share/`；嵌入契约 `api-permissions/.../permissions/embedded/`。
- 导出：后端 `exportCenter/manage/`；SDK `api/exportCenter/` 和 `api/chart/ChartDataApi.java`。
- 电子表格：前端 `views/menu/spreadsheet/plugins/`、SDK `api/spreadsheet/`；不以插件文件存在认定企业后端可用。
- 填报/报告/告警：SDK `api/xpack/dataFilling/`、`api/report/`、`api/threshold/` 及其页面；私有执行实现缺失。
- 同步：`sdk/api/api-sync/`、`sdk/extensions/extensions-sync/`、前端 `api/sync/` 和同步页面。
- 横切能力：`sdk/common/.../cache/`、后端 `listener/InitSqlListener.java`、`config/JpaUpdateNonNullAspect.java`、`websocket/`。

调研期间没有提交/推送、安装依赖、启动参考服务、初始化子模块、改动许可证或修改权限；新建数据源表单已取消，未产生新业务连接。文档与静态清单是本轮交付。按用户要求，功能开发继续暂停。
