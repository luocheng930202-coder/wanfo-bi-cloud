-- Optional migration for an existing RuoYi SYSTEM database, not the BI metadata database.
-- Back up and review existing generated-code settings / fully-qualified task targets first.
-- Bean-name task targets and unrelated package names are deliberately unchanged.
START TRANSACTION;

UPDATE gen_table
SET package_name = CONCAT('com.wanfo', SUBSTRING(package_name, 10))
WHERE package_name = 'com.ruoyi' OR package_name LIKE 'com.ruoyi.%';

UPDATE sys_job
SET invoke_target = CONCAT('com.wanfo', SUBSTRING(invoke_target, 10))
WHERE invoke_target LIKE 'com.ruoyi.%';

COMMIT;
