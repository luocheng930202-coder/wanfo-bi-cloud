package com.wanfo.bi.domain.chart;
/** Persisted chart chrome; optional fields keep legacy definitions valid. */
public class ChartVisualStyle {
    public record MetricStyle(String key,com.wanfo.bi.service.DashboardPresentation.NumberFormat format,
            String seriesType,String axis,String color,java.util.List<com.wanfo.bi.service.DashboardPresentation.Threshold> rules) { }
    public record ReferenceLine(String key,String kind,Double value,String name,String color,String lineType) { }
    public record AxisRange(Double min,Double max) { }
    public record ComboAxes(AxisRange left,AxisRange right) { }
    private java.util.List<MetricStyle> metrics;
    private java.util.List<ReferenceLine> referenceLines;
    private ComboAxes comboAxes;
    public java.util.List<MetricStyle> getMetrics() { return metrics; }
    public void setMetrics(java.util.List<MetricStyle> value) { metrics=value; }
    public java.util.List<ReferenceLine> getReferenceLines() { return referenceLines; }
    public void setReferenceLines(java.util.List<ReferenceLine> value) { referenceLines=value; }
    public ComboAxes getComboAxes() { return comboAxes; }
    public void setComboAxes(ComboAxes value) { comboAxes=value; }
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
    private String funnelAlign;
    public String getFunnelAlign() { return funnelAlign; }
    public void setFunnelAlign(String value) { funnelAlign=value; }
    private Integer funnelGap;
    public Integer getFunnelGap() { return funnelGap; }
    public void setFunnelGap(Integer value) { funnelGap=value; }
    private Integer funnelMinSize;
    public Integer getFunnelMinSize() { return funnelMinSize; }
    public void setFunnelMinSize(Integer value) { funnelMinSize=value; }
    private Integer funnelMaxSize;
    public Integer getFunnelMaxSize() { return funnelMaxSize; }
    public void setFunnelMaxSize(Integer value) { funnelMaxSize=value; }
    private String funnelLabelPosition;
    public String getFunnelLabelPosition() { return funnelLabelPosition; }
    public void setFunnelLabelPosition(String value) { funnelLabelPosition=value; }
    private String funnelLabelContent;
    public String getFunnelLabelContent() { return funnelLabelContent; }
    public void setFunnelLabelContent(String value) { funnelLabelContent=value; }
    private String radarShape;
    public String getRadarShape() { return radarShape; }
    public void setRadarShape(String value) { radarShape=value; }
    private Integer radarSplitNumber;
    public Integer getRadarSplitNumber() { return radarSplitNumber; }
    public void setRadarSplitNumber(Integer value) { radarSplitNumber=value; }
    private Boolean radarShowSplitArea;
    public Boolean getRadarShowSplitArea() { return radarShowSplitArea; }
    public void setRadarShowSplitArea(Boolean value) { radarShowSplitArea=value; }
    private Boolean radarShowAxisName;
    public Boolean getRadarShowAxisName() { return radarShowAxisName; }
    public void setRadarShowAxisName(Boolean value) { radarShowAxisName=value; }
    private String scatterSymbol;
    public String getScatterSymbol() { return scatterSymbol; }
    public void setScatterSymbol(String value) { scatterSymbol=value; }
    private Integer scatterSize;
    public Integer getScatterSize() { return scatterSize; }
    public void setScatterSize(Integer value) { scatterSize=value; }
    private Double scatterOpacity;
    public Double getScatterOpacity() { return scatterOpacity; }
    public void setScatterOpacity(Double value) { scatterOpacity=value; }
    private Integer scatterBorderWidth;
    public Integer getScatterBorderWidth() { return scatterBorderWidth; }
    public void setScatterBorderWidth(Integer value) { scatterBorderWidth=value; }
    private String scatterBorderColor;
    public String getScatterBorderColor() { return scatterBorderColor; }
    public void setScatterBorderColor(String value) { scatterBorderColor=value; }
    private String scatterLabelContent;
    public String getScatterLabelContent() { return scatterLabelContent; }
    public void setScatterLabelContent(String value) { scatterLabelContent=value; }
    private String mapColorMode;
    public String getMapColorMode() { return mapColorMode; }
    public void setMapColorMode(String value) { mapColorMode=value; }
    private String mapLowColor;
    public String getMapLowColor() { return mapLowColor; }
    public void setMapLowColor(String value) { mapLowColor=value; }
    private String mapHighColor;
    public String getMapHighColor() { return mapHighColor; }
    public void setMapHighColor(String value) { mapHighColor=value; }
    private String mapNoDataColor;
    public String getMapNoDataColor() { return mapNoDataColor; }
    public void setMapNoDataColor(String value) { mapNoDataColor=value; }
    private String mapBorderColor;
    public String getMapBorderColor() { return mapBorderColor; }
    public void setMapBorderColor(String value) { mapBorderColor=value; }
    private String mapLabelContent;
    public String getMapLabelContent() { return mapLabelContent; }
    public void setMapLabelContent(String value) { mapLabelContent=value; }
    private java.util.List<Double> mapBreaks;
    public java.util.List<Double> getMapBreaks() { return mapBreaks; }
    public void setMapBreaks(java.util.List<Double> value) { mapBreaks=value; }
    private Boolean mapShowLabels;
    public Boolean getMapShowLabels() { return mapShowLabels; }
    public void setMapShowLabels(Boolean value) { mapShowLabels=value; }
    private Boolean mapShowTooltip;
    public Boolean getMapShowTooltip() { return mapShowTooltip; }
    public void setMapShowTooltip(Boolean value) { mapShowTooltip=value; }
    public record TableColumn(String key,Integer width,String align,String fixed,Boolean wrap,java.util.List<com.wanfo.bi.service.DashboardPresentation.Threshold> backgroundRules) {}
    private java.util.List<TableColumn> tableColumns;
    public java.util.List<TableColumn> getTableColumns() {return tableColumns;}
    public void setTableColumns(java.util.List<TableColumn> v) {tableColumns=v;}
    private Boolean tableStripe;
    public Boolean getTableStripe() {return tableStripe;}
    public void setTableStripe(Boolean v) {tableStripe=v;}
    private Boolean tableBorder;
    public Boolean getTableBorder() {return tableBorder;}
    public void setTableBorder(Boolean v) {tableBorder=v;}
    private Integer tableRowHeight;
    public Integer getTableRowHeight() {return tableRowHeight;}
    public void setTableRowHeight(Integer v) {tableRowHeight=v;}
    private String tableNullText;
    public String getTableNullText() {return tableNullText;}
    public void setTableNullText(String v) {tableNullText=v;}
    private String tableHeaderColor;
    public String getTableHeaderColor() {return tableHeaderColor;}
    public void setTableHeaderColor(String v) {tableHeaderColor=v;}
    private String tableHeaderBackground;
    public String getTableHeaderBackground() {return tableHeaderBackground;}
    public void setTableHeaderBackground(String v) {tableHeaderBackground=v;}
    private Boolean pivotRowTotals;
    public Boolean getPivotRowTotals() {return pivotRowTotals;}
    public void setPivotRowTotals(Boolean v) {pivotRowTotals=v;}
    private Boolean pivotColTotals;
    public Boolean getPivotColTotals() {return pivotColTotals;}
    public void setPivotColTotals(Boolean v) {pivotColTotals=v;}
    private Boolean pivotRowSubtotals;
    public Boolean getPivotRowSubtotals() {return pivotRowSubtotals;}
    public void setPivotRowSubtotals(Boolean v) {pivotRowSubtotals=v;}
    private Boolean pivotColSubtotals;
    public Boolean getPivotColSubtotals() {return pivotColSubtotals;}
    public void setPivotColSubtotals(Boolean v) {pivotColSubtotals=v;}
    private Boolean pivotTotalsFirst;
    public Boolean getPivotTotalsFirst() {return pivotTotalsFirst;}
    public void setPivotTotalsFirst(Boolean v) {pivotTotalsFirst=v;}
    private Boolean pivotValuesInCols;
    public Boolean getPivotValuesInCols() {return pivotValuesInCols;}
    public void setPivotValuesInCols(Boolean v) {pivotValuesInCols=v;}
    private String pivotHierarchy;
    public String getPivotHierarchy() {return pivotHierarchy;}
    public void setPivotHierarchy(String v) {pivotHierarchy=v;}
    private Integer pivotRowHeight;
    public Integer getPivotRowHeight() {return pivotRowHeight;}
    public void setPivotRowHeight(Integer v) {pivotRowHeight=v;}
    private Integer pivotColumnWidth;
    public Integer getPivotColumnWidth() {return pivotColumnWidth;}
    public void setPivotColumnWidth(Integer v) {pivotColumnWidth=v;}
    private Boolean pivotColorScale;
    public Boolean getPivotColorScale() {return pivotColorScale;}
    public void setPivotColorScale(Boolean v) {pivotColorScale=v;}
    private String pivotLowColor;
    public String getPivotLowColor() {return pivotLowColor;}
    public void setPivotLowColor(String v) {pivotLowColor=v;}
    private String pivotHighColor;
    public String getPivotHighColor() {return pivotHighColor;}
    public void setPivotHighColor(String v) {pivotHighColor=v;}
    private String pivotHeaderBackground;
    public String getPivotHeaderBackground() {return pivotHeaderBackground;}
    public void setPivotHeaderBackground(String v) {pivotHeaderBackground=v;}
    private String pivotHeaderColor;
    public String getPivotHeaderColor() {return pivotHeaderColor;}
    public void setPivotHeaderColor(String v) {pivotHeaderColor=v;}
    private java.util.List<String> mapPieceColors;
    public java.util.List<String> getMapPieceColors(){return mapPieceColors;}
    public void setMapPieceColors(java.util.List<String> v){mapPieceColors=v;}
    private String heatmapLowColor,heatmapHighColor;
    private Boolean heatmapShowScale,treemapShowBreadcrumb,boxplotHorizontal,waterfallShowTotal;
    private Integer treemapLeafDepth,sankeyNodeGap,histogramBins;
    private Double sankeyCurveness;
    public String getHeatmapLowColor(){return heatmapLowColor;} public void setHeatmapLowColor(String v){heatmapLowColor=v;}
    public String getHeatmapHighColor(){return heatmapHighColor;} public void setHeatmapHighColor(String v){heatmapHighColor=v;}
    public Boolean getHeatmapShowScale(){return heatmapShowScale;} public void setHeatmapShowScale(Boolean v){heatmapShowScale=v;}
    public Integer getTreemapLeafDepth(){return treemapLeafDepth;} public void setTreemapLeafDepth(Integer v){treemapLeafDepth=v;}
    public Boolean getTreemapShowBreadcrumb(){return treemapShowBreadcrumb;} public void setTreemapShowBreadcrumb(Boolean v){treemapShowBreadcrumb=v;}
    public Integer getSankeyNodeGap(){return sankeyNodeGap;} public void setSankeyNodeGap(Integer v){sankeyNodeGap=v;}
    public Double getSankeyCurveness(){return sankeyCurveness;} public void setSankeyCurveness(Double v){sankeyCurveness=v;}
    public Boolean getBoxplotHorizontal(){return boxplotHorizontal;} public void setBoxplotHorizontal(Boolean v){boxplotHorizontal=v;}
    public Integer getHistogramBins(){return histogramBins;} public void setHistogramBins(Integer v){histogramBins=v;}
    public Boolean getWaterfallShowTotal(){return waterfallShowTotal;} public void setWaterfallShowTotal(Boolean v){waterfallShowTotal=v;}
}
