package com.wanfo.bi.domain.chart;
import java.util.Set;
import com.wanfo.bi.domain.query.QueryModel;
/** Saved chart visual kinds supported by the query + ECharts renderer. */
public final class ChartTypes {
 private ChartTypes() { }
 public static final Set<String> ALL = Set.of(
         "bar","line","area","hbar","stack","percent",
         "pie","ring","funnel","radar","scatter","gauge","combo","table","pivot","map");
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
   if (q.getMetrics() == null || q.getMetrics().size() != 2) throw new IllegalArgumentException("散点图需要两个度量（横轴与纵轴）");
   if (q.getDimensions() != null && q.getDimensions().size() > 1) throw new IllegalArgumentException("散点图最多一个维度用于系列分组");
   return;
  }
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
