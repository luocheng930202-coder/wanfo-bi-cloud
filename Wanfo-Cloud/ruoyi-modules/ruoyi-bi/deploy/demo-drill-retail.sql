-- Demo data for region -> city -> store drill (run in business DB, e.g. ry_vue)
CREATE TABLE IF NOT EXISTS demo_drill_sales (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  region VARCHAR(32) NOT NULL,
  city VARCHAR(64) NOT NULL,
  store VARCHAR(128) NOT NULL,
  amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  order_date DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE FROM demo_drill_sales;

INSERT INTO demo_drill_sales (region, city, store, amount, order_date) VALUES
('华东', '上海市', '上海中心店', 8070, '2026-01-10'),
('华东', '上海市', '上海北站店', 8910, '2026-01-11'),
('华东', '杭州市', '杭州西湖店', 5200, '2026-01-12'),
('华东', '杭州市', '杭州滨江店', 4800, '2026-01-13'),
('华北', '北京市', '北京国贸店', 12000, '2026-01-10'),
('华北', '北京市', '北京望京店', 9500, '2026-01-11'),
('西南', '成都市', '成都春熙店', 6600, '2026-01-14'),
('西南', '成都市', '成都高新店', 7200, '2026-01-15');
