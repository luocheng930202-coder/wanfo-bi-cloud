package com.wanfo.bi.security;
import java.util.*;
import java.math.BigDecimal;
/** Compiles only validated identifiers and enum operators. Values always use JDBC parameters. */
public final class DataPolicyCompiler {
    private DataPolicyCompiler() { }
    public record Compiled(String projection,String predicate,List<Object> params,List<String> columns) { }
    public static Compiled compile(List<String> selected,Map<String,String> types,String quote,DatasetDataPolicy.Scope scope) {
        if(quote==null || quote.isBlank()) throw new IllegalArgumentException("驱动不支持标识符引用");
        List<String> projection=new ArrayList<>(),columns=new ArrayList<>(),conditions=new ArrayList<>();List<Object> params=new ArrayList<>();
        Set<String> referenced=new HashSet<>(scope.denied());referenced.addAll(scope.masked());scope.rows().forEach(r->referenced.add(r.fieldName()));
        if(!types.keySet().containsAll(referenced) || !types.keySet().containsAll(selected)) throw new BiAccessException(403,"数据权限字段已失效，请联系管理员");
        for(String field:selected) {
            if(scope.denied().contains(field)) continue;
            String q=quote+field.replace(quote,quote+quote)+quote;
            projection.add(scope.masked().contains(field)?"'***' AS "+q:q);columns.add(field);
        }
        if(columns.isEmpty()) throw new BiAccessException(403,"没有可查询的字段");
        Map<String,String> ops=Map.of("EQ","=","NE","<>","GT",">","GE",">=","LT","<","LE","<=");
        for(var r:scope.rows()) {
            String field=quote+r.fieldName().replace(quote,quote+quote)+quote;
            if("IS_NULL".equals(r.ruleKind())) {conditions.add(field+" IS NULL");continue;}
            if("IS_NOT_NULL".equals(r.ruleKind())) {conditions.add(field+" IS NOT NULL");continue;}
            String op=ops.get(r.ruleKind());
            if(op==null || r.ruleValue()==null) throw new BiAccessException(403,"数据权限规则无效");
            String type=types.get(r.fieldName()).toLowerCase(Locale.ROOT);
            Object value=r.ruleValue();
            if(type.matches(".*(int|decimal|numeric|float|double|real).*")) {
                try {value=new BigDecimal(r.ruleValue());} catch(NumberFormatException e) {throw new BiAccessException(403,"数据权限数值无效，请联系管理员");}
            }
            conditions.add(field+" "+op+" ?");params.add(value);
        }
        return new Compiled(String.join(", ",projection),String.join(" AND ",conditions),List.copyOf(params),List.copyOf(columns));
    }
}
