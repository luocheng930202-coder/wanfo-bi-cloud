package com.wanfo.bi.query;

import com.wanfo.bi.domain.query.QueryModel;
import java.util.*;

/** Rank authorized, filtered categories, then aggregate original facts (AVG/DISTINCT remain exact). */
public final class TopNQuery {
 public static final String OTHER="__bi_other_group";
 private TopNQuery(){}
 public static ChartQueryCompiler.Compiled compile(QueryModel q,String base,Map<String,String> types,ChartQueryCompiler compiler){
  var top=q.getTopN();
  if(top.count()<1 || top.count()>100 || q.getDimensions().isEmpty() || q.getDimensions().size()>2 || q.getMetrics().isEmpty() || q.getMetrics().stream().anyMatch(m->m.getAgg()==null || "NONE".equalsIgnoreCase(m.getAgg())))throw new IllegalArgumentException("Top N 需要 1～100 个分类及聚合指标");
  if(q.getPivot()!=null || q.getTimeComparison()!=null || q.getDimensions().stream().anyMatch(d->d.getTimeGrain()!=null || (d.getTimeFill()!=null && !"none".equals(d.getTimeFill()))))throw new IllegalArgumentException("Top N 暂不能与透视汇总、时间粒度、时间补齐或同比环比组合");
  if(types.containsKey(OTHER) || q.getDimensions().stream().anyMatch(d->OTHER.equals(output(d))) || q.getMetrics().stream().anyMatch(m->OTHER.equals(output(m))))throw new IllegalArgumentException("Top N 输出名称冲突");
  var metric=q.getMetrics().stream().filter(m->output(m).equals(top.metric())).findFirst().orElseThrow(()->new IllegalArgumentException("Top N 排名指标已失效"));
  var dim=q.getDimensions().get(0);String field=FilterTreeCompiler.quote(dim.getField());
  var predicate=FilterTreeCompiler.compile(q.getFilters(),q.getFilterTree(),types);
  var ranking=new QueryModel();ranking.setDatasetId(q.getDatasetId());ranking.setDimensions(List.of(new QueryModel.Dimension(dim.getField(),"__category")));ranking.setMetrics(List.of(new QueryModel.Metric(metric.getField(),metric.getAgg(),"__rank")));ranking.setLimit(top.count());ranking.setOrderBys(List.of(new QueryModel.OrderBy("__rank","DESC"),new QueryModel.OrderBy("__category","ASC")));
  var rank=compiler.compile(ranking,"SELECT * FROM bi_top_input",types);
  String join="k.\"__category\" = v."+field+" OR (k.\"__category\" IS NULL AND v."+field+" IS NULL)";
  String hit="k.\"__bi_keep\" = 1";
  var projected=new ArrayList<String>();for(var name:types.keySet()){String column=FilterTreeCompiler.quote(name);projected.add(name.equals(dim.getField()) && top.includeOther()?"CASE WHEN "+hit+" THEN v."+column+" ELSE NULL END AS "+column:"v."+column);}
  projected.add((top.includeOther()?"CASE WHEN "+hit+" THEN 0 ELSE 1 END":"0")+" AS "+FilterTreeCompiler.quote(OTHER));
  String mapped="SELECT "+String.join(", ",projected)+" FROM bi_top_input AS v LEFT JOIN bi_top_keys AS k ON ("+join+")"+(top.includeOther()?"":" WHERE "+hit);
  var actual=new QueryModel();actual.setDatasetId(q.getDatasetId());var dimensions=new ArrayList<>(q.getDimensions());dimensions.add(new QueryModel.Dimension(OTHER,OTHER));actual.setDimensions(dimensions);actual.setMetrics(q.getMetrics());actual.setOrderBys(q.getOrderBys());actual.setLimit(10000);
  var extended=new LinkedHashMap<>(types);extended.put(OTHER,"INTEGER");
  // The internal sentinel fetch is one beyond the normal result bound; never return a partial denominator.
  var compiled=compiler.compile(actual,mapped,extended);compiled.sql=compiled.sql.substring(0,compiled.sql.lastIndexOf(" LIMIT "))+" LIMIT 10001";
  compiled.sql="WITH bi_top_input AS (SELECT * FROM ("+base+") AS bi_filtered"+(predicate.sql().isEmpty()?"":" WHERE "+predicate.sql())+"), bi_top_keys AS (SELECT ranked.*, 1 AS \"__bi_keep\" FROM ("+rank.sql+") AS ranked) "+compiled.sql;compiled.params=predicate.params();return compiled;
 }
 public record Result(List<String> columns,List<Map<String,Object>> rows,Map<String,Object> metadata){}
 public static Result decode(QueryModel q,List<String> columns,List<Map<String,Object>> input){
  if(input.size()>(q.getLimit()==null?ChartQueryCompiler.DEFAULT_LIMIT:q.getLimit()))throw new IllegalArgumentException("Top N 完整结果超过展示上限，请减少分类、系列或增加最多行数");
  var dim=q.getDimensions().get(0);String key=output(dim);var values=new HashSet<String>();for(var row:input)if(!isOther(row))values.add(String.valueOf(row.get(key)));String label="其他";for(int i=1;values.contains(label);i++)label="其他（汇总"+(i==1?"":i)+"）";
  var rows=new ArrayList<Map<String,Object>>();var otherRows=new ArrayList<Integer>();
  for(var row:input){var copy=new LinkedHashMap<>(row);copy.remove(OTHER);if(isOther(row)){copy.put(key,label);otherRows.add(rows.size());}rows.add(copy);}
  return new Result(columns.stream().filter(c->!c.equals(OTHER)).toList(),rows,Map.of("count",q.getTopN().count(),"includeOther",q.getTopN().includeOther(),"scope","filtered","otherRowIndexes",otherRows,"note",q.getTopN().includeOther()?"先筛选和权限过滤，再按指标排名；其他项由剩余原始记录重新聚合，占比分母包含其他项。":"先筛选和权限过滤，再按指标排名；占比分母仅包含保留分类。"));
 }
 private static boolean isOther(Map<String,Object> row){return row.get(OTHER) instanceof Number n && n.intValue()==1;}
 private static String output(QueryModel.Dimension d){return d.getAlias()==null || d.getAlias().isBlank()?d.getField():d.getAlias();}
 private static String output(QueryModel.Metric m){return m.getAlias()==null || m.getAlias().isBlank()?m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField():m.getAlias();}
}
