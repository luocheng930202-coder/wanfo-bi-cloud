package com.wanfo.bi.domain.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图表查询模型（中间表示）。
 *
 * <p>它是「前端图表配置」与「最终 SQL」之间的桥梁：
 * <pre>
 *   前端图表定义 JSON
 *     -> QueryModel（本类，运行期校验）
 *     -> ChartQueryCompiler 编译
 *     -> ★ 行级权限注入（阶段 S8）
 *     -> ★ 列级裁剪（阶段 S8）
 *     -> Calcite 执行
 * </pre>
 *
 * <p>刻意不直接拼 SQL 字符串：先过模型是为了能在编译期做字段校验、
 * 聚合白名单检查、以及后续插入权限条件。
 *
 * @author zhengliang
 */
public class QueryModel implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 聚合函数白名单，防止前端传入任意函数 */
    public static final List<String> AGG_WHITELIST = List.of(
            "SUM", "AVG", "COUNT", "COUNT_DISTINCT", "MIN", "MAX", "NONE");

    /** 比较操作符白名单 */
    public static final List<String> OP_WHITELIST = List.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "NOT_LIKE",
            "IN", "NOT_IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL");

    /** 数据集 ID */
    private Long datasetId;

    /** 图表类型 */
    private String chartType;

    /** 维度（GROUP BY 的部分） */
    private List<Dimension> dimensions = new ArrayList<>();

    /** 度量（SELECT 的聚合部分） */
    private List<Metric> metrics = new ArrayList<>();

    /** 过滤条件（WHERE 的部分） */
    private List<Filter> filters = new ArrayList<>();

    /** Nested user filters; combined with legacy filters using AND, never with security predicates using OR. */
    private FilterNode filterTree;

    /** 排序 */
    private List<OrderBy> orderBys = new ArrayList<>();

    /** 返回行数上限 */
    private Integer limit;

    public QueryModel addDimension(String field, String alias) {
        dimensions.add(new Dimension(field, alias));
        return this;
    }

    public QueryModel addMetric(String field, String agg, String alias) {
        metrics.add(new Metric(field, agg, alias));
        return this;
    }

    public QueryModel addFilter(String field, String op, Object value) {
        filters.add(new Filter(field, op, value));
        return this;
    }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public List<Dimension> getDimensions() { return dimensions; }
    public void setDimensions(List<Dimension> dimensions) { this.dimensions = dimensions; }

    public List<Metric> getMetrics() { return metrics; }
    public void setMetrics(List<Metric> metrics) { this.metrics = metrics; }

    public List<Filter> getFilters() { return filters; }
    public void setFilters(List<Filter> filters) { this.filters = filters; }

    public FilterNode getFilterTree() { return filterTree; }
    public void setFilterTree(FilterNode filterTree) { this.filterTree = filterTree; }

    public List<OrderBy> getOrderBys() { return orderBys; }
    public void setOrderBys(List<OrderBy> orderBys) { this.orderBys = orderBys; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    /** 维度：SELECT 原始列 + GROUP BY */
    public static class Dimension implements Serializable
    {
        private static final long serialVersionUID = 1L;
        /** 字段名（对应 bi_dataset_field.origin_name） */
        private String field;
        /** 输出别名，不填则用字段名 */
        private String alias;

        /** Optional calendar bucket; null keeps the original value. */
        private String timeGrain;

        public Dimension() {}

        public Dimension(String field, String alias)
        {
            this.field = field;
            this.alias = alias;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }

        public String getTimeGrain() { return timeGrain; }
        public void setTimeGrain(String timeGrain) { this.timeGrain = timeGrain; }
    }

    /** 度量：聚合表达式 */
    public static class Metric implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private String field;
        /** 聚合函数，见 AGG_WHITELIST；NONE 表示直接取列 */
        private String agg = "NONE";
        private String alias;

        public Metric() {}

        public Metric(String field, String agg, String alias)
        {
            this.field = field;
            this.agg = (agg == null || agg.isBlank()) ? "NONE" : agg.toUpperCase();
            this.alias = alias;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getAgg() { return agg; }
        public void setAgg(String agg) { this.agg = agg; }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    /** 过滤条件 */
    public static class Filter implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private String field;
        /** 见 OP_WHITELIST */
        private String op;
        /** 单值 / IN 列表 / BETWEEN 的 [起,止] */
        private Object value;

        public Filter() {}

        public Filter(String field, String op, Object value)
        {
            this.field = field;
            this.op = (op == null) ? null : op.toUpperCase();
            this.value = value;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    /** A group has logic AND/OR and children; a leaf has field/op/value. Mixed shapes are rejected. */
    public static class FilterNode extends Filter
    {
        private String logic;
        private List<FilterNode> children;
        public String getLogic() { return logic; }
        public void setLogic(String logic) { this.logic = logic; }
        public List<FilterNode> getChildren() { return children; }
        public void setChildren(List<FilterNode> children) { this.children = children; }
    }

    /** 排序项 */
    public static class OrderBy implements Serializable
    {
        private static final long serialVersionUID = 1L;
        /** 可以是字段名或度量别名 */
        private String field;
        /** asc / desc */
        private String order = "asc";

        public OrderBy() {}

        public OrderBy(String field, String order)
        {
            this.field = field;
            this.order = (order == null || order.isBlank()) ? "asc" : order.toLowerCase();
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getOrder() { return order; }
        public void setOrder(String order) { this.order = order; }
    }
}

