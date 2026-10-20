package com.wanfo.bi.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询结果。
 *
 * <p>刻意用 {@code List<Map<String,Object>>} 而不是强类型 DTO：BI 查询的列是运行期决定的
 * （数据集 + 图表定义决定），无法预编译成 Java 类。列顺序用 {@link LinkedHashMap} 保证。
 *
 * @author zhengliang
 */
public class QueryResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 列名列表，顺序与 rows 中的 key 一致 */
    private List<String> columns = new ArrayList<>();

    /** 数据行 */
    private List<Map<String, Object>> rows = new ArrayList<>();

    /** 执行耗时（毫秒） */
    private long costMs;

    /** 实际执行的 SQL，便于前端展示与排查 */
    private String executedSql;

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }

    public long getCostMs() { return costMs; }
    public void setCostMs(long costMs) { this.costMs = costMs; }

    public String getExecutedSql() { return executedSql; }
    public void setExecutedSql(String executedSql) { this.executedSql = executedSql; }

    @Override
    public String toString()
    {
        return "QueryResult{columns=" + columns.size() + ", rows=" + rows.size()
                + ", costMs=" + costMs + "}";
    }
}

