package com.wanfo.bi.query;

import java.time.LocalDate;
import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;

/** Fill only the bounded, authorized result range; never query or infer an outside range. */
public final class TimeGapFill {
    private TimeGapFill() { }
    public record Result(List<Map<String,Object>> rows,Map<String,Object> metadata) { }
    public static void validate(QueryModel model) {
        for(var dimension:model.getDimensions()) {
            String mode=dimension.getTimeFill();if(mode==null)continue;
            if(!Set.of("none","null","zero").contains(mode))throw new IllegalArgumentException("缺失周期仅支持保持原结果、补空值或补零");
            if(mode.equals("none"))continue;
            if(dimension.getTimeGrain()==null || model.getDimensions().size()!=1 || model.getMetrics().isEmpty()
                    || model.getMetrics().stream().anyMatch(m->m.getAgg()==null || m.getAgg().equalsIgnoreCase("NONE")))
                throw new IllegalArgumentException("时间补齐需要一个已设置粒度的时间维度及聚合指标");
            String key=key(dimension);
            if(model.getOrderBys().size()>1 || model.getOrderBys().stream().anyMatch(o->o==null || (!Objects.equals(o.getField(),key) && !Objects.equals(o.getField(),dimension.getField()))))
                throw new IllegalArgumentException("时间补齐仅支持按时间排序，请清除指标排名或其他排序");
        }
    }
    private static String key(QueryModel.Dimension d) {return d.getAlias()==null || d.getAlias().isBlank()?d.getField():d.getAlias();}
    private static LocalDate next(LocalDate date,String grain) {
        return switch(grain) {case "day"->date.plusDays(1);case "week"->date.plusWeeks(1);case "month"->date.plusMonths(1);case "quarter"->date.plusMonths(3);case "year"->date.plusYears(1);default->throw new IllegalArgumentException("无效时间粒度");};
    }
    public static Result apply(QueryModel model,List<Map<String,Object>> rows) {
        var dimension=model.getDimensions().stream().filter(d->d.getTimeFill()!=null && Set.of("null","zero").contains(d.getTimeFill())).findFirst().orElse(null);
        if(dimension==null)return new Result(rows,null);
        int limit=model.getLimit()==null?ChartQueryCompiler.DEFAULT_LIMIT:model.getLimit();
        Map<String,Object> meta=new LinkedHashMap<>();meta.put("mode",dimension.getTimeFill());meta.put("sourceRows",rows.size());meta.put("addedRows",0);meta.put("periods",List.of());
        if(rows.size()>=limit)return skipped(rows,meta,"查询结果可能已截断，请缩小时间范围或增加行数上限后再补齐");
        String key=key(dimension);var dated=new TreeMap<LocalDate,Map<String,Object>>();var undated=new ArrayList<Map<String,Object>>();
        for(var row:rows) {
            Object value=row.get(key);if(value==null){undated.add(row);continue;}
            LocalDate date=LocalDate.parse(String.valueOf(value));
            if(dated.put(date,row)!=null)return skipped(rows,meta,"周期存在重复输出，无法安全补齐");
        }
        if(dated.size()<2){meta.put("status","complete");return new Result(rows,meta);}
        var output=new ArrayList<Map<String,Object>>();var periods=new ArrayList<String>();
        for(LocalDate date=dated.firstKey();!date.isAfter(dated.lastKey());date=next(date,dimension.getTimeGrain())) {
            if(output.size()+undated.size()>=limit)return skipped(rows,meta,"补齐后的周期数超过行数上限，请缩小时间范围或增加上限");
            var row=dated.get(date);
            if(row==null) {
                row=new LinkedHashMap<>();row.put(key,date.toString());periods.add(date.toString());
                for(var metric:model.getMetrics()) {
                    String agg=metric.getAgg().toLowerCase(Locale.ROOT);
                    String name=metric.getAlias()==null || metric.getAlias().isBlank()?agg+"_"+metric.getField():metric.getAlias();
                    row.put(name,dimension.getTimeFill().equals("zero")?0:null);
                }
            }
            output.add(row);
        }
        boolean descending=!model.getOrderBys().isEmpty() && "desc".equalsIgnoreCase(model.getOrderBys().get(0).getOrder());
        if(descending){Collections.reverse(output);output.addAll(undated);}else output.addAll(0,undated);
        meta.put("status","complete");meta.put("addedRows",periods.size());meta.put("periods",periods);
        return new Result(output,meta);
    }
    private static Result skipped(List<Map<String,Object>> rows,Map<String,Object> meta,String reason) {
        meta.put("status","skipped");meta.put("reason",reason);return new Result(rows,meta);
    }
}
