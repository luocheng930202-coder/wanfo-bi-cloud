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

    private String scopedCatalog(Connection conn, String requested) throws SQLException
    {
        String product = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (!product.contains("mysql") && !product.contains("mariadb"))
        { throw new IllegalArgumentException("当前库表建模支持 MySQL 协议数据源，其他数据库适配尚未开放"); }
        String configured = conn.getCatalog();
        if (configured == null || configured.isBlank())
        { throw new IllegalArgumentException("请先在数据源连接串中指定建模数据库"); }
        if (Set.of("mysql", "sys", "information_schema", "performance_schema").contains(configured.toLowerCase(Locale.ROOT)))
        { throw new BiAccessException(403, "系统库不可用于业务建模"); }
        if (requested != null && !requested.isBlank() && !configured.equals(requested))
        { throw new BiAccessException(403, "该数据库不在此数据源的建模范围内"); }
        return configured;
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
        String q = md.getIdentifierQuoteString().trim();
        if (q.isEmpty()) { throw new IllegalArgumentException("数据库驱动不支持标识符引用"); }
        return q + identifier.replace(q, q + q) + q;
    }

    public List<String> listCatalogs(String schemaName) throws Exception
    {
        requireModel(schemaName);
        try (Connection conn = engine.borrowConnection(schemaName)) { return List.of(scopedCatalog(conn, null)); }
    }

    public List<Map<String,Object>> listTables(String schemaName, String catalog) throws Exception
    {
        requireModel(schemaName);
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            String cat = scopedCatalog(conn, catalog);
            List<Map<String,Object>> tables = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getTables(cat, null, "%", new String[]{"TABLE","VIEW"}))
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

    private List<Map<String,Object>> columns(Connection conn, String catalog, String table) throws Exception
    {
        if (table == null || table.isBlank()) { throw new IllegalArgumentException("请选择物理表"); }
        DatabaseMetaData md = conn.getMetaData();
        boolean exists = false;
        try (ResultSet rs = md.getTables(catalog, null, pattern(md, table), new String[]{"TABLE","VIEW"}))
        { while (rs.next()) { if (table.equals(rs.getString("TABLE_NAME"))) { exists = true; break; } } }
        if (!exists) { throw new IllegalArgumentException("表不存在或不在当前建模范围内"); }
        Set<String> primary = new HashSet<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, null, table))
        { while (rs.next()) { primary.add(rs.getString("COLUMN_NAME")); } }
        List<Map<String,Object>> result = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, null, pattern(md, table), "%"))
        {
            while (rs.next())
            {
                if (!table.equals(rs.getString("TABLE_NAME"))) { continue; }
                String name = rs.getString("COLUMN_NAME"), type = rs.getString("TYPE_NAME");
                String lower = type == null ? "" : type.toLowerCase(Locale.ROOT);
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
        try (Connection conn = engine.borrowConnection(schemaName))
        { return columns(conn, scopedCatalog(conn, catalog), table); }
    }

    public record TablePlan(String catalog, String table, List<String> selected, List<Map<String,Object>> columns, String sql) { }

    public record JoinPlan(String catalog, String leftTable, String rightTable, String sql, List<Map<String,Object>> rightColumns) { }

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
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            String cat = scopedCatalog(conn, catalog);
            List<Map<String,Object>> columns = columns(conn, cat, table);
            Set<String> valid = columns.stream().map(c -> (String)c.get("name")).collect(Collectors.toSet());
            List<String> selected = new ArrayList<>(new LinkedHashSet<>(fields));
            if (!valid.containsAll(selected)) { throw new IllegalArgumentException("字段不存在，请刷新后重新选择"); }
            List<String> quoted = new ArrayList<>();
            for (String field : selected) { quoted.add(quote(conn.getMetaData(), field)); }
            String sql = "SELECT " + String.join(", ",quoted) + " FROM " + quote(conn.getMetaData(),cat) + "." + quote(conn.getMetaData(),table);
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
            String cat = scopedCatalog(conn, catalog);
            List<Map<String,Object>> leftCols = columns(conn, cat, leftTable);
            List<Map<String,Object>> rightCols = columns(conn, cat, rightTable);
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
            { projections.add("t0." + quote(meta, field)); }
            List<Map<String,Object>> pickedRight = new ArrayList<>();
            for (String field : rightSelected)
            {
                String alias = "r_" + field;
                projections.add("t1." + quote(meta, field) + " AS " + quote(meta, alias));
                rightCols.stream().filter(c -> field.equals(c.get("name"))).findFirst().ifPresent(pickedRight::add);
            }
            String from = quote(meta, cat) + "." + quote(meta, leftTable) + " t0 "
                    + jt + " JOIN " + quote(meta, cat) + "." + quote(meta, rightTable) + " t1 ON t0."
                    + quote(meta, leftKey) + " = t1." + quote(meta, rightKey);
            String sql = "SELECT " + String.join(", ", projections) + " FROM " + from;
            return new JoinPlan(cat, leftTable, rightTable, sql, pickedRight);
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
        try (Connection conn = engine.borrowConnection(schemaName))
        {
            Map<String,String> types=new LinkedHashMap<>();
            plan.columns().stream().filter(c -> fields.contains((String)c.get("name"))).forEach(c -> types.put((String)c.get("name"),String.valueOf(c.get("type"))));
            var compiled=DataPolicyCompiler.compile(plan.selected(),types,conn.getMetaData().getIdentifierQuoteString().trim(),scope);
            String sql="SELECT "+compiled.projection()+" FROM "+quote(conn.getMetaData(),plan.catalog())+"."+quote(conn.getMetaData(),table)
                    +(compiled.predicate().isEmpty()?"":" WHERE "+compiled.predicate())+" LIMIT "+maxRows;
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
                        Object value = rs.getObject(i);
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
