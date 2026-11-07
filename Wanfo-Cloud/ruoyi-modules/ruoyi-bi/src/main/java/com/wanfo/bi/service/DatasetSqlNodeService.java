package com.wanfo.bi.service;

import java.sql.*;
import java.util.*;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.query.JdbcResultValues;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;
import org.springframework.stereotype.Service;

/** Bounded, single-connector SQL nodes; authorization belongs to the calling service. */
@Service
public class DatasetSqlNodeService {
    private final CalciteQueryEngine engine;
    private final CatalogService catalog;
    public DatasetSqlNodeService(CalciteQueryEngine engine,CatalogService catalog){this.engine=engine;this.catalog=catalog;}

    public static String normalize(String sql) throws Exception {
        if(sql==null || sql.isBlank() || sql.length()>100000)throw new IllegalArgumentException("SQL 不能为空且不能超过 100000 字符");
        String text=sql.trim();if(text.endsWith(";"))text=text.substring(0,text.length()-1);
        var node=SqlParser.create(text,SqlParser.config().withLex(Lex.MYSQL_ANSI)).parseQuery();
        if(!node.isA(SqlKind.QUERY))throw new IllegalArgumentException("仅支持单条只读 SELECT / WITH 查询");
        return node.toSqlString(CalciteSqlDialect.DEFAULT).getSql();
    }
    public static String project(String sql,List<String> fields){
        if(fields==null || fields.isEmpty() || fields.size()>200)throw new IllegalArgumentException("请选择 1～200 个字段");
        return "SELECT "+String.join(", ",fields.stream().map(DatasetSqlNodeService::identifier).toList())+" FROM ("+sql+") AS bi_sql_node";
    }
    private static String identifier(String name){return "\""+name.replace("\"","\"\"")+"\"";}
    public static boolean isSqlNode(BiDatasetTableDO row){
        if(row.getModelJson()==null)return false;
        return new tools.jackson.databind.ObjectMapper().readTree(row.getModelJson()).hasNonNull("sourceSql");
    }

    public Map<String,Object> inspect(BiDatasource source,String sql,String sourceCatalog,List<String> selected,DatasetJoinModel model,int maxRows) throws Exception {
        return inspect(source,sql,sourceCatalog,selected,model,maxRows,Map.of(),true);
    }
    public Map<String,Object> inspect(BiDatasource source,String sql,String sourceCatalog,List<String> selected,DatasetJoinModel model,int maxRows,Map<String,List<String>> values,boolean edit) throws Exception {
        if(maxRows<1 || maxRows>100)throw new IllegalArgumentException("预览行数须在 1～100 之间");
        catalog.requireModel(source.getSchemaName());
        var bound=com.wanfo.bi.query.SqlTemplateParameters.compile(sql,model==null?null:model.getSqlParameters(),values,edit);
        String normalized=sql.contains("$DE_PARAM{")?sql.trim().replaceFirst(";\\s*$", ""):normalize(sql),base=normalize(bound.sql());
        if(selected!=null && !selected.isEmpty())base=project(base,selected);
        Set<String> available=new LinkedHashSet<>(selected==null?List.of():selected);
        var joins=model==null?List.<DatasetJoinModel.JoinStep>of():model.normalizedJoins();
        if(joins.size()>8)throw new IllegalArgumentException("单个数据集最多配置 8 条关联");
        for(int i=0;i<joins.size();i++){
            var join=joins.get(i);if(join==null || !available.contains(join.getLeftKey()))throw new IllegalArgumentException("关联左键不可用");
            String prefix=DatasetJoinModel.fieldPrefix(i+1);
            base=catalog.savedJoinStep(source.getSchemaName(),sourceCatalog,base,join.getRightTable(),join.getJoinType(),join.getRightKey(),join.getRightFields(),join.getLeftKey(),prefix,i+1).sql();
            for(String field:join.getRightFields())if(!available.add(prefix+field))throw new IllegalArgumentException("关联字段名重复");
            if(available.size()>200)throw new IllegalArgumentException("关联后最多 200 个字段");
        }
        long started=System.currentTimeMillis();
        try(Connection connection=engine.open(List.of(source))){
            return readQuery(connection,base,bound.params(),model==null?null:model.getDerivedFields(),maxRows,normalized,started);
        }
    }
    private Map<String,Object> readQuery(Connection connection,String base,List<Object> bindings,List<com.wanfo.bi.domain.dataset.DatasetDerivedField> derived,int maxRows,String normalized,long started) throws Exception {
        try(PreparedStatement statement=connection.prepareStatement("SELECT * FROM ("+base+") AS bi_preview LIMIT "+maxRows)){
            for(int i=0;i<bindings.size();i++)statement.setObject(i+1,bindings.get(i));
            statement.setMaxRows(maxRows);statement.setQueryTimeout(10);
            try(ResultSet rs=statement.executeQuery()){
                var metadata=rs.getMetaData();int count=metadata.getColumnCount();if(count<1 || count>200)throw new IllegalArgumentException("SQL 结果须包含 1～200 个字段");
                List<String> columns=new ArrayList<>();List<Map<String,Object>> definitions=new ArrayList<>();Set<String> names=new HashSet<>();
                for(int i=1;i<=count;i++){
                    String name=metadata.getColumnLabel(i);if(name==null || name.isBlank() || name.length()>200 || !names.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("结果字段名为空、过长或重复，请为表达式及同名字段设置唯一别名");
                    columns.add(name);int type=metadata.getColumnType(i);boolean numeric=Set.of(Types.TINYINT,Types.SMALLINT,Types.INTEGER,Types.BIGINT,Types.FLOAT,Types.REAL,Types.DOUBLE,Types.NUMERIC,Types.DECIMAL).contains(type);
                    definitions.add(Map.of("name",name,"type",metadata.getColumnTypeName(i),"suggestAs",numeric?"metric":"dimension","nullable",metadata.isNullable(i)!=ResultSetMetaData.columnNoNulls));
                }
                if(derived!=null && !derived.isEmpty()){
                    var fields=new ArrayList<com.wanfo.bi.domain.BiDatasetFieldDO>();for(var definition:definitions){var field=new com.wanfo.bi.domain.BiDatasetFieldDO();field.setOriginName(String.valueOf(definition.get("name")));field.setOriginType(String.valueOf(definition.get("type")));field.setOrderNum(fields.size());fields.add(field);}
                    fields.addAll(com.wanfo.bi.query.DatasetDerivedFields.compile(fields,derived));
                    String expanded=com.wanfo.bi.query.DatasetQueryBaseBuilder.expand(base,fields);
                    var derivedResult=readQuery(connection,expanded,bindings,null,maxRows,normalized,started);derivedResult.put("normalizedSql",normalized);derivedResult.put("costMs",System.currentTimeMillis()-started);return derivedResult;
                }
                List<Map<String,Object>> rows=new ArrayList<>();while(rows.size()<maxRows && rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=count;i++){Object value=JdbcResultValues.read(rs,i);if(value instanceof Long || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger)value=value.toString();row.put(columns.get(i-1),value);}rows.add(row);
                }
                Map<String,Object> result=new LinkedHashMap<>();result.put("ok",true);result.put("columns",columns);result.put("fieldMetadata",definitions);result.put("rows",rows);result.put("rowCount",rows.size());result.put("costMs",System.currentTimeMillis()-started);result.put("normalizedSql",normalized);return result;
            }
        }
    }
}
