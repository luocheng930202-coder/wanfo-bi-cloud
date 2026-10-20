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
}
