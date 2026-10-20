package com.wanfo.bi.service;
import java.util.*;
import com.wanfo.bi.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DashboardComponentsTest {
    DashboardTest f=new DashboardTest();
    DashboardService.Panel text(String value){return new DashboardService.Panel("text",null,"说明",0,0,6,4,"text",null,new DashboardContent.Content(value,null));}
    @Test void staticComponentsDoNotExportChartsOrCreateDataBindings(){
        doAnswer(i->{((BiDashboardDO)i.getArgument(0)).setId(7L);return 1;}).when(f.mapper).insert(any());
        f.service.create(new DashboardService.SaveRequest(null,f.definition(List.of(text("<script>plain text</script>")))));
        verifyNoInteractions(f.charts,f.queries);verify(f.mapper,never()).bind(any(),any(),any(),any());
        var c=org.mockito.ArgumentCaptor.forClass(BiDashboardDO.class);verify(f.mapper).insert(c.capture());assertTrue(c.getValue().getDefinitionJson().contains("plain text"));
    }
    @Test void staticContentIsFrozenInPublicationAndCannotQuery() throws Exception {
        var row=f.stored(true);row.setPublishedJson(f.json.writeValueAsString(new DashboardService.Publication(1,"Static",List.of(new DashboardService.PublishedPanel(text("Published"),null)))));row.setDefinitionJson(f.json.writeValueAsString(f.definition(List.of(text("Draft")))));
        var result=f.json.writeValueAsString(f.service.view(7L));assertTrue(result.contains("Published"));assertFalse(result.contains("Draft"));assertThrows(IllegalArgumentException.class,()->f.service.panelData(7L,"text",1));verifyNoInteractions(f.queries);
    }
    @Test void kpiUsesFirstAggregateAcrossAllDimensions(){
        f.stored(false);when(f.mapper.update(any())).thenReturn(1);var p=new DashboardService.Panel("kpi",4L,"总数",0,0,4,4,"kpi",null);f.service.update(7L,new DashboardService.SaveRequest(2,f.definition(List.of(p))));
        var c=org.mockito.ArgumentCaptor.forClass(BiDashboardDO.class);verify(f.mapper).update(c.capture());
        var row=f.mapper.get(7L);row.setDefinitionJson(c.getValue().getDefinitionJson());when(f.mapper.publish(eq(7L),eq(2),eq(3),anyString(),anyString())).thenReturn(1);f.service.publish(7L,2);
        var pub=org.mockito.ArgumentCaptor.forClass(String.class);verify(f.mapper).publish(eq(7L),eq(2),eq(3),pub.capture(),anyString());var q=f.json.readValue(pub.getValue(),DashboardService.Publication.class).panels().get(0).chart().query();assertTrue(q.getDimensions().isEmpty());assertEquals(1,q.getMetrics().size());assertEquals("COUNT",q.getMetrics().get(0).getAgg());assertEquals("private-filter",q.getFilters().get(0).getValue());
    }
    @Test void nonAggregateKpiAndStaticChartReferencesAreRejected(){
        var d=f.chart();d.query().getMetrics().get(0).setAgg("NONE");when(f.charts.exportForDashboard(4L)).thenReturn(d);assertThrows(IllegalArgumentException.class,()->f.service.create(new DashboardService.SaveRequest(null,f.definition(List.of(new DashboardService.Panel("a",4L,"bad",0,0,4,4,"kpi",null))))));
        assertThrows(IllegalArgumentException.class,()->f.service.create(new DashboardService.SaveRequest(null,f.definition(List.of(new DashboardService.Panel("a",4L,"bad",0,0,4,4,"text",null,new DashboardContent.Content("x",null)))))));
    }
    @Test void imageAndTextBoundsRejectUrlsSvgAndMismatchedBytes(){
        for(var image:List.of("https://example.com/x.png","data:image/svg+xml;base64,PHN2Zz4=","data:image/png;base64,YWJj","data:image/png;base64,"+"A".repeat(350000)))assertThrows(IllegalArgumentException.class,()->DashboardContent.normalize("image",new DashboardContent.Content(null,image)));
        assertThrows(IllegalArgumentException.class,()->DashboardContent.normalize("text",new DashboardContent.Content("x".repeat(2001),null)));
        String png="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII=";assertEquals(png,DashboardContent.normalize("image",new DashboardContent.Content(null,png)).image());
    }
    @Test void incompleteStaticContentCanSaveButCannotPublish(){
        var row=f.stored(false);row.setDefinitionJson(f.json.writeValueAsString(f.definition(List.of(text("")))));assertThrows(IllegalArgumentException.class,()->f.service.publish(7L,2));verify(f.mapper,never()).publish(any(),anyInt(),anyInt(),anyString(),anyString());
    }
}
