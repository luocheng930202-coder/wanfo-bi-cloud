# 备份与恢复演练 Runbook（S3-06 / OPS-010）

## 声明目标（本机开发默认）

| 目标 | 声明值 | 说明 |
|---|---|---|
| 元数据库 RPO | 1440 分钟（1 天） | 可通过 `bi.backup.declared-rpo-minutes` 覆盖 |
| 元数据库 RTO | 240 分钟（4 小时） | 可通过 `bi.backup.declared-rto-minutes` 覆盖 |
| Redis | 可重建 | 会话/缓存丢失可接受；不以 Redis 为权威状态 |
| 数仓（ClickHouse） | 仓引擎责任人另册 | 本包只登记证据，不替代引擎侧备份 |
| 审计归档 | 长期保留 | `dp_audit_archive`；在线表按保留策略归档 |

## 备份（元数据）

```powershell
powershell -ExecutionPolicy Bypass -File D:\datacenter\scripts\backup-metadata.ps1
```

产物默认写入 `D:\datacenter\.local\backups\metadata-ry_vue-*.sql`。

## 恢复演练（必须用隔离副本）

1. 准备空的 MySQL 副本库（禁止指向生产/本机正在服务的 `ry_vue`）。
2. 导入 dump：`mysql ... < metadata-....sql`
3. 核对：`SHOW TABLES` 数量、`bi_flyway_history` 最新 version、抽查 `dp_asset`/`sys_user` 行数。
4. （可选）用临时配置启动 BI 指向副本，确认登录与目录只读可用。
5. 将结果登记到平台：

`POST /bi/ops/backup-drills`

```json
{
  "drillType": "RESTORE_DRY_RUN",
  "scope": { "metadataDb": true, "redis": false, "warehouse": false, "objectStore": false },
  "result": "PASSED",
  "rpoMinutes": 60,
  "rtoMinutes": 45,
  "artifactPath": "D:/datacenter/.local/backups/metadata-ry_vue-....sql",
  "checklist": ["副本导入成功", "Flyway 版本一致", "抽查资产表行数"],
  "notes": "在隔离副本完成，未触碰生产库"
}
```

## 就绪检查

- `GET /bi/ops/backup-drills/readiness`：声明 RPO/RTO、最近演练、本地 dump 年龄、Flyway 版本、审计归档计数。
- 前端：`/bi-admin/backup-drills`

## 边界

- **不**提供一键恢复生产；恢复始终人工确认。
- dump / 演练记录 **不得**包含明文密码或密钥。
- Flyway 升级演练：在副本先备份再升版本，登记 `FLYWAY_CHECK`。
