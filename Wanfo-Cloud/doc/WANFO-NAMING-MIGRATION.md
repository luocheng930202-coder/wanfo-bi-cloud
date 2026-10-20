# Wanfo 项目命名迁移

- GitHub 仓库名称：`wanfo-bi-cloud`。
- 后端工程目录：`Wanfo-Cloud`，根 Maven 坐标：`com.wanfo:wanfo-cloud:3.6.8`。
- 前端工程目录：`Wanfo-Cloud-Vue`，npm 包名：`wanfo-cloud-vue`，应用标题：`Wanfo BI Cloud`。
- Java 源码和测试源码位于 `src/main/java/com/wanfo`、`src/test/java/com/wanfo`。
- Java import、Maven groupId、MyBatis namespace/type、Spring 自动配置导入、扫描配置、日志类名、代码生成模板及初始化 SQL 同步使用 `com.wanfo`。

## 兼容边界

内部模块和微服务 ID（`ruoyi-auth`、`ruoyi-system`、`ruoyi-bi` 等）、Nacos Data ID、接口权限字符串、数据库表名、路由和 JAR 名称保留，避免同时改变部署拓扑。原始 LICENSE、作者信息和上游项目链接保留。旧 Git 历史保留在本地备份，新统一仓库使用清理后的源码快照。

## 已有环境升级

1. 备份当前代码、数据库和配置。彻底停止旧 Java 进程，在新目录先执行 `mvn clean`，再执行 `mvn verify`；Windows 中将两个命令分开，避免打包插件持有旧依赖 JAR 导致 clean 失败。
2. 检查 Nacos 已发布配置中的 MyBatis 包扫描、代码生成默认包名、日志配置和自定义全限定类名，改为新前缀。项目 SQL 中的初始化配置已更新，但不会自动覆盖服务器中已有配置。本机 `ruoyi-nacos-config/application-dev.yml` 已同步。
3. 如已使用代码生成表配置或全限定类名定时任务，先核对 [数据库迁移 SQL](../sql/wanfo-package-migration.sql)，在若依系统数据库中执行；不要在 BI 独立元数据库中执行。没有这些历史配置的环境无需执行。
4. 原 Redis 登录态等包含旧 Java 类型名的序列化对象可能无法继续使用，应重新登录，并按实际缓存键清理需要重建的业务缓存。不要清空整个共享 Redis。此次没有更改或放宽反序列化白名单。
5. 按原顺序启动认证、系统、网关和 BI 服务。前端在新目录执行 `npm run dev`。IDE、终端工作目录、启动任务和部署路径同步切换到新目录。

## 本地保留资料

修改前文本备份与迁移清单位于工作区 `.local/wanfo-rename-20261020/`，不上传 GitHub。外部进程占用导致旧工程目录可能留下空目录或原文件副本，正式源码以两个 `Wanfo-*` 目录为准。旧目录已由工作区 `.gitignore` 排除。

后续发布准备已将两个工程合并为根目录 Git 仓库，origin 指向 luocheng930202-coder/wanfo-bi-cloud。原 Git 元数据和完整历史 bundle 保存在 .local/release-prep-20261020/，不上传。现有业务数据库和 Nacos 远程配置未自动修改。JWT 签名密钥改由 WANFO_JWT_SECRET 提供，启动说明见根目录 docs/QUICKSTART.md。

## 验证结果（2026-10-20）

- 447 个 Java 包声明与新目录一致，无旧 Maven groupId 残留。
- 全部 23 个 Maven 模块 `verify` 成功；48 个测试套件、300 项测试，失败/错误/跳过均为 0。
- BI 可执行 JAR 中包含 203 个新包类，无旧包类目录。
- 前端 `npm run typecheck:bi`、`npm run build:prod` 均成功；全项目保留迁移前的 268 条既有类型诊断。
- 本地前端从 `Wanfo-Cloud-Vue` 启动，首页 HTTP 200 且标题为 `Wanfo BI Cloud`；认证、系统、网关和 BI 从新后端目录恢复运行。
- 旧目录递归清理被自动审批拦截，未提供更具体原因；未继续删除，副本由工作区忽略规则排除上传。
