# 查询模型、过滤树与校验（2026-10-16）

本批推进 S4-T01/T02/T03，扩展 `/bi/chart/data` 的 QueryModel。仍需 `bi:dataset:query` 与数据集 view/query；本批没有增加新的菜单或调整已有用户授权。`/bi/chart/compile` 仍只供管理员调试。

## 请求契约

以下示例中的 datasetId 应替换为实际有权查询的表数据集 ID，字段也须来自该数据集的可用字段清单。

```json
{
  "datasetId": 123,
  "dimensions": [{"field": "city", "alias": "城市"}],
  "metrics": [{"field": "id", "agg": "COUNT", "alias": "数量"}],
  "filters": [{"field": "id", "op": "GTE", "value": 2}],
  "filterTree": {
    "logic": "OR",
    "children": [
      {"field": "city", "op": "EQ", "value": "南京市"},
      {
        "logic": "AND",
        "children": [
          {"field": "id", "op": "BETWEEN", "value": [3, 5]},
          {"field": "city", "op": "NOT_LIKE", "value": "上海%"}
        ]
      }
    ]
  },
  "orderBys": [{"field": "数量", "order": "desc"}],
  "limit": 100
}
```

旧 `filters` 保留，全部按 AND 连接；同时提供 filterTree 时，将旧条件和整棵树再用 AND 连接。filterTree 可直接是字段条件，或是 AND/OR 分组；组不能混入 field/op/value，叶子不能携带 logic/children。不支持原始 SQL 条件、NOT 分组、HAVING 或表达式字段。

| 操作符 | 值 |
|---|---|
| EQ、NE、GT、GTE、LT、LTE | 非空字符串、数字或布尔标量；数值字段会校验并绑定 BigDecimal |
| LIKE、NOT_LIKE | 文本字段和字符串模式，保留 `%`、`_` 通配语义 |
| IN、NOT_IN | 1～100 项非空标量数组 |
| BETWEEN | 恰好两项非空标量数组 |
| IS_NULL、IS_NOT_NULL | 不提供 value，或 value 为 null |

日期、文本排序与 NULL 比较沿用底层数据库语义；本批未实现日期粒度、时区或任意表达式转换。EQ null 不自动转义为 IS NULL，空列表不静默解释为全量或空结果。NOT_IN / NOT_LIKE 编译为带空格的 SQL 关键字。

## 约束及返回

- 过滤树最多 8 层、100 个节点；旧 filters 与树共享节点额度。值最多 1000 字符，总过滤参数不超过 1000 项。
- 查询输出为 1～200 个维度/度量，排序最多 200 项。null 列表、null 列表项、混合形状和超限请求返回业务 code 400。
- 输出别名最长 128 字符，不含控制字符，并按不区分大小写检查唯一性。空别名统一采用默认名，SELECT 与 ORDER BY 使用同一名称。
- 排序支持明确的输出别名或无歧义来源字段，方向只接受 asc/desc；不能按不在输出中的字段排序。一个来源字段有多个度量时，使用明确的输出别名。
- SUM/AVG 只支持数值字段；COUNT、COUNT_DISTINCT、MIN、MAX 保留。NONE 为原始度量，不允许与聚合度量混用；与维度同时使用时按全部输出原始列分组，不再隐式替换为 MIN。
- limit 缺省为 1000，显式值须为 1～10000，SQL 和 JDBC 最大行数同时约束。负值、0、过大值直接拒绝，避免原实现的整数加一溢出及静默截断。
- 值始终按深度优先、从左到右的顺序绑定；不将用户值嵌入 SQL。标识符来自可见字段白名单，并使用引用符转义。
- JSON 字段类型错误统一返回 code 400，沿用若依 AjaxResult 格式；普通使用者仍看不到执行 SQL 或参数。

旧请求中依赖空 IN、标量 IN、多于两项的 BETWEEN、重复别名、无效排序方向或过大 limit 的情况需修正；本批有意将这些模糊行为改为明确拒绝。

## 权限及架构边界

数据权限先生成受限输入关系，再在外层应用用户过滤树，最后聚合、排序、LIMIT。用户 OR 只存在于外层筛选，不能把管理员配置的行限制合并成 OR；权限参数排在用户参数之前。隐藏及脱敏字段从编译白名单移除，树的任意深度都不能使用这些字段。

`FilterTreeCompiler` 和 `ChartQueryCompiler` 不依赖若依身份类型；宿主适配和主体规则解析仍在已有接口后面。已移除未使用的按 SQL 关键字位置插入权限片段的旧辅助方法，避免后续入口误用。当前为结构化模型编译为参数化 Calcite SQL，仍由 Calcite 解析执行；不将此批计为完整 Calcite SqlNode AST、多数据库方言或跨源查询优化完成。

本批没有新数据库迁移，也没有修改数据源配置或业务表。图表可视化编辑、定义保存与看板授权继续按 WBS 下一阶段推进；过滤树编辑器可复用本契约。

## 验证记录

- 后端共 60 个测试通过（新增 16 个过滤树与模型边界测试），Maven 打包通过，新版 BI 已启动。
- 真实若依/网关/BI/MySQL 新增 34 项检查通过，覆盖嵌套 OR 不扩大行权限、旧 filters 与树同时生效、Unicode NOT LIKE、NOT IN、注入值参数化、聚合前筛选、深层隐藏/脱敏字段拒绝，以及非法形状/深度/类型/分页/别名/排序返回 code 400。
- 原有 45 项建模及 24 项行列权限检查复验通过，包含同会话规则修改和多主体冲突。
- 临时目录、数据集、规则、用户、角色及会话已清理；本地临时凭据已移除，原有授权及 Doris 数据源保留。结果位于 `D:/datacenter/.local/bi-integration/filter-tree-results.json`。
- 本批为后端能力，没有新增页面，不新增前端构建结论；原有全量 TS 声明问题保持待处理。Doris 业务字段策略、其他协议和完整独立部署仍未计入验收。
