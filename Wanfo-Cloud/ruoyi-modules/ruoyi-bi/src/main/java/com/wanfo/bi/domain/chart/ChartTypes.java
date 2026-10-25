package com.wanfo.bi.domain.chart;
import java.util.Set;
import com.wanfo.bi.domain.query.QueryModel;
/** Saved chart visual kinds supported by the query + ECharts renderer. */
public final class ChartTypes {
 private ChartTypes() { }
 public static final Set<String> ALL = Set.of(
         "bar","line","area","hbar","stack","percent",
         "pie","ring","funnel","radar","scatter","gauge","combo","table","pivot","map",
         "heatmap","treemap","sunburst","sankey","boxplot","histogram","waterfall","geo-map");
 private static final Set<String> PIE_LIKE = Set.of("pie","ring","funnel");
 private static final Set<String> CARTESIAN = Set.of("bar","line","area","hbar","stack","percent","combo","radar");
 public static boolean allowed(String type) { return type != null && ALL.contains(type); }
 public static void validateShape(String type, QueryModel q) {
  if (!allowed(type) || q == null) throw new IllegalArgumentException("不支持的图表类型");
  if ("table".equals(type) || "pivot".equals(type)) return;
  if ("gauge".equals(type)) {
   if (!q.getDimensions().isEmpty()) throw new IllegalArgumentException("仪表盘不需要维度，请只保留一个度量");
   if (q.getMetrics() == null || q.getMetrics().size() != 1) throw new IllegalArgumentException("仪表盘仅支持一个度量");
   return;
  }
  if ("scatter".equals(type)) {
   if (q.getMetrics() == null || (q.getMetrics().size() < 2 || q.getMetrics().size() > 3)) throw new IllegalArgumentException("散点图需要 X、Y 两个度量，可选第三个气泡大小指标");
   if (q.getDimensions() != null && q.getDimensions().size() > 1) throw new IllegalArgumentException("散点图最多一个维度用于系列分组");
   return;
  }
  if("histogram".equals(type)){if(!q.getDimensions().isEmpty()||q.getMetrics().size()!=1||!"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("直方图需要一个未聚合数值字段且不需要维度");return;}
  if("geo-map".equals(type)){if(q.getDimensions().size()>1||q.getMetrics().size()<2||q.getMetrics().size()>3||q.getMetrics().stream().anyMatch(m->!"NONE".equalsIgnoreCase(m.getAgg())))throw new IllegalArgumentException("经纬度地图须提供未聚合的经度、纬度，可选大小指标和名称维度");return;}
  if("heatmap".equals(type)){if(q.getDimensions().size()!=2||q.getMetrics().size()!=1||"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("热力图需要两个维度和一个聚合指标");return;}
  if("sunburst".equals(type)){if(q.getDimensions().size()<2||q.getDimensions().size()>6||q.getDimensions().stream().map(d->d.getField()).distinct().count()!=q.getDimensions().size()||q.getMetrics().size()!=1||"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("旭日图需要 2～6 个不重复层级维度和一个聚合指标");return;}
  if("sankey".equals(type)){if(q.getDimensions().size()!=2||q.getMetrics().size()!=1||"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("桑基图需要两个维度和一个聚合指标");return;}
  if("treemap".equals(type)){if(q.getDimensions().isEmpty()||q.getDimensions().size()>6||q.getDimensions().stream().map(d->d.getField()).distinct().count()!=q.getDimensions().size()||q.getMetrics().size()!=1||"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("矩形树图需要 1～6 个不重复层级维度和一个聚合指标");return;}
  if("waterfall".equals(type)){if(q.getDimensions().size()!=1||q.getMetrics().size()!=1||"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("瀑布图需要一个维度和一个聚合指标");return;}
  if("boxplot".equals(type)){if(q.getDimensions().size()!=1||q.getMetrics().size()!=1||!"NONE".equalsIgnoreCase(q.getMetrics().get(0).getAgg()))throw new IllegalArgumentException("箱线图需要分类维度和未聚合数值字段");return;}
  if (PIE_LIKE.contains(type) || "map".equals(type)) {
   if (q.getDimensions() == null || q.getDimensions().size() != 1 || q.getMetrics() == null || q.getMetrics().size() != 1)
    throw new IllegalArgumentException("map".equals(type) ? "行政区地图须选择一个地区维度和一个度量" : "饼图/环形图/漏斗图须选择一个维度和一个度量");
   return;
  }
  if (!CARTESIAN.contains(type)) throw new IllegalArgumentException("不支持的图表类型");
  if (q.getDimensions() == null || (q.getDimensions().size() != 1 && !( !"radar".equals(type) && q.getDimensions().size()==2 && !q.getDimensions().get(0).getField().equals(q.getDimensions().get(1).getField()))) || q.getMetrics() == null || q.getMetrics().isEmpty() || q.getMetrics().size() > 5)
   throw new IllegalArgumentException("图形须选择一个分类维度、可选一个系列维度和 1～5 个度量");
  if(q.getDimensions().size()==2 && q.getMetrics().stream().anyMatch(m->m.getAgg()==null || "NONE".equalsIgnoreCase(m.getAgg())))throw new IllegalArgumentException("系列维度需要聚合指标");
  if ("combo".equals(type) && q.getMetrics().size() < 2) throw new IllegalArgumentException("柱线组合图至少需要两个度量");
 }
}
