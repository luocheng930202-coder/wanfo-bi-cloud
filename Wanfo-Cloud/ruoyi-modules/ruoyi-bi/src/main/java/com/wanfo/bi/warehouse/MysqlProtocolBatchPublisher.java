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

/**
 * Doris / StarRocks warehouse publisher (MySQL protocol).
 * Mirrors ClickHouseBatchPublisher semantics: staging → validate → atomic promote for REPLACE/MERGE.
 */
public final class MysqlProtocolBatchPublisher {
    public static final int DEFAULT_BATCH_SIZE = ClickHouseBatchPublisher.DEFAULT_BATCH_SIZE;
    public static final int MAX_BATCH_ROWS = ClickHouseBatchPublisher.MAX_BATCH_ROWS;

    private MysqlProtocolBatchPublisher() {}

    public record Column(String name, String sourceType, int jdbcType, int precision, int scale, boolean nullable) {}
    public record PublishResult(long rows, String table, boolean replaced) {}
    public record IncrementalPublishResult(long rows, String table, boolean replaced, String watermarkValue) {}

    public static PublishResult publish(Connection target, WarehousePublisher.Engine engine, ResultSet sourceRows,
            String database, String table, long runId, int batchSize, int maxRows) throws SQLException {
        List<Column> columns = sourceColumns(sourceRows.getMetaData());
        return publishPrepared(target, engine, database, table, runId, batchSize, maxRows, columns,
                (connection, cols, insertSql, size, limit) -> insertRows(connection, sourceRows, cols, insertSql, size, limit, -1).rows());
    }

    public static PublishResult publishMaps(Connection target, WarehousePublisher.Engine engine, String database,
            String table, long runId, List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows)
            throws SQLException {
        if (columnNames == null || columnNames.isEmpty()) throw new IllegalArgumentException("发布字段不能为空");
        if (rows == null) throw new IllegalArgumentException("发布数据不能为空");
        List<Column> columns = mapColumns(columnNames, rows);
        return publishPrepared(target, engine, database, table, runId, batchSize, maxRows, columns,
                (connection, cols, insertSql, size, limit) -> insertMapRows(connection, cols, rows, insertSql, size, limit),
                false);
    }

    /** Append into existing target; if missing, bootstrap like REPLACE first publish. */
    public static PublishResult appendMaps(Connection target, WarehousePublisher.Engine engine, String database,
            String table, long runId, List<String> columnNames, List<Map<String, Object>> rows, int batchSize, int maxRows)
            throws SQLException {
        if (columnNames == null || columnNames.isEmpty()) throw new IllegalArgumentException("发布字段不能为空");
        if (rows == null) throw new IllegalArgumentException("发布数据不能为空");
        List<Column> columns = mapColumns(columnNames, rows);
        return publishPrepared(target, engine, database, table, runId, batchSize, maxRows, columns,
                (connection, cols, insertSql, size, limit) -> insertMapRows(connection, cols, rows, insertSql, size, limit),
                true);
    }

