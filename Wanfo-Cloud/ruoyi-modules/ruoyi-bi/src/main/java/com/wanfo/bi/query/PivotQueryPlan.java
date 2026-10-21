package com.wanfo.bi.query;

import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.domain.query.QueryModel;

/** One bounded UNION query computes each prefix grouping from the same secured, filtered source. */
public final class PivotQueryPlan {
    private PivotQueryPlan() { }
    public static final int MAX_GROUPS=1000;
    public record Plan(ChartQueryCompiler.Compiled query,int maxRows,String rowDepthKey,String colDepthKey) { }
    public record Result(List<Map<String,Object>> rows,Map<String,Object> totals) { }
    private static String output(QueryModel.Dimension d) {return d.getAlias()==null || d.getAlias().isBlank()?d.getField():d.getAlias();}
    private static String output(QueryModel.Metric m) {return m.getAlias()==null || m.getAlias().isBlank()?m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField():m.getAlias();}
    public static void validate(QueryModel model) {
        var p=model.getPivot();if(p==null)return;
        if(p.rows()==null || p.columns()==null || p.rows().isEmpty() || p.rows().size()>3 || p.columns().isEmpty() || p.columns().size()>2)
            throw new IllegalArgumentException("透视查询须包含 1～3 个行维度和 1～2 个列维度");
        List<String> axes=new ArrayList<>(p.rows());axes.addAll(p.columns());
        if(axes.stream().anyMatch(Objects::isNull) || new HashSet<>(axes).size()!=axes.size()
                || axes.size()!=model.getDimensions().size() || !new HashSet<>(axes).equals(new HashSet<>(model.getDimensions().stream().map(QueryModel.Dimension::getField).toList())))
            throw new IllegalArgumentException("透视行列字段须与查询维度完全一致且不重复");
        if(model.getMetrics().isEmpty() || model.getMetrics().size()>3 || model.getMetrics().stream().anyMatch(m->m.getAgg()==null || m.getAgg().equalsIgnoreCase("NONE")))
            throw new IllegalArgumentException("透视查询须包含 1～3 个聚合指标");
        if(model.getLimit()!=null && model.getLimit()>MAX_GROUPS)throw new IllegalArgumentException("透视明细最多展示 1000 个分组");
        if(model.getDimensions().stream().anyMatch(d->d.getTimeGrain()!=null))throw new IllegalArgumentException("透视时间字段请先在数据集中定义分组，不能混用时间分桶");
    }
    public static Plan compile(QueryModel model,String base,Map<String,String> types,ChartQueryCompiler compiler) {
        return compile(model,base,types,compiler,List.of());
    }
    public static Plan compile(QueryModel model,String base,Map<String,String> types,ChartQueryCompiler compiler,List<Object> securityParams) {
        compiler.compile(model,base,types); // All field, alias, filter, sort and permission checks run first.
        var p=model.getPivot();if(p==null)throw new IllegalArgumentException("缺少透视轴配置");
        List<String> names=new ArrayList<>();model.getDimensions().forEach(d->names.add(output(d)));model.getMetrics().forEach(m->names.add(output(m)));
        String r="__bi_pivot_row_depth",c="__bi_pivot_col_depth";
        while(contains(names,r))r+="_";names.add(r);while(contains(names,c))c+="_";
        List<String> parts=new ArrayList<>();var compiled=new ChartQueryCompiler.Compiled();
        for(int rd=p.rows().size();rd>=0;rd--)for(int cd=p.columns().size();cd>=0;cd--) {
            Set<String> selected=new HashSet<>(p.rows().subList(0,rd));selected.addAll(p.columns().subList(0,cd));
            boolean leaf=rd==p.rows().size() && cd==p.columns().size();
            var q=new QueryModel();q.setDatasetId(model.getDatasetId());q.setDimensions(model.getDimensions().stream().filter(d->selected.contains(d.getField())).toList());
            q.setMetrics(model.getMetrics());q.setFilters(model.getFilters());q.setFilterTree(model.getFilterTree());
            q.setOrderBys(leaf?model.getOrderBys():List.of());q.setLimit((leaf?(model.getLimit()==null?MAX_GROUPS:model.getLimit()):MAX_GROUPS)+1);
            var child=compiler.compile(q,base,types);compiled.params.addAll(securityParams);compiled.params.addAll(child.params);
            List<String> projection=new ArrayList<>();
            for(var d:model.getDimensions())projection.add((selected.contains(d.getField())?FilterTreeCompiler.quote(output(d)):"NULL")+" AS "+FilterTreeCompiler.quote(output(d)));
            for(var metric:model.getMetrics())projection.add(FilterTreeCompiler.quote(output(metric)));
            projection.add(rd+" AS "+FilterTreeCompiler.quote(r));projection.add(cd+" AS "+FilterTreeCompiler.quote(c));
            parts.add("SELECT "+String.join(", ",projection)+" FROM ("+child.sql+") AS pivot_level");
        }
        compiled.sql=String.join(" UNION ALL ",parts);
        return new Plan(compiled,parts.size()*(MAX_GROUPS+1),r,c);
    }
    private static boolean contains(List<String> names,String value){return names.stream().anyMatch(n->n.equalsIgnoreCase(value));}
    private static Object exact(Object value){return value instanceof BigDecimal d?d.toPlainString():value instanceof Number?value.toString():value;}
    public static Result decode(QueryModel model,Plan plan,List<Map<String,Object>> raw) {
        var p=model.getPivot();int limit=model.getLimit()==null?MAX_GROUPS:model.getLimit();
        Map<String,String> outputs=new LinkedHashMap<>();model.getDimensions().forEach(d->outputs.put(d.getField(),output(d)));
        var leaf=new ArrayList<Map<String,Object>>();var cells=new ArrayList<Map<String,Object>>();var counts=new HashMap<String,Integer>();boolean unavailable=false;
        for(var source:raw) {
            int rd=((Number)source.get(plan.rowDepthKey())).intValue(),cd=((Number)source.get(plan.colDepthKey())).intValue();
            Map<String,Object> values=new LinkedHashMap<>();for(var metric:model.getMetrics())values.put(output(metric),exact(source.get(output(metric))));
            if(rd==p.rows().size() && cd==p.columns().size()) {
                Map<String,Object> row=new LinkedHashMap<>();for(var d:model.getDimensions())row.put(output(d),source.get(output(d)));row.putAll(values);leaf.add(row);continue;
            }
            if(counts.merge(rd+":"+cd,1,Integer::sum)>MAX_GROUPS)unavailable=true;
            Map<String,Object> dimensions=new LinkedHashMap<>();for(String f:p.rows().subList(0,rd))dimensions.put(outputs.get(f),source.get(outputs.get(f)));for(String f:p.columns().subList(0,cd))dimensions.put(outputs.get(f),source.get(outputs.get(f)));
            cells.add(Map.of("rowDepth",rd,"colDepth",cd,"dimensions",dimensions,"values",values));
        }
        Map<String,Object> totals=new LinkedHashMap<>();totals.put("status",unavailable?"unavailable":"complete");totals.put("scope","filtered");
        totals.put("rowFields",p.rows().stream().map(outputs::get).toList());totals.put("colFields",p.columns().stream().map(outputs::get).toList());totals.put("metricKeys",model.getMetrics().stream().map(PivotQueryPlan::output).toList());
        totals.put("detailTruncated",leaf.size()>limit);totals.put("cells",unavailable?List.of():cells);
        if(unavailable)totals.put("reason","汇总分组超过 1000 个，请缩小筛选范围或减少维度；已关闭汇总，避免显示不完整结果");
        return new Result(new ArrayList<>(leaf.subList(0,Math.min(limit,leaf.size()))),totals);
    }
}
