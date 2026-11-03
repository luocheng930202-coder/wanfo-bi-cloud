-- Dedicated fictional fixtures; never touch business tables. Values are for UI demonstrations.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS demo_canteen_overview_v1 (id INT PRIMARY KEY,purchase_wan DECIMAL(12,2),meal_visits INT,schools INT,acceptance_pct DECIMAL(5,2),safety_score DECIMAL(5,2));
INSERT IGNORE INTO demo_canteen_overview_v1 VALUES (1,286.4,326000,24,99.2,98.6);
CREATE TABLE IF NOT EXISTS demo_canteen_categories_v1 (id INT PRIMARY KEY,category VARCHAR(30),amount_wan DECIMAL(12,2));
INSERT IGNORE INTO demo_canteen_categories_v1 VALUES (1,'粮油',78.4),(2,'蔬果',66.8),(3,'肉禽',82.6),(4,'水产',34.2),(5,'乳品',24.4);
CREATE TABLE IF NOT EXISTS demo_canteen_costs_v1 (id INT PRIMARY KEY,category VARCHAR(30),amount_wan DECIMAL(12,2));
INSERT IGNORE INTO demo_canteen_costs_v1 VALUES (1,'食材采购',286.4),(2,'人员费用',96.2),(3,'能源支出',45.6),(4,'其他费用',33.8);
CREATE TABLE IF NOT EXISTS demo_canteen_meals_v1 (id INT PRIMARY KEY,day_label VARCHAR(30),meal_visits INT);
INSERT IGNORE INTO demo_canteen_meals_v1 VALUES (1,'10-26 周一',42000),(2,'10-27 周二',45500),(3,'10-28 周三',44800),(4,'10-29 周四',47300),(5,'10-30 周五',48600),(6,'10-31 周六',50100),(7,'11-01 周日',47700);
CREATE TABLE IF NOT EXISTS demo_canteen_regions_v1 (id INT PRIMARY KEY,region_code VARCHAR(6),region_name VARCHAR(30),schools INT);
INSERT IGNORE INTO demo_canteen_regions_v1 VALUES (1,'320100','南京市',8),(2,'320500','苏州市',6),(3,'320200','无锡市',4),(4,'320400','常州市',3),(5,'320600','南通市',3);
CREATE TABLE IF NOT EXISTS demo_canteen_meal_types_v1 (id INT PRIMARY KEY,meal_type VARCHAR(30),visits INT);
INSERT IGNORE INTO demo_canteen_meal_types_v1 VALUES (1,'早餐',71720),(2,'午餐',156480),(3,'晚餐',81500),(4,'其他',16300);
CREATE TABLE IF NOT EXISTS demo_canteen_devices_v1 (id INT PRIMARY KEY,device_name VARCHAR(50),location_name VARCHAR(60),status_label VARCHAR(30),temperature_c DECIMAL(6,1));
INSERT IGNORE INTO demo_canteen_devices_v1 VALUES (1,'智能冷藏柜','第一实验学校','运行正常',4.2),(2,'蒸汽消毒柜','育才中学','运行正常',98.5),(3,'排烟监测器','滨河小学','运行正常',26.8),(4,'留样冷藏柜','第二实验学校','待巡检',5.1);
CREATE TABLE IF NOT EXISTS demo_canteen_receipts_v1 (id INT PRIMARY KEY,school VARCHAR(60),event_time VARCHAR(12),category VARCHAR(30),amount_yuan DECIMAL(12,2),status_label VARCHAR(30));
INSERT IGNORE INTO demo_canteen_receipts_v1 VALUES (1,'第一实验学校','10:23','蔬菜',8620,'已入库'),(2,'育才中学','10:16','肉禽',12480,'已入库'),(3,'滨河小学','09:58','粮油',6350,'待验收'),(4,'第二实验学校','09:42','水产',5280,'已入库');
