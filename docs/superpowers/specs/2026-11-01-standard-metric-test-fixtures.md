# 标准指标验收样例（手工/联调）

部署 V64 干净切换后，用同一表数据集按下列顺序重建：

## 1. 原子指标

| code | measure | agg | allowedDimensions |
|------|---------|-----|-------------------|
| `gmv_total` | amount | SUM | city, channel |
| `order_cnt` | id | COUNT | city, channel |

口径：业务定义 / 粒度=订单日 / 单位=元或单 / 时区=`Asia/Shanghai` / 负责人填写后发布。

## 2. 派生

| code | components | expression |
|------|------------|------------|
| `aov` | gmv_total→`gmv`, order_cnt→`orders` | `gmv/orders` |

允许维度：`city`（或取两原子交集）。发布后预览可选分组 `city`。

## 3. 复合（仅标量）

| code | expression |
|------|------------|
| `margin_proxy` | `(gmv_total - order_cnt) / gmv_total` |

组件：已发布 `gmv_total`、`order_cnt`。预览不应带运行时维度。

## 4. 自动化覆盖

`MetricStandardLifecycleTest` 覆盖：创建→发布→维内取数→非法维拒绝→派生按行求值→复合标量→消费上下文 allowlist。
