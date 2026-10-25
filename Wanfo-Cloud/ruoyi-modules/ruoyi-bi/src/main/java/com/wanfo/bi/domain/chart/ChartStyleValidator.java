package com.wanfo.bi.domain.chart;

import java.util.Set;

/** Validate optional presentation values without changing legacy defaults. */
public final class ChartStyleValidator {
    private ChartStyleValidator() { }
    public static void validate(ChartVisualStyle s,String type) {
        if (s == null) return;
        choice(s.getPivotHierarchy(),Set.of("grid","tree"),"透视表头层次");range(s.getPivotRowHeight(),28,80,"透视行高");range(s.getPivotColumnWidth(),60,400,"透视列宽");
        color(s.getPivotLowColor());color(s.getPivotHighColor());color(s.getPivotHeaderBackground());color(s.getPivotHeaderColor());
        range(s.getTableRowHeight(),28,80,"明细表行高");color(s.getTableHeaderColor());color(s.getTableHeaderBackground());
        if(s.getTableNullText()!=null && (s.getTableNullText().length()>20 || s.getTableNullText().codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("空值文本最多20字且不能含控制字符");
        if(s.getTableColumns()!=null){
            if(s.getTableColumns().size()>100)throw new IllegalArgumentException("最多100个列样式");
            var keys=new java.util.HashSet<String>();var columns=new java.util.ArrayList<ChartVisualStyle.TableColumn>();
            for(var c:s.getTableColumns()){
                if(c==null || !validKey(c.key()) || !keys.add(c.key()))throw new IllegalArgumentException("列样式键无效或重复");
                range(c.width(),60,800,"明细表列宽");choice(c.align(),Set.of("left","center","right"),"列对齐");choice(c.fixed(),Set.of("none","left","right"),"固定列");
                var rules=com.wanfo.bi.service.DashboardPresentation.normalizeMetrics(java.util.List.of(new com.wanfo.bi.service.DashboardPresentation.MetricStyle(c.key(),null,c.backgroundRules()))).get(0).rules();
                columns.add(new ChartVisualStyle.TableColumn(c.key(),c.width(),c.align(),c.fixed(),c.wrap(),rules));
            }s.setTableColumns(java.util.List.copyOf(columns));
        }
        if(s.getMapPieceColors()!=null && !s.getMapPieceColors().isEmpty()){
            if(s.getMapBreaks()==null || s.getMapBreaks().isEmpty() || s.getMapPieceColors().size()!=s.getMapBreaks().size()+1 || s.getMapPieceColors().size()>6)throw new IllegalArgumentException("逐段颜色须与分段数量一致，最多6段");
            for(String value:s.getMapPieceColors())color(value);
        }
        choice(s.getMapColorMode(),Set.of("continuous","piecewise"),"地图色阶模式");
        choice(s.getMapLabelContent(),Set.of("name","value","nameValue"),"地图标签内容");
        color(s.getMapLowColor());color(s.getMapHighColor());color(s.getMapNoDataColor());color(s.getMapBorderColor());
        if(s.getMapBreaks()!=null){
            if(s.getMapBreaks().size()>5)throw new IllegalArgumentException("地图最多 5 个分段边界");
            double previous=Double.NEGATIVE_INFINITY;
            for(Double value:s.getMapBreaks()){
                if(value==null || !Double.isFinite(value) || Math.abs(value)>9007199254740991d || value<=previous)throw new IllegalArgumentException("地图分段边界须为严格递增的有效原始数值");
                previous=value;
            }
            if("piecewise".equals(s.getMapColorMode()) && s.getMapBreaks().isEmpty())throw new IllegalArgumentException("地图分段须至少一个边界");
        }else if("piecewise".equals(s.getMapColorMode()))throw new IllegalArgumentException("地图分段须至少一个边界");
        choice(s.getFunnelAlign(),Set.of("left","center","right"),"漏斗对齐");
        choice(s.getFunnelLabelPosition(),Set.of("inside","left","right"),"漏斗标签位置");
        choice(s.getFunnelLabelContent(),Set.of("name","value","nameValue"),"漏斗标签内容");
        range(s.getFunnelGap(),0,30,"阶段间距");range(s.getFunnelMinSize(),0,80,"漏斗最小宽度");range(s.getFunnelMaxSize(),20,100,"漏斗最大宽度");
        if("funnel".equals(type) && (s.getFunnelMinSize()==null?0:s.getFunnelMinSize()) >= (s.getFunnelMaxSize()==null?100:s.getFunnelMaxSize()))throw new IllegalArgumentException("漏斗最小宽度必须小于最大宽度");
        choice(s.getRadarShape(),Set.of("polygon","circle"),"雷达网格形状");range(s.getRadarSplitNumber(),2,10,"雷达网格层数");
        choice(s.getScatterSymbol(),Set.of("circle","rect","roundRect","triangle","diamond"),"散点形状");
        choice(s.getScatterLabelContent(),Set.of("category","coordinates"),"散点标签内容");
        color(s.getHeatmapLowColor());color(s.getHeatmapHighColor());range(s.getTreemapLeafDepth(),1,2,"矩形树图初始层级");
        range(s.getSankeyNodeGap(),0,40,"桑基图节点间距");range(s.getSankeyCurveness(),0,1,"桑基图连线弯曲度");
        range(s.getHistogramBins(),0,50,"直方图分箱数量");
        range(s.getScatterSize(),2,60,"散点大小");range(s.getScatterOpacity(),0.05,1,"散点透明度");range(s.getScatterBorderWidth(),0,5,"散点描边宽度");color(s.getScatterBorderColor());
        if(s.getMetrics()!=null) {
            if(s.getMetrics().size()>20)throw new IllegalArgumentException("最多 20 个指标样式");
            var keys=new java.util.HashSet<String>();
            var normalized=new java.util.ArrayList<ChartVisualStyle.MetricStyle>();
            for(var m:s.getMetrics()) {
                if(m==null || !validKey(m.key()) || !keys.add(m.key()))throw new IllegalArgumentException("指标样式键无效或重复");
                choice(m.seriesType(),Set.of("bar","line"),"系列图形");
                choice(m.axis(),Set.of("left","right"),"系列坐标轴");color(m.color());
                var metric=com.wanfo.bi.service.DashboardPresentation.normalizeMetrics(java.util.List.of(
                    new com.wanfo.bi.service.DashboardPresentation.MetricStyle(m.key(),m.format(),m.rules()))).get(0);
                normalized.add(new ChartVisualStyle.MetricStyle(m.key(),metric.format(),m.seriesType(),m.axis(),m.color(),metric.rules()));
            }
            s.setMetrics(java.util.List.copyOf(normalized));
        }
        if(s.getReferenceLines()!=null) {
            if(s.getReferenceLines().size()>8)throw new IllegalArgumentException("最多 8 条参考线");
            for(var r:s.getReferenceLines()) {
                if(r==null || !validKey(r.key()) || r.kind()==null || r.name()==null || r.name().length()>40 || r.name().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("参考线配置无效");
                choice(r.kind(),Set.of("fixed","average","min","max"),"参考线类型");
                choice(r.lineType(),Set.of("solid","dashed","dotted"),"参考线线型");color(r.color());
                if("fixed".equals(r.kind()) && r.value()==null)throw new IllegalArgumentException("固定参考线须填写原始数值");
                range(r.value(),-1e12,1e12,"参考线值");
            }
        }
        if(s.getComboAxes()!=null){axis(s.getComboAxes().left());axis(s.getComboAxes().right());}
        range(s.getAxisLabelRotate(),0,90,"轴标签旋转");
        range(s.getBarMaxWidth(),4,120,"柱条最大宽度");
        range(s.getBarRadius(),0,30,"柱条圆角");
        range(s.getLineWidth(),1,10,"线宽");
        range(s.getSymbolSize(),2,20,"数据点大小");
        range(s.getAreaOpacity(),0,1,"面积透明度");
        range(s.getPieInnerRadius(),0,80,"内半径");
        range(s.getPieOuterRadius(),20,90,"外半径");
        range(s.getStartAngle(),0,360,"起始角度");
        if("ring".equals(type) && (s.getPieInnerRadius()==null?52:s.getPieInnerRadius()) >= (s.getPieOuterRadius()==null?72:s.getPieOuterRadius()))
            throw new IllegalArgumentException("内半径必须小于外半径");
        choice(s.getPieLabelPosition(),Set.of("inside","outside"),"标签位置");
        choice(s.getGaugeStyle(),Set.of("pointer","progress"),"仪表样式");
        range(s.getGaugeMin(),-1e12,1e12,"仪表最小值");
        range(s.getGaugeMax(),-1e12,1e12,"仪表最大值");
        double min=s.getGaugeMin()==null?0:s.getGaugeMin();
        if("gauge".equals(type) && s.getGaugeMax()!=null && s.getGaugeMax()<=min)
            throw new IllegalArgumentException("仪表最大值必须大于最小值");
    }
    private static boolean validKey(String key){return key!=null && !key.isBlank() && key.length()<=128 && key.codePoints().noneMatch(Character::isISOControl);}
    private static void color(String value){if(value!=null && !value.matches("#[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("系列或参考线颜色须为六位十六进制色值");}
    private static void axis(ChartVisualStyle.AxisRange axis){if(axis==null)return;range(axis.min(),-1e12,1e12,"坐标轴最小值");range(axis.max(),-1e12,1e12,"坐标轴最大值");if(axis.min()!=null && axis.max()!=null && axis.min()>=axis.max())throw new IllegalArgumentException("坐标轴最大值必须大于最小值");}
    public static void validateBindings(ChartVisualStyle s,Set<String> outputs){
        if(s==null)return;
        if(s.getMetrics()!=null)for(var m:s.getMetrics())if(!outputs.contains(m.key()))throw new IllegalArgumentException("指标样式引用已失效："+m.key());
        if(s.getReferenceLines()!=null)for(var r:s.getReferenceLines())if(!outputs.contains(r.key()))throw new IllegalArgumentException("参考线指标引用已失效："+r.key());
    }
    public static void validateTableBindings(ChartVisualStyle s,Set<String> outputs,Set<String> metrics){
        if(s==null || s.getTableColumns()==null)return;
        for(var c:s.getTableColumns()){
            if(!outputs.contains(c.key()))throw new IllegalArgumentException("列样式引用已失效："+c.key());
            if(c.backgroundRules()!=null && !c.backgroundRules().isEmpty() && !metrics.contains(c.key()))throw new IllegalArgumentException("条件背景色仅支持指标列");
        }
    }
    private static void range(Number n,double min,double max,String name) {
        if(n!=null && (!Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max))
            throw new IllegalArgumentException(name+"超出允许范围");
    }
    private static void choice(String value,Set<String> choices,String name) {
        if(value!=null && !choices.contains(value))throw new IllegalArgumentException(name+"无效");
    }
}
