package com.wanfo.bi.domain.chart;

import java.util.Set;

/** Validate optional presentation values without changing legacy defaults. */
public final class ChartStyleValidator {
    private ChartStyleValidator() { }
    public static void validate(ChartVisualStyle s,String type) {
        if (s == null) return;
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
    private static void range(Number n,double min,double max,String name) {
        if(n!=null && (!Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max))
            throw new IllegalArgumentException(name+"超出允许范围");
    }
    private static void choice(String value,Set<String> choices,String name) {
        if(value!=null && !choices.contains(value))throw new IllegalArgumentException(name+"无效");
    }
}
