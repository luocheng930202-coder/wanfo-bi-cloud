package com.wanfo.bi.warehouse;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Single write-path facade for analysis-warehouse publications.
 * ClickHouse: staging → validate → EXCHANGE; Doris/StarRocks: staging → validate → REPLACE/SWAP/RENAME.
 */
public final class WarehousePublisher
{
    public enum PublishMode { REPLACE, MERGE, APPEND }
    public enum Engine { CLICKHOUSE, DORIS, STARROCKS }

    public record Progress(String phase) { }
    public record Result(long rows, String table, boolean replaced, String watermarkValue, PublishMode mode) { }

    private WarehousePublisher() { }

    public static PublishMode parseMode(String raw, String loadMode)
    {
        if (raw != null && !raw.isBlank())
        {
            String mode = raw.trim().toUpperCase(Locale.ROOT);
            if (!mode.equals("REPLACE") && !mode.equals("MERGE"))
                throw new IllegalArgumentException("发布模式须为 REPLACE 或 MERGE");
            return PublishMode.valueOf(mode);
        }
        return "INCREMENTAL".equalsIgnoreCase(loadMode == null ? "" : loadMode.trim())
                ? PublishMode.MERGE : PublishMode.REPLACE;
    }

    public static Engine parseEngine(String datasourceType)
    {
        String type = datasourceType == null ? "" : datasourceType.trim().toLowerCase(Locale.ROOT);
        return switch (type)
        {
            case "clickhouse", "ck" -> Engine.CLICKHOUSE;
            case "doris" -> Engine.DORIS;
            case "starrocks" -> Engine.STARROCKS;
            default -> throw new IllegalArgumentException("数仓目标引擎须为 ClickHouse、Doris 或 StarRocks");
        };
    }

    public static Result publishResultSet(Connection target, ResultSet rows, String database, String table, long runId,
            int batchSize, int maxRows, PublishMode mode, String watermarkColumn, List<String> keyColumns,
            Consumer<Progress> progress) throws SQLException
    {
        return publishResultSet(target, Engine.CLICKHOUSE, rows, database, table, runId, batchSize, maxRows, mode,
                watermarkColumn, keyColumns, progress);
    }

    public static Result publishResultSet(Connection target, Engine engine, ResultSet rows, String database, String table,
            long runId, int batchSize, int maxRows, PublishMode mode, String watermarkColumn, List<String> keyColumns,
            Consumer<Progress> progress) throws SQLException
    {
        notify(progress, "STAGED");
        notify(progress, "VALIDATING");
        if (mode == PublishMode.MERGE)
        {
            if (watermarkColumn == null || watermarkColumn.isBlank() || keyColumns == null || keyColumns.isEmpty())
                throw new IllegalArgumentException("MERGE 发布须提供水位字段与业务键");
            notify(progress, "PROMOTING");
            if (engine == Engine.CLICKHOUSE)
            {
                var incremental = ClickHouseBatchPublisher.publishIncremental(target, rows, database, table, runId,
                        batchSize, maxRows, watermarkColumn, keyColumns);
                return new Result(incremental.rows(), incremental.table(), incremental.replaced(),
                        incremental.watermarkValue(), mode);
            }
            var incremental = MysqlProtocolBatchPublisher.publishIncremental(target, engine, rows, database, table, runId,
                    batchSize, maxRows, watermarkColumn, keyColumns);
            return new Result(incremental.rows(), incremental.table(), incremental.replaced(),
                    incremental.watermarkValue(), mode);
        }
        notify(progress, "PROMOTING");
        if (engine == Engine.CLICKHOUSE)
        {
            var published = ClickHouseBatchPublisher.publish(target, rows, database, table, runId, batchSize, maxRows);
            return new Result(published.rows(), published.table(), published.replaced(), null, mode);
        }
        var published = MysqlProtocolBatchPublisher.publish(target, engine, rows, database, table, runId, batchSize, maxRows);
        return new Result(published.rows(), published.table(), published.replaced(), null, mode);
    }

    public static Result publishMaps(Connection target, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows,
            Consumer<Progress> progress) throws SQLException
    {
        return publishMaps(target, Engine.CLICKHOUSE, database, table, runId, columnNames, rows, batchSize, maxRows, progress);
    }

    public static Result publishMaps(Connection target, Engine engine, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows,
            Consumer<Progress> progress) throws SQLException
    {
        notify(progress, "STAGED");
        notify(progress, "VALIDATING");
        notify(progress, "PROMOTING");
        if (engine == Engine.CLICKHOUSE)
        {
            var published = ClickHouseBatchPublisher.publishMaps(target, database, table, runId, columnNames, rows,
                    batchSize, maxRows);
            return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.REPLACE);
        }
        var published = MysqlProtocolBatchPublisher.publishMaps(target, engine, database, table, runId, columnNames, rows,
                batchSize, maxRows);
        return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.REPLACE);
    }

    /** Append rows into an existing pipe_* table; creates the table on first write (same as REPLACE bootstrap). */
    /** Merge rows by business keys into an existing pipe_* table (or bootstrap on first write). */
    public static Result upsertMaps(Connection target, Engine engine, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, List<String> mergeKeys, int batchSize, int maxRows,
            Consumer<Progress> progress) throws SQLException
    {
        notify(progress, "STAGED");
        notify(progress, "VALIDATING");
        notify(progress, "PROMOTING");
        if (mergeKeys == null || mergeKeys.isEmpty())
            throw new IllegalArgumentException("UPSERT 发布须提供 mergeKeys");
        if (engine == Engine.CLICKHOUSE)
        {
            var published = ClickHouseBatchPublisher.upsertMaps(target, database, table, runId, columnNames, rows,
                    mergeKeys, batchSize, maxRows);
            return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.MERGE);
        }
        var published = MysqlProtocolBatchPublisher.upsertMaps(target, engine, database, table, runId, columnNames, rows,
                mergeKeys, batchSize, maxRows);
        return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.MERGE);
    }

    public static Result appendMaps(Connection target, Engine engine, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows,
            Consumer<Progress> progress) throws SQLException
    {
        notify(progress, "STAGED");
        notify(progress, "VALIDATING");
        notify(progress, "PROMOTING");
        if (engine == Engine.CLICKHOUSE)
        {
            var published = ClickHouseBatchPublisher.appendMaps(target, database, table, runId, columnNames, rows,
                    batchSize, maxRows);
            return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.APPEND);
        }
        var published = MysqlProtocolBatchPublisher.appendMaps(target, engine, database, table, runId, columnNames, rows,
                batchSize, maxRows);
        return new Result(published.rows(), published.table(), published.replaced(), null, PublishMode.APPEND);
    }

    public static void dropRunStage(Connection target, String database, long runId) throws SQLException
    {
        dropRunStage(target, Engine.CLICKHOUSE, database, runId);
    }

    public static void dropRunStage(Connection target, Engine engine, String database, long runId) throws SQLException
    {
        if (engine == Engine.CLICKHOUSE) ClickHouseBatchPublisher.dropRunStage(target, database, runId);
        else MysqlProtocolBatchPublisher.dropRunStage(target, engine, database, runId);
    }

    private static void notify(Consumer<Progress> progress, String phase)
    {
        if (progress != null) progress.accept(new Progress(phase));
    }
}
