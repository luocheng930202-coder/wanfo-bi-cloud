package com.wanfo.bi.domain.chart;
import com.wanfo.bi.domain.query.QueryModel;
import java.util.*;
public final class ChartAnalysisValidator {
 private ChartAnalysisValidator(){}
 public static void validate(ChartAdvancedOptions advanced,QueryModel query,String type){
  if(advanced==null || advanced.getAnalysis()==null)return;var a=advanced.getAnalysis();
  if(a.funnelRate()!=null && !Set.of("adjacent","first","").contains(a.funnelRate()))throw new IllegalArgumentException("漏斗转化率口径无效");
  if(a.funnelStages()!=null){if(a.funnelStages().size()>100 || new HashSet<>(a.funnelStages()).size()!=a.funnelStages().size() || a.funnelStages().stream().anyMatch(s->s==null || s.isBlank() || s.length()>200 || s.codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("固定阶段最多100个，名称不可重复、为空或包含控制字符");}
  number(a.scatterXSplit());number(a.scatterYSplit());
  if(a.trendMethod()!=null && !Set.of("linear","moving-average").contains(a.trendMethod()))throw new IllegalArgumentException("趋势拟合方式无效");
  if(a.forecastPeriods()!=null && (a.forecastPeriods()<0 || a.forecastPeriods()>12))throw new IllegalArgumentException("预测周期须在 0～12 之间");
  if(a.anomalySensitivity()!=null && (!Double.isFinite(a.anomalySensitivity()) || a.anomalySensitivity()<1 || a.anomalySensitivity()>4))throw new IllegalArgumentException("异常阈值须在 1～4 之间");
  if(a.anomalyMethod()!=null && !Set.of("stddev","mad").contains(a.anomalyMethod()))throw new IllegalArgumentException("异常检测算法无效");
  if(a.radarAxes()!=null){var seen=new HashSet<String>();if(a.radarAxes().size()>5)throw new IllegalArgumentException("雷达指标配置过多");for(var axis:a.radarAxes()){if(axis==null || axis.key()==null || !seen.add(axis.key()))throw new IllegalArgumentException("雷达指标配置重复或无效");number(axis.min());number(axis.max());number(axis.target());if(axis.min()==null || axis.max()==null || axis.min()>=axis.max())throw new IllegalArgumentException("雷达指标上限必须大于下限");if(axis.target()!=null && (axis.target()<axis.min() || axis.target()>axis.max()))throw new IllegalArgumentException("雷达目标值须在指标范围内");}}
  if("radar".equals(type) && Boolean.TRUE.equals(a.radarNormalize()))for(var metric:query.getMetrics()){var key=metric.getAlias()==null || metric.getAlias().isBlank()?metric.getAgg().toLowerCase(Locale.ROOT)+"_"+metric.getField():metric.getAlias();if(a.radarAxes()==null || a.radarAxes().stream().noneMatch(axis->axis.key().equals(key)))throw new IllegalArgumentException("请为每个雷达指标配置标准化范围");}
 }
 private static void number(Double value){if(value!=null && (!Double.isFinite(value) || Math.abs(value)>1e12))throw new IllegalArgumentException("高级分析数值超出范围");}
}
