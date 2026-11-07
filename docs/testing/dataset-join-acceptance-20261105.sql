-- Local acceptance fixtures only; existing tables are not changed.
USE bi_phase_a_test;
CREATE TABLE IF NOT EXISTS qa_ds_20261105_school (
 school_id INT PRIMARY KEY, school_name VARCHAR(50), district VARCHAR(30)
);
CREATE TABLE IF NOT EXISTS qa_ds_20261105_order (
 order_id INT PRIMARY KEY, school_id INT, amount DECIMAL(12,2)
);
CREATE TABLE IF NOT EXISTS qa_ds_20261105_budget (
 budget_id INT PRIMARY KEY, school_id INT, budget_amount DECIMAL(12,2)
);
INSERT IGNORE INTO qa_ds_20261105_school VALUES
 (1,'School A','North'),(2,'School B','South'),(3,'School C','North');
INSERT IGNORE INTO qa_ds_20261105_order VALUES
 (101,1,120),(102,1,80),(103,2,150),(104,3,200),(105,99,50);
INSERT IGNORE INTO qa_ds_20261105_budget VALUES
 (1,1,150),(2,1,100),(3,2,180),(4,3,220);

-- Case 1: 5 rows, sum(amount)=600; order 105 has no school.
SELECT o.order_id,o.school_id,o.amount,s.school_name,s.district
FROM qa_ds_20261105_order o
LEFT JOIN qa_ds_20261105_school s ON o.school_id=s.school_id
ORDER BY o.order_id;

-- Third SQL dataset: 3 rows, sum(budget_total)=650.
SELECT school_id,SUM(budget_amount) AS budget_total
FROM qa_ds_20261105_budget GROUP BY school_id;

-- Case 2 equivalent SQL: 4 order-grain rows, sum(amount)=550.
-- budget_total repeats at order grain: do NOT sum it as a school-level budget.
SELECT s.school_id,s.school_name,s.district,o.order_id,o.amount,b.budget_total
FROM qa_ds_20261105_school s
LEFT JOIN qa_ds_20261105_order o ON s.school_id=o.school_id
LEFT JOIN (
 SELECT school_id,SUM(budget_amount) AS budget_total
 FROM qa_ds_20261105_budget GROUP BY school_id
) b ON s.school_id=b.school_id
ORDER BY s.school_id,o.order_id;
