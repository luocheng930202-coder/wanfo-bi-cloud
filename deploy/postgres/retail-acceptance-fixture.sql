-- Synthetic PostgreSQL source fixtures for DP-037..DP-040 local acceptance.
-- This script creates only dp_acceptance_* tables under the configured analytics schema.
-- It contains no production data and is safe to re-run; seeded rows are upserted by key.

CREATE TABLE IF NOT EXISTS analytics.dp_acceptance_retail_org (
    tenant_id varchar(40) NOT NULL,
    org_id varchar(40) NOT NULL,
    parent_org_id varchar(40),
    org_level smallint NOT NULL,
    org_code varchar(20) NOT NULL,
    org_name varchar(100) NOT NULL,
    province_code varchar(20) NOT NULL,
    city_code varchar(20),
    county_code varchar(20),
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    is_current smallint NOT NULL,
    source_record_id varchar(80) NOT NULL,
    source_updated_at timestamptz,
    PRIMARY KEY (tenant_id, org_id, valid_from)
);

CREATE TABLE IF NOT EXISTS analytics.dp_acceptance_retail_product (
    tenant_id varchar(40) NOT NULL,
    product_id varchar(40) NOT NULL,
    product_name varchar(100) NOT NULL,
    category_id varchar(40),
    category_name varchar(100),
    list_price numeric(18,2),
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    is_current smallint NOT NULL,
    source_record_id varchar(80) NOT NULL,
    source_updated_at timestamptz,
    PRIMARY KEY (tenant_id, product_id, valid_from)
);

CREATE TABLE IF NOT EXISTS analytics.dp_acceptance_retail_order_line (
    tenant_id varchar(40) NOT NULL,
    order_id varchar(40) NOT NULL,
    line_no integer NOT NULL,
    order_date date NOT NULL,
    order_at timestamptz,
    store_id varchar(40) NOT NULL,
    product_id varchar(40) NOT NULL,
    org_id varchar(40) NOT NULL,
    province_code varchar(20) NOT NULL,
    city_code varchar(20),
    county_code varchar(20),
    channel varchar(30) NOT NULL,
    quantity numeric(18,3) NOT NULL,
    sales_amount numeric(18,2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    source_updated_at timestamptz NOT NULL,
    is_deleted smallint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, order_id, line_no)
);

INSERT INTO analytics.dp_acceptance_retail_org
    (tenant_id, org_id, parent_org_id, org_level, org_code, org_name, province_code, city_code,
     county_code, valid_from, valid_to, is_current, source_record_id, source_updated_at)
VALUES
    ('wanfo_demo', 'org-p11', NULL, 1, 'P11', '东岭省', 'P11', NULL, NULL, '2020-01-01 00:00:00+08', NULL, 1, 'org-p11-v1', now()),
    ('wanfo_demo', 'org-c1101', 'org-p11', 2, 'C1101', '东岭市', 'P11', 'C1101', NULL, '2020-01-01 00:00:00+08', NULL, 1, 'org-c1101-v1', now()),
    ('wanfo_demo', 'org-x110101', 'org-c1101', 3, 'X110101', '滨河区', 'P11', 'C1101', 'X110101', '2020-01-01 00:00:00+08', NULL, 1, 'org-x110101-v1', now()),
    ('wanfo_demo', 'org-x110102', 'org-c1101', 3, 'X110102', '新城区', 'P11', 'C1101', 'X110102', '2020-01-01 00:00:00+08', NULL, 1, 'org-x110102-v1', now()),
    ('wanfo_demo', 'org-p22', NULL, 1, 'P22', '南川省', 'P22', NULL, NULL, '2020-01-01 00:00:00+08', NULL, 1, 'org-p22-v1', now()),
    ('wanfo_demo', 'org-c2201', 'org-p22', 2, 'C2201', '南川市', 'P22', 'C2201', NULL, '2020-01-01 00:00:00+08', NULL, 1, 'org-c2201-v1', now()),
    ('wanfo_demo', 'org-x220101', 'org-c2201', 3, 'X220101', '江南区', 'P22', 'C2201', 'X220101', '2020-01-01 00:00:00+08', NULL, 1, 'org-x220101-v1', now()),
    ('wanfo_demo', 'org-x220102', 'org-c2201', 3, 'X220102', '开发区', 'P22', 'C2201', 'X220102', '2020-01-01 00:00:00+08', NULL, 1, 'org-x220102-v1', now())
ON CONFLICT (tenant_id, org_id, valid_from) DO UPDATE SET
    parent_org_id=EXCLUDED.parent_org_id, org_level=EXCLUDED.org_level, org_code=EXCLUDED.org_code,
    org_name=EXCLUDED.org_name, province_code=EXCLUDED.province_code, city_code=EXCLUDED.city_code,
    county_code=EXCLUDED.county_code, valid_to=EXCLUDED.valid_to, is_current=EXCLUDED.is_current,
    source_record_id=EXCLUDED.source_record_id, source_updated_at=EXCLUDED.source_updated_at;