    public static PublishResult upsertMaps(Connection target, WarehousePublisher.Engine engine, String database,
            String table, long runId, List<String> columnNames, List<Map<String, Object>> rows, List<String> mergeKeys,
            int batchSize, int maxRows) throws SQLException {
        requireMysqlProtocolEngine(engine);
        if (columnNames == null || columnNames.isEmpty() || rows == null)
            throw new IllegalArgumentException("发布数据不能为空");
        if (mergeKeys == null || mergeKeys.isEmpty() || mergeKeys.size() > 16)
            throw new IllegalArgumentException("业务键须包含 1～16 个字段");
        List<Column> columns = mapColumns(columnNames, rows);
        MapPositions positions = positionsOf(columns);
        List<String> normalizedKeys = normalizeKeys(mergeKeys, columns, positions);

        String stageName = "__dp_stage_" + runId;
        String publishName = "__dp_publish_" + runId;
        boolean stageCreated = false;
        boolean publishCreated = false;
        boolean published = false;
        try {
            ensureDatabase(target, database);
            createOlapTable(target, engine, database, stageName, columns);
            stageCreated = true;
            String columnList = columns.stream().map(c -> quote(c.name())).reduce((a, b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + q(database, stageName) + " (" + columnList + ") VALUES ("
                    + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            long rowCount = insertMapRows(target, columns, rows, insertSql, batchSize, maxRows);
            if (rowCount == 0)
                return new PublishResult(0, database + "." + table, tableExists(target, database, table));
            if (countRows(target, database, stageName) != rowCount)
                throw new IllegalStateException("UPSERT 暂存数据行数校验失败");
            assertUniqueKeys(target, database, stageName, normalizedKeys);
            assertNoNullKeys(target, database, stageName, normalizedKeys);

            boolean replaced = tableExists(target, database, table);
            if (replaced) {
                ensureCompatibleExistingTable(target, database, table, columns);
                createOlapTable(target, engine, database, publishName, columns);
                publishCreated = true;
                String keyTuple = keyTuple(normalizedKeys);
                try (Statement statement = target.createStatement()) {
                    statement.execute("INSERT INTO " + q(database, publishName) + " (" + columnList + ") SELECT "
                            + columnList + " FROM " + q(database, table) + " WHERE (" + keyTuple
                            + ") NOT IN (SELECT " + keyTuple + " FROM " + q(database, stageName) + ")");
                    statement.execute("INSERT INTO " + q(database, publishName) + " (" + columnList + ") SELECT "
                            + columnList + " FROM " + q(database, stageName));
                    long expected;
                    try (ResultSet expectedRs = statement.executeQuery(
                            "SELECT COUNT(*) FROM " + q(database, table) + " WHERE (" + keyTuple
                                    + ") NOT IN (SELECT " + keyTuple + " FROM " + q(database, stageName) + ")")) {
                        if (!expectedRs.next()) throw new IllegalStateException("UPSERT 合并行数校验失败");
                        expected = expectedRs.getLong(1) + rowCount;
                    }
                    if (countRows(target, database, publishName) != expected)
                        throw new IllegalStateException("UPSERT 合并行数校验失败");
                }
                promote(target, engine, database, table, publishName, true);
                published = true;
                dropQuietly(target, database, stageName);
                dropQuietly(target, database, publishName);
            } else {
                renameTable(target, engine, database, stageName, table);
                published = true;
            }
            return new PublishResult(rowCount, database + "." + table, replaced);
        } finally {
            if (!published) {
                if (publishCreated) dropQuietly(target, database, publishName);
                if (stageCreated) dropQuietly(target, database, stageName);
            }
        }
    }

    public static IncrementalPublishResult publishIncremental(Connection target, WarehousePublisher.Engine engine,
            ResultSet sourceRows, String database, String table, long runId, int batchSize, int maxRows,
            String watermarkColumn, List<String> keyColumns) throws SQLException {
        requireMysqlProtocolEngine(engine);
        requireIdentifier(database, 64, "目标数据库");
        requireIdentifier(table, 128, "目标表");
        if (runId < 1 || batchSize < 1 || batchSize > DEFAULT_BATCH_SIZE || maxRows < 1 || maxRows > MAX_BATCH_ROWS)
            throw new IllegalArgumentException("增量入仓运行参数无效");
        requireIdentifier(watermarkColumn, 128, "水位字段");
        if (keyColumns == null || keyColumns.isEmpty() || keyColumns.size() > 16)
            throw new IllegalArgumentException("业务键须包含 1～16 个字段");

        List<Column> columns = sourceColumns(sourceRows.getMetaData());
        MapPositions positions = positionsOf(columns);
        Integer watermarkPosition = positions.byLower.get(watermarkColumn.toLowerCase(Locale.ROOT));
        if (watermarkPosition == null) throw new IllegalArgumentException("水位字段不存在于源表");
        Column watermark = columns.get(watermarkPosition);
        if (watermark.nullable() || !isOrderableWatermark(watermark.jdbcType()))
            throw new IllegalArgumentException("水位字段必须为非空数字或日期时间类型");
        List<String> normalizedKeys = normalizeKeys(keyColumns, columns, positions);

        String stageName = "__dp_stage_" + runId;
        String publishName = "__dp_publish_" + runId;
        boolean stageCreated = false;
        boolean publishCreated = false;
        boolean published = false;
        try {
            ensureDatabase(target, database);
            createOlapTable(target, engine, database, stageName, columns);
            stageCreated = true;
            String columnList = columns.stream().map(c -> quote(c.name())).reduce((a, b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + q(database, stageName) + " (" + columnList + ") VALUES ("
                    + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            InsertResult inserted = insertRows(target, sourceRows, columns, insertSql, batchSize, maxRows, watermarkPosition + 1);
            long rows = inserted.rows();
            if (rows == 0)
                return new IncrementalPublishResult(0, database + "." + table, tableExists(target, database, table), null);
            if (countRows(target, database, stageName) != rows)
                throw new IllegalStateException("增量暂存数据行数校验失败，未替换目标表");
            assertUniqueKeys(target, database, stageName, normalizedKeys);
            assertNoNullKeys(target, database, stageName, normalizedKeys);

            boolean replaced = tableExists(target, database, table);
            if (replaced) {
                ensureCompatibleExistingTable(target, database, table, columns);
                createOlapTable(target, engine, database, publishName, columns);
                publishCreated = true;
                String keyTuple = keyTuple(normalizedKeys);
                try (Statement statement = target.createStatement()) {
                    statement.execute("INSERT INTO " + q(database, publishName) + " (" + columnList + ") SELECT "
                            + columnList + " FROM " + q(database, table) + " WHERE (" + keyTuple
                            + ") NOT IN (SELECT " + keyTuple + " FROM " + q(database, stageName) + ")");
                    statement.execute("INSERT INTO " + q(database, publishName) + " (" + columnList + ") SELECT "
                            + columnList + " FROM " + q(database, stageName));
                    long expected;
                    try (ResultSet expectedRs = statement.executeQuery(
                            "SELECT COUNT(*) FROM " + q(database, table) + " WHERE (" + keyTuple
                                    + ") NOT IN (SELECT " + keyTuple + " FROM " + q(database, stageName) + ")")) {
                        if (!expectedRs.next()) throw new IllegalStateException("增量合并行数校验失败，未替换目标表");
                        expected = expectedRs.getLong(1) + rows;
                    }
                    if (countRows(target, database, publishName) != expected)
                        throw new IllegalStateException("增量合并行数校验失败，未替换目标表");
                }
                promote(target, engine, database, table, publishName, true);
                published = true;
                dropQuietly(target, database, stageName);
                dropQuietly(target, database, publishName);
            } else {
                renameTable(target, engine, database, stageName, table);
                published = true;
            }
            return new IncrementalPublishResult(rows, database + "." + table, replaced, inserted.lastWatermark());
        } finally {
            if (!published) {
                if (publishCreated) dropQuietly(target, database, publishName);
                if (stageCreated) dropQuietly(target, database, stageName);
            }
        }
    }

    public static void dropRunStage(Connection target, WarehousePublisher.Engine engine, String database, long runId)
            throws SQLException {
        requireMysqlProtocolEngine(engine);
        requireIdentifier(database, 64, "目标数据库");
        if (runId < 1) throw new IllegalArgumentException("入仓运行 ID 无效");
        dropQuietly(target, database, "__dp_stage_" + runId);
        dropQuietly(target, database, "__dp_publish_" + runId);
        dropQuietly(target, database, "__dp_model_stage_" + runId);
        dropQuietly(target, database, "__dp_old_" + runId);
    }

    @FunctionalInterface
    private interface RowInserter {
        long insert(Connection target, List<Column> columns, String insertSql, int batchSize, int maxRows) throws SQLException;
    }

    private static PublishResult publishPrepared(Connection target, WarehousePublisher.Engine engine, String database,
            String table, long runId, int batchSize, int maxRows, List<Column> columns, RowInserter inserter)
            throws SQLException {
        return publishPrepared(target, engine, database, table, runId, batchSize, maxRows, columns, inserter, false);
    }

    private static PublishResult publishPrepared(Connection target, WarehousePublisher.Engine engine, String database,
            String table, long runId, int batchSize, int maxRows, List<Column> columns, RowInserter inserter,
            boolean append) throws SQLException {
        requireMysqlProtocolEngine(engine);
        requireIdentifier(database, 64, "目标数据库");
        requireIdentifier(table, 128, "目标表");
        if (table.startsWith("__dp_stage_") || table.startsWith("system"))
            throw new IllegalArgumentException("目标表名保留或无效");
        if (runId < 1) throw new IllegalArgumentException("入仓运行 ID 无效");
        if (batchSize < 1 || batchSize > DEFAULT_BATCH_SIZE) throw new IllegalArgumentException("批量行数须在 1～10000 之间");
        if (maxRows < 1 || maxRows > MAX_BATCH_ROWS) throw new IllegalArgumentException("单次入仓上限须在 1～1000000 行之间");

        String stage = "__dp_stage_" + runId;
        boolean stageCreated = false;
        boolean published = false;
        try {
            ensureDatabase(target, database);
            createOlapTable(target, engine, database, stage, columns);
            stageCreated = true;
            String columnList = columns.stream().map(c -> quote(c.name())).reduce((a, b) -> a + "," + b).orElseThrow();
            String insertSql = "INSERT INTO " + q(database, stage) + " (" + columnList
                    + ") VALUES (" + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            long rows = inserter.insert(target, columns, insertSql, batchSize, maxRows);
            if (countRows(target, database, stage) != rows)
                throw new IllegalStateException("暂存数据行数校验失败，未替换目标表");
            ensureCompatibleExistingTable(target, database, table, columns);
            boolean targetExists = tableExists(target, database, table);
            if (append && targetExists) {
                try (Statement statement = target.createStatement()) {
                    statement.execute("INSERT INTO " + q(database, table) + " (" + columnList + ") SELECT "
                            + columnList + " FROM " + q(database, stage));
                }
                published = true;
                dropQuietly(target, database, stage);
                return new PublishResult(rows, database + "." + table, false);
            }
            if (targetExists) {
                promote(target, engine, database, table, stage, true);
                published = true;
                dropQuietly(target, database, stage);
            } else {
                renameTable(target, engine, database, stage, table);
                published = true;
            }
            return new PublishResult(rows, database + "." + table, targetExists && !append);
        } finally {
            if (stageCreated && !published) dropQuietly(target, database, stage);
        }
    }

    private static void createOlapTable(Connection target, WarehousePublisher.Engine engine, String database,
            String table, List<Column> columns) throws SQLException {
        if (columns.isEmpty()) throw new IllegalArgumentException("发布字段不能为空");
        String key = quote(columns.get(0).name());
        String definitions = columns.stream()
                .map(c -> quote(c.name()) + " " + mysqlType(c))
                .reduce((a, b) -> a + "," + b).orElseThrow();
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(q(database, table)).append(" (").append(definitions).append(") ");
        if (engine == WarehousePublisher.Engine.DORIS) ddl.append("ENGINE=OLAP ");
        ddl.append("DUPLICATE KEY(").append(key).append(") ");
        ddl.append("DISTRIBUTED BY HASH(").append(key).append(") BUCKETS 1 ");
        ddl.append("PROPERTIES(\"replication_num\"=\"1\")");
        try (Statement statement = target.createStatement()) {
            statement.execute(ddl.toString());
        }
    }

    /** Visible for unit tests. */
    static String mysqlType(Column column) {
        String type = switch (column.jdbcType()) {
            case Types.TINYINT -> "TINYINT";
            case Types.SMALLINT -> "SMALLINT";
            case Types.INTEGER -> "INT";
            case Types.BIGINT -> "BIGINT";
            case Types.NUMERIC, Types.DECIMAL -> decimal(column.precision(), column.scale());
            case Types.FLOAT, Types.REAL -> "FLOAT";
            case Types.DOUBLE -> "DOUBLE";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
            case Types.DATE -> "DATE";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "DATETIME";
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB, Types.OTHER -> "VARCHAR(65533)";
            default -> throw new IllegalArgumentException("暂不支持源字段 JDBC 类型：" + column.jdbcType());
        };
        return column.nullable() ? type + " NULL" : type + " NOT NULL";
    }

    private static String decimal(int precision, int scale) {
        int p = precision < 1 ? 38 : Math.min(precision, 38);
        int s = Math.max(0, Math.min(scale < 0 ? 0 : scale, p));
        return "DECIMAL(" + p + "," + s + ")";
    }

    private static void promote(Connection target, WarehousePublisher.Engine engine, String database,
            String liveTable, String candidate, boolean liveExists) throws SQLException {
        if (!liveExists) {
            renameTable(target, engine, database, candidate, liveTable);
            return;
        }
        try (Statement statement = target.createStatement()) {
            if (engine == WarehousePublisher.Engine.DORIS) {
                statement.execute("ALTER TABLE " + q(database, liveTable) + " REPLACE WITH TABLE " + quote(candidate)
                        + " PROPERTIES(\"swap\"=\"false\")");
                return;
            }
            // StarRocks: prefer SWAP, fall back to rename chain.
            try {
                statement.execute("ALTER TABLE " + q(database, liveTable) + " SWAP WITH " + quote(candidate));
                return;
            } catch (SQLException swapFailed) {
                String oldName = "__dp_old_" + Math.abs(candidate.hashCode());
                try {
                    statement.execute("ALTER TABLE " + q(database, liveTable) + " RENAME " + quote(oldName));
                    statement.execute("ALTER TABLE " + q(database, candidate) + " RENAME " + quote(liveTable));
                    dropQuietly(target, database, oldName);
                } catch (SQLException renameFailed) {
                    renameFailed.addSuppressed(swapFailed);
                    throw renameFailed;
                }
            }
        }
    }

    private static void renameTable(Connection target, WarehousePublisher.Engine engine, String database,
            String from, String to) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("ALTER TABLE " + q(database, from) + " RENAME " + quote(to));
        }
    }

    private static void ensureDatabase(Connection target, String database) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Doris/StarRocks 数仓目标库不存在：" + database);
            }
        }
    }

    private static void ensureCompatibleExistingTable(Connection target, String database, String table,
            List<Column> sourceColumns) throws SQLException {
        if (!tableExists(target, database, table)) return;
        List<String> actual = new ArrayList<>();
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) actual.add(rs.getString(1));
            }
        }
        if (actual.size() != sourceColumns.size())
            throw new IllegalArgumentException("源表结构已变化，目标表未自动替换；请先评审结构迁移");
        for (int i = 0; i < sourceColumns.size(); i++) {
            if (!sourceColumns.get(i).name().equalsIgnoreCase(actual.get(i)))
                throw new IllegalArgumentException("源表结构已变化，目标表未自动替换；请先评审结构迁移");
        }
    }

    private static boolean tableExists(Connection target, String database, String table) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=? LIMIT 1")) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static long countRows(Connection target, String database, String table) throws SQLException {
        try (Statement statement = target.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + q(database, table))) {
            if (!rows.next()) throw new SQLException("staging row count unavailable");
            return rows.getLong(1);
        }
    }

    private static void assertUniqueKeys(Connection target, String database, String table, List<String> keys)
            throws SQLException {
        String keyTuple = keyTuple(keys);
        String sql = "SELECT COUNT(*) FROM (SELECT " + keyTuple + " FROM " + q(database, table)
                + " GROUP BY " + keyTuple + " HAVING COUNT(*) > 1) dup";
        try (Statement statement = target.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next() || rs.getLong(1) > 0)
                throw new IllegalArgumentException("增量批次包含重复业务键，请先修正源数据");
        }
    }

    private static void assertNoNullKeys(Connection target, String database, String table, List<String> keys)
            throws SQLException {
        String predicate = keys.stream().map(k -> quote(k) + " IS NULL").reduce((a, b) -> a + " OR " + b).orElseThrow();
        try (Statement statement = target.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + q(database, table) + " WHERE " + predicate)) {
            if (!rs.next() || rs.getLong(1) > 0)
                throw new IllegalArgumentException("增量批次的业务键含空值");
        }
    }

    private static String keyTuple(List<String> keys) {
        return keys.stream().map(MysqlProtocolBatchPublisher::quote).reduce((a, b) -> a + "," + b).orElseThrow();
    }

    private static List<String> normalizeKeys(List<String> keyColumns, List<Column> columns, MapPositions positions) {
        List<String> normalizedKeys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String key : keyColumns) {
            requireIdentifier(key, 128, "业务键字段");
            Integer position = positions.byLower.get(key.toLowerCase(Locale.ROOT));
            if (position == null) throw new IllegalArgumentException("业务键字段不存在于源表");
            Column column = columns.get(position);
            if (column.nullable()) throw new IllegalArgumentException("业务键字段必须定义为非空");
            if (!seen.add(column.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("业务键字段重复");
            normalizedKeys.add(column.name());
        }
        return normalizedKeys;
    }

    private record MapPositions(java.util.Map<String, Integer> byLower) {}

    private static MapPositions positionsOf(List<Column> columns) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < columns.size(); i++) map.put(columns.get(i).name().toLowerCase(Locale.ROOT), i);
        return new MapPositions(map);
    }

    private static void dropQuietly(Connection target, String database, String table) {
        try (Statement statement = target.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + q(database, table));
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    private record InsertResult(long rows, String lastWatermark) {}

    private static InsertResult insertRows(Connection target, ResultSet source, List<Column> columns,
            String sql, int batchSize, int maxRows, int watermarkPosition) throws SQLException {
        long rows = 0;
        String lastWatermark = null;
        try (PreparedStatement insert = target.prepareStatement(sql)) {
            while (source.next()) {
                if (++rows > maxRows) throw new IllegalArgumentException("源表超过本次入仓行数上限");
                if (watermarkPosition > 0) {
                    Object watermark = source.getObject(watermarkPosition);
                    if (watermark == null) throw new IllegalArgumentException("水位字段数据不能含空值");
                    lastWatermark = watermark.toString();
                }
                for (int index = 0; index < columns.size(); index++) {
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

    private static long insertMapRows(Connection target, List<Column> columns, List<Map<String, Object>> rows,
            String sql, int batchSize, int maxRows) throws SQLException {
        if (rows.size() > maxRows) throw new IllegalArgumentException("源表超过本次入仓行数上限");
        long count = 0;
        try (PreparedStatement insert = target.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                count++;
                for (int index = 0; index < columns.size(); index++) {
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

    private static List<Column> sourceColumns(ResultSetMetaData metadata) throws SQLException {
        int count = metadata.getColumnCount();
        if (count < 1 || count > 500) throw new IllegalArgumentException("源字段数量须在 1～500 之间");
        List<Column> columns = new ArrayList<>(count);
        Set<String> names = new HashSet<>();
        for (int index = 1; index <= count; index++) {
            String name = metadata.getColumnLabel(index);
            requireColumnName(name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("源字段名重复，无法安全入仓");
            Column column = new Column(name, metadata.getColumnTypeName(index), metadata.getColumnType(index),
                    metadata.getPrecision(index), metadata.getScale(index),
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls);
            mysqlType(column);
            columns.add(column);
        }
        return List.copyOf(columns);
    }

    private static List<Column> mapColumns(List<String> columnNames, List<Map<String, Object>> rows) {
        if (columnNames.size() > 500) throw new IllegalArgumentException("源字段数量须在 1～500 之间");
        Set<String> names = new HashSet<>();
        List<Column> columns = new ArrayList<>(columnNames.size());
        for (String name : columnNames) {
            requireColumnName(name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("源字段名重复，无法安全入仓");
            Integer jdbcType = null;
            boolean nullable = rows.isEmpty();
            for (Map<String, Object> row : rows) {
                Object value = row.get(name);
                if (value == null) { nullable = true; continue; }
                int sample = jdbcTypeOf(value);
                jdbcType = jdbcType == null ? sample : (jdbcType == sample ? jdbcType : Types.VARCHAR);
            }
            int resolved = jdbcType == null ? Types.VARCHAR : jdbcType;
            columns.add(new Column(name, "PIPELINE", resolved, resolved == Types.DECIMAL ? 38 : 0,
                    resolved == Types.DECIMAL ? 12 : 0, nullable || rows.isEmpty()));
        }
        return List.copyOf(columns);
    }

    private static int jdbcTypeOf(Object value) {
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
        return Types.VARCHAR;
    }

    private static boolean isOrderableWatermark(int type) {
        return switch (type) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
                    Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> true;
            default -> false;
        };
    }

    private static void requireMysqlProtocolEngine(WarehousePublisher.Engine engine) {
        if (engine != WarehousePublisher.Engine.DORIS && engine != WarehousePublisher.Engine.STARROCKS)
            throw new IllegalArgumentException("MysqlProtocolBatchPublisher 仅支持 Doris/StarRocks");
    }

    private static void requireColumnName(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("源字段名无效");
    }

    private static void requireIdentifier(String value, int max, String label) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException(label + "标识符无效");
    }

    private static String quote(String value) { return "`" + value.replace("`", "``") + "`"; }

    private static String q(String database, String table) { return quote(database) + "." + quote(table); }
}
