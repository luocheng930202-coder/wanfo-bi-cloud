package com.wanfo.bi.domain.chart;
/** Persisted chart chrome; optional fields keep legacy definitions valid. */
public class ChartVisualStyle {
    private Boolean showLegend;
    private String legendPosition;
    private Boolean showTitle;
    private String title;
    private String palette;
    private Integer axisLabelRotate;
    public Boolean getShowLegend() { return showLegend; }
    public void setShowLegend(Boolean showLegend) { this.showLegend = showLegend; }
    public String getLegendPosition() { return legendPosition; }
    public void setLegendPosition(String legendPosition) { this.legendPosition = legendPosition; }
    public Boolean getShowTitle() { return showTitle; }
    public void setShowTitle(Boolean showTitle) { this.showTitle = showTitle; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPalette() { return palette; }
    public void setPalette(String palette) { this.palette = palette; }
    public Integer getAxisLabelRotate() { return axisLabelRotate; }
    public void setAxisLabelRotate(Integer axisLabelRotate) { this.axisLabelRotate = axisLabelRotate; }
    private Boolean showLabels;
    public Boolean getShowLabels() { return showLabels; }
    public void setShowLabels(Boolean showLabels) { this.showLabels = showLabels; }
    private Integer barMaxWidth;
    public Integer getBarMaxWidth() { return barMaxWidth; }
    public void setBarMaxWidth(Integer barMaxWidth) { this.barMaxWidth = barMaxWidth; }
    private Integer barRadius;
    public Integer getBarRadius() { return barRadius; }
    public void setBarRadius(Integer barRadius) { this.barRadius = barRadius; }
    private Integer lineWidth;
    public Integer getLineWidth() { return lineWidth; }
    public void setLineWidth(Integer lineWidth) { this.lineWidth = lineWidth; }
    private Integer symbolSize;
    public Integer getSymbolSize() { return symbolSize; }
    public void setSymbolSize(Integer symbolSize) { this.symbolSize = symbolSize; }
    private Boolean showPoints;
    public Boolean getShowPoints() { return showPoints; }
    public void setShowPoints(Boolean showPoints) { this.showPoints = showPoints; }
    private Double areaOpacity;
    public Double getAreaOpacity() { return areaOpacity; }
    public void setAreaOpacity(Double areaOpacity) { this.areaOpacity = areaOpacity; }
    private Integer pieInnerRadius;
    public Integer getPieInnerRadius() { return pieInnerRadius; }
    public void setPieInnerRadius(Integer pieInnerRadius) { this.pieInnerRadius = pieInnerRadius; }
    private Integer pieOuterRadius;
    public Integer getPieOuterRadius() { return pieOuterRadius; }
    public void setPieOuterRadius(Integer pieOuterRadius) { this.pieOuterRadius = pieOuterRadius; }
    private String pieLabelPosition;
    public String getPieLabelPosition() { return pieLabelPosition; }
    public void setPieLabelPosition(String pieLabelPosition) { this.pieLabelPosition = pieLabelPosition; }
    private Integer startAngle;
    public Integer getStartAngle() { return startAngle; }
    public void setStartAngle(Integer startAngle) { this.startAngle = startAngle; }
    private Boolean ringShowTotal;
    public Boolean getRingShowTotal() { return ringShowTotal; }
    public void setRingShowTotal(Boolean ringShowTotal) { this.ringShowTotal = ringShowTotal; }
    private Double gaugeMin;
    public Double getGaugeMin() { return gaugeMin; }
    public void setGaugeMin(Double gaugeMin) { this.gaugeMin = gaugeMin; }
    private Double gaugeMax;
    public Double getGaugeMax() { return gaugeMax; }
    public void setGaugeMax(Double gaugeMax) { this.gaugeMax = gaugeMax; }
    private String gaugeStyle;
    public String getGaugeStyle() { return gaugeStyle; }
    public void setGaugeStyle(String gaugeStyle) { this.gaugeStyle = gaugeStyle; }
}
