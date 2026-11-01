-- Empty physical model definitions for the synthetic retail pilot.
-- This file creates no users/grants and inserts no rows. Apply only to a reviewed dev/acceptance target.

CREATE TABLE IF NOT EXISTS wanfo_wh_dwd.dim_retail_org
(
    tenant_id String,
    org_id String,
    parent_org_id Nullable(String),
    org_level UInt8,
    org_code String,
    org_name String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    valid_from DateTime64(3, 'UTC'),
    valid_to Nullable(DateTime64(3, 'UTC')),
    is_current UInt8,
    source_system LowCardinality(String),
    source_record_id String,
    source_updated_at Nullable(DateTime64(3, 'UTC')),
    ingested_at DateTime64(3, 'UTC'),
    load_run_id UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(valid_from)
ORDER BY (tenant_id, org_id, valid_from);

CREATE TABLE IF NOT EXISTS wanfo_wh_dwd.dim_retail_store
(
    tenant_id String,
    store_id String,
    store_name String,
    org_id String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    province_name String,
    city_name Nullable(String),
    county_name Nullable(String),
    valid_from DateTime64(3, 'UTC'),
    valid_to Nullable(DateTime64(3, 'UTC')),
    is_current UInt8,
    source_system LowCardinality(String),
    source_record_id String,
    source_updated_at Nullable(DateTime64(3, 'UTC')),
    ingested_at DateTime64(3, 'UTC'),
    load_run_id UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(valid_from)
ORDER BY (tenant_id, org_id, store_id, valid_from);

CREATE TABLE IF NOT EXISTS wanfo_wh_dwd.dim_retail_product
(
    tenant_id String,
    product_id String,
    product_name String,
    category_id Nullable(String),
    category_name Nullable(String),
    list_price Nullable(Decimal(18, 2)),
    valid_from DateTime64(3, 'UTC'),
    valid_to Nullable(DateTime64(3, 'UTC')),
    is_current UInt8,
    source_system LowCardinality(String),
    source_record_id String,
    source_updated_at Nullable(DateTime64(3, 'UTC')),
    ingested_at DateTime64(3, 'UTC'),
    load_run_id UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(valid_from)
ORDER BY (tenant_id, product_id, valid_from);

CREATE TABLE IF NOT EXISTS wanfo_wh_dwd.fact_retail_order_line
(
    tenant_id String,
    order_id String,
    line_no UInt32,
    order_date Date,
    order_at Nullable(DateTime64(3, 'UTC')),
    store_id String,
    product_id String,
    org_id String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    channel LowCardinality(String),
    quantity Decimal(18, 3),
    sales_amount Decimal(18, 2),
    currency_code LowCardinality(String),
    source_system LowCardinality(String),
    source_updated_at DateTime64(3, 'UTC'),
    ingested_at DateTime64(3, 'UTC'),
    load_run_id UInt64,
    is_deleted UInt8
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(order_date)
ORDER BY (tenant_id, org_id, order_date, order_id, line_no);

CREATE TABLE IF NOT EXISTS wanfo_wh_dws.agg_retail_sales_monthly
(
    tenant_id String,
    month_start Date,
    org_id String,
    province_code String,
    city_code Nullable(String),
    county_code Nullable(String),
    channel LowCardinality(String),
    category_id Nullable(String),
    sales_amount Decimal(20, 2),
    net_quantity Decimal(20, 3),
    order_count UInt64,
    source_max_updated_at Nullable(DateTime64(3, 'UTC')),
    generated_at DateTime64(3, 'UTC'),
    load_run_id UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(month_start)
ORDER BY (tenant_id, org_id, month_start, channel, ifNull(category_id, ''));
