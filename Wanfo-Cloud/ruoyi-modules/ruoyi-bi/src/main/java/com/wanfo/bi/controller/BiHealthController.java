package com.wanfo.bi.controller;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.query.CalciteQueryEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BI 平台健康检查与自检接口。
 *
 * <p>这是 S1 阶段的验收接口：服务启动后能返回引擎版本、已注册 schema、数据源连通性，
 * 用来确认「元数据库 + Calcite + 数据源」三层链路是否贯通。
 *
 * @author zhengliang
 */
@Tag(name = "BI 健康检查")
@RestController
@RequestMapping("/bi/health")
public class BiHealthController
{
    private final CalciteQueryEngine engine;
    private final BiProperties properties;

    public BiHealthController(CalciteQueryEngine engine, BiProperties properties)
    {
        this.engine = engine;
        this.properties = properties;
    }

    @Operation(summary = "健康检查")
    @GetMapping
    public Map<String, Object> health() throws Exception
    {
        List<BiDatasource> datasources = resolveDatasources();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("module", "ruoyi-modules-bi");
        result.put("configuredEngine", properties.getEngine() + " " + properties.getEngineVersion());

        // 建立一次 Calcite 连接，取引擎实际版本并顺带注册 schema
        try (Connection conn = engine.open(datasources))
        {
            result.put("engineRuntime", CalciteQueryEngine.engineVersion(conn));
            result.put("engineDetail", engine.engineInfo());
        }
        catch (Exception e)
        {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }

        result.put("datasourceCount", datasources.size());
        result.put("datasources", engine.probeAll(datasources));

        return result;
    }

    @Operation(summary = "列出已配置的数据源")
    @GetMapping("/datasources")
    public List<Map<String, Object>> datasources()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BiDatasource ds : resolveDatasources())
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", ds.getName());
            m.put("type", ds.getType());
            m.put("schemaName", ds.getSchemaName());
            m.put("jdbcUrl", ds.getJdbcUrl());
            list.add(m);
        }
        return list;
    }

    /**
     * 配置项 -> 数据源领域对象。
     * 接入 bi_datasource 表后，此方法替换为 DataSourceProvider.list()。
     */
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
}

