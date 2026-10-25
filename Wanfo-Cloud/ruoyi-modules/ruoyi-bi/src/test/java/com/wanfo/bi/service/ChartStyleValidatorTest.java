package com.wanfo.bi.service;

import com.wanfo.bi.domain.chart.ChartStyleValidator;
import com.wanfo.bi.domain.chart.ChartVisualStyle;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ChartStyleValidatorTest {
    @Test void specializedStylesSurviveSerialization() {
        var s=new ChartVisualStyle();s.setFunnelAlign("left");s.setFunnelGap(8);s.setFunnelMinSize(12);s.setFunnelMaxSize(92);s.setFunnelLabelPosition("inside");s.setFunnelLabelContent("nameValue");
        s.setRadarShape("circle");s.setRadarSplitNumber(4);s.setRadarShowSplitArea(false);s.setRadarShowAxisName(true);s.setAreaOpacity(0.25);
        s.setScatterSymbol("diamond");s.setScatterSize(18);s.setScatterOpacity(0.45);s.setScatterBorderWidth(2);s.setScatterBorderColor("#3366FF");s.setScatterLabelContent("coordinates");
        var json=new ObjectMapper();var restored=json.readValue(json.writeValueAsString(s),ChartVisualStyle.class);
        for(var type:java.util.List.of("funnel","radar","scatter"))assertDoesNotThrow(()->ChartStyleValidator.validate(restored,type));
        assertEquals("left",restored.getFunnelAlign());assertEquals(8,restored.getFunnelGap());assertEquals("nameValue",restored.getFunnelLabelContent());
        assertEquals("circle",restored.getRadarShape());assertEquals(4,restored.getRadarSplitNumber());assertFalse(restored.getRadarShowSplitArea());
        assertEquals("diamond",restored.getScatterSymbol());assertEquals(18,restored.getScatterSize());assertEquals(0.45,restored.getScatterOpacity());
    }
    @Test void specializedStyleLimitsAndUnsafeSymbolsAreRejected() {
        var s=new ChartVisualStyle();s.setFunnelGap(31);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"funnel"));
        s.setFunnelGap(null);s.setFunnelMinSize(70);s.setFunnelMaxSize(50);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"funnel"));
        s.setFunnelMinSize(null);s.setFunnelMaxSize(null);s.setRadarSplitNumber(11);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"radar"));
        s.setRadarSplitNumber(null);s.setScatterOpacity(Double.NaN);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"scatter"));
        s.setScatterOpacity(null);s.setScatterSymbol("image://https://example.com/private");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"scatter"));
        s.setScatterSymbol(null);s.setScatterBorderColor("url(x)");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"scatter"));
    }
    @Test void metricFormatsSeriesAndReferencesRoundtripWithoutChangingQueryValues() {
        var s=new ChartVisualStyle();
        var format=new DashboardPresentation.NumberFormat("number","yi",2,true,true,"","元","—");
        s.setMetrics(java.util.List.of(new ChartVisualStyle.MetricStyle("sum_amount",format,"line","right","#3366FF",java.util.List.of(new DashboardPresentation.Threshold("GTE","10000","#F56C6C")))));
        s.setReferenceLines(java.util.List.of(new ChartVisualStyle.ReferenceLine("sum_amount","fixed",10000d,"目标","#F59E0B","dashed")));
        s.setComboAxes(new ChartVisualStyle.ComboAxes(null,new ChartVisualStyle.AxisRange(0d,20000d)));
        var json=new ObjectMapper();var restored=json.readValue(json.writeValueAsString(s),ChartVisualStyle.class);
        assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"combo"));
        assertDoesNotThrow(()->ChartStyleValidator.validateBindings(restored,java.util.Set.of("sum_amount")));
        assertEquals("yi",restored.getMetrics().get(0).format().scale());
        assertEquals("line",restored.getMetrics().get(0).seriesType());assertEquals("right",restored.getMetrics().get(0).axis());
        assertEquals(10000d,restored.getReferenceLines().get(0).value());
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validateBindings(restored,java.util.Set.of("new_metric")));
    }
    @Test void metricStylesRejectDuplicatesInvalidFormatsAxesAndReferences() {
        var s=new ChartVisualStyle();
        var invalid=new DashboardPresentation.NumberFormat("percent","wan",2,true,true,"","",null);
        s.setMetrics(java.util.List.of(new ChartVisualStyle.MetricStyle("value",invalid,null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"bar"));
        var m=new ChartVisualStyle.MetricStyle("value",null,"line","right","#3366FF",null);
        s.setMetrics(java.util.List.of(m,m));assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"combo"));
        s.setMetrics(null);s.setComboAxes(new ChartVisualStyle.ComboAxes(new ChartVisualStyle.AxisRange(10d,0d),null));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"combo"));
        s.setComboAxes(null);s.setReferenceLines(java.util.List.of(new ChartVisualStyle.ReferenceLine("value","fixed",Double.NaN,"目标","#3366FF","dashed")));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"bar"));
        s.setReferenceLines(java.util.List.of(new ChartVisualStyle.ReferenceLine("value","fixed",null,"目标","#3366FF","dashed")));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"bar"));
    }
    @Test void oldDefinitionsKeepTheirDefaults() {
        ChartStyleValidator.validate(null,"bar");
        ChartStyleValidator.validate(new ChartVisualStyle(),"ring");
    }
    @Test void rejectsInvalidRangesIncludingImplicitDefaults() {
        var s=new ChartVisualStyle();s.setAreaOpacity(Double.NaN);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"area"));
        s.setAreaOpacity(0.2);s.setBarMaxWidth(999);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"bar"));
        s.setBarMaxWidth(36);s.setPieInnerRadius(80);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"ring"));
        s.setPieInnerRadius(null);s.setPieOuterRadius(40);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"ring"));
        // A pie has no inner ring; old ring settings must not invalidate its outer radius.
        assertDoesNotThrow(()->ChartStyleValidator.validate(s,"pie"));
        s.setGaugeMin(20d);s.setGaugeMax(10d);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"gauge"));
    }
    @Test void persistsShapeOptionsWithoutLosingUserChrome() {
        var s=new ChartVisualStyle();s.setTitle("销售额");s.setBarRadius(6);s.setLineWidth(4);
        s.setShowPoints(false);s.setRingShowTotal(true);s.setPieInnerRadius(42);
        s.setGaugeStyle("progress");s.setGaugeMin(-100d);s.setGaugeMax(200d);
        var json=new ObjectMapper();
        var restored=json.readValue(json.writeValueAsString(s),ChartVisualStyle.class);
        assertEquals("销售额",restored.getTitle());assertEquals(6,restored.getBarRadius());
        assertEquals(4,restored.getLineWidth());assertFalse(restored.getShowPoints());
        assertTrue(restored.getRingShowTotal());assertEquals(42,restored.getPieInnerRadius());
        assertEquals("progress",restored.getGaugeStyle());assertEquals(200d,restored.getGaugeMax());
        assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"ring"));
    }
    @Test void mapStyleRoundtripAndInvalidBreaks() {
        var s=new ChartVisualStyle();s.setMapColorMode("piecewise");s.setMapBreaks(java.util.List.of(-10d,0d,100d));s.setMapLowColor("#FFFFFF");s.setMapHighColor("#006EDD");s.setMapNoDataColor("#EEEEEE");s.setMapBorderColor("#A0A6AD");s.setMapShowLabels(true);s.setMapLabelContent("nameValue");s.setMapShowTooltip(false);
        var mapper=new ObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(s),ChartVisualStyle.class);
        assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"map"));assertEquals(s.getMapBreaks(),restored.getMapBreaks());assertFalse(restored.getMapShowTooltip());
        for(var values:java.util.List.of(java.util.List.<Double>of(),java.util.List.of(1d,1d),java.util.List.of(2d,1d),java.util.List.of(Double.NaN),java.util.List.of(Double.POSITIVE_INFINITY),java.util.List.of(1d,2d,3d,4d,5d,6d))){s.setMapBreaks(values);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"map"));}
        s.setMapBreaks(java.util.List.of(100d));s.setMapNoDataColor("url(x)");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"map"));
    }
    @Test void tableStylesRoundtripAndValidateBindings() {
        var s=new ChartVisualStyle();s.setTableStripe(true);s.setTableBorder(false);s.setTableRowHeight(44);s.setTableNullText("暂无");s.setTableHeaderBackground("#EEF2FF");s.setTableHeaderColor("#312E81");
        s.setTableColumns(java.util.List.of(new ChartVisualStyle.TableColumn("region",200,"left","left",true,null),new ChartVisualStyle.TableColumn("sales",160,"right","right",false,java.util.List.of(new DashboardPresentation.Threshold("GTE","10000.01","#DCFCE7")))));
        var mapper=new ObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(s),ChartVisualStyle.class);assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"table"));
        assertEquals(44,restored.getTableRowHeight());assertFalse(restored.getTableBorder());assertEquals("left",restored.getTableColumns().get(0).fixed());
        assertDoesNotThrow(()->ChartStyleValidator.validateTableBindings(restored,java.util.Set.of("region","sales"),java.util.Set.of("sales")));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validateTableBindings(restored,java.util.Set.of("sales"),java.util.Set.of("sales")));
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validateTableBindings(restored,java.util.Set.of("region","sales"),java.util.Set.of()));
    }
    @Test void invalidTableStyleIsRejected() {
        var s=new ChartVisualStyle();s.setTableRowHeight(81);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"table"));s.setTableRowHeight(32);
        s.setTableNullText("x\n");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"table"));s.setTableNullText(null);
        s.setTableColumns(java.util.List.of(new ChartVisualStyle.TableColumn("sales",59,"right","left",false,null)));assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"table"));
        s.setTableColumns(java.util.List.of(new ChartVisualStyle.TableColumn("sales",160,"right","left",false,java.util.List.of(new DashboardPresentation.Threshold("GT","bad","#FFFFFF")))));assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"table"));
    }
    @Test void pivotStylesRoundtripAndBounds() {
        var s=new ChartVisualStyle();s.setPivotHierarchy("tree");s.setPivotRowTotals(true);s.setPivotColTotals(true);s.setPivotRowSubtotals(true);s.setPivotColSubtotals(false);s.setPivotTotalsFirst(true);s.setPivotValuesInCols(false);s.setPivotRowHeight(40);s.setPivotColumnWidth(160);s.setPivotColorScale(true);s.setPivotLowColor("#EFF6FF");s.setPivotHighColor("#2563EB");s.setPivotHeaderBackground("#EEF2FF");s.setPivotHeaderColor("#312E81");
        var mapper=new ObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(s),ChartVisualStyle.class);assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"pivot"));assertTrue(restored.getPivotRowTotals());assertFalse(restored.getPivotValuesInCols());assertEquals("tree",restored.getPivotHierarchy());
        s.setPivotHierarchy("script");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"pivot"));s.setPivotHierarchy("grid");s.setPivotColumnWidth(401);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"pivot"));s.setPivotColumnWidth(160);s.setPivotHighColor("url(x)");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"pivot"));
    }
    @Test void mapPieceColorsRoundtripWithAutomaticFallback() {
        var s=new ChartVisualStyle();s.setMapColorMode("piecewise");s.setMapBreaks(java.util.List.of(0d,100d));s.setMapPieceColors(java.util.Arrays.asList("#22C55E",null,"#EF4444"));
        var mapper=new ObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(s),ChartVisualStyle.class);assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"map"));assertEquals(s.getMapPieceColors(),restored.getMapPieceColors());
    }
    @Test void mapPieceColorsRejectWrongCountAndUnsafeValues() {
        var s=new ChartVisualStyle();s.setMapBreaks(java.util.List.of(100d));s.setMapPieceColors(java.util.List.of("#FFFFFF"));assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"map"));
        s.setMapPieceColors(java.util.List.of("url(x)","#FFFFFF"));assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"map"));
        s.setMapPieceColors(java.util.Arrays.asList(null,null));assertDoesNotThrow(()->ChartStyleValidator.validate(s,"map"));
    }
    @Test void specializedChartStylesRoundtripAndEnforceBounds() {
        var s=new ChartVisualStyle();s.setHeatmapLowColor("#E8F3FF");s.setHeatmapHighColor("#2864DC");s.setHeatmapShowScale(false);s.setTreemapLeafDepth(6);s.setTreemapShowBreadcrumb(false);s.setSankeyNodeGap(20);s.setSankeyCurveness(.75);s.setBoxplotHorizontal(true);s.setBoxplotOutliers(true);s.setBoxplotOutlierColor("#EF4444");s.setBoxplotOutlierSize(10);s.setBoxplotOutlierSymbol("diamond");s.setBoxplotOutlierIqrMultiplier(1.8);s.setMapBaseStyle("dark");s.setMapProjection("mercator");s.setHistogramBins(12);s.setWaterfallShowTotal(true);
        var mapper=new ObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(s),ChartVisualStyle.class);assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"heatmap"));assertEquals(12,restored.getHistogramBins());assertTrue(restored.getBoxplotHorizontal());assertFalse(restored.getTreemapShowBreadcrumb());
        assertEquals("mercator",restored.getMapProjection());assertEquals("dark",restored.getMapBaseStyle());assertEquals(1.8,restored.getBoxplotOutlierIqrMultiplier());
        restored.setBoxplotOutlierIqrMultiplier(3.1);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(restored,"boxplot"));restored.setBoxplotOutlierIqrMultiplier(1.5);restored.setMapProjection("conic");assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(restored,"map"));restored.setMapProjection("raw");
        restored.setSankeyCurveness(1.1);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(restored,"sankey"));restored.setSankeyCurveness(.5);restored.setHistogramBins(51);assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(restored,"histogram"));
    }
}
