package com.wanfo.bi.service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.DatasetDataPolicy;
import com.wanfo.bi.security.DataPolicyCompiler;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import org.springframework.stereotype.Service;

/** Controlled modelling over the database explicitly configured in each datasource. */
@Service
public class CatalogService
{
    private com.wanfo.bi.datasource.snapshot.SnapshotRegistry snapshots;
    @org.springframework.beans.factory.annotation.Autowired
    public void configureSnapshots(com.wanfo.bi.datasource.snapshot.SnapshotRegistry snapshots){this.snapshots=snapshots;}
    private boolean snapshot(String alias){return com.wanfo.bi.datasource.snapshot.SnapshotRegistry.supports(activeSource(alias).getType());}
    private void snapshotScope(String catalog,String table){if(catalog!=null && !catalog.isBlank() && !catalog.equals("snapshot"))throw new BiAccessException(403,"快照建模范围无效");if(table!=null && !table.equals("data"))throw new IllegalArgumentException("快照仅包含 data 表");}
    private List<Map<String,Object>> snapshotColumns(String alias){
        return snapshots.load(activeSource(alias).getId()).columns().stream().map(c->Map.<String,Object>of("name",c.name(),"type",c.type(),"nullable",true,"primaryKey",false,"suggestAs",c.type().equals("DECIMAL")?"metric":"dimension")).toList();
    }
    private final CalciteQueryEngine engine;
    private final DataSourceProvider provider;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;

    public CatalogService(CalciteQueryEngine engine, DataSourceProvider provider,
                          BiResourceAuthorizer resources, CapabilityProvider capabilities)
    {
        this.engine = engine; this.provider = provider; this.resources = resources; this.capabilities = capabilities;
        engine.setFallbackProvider(provider);
    }

    public List<Map<String,Object>> sources()
    {
        capabilities.require("bi:datasource:model");
        return provider.listEnabled().stream().filter(ds -> "0".equals(ds.getStatus())
                && resources.allowed("datasource", ds.getId(), "model"))
                .map(ds -> Map.<String,Object>of("id", ds.getId(), "name", ds.getName(),
                        "schemaName", ds.getSchemaName(), "type", ds.getType())).toList();
    }

    public BiDatasource requireModel(String schemaName)
    {
        capabilities.require("bi:datasource:model");
        BiDatasource ds = activeSource(schemaName);
        resources.require("datasource", ds.getId(), "model");
        return ds;
    }

    private BiDatasource activeSource(String schemaName)
    {
        BiDatasource ds = provider.getBySchemaName(schemaName);
        if (ds == null || !"0".equals(ds.getStatus())) { throw new BiAccessException(403, "数据源不可用"); }
        return ds;
    }

    private com.wanfo.bi.datasource.JdbcSourceScope sourceScope(Connection conn, String alias, String requested) throws SQLException
    {
        var scope = com.wanfo.bi.datasource.JdbcSourceScope.resolve(conn, activeSource(alias).getSourceSchema());
        scope.require(requested);
        return scope;
    }

    private static String pattern(DatabaseMetaData md, String identifier) throws SQLException
    {
        String escape = md.getSearchStringEscape();
        if (escape == null || escape.isEmpty())
        {
            if (identifier.contains("%") || identifier.contains("_"))
            { throw new IllegalArgumentException("数据库驱动未提供标识符匹配转义"); }
            return identifier;
        }
        return identifier.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static String quote(DatabaseMetaData md, String identifier) throws SQLException
    {
        return com.wanfo.bi.datasource.JdbcSourceScope.quote(md, identifier);
    }

    private static String logical(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    public List<String> listCatalogs(String schemaName) throws Exception
    {
        requireModel(schemaName);
        if(snapshot(schemaName))return List.of("snapshot");
        try (Connection conn = engine.borrowConnection(schemaName)) { return List.of(sourceScope(conn, schemaName, null).name()); }
    }

    public List<Map<String,Object>> listTables(String schemaName, String catalog) throws Exception
    {
        requireModel(schemaName);
        if(snapshot(schemaName)){snapshotScope(catalog,null);return List.of(Map.of("name","data","type","TABLE","catalog","snapshot","comment","数据快照"));}
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            var scope = sourceScope(conn, schemaName, catalog);
            String cat = scope.name();
            List<Map<String,Object>> tables = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getTables(scope.catalog(), scope.schema() == null ? null : pattern(conn.getMetaData(), scope.schema()), "%", new String[]{"TABLE","VIEW"}))
            {
                while (rs.next())
                {
                    Map<String,Object> t = new LinkedHashMap<>();
                    t.put("name", rs.getString("TABLE_NAME")); t.put("type", rs.getString("TABLE_TYPE"));
                    t.put("comment", rs.getString("REMARKS")); t.put("catalog", cat); tables.add(t);
                }
            }
            tables.sort(Comparator.comparing(t -> String.valueOf(t.get("name"))));
            return tables;
        }
    }

