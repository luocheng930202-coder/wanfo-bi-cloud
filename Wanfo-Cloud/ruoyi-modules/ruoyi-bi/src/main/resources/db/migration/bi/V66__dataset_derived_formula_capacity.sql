-- Structured grouping rules compile to CASE expressions, which can exceed the legacy 1000-character limit.
-- The application still caps expressions at 4000 characters before persistence.
ALTER TABLE bi_dataset_field
    MODIFY COLUMN ext_formula TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '计算或分组字段表达式（应用限长4000字符）';
