package com.wanfo.bi.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.security.BiAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DashboardPreviewTest {
    DashboardTest f=new DashboardTest();
    DashboardService.PreviewRequest request(boolean control){
        f.stored(true);when(f.queries.datasetFields(9L)).thenReturn(List.of(Map.of("name","city","type","VARCHAR"),Map.of("name","id","type","INT")));
        var settings=new DashboardInteraction.Settings(control?List.of(new DashboardInteraction.Control("city","城市","enum","上海",List.of(new DashboardInteraction.Target("a","city")))):null,null);
        var d=new DashboardService.Definition(1,"Unsaved draft",List.of(f.panel("a",0,0)),null,null,null,settings);
        return new DashboardService.PreviewRequest(2,d,null);
    }
    @Test void unsavedPreviewUsesEditorQueryAndNeverWritesDraftOrPublication() throws Exception {
        var r=request(true);when(f.queries.query(any())).thenReturn(Map.of("columns",List.of("place","n"),"rows",List.of(),"executedSql","secret SQL","params",List.of("private")));
        var result=f.service.preview(7L,r);String json=f.json.writeValueAsString(result);assertTrue(json.contains("Unsaved draft"));assertFalse(json.contains("secret SQL"));assertFalse(json.contains("executedSql"));assertFalse(json.contains("datasetId"));
        var q=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries).query(q.capture());assertEquals("private-filter",q.getValue().getFilters().get(0).getValue());assertEquals("上海",q.getValue().getFilters().get(1).getValue());verify(f.queries,never()).queryForDashboard(any());verify(f.mapper,never()).update(any());verify(f.mapper,never()).publish(any(),anyInt(),anyInt(),anyString(),anyString());
    }
    @Test void stalePreviewIsRejectedBeforeReadingSourceCharts() throws Exception {var r=request(false);assertEquals(409,assertThrows(BiAccessException.class,()->f.service.preview(7L,new DashboardService.PreviewRequest(1,r.definition(),null))).getCode());verify(f.charts,never()).exportForDashboard(any());verify(f.queries,never()).query(any());}
    @Test void resourceEditIsRequiredBeforeExport() throws Exception {var r=request(false);doThrow(new BiAccessException(403,"no edit")).when(f.resources).require("dashboard",7L,"edit");assertThrows(BiAccessException.class,()->f.service.preview(7L,r));verify(f.charts,never()).exportForDashboard(any());}
    @Test void currentEditorQueryRevocationCannotFallBackToConsumerIdentity() throws Exception {var r=request(false);when(f.queries.query(any())).thenThrow(new BiAccessException(403,"no query"));assertThrows(BiAccessException.class,()->f.service.preview(7L,r));verify(f.queries,never()).queryForDashboard(any());}
    @Test void newPreviewRequiresAddWithoutRequiringExistingResource() throws Exception {var r=request(false);when(f.queries.query(any())).thenReturn(Map.of("rows",List.of()));f.service.preview(null,new DashboardService.PreviewRequest(null,r.definition(),null));verify(f.caps).require("bi:dashboard:add");verify(f.resources,never()).require("dashboard",7L,"edit");}
    @Test void optionsUseBoundFieldsAndEditorScope() throws Exception {var r=request(true);when(f.queries.query(any())).thenReturn(Map.of("rows",List.of(Map.of("option_value","上海"))));assertEquals(List.of("上海"),f.service.previewOptions(7L,"city",r).get("values"));var q=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries).query(q.capture());assertEquals("city",q.getValue().getDimensions().get(0).getField());assertEquals(1,q.getValue().getFilters().size());assertEquals(101,q.getValue().getLimit());verify(f.queries,never()).queryForDashboard(any());}
    @Test void arbitraryInputKeysCannotRunPreviewQuery() throws Exception {var r=request(true);assertThrows(IllegalArgumentException.class,()->f.service.preview(7L,new DashboardService.PreviewRequest(2,r.definition(),Map.of("query",Map.of()))));assertThrows(IllegalArgumentException.class,()->f.service.previewOptions(7L,"forged",r));verify(f.queries,never()).query(any());}
    @Test void lockedFlagIsRoundTrippedAndLegacyDefaultsUnlocked() throws Exception {var r=request(false);when(f.mapper.update(any())).thenReturn(1);var p=new DashboardService.Panel("a",4L,"Locked",0,0,6,8,"chart",null,null,true);f.service.update(7L,new DashboardService.SaveRequest(2,f.definition(List.of(p))));var c=org.mockito.ArgumentCaptor.forClass(com.wanfo.bi.domain.BiDashboardDO.class);verify(f.mapper).update(c.capture());assertTrue(f.json.readValue(c.getValue().getDefinitionJson(),DashboardService.Definition.class).panels().get(0).locked());assertFalse(f.panel("old",0,0).locked());}
}
