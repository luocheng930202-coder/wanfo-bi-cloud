package com.wanfo.bi.query;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calcite 查询引擎。
 *
 * <p><b>Calcite 1.42 关键约定（均为本项目实测踩坑后固化，勿轻易改动）：</b>
 * <ol>
 *   <li>不能使用 model.json + JdbcSchemaFactory —— 1.42 已移除该类，
 *       替代的 JsonJdbcSchema 是包内类，无法作为 SchemaFactory 被反射实例化</li>
 *   <li>{@link JdbcSchema#create} 只构造实例，<b>必须显式 {@code root.add(name, schema)}</b>，
 *       否则解析器查不到 schema，表现为 {@code Object 'xxx' not found}</li>
 *   <li>必须设置 {@code lex=MYSQL_ANSI} 与 {@code caseSensitive=false}，
 *       否则 Calcite 默认把标识符转大写且大小写敏感，与 MySQL / Doris 实际行为不一致</li>
 *   <li>不要用 {@code JdbcSchema.dataSource()}，其参数顺序与直觉不符，
 *       表现为 {@code Cannot load JDBC driver class 'root'}</li>
 *   <li>commons-dbcp2 在 calcite-core 中是 runtime scope，编译期不可见，需在 pom 显式声明</li>
 * </ol>
 *
 * @author zhengliang
 */
@Component
public class CalciteQueryEngine
{
    private static final Logger log = LoggerFactory.getLogger(CalciteQueryEngine.class);

    /**
     * schemaName -> 连接池。
     * BI 的数据源数量可控（一般 < 50），且连接池本身开销低，
     * 因此按 schema 缓存池，避免每次查询重建连接。
     */
    private final Map<String, BasicDataSource> pools = new ConcurrentHashMap<>();

    /**
     * 创建一个 Calcite 连接并注册全部数据源 schema。
     *
     * @param datasources 需要联邦查询的数据源列表
     * @return Calcite 连接，调用方负责关闭
     */
    public Connection open(List<BiDatasource> datasources) throws Exception
    {
        Properties props = new Properties();
        // 关键：与 MySQL / Doris 的标识符行为对齐
        props.setProperty("lex", "MYSQL_ANSI");
        props.setProperty("caseSensitive", "false");
        props.setProperty("fun", "standard,mysql");

        Connection conn = DriverManager.getConnection("jdbc:calcite:", props);
        CalciteConnection calciteConn = conn.unwrap(CalciteConnection.class);
        SchemaPlus root = calciteConn.getRootSchema();

        for (BiDatasource ds : datasources)
        {
            register(root, ds);
        }
        return conn;
    }

    /**
     * 注册单个数据源为 Calcite schema。
     *
     * @param root 根 schema
     * @param ds   数据源定义
     */
    private void register(SchemaPlus root, BiDatasource ds) throws Exception
    {
        if (ds.getSchemaName() == null || ds.getSchemaName().isBlank())
        {
            throw new IllegalArgumentException("数据源 schemaName 不能为空: " + ds.getName());
        }
        if (ds.getDriverClass() != null && !ds.getDriverClass().isBlank())
        {
            Class.forName(ds.getDriverClass());
        }

        // 连接池按 schema 缓存复用，避免每次查询都重建连接。
        // TODO(S3-T02)：若同一 schema 的连接参数发生变更，需要比对配置并重建池；
        //              当前实现要求变更配置后重启服务。
        BasicDataSource pool = pools.get(ds.getSchemaName());
        if (pool == null)
        {
            pool = buildPool(ds);
            pools.put(ds.getSchemaName(), pool);
        }

        JdbcSchema schema = JdbcSchema.create(root, ds.getSchemaName(), pool, metadata -> {
            var dialect=org.apache.calcite.sql.SqlDialectFactoryImpl.INSTANCE.create(metadata);
            return dialect instanceof org.apache.calcite.sql.dialect.MysqlSqlDialect
                    ? new CalendarMysqlDialect(org.apache.calcite.sql.SqlDialects.createContext(metadata)) : dialect;
        }, null, null);
        // 关键：1.42 的 create() 不会自动挂载，必须显式 add
        root.add(ds.getSchemaName(), schema);

        log.info("Calcite schema 注册: {} ({}) <- {}", ds.getSchemaName(), ds.getType(), ds.getName());
    }

    private BasicDataSource buildPool(BiDatasource ds)
    {
        BasicDataSource pool = new BasicDataSource();
        pool.setDriverClassName(ds.getDriverClass());
        pool.setUrl(ds.getJdbcUrl());
        pool.setUsername(ds.getUsername());
        pool.setPassword(ds.getPassword());
        pool.setInitialSize(1);
        pool.setMaxTotal(8);
        pool.setMinIdle(1);
        // 连接保活，避免长时间空闲被源库断开
        pool.setTimeBetweenEvictionRunsMillis(60_000);
        pool.setMinEvictableIdleTimeMillis(300_000);
        pool.setValidationQuery("SELECT 1");
        pool.setTestWhileIdle(true);
        return pool;
    }

    /**
     * 执行查询。
     *
     * @param conn    Calcite 连接
     * @param sql     待执行 SQL（应已注入行级权限条件）
     * @param maxRows 最大返回行数，防止前端被超大结果集拖垮
     * @return 查询结果
     */
    public QueryResult query(Connection conn, String sql, int maxRows) throws Exception
    {
        QueryResult result = new QueryResult();
        result.setExecutedSql(sql);

        long start = System.currentTimeMillis();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql))
        {
            ResultSetMetaData rsmd = rs.getMetaData();
            int colCount = rsmd.getColumnCount();

            List<String> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++)
            {
                columns.add(rsmd.getColumnLabel(i));
            }
            result.setColumns(columns);

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < maxRows)
            {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++)
                {
                    row.put(rsmd.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            result.setRows(rows);
        }
        result.setCostMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 参数化执行查询。
     *
     * <p>字面量走 JDBC 参数绑定，编码由驱动按连接字符集处理，
     * 可绕开 Calcite 方言硬编码的 ISO-8859-1 默认字符集。
     *
     * @param conn    Calcite 连接
     * @param sql     含 ? 占位符的 SQL
     * @param params  参数值，顺序与 ? 一一对应
     * @param maxRows 最大返回行数
     */
    public QueryResult queryWithParams(Connection conn, String sql,
                                       java.util.List<Object> params, int maxRows) throws Exception
    {
        QueryResult result = new QueryResult();
        result.setExecutedSql(sql);

        long start = System.currentTimeMillis();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql))
        {
            if (params != null)
            {
                for (int i = 0; i < params.size(); i++)
                {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (java.sql.ResultSet rs = ps.executeQuery())
            {
                java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                List<String> columns = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++)
                {
                    columns.add(rsmd.getColumnLabel(i));
                }
                result.setColumns(columns);

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() < maxRows)
                {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++)
                    {
                        row.put(rsmd.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                result.setRows(rows);
            }
        }
        result.setCostMs(System.currentTimeMillis() - start);
        return result;
    }

    /** 引擎自检信息，供 /bi/health 使用 */
    public Map<String, Object> engineInfo()
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("engine", "Apache Calcite");
        info.put("registeredSchemaCount", pools.size());
        info.put("lex", "MYSQL_ANSI");
        info.put("caseSensitive", false);
        info.put("jdbcDialect", "MySQL (默认，可按数据源类型切换)");
        return info;
    }

    /** 连接池健康探测：逐个数据源执行 SELECT 1 */
    public Map<String, Boolean> probeAll(List<BiDatasource> datasources)
    {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (BiDatasource ds : datasources)
        {
            boolean ok = false;
            BasicDataSource pool = pools.get(ds.getSchemaName());
            try
            {
                if (pool != null)
                {
                    try (Connection c = pool.getConnection();
                         Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT 1"))
                    {
                        ok = rs.next();
                    }
                }
                else
                {
                    // 连接池是首次 open() 时才建立的；单独调用探测接口时池可能还不存在，
                    // 此时直接用 JDBC 探测，保证探测接口可独立使用
                    try (Connection c = DriverManager.getConnection(
                                ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
                         Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT 1"))
                    {
                        ok = rs.next();
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("数据源连通性探测失败 {}: {}", ds.getName(), e.getMessage());
            }
            result.put(ds.getName(), ok);
        }
        return result;
    }

    /** 关闭全部连接池（应用停止时调用） */
    public void shutdown()
    {
        // BasicDataSource.close() 抛受检异常 SQLException，不能直接用方法引用当 Consumer
        for (BasicDataSource pool : pools.values())
        {
            try
            {
                pool.close();
            }
            catch (Exception e)
            {
                log.warn("关闭数据源连接池失败: {}", e.getMessage());
            }
        }
        pools.clear();
    }

    /**
     * 借用某个数据源的原生 JDBC 连接。
     *
     * <p>用于元数据采集（读库/表/字段）—— 这类操作要的是 DatabaseMetaData，
     * 绕开 Calcite 反而更直接。连接来自共享池，调用方必须关闭。
     *
     * @param schemaName Calcite schema 别名
     * @return 原生 JDBC 连接（URL 中已含库名）
     */
    public Connection borrowConnection(String schemaName) throws Exception
    {
        BasicDataSource pool = pools.get(schemaName);
        if (pool != null)
        {
            return pool.getConnection();
        }
        // 池尚未建立（引擎未 open 过）时兜底：直接从 DataSourceProvider 取连接参数
        BiDatasource ds = (fallbackProvider == null) ? null : fallbackProvider.getBySchemaName(schemaName);
        if (ds == null)
        {
            throw new IllegalStateException("数据源不存在或未启用: " + schemaName);
        }
        if (ds.getDriverClass() != null && !ds.getDriverClass().isBlank())
        {
            Class.forName(ds.getDriverClass());
        }
        return DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
    }

    /**
     * 注入数据源提供者，供 borrowConnection 在池未建立时兜底。
     */
    public void setFallbackProvider(DataSourceProvider provider)
    {
        this.fallbackProvider = provider;
    }

    private DataSourceProvider fallbackProvider;

    /**
     * 使指定 schema 的连接池失效。
     *
     * <p>数据源配置变更后必须调用，否则连接池会继续使用旧参数，
     * 表现为「改了配置但没生效」。下次 open() 会按新配置重建。
     */
    public void invalidate(String schemaName)
    {
        BasicDataSource pool = pools.remove(schemaName);
        if (pool != null)
        {
            try
            {
                pool.close();
            }
            catch (Exception e)
            {
                log.warn("关闭失效连接池异常 {}: {}", schemaName, e.getMessage());
            }
            log.info("连接池已失效并关闭: {}", schemaName);
        }
    }

    /** 供日志与健康检查展示引擎版本 */
    public static String engineVersion(Connection conn)
    {
        try
        {
            DatabaseMetaData md = conn.getMetaData();
            return md.getDatabaseProductName() + " " + md.getDatabaseProductVersion();
        }
        catch (Exception e)
        {
            return "unknown";
        }
    }
}

