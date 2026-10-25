-- RuoYi adapter installation only. Run explicitly against the RuoYi system database.
-- Do not put sys_menu writes in BI Flyway migrations; standalone mode has no sys_* tables.
-- Uses auto IDs; does not grant any existing business role automatically.
START TRANSACTION;
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT 'BI 资源管理', 0, 5, 'bi-admin', NULL, 'BiAdmin', 1, 1, 'M', '0', '0', '', 'chart', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'bi-admin');

SET @bi_parent = (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'bi-admin' AND menu_type = 'M');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT 'BI 工作台', @bi_parent, 0, 'workbench', 'bi/workbench/index', 'BiWorkbench',
    1, 1, 'C', '0', '0', '', 'guide', 'system', NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM sys_menu WHERE parent_id = @bi_parent AND path = 'workbench');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '数据源管理', @bi_parent, 1, 'datasource', 'bi/datasource/index', 'BiDatasource',
    1, 1, 'C', '0', '0', 'bi:datasource:list', 'server', 'system', NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM sys_menu WHERE parent_id = @bi_parent AND path = 'datasource');

SET @bi_datasource = (SELECT menu_id FROM sys_menu WHERE parent_id = @bi_parent
    AND path = 'datasource' AND perms = 'bi:datasource:list');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status,
    perms, icon, create_by, create_time)
