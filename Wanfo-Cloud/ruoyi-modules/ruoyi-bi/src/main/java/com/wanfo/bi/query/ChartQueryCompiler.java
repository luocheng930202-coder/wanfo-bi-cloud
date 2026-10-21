package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import org.springframework.stereotype.Component;

/** Validated query AST to parameterized Calcite SQL. Security restrictions are supplied as the input relation. */
@Component
public class ChartQueryCompiler {
    public static final int DEFAULT_LIMIT=1000;
    public static final int MAX_LIMIT=10000;
    public static class Compiled {
        public String sql;
        public List<Object> params=new ArrayList<>();
    }
    /** Compatibility for callers without type metadata. Production services use the typed overload. */
    public Compiled compile(QueryModel model,String base,Collection<String> fields) {
        Map<String,String> types=new LinkedHashMap<>();
        if(fields!=null) fields.forEach(f->types.put(f,""));
        return compile(model,base,types);
    }
    public Compiled compile(QueryModel model,String base,Map<String,String> types) {
        validate(model,types);
        if(base==null || base.isBlank()) throw invalid("数据集查询定义为空");
        var predicate=FilterTreeCompiler.compile(model.getFilters(),model.getFilterTree(),types);
        List<String> select=new ArrayList<>(),group=new ArrayList<>();
        Map<String,String> output=new LinkedHashMap<>(), origins=new LinkedHashMap<>();
        Set<String> aliases=new HashSet<>();
        boolean aggregate=model.getMetrics().stream().anyMatch(m->!agg(m).equals("NONE"));
        for(var d:model.getDimensions()) {
            String name=alias(d.getAlias(),d.getField());addAlias(name,aliases);
            String col=TimeGrain.expression(d,types.get(d.getField()));
            select.add(col+" AS "+FilterTreeCompiler.quote(name));group.add(col);
            output.put(name,name);origins.putIfAbsent(d.getField(),name);
        }
        for(var m:model.getMetrics()) {
            String a=agg(m),name=alias(m.getAlias(),defaultAlias(m));addAlias(name,aliases);
            if(a.equals("NONE") && aggregate) throw invalid("原始度量不能与聚合度量混用，请选择明确的聚合方式");
            String col=FilterTreeCompiler.quote(m.getField());
            String expr=switch(a) {case "NONE"->col;case "COUNT_DISTINCT"->"COUNT(DISTINCT "+col+")";default->a+"("+col+")";};
            select.add(expr+" AS "+FilterTreeCompiler.quote(name));
            if(a.equals("NONE") && !model.getDimensions().isEmpty()) group.add(col);
            output.put(name,name);
            // A source used by multiple metrics is ambiguous; require an explicit output alias.
            if(origins.containsKey(m.getField())) origins.put(m.getField(),null);else origins.put(m.getField(),name);
        }
        StringBuilder sql=new StringBuilder("SELECT "+String.join(", ",select)+" FROM ("+base+") AS bi_src");
        if(!predicate.sql().isEmpty()) sql.append(" WHERE ").append(predicate.sql());
        if(!group.isEmpty()) sql.append(" GROUP BY ").append(String.join(", ",new LinkedHashSet<>(group)));
        List<String> order=new ArrayList<>();
        for(var o:model.getOrderBys()) {
            if(o==null || o.getField()==null) throw invalid("排序项不能为空");
            String name=output.containsKey(o.getField())?o.getField():origins.get(o.getField());
            if(name==null) throw invalid("排序字段须为明确的当前查询输出");
            String direction=o.getOrder()==null?"ASC":o.getOrder().toUpperCase(Locale.ROOT);
            if(!Set.of("ASC","DESC").contains(direction)) throw invalid("排序方向仅支持 ASC/DESC");
            boolean calendar=model.getDimensions().stream().anyMatch(d->d.getTimeGrain()!=null && alias(d.getAlias(),d.getField()).equals(name));
            order.add(FilterTreeCompiler.quote(name)+" "+direction+(calendar?(direction.equals("ASC")?" NULLS FIRST":" NULLS LAST"):""));
        }
        if(order.isEmpty()) {
            for(var d:model.getDimensions()) if(d.getTimeGrain()!=null) {
                order.add(FilterTreeCompiler.quote(alias(d.getAlias(),d.getField()))+" ASC NULLS FIRST");
                break;
            }
        }
        if(order.isEmpty() && !model.getMetrics().isEmpty()) {
            var m=model.getMetrics().get(0);order.add(FilterTreeCompiler.quote(alias(m.getAlias(),defaultAlias(m)))+" DESC");
        }
        if(!order.isEmpty()) sql.append(" ORDER BY ").append(String.join(", ",order));
        sql.append(" LIMIT ").append(model.getLimit()==null?DEFAULT_LIMIT:model.getLimit());
        Compiled compiled=new Compiled();compiled.sql=sql.toString();compiled.params=predicate.params();return compiled;
    }
    private void validate(QueryModel m,Map<String,String> types) {
        if(m==null) throw invalid("查询模型不能为空");
        if(m.getDatasetId()==null || m.getDatasetId()<1) throw invalid("请选择有效数据集");
        if(m.getDimensions()==null || m.getMetrics()==null || m.getFilters()==null || m.getOrderBys()==null) throw invalid("查询列表不能为 null");
        if(m.getDimensions().size()+m.getMetrics().size()<1 || m.getDimensions().size()+m.getMetrics().size()>200 || m.getOrderBys().size()>200) throw invalid("查询须包含 1～200 个输出字段，排序不超过 200 项");
        if(m.getLimit()!=null && (m.getLimit()<1 || m.getLimit()>MAX_LIMIT)) throw invalid("查询行数须在 1～10000 之间");
        for(var d:m.getDimensions()) {if(d==null)throw invalid("维度不能为空");field(d.getField(),types);TimeGrain.expression(d,types.get(d.getField()));}
        for(var metric:m.getMetrics()) {
            if(metric==null)throw invalid("度量不能为空");field(metric.getField(),types);
            String a=agg(metric);if(!QueryModel.AGG_WHITELIST.contains(a)) throw invalid("不支持的聚合函数");
            String type=types.get(metric.getField());
            if(Set.of("SUM","AVG").contains(a) && type!=null && !type.isEmpty() && !FilterTreeCompiler.numeric(type)) throw invalid("SUM/AVG 仅支持数值字段");
        }
    }
    private void field(String name,Map<String,String> types) {
        if(name==null || !types.containsKey(name)) throw invalid("字段不存在或无权访问");
    }
    private void addAlias(String name,Set<String> aliases) {
        if(name.length()>128 || name.codePoints().anyMatch(Character::isISOControl) || !aliases.add(name.toLowerCase(Locale.ROOT))) throw invalid("输出别名须唯一且不超过 128 字符，不能包含控制字符");
    }
    private static String agg(QueryModel.Metric m) {return m.getAgg()==null?"NONE":m.getAgg().toUpperCase(Locale.ROOT);}
    private static String defaultAlias(QueryModel.Metric m) {return agg(m).equals("NONE")?m.getField():agg(m).toLowerCase(Locale.ROOT)+"_"+m.getField();}
    private static String alias(String name,String fallback) {return name==null || name.isBlank()?fallback:name;}
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
}
