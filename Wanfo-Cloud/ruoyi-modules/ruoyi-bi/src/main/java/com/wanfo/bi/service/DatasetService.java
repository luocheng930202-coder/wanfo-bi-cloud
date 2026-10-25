package com.wanfo.bi.service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetGroupDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetGroupMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.query.DatasetQueryBaseBuilder;
import com.wanfo.bi.security.DataPolicyCompiler;
import com.wanfo.bi.query.QueryResult;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.BiAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据集服务。
 *
 * <p>核心职责是「把用户勾选的物理表 + 字段」翻译成可执行的 SQL（unionSql），
 * 后续图表查询只需要替换轴/过滤条件即可，不必每次重新拼装。
 *
 * @author zhengliang
 */
@Service
public class DatasetService
{
    /** JDBC 类型 -> BI 统一类型 */
    private static final Map<String, String> TYPE_MAP = new LinkedHashMap<>();

    static
    {
        for (String t : new String[]{"int", "integer", "tinyint", "smallint", "mediumint", "bigint"})
        {
            TYPE_MAP.put(t, "number");
        }
        for (String t : new String[]{"decimal", "numeric", "float", "double", "real", "money"})
        {
            TYPE_MAP.put(t, "number");
        }
        for (String t : new String[]{"date", "datetime", "timestamp", "time", "year"})
        {
            TYPE_MAP.put(t, "date");
        }
        for (String t : new String[]{"bit", "bool", "boolean"})
        {
            TYPE_MAP.put(t, "boolean");
        }
        TYPE_MAP.put("char", "string");
        TYPE_MAP.put("varchar", "string");
        TYPE_MAP.put("tinytext", "string");
        TYPE_MAP.put("text", "string");
        TYPE_MAP.put("mediumtext", "string");
        TYPE_MAP.put("longtext", "string");
        TYPE_MAP.put("json", "string");
    }

    private final BiDatasetGroupMapper groupMapper;
    private final BiDatasetTableMapper datasetMapper;
    private final BiDatasetFieldMapper fieldMapper;
    private final DataSourceProvider datasourceProvider;
    private final CalciteQueryEngine engine;
    private final CatalogService catalog;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final com.wanfo.bi.security.DatasetDataPolicy policies;

    public DatasetService(BiDatasetGroupMapper groupMapper,
                          BiDatasetTableMapper datasetMapper,
                          BiDatasetFieldMapper fieldMapper,
                          DataSourceProvider datasourceProvider,
                          CalciteQueryEngine engine, CatalogService catalog, BiResourceAuthorizer resources, CapabilityProvider capabilities, com.wanfo.bi.security.DatasetDataPolicy policies)
    {
        this.groupMapper = groupMapper;
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.datasourceProvider = datasourceProvider;
        this.engine = engine;
        this.catalog = catalog; this.resources = resources; this.capabilities = capabilities; this.policies=policies;
    }

    // ==================== 目录 ====================

