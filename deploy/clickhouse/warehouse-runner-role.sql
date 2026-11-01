-- Least-privilege role for the local DP acceptance ClickHouse datasource.
-- Apply with a local ClickHouse administrator; create a separate user/password
-- outside this repository, then grant this role to that user.
-- No production hosts, users, passwords, or broad ALL grants are included.

CREATE ROLE IF NOT EXISTS dp_warehouse_runner;

-- Metadata discovery required by JDBC; this grants no access to business rows.
GRANT SELECT ON system.columns TO dp_warehouse_runner;
GRANT SELECT ON system.databases TO dp_warehouse_runner;
GRANT SELECT ON system.settings TO dp_warehouse_runner;
GRANT SELECT ON system.tables TO dp_warehouse_runner;

-- Bounded ingestion destination: create/publish/drop only within the ODS layer.
GRANT SHOW TABLES, SELECT, INSERT, CREATE TABLE, DROP TABLE, ALTER TABLE
    ON wanfo_wh_ods.* TO dp_warehouse_runner;

-- Pipeline WAREHOUSE_PUBLISH writes pipe_* into DWD / DWS / ADS (REPLACE or APPEND).
GRANT SHOW TABLES, SELECT, INSERT, CREATE TABLE, DROP TABLE, ALTER TABLE
    ON wanfo_wh_dwd.* TO dp_warehouse_runner;
GRANT SHOW TABLES, SELECT, INSERT, CREATE TABLE, DROP TABLE, ALTER TABLE
    ON wanfo_wh_dws.* TO dp_warehouse_runner;
GRANT SHOW TABLES, SELECT, INSERT, CREATE TABLE, DROP TABLE, ALTER TABLE
    ON wanfo_wh_ads.* TO dp_warehouse_runner;
