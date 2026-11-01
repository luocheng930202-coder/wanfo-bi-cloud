package com.wanfo.bi.datasource;

import java.util.List;

/** Server-owned catalog of supported source types and their platform capabilities. */
public final class ConnectorRegistry
{
    private ConnectorRegistry() { }

    private static final List<Category> CATEGORIES = List.of(
        new Category("oltp", "OLTP"),
        new Category("olap", "OLAP"),
        new Category("lake", "数据湖"),
        new Category("api", "API 数据"),
        new Category("file", "文件")
    );

    private static final List<Connector> CONNECTORS = List.of(
        new Connector("mysql", "MySQL", "oltp", "My", "#00758f", "关系型数据库", "jdbc:mysql://host:3306/database", List.of("query", "schema", "table", "column")),
        new Connector("postgresql", "PostgreSQL", "oltp", "PG", "#336791", "关系型数据库", "jdbc:postgresql://host:5432/database", List.of("query", "schema", "table", "column")),
        new Connector("oracle", "Oracle", "oltp", "Or", "#c74634", "关系型数据库", "jdbc:oracle:thin:@//host:1521/service", List.of("query", "schema", "table", "column")),
        new Connector("sqlserver", "SQL Server", "oltp", "SQL", "#b52d36", "关系型数据库", "jdbc:sqlserver://host:1433;databaseName=database", List.of("query", "schema", "table", "column")),
        new Connector("doris", "Doris", "olap", "Do", "#2876df", "分析仓目标 · REPLACE/MERGE 入仓", "jdbc:mysql://host:9030/database", List.of("query", "schema", "table", "column", "warehouse-replace", "warehouse-merge")),
        new Connector("starrocks", "StarRocks", "olap", "SR", "#6849c9", "分析仓目标 · REPLACE/MERGE 入仓", "jdbc:mysql://host:9030/database", List.of("query", "schema", "table", "column", "warehouse-replace", "warehouse-merge")),
        new Connector("clickhouse", "ClickHouse", "olap", "CH", "#997400", "列式分析数据库", "jdbc:clickhouse://host:8123/database", List.of("query", "schema", "table", "column")),
        new Connector("api", "JSON API", "api", "API", "#2876df", "GET JSON · 可刷新快照", "", List.of("snapshot", "pagination", "oauth", "array-expand")),
        new Connector("csv", "CSV", "file", "CSV", "#248451", "UTF-8 · 保留标识符前导零", "", List.of("snapshot", "preview", "append", "replace")),
        new Connector("excel", "Excel", "file", "XLS", "#248451", "XLS / XLSX · 工作表导入", "", List.of("snapshot", "preview", "append", "replace")),
        new Connector("s3", "S3 Compatible", "lake", "S3", "#d4720d", "对象存储 CSV/Parquet 批入仓 · host=endpoint schema=bucket", "https://s3.example.com", List.of("object-list", "csv-ingest", "parquet-ingest")),
        new Connector("oss", "Aliyun OSS", "lake", "OSS", "#ff6a00", "OSS S3 兼容协议 · host=endpoint schema=bucket", "https://oss-cn-hangzhou.aliyuncs.com", List.of("object-list", "csv-ingest", "parquet-ingest")),
        new Connector("minio", "MinIO", "lake", "MI", "#c72e49", "MinIO / 私有对象存储 CSV/Parquet", "http://minio:9000", List.of("object-list", "csv-ingest", "parquet-ingest"))
    );

    public static Catalog catalog()
    {
        return new Catalog(CATEGORIES, CONNECTORS);
    }

    public static Connector find(String type)
    {
        if (type == null) return null;
        return CONNECTORS.stream().filter(connector -> connector.value().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public record Category(String value, String label) { }

    public record Connector(String value, String label, String category, String abbr, String color,
                            String description, String url, List<String> capabilities) { }

    public record Catalog(List<Category> categories, List<Connector> connectors) { }
}