    private List<Map<String,Object>> columns(Connection conn, com.wanfo.bi.datasource.JdbcSourceScope scope, String table) throws Exception
    {
        if (table == null || table.isBlank()) { throw new IllegalArgumentException("请选择物理表"); }
        DatabaseMetaData md = conn.getMetaData();
        boolean exists = false;
        try (ResultSet rs = md.getTables(scope.catalog(), scope.schema() == null ? null : pattern(md, scope.schema()), pattern(md, table), new String[]{"TABLE","VIEW"}))
        { while (rs.next()) { if (table.equals(rs.getString("TABLE_NAME"))) { exists = true; break; } } }
        if (!exists) { throw new IllegalArgumentException("表不存在或不在当前建模范围内"); }
        Set<String> primary = new HashSet<>();
        try (ResultSet rs = md.getPrimaryKeys(scope.catalog(), scope.schema(), table))
        { while (rs.next()) { primary.add(rs.getString("COLUMN_NAME")); } }
        catch (SQLFeatureNotSupportedException ignored) { /* Views and some analytical drivers have no primary-key metadata. */ }
        List<Map<String,Object>> result = new ArrayList<>();
        try (ResultSet rs = md.getColumns(scope.catalog(), scope.schema() == null ? null : pattern(md, scope.schema()), pattern(md, table), "%"))
        {
            while (rs.next())
            {
                if (!table.equals(rs.getString("TABLE_NAME"))) { continue; }
                String name = rs.getString("COLUMN_NAME"), type = rs.getString("TYPE_NAME");
                type = com.wanfo.bi.datasource.JdbcSourceScope.columnType(rs.getInt("DATA_TYPE"), type);
                String lower = type.toLowerCase(Locale.ROOT);
                boolean number = lower.matches(".*(int|decimal|numeric|float|double|real).*" );
                Map<String,Object> field = new LinkedHashMap<>();
                field.put("name",name); field.put("type",type); field.put("size",rs.getInt("COLUMN_SIZE"));
                field.put("nullable", "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                field.put("comment",rs.getString("REMARKS")); field.put("primaryKey",primary.contains(name));
                field.put("suggestAs",number ? "metric" : "dimension"); result.add(field);
            }
        }
        if (result.isEmpty()) { throw new IllegalArgumentException("物理表没有可读取的字段"); }
        return result;
    }

    public List<Map<String,Object>> listColumns(String schemaName, String catalog, String table) throws Exception
    {
        requireModel(schemaName);
        if(snapshot(schemaName)){snapshotScope(catalog,table);return snapshotColumns(schemaName);}
        try (Connection conn = engine.borrowConnection(schemaName))
        { return columns(conn, sourceScope(conn, schemaName, catalog), table); }
    }

    public record TablePlan(String catalog, String table, List<String> selected, List<Map<String,Object>> columns, String sql) { }

    public record JoinPlan(String catalog, String leftTable, String rightTable, String sql, List<Map<String,Object>> rightColumns) { }
    public record JoinStepPlan(String sql, List<Map<String,Object>> rightColumns) { }

    public TablePlan plan(String schemaName, String catalog, String table, List<String> fields) throws Exception
    {
        requireModel(schemaName);
        return savedPlan(schemaName, catalog, table, fields);
    }

    // Internal consumption path: DatasetService must first check dataset view/query permissions.
    TablePlan savedPlan(String schemaName, String catalog, String table, List<String> fields) throws Exception
    {
        activeSource(schemaName);
        if (fields == null || fields.isEmpty() || fields.size() > 200)
        { throw new IllegalArgumentException("请选择 1～200 个字段"); }
        if(snapshot(schemaName)){
            snapshotScope(catalog,table);var columns=snapshotColumns(schemaName);
            var valid=columns.stream().map(c->(String)c.get("name")).collect(Collectors.toSet());
            var selected=new ArrayList<>(new LinkedHashSet<>(fields));
            if(!valid.containsAll(selected))throw new IllegalArgumentException("字段不存在");
            return new TablePlan("snapshot","data",selected,columns,"SELECT "+selected.stream().map(CatalogService::logical).collect(Collectors.joining(", "))+" FROM "+logical(schemaName)+".\"data\"");
        }
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            var scope = sourceScope(conn, schemaName, catalog);
            String cat = scope.name();
            List<Map<String,Object>> columns = columns(conn, scope, table);
            Set<String> valid = columns.stream().map(c -> (String)c.get("name")).collect(Collectors.toSet());
            List<String> selected = new ArrayList<>(new LinkedHashSet<>(fields));
            if (!valid.containsAll(selected)) { throw new IllegalArgumentException("字段不存在，请刷新后重新选择"); }
            List<String> quoted = new ArrayList<>();
            for (String field : selected) { quoted.add(logical(field)); }
            String sql = "SELECT " + String.join(", ",quoted) + " FROM " + logical(schemaName) + "." + logical(table);
            return new TablePlan(cat,table,selected,columns,sql);
        }
    }

