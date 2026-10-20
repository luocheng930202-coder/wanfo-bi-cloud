package com.wanfo.bi.controller;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.query.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * BI 查询接口（S3/S4 阶段雏形）。
 *
 * <p>当前仅用于打通链路验证，S4 阶段会重构为：
 * <pre>
 *   图表查询请求(JSON)
 *     -> 查询模型构建(维度/度量/过滤)
 *     -> 行级权限条件注入   &lt;-- S8 的落点，必须在这里
 *     -> 列级权限裁剪
 *     -> Calcite 编译成目标方言 SQL
 *     -> 执行
 * </pre>
 *
 * @author zhengliang
 */
@Tag(name = "BI 查询")
@RestController
@RequestMapping("/bi/query")
public class BiQueryController
{
    private final CalciteQueryEngine engine;
    private final BiProperties properties;

    public BiQueryController(CalciteQueryEngine engine, BiProperties properties)
    {
        this.engine = engine;
        this.properties = properties;
    }

    @Operation(summary = "执行查询")
    @PostMapping
    public Map<String, Object> query(@RequestBody QueryRequest request) throws Exception
    {
        if (request.getSql() == null || request.getSql().isBlank())
        {
            throw new IllegalArgumentException("sql 不能为空");
        }
        assertReadOnly(request.getSql());

        int maxRows = request.getMaxRows() > 0 ? request.getMaxRows() : properties.getDefaultMaxRows();
        int timeout = request.getTimeoutSeconds() > 0
                ? request.getTimeoutSeconds() : properties.getQueryTimeoutSeconds();

        QueryResult result = withTimeout(timeout, () -> {
            try (Connection conn = engine.open(resolveDatasources()))
            {
                return engine.query(conn, request.getSql(), maxRows);
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("columns", result.getColumns());
        response.put("rows", result.getRows());
        response.put("rowCount", result.getRows().size());
        response.put("costMs", result.getCostMs());
        response.put("executedSql", result.getExecutedSql());
        return response;
    }

    @Operation(summary = "跨源 JOIN 示例（行政区划 × 学校）")
    @GetMapping("/cross-source-demo")
    public Map<String, Object> crossSourceDemo() throws Exception
    {
        String sql = "SELECT o.province AS 省份, o.city AS 城市, COUNT(s.id) AS 学校数量 "
                + "FROM poca.org_region o "
                + "LEFT JOIN pocb.school s ON s.region_id = o.id "
                + "GROUP BY o.province, o.city "
                + "ORDER BY 学校数量 DESC, o.province, o.city";

        QueryResult result = withTimeout(properties.getQueryTimeoutSeconds(), () -> {
            try (Connection conn = engine.open(resolveDatasources()))
            {
                return engine.query(conn, sql, 100);
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", "两个独立 MySQL 库(poc_a / poc_b)通过 Calcite 做跨源 JOIN");
        response.put("columns", result.getColumns());
        response.put("rows", result.getRows());
        response.put("costMs", result.getCostMs());
        return response;
    }

    /**
     * 只允许只读查询。
     * TODO(S3-T09)：这里仅做关键字粗筛，正式实现需用 Calcite 解析器校验语法树，
     *             确认语句确实是 SELECT（而非 INSERT/UPDATE/DELETE/DDL）。
     */
    private void assertReadOnly(String sql) throws Exception
    {
        var node = org.apache.calcite.sql.parser.SqlParser.create(sql)
                .parseStmt();
        if (!(node instanceof org.apache.calcite.sql.SqlSelect))
        {
            throw new IllegalArgumentException("BI 查询接口当前只允许 SELECT 语句");
        }
    }

    private <T> T withTimeout(int seconds, java.util.concurrent.Callable<T> task) throws Exception
    {
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "bi-query");
            t.setDaemon(true);
            return t;
        });
        try
        {
            Future<T> f = es.submit(task);
            return f.get(seconds, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            throw new IllegalStateException("查询超时(" + seconds + "s)，已中断");
        }
        finally
        {
            es.shutdownNow();
        }
    }

    private List<BiDatasource> resolveDatasources()
    {
        List<BiDatasource> list = new ArrayList<>();
        for (BiProperties.Ds c : properties.getDatasources())
        {
            BiDatasource ds = new BiDatasource();
            ds.setName(c.getName());
            ds.setType(c.getType());
            ds.setSchemaName(c.getSchemaName());
            ds.setJdbcUrl(c.getUrl());
            ds.setDriverClass(c.getDriver());
            ds.setUsername(c.getUsername());
            ds.setPassword(c.getPassword());
            list.add(ds);
        }
        return list;
    }

    /** 查询请求体 */
    public static class QueryRequest
    {
        private String sql;
        private int maxRows;
        private int timeoutSeconds;

        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }

        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int t) { this.timeoutSeconds = t; }
    }
}

