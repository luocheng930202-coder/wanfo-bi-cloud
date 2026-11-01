-- ODS landing tables for the local PostgreSQL acceptance fixture.
-- The platform ingestion service normally infers these schemas from JDBC metadata.
-- This explicit DDL supports local data-path acceptance without changing app config.

CREATE TABLE IF NOT EXISTS wanfo_wh_ods.dp_acceptance_retail_org
(
    tenant_id String,
    org_id String,
    parent_org_id Nullable(String),
    org_level Int16,
    org_code String,
    org_name String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    valid_from DateTime64(6, 'UTC'),
    valid_to Nullable(DateTime64(6, 'UTC')),
    is_current Int16,
    source_record_id String,
    source_updated_at Nullable(DateTime64(6, 'UTC'))
)
ENGINE = MergeTree
ORDER BY (tenant_id, org_id, valid_from);

CREATE TABLE IF NOT EXISTS wanfo_wh_ods.dp_acceptance_retail_product
(
    tenant_id String,
    product_id String,
    product_name String,
    category_id Nullable(String),
    category_name Nullable(String),
    list_price Nullable(Decimal(18, 2)),
    valid_from DateTime64(6, 'UTC'),
    valid_to Nullable(DateTime64(6, 'UTC')),
    is_current Int16,
    source_record_id String,
    source_updated_at Nullable(DateTime64(6, 'UTC'))
)
ENGINE = MergeTree
ORDER BY (tenant_id, product_id, valid_from);

CREATE TABLE IF NOT EXISTS wanfo_wh_ods.dp_acceptance_retail_order_line
(
    tenant_id String,
    order_id String,
    line_no Int32,
    order_date Date,
    order_at Nullable(DateTime64(6, 'UTC')),
    store_id String,
    product_id String,
    org_id String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    channel String,
    quantity Decimal(18, 3),
    sales_amount Decimal(18, 2),
    currency_code String,
    source_updated_at DateTime64(6, 'UTC'),
    is_deleted Int16
)
ENGINE = MergeTree
ORDER BY (tenant_id, order_id, line_no);
