package com.wanfo.bi.query;

import java.time.*;
import java.math.*;
import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;

/** Calendar comparisons query a bounded prior range through the same secured input relation. */
public final class TimeComparison {
    private TimeComparison() { }
    public record Field(String key,String baseline,String difference,String rate) { }
    public record Result(List<String> columns,List<Map<String,Object>> rows,Map<String,Object> metadata,Map<String,String> labels) { }
    public static void validate(QueryModel q){
        if(q.getTimeComparison()==null)return;
        if(!Set.of("previous","year").contains(q.getTimeComparison()))throw new IllegalArgumentException("时间比较仅支持同比或环比");
        if(q.getPivot()!=null || q.getDimensions().size()!=1 || q.getDimensions().get(0).getTimeGrain()==null || q.getMetrics().isEmpty() || q.getMetrics().size()>3 || q.getMetrics().stream().anyMatch(m->m.getAgg()==null || m.getAgg().equalsIgnoreCase("NONE")))throw new IllegalArgumentException("时间比较需要一个设置粒度的时间维度及 1～3 个聚合指标");
        if(q.getLimit()!=null && q.getLimit()>1000)throw new IllegalArgumentException("时间比较最多展示 1000 个周期");
        var d=q.getDimensions().get(0);if(q.getOrderBys().size()>1 || q.getOrderBys().stream().anyMatch(o->o==null || !Objects.equals(o.getField(),d.getField()) && !Objects.equals(o.getField(),key(d))))throw new IllegalArgumentException("时间比较仅支持按时间排序");
        for(var filter:q.getFilters())shiftFilter(filter,q);shiftTree(q.getFilterTree(),q);
    }
    private static String key(QueryModel.Dimension d){return d.getAlias()==null || d.getAlias().isBlank()?d.getField():d.getAlias();}
    public static LocalDate previous(LocalDate date,String grain,String mode){
        if("year".equals(mode))return "week".equals(grain)?date.minusWeeks(52):date.minusYears(1);
        return switch(grain){case "day"->date.minusDays(1);case "week"->date.minusWeeks(1);case "month"->date.minusMonths(1);case "quarter"->date.minusMonths(3);case "year"->date.minusYears(1);default->throw new IllegalArgumentException("无效时间粒度");};
    }
    private static LocalDate next(LocalDate date,String grain){return switch(grain){case "day"->date.plusDays(1);case "week"->date.plusWeeks(1);case "month"->date.plusMonths(1);case "quarter"->date.plusMonths(3);case "year"->date.plusYears(1);default->throw new IllegalArgumentException("无效时间粒度");};}
    private static LocalDate shiftedDate(LocalDate date,QueryModel q,boolean upper){
        String grain=q.getDimensions().get(0).getTimeGrain();var prior=previous(date,grain,q.getTimeComparison());
        return upper && Set.of("month","quarter","year").contains(grain) && date.getDayOfMonth()==date.lengthOfMonth()?prior.withDayOfMonth(prior.lengthOfMonth()):prior;
    }
    private static Object shifted(Object value,QueryModel q,boolean upper){
        if(value instanceof Collection<?> values)return values.stream().map(v->shifted(v,q,upper)).toList();
        if(!(value instanceof String text))throw new IllegalArgumentException("比较时间筛选请使用 ISO 日期或本地日期时间");
        try{
            if(text.length()==10)return shiftedDate(LocalDate.parse(text),q,upper).toString();
            var dt=LocalDateTime.parse(text.replace(' ','T'));return shiftedDate(dt.toLocalDate(),q,upper).atTime(dt.toLocalTime()).toString().replace('T',' ');
        }catch(java.time.DateTimeException ex){throw new IllegalArgumentException("比较时间筛选请使用 yyyy-MM-dd 或本地日期时间");}
    }
    private static QueryModel.Filter shiftFilter(QueryModel.Filter f,QueryModel q){
        if(f==null)return null;
        Object value=f.getValue();if(Objects.equals(f.getField(),q.getDimensions().get(0).getField())){
            if(!Set.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN","BETWEEN","IS_NULL","IS_NOT_NULL").contains(String.valueOf(f.getOp()).toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("时间比较不支持模糊时间筛选");
            String op=String.valueOf(f.getOp()).toUpperCase(Locale.ROOT);
            if(op.equals("BETWEEN") && value instanceof List<?> range && range.size()==2)value=List.of(shifted(range.get(0),q,false),shifted(range.get(1),q,true));
            else if(!Set.of("IS_NULL","IS_NOT_NULL").contains(op))value=shifted(value,q,op.equals("LTE"));
        }
        return new QueryModel.Filter(f.getField(),f.getOp(),value);
    }
    private static QueryModel.FilterNode shiftTree(QueryModel.FilterNode f,QueryModel q){
        if(f==null)return null;var out=new QueryModel.FilterNode();var leaf=shiftFilter(f,q);out.setField(leaf.getField());out.setOp(leaf.getOp());out.setValue(leaf.getValue());out.setLogic(f.getLogic());if(f.getChildren()!=null)out.setChildren(f.getChildren().stream().map(c->shiftTree(c,q)).toList());return out;
    }
    public static QueryModel baseline(QueryModel q,List<Map<String,Object>> rows){
        var d=q.getDimensions().get(0);var dates=rows.stream().map(row->row.get(key(d))).filter(Objects::nonNull).map(v->previous(LocalDate.parse(String.valueOf(v)),d.getTimeGrain(),q.getTimeComparison())).sorted().toList();
        if(dates.isEmpty())return null;
        var out=new QueryModel();out.setDatasetId(q.getDatasetId());out.setDimensions(q.getDimensions());out.setMetrics(q.getMetrics());out.setOrderBys(List.of(new QueryModel.OrderBy(key(d),"asc")));out.setLimit(1001);
        var filters=new ArrayList<QueryModel.Filter>();for(var f:q.getFilters())filters.add(shiftFilter(f,q));filters.add(new QueryModel.Filter(d.getField(),"GTE",dates.get(0).toString()));filters.add(new QueryModel.Filter(d.getField(),"LT",next(dates.get(dates.size()-1),d.getTimeGrain()).toString()));out.setFilters(filters);out.setFilterTree(shiftTree(q.getFilterTree(),q));return out;
    }
    private static String metric(QueryModel.Metric m){return m.getAlias()==null || m.getAlias().isBlank()?m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField():m.getAlias();}
    private static BigDecimal decimal(Object value){if(value==null)return null;try{return new BigDecimal(String.valueOf(value));}catch(NumberFormatException ex){return null;}}
    public static Result apply(QueryModel q,List<String> columns,List<Map<String,Object>> current,List<Map<String,Object>> prior,Map<String,Object> fill,boolean truncated){
        String label="year".equals(q.getTimeComparison())?"同比":"环比";var meta=new LinkedHashMap<String,Object>();meta.put("mode",q.getTimeComparison());meta.put("label",label);meta.put("grain",q.getDimensions().get(0).getTimeGrain());meta.put("fields",List.of());
        if(truncated || prior.size()>1000 || fill!=null && "skipped".equals(fill.get("status"))){meta.put("status","skipped");meta.put("reason","结果可能截断或周期补齐失败，请缩小时间范围后比较");return new Result(columns,current,meta,Map.of());}
        String time=key(q.getDimensions().get(0));var lookup=new HashMap<String,Map<String,Object>>();for(var row:prior)if(row.get(time)!=null && lookup.put(String.valueOf(row.get(time)),row)!=null)throw new IllegalArgumentException("比较基期存在重复周期");
        var fields=new ArrayList<Field>();var names=new HashSet<String>();columns.forEach(c->names.add(c.toLowerCase(Locale.ROOT)));var labels=new LinkedHashMap<String,String>();
        for(var m:q.getMetrics()){
            int i=fields.size();String base="__cmp_"+i;while(names.contains((base+"_base").toLowerCase(Locale.ROOT)) || names.contains((base+"_diff").toLowerCase(Locale.ROOT)) || names.contains((base+"_rate").toLowerCase(Locale.ROOT)))base+="_";
            var f=new Field(metric(m),base+"_base",base+"_diff",base+"_rate");fields.add(f);names.add(f.baseline());names.add(f.difference());names.add(f.rate());labels.put(f.baseline(),f.key()+" · "+label+"基期");labels.put(f.difference(),f.key()+" · 增长额");labels.put(f.rate(),f.key()+" · 增长率（比例）");
        }
        Set<?> synthetic=fill!=null && fill.get("periods") instanceof List<?> list?new HashSet<>(list):Set.of();var result=new ArrayList<Map<String,Object>>();int missing=0,zero=0;
        for(var source:current){var row=new LinkedHashMap<>(source);Object date=row.get(time);var match=date==null?null:lookup.get(previous(LocalDate.parse(String.valueOf(date)),q.getDimensions().get(0).getTimeGrain(),q.getTimeComparison()).toString());
            for(var f:fields){var a=synthetic.contains(date)?null:decimal(row.get(f.key()));var b=match==null?null:decimal(match.get(f.key()));var diff=a==null || b==null?null:a.subtract(b);if(b==null)missing++;if(b!=null && b.signum()==0)zero++;
                row.put(f.baseline(),b==null?null:b.toPlainString());row.put(f.difference(),diff==null?null:diff.toPlainString());row.put(f.rate(),diff==null || b.signum()==0?null:diff.divide(b.abs(),12,RoundingMode.HALF_UP).toPlainString());
            }result.add(row);
        }
        var all=new ArrayList<>(columns);fields.forEach(f->{all.add(f.baseline());all.add(f.difference());all.add(f.rate());});meta.put("status","complete");meta.put("fields",fields);meta.put("missingValues",missing);meta.put("zeroBases",zero);meta.put("note","基期缺失及补齐周期不计算增长；基期为零时增长率为空。增长率=(本期-基期)/ABS(基期)；周同比为前52周。时间筛选同步平移，其他筛选和权限不变。");return new Result(all,result,meta,labels);
    }
}
