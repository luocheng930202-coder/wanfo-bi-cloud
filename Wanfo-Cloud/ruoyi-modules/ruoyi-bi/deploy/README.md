# 若依权限接入：第一批

第七批增加 V8 看板草稿/当前发布快照及引用绑定，入口 `/bi-admin/dashboard`。升级前备份并执行全部 V3～V8，然后显式重跑菜单脚本。纯看板使用者需看板 list/consume 功能、看板 view/consume 及数据集 consume 资源资格，无需自由查询权限；授权不自动扩大到数据集。发布与消费、回退边界及 92 个测试/72 项联调见 [看板编排发布与消费授权](../../../doc/BI看板-编排发布与消费授权.md)。

第六批已增加 V7 图表定义存储及乐观版本、`/bi-admin/chart` 编辑入口和图表功能/资源权限。升级前备份，执行全部 V3～V7，再显式重跑菜单脚本。图表取数仍要求当前用户具备数据集查看/查询资格；看板纯消费策略尚未实现。定义格式、安装步骤及 72 个测试/38 项联调见 [基础图表配置预览与保存](../../../doc/BI图表-配置预览与保存.md)。

第五批已扩展图表 QueryModel 的嵌套 AND/OR 过滤树及类型、分页、别名、排序校验，无新增迁移或菜单。旧 `filters` 继续兼容，但空 IN、模糊 BETWEEN、重复别名、非法分页等现在明确返回 code 400。升级后的接口契约及 60 个测试、34 项新联调说明见 [查询模型与过滤树](../../../doc/BI查询模型-过滤树与校验.md)。可视化筛选编辑器随下一阶段图表配置接入。

最新第四批已增加 V6 固定行规则及字段隐藏/常量脱敏，数据集预览与图表查询在聚合/分页前执行策略。管理员通过数据集“数据权限”配置；普通消费者仍须若依功能权限和数据集 view/query。升级边界、冲突语义、管理员豁免及验证见 [固定行规则与列限制](../../../doc/BI数据权限-固定行规则与列限制.md)。部署应执行全部 V3～V6 迁移；下文历史范围由最新批次说明补充。

当前已完成若依身份/功能权限适配、主体映射基础、数据源和表数据集资源授权，新增/编辑表单可测试连接，保存失败不写库。已支持授权范围内库表字段浏览、保存前预览及表数据集保存与授权。行列规则、其他资源策略和旧权限表主体迁移仍按 WBS 推进。

## 零售指挥大屏演练数据

全链路案例（模板大屏 + 宽表建模 + 地图/下钻/填报等）见仓库 `doc/零售指挥大屏-搭建手册.md`。装载演示表：

`powershell -ExecutionPolicy Bypass -File deploy/seed-retail-screen.ps1`

写入数据源 JDBC 对应库（默认 `bi_phase_a_test`）。

列中文注释与已有数据集 `show_name` 同步：

`pwsh -ExecutionPolicy Bypass -File deploy/refresh-dataset-show-names.ps1`

（可选 `-DryRun`、`-AllDatasources`、`-SkipRetailComments`；SQL：`demo-retail-column-comments.sql`、`refresh-dataset-show-names.sql`）

## 运行与安装

本地编译 BI 模块请使用 `D:\Java\apache-maven-3.9.9` 自带 `settings.xml`（本地仓库 `D:\Java\mavenRepo`、阿里云镜像），避免用户目录 `~/.m2/settings.xml` 中不可达的内网 Nexus。可先停止 9206 进程，再在仓库根目录执行 `deploy/build-bi.ps1`，或：

`D:\Java\apache-maven-3.9.9\bin\mvn.cmd -gs D:\Java\apache-maven-3.9.9\conf\settings.xml -f pom.xml -pl ruoyi-modules/ruoyi-bi -am package -DskipTests`

1. BI 模式配置为 `bi.security.mode=ruoyi`（默认）；其他模式尚无实现，缺少适配器会导致启动失败，不自动放行。
2. 正常启动 BI 时，Flyway 在现有 V1/V2 后执行 V3，新增 `bi_principal`，不修改 `sys_*`、历史行列规则或授权。执行升级前备份元数据库。当前代码尚不支持直接部署为独立产品。
3. 在若依系统数据库显式执行 `ruoyi-menu.sql`，安装 `/bi-admin/datasource` 菜单及操作权限。脚本使用自动 ID，可重复执行；执行前确认没有自定义同名路径占用，执行后核对输出。此脚本不由 BI 启动自动执行。
4. 在若依角色管理中给指定角色分配菜单及所需按钮。脚本不自动给普通角色授权；若依超级管理员沿用 `*:*:*`。角色变更后重新登录，确保 Redis 登录态中的权限集合更新；本批沿用若依会话权限快照，不承诺活动会话秒级撤权。
5. 前端开发代理：`/dev-api/bi` 指向 BI 9206（可用 `VITE_BI_API_TARGET` 配置），其他 `/dev-api` 指向若依网关 8080。需启动若依认证、系统、网关及相应 Nacos/Redis/MySQL；不能再把所有请求代理到 BI。生产反向代理同样须正确区分这些入口。网关使用本地文件时，除公共配置目录外显式添加 `file:D:/datacenter/ruoyi-nacos-config/ruoyi-gateway-dev.yml`，目录不会自动加载这个专用文件；路由前缀为 `spring.cloud.gateway.server.webflux`，BI 路由不移除 `/bi`。

