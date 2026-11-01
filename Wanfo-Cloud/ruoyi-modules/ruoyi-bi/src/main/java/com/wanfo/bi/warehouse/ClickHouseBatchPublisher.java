package com.wanfo.bi.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Publishes a bounded JDBC result into a ClickHouse table through an isolated staging table.
 * Callers must serialize publications to the same target table across service instances.
 */
public final class ClickHouseBatchPublisher
{
    public static final int DEFAULT_BATCH_SIZE = 10_000;
    public static final int MAX_BATCH_ROWS = 1_000_000;

    private ClickHouseBatchPublisher() { }

    public record Column(String name, String sourceType, int jdbcType, int precision, int scale, boolean nullable) { }
    public record PublishResult(long rows, String table, boolean replaced) { }
    public record IncrementalPublishResult(long rows, String table, boolean replaced, String watermarkValue) { }

    public static PublishResult publish(Connection target, ResultSet sourceRows, String database,
            String table, long runId, int batchSize, int maxRows) throws SQLException
    {
        List<Column> columns = sourceColumns(sourceRows.getMetaData());
        return publishPrepared(target, database, table, runId, batchSize, maxRows, columns, (connection, cols, insertSql, size, limit) ->
                insertRows(connection, sourceRows, cols, insertSql, size, limit, -1).rows());
    }

    /**
     * Publishes in-memory pipeline rows into a warehouse layer table via staging + atomic rename/exchange.
     * Column types are inferred once from sample values (nullable String fallback).
     */
    public static PublishResult publishMaps(Connection target, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows) throws SQLException
    {
        if (columnNames == null || columnNames.isEmpty()) throw new IllegalArgumentException("发布字段不能为空");
        if (rows == null) throw new IllegalArgumentException("发布数据不能为空");
        List<Column> columns = mapColumns(columnNames, rows);
        return publishPrepared(target, database, table, runId, batchSize, maxRows, columns, (connection, cols, insertSql, size, limit) ->
                insertMapRows(connection, cols, rows, insertSql, size, limit), false);
    }

    /** Append into existing target; if missing, bootstrap like REPLACE first publish. */
    public static PublishResult appendMaps(Connection target, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows) throws SQLException
    {
        if (columnNames == null || columnNames.isEmpty()) throw new IllegalArgumentException("发布字段不能为空");
        if (rows == null) throw new IllegalArgumentException("发布数据不能为空");
        List<Column> columns = mapColumns(columnNames, rows);
        return publishPrepared(target, database, table, runId, batchSize, maxRows, columns, (connection, cols, insertSql, size, limit) ->
                insertMapRows(connection, cols, rows, insertSql, size, limit), true);
    }

