SET NOCOUNT ON;
IF DB_ID(N'wanfo_bi_sql') IS NULL CREATE DATABASE wanfo_bi_sql;
GO
IF SUSER_ID(N'bi_reader') IS NULL
    CREATE LOGIN bi_reader WITH PASSWORD = '$(ReaderPassword)', CHECK_POLICY = ON;
GO
USE wanfo_bi_sql;
GO
IF USER_ID(N'bi_reader') IS NULL CREATE USER bi_reader FOR LOGIN bi_reader;
IF SCHEMA_ID(N'BI_TEST') IS NULL EXEC(N'CREATE SCHEMA BI_TEST AUTHORIZATION dbo');
IF OBJECT_ID(N'BI_TEST.sales_demo', N'U') IS NOT NULL DROP TABLE BI_TEST.sales_demo;
CREATE TABLE BI_TEST.sales_demo (
    id bigint NOT NULL PRIMARY KEY,
    order_date date NOT NULL,
    event_time datetime2(3) NOT NULL,
    province nvarchar(40) NOT NULL,
    city nvarchar(40) NOT NULL,
    district nvarchar(40) NOT NULL,
    channel nvarchar(32) NOT NULL,
    amount decimal(18,2) NOT NULL,
    quantity int NOT NULL,
    optional_note nvarchar(80) NULL
);
WITH rows AS (
    SELECT 1 AS n
    UNION ALL SELECT n + 1 FROM rows WHERE n < 40
)
INSERT INTO BI_TEST.sales_demo (id, order_date, event_time, province, city, district, channel, amount, quantity, optional_note)
SELECT n,
       DATEADD(day, n - 1, CONVERT(date, '2026-10-01')),
       DATEADD(millisecond, n * 125, CONVERT(datetime2(3), '2026-10-01T08:00:00')),
       CASE WHEN n % 2 = 0 THEN N'江苏省' ELSE N'浙江省' END,
       CASE WHEN n % 2 = 0 THEN N'南京市' ELSE N'杭州市' END,
       CASE WHEN n % 2 = 0 THEN N'玄武区' ELSE N'西湖区' END,
       CASE WHEN n % 3 = 0 THEN N'线上' ELSE N'门店' END,
       CAST(n * 125.25 AS decimal(18,2)),
       n % 5 + 1,
       CASE WHEN n % 7 = 0 THEN NULL ELSE CONCAT(N'样例-', n) END
FROM rows
OPTION (MAXRECURSION 0);
GRANT SELECT ON SCHEMA::BI_TEST TO bi_reader;
SELECT DB_NAME() AS database_name, SCHEMA_NAME() AS schema_name, COUNT(*) AS row_count FROM BI_TEST.sales_demo;
GO
