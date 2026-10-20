-- Sync bi_dataset_field.show_name from physical COLUMN_COMMENT (information_schema).
-- Meta: ry_vue.bi_* ; business columns: typically bi_phase_a_test (or dataset source_catalog).
-- Run via refresh-dataset-show-names.ps1 (sets @datasource_id).

SET NAMES utf8mb4;
USE ry_vue;

SET @datasource_id := IFNULL(@datasource_id, NULL);

-- Preview (optional): uncomment to inspect before update
-- SELECT f.id, t.name AS dataset_name, f.origin_name, f.show_name AS old_show,
--        TRIM(c.COLUMN_COMMENT) AS new_show, t.source_catalog, t.source_table
-- FROM bi_dataset_field f
-- INNER JOIN bi_dataset_table t ON f.dataset_id = t.id AND t.del_flag = '0' AND t.type = 'table'
-- INNER JOIN bi_datasource d ON t.datasource_id = d.id AND d.del_flag = '0'
-- INNER JOIN information_schema.COLUMNS c
--   ON c.TABLE_SCHEMA = IFNULL(NULLIF(TRIM(t.source_catalog), ''), 'bi_phase_a_test')
--  AND c.TABLE_NAME = t.source_table
--  AND c.COLUMN_NAME = IF(f.origin_name LIKE 'r\_%' AND f.ext_flag = '0', SUBSTRING(f.origin_name, 3), f.origin_name)
-- WHERE f.del_flag = '0' AND f.ext_flag = '0'
--   AND (@datasource_id IS NULL OR d.id = @datasource_id);

UPDATE bi_dataset_field f
INNER JOIN bi_dataset_table t ON f.dataset_id = t.id AND t.del_flag = '0' AND t.type = 'table'
INNER JOIN bi_datasource d ON t.datasource_id = d.id AND d.del_flag = '0'
INNER JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = IFNULL(NULLIF(TRIM(t.source_catalog), ''), 'bi_phase_a_test')
 AND c.TABLE_NAME = t.source_table
 AND c.COLUMN_NAME = IF(f.origin_name LIKE 'r\_%' AND f.ext_flag = '0', SUBSTRING(f.origin_name, 3), f.origin_name)
SET f.show_name = IF(CHAR_LENGTH(TRIM(c.COLUMN_COMMENT)) > 0, LEFT(TRIM(c.COLUMN_COMMENT), 200), f.origin_name),
    f.update_time = NOW()
WHERE f.del_flag = '0'
  AND f.ext_flag = '0'
  AND (@datasource_id IS NULL OR d.id = @datasource_id);

SELECT ROW_COUNT() AS updated_fields_exact_table;

-- Fallback: match column name across business schema (helps views like v_retail_wide)
SET @business_schema := IFNULL(@business_schema, 'bi_phase_a_test');

UPDATE bi_dataset_field f
INNER JOIN bi_dataset_table t ON f.dataset_id = t.id AND t.del_flag = '0' AND t.type = 'table'
INNER JOIN bi_datasource d ON t.datasource_id = d.id AND d.del_flag = '0'
INNER JOIN (
  SELECT COLUMN_NAME AS col_name, MAX(LEFT(TRIM(COLUMN_COMMENT), 200)) AS col_comment
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @business_schema
    AND CHAR_LENGTH(TRIM(COLUMN_COMMENT)) > 0
  GROUP BY COLUMN_NAME
) c ON c.col_name = IF(f.origin_name LIKE 'r\_%' AND f.ext_flag = '0', SUBSTRING(f.origin_name, 3), f.origin_name)
SET f.show_name = c.col_comment,
    f.update_time = NOW()
WHERE f.del_flag = '0'
  AND f.ext_flag = '0'
  AND (@datasource_id IS NULL OR d.id = @datasource_id)
  AND (f.show_name IS NULL OR f.show_name = f.origin_name OR f.show_name LIKE 'r\_%');

SELECT ROW_COUNT() AS updated_fields_fallback;