    public JoinPlan planJoin(String schemaName, String catalog, String leftTable, List<String> leftFields,
                             String joinType, String rightTable, List<String> rightFields,
                             String leftKey, String rightKey) throws Exception
    {
        requireModel(schemaName);
        return savedJoinPlan(schemaName, catalog, leftTable, leftFields, joinType, rightTable, rightFields, leftKey, rightKey);
    }

    JoinPlan savedJoinPlan(String schemaName, String catalog, String leftTable, List<String> leftFields,
                           String joinType, String rightTable, List<String> rightFields,
                           String leftKey, String rightKey) throws Exception
    {
        activeSource(schemaName);
        if(snapshot(schemaName))throw new IllegalArgumentException("快照数据源暂不支持关联建模，请先在上游整理为完整数据表");
        if (leftTable == null || leftTable.isBlank() || rightTable == null || rightTable.isBlank())
        { throw new IllegalArgumentException("请选择关联表"); }
        if (leftKey == null || leftKey.isBlank() || rightKey == null || rightKey.isBlank())
        { throw new IllegalArgumentException("请指定关联键"); }
        if (leftFields == null || leftFields.isEmpty() || leftFields.size() > 200)
        { throw new IllegalArgumentException("左表字段无效"); }
        if (rightFields == null || rightFields.isEmpty() || rightFields.size() > 50)
        { throw new IllegalArgumentException("请选择 1～50 个右表字段"); }
        String jt = joinType == null ? "LEFT" : joinType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INNER", "LEFT", "RIGHT").contains(jt))
        { throw new IllegalArgumentException("不支持的关联类型: " + joinType); }
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            DatabaseMetaData meta = conn.getMetaData();
            var scope = sourceScope(conn, schemaName, catalog);
            String cat = scope.name();
            List<Map<String,Object>> leftCols = columns(conn, scope, leftTable);
            List<Map<String,Object>> rightCols = columns(conn, scope, rightTable);
            Set<String> leftValid = leftCols.stream().map(c -> (String) c.get("name")).collect(Collectors.toSet());
            Set<String> rightValid = rightCols.stream().map(c -> (String) c.get("name")).collect(Collectors.toSet());
            List<String> leftSelected = new ArrayList<>(new LinkedHashSet<>(leftFields));
            List<String> rightSelected = new ArrayList<>(new LinkedHashSet<>(rightFields));
            if (!leftValid.containsAll(leftSelected) || !leftValid.contains(leftKey))
            { throw new IllegalArgumentException("左表字段或关联键不存在"); }
            if (!rightValid.containsAll(rightSelected) || !rightValid.contains(rightKey))
            { throw new IllegalArgumentException("右表字段或关联键不存在"); }
            List<String> projections = new ArrayList<>();
            for (String field : leftSelected)
            { projections.add("t0." + logical(field)); }
            List<Map<String,Object>> pickedRight = new ArrayList<>();
            for (String field : rightSelected)
            {
                String alias = "r_" + field;
                projections.add("t1." + logical(field) + " AS " + logical(alias));
                rightCols.stream().filter(c -> field.equals(c.get("name"))).findFirst().ifPresent(pickedRight::add);
            }
            String from = logical(schemaName) + "." + logical(leftTable) + " t0 "
                    + jt + " JOIN " + logical(schemaName) + "." + logical(rightTable) + " t1 ON t0."
                    + logical(leftKey) + " = t1." + logical(rightKey);
            String sql = "SELECT " + String.join(", ", projections) + " FROM " + from;
            return new JoinPlan(cat, leftTable, rightTable, sql, pickedRight);
        }
    }

    /** Appends one validated physical table to an already projected, identifier-safe dataset query. */
    JoinStepPlan savedJoinStep(String schemaName, String catalog, String currentSql, String rightTable,
                               String joinType, String rightKey, List<String> rightFields,
                               String leftKey, String prefix, int aliasIndex) throws Exception {
        activeSource(schemaName);
        if (snapshot(schemaName)) throw new IllegalArgumentException("快照数据源暂不支持关联建模");
        if (currentSql == null || currentSql.isBlank() || leftKey == null || leftKey.isBlank())
            throw new IllegalArgumentException("关联左键无效");
        if (rightTable == null || rightTable.isBlank() || rightKey == null || rightKey.isBlank())
            throw new IllegalArgumentException("请选择关联表和右键");
        if (rightFields == null || rightFields.isEmpty() || rightFields.size() > 50)
            throw new IllegalArgumentException("每个关联最多选择 50 个右表字段");
        if (prefix == null || !prefix.matches("r[0-9]*_")) throw new IllegalArgumentException("关联字段前缀无效");
        String jt = joinType == null ? "LEFT" : joinType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INNER", "LEFT", "RIGHT").contains(jt)) throw new IllegalArgumentException("不支持的关联类型: " + joinType);
        try (Connection conn = engine.borrowConnection(schemaName)) {
            var scope = sourceScope(conn, schemaName, catalog);
            List<Map<String,Object>> rightCols = columns(conn, scope, rightTable);
            Set<String> valid = rightCols.stream().map(c -> (String)c.get("name")).collect(Collectors.toSet());
            List<String> selected = new ArrayList<>(new LinkedHashSet<>(rightFields));
            if (!valid.contains(rightKey) || !valid.containsAll(selected)) throw new IllegalArgumentException("右表键或字段不存在，请刷新后重试");
            String alias = "t" + aliasIndex;
            List<String> projection = new ArrayList<>();
            for (String field : selected) projection.add(alias + "." + logical(field) + " AS " + logical(prefix + field));
            List<Map<String,Object>> picked = rightCols.stream().filter(c -> selected.contains(c.get("name"))).toList();
            String relation = logical(schemaName) + "." + logical(rightTable);
            String sql = "SELECT bi_prev.*, " + String.join(", ", projection) + " FROM (" + currentSql + ") bi_prev "
                    + jt + " JOIN " + relation + " " + alias + " ON bi_prev." + logical(leftKey) + " = " + alias + "." + logical(rightKey);
            return new JoinStepPlan(sql, picked);
        }
    }

    public Map<String,Object> preview(String schemaName, String catalog, String table, List<String> fields, int maxRows) throws Exception
    {
        requireModel(schemaName);
        return savedPreview(schemaName, catalog, table, fields, maxRows);
    }

    Map<String,Object> savedPreview(String schemaName, String catalog, String table, List<String> fields, int maxRows) throws Exception
    {
        return savedPreview(schemaName,catalog,table,fields,maxRows,DatasetDataPolicy.Scope.unrestricted());
    }

    Map<String,Object> savedPreview(String schemaName, String catalog, String table, List<String> fields, int maxRows, DatasetDataPolicy.Scope scope) throws Exception
    {
        if (maxRows < 1 || maxRows > 100) { throw new IllegalArgumentException("预览行数须在 1～100 之间"); }
        TablePlan plan = savedPlan(schemaName, catalog, table, fields);
        long start = System.currentTimeMillis();
        try (Connection conn = snapshot(schemaName)?engine.open(List.of(activeSource(schemaName))):engine.borrowConnection(schemaName))
        {
            Map<String,String> types=new LinkedHashMap<>();
            plan.columns().stream().filter(c -> fields.contains((String)c.get("name"))).forEach(c -> types.put((String)c.get("name"),String.valueOf(c.get("type"))));
            var compiled=DataPolicyCompiler.compile(plan.selected(),types,conn.getMetaData().getIdentifierQuoteString().trim(),scope);
            String sql="SELECT "+compiled.projection()+" FROM "+(snapshot(schemaName)?logical(schemaName)+".\"data\"":sourceScope(conn,schemaName,plan.catalog()).relation(conn.getMetaData(),table))
                    +(compiled.predicate().isEmpty()?"":" WHERE "+compiled.predicate());
            sql=com.wanfo.bi.datasource.JdbcSourceScope.limit(conn.getMetaData().getDatabaseProductName(),sql,maxRows);
            try (PreparedStatement st=conn.prepareStatement(sql)) {
            st.setMaxRows(maxRows); st.setQueryTimeout(10);
            for(int p=0;p<compiled.params().size();p++) st.setObject(p+1,compiled.params().get(p));
            try (ResultSet rs = st.executeQuery())
            {
                List<Map<String,Object>> rows = new ArrayList<>();
                while (rows.size() < maxRows && rs.next())
                {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for (int i=1;i<=compiled.columns().size();i++)
                    {
                        Object value = com.wanfo.bi.query.JdbcResultValues.read(rs, i);
                        if (value instanceof Long || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger)
                        { value = value.toString(); }
                        row.put(compiled.columns().get(i-1),value);
                    }
                    rows.add(row);
                }
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("ok",true); result.put("columns",compiled.columns()); result.put("rows",rows);
                result.put("rowCount",rows.size()); result.put("costMs",System.currentTimeMillis()-start);
                return result;
            }
            }
        }
    }

    public Map<String,Object> tree(String schemaName) throws Exception
    {
        List<String> catalogs = listCatalogs(schemaName);
        return Map.of("schemaName",schemaName,"catalogCount",catalogs.size(),"catalogs",catalogs);
    }
}
