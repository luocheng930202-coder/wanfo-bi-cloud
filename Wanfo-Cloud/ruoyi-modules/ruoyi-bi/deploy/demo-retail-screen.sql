-- Retail command-center demo (full-chain BI screen exercise)
-- Target DB: bi_phase_a_test (local BI datasource id=58)
SET NAMES utf8mb4;

DROP VIEW IF EXISTS v_retail_wide;
DROP TABLE IF EXISTS fact_order;
DROP TABLE IF EXISTS dim_target;
DROP TABLE IF EXISTS ops_retail_note;
DROP TABLE IF EXISTS dim_product;
DROP TABLE IF EXISTS dim_store;

CREATE TABLE dim_store (
  store_id   INT PRIMARY KEY COMMENT '门店ID',
  store_name VARCHAR(64) NOT NULL COMMENT '门店名称',
  province   VARCHAR(32) NOT NULL COMMENT '省份',
  city       VARCHAR(64) NOT NULL COMMENT '城市',
  open_date  DATE NOT NULL COMMENT '开业日期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店维表';

CREATE TABLE dim_product (
  product_id   INT PRIMARY KEY COMMENT '商品ID',
  product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
  category     VARCHAR(64) NOT NULL COMMENT '品类',
  list_price   DECIMAL(12,2) NOT NULL COMMENT '标价'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品维表';

CREATE TABLE dim_target (
  month_key      CHAR(7) NOT NULL COMMENT '月份',
  province       VARCHAR(32) NOT NULL COMMENT '省份',
  target_amount  DECIMAL(14,2) NOT NULL COMMENT '目标销售额',
  PRIMARY KEY (month_key, province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售目标';

CREATE TABLE fact_order (
  order_id    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  order_date  DATE NOT NULL COMMENT '订单日期',
  store_id    INT NOT NULL COMMENT '门店ID',
  product_id  INT NOT NULL COMMENT '商品ID',
  channel     VARCHAR(16) NOT NULL COMMENT '渠道',
  qty         INT NOT NULL COMMENT '数量',
  amount      DECIMAL(14,2) NOT NULL COMMENT '销售金额',
  KEY idx_order_date (order_date),
  KEY idx_order_store (store_id),
  KEY idx_order_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单事实表';

CREATE TABLE ops_retail_note (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_date   DATE NOT NULL COMMENT '备注日期',
  province    VARCHAR(32) NOT NULL COMMENT '省份',
  channel     VARCHAR(16) NOT NULL COMMENT '渠道',
  content     VARCHAR(500) NOT NULL COMMENT '备注内容',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营备注';

INSERT INTO dim_store (store_id, store_name, province, city, open_date) VALUES
(1, 'Shanghai-Center', '上海市', '上海市', '2020-01-15'),
(2, 'Shanghai-North', '上海市', '上海市', '2021-06-01'),
(3, 'Hangzhou-WestLake', '浙江省', '杭州市', '2019-11-20'),
(4, 'Beijing-Guomao', '北京市', '北京市', '2018-03-10'),
(5, 'Guangzhou-Tianhe', '广东省', '广州市', '2022-08-08'),
(6, 'Chengdu-Chunxi', '四川省', '成都市', '2020-12-01');

INSERT INTO dim_product (product_id, product_name, category, list_price) VALUES
(101, 'Latte', 'Drink', 28.00),
(102, 'Oat-Americano', 'Drink', 26.00),
(103, 'Cheese-Cake', 'Bakery', 32.00),
(104, 'Croissant', 'Bakery', 18.00),
(105, 'Kids-Meal', 'Food', 45.00),
(106, 'Salad-Bowl', 'Food', 38.00),
(107, 'Water', 'Retail', 6.00),
(108, 'Mug', 'Merch', 89.00);

INSERT INTO dim_target (month_key, province, target_amount) VALUES
('2026-01', '上海市', 120000.00),
('2026-01', '浙江省', 80000.00),
('2026-01', '北京市', 150000.00),
('2026-01', '广东省', 90000.00),
('2026-01', '四川省', 70000.00);

INSERT INTO ops_retail_note (note_date, province, channel, content) VALUES
('2026-01-10', '上海市', 'STORE', 'Demo: peak queue on holiday, extra staff deployed.');

SET SESSION cte_max_recursion_depth = 10000;

INSERT INTO fact_order (order_date, store_id, product_id, channel, qty, amount)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 3200
)
SELECT
  DATE_SUB('2026-01-18', INTERVAL (n MOD 45) DAY),
  1 + (n MOD 6),
  101 + (n MOD 8),
  ELT(1 + (n MOD 3), 'STORE', 'TMALL', 'DOUYIN'),
  1 + (n MOD 4),
  ROUND((15 + (n MOD 50)) * (1 + (n MOD 4)) * (0.85 + (n MOD 10) / 100.0), 2)
FROM seq;

CREATE OR REPLACE VIEW v_retail_wide AS
SELECT
  o.order_id, o.order_date, o.store_id, o.product_id, o.channel, o.qty, o.amount,
  s.store_name, s.province, s.city, p.product_name, p.category, p.list_price
FROM fact_order o
LEFT JOIN dim_store s ON o.store_id = s.store_id
LEFT JOIN dim_product p ON o.product_id = p.product_id;