SELECT p.label, @bi_datasource, p.position, '#', 'F', '0', '0', p.permission, '#', 'system', NOW()
FROM (
    SELECT '新增数据源' label, 1 position, 'bi:datasource:add' permission
    UNION ALL SELECT '编辑与启停', 2, 'bi:datasource:edit'
    UNION ALL SELECT '删除数据源', 3, 'bi:datasource:remove'
    UNION ALL SELECT '连接测试与探测', 4, 'bi:datasource:test'
    UNION ALL SELECT '配置同步', 5, 'bi:datasource:sync'
    UNION ALL SELECT '资源授权', 6, 'bi:datasource:grant'
    UNION ALL SELECT '库表浏览与建模', 7, 'bi:datasource:model'
) p
WHERE @bi_datasource IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM sys_menu m WHERE m.parent_id = @bi_datasource AND m.perms = p.permission);
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据集管理',@bi_parent,2,'dataset','bi/dataset/index','BiDataset',1,1,'C','0','0','bi:dataset:list','table','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='dataset');
SET @bi_dataset=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='dataset' AND perms='bi:dataset:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_dataset,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '创建数据集' label,1 position,'bi:dataset:add' permission
 UNION ALL SELECT '查询与预览',2,'bi:dataset:query'
 UNION ALL SELECT '删除数据集',3,'bi:dataset:remove'
 UNION ALL SELECT '数据集授权',4,'bi:dataset:grant'
 UNION ALL SELECT '新建目录',5,'bi:dataset:group:add'
 UNION ALL SELECT '删除目录',6,'bi:dataset:group:remove'
) p WHERE @bi_dataset IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_dataset AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '图表管理',@bi_parent,3,'chart','bi/chart/index','BiChart',1,1,'C','0','0','bi:chart:list','chart','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='chart');
SET @bi_chart=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='chart' AND perms='bi:chart:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_chart,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '创建图表' label,1 position,'bi:chart:add' permission
 UNION ALL SELECT '编辑图表',2,'bi:chart:edit'
 UNION ALL SELECT '图表取数',3,'bi:chart:query'
 UNION ALL SELECT '删除图表',4,'bi:chart:remove'
 UNION ALL SELECT '图表授权',5,'bi:chart:grant'
) p WHERE @bi_chart IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_chart AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据指标',@bi_parent,3,'metric','bi/metric/index','BiMetric',1,1,'C','0','0','bi:metric:list','dict','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='metric');
SET @bi_metric=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='metric' AND perms='bi:metric:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_metric,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '创建指标' label,1 position,'bi:metric:add' permission
 UNION ALL SELECT '编辑指标',2,'bi:metric:edit'
 UNION ALL SELECT '指标取数',3,'bi:metric:query'
 UNION ALL SELECT '发布与下线',4,'bi:metric:publish'
 UNION ALL SELECT '删除指标',5,'bi:metric:remove'
 UNION ALL SELECT '指标授权',6,'bi:metric:grant'
) p WHERE @bi_metric IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_metric AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '看板管理',@bi_parent,4,'dashboard','bi/dashboard/index','BiDashboardAdmin',1,1,'C','0','0','bi:dashboard:list','dashboard','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='dashboard');
SET @bi_dashboard=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='dashboard' AND perms='bi:dashboard:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_dashboard,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '创建看板' label,1 position,'bi:dashboard:add' permission
 UNION ALL SELECT '编辑草稿',2,'bi:dashboard:edit'
 UNION ALL SELECT '发布与下线',3,'bi:dashboard:publish'
 UNION ALL SELECT '查看已发布看板',4,'bi:dashboard:consume'
 UNION ALL SELECT '删除看板',5,'bi:dashboard:remove'
 UNION ALL SELECT '看板授权',6,'bi:dashboard:grant'
) p WHERE @bi_dashboard IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_dashboard AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_dashboard,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (SELECT '素材浏览' label,7 position,'bi:asset:list' permission UNION ALL SELECT '素材上传',8,'bi:asset:add' UNION ALL SELECT '素材删除',9,'bi:asset:remove' UNION ALL SELECT '素材重命名',10,'bi:asset:edit' UNION ALL SELECT '素材授权',11,'bi:asset:grant') p
WHERE @bi_dashboard IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_dashboard AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_dashboard,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (SELECT '模板浏览' label,12 position,'bi:template:list' permission UNION ALL SELECT '保存模板',13,'bi:template:add' UNION ALL SELECT '更新模板',14,'bi:template:edit' UNION ALL SELECT '删除模板',15,'bi:template:remove' UNION ALL SELECT '模板授权',16,'bi:template:grant') p
WHERE @bi_dashboard IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_dashboard AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '导出中心',@bi_parent,5,'export','bi/export/index','BiExport',1,1,'C','0','0','bi:export:list','download','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='export');
SET @bi_export=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='export' AND perms='bi:export:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_export,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '提交导出' label,1 position,'bi:export:add' permission
 UNION ALL SELECT '下载文件',2,'bi:export:download'
 UNION ALL SELECT '取消任务',3,'bi:export:cancel'
) p WHERE @bi_export IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_export AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '报表订阅',@bi_parent,8,'subscription','bi/subscription/index','BiSubscription',1,1,'C','0','0','bi:subscription:list','message','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='subscription');
SET @bi_subscription=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='subscription' AND perms='bi:subscription:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_subscription,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (SELECT '新建订阅' label,1 position,'bi:subscription:add' permission UNION ALL SELECT '编辑与启停',2,'bi:subscription:edit' UNION ALL SELECT '删除订阅',3,'bi:subscription:remove') p
WHERE @bi_subscription IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_subscription AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据填报',@bi_parent,6,'form','bi/form/index','BiDataForm',1,1,'C','0','0','bi:form:list','edit','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='form');
SET @bi_form=(SELECT menu_id FROM sys_menu WHERE parent_id=@bi_parent AND path='form' AND perms='bi:form:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.label,@bi_form,p.position,'#','F','0','0',p.permission,'#','system',NOW()
FROM (
 SELECT '新建表单' label,1 position,'bi:form:add' permission
 UNION ALL SELECT '编辑表单',2,'bi:form:edit'
 UNION ALL SELECT '提交数据',3,'bi:form:submit'
 UNION ALL SELECT '删除表单',4,'bi:form:remove'
) p WHERE @bi_form IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@bi_form AND m.perms=p.permission);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '模板中心',@bi_parent,7,'template','bi/template/index','BiTemplateCenter',1,1,'C','0','0','bi:template:list','collection','system',NOW()
WHERE @bi_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@bi_parent AND path='template');

-- 为超级管理员角色（role_id=1）挂载 BI 菜单树（含目录、页面与按钮权限），可重复执行。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE @bi_parent IS NOT NULL
  AND (
    m.menu_id = @bi_parent
    OR m.parent_id = @bi_parent
    OR m.parent_id IN (SELECT menu_id FROM sys_menu WHERE parent_id = @bi_parent)
    OR m.parent_id IN (
      SELECT c.menu_id FROM sys_menu c
      INNER JOIN sys_menu p ON c.parent_id = p.menu_id AND p.parent_id = @bi_parent
    )
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

COMMIT;

-- Verify the resulting IDs, paths and permission codes before assigning roles in RuoYi.
SELECT menu_id, parent_id, menu_name, path, component, perms FROM sys_menu
WHERE menu_id IN (@bi_parent, @bi_datasource,@bi_dataset,@bi_chart,@bi_metric,@bi_dashboard,@bi_export,@bi_form) OR parent_id IN (@bi_datasource,@bi_dataset,@bi_chart,@bi_metric,@bi_dashboard,@bi_export,@bi_form)
   OR (path IN ('template','form','workbench','metric') AND parent_id=@bi_parent);
