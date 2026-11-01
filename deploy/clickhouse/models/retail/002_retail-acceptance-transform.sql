-- One-time/idempotent mapping from the local PostgreSQL acceptance fixture in
-- deploy/postgres/retail-acceptance-fixture.sql into the retail-v1 DWD tables.
-- Run only after the three dp_acceptance_* tables have been ingested into ODS.
-- This script inserts missing business keys only; it does not delete or overwrite data.

INSERT INTO wanfo_wh_dwd.dim_retail_org
    (tenant_id, org_id, parent_org_id, org_level, org_code, org_name, province_code,
     city_code, county_code, valid_from, valid_to, is_current, source_system,
     source_record_id, source_updated_at, ingested_at, load_run_id)
SELECT s.tenant_id, s.org_id, s.parent_org_id, toUInt8(s.org_level), s.org_code, s.org_name,
       s.province_code, s.city_code, s.county_code, s.valid_from, s.valid_to,
       toUInt8(s.is_current), 'postgresql:dp_acceptance', s.source_record_id,
       s.source_updated_at, now64(3, 'UTC'), toUInt64(0)
FROM wanfo_wh_ods.dp_acceptance_retail_org AS s
WHERE NOT EXISTS (
    SELECT 1 FROM wanfo_wh_dwd.dim_retail_org AS d
    WHERE d.tenant_id=s.tenant_id AND d.org_id=s.org_id AND d.valid_from=s.valid_from
);

INSERT INTO wanfo_wh_dwd.dim_retail_product
    (tenant_id, product_id, product_name, category_id, category_name, list_price,
     valid_from, valid_to, is_current, source_system, source_record_id,
     source_updated_at, ingested_at, load_run_id)
SELECT s.tenant_id, s.product_id, s.product_name, s.category_id, s.category_name,
       s.list_price, s.valid_from, s.valid_to, toUInt8(s.is_current),
       'postgresql:dp_acceptance', s.source_record_id, s.source_updated_at,
       now64(3, 'UTC'), toUInt64(0)
FROM wanfo_wh_ods.dp_acceptance_retail_product AS s
WHERE NOT EXISTS (
    SELECT 1 FROM wanfo_wh_dwd.dim_retail_product AS d
    WHERE d.tenant_id=s.tenant_id AND d.product_id=s.product_id AND d.valid_from=s.valid_from
);

INSERT INTO wanfo_wh_dwd.fact_retail_order_line
    (tenant_id, order_id, line_no, order_date, order_at, store_id, product_id, org_id,
     province_code, city_code, county_code, channel, quantity, sales_amount, currency_code,
     source_system, source_updated_at, ingested_at, load_run_id, is_deleted)
SELECT s.tenant_id, s.order_id, toUInt32(s.line_no), s.order_date, s.order_at, s.store_id,
       s.product_id, s.org_id, s.province_code, s.city_code, s.county_code, s.channel,
       s.quantity, s.sales_amount, s.currency_code, 'postgresql:dp_acceptance',
       s.source_updated_at, now64(3, 'UTC'), toUInt64(0), toUInt8(s.is_deleted)
FROM wanfo_wh_ods.dp_acceptance_retail_order_line AS s
WHERE NOT EXISTS (
    SELECT 1 FROM wanfo_wh_dwd.fact_retail_order_line AS d
    WHERE d.tenant_id=s.tenant_id AND d.order_id=s.order_id AND d.line_no=s.line_no
);

-- Acceptance summary; this returns counts only, never fact rows.
SELECT 'dwd_org_rows' AS check_name, count() AS row_count
FROM wanfo_wh_dwd.dim_retail_org WHERE tenant_id='wanfo_demo'
UNION ALL
SELECT 'dwd_product_rows', count()
FROM wanfo_wh_dwd.dim_retail_product WHERE tenant_id='wanfo_demo'
UNION ALL
SELECT 'dwd_order_line_rows', count()
FROM wanfo_wh_dwd.fact_retail_order_line WHERE tenant_id='wanfo_demo';