## 权限契约

| 权限码 | 数据源接口 |
|---|---|
| `bi:datasource:list` | GET manage / list |
| `bi:datasource:add` | POST 数据源 |
| `bi:datasource:edit` | PUT 数据源 / 状态 |
| `bi:datasource:remove` | DELETE 数据源 |
| `bi:datasource:test` | POST probe-test / 已保存 schema 的 test；GET probe |
| `bi:datasource:sync` | POST reload（覆盖配置同步，单独授权） |
| `bi:datasource:grant` | GET principals / 资源授权列表；POST 增加授权；DELETE 撤销授权 |

所有 `/bi/**` 控制器接口要求有效登录，包括直连 BI 的请求。数据源接口额外检查上述功能码及资源授权。元数据建模及表数据集已有各自功能和资源校验；SQL 数据集、原始 SQL 和未接入策略的图表接口仍要求 `bi:resource:admin`（超级管理员 `*:*:*` 可访问）。

最新升级需执行 V3～V5，并显式重跑菜单脚本以安装 `/bi-admin/dataset` 及 `bi:datasource:model`。表数据集新功能码、资源动作、消费与建模权限边界及 32 个单测/45 项联调结果见 [数据集建模预览与授权](../../../doc/BI数据集-建模预览与授权.md)。下文第一批记录保留作历史追溯，以最新批次说明为准。

V4 升级新增资源归属和授权表，已有数据源默认由管理员管理，普通角色须显式获授查看和操作权限。若依菜单脚本已增加授权功能码，升级时重新执行一次。数据源列表中的“授权”支持选择若依用户、角色、部门；授予编辑等操作时应同时授予查看，功能权限仍须在若依角色管理中配置。详细行为和本轮验证见 [数据源资源授权与连接测试](../../../doc/BI数据源-资源授权与连接测试.md)。

`CurrentActorProvider`、`CapabilityProvider`、`PrincipalDirectory` 和 `BiActor` 不引用若依身份类型。若依会话及响应包装放在 `adapter/ruoyi`；未来独立外壳替换这些适配实现。应用启动注解和基础设施依赖仍需未来独立装配任务拆分。

BI 先验证签名 Token 对应的 Redis 会话、过期时间和账户状态，再运行若依 HeaderInterceptor；当前用户及审计名来自服务端会话，不读取客户端 `user_id`/`username` 身份头。主体由身份来源 + 类型 + 外部 ID 唯一映射，用户/角色/组织内部 ID 不混用。无映射时按首次访问创建，不扫描或复制全部若依用户。组织/角色成员信息沿用若依会话快照。

V3 只增加主体目录，不重新解释旧 `role_id`。后续 S2-T09 通过新迁移明确旧规则映射与资源归属，禁止将若依角色 ID 当作 BI 主体 ID 使用。

## 验证及回滚

自动测试覆盖直接 HTTP 请求的匿名/无效/过期身份、功能拒绝、允许、通配授权和伪造用户头；使用 MockMvc、模拟会话和主体目录，不会启动真实服务或修改数据库。

2026-10-16 本批验证结果：

- 后端：`mvn -pl ruoyi-modules/ruoyi-bi -am test "-Dtest=BiDatasourceSecurityTest,DatabasePrincipalDirectoryTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过，10 个测试、0 失败；覆盖全部 10 个数据源路由的功能拒绝和替代适配器路径。
- 前端：`npm run build:prod` 通过。
- 前端：`vue-tsc --noEmit` 未通过；已有 `src/types/global.d.ts` 的 `declare module 'vue'` 覆盖 Vue 导出，并存在 MenuSortParams、日志类型等缺失。本批未修改这些声明；不将生产构建通过表述为类型检查通过。
- 首轮验证时 Docker 引擎未运行。随后用户启动 Docker 并授权联调，已完成 V3、菜单安装、真实登录与角色/API/浏览器验证，见下述联调记录。

数据库集成验收需在隔离库执行 V1→V2→V3，检查 Flyway 历史、映射唯一性及并发首次访问。菜单安装、若依真实登录、权限分配和页面交互需在联调环境验证，未执行时不得标为已通过。

2026-10-16 后续联调已验证开发库 V2→V3 增量升级、菜单重复安装、真实角色允许/拒绝、实际审计身份、并发读取映射唯一性、撤权后重新登录、真实会话失效及浏览器按钮权限。测试用户/角色/数据源已逻辑删除，测试会话已清理。全新隔离库完整初始化及并发首次建映射仍需另行验证，不能由本次并发读取替代。详细结果见 [第一批联调记录](../../../doc/BI若依权限-第一批联调记录.md)。

回滚应用时，V3 新表可保留；旧 V1/V2 表不变。不通过删除 Flyway 历史或改写 V3 回滚。若需移除菜单，先回收角色授权并保存菜单 ID 清单，再按该清单移除本次菜单，避免按通配路径误删自定义菜单；数据恢复使用升级前备份。