    /** Merge in-memory rows by business keys (no watermark). */
    public static PublishResult upsertMaps(Connection target, String database, String table, long runId,
            List<String> columnNames, List<Map<String, Object>> rows, List<String> mergeKeys, int batchSize, int maxRows)
            throws SQLException
    {
        if (columnNames == null || columnNames.isEmpty() || rows == null)
            throw new IllegalArgumentException("发布数据不能为空");
        if (mergeKeys == null || mergeKeys.isEmpty() || mergeKeys.size() > 16)
            throw new IllegalArgumentException("业务键须包含 1～16 个字段");
        List<Column> columns = mapColumns(columnNames, rows);
        java.util.Map<String, Integer> positions = new java.util.HashMap<>();
        for (int index = 0; index < columns.size(); index++)
            positions.put(columns.get(index).name().toLowerCase(Locale.ROOT), index);
        List<String> normalizedKeys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String key : mergeKeys)
        {
            requireIdentifier(key, 128, "业务键字段");
            Integer position = positions.get(key.toLowerCase(Locale.ROOT));
            if (position == null) throw new IllegalArgumentException("业务键字段不存在于发布 schema");
            if (!seenKeys.add(columns.get(position).name().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("业务键字段重复");
            normalizedKeys.add(columns.get(position).name());
        }

        String stageName = "__dp_stage_" + runId;
        String publishName = "__dp_publish_" + runId;
        String targetName = quote(database) + "." + quote(table);
        String stage = quote(database) + "." + quote(stageName);
        String publish = quote(database) + "." + quote(publishName);
        boolean stageCreated = false;
        boolean publishCreated = false;
        boolean published = false;
        try
        {
            ensureAtomicDatabase(target, database);
            String definitions = columns.stream().map(column -> quote(column.name()) + " " + clickHouseType(column))
                    .reduce((a, b) -> a + "," + b).orElseThrow();
            try (Statement statement = target.createStatement())
            {
                statement.execute("CREATE TABLE " + stage + " (" + definitions + ") ENGINE=MergeTree ORDER BY tuple()");
                stageCreated = true;
            }
            String columnList = columns.stream().map(column -> quote(column.name())).reduce((a, b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + stage + " (" + columnList + ") VALUES ("
                    + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            long rowCount = insertMapRows(target, columns, rows, insertSql, batchSize, maxRows);
            if (rowCount == 0) return new PublishResult(0, database + "." + table, exists(target, targetName));
            if (countRows(target, stage) != rowCount)
                throw new IllegalStateException("UPSERT 暂存数据行数校验失败");

            String keyTuple = "tuple(" + normalizedKeys.stream().map(ClickHouseBatchPublisher::quote).reduce((a, b) -> a + "," + b).orElseThrow() + ")";
            try (Statement statement = target.createStatement();
                 ResultSet duplicates = statement.executeQuery("SELECT count() FROM (SELECT " + keyTuple + " FROM " + stage
                         + " GROUP BY " + keyTuple + " HAVING count() > 1)"))
            {
                if (!duplicates.next() || duplicates.getLong(1) > 0)
                    throw new IllegalArgumentException("UPSERT 批次包含重复业务键");
            }

            boolean replaced = exists(target, targetName);
            if (replaced)
            {
                ensureCompatibleExistingTable(target, database, table, columns);
                try (Statement statement = target.createStatement())
                {
                    statement.execute("CREATE TABLE " + publish + " AS " + targetName);
                    publishCreated = true;
                    String oldRows = "SELECT " + columnList + " FROM " + targetName + " WHERE " + keyTuple
                            + " NOT IN (SELECT " + keyTuple + " FROM " + stage + ")";
                    statement.execute("INSERT INTO " + publish + " (" + columnList + ") " + oldRows);
                    statement.execute("INSERT INTO " + publish + " (" + columnList + ") SELECT " + columnList + " FROM " + stage);
                    long expectedRows;
                    try (ResultSet expected = statement.executeQuery("SELECT count() FROM " + targetName + " WHERE "
                            + keyTuple + " NOT IN (SELECT " + keyTuple + " FROM " + stage + ")"))
                    {
                        if (!expected.next()) throw new IllegalStateException("UPSERT 合并行数校验失败");
                        expectedRows = expected.getLong(1) + rowCount;
                    }
                    if (countRows(target, publish) != expectedRows)
                        throw new IllegalStateException("UPSERT 合并行数校验失败");
                    statement.execute("EXCHANGE TABLES " + targetName + " AND " + publish);
                    published = true;
                }
                try (Statement statement = target.createStatement())
                {
                    try { statement.execute("DROP TABLE IF EXISTS " + publish); } catch (SQLException ignored) { }
                    try { statement.execute("DROP TABLE IF EXISTS " + stage); } catch (SQLException ignored) { }
                }
            }
            else
            {
                try (Statement statement = target.createStatement()) { statement.execute("RENAME TABLE " + stage + " TO " + targetName); }
                published = true;
            }
            return new PublishResult(rowCount, database + "." + table, replaced);
        }
        finally
        {
            if (!published)
            {
                try (Statement statement = target.createStatement())
                {
                    if (publishCreated) statement.execute("DROP TABLE IF EXISTS " + publish);
                    if (stageCreated) statement.execute("DROP TABLE IF EXISTS " + stage);
                }
                catch (SQLException ignored) { }
            }
        }
    }

    @FunctionalInterface
    private interface RowInserter
    {
        long insert(Connection target, List<Column> columns, String insertSql, int batchSize, int maxRows) throws SQLException;
    }

    private static PublishResult publishPrepared(Connection target, String database, String table, long runId,
            int batchSize, int maxRows, List<Column> columns, RowInserter inserter) throws SQLException
    {
        return publishPrepared(target, database, table, runId, batchSize, maxRows, columns, inserter, false);
    }

    private static PublishResult publishPrepared(Connection target, String database, String table, long runId,
            int batchSize, int maxRows, List<Column> columns, RowInserter inserter, boolean append) throws SQLException
    {
        requireClickHouse(target);
        requireIdentifier(database, 64, "目标数据库");
        requireIdentifier(table, 128, "目标表");
        if (!database.startsWith("wanfo_wh_") || !Set.of("wanfo_wh_ods", "wanfo_wh_dwd", "wanfo_wh_dws", "wanfo_wh_ads").contains(database))
            throw new IllegalArgumentException("目标必须位于受控数仓逻辑层");
        if (table.startsWith("__dp_stage_") || table.startsWith("system"))
            throw new IllegalArgumentException("目标表名保留或无效");
        if (runId < 1) throw new IllegalArgumentException("入仓运行 ID 无效");
        if (batchSize < 1 || batchSize > DEFAULT_BATCH_SIZE) throw new IllegalArgumentException("批量行数须在 1～10000 之间");
        if (maxRows < 1 || maxRows > MAX_BATCH_ROWS) throw new IllegalArgumentException("单次入仓上限须在 1～1000000 行之间");

        String stage = "__dp_stage_" + runId;
        String qualifiedTarget = quote(database) + "." + quote(table);
        String qualifiedStage = quote(database) + "." + quote(stage);
        boolean stageCreated = false;
        boolean published = false;
        try
        {
            ensureAtomicDatabase(target, database);
            String definitions = columns.stream().map(column -> quote(column.name()) + " " + clickHouseType(column)).reduce((a, b) -> a + "," + b).orElseThrow();
            try (Statement statement = target.createStatement())
            {
                statement.execute("CREATE TABLE " + qualifiedStage + " (" + definitions + ") ENGINE=MergeTree ORDER BY tuple()");
                stageCreated = true;
            }
            String columnList = columns.stream().map(column -> quote(column.name())).reduce((a,b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + qualifiedStage + " (" + columnList
                    + ") VALUES (" + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            long rows = inserter.insert(target, columns, insertSql, batchSize, maxRows);
            if (countRows(target, qualifiedStage) != rows)
                throw new IllegalStateException("暂存数据行数校验失败，未替换目标表");
            ensureCompatibleExistingTable(target, database, table, columns);
            boolean targetExists = exists(target, qualifiedTarget);
            try (Statement statement = target.createStatement())
            {
                if (append && targetExists)
                {
                    statement.execute("INSERT INTO " + qualifiedTarget + " (" + columnList + ") SELECT " + columnList
                            + " FROM " + qualifiedStage);
                    statement.execute("DROP TABLE " + qualifiedStage);
                    published = true;
                    return new PublishResult(rows, database + "." + table, false);
                }
                if (targetExists) statement.execute("EXCHANGE TABLES " + qualifiedTarget + " AND " + qualifiedStage);
                else statement.execute("RENAME TABLE " + qualifiedStage + " TO " + qualifiedTarget);
                published = true;
            }
            if (targetExists && !append)
            {
                try (Statement statement = target.createStatement()) { statement.execute("DROP TABLE " + qualifiedStage); }
                catch (SQLException ignored) { /* New target is already published; leave the old table for later cleanup. */ }
            }
            return new PublishResult(rows, database + "." + table, targetExists && !append);
        }
        finally
        {
            if (stageCreated && !published)
            {
                try (Statement statement = target.createStatement()) { statement.execute("DROP TABLE IF EXISTS " + qualifiedStage); }
                catch (SQLException ignored) { /* Preserve the primary ingestion error; stale staging is not query-visible. */ }
            }
        }
    }

    private static List<Column> mapColumns(List<String> columnNames, List<Map<String, Object>> rows)
    {
        if (columnNames.size() > 500) throw new IllegalArgumentException("源字段数量须在 1～500 之间");
        Set<String> names = new HashSet<>();
        List<Column> columns = new ArrayList<>(columnNames.size());
        for (String name : columnNames)
        {
            requireColumnName(name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("源字段名重复，无法安全入仓");
            Integer jdbcType = null;
            boolean nullable = rows.isEmpty();
            for (Map<String, Object> row : rows)
            {
                Object value = row.get(name);
                if (value == null) { nullable = true; continue; }
                int sample = jdbcTypeOf(value);
                jdbcType = jdbcType == null ? sample : widenJdbcTypes(jdbcType, sample);
            }
            int resolved = jdbcType == null ? Types.VARCHAR : jdbcType;
            columns.add(new Column(name, "PIPELINE", resolved, resolved == Types.DECIMAL ? 38 : 0, resolved == Types.DECIMAL ? 12 : 0, nullable || rows.isEmpty()));
        }
        return List.copyOf(columns);
    }

    private static int jdbcTypeOf(Object value)
    {
        if (value instanceof Boolean) return Types.BOOLEAN;
        if (value instanceof Byte) return Types.TINYINT;
        if (value instanceof Short) return Types.SMALLINT;
        if (value instanceof Integer) return Types.INTEGER;
        if (value instanceof Long) return Types.BIGINT;
        if (value instanceof Float) return Types.FLOAT;
        if (value instanceof Double) return Types.DOUBLE;
        if (value instanceof java.math.BigDecimal) return Types.DECIMAL;
        if (value instanceof java.time.LocalDate || value instanceof java.sql.Date) return Types.DATE;
        if (value instanceof java.time.LocalDateTime || value instanceof java.sql.Timestamp) return Types.TIMESTAMP;
        if (value instanceof java.time.OffsetDateTime) return Types.TIMESTAMP_WITH_TIMEZONE;
        if (value instanceof Number number) return number.toString().contains(".") ? Types.DOUBLE : Types.BIGINT;
        return Types.VARCHAR;
    }

    private static int widenJdbcTypes(int current, int sample)
    {
        if (current == sample) return current;
        if (current == Types.VARCHAR || sample == Types.VARCHAR) return Types.VARCHAR;
        if (isIntegral(current) && isIntegral(sample)) return widerIntegral(current, sample);
        if (isFloating(current) && isFloating(sample)) return Types.DOUBLE;
        if ((isIntegral(current) || isFloating(current)) && (isIntegral(sample) || isFloating(sample))) return Types.DOUBLE;
        return Types.VARCHAR;
    }

    private static int widerIntegral(int left, int right)
    {
        int rank = Math.max(integralRank(left), integralRank(right));
        return switch (rank) { case 1 -> Types.TINYINT; case 2 -> Types.SMALLINT; case 3 -> Types.INTEGER; default -> Types.BIGINT; };
    }

    private static int integralRank(int type)
    {
        return switch (type) { case Types.TINYINT -> 1; case Types.SMALLINT -> 2; case Types.INTEGER -> 3; default -> 4; };
    }

    private static boolean isIntegral(int type)
    {
        return type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER || type == Types.BIGINT;
    }

    private static boolean isFloating(int type)
    {
        return type == Types.FLOAT || type == Types.DOUBLE || type == Types.DECIMAL || type == Types.NUMERIC || type == Types.REAL;
    }

    private static long insertMapRows(Connection target, List<Column> columns, List<Map<String, Object>> rows,
            String sql, int batchSize, int maxRows) throws SQLException
    {
        if (rows.size() > maxRows) throw new IllegalArgumentException("源表超过本次入仓行数上限");
        long count = 0;
        try (PreparedStatement insert = target.prepareStatement(sql))
        {
            for (Map<String, Object> row : rows)
            {
                count++;
                for (int index = 0; index < columns.size(); index++)
                {
                    Object value = row.get(columns.get(index).name());
                    if (value == null) insert.setNull(index + 1, columns.get(index).jdbcType());
                    else if (columns.get(index).jdbcType() == Types.VARCHAR) insert.setString(index + 1, String.valueOf(value));
                    else insert.setObject(index + 1, value);
                }
                insert.addBatch();
                if (count % batchSize == 0) insert.executeBatch();
            }
            if (count % batchSize != 0) insert.executeBatch();
        }
        return count;
    }

    /**
     * Publishes an append/update delta by atomically replacing the ODS snapshot. Existing rows with
     * any delta business key are removed before the delta is appended, making a retry idempotent.
     */
    public static IncrementalPublishResult publishIncremental(Connection target, ResultSet sourceRows,
            String database, String table, long runId, int batchSize, int maxRows,
            String watermarkColumn, List<String> keyColumns) throws SQLException
    {
        requireClickHouse(target);
        requireIdentifier(database, 64, "目标数据库");
        requireIdentifier(table, 128, "目标表");
        if (!Set.of("wanfo_wh_ods", "wanfo_wh_dwd", "wanfo_wh_dws", "wanfo_wh_ads").contains(database))
            throw new IllegalArgumentException("目标必须位于受控数仓逻辑层");
        if (runId < 1 || batchSize < 1 || batchSize > DEFAULT_BATCH_SIZE || maxRows < 1 || maxRows > MAX_BATCH_ROWS)
            throw new IllegalArgumentException("增量入仓运行参数无效");
        requireIdentifier(watermarkColumn, 128, "水位字段");
        if (keyColumns == null || keyColumns.isEmpty() || keyColumns.size() > 16)
            throw new IllegalArgumentException("业务键须包含 1～16 个字段");

        List<Column> columns = sourceColumns(sourceRows.getMetaData());
        Map<String, Integer> positions = new java.util.HashMap<>();
        for (int index = 0; index < columns.size(); index++) positions.put(columns.get(index).name().toLowerCase(Locale.ROOT), index);
        Integer watermarkPosition = positions.get(watermarkColumn.toLowerCase(Locale.ROOT));
        if (watermarkPosition == null) throw new IllegalArgumentException("水位字段不存在于源表");
        Column watermark = columns.get(watermarkPosition);
        if (watermark.nullable() || !isOrderableWatermark(watermark.jdbcType()))
            throw new IllegalArgumentException("水位字段必须为非空数字或日期时间类型");
        List<String> normalizedKeys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String key : keyColumns)
        {
            requireIdentifier(key, 128, "业务键字段");
            Integer position = positions.get(key.toLowerCase(Locale.ROOT));
            if (position == null) throw new IllegalArgumentException("业务键字段不存在于源表");
            Column column = columns.get(position);
            if (column.nullable()) throw new IllegalArgumentException("业务键字段必须定义为非空");
            if (!seenKeys.add(column.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("业务键字段重复");
            normalizedKeys.add(column.name());
        }

        String stageName = "__dp_stage_" + runId;
        String publishName = "__dp_publish_" + runId;
        String targetName = quote(database) + "." + quote(table);
        String stage = quote(database) + "." + quote(stageName);
        String publish = quote(database) + "." + quote(publishName);
        boolean stageCreated = false;
        boolean publishCreated = false;
        boolean published = false;
        try
        {
            ensureAtomicDatabase(target, database);
            String definitions = columns.stream().map(column -> quote(column.name()) + " " + clickHouseType(column)).reduce((a, b) -> a + "," + b).orElseThrow();
            try (Statement statement = target.createStatement())
            {
                statement.execute("CREATE TABLE " + stage + " (" + definitions + ") ENGINE=MergeTree ORDER BY tuple()");
                stageCreated = true;
            }
            String columnList = columns.stream().map(column -> quote(column.name())).reduce((a, b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + stage + " (" + columnList + ") VALUES (" + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            InsertResult inserted = insertRows(target, sourceRows, columns, insertSql, batchSize, maxRows, watermarkPosition + 1);
            long rows = inserted.rows();
            if (rows == 0) return new IncrementalPublishResult(0, database + "." + table, exists(target, targetName), null);
            if (countRows(target, stage) != rows) throw new IllegalStateException("增量暂存数据行数校验失败，未替换目标表");

            String keyTuple = "tuple(" + normalizedKeys.stream().map(ClickHouseBatchPublisher::quote).reduce((a, b) -> a + "," + b).orElseThrow() + ")";
            try (Statement statement = target.createStatement();
                 ResultSet duplicates = statement.executeQuery("SELECT count() FROM (SELECT " + keyTuple + " FROM " + stage + " GROUP BY " + keyTuple + " HAVING count() > 1)"))
            {
                if (!duplicates.next() || duplicates.getLong(1) > 0) throw new IllegalArgumentException("增量批次包含重复业务键，请先修正源数据");
            }
            String nullKeyPredicate = normalizedKeys.stream().map(key -> quote(key) + " IS NULL").reduce((a, b) -> a + " OR " + b).orElseThrow();
            try (Statement statement = target.createStatement(); ResultSet nullKeys = statement.executeQuery("SELECT count() FROM " + stage + " WHERE " + nullKeyPredicate))
            {
                if (!nullKeys.next() || nullKeys.getLong(1) > 0) throw new IllegalArgumentException("增量批次的业务键含空值");
            }

            boolean replaced = exists(target, targetName);
            if (replaced)
            {
                ensureCompatibleExistingTable(target, database, table, columns);
                try (Statement statement = target.createStatement())
                {
                    statement.execute("CREATE TABLE " + publish + " AS " + targetName);
                    publishCreated = true;
                    String oldRows = "SELECT " + columnList + " FROM " + targetName + " WHERE " + keyTuple + " NOT IN (SELECT " + keyTuple + " FROM " + stage + ")";
                    statement.execute("INSERT INTO " + publish + " (" + columnList + ") " + oldRows);
                    statement.execute("INSERT INTO " + publish + " (" + columnList + ") SELECT " + columnList + " FROM " + stage);
                    long expectedRows;
                    try (ResultSet expected = statement.executeQuery("SELECT count() FROM " + targetName + " WHERE " + keyTuple + " NOT IN (SELECT " + keyTuple + " FROM " + stage + ")"))
                    {
                        if (!expected.next()) throw new IllegalStateException("增量合并行数校验失败，未替换目标表");
                        expectedRows = expected.getLong(1) + rows;
                    }
                    if (countRows(target, publish) != expectedRows)
                        throw new IllegalStateException("增量合并行数校验失败，未替换目标表");
                    statement.execute("EXCHANGE TABLES " + targetName + " AND " + publish);
                    published = true;
                }
                try (Statement statement = target.createStatement())
                {
                    try { statement.execute("DROP TABLE IF EXISTS " + publish); } catch (SQLException ignored) { /* Published target is already live. */ }
                    try { statement.execute("DROP TABLE IF EXISTS " + stage); } catch (SQLException ignored) { /* Startup recovery removes the run-owned delta table. */ }
                }
            }
            else
            {
                try (Statement statement = target.createStatement()) { statement.execute("RENAME TABLE " + stage + " TO " + targetName); }
                published = true;
            }
            return new IncrementalPublishResult(rows, database + "." + table, replaced, inserted.lastWatermark());
        }
        finally
        {
            if (!published)
            {
                try (Statement statement = target.createStatement())
                {
                    if (publishCreated) statement.execute("DROP TABLE IF EXISTS " + publish);
                    if (stageCreated) statement.execute("DROP TABLE IF EXISTS " + stage);
                }
                catch (SQLException ignored) { /* Keep the primary error; startup recovery also removes run-owned stages. */ }
            }
        }
    }

    /** Removes only the deterministic staging table owned by an interrupted run. */
    public static void dropRunStage(Connection target, String database, long runId) throws SQLException
    {
        requireClickHouse(target);
        requireIdentifier(database, 64, "目标数据库");
        if (!Set.of("wanfo_wh_ods", "wanfo_wh_dwd", "wanfo_wh_dws", "wanfo_wh_ads").contains(database))
            throw new IllegalArgumentException("目标必须位于受控数仓逻辑层");
        if (runId < 1) throw new IllegalArgumentException("入仓运行 ID 无效");
        try (Statement statement = target.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS " + quote(database) + "." + quote("__dp_stage_" + runId));
            statement.execute("DROP TABLE IF EXISTS " + quote(database) + "." + quote("__dp_publish_" + runId));
            statement.execute("DROP TABLE IF EXISTS " + quote(database) + "." + quote("__dp_model_stage_" + runId));
        }
    }

    private record InsertResult(long rows, String lastWatermark) { }

    private static InsertResult insertRows(Connection target, ResultSet source, List<Column> columns,
            String sql, int batchSize, int maxRows, int watermarkPosition) throws SQLException
    {
        long rows = 0;
        String lastWatermark = null;
        try (PreparedStatement insert = target.prepareStatement(sql))
        {
            while (source.next())
            {
                if (++rows > maxRows) throw new IllegalArgumentException("源表超过本次入仓行数上限");
                if (watermarkPosition > 0)
                {
                    Object watermark = source.getObject(watermarkPosition);
                    if (watermark == null) throw new IllegalArgumentException("水位字段数据不能含空值");
                    lastWatermark = watermark.toString();
                }
                for (int index = 0; index < columns.size(); index++)
                {
                    Object value = source.getObject(index + 1);
                    if (value == null) insert.setNull(index + 1, columns.get(index).jdbcType());
                    else if (columns.get(index).jdbcType() == Types.OTHER) insert.setString(index + 1, value.toString());
                    else if (columns.get(index).jdbcType() == Types.CLOB || columns.get(index).jdbcType() == Types.NCLOB)
                        insert.setString(index + 1, source.getString(index + 1));
                    else insert.setObject(index + 1, value);
                }
                insert.addBatch();
                if (rows % batchSize == 0) insert.executeBatch();
            }
            if (rows % batchSize != 0) insert.executeBatch();
        }
        return new InsertResult(rows, lastWatermark);
    }

    private static List<Column> sourceColumns(ResultSetMetaData metadata) throws SQLException
    {
        int count = metadata.getColumnCount();
        if (count < 1 || count > 500) throw new IllegalArgumentException("源字段数量须在 1～500 之间");
        List<Column> columns = new ArrayList<>(count);
        Set<String> names = new HashSet<>();
        for (int index = 1; index <= count; index++)
        {
            String name = metadata.getColumnLabel(index);
            requireColumnName(name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("源字段名重复，无法安全入仓");
            int jdbcType = metadata.getColumnType(index);
            String sourceType = metadata.getColumnTypeName(index);
            int precision = metadata.getPrecision(index);
            int scale = metadata.getScale(index);
            boolean nullable = metadata.isNullable(index) != ResultSetMetaData.columnNoNulls;
            Column column = new Column(name, sourceType, jdbcType, precision, scale, nullable);
            clickHouseType(column); // Reject unsupported fields before creating the staging table.
            columns.add(column);
        }
        return List.copyOf(columns);
    }

    private static String clickHouseType(Column column)
    {
        String type = switch (column.jdbcType())
        {
            case Types.TINYINT -> "Int8";
            case Types.SMALLINT -> "Int16";
            case Types.INTEGER -> "Int32";
            case Types.BIGINT -> "Int64";
            case Types.NUMERIC, Types.DECIMAL -> decimal(column.precision(), column.scale());
            case Types.FLOAT, Types.REAL -> "Float32";
            case Types.DOUBLE -> "Float64";
            case Types.BOOLEAN -> "Bool";
            case Types.BIT -> {
                if (column.precision() > 1) throw new IllegalArgumentException("不支持多位 BIT 字段");
                yield "Bool";
            }
            case Types.DATE -> "Date";
            case Types.TIMESTAMP -> "DateTime64(6)";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "DateTime64(6, 'UTC')";
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "String";
            case Types.OTHER -> {
                if (!isTextualVendorType(column.sourceType())) throw new IllegalArgumentException("不支持的厂商字段类型：" + column.sourceType());
                yield "String";
            }
            default -> throw new IllegalArgumentException("暂不支持源字段 JDBC 类型：" + column.jdbcType());
        };
        return column.nullable() ? "Nullable(" + type + ")" : type;
    }

    private static String decimal(int precision, int scale)
    {
        if (precision < 1 || precision > 76 || scale < 0 || scale > precision)
            throw new IllegalArgumentException("源 DECIMAL 精度或小数位超出 ClickHouse 支持范围");
        return "Decimal(" + precision + ", " + scale + ")";
    }

    private static boolean isOrderableWatermark(int type)
    {
        return switch (type)
        {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
                    Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> true;
            default -> false;
        };
    }

    private static void ensureAtomicDatabase(Connection target, String database) throws SQLException
    {
        try (Statement statement = target.createStatement();
             ResultSet rows = statement.executeQuery("SELECT engine FROM system.databases WHERE name='" + database + "'"))
        {
            if (!rows.next()) throw new IllegalArgumentException("ClickHouse 数仓目标库不存在");
            if (!"Atomic".equalsIgnoreCase(rows.getString(1)))
                throw new IllegalArgumentException("目标数据库必须使用 Atomic 引擎才能原子发布");
        }
    }

    private static void ensureCompatibleExistingTable(Connection target, String database, String table,
            List<Column> sourceColumns) throws SQLException
    {
        if (!exists(target, quote(database) + "." + quote(table))) return;
        List<String> expected = sourceColumns.stream().map(ClickHouseBatchPublisher::clickHouseType).toList();
        List<String> actualNames = new ArrayList<>();
        List<String> actualTypes = new ArrayList<>();
        try (Statement statement = target.createStatement();
             ResultSet rows = statement.executeQuery("DESCRIBE TABLE " + quote(database) + "." + quote(table)))
        {
            while (rows.next()) { actualNames.add(rows.getString("name")); actualTypes.add(rows.getString("type")); }
        }
        if (actualTypes.size() != expected.size()) throw new IllegalArgumentException("源表结构已变化，目标表未自动替换；请先评审结构迁移");
        for (int index = 0; index < sourceColumns.size(); index++)
        {
            if (!sourceColumns.get(index).name().equals(actualNames.get(index))
                    || !expected.get(index).equals(actualTypes.get(index)))
                throw new IllegalArgumentException("源表结构已变化，目标表未自动替换；请先评审结构迁移");
        }
    }

    private static boolean isTextualVendorType(String value)
    {
        if (value == null) return false;
        String type = value.toLowerCase(Locale.ROOT);
        return type.contains("uuid") || type.contains("json") || type.contains("enum");
    }

    private static boolean exists(Connection target, String qualifiedTable) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("EXISTS TABLE " + qualifiedTable))
        { return rows.next() && rows.getInt(1) == 1; }
    }

    private static long countRows(Connection target, String qualifiedTable) throws SQLException
    {
        try (Statement statement = target.createStatement(); ResultSet rows = statement.executeQuery("SELECT count() FROM " + qualifiedTable))
        {
            if (!rows.next()) throw new SQLException("ClickHouse staging row count is unavailable");
            return rows.getLong(1);
        }
    }

    private static void requireClickHouse(Connection target) throws SQLException
    {
        if (!target.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("clickhouse"))
            throw new IllegalArgumentException("数仓目标仅支持 ClickHouse");
    }

    private static void requireColumnName(String value)
    {
        if (value == null || value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("源字段名无效");
    }

    private static void requireIdentifier(String value, int max, String label)
    {
        if (value == null || value.length() > max || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException(label + "标识符无效");
    }

    private static String quote(String value) { return "`" + value.replace("`", "``") + "`"; }
}
