-- Apply Chinese COLUMN_COMMENT to retail demo tables (idempotent via MODIFY).
-- Target: bi_phase_a_test on local mysql-dev. Safe to re-run.
SET NAMES utf8mb4;
USE bi_phase_a_test;

ALTER TABLE dim_store
  MODIFY store_id   INT NOT NULL COMMENT '门店ID',
  MODIFY store_name VARCHAR(64) NOT NULL COMMENT '门店名称',
  MODIFY province   VARCHAR(32) NOT NULL COMMENT '省份',
  MODIFY city       VARCHAR(64) NOT NULL COMMENT '城市',
  MODIFY open_date  DATE NOT NULL COMMENT '开业日期';

ALTER TABLE dim_product
  MODIFY product_id   INT NOT NULL COMMENT '商品ID',
  MODIFY product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
  MODIFY category     VARCHAR(64) NOT NULL COMMENT '品类',
  MODIFY list_price   DECIMAL(12,2) NOT NULL COMMENT '标价';

ALTER TABLE dim_target
  MODIFY month_key      CHAR(7) NOT NULL COMMENT '月份',
  MODIFY province       VARCHAR(32) NOT NULL COMMENT '省份',
  MODIFY target_amount  DECIMAL(14,2) NOT NULL COMMENT '目标销售额';

ALTER TABLE fact_order
  MODIFY order_id    BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  MODIFY order_date  DATE NOT NULL COMMENT '订单日期',
  MODIFY store_id    INT NOT NULL COMMENT '门店ID',
  MODIFY product_id  INT NOT NULL COMMENT '商品ID',
  MODIFY channel     VARCHAR(16) NOT NULL COMMENT '渠道',
  MODIFY qty         INT NOT NULL COMMENT '数量',
  MODIFY amount      DECIMAL(14,2) NOT NULL COMMENT '销售金额';

ALTER TABLE ops_retail_note
  MODIFY note_date   DATE NOT NULL COMMENT '备注日期',
  MODIFY province    VARCHAR(32) NOT NULL COMMENT '省份',
  MODIFY channel     VARCHAR(16) NOT NULL COMMENT '渠道',
  MODIFY content     VARCHAR(500) NOT NULL COMMENT '备注内容';

-- View (column COMMENT on views is not used here; show_name for v_retail_wide comes from refresh script fallback)
CREATE OR REPLACE VIEW v_retail_wide AS
SELECT
  o.order_id, o.order_date, o.store_id, o.product_id, o.channel, o.qty, o.amount,
  s.store_name, s.province, s.city, p.product_name, p.category, p.list_price
FROM fact_order o
LEFT JOIN dim_store s ON o.store_id = s.store_id
LEFT JOIN dim_product p ON o.product_id = p.product_id;
