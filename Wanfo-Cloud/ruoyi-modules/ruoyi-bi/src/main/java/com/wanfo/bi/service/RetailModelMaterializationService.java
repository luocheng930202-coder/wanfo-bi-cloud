package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.datasource.JdbcConnectionSettings;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;
import com.wanfo.bi.mapper.WarehouseLoadMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Fixed, versioned retail DWS materialization. No client SQL or table names are accepted. */
@Service
public class RetailModelMaterializationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RetailModelMaterializationService.class);
    private static final String ODS_DATABASE = "wanfo_wh_ods";
    private static final String DWD_DATABASE = "wanfo_wh_dwd";
    private static final String DWS_DATABASE = "wanfo_wh_dws";
    private static final String FACT = "fact_retail_order_line";
    private static final String ORG = "dim_retail_org";
    private static final String PRODUCT = "dim_retail_product";
    private static final String TARGET = "agg_retail_sales_monthly";

    private static final String DIMENSION_MATCH_SQL = """
            SELECT count() FROM (
              SELECT f.tenant_id,f.order_id,f.line_no,
                     countIf(o.org_id != '') AS org_matches,
                     countIf(p.product_id != '') AS product_matches
              FROM wanfo_wh_dwd.fact_retail_order_line f
              LEFT JOIN wanfo_wh_dwd.dim_retail_org o
                ON o.tenant_id=f.tenant_id AND o.org_id=f.org_id
               AND o.valid_from <= coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
               AND (o.valid_to IS NULL OR o.valid_to > coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
              LEFT JOIN wanfo_wh_dwd.dim_retail_product p
                ON p.tenant_id=f.tenant_id AND p.product_id=f.product_id
               AND p.valid_from <= coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
               AND (p.valid_to IS NULL OR p.valid_to > coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
              WHERE f.is_deleted=0
              GROUP BY f.tenant_id,f.order_id,f.line_no
              HAVING org_matches != 1 OR product_matches != 1
            )
            """;

    private final WarehouseLoadMapper mapper;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final Executor executor;
    private final DataSource platformDataSource;

    public RetailModelMaterializationService(WarehouseLoadMapper mapper, DataSourceProvider sources,
            BiResourceAuthorizer resources, CapabilityProvider capabilities, CurrentActorProvider actors,
            LiveActorResolver live, @Qualifier("biWarehouseExecutor") Executor executor, DataSource platformDataSource)
    {
        this.mapper = mapper;
        this.sources = sources;
        this.resources = resources;
        this.capabilities = capabilities;
        this.actors = actors;
        this.live = live;
        this.executor = executor;
        this.platformDataSource = platformDataSource;
    }

    public Map<String, Object> trigger(Long targetDatasourceId, String traceId)
    {
        capabilities.require("bi:resource:admin");
        if (targetDatasourceId == null || targetDatasourceId < 1) throw new IllegalArgumentException("请指定 ClickHouse ODS 数据源");
        BiDatasource target = requireTarget(targetDatasourceId);
        resources.require("datasource", targetDatasourceId, "model");
        BiActor actor = actors.currentActor();
        Map<String, Object> run = new HashMap<>();
        run.put("sourceId", targetDatasourceId);
        run.put("targetId", targetDatasourceId);
        run.put("sourceTable", "retail-v1 DWD → DWS");
        run.put("targetDatabase", DWS_DATABASE);
        run.put("targetTable", TARGET);
        run.put("traceId", traceId == null ? "" : traceId.substring(0, Math.min(64, traceId.length())));
        run.put("actor", actor.username());
        run.put("loadMode", "MODEL");
        if (mapper.insertRun(run) != 1 || !(run.get("id") instanceof Number id)) throw new IllegalStateException("模型物化运行记录创建失败");
        long runId = id.longValue();
        try
        {
            executor.execute(() -> execute(runId, target, actor));
        }
        catch (RuntimeException rejected)
        {
            mapper.fail(runId, "模型物化队列繁忙，请稍后重试");
        }
        return mapper.run(runId);
    }

    public Map<String, Object> latestReconciliation(Long targetDatasourceId)
    {
        capabilities.require("bi:resource:admin");
        BiDatasource target = requireTarget(targetDatasourceId);
        resources.require("datasource", target.getId(), "model");
        return mapper.latestReconciliation(targetDatasourceId);
    }

    private void execute(long runId, BiDatasource target, BiActor actor)
    {
        try
        {
            var current = live.resolve(actor);
            current.require("bi:resource:admin");
            String lockName = targetLockName(DWS_DATABASE, TARGET);
            try (Connection lockConnection = platformDataSource.getConnection())
            {
                if (!acquireLock(lockConnection, lockName))
                {
                    mapper.fail(runId, "同一数仓模型已有刷新任务运行，请稍后重试");
                    return;
                }
                try
                {
                    ActorScope.run(current.actor(), current.capabilities(), () -> {
                        try { materialize(runId, target); }
                        catch (Exception failure) { throw new MaterializationFailure(failure); }
                    });
                }
                finally { releaseLock(lockConnection, lockName); }
            }
        }
        catch (Exception failure)
        {
            LOGGER.warn("Retail model materialization failed; previous DWS target remains published (run {})", runId);
            mapper.fail(runId, "零售月度模型刷新失败；旧版本保持可用，请检查模型表、来源数据和目标授权");
        }
    }

    private void materialize(long runId, BiDatasource definition) throws Exception
    {
        if (mapper.start(runId) != 1) return;
        resources.require("datasource", definition.getId(), "model");
        try (Connection target = JdbcConnectionSettings.open(definition))
        {
            requireAtomicDatabase(target, DWS_DATABASE);
            String targetName = quote(DWS_DATABASE) + "." + quote(TARGET);
            if (!exists(target, targetName)) throw new IllegalArgumentException("请先按 retail-v1 模型 DDL 创建 DWS 汇总目标表");
            if (invalidFacts(target) != 0) throw new IllegalArgumentException("DWD 明细包含空业务键、空租户/组织或无效删除标志，模型发布已阻止");
            if (duplicateFactKeys(target) != 0) throw new IllegalArgumentException("DWD 订单明细业务键重复，模型发布已阻止");
            if (unmatchedDimensionRows(target) != 0) throw new IllegalArgumentException("存在未匹配或多重匹配的组织/商品历史维度，模型发布已阻止");

            String stageName = "__dp_model_stage_" + runId;
            String stage = quote(DWS_DATABASE) + "." + quote(stageName);
            boolean published = false;
            try
            {
                try (Statement statement = target.createStatement())
                {
                    statement.setQueryTimeout(600);
                    statement.execute("DROP TABLE IF EXISTS " + stage);
                    statement.execute("CREATE TABLE " + stage + " AS " + targetName);
                    statement.execute(modelInsert(stage, runId));
                }
                long staged = count(target, stage);
                if (duplicateKeys(target, stage) != 0) throw new IllegalStateException("DWS 汇总业务键重复，未发布");
                if (staged > countValidFacts(target)) throw new IllegalStateException("汇总结果行数异常，未发布");
                Map<String, Object> reconciliation = reconciliationResult(target, stage, runId, definition.getId());
                if (mapper.saveReconciliation(reconciliation) != 1) throw new IllegalStateException("模型对账记录保存失败，未发布");
                try (Statement statement = target.createStatement())
                {
                    statement.execute("EXCHANGE TABLES " + targetName + " AND " + stage);
                    published = true;
                    try { statement.execute("DROP TABLE IF EXISTS " + stage); }
                    catch (SQLException ignored) { /* Published table is live; startup recovery owns the old stage. */ }
                }
                if (mapper.markReconciliationPublished(runId, "DWS 汇总已原子发布；源/目标销售额和净数量全量核对一致") != 1)
                    throw new IllegalStateException("模型已发布，但对账状态未能更新；请检查运行和对账记录");
                if (mapper.succeed(runId, staged, true) != 1) throw new IllegalStateException("模型运行状态更新失败");
            }
            finally
            {
                if (!published)
                {
                    try (Statement statement = target.createStatement()) { statement.execute("DROP TABLE IF EXISTS " + stage); }
                    catch (SQLException ignored) { /* Startup recovery removes the run-owned model stage. */ }
                }
            }
        }
    }

    private static String modelInsert(String stage, long runId)
    {
        return """
                INSERT INTO %s
                (tenant_id,month_start,org_id,province_code,city_code,county_code,channel,category_id,
                 sales_amount,net_quantity,order_count,source_max_updated_at,generated_at,load_run_id)
                SELECT f.tenant_id,toDate(toStartOfMonth(f.order_date)),f.org_id,o.province_code,o.city_code,o.county_code,
                       f.channel,p.category_id,CAST(sum(f.sales_amount),'Decimal(20,2)'),CAST(sum(f.quantity),'Decimal(20,3)'),
                       uniqExact(f.order_id),max(f.source_updated_at),now64(3,'UTC'),%d
                FROM wanfo_wh_dwd.fact_retail_order_line f
                INNER JOIN wanfo_wh_dwd.dim_retail_org o
                  ON o.tenant_id=f.tenant_id AND o.org_id=f.org_id
                 AND o.valid_from <= coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
                 AND (o.valid_to IS NULL OR o.valid_to > coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
                INNER JOIN wanfo_wh_dwd.dim_retail_product p
                  ON p.tenant_id=f.tenant_id AND p.product_id=f.product_id
                 AND p.valid_from <= coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
                 AND (p.valid_to IS NULL OR p.valid_to > coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
                WHERE f.is_deleted=0
                GROUP BY f.tenant_id,toDate(toStartOfMonth(f.order_date)),f.org_id,o.province_code,o.city_code,
                         o.county_code,f.channel,p.category_id
                """.formatted(stage, runId);
    }

    private BiDatasource requireTarget(Long id)
    {
        BiDatasource target = sources.getById(id);
        if (target == null || !"0".equals(target.getStatus()) || !List.of("clickhouse", "ck").contains(target.getType().toLowerCase(Locale.ROOT))
                || !ODS_DATABASE.equals(target.getSourceSchema()))
            throw new IllegalArgumentException("目标必须是启用状态且 schema 配置为 wanfo_wh_ods 的 ClickHouse 数据源");
        return target;
    }

    private static long unmatchedDimensionRows(Connection target) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery(DIMENSION_MATCH_SQL))
        {
            if (!rows.next()) throw new SQLException("维度关系校验结果不可用");
            return rows.getLong(1);
        }
    }

    private static long duplicateKeys(Connection target, String stage) throws SQLException
    {
        String keys = "tenant_id,month_start,org_id,channel,category_id";
        try (Statement statement = target.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count() FROM (SELECT " + keys + " FROM " + stage + " GROUP BY " + keys + " HAVING count()>1)"))
        {
            if (!rows.next()) throw new SQLException("DWS 键校验结果不可用");
            return rows.getLong(1);
        }
    }

    private static long countValidFacts(Connection target) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("SELECT count() FROM " + quote(DWD_DATABASE) + "." + quote(FACT) + " WHERE is_deleted=0"))
        {
            if (!rows.next()) throw new SQLException("事实行数不可用");
            return rows.getLong(1);
        }
    }

    private static long duplicateFactKeys(Connection target) throws SQLException
    {
        String sql = "SELECT count() FROM (SELECT tenant_id,order_id,line_no FROM " + quote(DWD_DATABASE) + "." + quote(FACT)
                + " GROUP BY tenant_id,order_id,line_no HAVING count()>1)";
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            if (!rows.next()) throw new SQLException("订单明细业务键校验结果不可用");
            return rows.getLong(1);
        }
    }

    private static long invalidFacts(Connection target) throws SQLException
    {
        String sql = "SELECT count() FROM " + quote(DWD_DATABASE) + "." + quote(FACT)
                + " WHERE tenant_id='' OR order_id='' OR org_id='' OR product_id='' OR is_deleted NOT IN (0,1)";
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            if (!rows.next()) throw new SQLException("订单明细完整性校验结果不可用");
            return rows.getLong(1);
        }
    }

    private static Map<String, Object> reconciliationResult(Connection target, String stage, long runId, Long targetId) throws SQLException
    {
        BigDecimal[] expected = sums(target, "SELECT sum(sales_amount),sum(quantity) FROM " + quote(DWD_DATABASE) + "." + quote(FACT) + " WHERE is_deleted=0");
        BigDecimal[] actual = sums(target, "SELECT sum(sales_amount),sum(net_quantity) FROM " + stage);
        if (expected[0].compareTo(actual[0]) != 0 || expected[1].compareTo(actual[1]) != 0)
            throw new IllegalStateException("源事实与 DWS 金额/数量对账不一致，未发布");
        Timestamp sourceMax = sourceMaxUpdatedAt(target);
        Long age = sourceMax == null ? null : Math.max(0, Duration.between(sourceMax.toInstant(), Instant.now()).getSeconds());
        Map<String, Object> result = new HashMap<>();
        result.put("runId", runId); result.put("targetId", targetId); result.put("modelVersion", "retail-v1");
        result.put("sourceTable", DWD_DATABASE + "." + FACT); result.put("targetTable", DWS_DATABASE + "." + TARGET);
        result.put("sourceRows", countValidFacts(target)); result.put("targetRows", count(target, stage));
        result.put("sourceAmount", expected[0]); result.put("targetAmount", actual[0]);
        result.put("sourceQuantity", expected[1]); result.put("targetQuantity", actual[1]);
        result.put("sourceMaxUpdatedAt", sourceMax); result.put("freshnessAgeSeconds", age);
        result.put("status", "PREPARED"); result.put("summary", "全量明细与汇总销售额/净数量核对一致，等待原子发布");
        return result;
    }

    private static Timestamp sourceMaxUpdatedAt(Connection target) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("SELECT max(source_updated_at) FROM " + quote(DWD_DATABASE) + "." + quote(FACT) + " WHERE is_deleted=0"))
        {
            if (!rows.next()) throw new SQLException("来源最新更新时间不可用");
            return rows.getTimestamp(1);
        }
    }

    private static BigDecimal[] sums(Connection target, String sql) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            if (!rows.next()) throw new SQLException("汇总对账结果不可用");
            BigDecimal amount = rows.getBigDecimal(1);
            BigDecimal quantity = rows.getBigDecimal(2);
            return new BigDecimal[]{ amount == null ? BigDecimal.ZERO : amount, quantity == null ? BigDecimal.ZERO : quantity };
        }
    }

    private static long count(Connection target, String table) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("SELECT count() FROM " + table))
        {
            if (!rows.next()) throw new SQLException("暂存行数不可用");
            return rows.getLong(1);
        }
    }

    private static boolean exists(Connection target, String table) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("EXISTS TABLE " + table))
        { return rows.next() && rows.getInt(1) == 1; }
    }

    private static void requireAtomicDatabase(Connection target, String database) throws SQLException
    {
        if (!target.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("clickhouse"))
            throw new IllegalArgumentException("模型目标仅支持 ClickHouse");
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("SELECT engine FROM system.databases WHERE name='" + database + "'"))
        {
            if (!rows.next() || !"Atomic".equalsIgnoreCase(rows.getString(1))) throw new IllegalArgumentException("DWS 目标库必须存在且使用 Atomic 引擎");
        }
    }

    private static boolean acquireLock(Connection connection, String name) throws SQLException
    {
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)"))
        {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() && rows.getInt(1) == 1; }
        }
    }

    private static void releaseLock(Connection connection, String name) throws SQLException
    {
        try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)"))
        { statement.setString(1, name); try (ResultSet ignored = statement.executeQuery()) { /* release session lock */ } }
    }

    private static String targetLockName(String database, String table)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((database + "." + table).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return "dpwh:" + HexFormat.of().formatHex(digest, 0, 24);
        }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String quote(String value) { return "`" + value.replace("`", "``") + "`"; }

    private static final class MaterializationFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private MaterializationFailure(Throwable cause) { super(cause); }
    }
}