    /** 目录树（前端树控件直接用） */
    public List<Map<String, Object>> groupTree()
    {
        List<BiDatasetGroupDO> all = groupMapper.selectAll();
        if (!capabilities.has("bi:resource:admin"))
        {
            Set<Long> visible = new LinkedHashSet<>();
            for (BiDatasetGroupDO group : all)
            {
                if (resources.allowed("dataset-group", group.getId(), "view"))
                { visible.add(group.getId()); }
            }
            for (BiDatasetTableDO dataset : datasetMapper.selectAll(null))
            {
                if ("table".equals(dataset.getType()) && resources.allowed("dataset", dataset.getId(), "view"))
                { visible.add(dataset.getGroupId()); }
            }
            Map<Long, BiDatasetGroupDO> groups = all.stream().collect(Collectors.toMap(BiDatasetGroupDO::getId, g -> g));
            for (Long id : new ArrayList<>(visible))
            {
                Set<Long> visited = new LinkedHashSet<>();
                BiDatasetGroupDO node = groups.get(id);
                while (node != null && visited.add(node.getId()))
                { visible.add(node.getId()); node = groups.get(node.getParentId()); }
            }
            all = all.stream().filter(g -> visible.contains(g.getId())).toList();
        }
        Map<Long, Map<String, Object>> index = new LinkedHashMap<>();

        for (BiDatasetGroupDO g : all)
        {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", g.getId());
            node.put("parentId", g.getParentId());
            node.put("name", g.getName());
            node.put("orderNum", g.getOrderNum());
            node.put("children", new ArrayList<Map<String, Object>>());
            index.put(g.getId(), node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (BiDatasetGroupDO g : all)
        {
            Map<String, Object> node = index.get(g.getId());
            Map<String, Object> parent = index.get(g.getParentId());
            if (parent != null)
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
            else
            {
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional
    public Map<String, Object> createGroup(String name, Long parentId, String operator)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("目录名不能为空");
        }
        long pid = (parentId == null) ? 0L : parentId;
        if (pid != 0) { resources.require("dataset-group", pid, "edit"); }
        if (groupMapper.countByName(pid, name) > 0)
        {
            throw new IllegalArgumentException("同级下已存在同名目录: " + name);
        }

        // ancestors: "0,1,5" 形式，便于拼 LIKE 查询子树
        String ancestors;
        if (pid == 0)
        {
            ancestors = "0";
        }
        else
        {
            BiDatasetGroupDO parent = groupMapper.selectById(pid);
            if (parent == null)
            {
                throw new IllegalArgumentException("父目录不存在: " + pid);
            }
            ancestors = parent.getAncestors() + "," + pid;
        }

        BiDatasetGroupDO row = new BiDatasetGroupDO();
        row.setParentId(pid);
        row.setAncestors(ancestors);
        row.setName(name);
        row.setOrderNum(0);
        row.setStatus("0");
        row.setCreateBy(operator);
        groupMapper.insert(row);
        resources.register("dataset-group", row.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("ancestors", ancestors);
        return result;
    }

    @Transactional
    public Map<String, Object> deleteGroup(Long id, String operator)
    {
        resources.require("dataset-group", id, "remove");
        if (groupMapper.countChildren(id) > 0)
        {
            throw new IllegalArgumentException("该目录下还有子目录，请先删除子目录");
        }
        if (datasetMapper.countByGroupId(id) > 0)
        {
            throw new IllegalArgumentException("该目录下还有数据集，请先删除数据集");
        }
        int rows = groupMapper.deleteById(id, operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", rows);
        return result;
    }

    // ==================== 数据集 ====================

    public List<Map<String, Object>> list(Long groupId)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BiDatasetTableDO row : datasetMapper.selectAll(groupId))
        {
            if (!resources.allowed("dataset", row.getId(), "view")) { continue; }
            if (!"table".equals(row.getType()) && !capabilities.has("bi:resource:admin")) { continue; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.getId());
            m.put("groupId", row.getGroupId());
            m.put("name", row.getName());
            m.put("type", row.getType());
            m.put("datasourceId", row.getDatasourceId());
            m.put("sourceTable", row.getSourceTable());
            m.put("sourceCatalog", row.getSourceCatalog());
            m.put("resourceActions", java.util.stream.Stream.of("query", "remove", "grant")
                    .filter(action -> resources.allowed("dataset", row.getId(), action)).toList());
            var scope=policies.resolve(row.getId());
            m.put("fieldCount", fieldMapper.selectByDatasetId(row.getId()).stream().filter(f -> !scope.denied().contains(f.getOriginName())).count());
            result.add(m);
        }
        return result;
    }

    /** 数据集详情（含字段） */
    public Map<String, Object> detail(Long id)
    {
        resources.require("dataset", id, "view");
        BiDatasetTableDO row = datasetMapper.selectById(id);
        if (row == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + id);
        }
        if (!"table".equals(row.getType())) { resources.requireAdministrator(); }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("groupId", row.getGroupId());
        m.put("name", row.getName());
        m.put("type", row.getType());
        m.put("datasourceId", row.getDatasourceId());
        m.put("sourceTable", row.getSourceTable());
        m.put("sourceCatalog", row.getSourceCatalog());
        m.put("sqlText", row.getSqlText());
        m.put("unionSql", capabilities.has("bi:resource:admin") ? row.getUnionSql() : null);
        m.put("cacheEnabled", row.getCacheEnabled());
        m.put("cacheTtl", row.getCacheTtl());
        m.put("remark", row.getRemark());
        m.put("modelJson", row.getModelJson());
        var scope=policies.resolve(id);
        m.put("fields", fieldMapper.selectByDatasetId(id).stream().filter(f -> !scope.denied().contains(f.getOriginName())).toList());
        return m;
    }

    @Transactional
    public Map<String, Object> updateField(Long datasetId, Long fieldId, String showName, String dimFlag, String operator)
    {
        BiDatasetTableDO ds = datasetMapper.selectById(datasetId);
        if (ds == null || !"table".equals(ds.getType())) {
            throw new IllegalArgumentException("数据集不存在或不可编辑");
        }
        capabilities.require("bi:datasource:model");
        long gid = ds.getGroupId() == null ? 0L : ds.getGroupId();
        if (gid != 0) {
            resources.require("dataset-group", gid, "edit");
        }
        BiDatasetFieldDO row = fieldMapper.selectById(fieldId);
        if (row == null || !datasetId.equals(row.getDatasetId())) {
            throw new IllegalArgumentException("字段不存在");
        }
        if ("1".equals(row.getExtFlag()) && showName != null) {
            String s = showName.trim();
            if (s.isEmpty() || s.length() > 200) {
                throw new IllegalArgumentException("显示名称须为 1～200 字");
            }
            row.setShowName(s);
        } else if (showName != null) {
            String s = showName.trim();
            if (s.isEmpty() || s.length() > 200) {
                throw new IllegalArgumentException("显示名称须为 1～200 字");
            }
            row.setShowName(s);
        }
        if (dimFlag != null) {
            if (!"0".equals(dimFlag) && !"1".equals(dimFlag)) {
                throw new IllegalArgumentException("维度标识无效");
            }
            if ("1".equals(row.getExtFlag())) {
                row.setDimFlag(dimFlag);
            } else {
                row.setDimFlag(dimFlag);
                row.setGroupName("0".equals(dimFlag) ? "度量" : "维度");
            }
        }
        fieldMapper.update(row);
        return Map.of("id", fieldId, "showName", row.getShowName(), "dimFlag", row.getDimFlag());
    }

    /**
     * 创建数据集（表型）。
     *
     * <p>流程：校验数据源 -> 校验物理表与字段真实存在 -> 生成 unionSql -> 落库 -> 批量写字段
     *
     * @param form        数据集定义
     * @param fieldNames  勾选的字段名（保持前端顺序）
     */
    public record FieldSelection(String name, String showName, String dimFlag) { }

    @Transactional
    public Map<String, Object> createTableDataset(BiDatasetTableDO form,
                                                   List<FieldSelection> selections,
                                                   String operator) throws Exception
    {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("至少要选择 1 个字段");
        }
        if (form.getName() == null || form.getName().isBlank())
        {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        long gid = (form.getGroupId() == null) ? 0L : form.getGroupId();
        if (datasetMapper.countByName(gid, form.getName()) > 0)
        {
            throw new IllegalArgumentException("同目录下已存在同名数据集: " + form.getName());
        }
        if (form.getSourceTable() == null || form.getSourceTable().isBlank())
        {
            throw new IllegalArgumentException("物理表名不能为空");
        }
        List<String> fieldNames = selections.stream().map(FieldSelection::name).toList();

        BiDatasource ds = datasourceProvider.getById(form.getDatasourceId());
        if (ds == null)
        {
            throw new IllegalArgumentException("数据源不存在或已停用: " + form.getDatasourceId());
        }

        if (gid != 0) { resources.require("dataset-group", gid, "edit"); }
        CatalogService.TablePlan plan = catalog.plan(ds.getSchemaName(), form.getSourceCatalog(), form.getSourceTable(), fieldNames);
        form.setSourceCatalog(plan.catalog());
        form.setSourceTable(plan.table());
        // 校验字段真实存在，防止表结构变更后前端残留的旧字段被写进来
        Map<String, Map<String, Object>> actual = new LinkedHashMap<>();
        for (Map<String, Object> c : plan.columns())
        {
            actual.put(String.valueOf(c.get("name")).toLowerCase(), c);
        }

        List<BiDatasetFieldDO> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int order = 0;
        for (FieldSelection sel : selections)
        {
            String raw = sel.name();
            String key = raw.toLowerCase();
            if (!seen.add(key))
            {
                continue;
            }
            Map<String, Object> col = actual.get(key);
            if (col == null)
            {
                throw new IllegalArgumentException("字段在数据源中不存在: " + raw
                        + "（可能表结构已变更，请刷新后重选）");
            }
            BiDatasetFieldDO f = new BiDatasetFieldDO();
            f.setOriginName((String) col.get("name"));
            f.setOriginType((String) col.get("type"));
            String show = sel.showName() == null ? null : sel.showName().trim();
            if (show == null || show.isEmpty()) {
                show = FieldDisplayLabels.catalogShowName(col);
            }
            if (show.length() > 200) {
                throw new IllegalArgumentException("显示名称不能超过 200 字: " + raw);
            }
            f.setShowName(show);
            String dim = sel.dimFlag();
            if (dim != null && ("0".equals(dim) || "1".equals(dim))) {
                f.setDimFlag(dim);
            } else {
                f.setDimFlag("metric".equals(col.get("suggestAs")) ? "0" : "1");
            }
            f.setExtFlag("0");
            f.setGroupName("0".equals(f.getDimFlag()) ? "度量" : "维度");
            f.setOrderNum(order++);
            f.setStatus("0");
            fields.add(f);
        }

        form.setType("table");
        form.setGroupId(gid);
        form.setCacheEnabled(form.getCacheEnabled() == null ? "0" : form.getCacheEnabled());
        form.setCacheTtl(form.getCacheTtl() == null ? 300 : form.getCacheTtl());
        form.setUnionSql(buildSelectSql(ds.getSchemaName(), form.getSourceTable(),
                fields.stream().map(BiDatasetFieldDO::getOriginName).collect(Collectors.toList())));
        form.setCreateBy(operator);

        datasetMapper.insert(form);
        resources.register("dataset", form.getId());
        fields.forEach(f -> f.setDatasetId(form.getId()));
        fieldMapper.insertBatch(fields);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", form.getId());
        result.put("fieldCount", fields.size());
        result.put("unionSql", form.getUnionSql());
        return result;
    }

    /** 创建 SQL 型数据集 */
    @Transactional
    public Map<String, Object> createSqlDataset(BiDatasetTableDO form,
                                                 List<BiDatasetFieldDO> fields,
                                                 String operator) throws Exception
    {
        resources.requireAdministrator();
        if (form.getName() == null || form.getName().isBlank())
        {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        if (form.getSqlText() == null || form.getSqlText().isBlank())
        {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        BiDatasource ds = datasourceProvider.getById(form.getDatasourceId());
        if (ds == null)
        {
            throw new IllegalArgumentException("数据源不存在或已停用: " + form.getDatasourceId());
        }

        form.setType("sql");
        form.setGroupId(form.getGroupId() == null ? 0L : form.getGroupId());
        if (datasetMapper.countByName(form.getGroupId(), form.getName()) > 0)
        {
            throw new IllegalArgumentException("同目录下已存在同名数据集: " + form.getName());
        }
        form.setCacheEnabled("0");
        form.setCacheTtl(300);
        form.setCreateBy(operator);

        datasetMapper.insert(form);
        resources.register("dataset", form.getId());
        if (fields != null && !fields.isEmpty())
        {
            int order = 0;
            for (BiDatasetFieldDO f : fields)
            {
                f.setDatasetId(form.getId());
                if (f.getOrderNum() == null)
                {
                    f.setOrderNum(order);
                }
                if (f.getDimFlag() == null)
                {
                    f.setDimFlag("1");
                }
                f.setExtFlag(f.getExtFlag() == null ? "0" : f.getExtFlag());
                f.setStatus("0");
                order++;
            }
            fieldMapper.insertBatch(fields);
        }

        // SQL 型不做字段校验（列由用户自己负责），直接试查一次确认可执行
        Map<String, Object> probe = preview(form.getId(), 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", form.getId());
        result.put("probeOk", probe.get("ok"));
        if (Boolean.FALSE.equals(probe.get("ok")))
        {
            throw new IllegalArgumentException("SQL 无法执行: " + probe.get("message"));
        }
        return result;
    }

    // ==================== 查询 ====================

    /**
     * 预览数据集数据。
     *
     * @param id      数据集 ID
     * @param maxRows 返回行数
     */
    public Map<String, Object> preview(Long id, int maxRows) throws Exception
    {
        resources.require("dataset", id, "view");
        resources.require("dataset", id, "query");
        if (maxRows < 1 || maxRows > 100) { throw new IllegalArgumentException("预览行数须在 1～100 之间"); }
        BiDatasetTableDO dataset = datasetMapper.selectById(id);
        if (dataset == null) { throw new IllegalArgumentException("数据集不存在"); }
        if ("table".equals(dataset.getType()))
        {
            BiDatasource source = datasourceProvider.getById(dataset.getDatasourceId());
            if (source == null || !"0".equals(source.getStatus())) { throw new BiAccessException(403, "数据源不可用"); }
            if ((dataset.getModelJson() != null && !dataset.getModelJson().isBlank())
                    || fieldMapper.selectByDatasetId(id).stream().anyMatch(f -> "1".equals(f.getExtFlag())))
            { return previewJoinedTable(dataset, id, maxRows, source); }
            List<String> fields = fieldMapper.selectByDatasetId(id).stream().map(BiDatasetFieldDO::getOriginName).toList();
            Map<String,Object> result = catalog.savedPreview(source.getSchemaName(), dataset.getSourceCatalog(), dataset.getSourceTable(), fields, maxRows, policies.resolve(id));
            result.put("datasetId",id); result.put("datasetName",dataset.getName()); return result;
        }
        resources.requireAdministrator();
        return previewSql(id,maxRows);
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> qualityReport(Long id)throws Exception{return qualityReport(id,false);}
    public Map<String,Object> qualityReport(Long id,boolean fullScan)throws Exception{
        if(fullScan)return fullQualityReport(id);
        Map<String,Object> sample=preview(id,100);Object rawColumns=sample.get("columns"),rawRows=sample.get("rows");
        if(!(rawColumns instanceof List<?> columns)||!(rawRows instanceof List<?> rows))throw new IllegalStateException("数据质量检查结果格式无效");
        List<String> names=columns.stream().map(String::valueOf).toList();List<Map<String,Object>> records=new ArrayList<>();
        for(Object row:rows)if(row instanceof Map<?,?> map){Map<String,Object> values=new LinkedHashMap<>();map.forEach((k,v)->values.put(String.valueOf(k),v));records.add(values);}
        return DatasetQuality.summarize(names,records);
    }

    private Map<String,Object> fullQualityReport(Long id)throws Exception{
        resources.require("dataset",id,"view");resources.require("dataset",id,"query");
        BiDatasetTableDO dataset=datasetMapper.selectById(id);if(dataset==null)throw new IllegalArgumentException("数据集不存在");
        List<String> columns;List<String> types;String base;List<Object> params=List.of();Connection conn;
        if("table".equals(dataset.getType())){
            BiDatasource source=datasourceProvider.getById(dataset.getDatasourceId());if(source==null||!"0".equals(source.getStatus()))throw new BiAccessException(403,"数据源不可用");
            List<BiDatasetFieldDO> fields=fieldMapper.selectByDatasetId(id);if(fields.isEmpty())throw new IllegalArgumentException("数据集没有可扫描字段");
            List<String> physicalFields=fields.stream().filter(f->!"1".equals(f.getExtFlag())).map(BiDatasetFieldDO::getOriginName).toList();
            String physicalBase=dataset.getModelJson()!=null&&!dataset.getModelJson().isBlank()?dataset.getUnionSql():catalog.savedPlan(source.getSchemaName(),dataset.getSourceCatalog(),dataset.getSourceTable(),physicalFields).sql();
            conn=engine.borrowConnection(source.getSchemaName());
            String quote=conn.getMetaData().getIdentifierQuoteString().trim();base=DatasetQueryBaseBuilder.expand(physicalBase,fields,quote);
            Map<String,String> fieldTypes=new LinkedHashMap<>();for(BiDatasetFieldDO field:fields)fieldTypes.put(field.getOriginName(),String.valueOf(field.getOriginType()));
            var scope=policies.resolve(id);var access=DataPolicyCompiler.compile(new ArrayList<>(fieldTypes.keySet()),fieldTypes,quote,scope);
            columns=access.columns();types=columns.stream().map(fieldTypes::get).toList();params=access.params();
            base="SELECT "+access.projection()+" FROM ("+base+") AS quality_source"+(access.predicate().isBlank()?"":" WHERE "+access.predicate());
        }else{
            resources.requireAdministrator();BiDatasource source=datasourceProvider.getById(dataset.getDatasourceId());if(source==null||!"0".equals(source.getStatus()))throw new BiAccessException(403,"数据源不可用");
            base="sql".equals(dataset.getType())?dataset.getSqlText():dataset.getUnionSql();if(base==null||base.isBlank())throw new IllegalArgumentException("数据集查询定义为空");
            conn=engine.open(datasourceProvider.listEnabled());columns=fieldMapper.selectByDatasetId(id).stream().map(BiDatasetFieldDO::getOriginName).toList();types=fieldMapper.selectByDatasetId(id).stream().map(f->String.valueOf(f.getOriginType())).toList();
        }
        long started=System.currentTimeMillis();
        try(conn){
            String quote=conn.getMetaData().getIdentifierQuoteString().trim();
            String relation="("+base+") AS full_quality";StringBuilder aggregate=new StringBuilder("SELECT COUNT(*)");
            for(int i=0;i<columns.size();i++){
                String field=qualityIdentifier(quote,columns.get(i));boolean text=isTextType(types.get(i));
                String nullCheck=field+" IS NULL"+(text?" OR TRIM("+field+") = ''":"");String distinctValue=text?"NULLIF(TRIM("+field+"), '')":field;
                aggregate.append(", COALESCE(SUM(CASE WHEN ").append(nullCheck).append(" THEN 1 ELSE 0 END), 0)");
                aggregate.append(", COUNT(DISTINCT ").append(distinctValue).append(")");
            }
            aggregate.append(" FROM ").append(relation);
            long rowCount;long[] nullCounts=new long[columns.size()],distinctCounts=new long[columns.size()];
            try(java.sql.PreparedStatement statement=conn.prepareStatement(aggregate.toString())){
                statement.setQueryTimeout(120);bindQualityParams(statement,params);try(java.sql.ResultSet rs=statement.executeQuery()){
                    if(!rs.next())throw new IllegalStateException("全量质量统计没有返回结果");rowCount=rs.getLong(1);int index=2;
                    for(int i=0;i<columns.size();i++){nullCounts[i]=rs.getLong(index++);distinctCounts[i]=rs.getLong(index++);}
                }
            }
            long duplicateRows=0;
            if(!columns.isEmpty()){
                String groupFields=columns.stream().map(c->qualityIdentifier(quote,c)).collect(Collectors.joining(", "));
                String duplicateSql="SELECT COALESCE(SUM(duplicate_count - 1), 0) FROM (SELECT COUNT(*) AS duplicate_count FROM "+relation+" GROUP BY "+groupFields+" HAVING COUNT(*) > 1) duplicate_groups";
                try(java.sql.PreparedStatement statement=conn.prepareStatement(duplicateSql)){
                    statement.setQueryTimeout(120);bindQualityParams(statement,params);try(java.sql.ResultSet rs=statement.executeQuery()){if(!rs.next())throw new IllegalStateException("重复行统计没有返回结果");duplicateRows=rs.getLong(1);}
                }
            }
            return DatasetQuality.fullScan(columns,rowCount,nullCounts,distinctCounts,duplicateRows,System.currentTimeMillis()-started);
        }
    }
    private static void bindQualityParams(java.sql.PreparedStatement statement,List<Object> params)throws java.sql.SQLException{for(int i=0;i<params.size();i++)statement.setObject(i+1,params.get(i));}
    private static String qualityIdentifier(String quote,String name){String end="[".equals(quote)?"]":quote;return quote+name.replace(end,end+end)+end;}
    private static boolean isTextType(String type){String normalized=type==null?"":type.toLowerCase(java.util.Locale.ROOT);return normalized.matches(".*(char|varchar|string|text).*");}

    private Map<String,Object> previewJoinedTable(BiDatasetTableDO dataset, Long id, int maxRows, BiDatasource source) throws Exception
    {
        List<BiDatasetFieldDO> fields = fieldMapper.selectByDatasetId(id);
        String physicalBase = dataset.getModelJson()!=null && !dataset.getModelJson().isBlank()
                ? dataset.getUnionSql()
                : catalog.savedPlan(source.getSchemaName(),dataset.getSourceCatalog(),dataset.getSourceTable(),
                    fields.stream().filter(f -> !"1".equals(f.getExtFlag())).map(BiDatasetFieldDO::getOriginName).toList()).sql();
        var scope = policies.resolve(id);
        Map<String, String> types = new LinkedHashMap<>();
        for (BiDatasetFieldDO f : fields)
        {
            types.put(f.getOriginName(), String.valueOf(f.getOriginType()));
        }
        long start = System.currentTimeMillis();
        try (Connection conn = engine.borrowConnection(source.getSchemaName()))
        {
            String base = DatasetQueryBaseBuilder.expand(physicalBase, fields,conn.getMetaData().getIdentifierQuoteString().trim());
            var compiled = DataPolicyCompiler.compile(new ArrayList<>(types.keySet()), types,
                    conn.getMetaData().getIdentifierQuoteString().trim(), scope);
            String sql = "SELECT " + compiled.projection() + " FROM (" + base + ") AS policy_src"
                    + (compiled.predicate().isEmpty() ? "" : " WHERE " + compiled.predicate()) + " LIMIT " + maxRows;
            try (java.sql.PreparedStatement st = conn.prepareStatement(sql))
            {
                st.setMaxRows(maxRows);
                st.setQueryTimeout(10);
                for (int p = 0; p < compiled.params().size(); p++)
                { st.setObject(p + 1, compiled.params().get(p)); }
                try (java.sql.ResultSet rs = st.executeQuery())
                {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rows.size() < maxRows && rs.next())
                    {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= compiled.columns().size(); i++)
                        {
                            Object value = com.wanfo.bi.query.JdbcResultValues.read(rs, i);
                            if (value instanceof Long || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger)
                            { value = value.toString(); }
                            row.put(compiled.columns().get(i - 1), value);
                        }
                        rows.add(row);
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("ok", true);
                    result.put("datasetId", id);
                    result.put("datasetName", dataset.getName());
                    result.put("columns", compiled.columns());
                    result.put("rows", rows);
                    result.put("rowCount", rows.size());
                    result.put("costMs", System.currentTimeMillis() - start);
                    return result;
                }
            }
        }
    }

    private Map<String,Object> previewSql(Long id, int maxRows)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        BiDatasetTableDO row = datasetMapper.selectById(id);
        if (row == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + id);
        }
        BiDatasource ds = datasourceProvider.getById(row.getDatasourceId());
        if (ds == null)
        {
            throw new IllegalArgumentException("数据源不存在或已停用");
        }

        String baseSql = "sql".equals(row.getType())
                ? row.getSqlText()
                : row.getUnionSql();

        String sql = baseSql + " LIMIT " + maxRows;
        try (Connection conn = engine.open(datasourceProvider.listEnabled()))
        {
            QueryResult qr = engine.query(conn, sql, maxRows);
            result.put("ok", true);
            result.put("datasetId", id);
            result.put("datasetName", row.getName());
            result.put("columns", qr.getColumns());
            result.put("rows", qr.getRows());
            result.put("rowCount", qr.getRows().size());
            result.put("costMs", qr.getCostMs());
            result.put("executedSql", qr.getExecutedSql());
        }
        catch (Exception e)
        {
            result.put("ok", false);
            result.put("datasetId", id);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ==================== 删除 ====================

    @Transactional
    public Map<String, Object> delete(Long id, String operator)
    {
        resources.require("dataset", id, "remove");
        BiDatasetTableDO row = datasetMapper.selectById(id);
        if (row == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + id);
        }
        List<Map<String, Object>> refs = datasetMapper.countChartRefs(id);
        if (refs != null && !refs.isEmpty())
        {
            throw new IllegalArgumentException("数据集仍被图表引用，无法删除；请查看引用关系并先解除绑定");
        }

        fieldMapper.deleteByDatasetId(id);
        policies.deleteWithDataset(id);
        int rows = datasetMapper.deleteById(id, operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", rows);
        result.put("fieldsCleaned", true);
        return result;
    }

    // ==================== 内部 ====================

    /** JDBC 类型 -> BI 统一类型 */
    static String mapType(String jdbcType)
    {
        if (jdbcType == null)
        {
            return "string";
        }
        return TYPE_MAP.getOrDefault(jdbcType.toLowerCase(), "string");
    }

    /**
     * 生成数据集的基础 SELECT。
     * 字段名用双引号包裹，规避保留字与大小写问题。
     */
    private String buildSelectSql(String schemaName, String table, List<String> columns)
    {
        String cols = columns.stream()
                .map(c -> "\"" + c.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(", "));
        return "SELECT " + cols + " FROM " + "\"" + schemaName.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
    }
}

