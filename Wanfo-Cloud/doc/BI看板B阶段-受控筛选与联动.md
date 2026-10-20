# B 阶段：受控看板筛选与联动

## 定义和发布

schemaVersion 继续为 1，新增可选 interaction；旧草稿和发布快照默认为空 controls/links。每次保存/发布重新检查引用、当前作者字段资格及类型，服务端生成 target.valueType，不信任客户端类型声明。发布冻结控件默认值、源/目标字段和图表查询；消费时不读取新草稿或新图表定义。

```json
{
  "interaction": {
    "controls": [{"key":"city_filter","label":"城市","type":"enum","defaultValue":"上海",
      "targets":[{"panel":"bar","field":"city"},{"panel":"detail","field":"city_name"}]}],
    "links":[{"key":"city_link","sourcePanel":"bar","sourceField":"city",
      "targets":[{"panel":"detail","field":"city_name"}]}]
  }
}
```

最多 12 个控件、12 条联动；每项 1～20 个不同目标组件。ID 为 1～64 位字母/数字/下划线/连字符，所有交互 ID 唯一。字段类型统一为 text / number / date，目标类型必须相同；date 本轮仅支持 DATE，未包含 TIMESTAMP。联动源须为图表第一维度，不允许自指；多条联动直接与目标条件相交，不进行递归传播。

控件 type：text（精确 EQ）、enum（单值 EQ）、number/date（两个端点的闭区间）。number 的 BigDecimal 精度最多 30、scale 绝对值最多 12；日期为严格 ISO YYYY-MM-DD。字符串最长 256 字符；拒绝对象、嵌套数组和布尔值，默认值同样校验。文本/枚举的 null 或空字符串表示不筛选；number/date 的 null 表示不筛选，区间须完整。联动 null 则明确表示 IS NULL，空字符串链接值是 EQ 空字符串。

## 消费接口

POST `/bi/dashboard/{id}/panels/{key}/data?revision=...`，可选请求体：

```json
{"filters":{"city_filter":"杭州"},"links":{"city_link":"杭州"}}
```

filters 缺少某个 key 时采用发布默认值；显式 null 清除该控件。links 只有提交的项生效。任意未知顶层属性、未发布控件/联动 ID、错误值类型返回 400；发布版本变化返回 409；权限/行列规则失效拒绝查询。服务端复制冻结查询，在原筛选及 filterTree 之外追加 AND 条件，不修改快照。请求不能覆盖 datasetId、字段或查询结构。

POST `/bi/dashboard/{id}/controls/{key}/options?revision=...` 接受同样请求体，仅支持 enum。使用第一个目标的冻结图表查询，保留原固定筛选、其他已应用筛选和联动，去掉控件自身条件后，按该绑定字段分组读取去重值。以当前消费主体取数，返回 values（不含 NULL）及 truncated，最多 100 个候选值。候选项不是绕过权限的白名单：所有用户提交值仍参数绑定，并与行权限相交。

查看清单仅公开 key/label/type/defaultValue 及联动 sourcePanel，不公开字段映射、数据集 ID、图表 ID 或查询。候选值需要看板消费与相应数据集 consume 权限，不要求探索权限。联动还重新检查来源图表的消费和列权限；目标各自执行当前用户的行列策略。身份与授权使用现有接口，未来独立部署无需修改协议。

## 查看端状态

输入、已应用筛选和活动联动分离；首次加载使用发布默认值。应用筛选刷新双图并清除旧联动；清空/恢复默认按钮仅修改输入。点击来源图表或明细“选择”即时联动，待应用输入不参与取数；清除联动保留已应用筛选。

同发布版本整体刷新保留输入、已应用条件和活动联动，发布版本变化重新初始化默认值。界面使用请求代次与组件请求序号抑制过期响应；每轮最多两个组件 worker。图表编辑页中仅配置/保存绑定，未发布筛选不影响草稿原图预览。

本轮未包含多选、模糊匹配、枚举 NULL 选项、服务端远程搜索、父子级联声明、Tab/容器作用域、钻取和外部参数。治理限流/超时预算仍按 WBS 后续专项实施；客户端防旧响应不等于服务端取消执行。

## 验证

107 项 BI 测试通过；30 项本地 MySQL 接口检查通过，覆盖类型映射、默认值隔离、范围/日期、0、未知请求、版本、纯消费账号、行规则、遮蔽、撤权即时生效。浏览器验证输入/应用、清空/重置、数值/日期、点击/明细联动、保存重开和同版本刷新；可复用看板 ID 为 55。前端最终生产构建通过。工程全量 vue-tsc 的原有类型声明阻塞仍存在，未声称通过。
