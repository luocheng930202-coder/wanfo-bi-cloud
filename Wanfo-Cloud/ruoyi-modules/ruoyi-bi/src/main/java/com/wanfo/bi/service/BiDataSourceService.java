package com.wanfo.bi.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.DbDataSourceProvider;
import com.wanfo.bi.query.CalciteQueryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源管理服务（S3-T02）。
 *
 * <p>职责：密码加解密、schema 别名唯一性校验、驱动推导、写库后通知查询引擎重建连接池。
 *
 * @author zhengliang
 */
@Service
public class BiDataSourceService
{
    private static final Logger log = LoggerFactory.getLogger(BiDataSourceService.class);

    private final DbDataSourceProvider provider;
    private final CredentialCipher cipher;
    private final CalciteQueryEngine engine;
    private final BiProperties properties;
    private final BiResourceAuthorizer resources;

    public BiDataSourceService(DbDataSourceProvider provider,
                               CredentialCipher cipher,
                               CalciteQueryEngine engine,
                               BiProperties properties, BiResourceAuthorizer resources)
    {
        this.provider = provider;
        this.cipher = cipher;
        this.engine = engine;
        this.properties = properties;
        this.resources = resources;
    }

    // ------------------------------------------------------------------ 查询

    /** 管理列表（密码脱敏） */
    public List<Map<String, Object>> listForManage()
    {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (BiDatasourceDO row : provider.listAll())
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.getId());
            m.put("name", row.getName());
            m.put("type", row.getType());
            m.put("schemaName", row.getSchemaName());
            m.put("jdbcUrl", row.getJdbcUrl());
            m.put("driverClass", row.getDriverClass());
            m.put("username", row.getUsername());
            // ★ 列表接口绝不返回密码原文，也不返回密文
            m.put("passwordSet", row.getPassword() != null && !row.getPassword().isEmpty());
            m.put("status", row.getStatus());
            m.put("remark", row.getRemark());
            m.put("createTime", row.getCreateTime());
            m.put("poolMaxTotal", row.getPoolMaxTotal());
            m.put("queryTimeout", row.getQueryTimeout());
            result.add(m);
        }
        return result;
    }

    public BiDatasourceDO get(Long id)
    {
        return provider.getRow(id);
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public Map<String, Object> create(BiDatasourceDO form, String operator)
    {
        if (form.getId() != null) { throw new IllegalArgumentException("新增数据源不能指定已有 ID"); }
        Map<String, Object> result = new LinkedHashMap<>();
        validate(form, true);

        Map<String, Object> probe = testFormConnection(form);
        result.put("reachable", probe.get("ok"));
        result.put("probe", probe);
        if (!Boolean.TRUE.equals(probe.get("ok")))
        {
            result.put("rolledBack", true);
            return result;
        }
        form.setDriverClass(resolveDriver(form.getType(), form.getDriverClass()));
        form.setPassword(cipher.encrypt(form.getPassword()));
        form.setStatus(form.getStatus() == null ? "0" : form.getStatus());
        form.setCreateBy(operator);
        form.setUpdateBy(operator);
        form.setDelFlag("0");
        if (form.getPoolMaxTotal() == null) { form.setPoolMaxTotal(8); }
        if (form.getPoolMinIdle() == null) { form.setPoolMinIdle(1); }
        if (form.getQueryTimeout() == null) { form.setQueryTimeout(60); }
        provider.insert(form);
        resources.register("datasource", form.getId());
        result.put("id", form.getId());
        engine.invalidate(form.getSchemaName());
        return result;
    }

    @Transactional
    public Map<String, Object> update(BiDatasourceDO form, String operator)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (form.getId() == null)
        {
            throw new IllegalArgumentException("id 不能为空");
        }
        BiDatasourceDO exists = provider.getRow(form.getId());
        if (exists == null)
        {
            throw new IllegalArgumentException("数据源不存在: " + form.getId());
        }

        Map<String, Object> probe = testFormConnection(form);
        result.put("probe", probe);
        if (!Boolean.TRUE.equals(probe.get("ok")))
        {
            result.put("rolledBack", true);
            return result;
        }
        // schema 别名不可改：它已被数据集、图表定义引用
        form.setSchemaName(null);
        form.setType(null);
        validate(form, false);

        if (form.getDriverClass() != null)
        {
            form.setDriverClass(resolveDriver(form.getType() != null ? form.getType() : exists.getType(),
                                               form.getDriverClass()));
        }
        // 密码为空表示"不修改"，保留原值
        if (form.getPassword() == null || form.getPassword().isEmpty())
        {
            form.setPassword(null);
        }
        else
        {
            form.setPassword(cipher.encrypt(form.getPassword()));
        }
        form.setUpdateBy(operator);

        int rows = provider.update(form);
        result.put("updated", rows);

        // 连接参数变了就重建池
        if (form.getJdbcUrl() != null || form.getPassword() != null
                || form.getUsername() != null || form.getDriverClass() != null)
        {
            engine.invalidate(exists.getSchemaName());
            result.put("poolInvalidated", exists.getSchemaName());
        }
        return result;
    }

    public Map<String, Object> changeStatus(Long id, String status, String operator)
    {
        if (!"0".equals(status) && !"1".equals(status))
        {
            throw new IllegalArgumentException("status 只能是 0 或 1");
        }
        BiDatasourceDO row = provider.getRow(id);
        if (row == null)
        {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        int rows = provider.updateStatus(id, status, operator);
        engine.invalidate(row.getSchemaName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", rows);
        result.put("status", status);
        result.put("schemaName", row.getSchemaName());
        return result;
    }

    public Map<String, Object> delete(Long id, String operator)
    {
        BiDatasourceDO row = provider.getRow(id);
        if (row == null)
        {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        int rows = provider.delete(id, operator);
        engine.invalidate(row.getSchemaName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", rows);
        result.put("schemaName", row.getSchemaName());
        result.put("tip", "已逻辑删除；已引用该数据源的数据集/图表需人工确认");
        return result;
    }

    // ------------------------------------------------------------------ 探测

    /** Tests unsaved form values without inserting or updating any metadata. */
    public Map<String, Object> testFormConnection(BiDatasourceDO form)
    {
        BiDatasourceDO target = new BiDatasourceDO();
        BiDatasourceDO saved = form.getId() == null ? null : provider.getRow(form.getId());
        if (form.getId() != null && saved == null) { throw new IllegalArgumentException("数据源不存在"); }
        target.setType(saved == null ? form.getType() : saved.getType());
        target.setJdbcUrl(form.getJdbcUrl());
        target.setUsername(form.getUsername());
        target.setDriverClass(resolveDriver(target.getType(), form.getDriverClass()));
        if (target.getJdbcUrl() == null || !target.getJdbcUrl().startsWith("jdbc:")
                || target.getUsername() == null || target.getUsername().isBlank())
        {
            throw new IllegalArgumentException("请填写连接串和账号");
        }
        if (saved != null && (form.getPassword() == null || form.getPassword().isEmpty()))
        {
            if (!java.util.Objects.equals(saved.getJdbcUrl(), target.getJdbcUrl())
                    || !java.util.Objects.equals(saved.getUsername(), target.getUsername())
                    || !java.util.Objects.equals(resolveDriver(saved.getType(), saved.getDriverClass()), target.getDriverClass()))
            {
                throw new IllegalArgumentException("更换连接地址、账号或驱动后，请重新填写密码");
            }
            target.setPassword(cipher.decrypt(saved.getPassword()));
        }
        else
        {
            target.setPassword(form.getPassword());
        }
        return testPlainConnection(target);
    }

    /** 直接用 JDBC 试连（不经过连接池） */
    public Map<String, Object> testConnection(BiDatasourceDO ds)
    {
        BiDatasourceDO target = new BiDatasourceDO();
        target.setJdbcUrl(ds.getJdbcUrl());
        target.setUsername(ds.getUsername());
        target.setDriverClass(resolveDriver(ds.getType(), ds.getDriverClass()));
        target.setPassword(cipher.decrypt(ds.getPassword()));
        return testPlainConnection(target);
    }

    private Map<String, Object> testPlainConnection(BiDatasourceDO ds)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        String url = ds.getJdbcUrl();
        String user = ds.getUsername();
        String pwd = ds.getPassword();
        // Bound MySQL/Doris connection and socket waits, including URL-specified overrides.
        if (url != null && url.startsWith("jdbc:mysql:"))
        {
            url = url.replaceAll("(?i)([?&])(connectTimeout|socketTimeout)=[^&]*", "$1");
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=5000&socketTimeout=5000";
        }
        try
        {
            if (ds.getDriverClass() != null && !ds.getDriverClass().isBlank())
            {
                Class.forName(ds.getDriverClass());
            }
            try (Connection conn = DriverManager.getConnection(url, user, pwd);
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1"))
            {
                result.put("ok", rs.next());
                result.put("databaseProduct",
                        conn.getMetaData().getDatabaseProductName()
                                + " " + conn.getMetaData().getDatabaseProductVersion());
            }
        }
        catch (Exception e)
        {
            result.put("ok", false);
            result.put("message", e instanceof java.sql.SQLException sql && "28000".equals(sql.getSQLState())
                    ? "账号或密码错误，或该账号没有连接权限"
                    : e instanceof ClassNotFoundException ? "未安装所选数据库驱动"
                    : "连接失败，请检查连接地址、端口、数据库名称和访问权限");
        }
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    /** 按 schema 名试连（管理页面用） */
    public Map<String, Object> testBySchema(String schemaName)
    {
        BiDatasourceDO row = provider.listAll().stream()
                .filter(d -> schemaName.equals(d.getSchemaName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + schemaName));
        Map<String, Object> r = testConnection(row);
        r.put("schemaName", schemaName);
        return r;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 把配置项 bi.datasources 同步到数据库（upsert 语义）。
     *
     * <p>供"配置驱动"的部署方式使用：以配置文件为准，覆盖同 schemaName 的记录。
     * 界面维护的数据源请用 create/update 接口，不要调用本方法。
     */
    @Transactional
    public Map<String, Object> syncFromConfig(String operator)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        int created = 0;
        int updated = 0;

        for (BiProperties.Ds c : properties.getDatasources())
        {
            BiDatasourceDO row = new BiDatasourceDO();
            row.setName(c.getName());
            row.setType(c.getType());
            row.setSchemaName(c.getSchemaName());
            row.setJdbcUrl(c.getUrl());
            row.setDriverClass(resolveDriver(c.getType(), c.getDriver()));
            row.setUsername(c.getUsername());
            row.setPassword(cipher.encrypt(c.getPassword()));
            row.setPoolMaxTotal(8);
            row.setPoolMinIdle(1);
            row.setQueryTimeout(60);
            row.setStatus("0");
            row.setRemark("配置同步");
            row.setUpdateBy(operator);

            BiDatasourceDO exists = provider.listAll().stream()
                    .filter(d -> c.getSchemaName().equals(d.getSchemaName()))
                    .findFirst()
                    .orElse(null);
            if (exists == null)
            {
                row.setCreateBy(operator);
                row.setDelFlag("0");
                provider.insert(row);
                created++;
            }
            else
            {
                row.setId(exists.getId());
                provider.update(row);
                updated++;
            }
            engine.invalidate(c.getSchemaName());
        }

        result.put("created", created);
        result.put("updated", updated);
        result.put("total", provider.listAll().size());
        return result;
    }

    private void validate(BiDatasourceDO form, boolean creating)
    {
        if (form.getName() == null || form.getName().isBlank())
        {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (creating)
        {
            if (form.getSchemaName() == null || form.getSchemaName().isBlank())
            {
                throw new IllegalArgumentException("schemaName 不能为空");
            }
            if (!form.getSchemaName().matches("[a-z][a-z0-9_]{0,30}"))
            {
                throw new IllegalArgumentException("schemaName 只能是小写字母开头的字母数字下划线组合，且不超过 31 字符");
            }
            if (provider.countBySchemaName(form.getSchemaName()) > 0)
            {
                throw new IllegalArgumentException("schemaName 已被占用: " + form.getSchemaName());
            }
        }
        if (form.getJdbcUrl() == null || !form.getJdbcUrl().startsWith("jdbc:"))
        {
            throw new IllegalArgumentException("jdbcUrl 必须以 jdbc: 开头");
        }
        if (creating && (form.getUsername() == null || form.getUsername().isBlank()))
        {
            throw new IllegalArgumentException("username 不能为空");
        }
    }

    /** Doris / StarRocks 走 MySQL 协议，复用 MySQL 驱动 */
    public static String resolveDriver(String type, String configured)
    {
        if (configured != null && !configured.isBlank())
        {
            return configured;
        }
        if (type == null)
        {
            return "com.mysql.cj.jdbc.Driver";
        }
        return switch (type.toLowerCase())
        {
            case "mysql", "mariadb", "doris", "starrocks", "tidb" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql", "pg", "greenplum", "gaussdb" -> "org.postgresql.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            case "sqlserver", "mssql" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "clickhouse", "ck" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "db2" -> "com.ibm.db2.jcc.DB2Driver";
            case "h2" -> "org.h2.Driver";
            case "impala" -> "com.cloudera.impala.jdbc.Driver";
            case "kingbase" -> "com.kingbase8.Driver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }
}