INSERT INTO analytics.dp_acceptance_retail_product
    (tenant_id, product_id, product_name, category_id, category_name, list_price,
     valid_from, valid_to, is_current, source_record_id, source_updated_at)
VALUES
    ('wanfo_demo', 'prod-01', '山茶油', 'cat-food', '食品', 68.00, '2020-01-01 00:00:00+08', NULL, 1, 'prod-01-v1', now()),
    ('wanfo_demo', 'prod-02', '有机大米', 'cat-food', '食品', 48.50, '2020-01-01 00:00:00+08', NULL, 1, 'prod-02-v1', now()),
    ('wanfo_demo', 'prod-03', '保温杯', 'cat-home', '家居', 89.00, '2020-01-01 00:00:00+08', NULL, 1, 'prod-03-v1', now()),
    ('wanfo_demo', 'prod-04', '运动背包', 'cat-sport', '运动', 129.00, '2020-01-01 00:00:00+08', NULL, 1, 'prod-04-v1', now())
ON CONFLICT (tenant_id, product_id, valid_from) DO UPDATE SET
    product_name=EXCLUDED.product_name, category_id=EXCLUDED.category_id,
    category_name=EXCLUDED.category_name, list_price=EXCLUDED.list_price,
    valid_to=EXCLUDED.valid_to, is_current=EXCLUDED.is_current,
    source_record_id=EXCLUDED.source_record_id, source_updated_at=EXCLUDED.source_updated_at;

INSERT INTO analytics.dp_acceptance_retail_order_line
    (tenant_id, order_id, line_no, order_date, order_at, store_id, product_id, org_id,
     province_code, city_code, county_code, channel, quantity, sales_amount, currency_code,
     source_updated_at, is_deleted)
SELECT 'wanfo_demo', 'demo-' || lpad(i::text, 4, '0'), 1,
       (date '2026-01-01' + ((i - 1) % 90)),
       ((date '2026-01-01' + ((i - 1) % 90))::timestamp + time '10:00') AT TIME ZONE 'Asia/Shanghai',
       'store-' || ((i - 1) % 4 + 1),
       'prod-' || lpad(((i - 1) % 4 + 1)::text, 2, '0'),
       CASE ((i - 1) % 4)
         WHEN 0 THEN 'org-x110101' WHEN 1 THEN 'org-x110102'
         WHEN 2 THEN 'org-x220101' ELSE 'org-x220102' END,
       CASE WHEN ((i - 1) % 4) < 2 THEN 'P11' ELSE 'P22' END,
       CASE WHEN ((i - 1) % 4) < 2 THEN 'C1101' ELSE 'C2201' END,
       CASE ((i - 1) % 4) WHEN 0 THEN 'X110101' WHEN 1 THEN 'X110102'
         WHEN 2 THEN 'X220101' ELSE 'X220102' END,
       CASE ((i - 1) % 3) WHEN 0 THEN 'STORE' WHEN 1 THEN 'ONLINE' ELSE 'PARTNER' END,
       CASE WHEN i % 9 = 0 THEN -1.000::numeric(18,3) ELSE (1 + (i % 3))::numeric(18,3) END,
       CASE WHEN i % 9 = 0 THEN -48.50::numeric(18,2)
            ELSE (20 + ((i * 17) % 180))::numeric(18,2) END,
       'CNY', now() - (i || ' minutes')::interval, 0
FROM generate_series(1, 36) AS i
ON CONFLICT (tenant_id, order_id, line_no) DO UPDATE SET
    order_date=EXCLUDED.order_date, order_at=EXCLUDED.order_at, store_id=EXCLUDED.store_id,
    product_id=EXCLUDED.product_id, org_id=EXCLUDED.org_id, province_code=EXCLUDED.province_code,
    city_code=EXCLUDED.city_code, county_code=EXCLUDED.county_code, channel=EXCLUDED.channel,
    quantity=EXCLUDED.quantity, sales_amount=EXCLUDED.sales_amount,
    currency_code=EXCLUDED.currency_code, source_updated_at=EXCLUDED.source_updated_at,
    is_deleted=EXCLUDED.is_deleted;

-- Compact acceptance summary; no source rows are printed.
SELECT 'org_rows=' || count(*) FROM analytics.dp_acceptance_retail_org WHERE tenant_id='wanfo_demo';
SELECT 'product_rows=' || count(*) FROM analytics.dp_acceptance_retail_product WHERE tenant_id='wanfo_demo';
SELECT 'order_line_rows=' || count(*) FROM analytics.dp_acceptance_retail_order_line WHERE tenant_id='wanfo_demo';
