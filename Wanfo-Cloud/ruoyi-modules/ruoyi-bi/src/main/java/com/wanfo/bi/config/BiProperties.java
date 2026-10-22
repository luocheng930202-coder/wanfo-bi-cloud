package com.wanfo.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BI 模块配置。
 *
 * <p>过渡期配置来源：启动参数 / 本地 application 配置。
 * 接入 {@code bi_datasource} 元数据表后（S2-T02），改由 DataSourceProvider 读取，
 * 本类仅保留引擎级参数。
 *
 * @author zhengliang
 */
@Component
@ConfigurationProperties(prefix = "bi")
public class BiProperties
{
    /** 引擎标识 */
    private String engine = "calcite";

    /** 引擎版本（文档展示用） */
    private String engineVersion = "1.42.0";

    /** 查询默认最大返回行数 */
    private int defaultMaxRows = 5000;

    /** 查询超时（秒） */
    private int queryTimeoutSeconds = 30;

    /** 过渡期数据源列表 */
    private List<Ds> datasources = new ArrayList<>();

    /** 在线模板市场（只读拉取，可选远程目录 URL） */
    private TemplateMarket templateMarket = new TemplateMarket();

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }

    public int getDefaultMaxRows() { return defaultMaxRows; }
    public void setDefaultMaxRows(int defaultMaxRows) { this.defaultMaxRows = defaultMaxRows; }

    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int s) { this.queryTimeoutSeconds = Math.min(120,Math.max(1,s)); }

    public List<Ds> getDatasources() { return datasources; }
    public void setDatasources(List<Ds> datasources) { this.datasources = datasources; }

    public TemplateMarket getTemplateMarket() { return templateMarket; }
    public void setTemplateMarket(TemplateMarket templateMarket) { this.templateMarket = templateMarket; }

    /** 模板市场目录与拉取超时（秒） */
    public static class TemplateMarket
    {
        /** 为空时仅使用内置 classpath 目录；须为 https（或本地调试 http）JSON 索引 */
        private String catalogUrl = "";
        private int fetchTimeoutSeconds = 15;
        /** Additional origins explicitly trusted by the operator; catalog origin is allowed automatically. */
        private List<String> allowedOrigins = new java.util.ArrayList<>();
        public List<String> getAllowedOrigins(){return allowedOrigins;}
        public void setAllowedOrigins(List<String> value){allowedOrigins=value==null?List.of():List.copyOf(value);}

        public String getCatalogUrl() { return catalogUrl; }
        public void setCatalogUrl(String catalogUrl) { this.catalogUrl = catalogUrl == null ? "" : catalogUrl.trim(); }

        public int getFetchTimeoutSeconds() { return fetchTimeoutSeconds; }
        public void setFetchTimeoutSeconds(int fetchTimeoutSeconds) { this.fetchTimeoutSeconds = Math.min(60,Math.max(3, fetchTimeoutSeconds)); }
    }

    /**
     * 过渡期数据源配置项，字段与 {@code BiDatasource} 对齐。
     */
    public static class Ds
    {
        private String name;
        private String type;
        private String schemaName;
        private String url;
        private String driver;
        private String username;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getDriver() { return driver; }
        public void setDriver(String driver) { this.driver = driver; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}

