package com.wanfo.bi.service;

import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.security.BiAccessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.wanfo.bi.service.DashboardInteraction.*;

class DashboardInteractionTest {
    DashboardTest f=new DashboardTest();
    Target target(String panel,String field,String type){return new Target(panel,field,type);}
    Settings settings(){return new Settings(List.of(new Control("city","城市","enum","上海",List.of(target("a","city","text"),target("b","city","text"))),new Control("amount","数量","number",null,List.of(target("b","id","number")))),List.of(new Link("click","a","city",List.of(target("b","city","text")))));}
    void published(Settings settings) throws Exception {var row=f.stored(true);row.setPublishedJson(f.json.writeValueAsString(new DashboardService.Publication(1,"Public",List.of(new DashboardService.PublishedPanel(f.panel("a",0,0),f.chart()),new DashboardService.PublishedPanel(f.panel("b",6,0),f.chart())),null,null,null,settings)));when(f.queries.queryForDashboard(any())).thenReturn(Map.of("columns",List.of("place","n"),"rows",List.of(Map.of("option_value","上海"))));}
    @Test void boundInputsAreAndedWithFrozenFiltersAndDoNotMutatePublication() throws Exception {
        published(settings());f.service.panelData(7L,"b",1,Map.of("filters",Map.of("city","广州","amount",List.of(0,0)),"links",Map.of("click","广州")));
        var capture=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries,times(2)).queryForDashboard(capture.capture());var q=capture.getAllValues().get(1);
        assertEquals("private-filter",q.getFilters().get(0).getValue());assertEquals("广州",q.getFilters().get(1).getValue());assertEquals(List.of(BigDecimal.ZERO,BigDecimal.ZERO),q.getFilters().get(2).getValue());assertEquals("广州",q.getFilters().get(3).getValue());
        clearInvocations(f.queries);f.service.panelData(7L,"a",1);verify(f.queries).queryForDashboard(capture.capture());assertEquals("上海",capture.getValue().getFilters().get(1).getValue());
    }
    @Test void clearingUsesExplicitNullAndZeroIsNotEmpty() throws Exception {
        published(settings());var filters=new HashMap<String,Object>();filters.put("city",null);filters.put("amount",List.of(0,0));f.service.panelData(7L,"b",1,Map.of("filters",filters));
        var c=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries).queryForDashboard(c.capture());assertEquals(2,c.getValue().getFilters().size());assertEquals("BETWEEN",c.getValue().getFilters().get(1).getOp());
    }
    @Test void arbitraryFieldsQueriesKeysAndInvalidValuesNeverExecute() throws Exception {
        published(settings());for(var body:List.of(Map.<String,Object>of("query",Map.of()),Map.<String,Object>of("datasetId",999),Map.<String,Object>of("filters",Map.of("unknown","x")),Map.<String,Object>of("links",Map.of("unknown","x")),Map.<String,Object>of("filters",Map.of("city",Map.of("sql","x"))),Map.<String,Object>of("filters",Map.of("amount",List.of(9,1)))))assertThrows(IllegalArgumentException.class,()->f.service.panelData(7L,"b",1,body));verifyNoInteractions(f.queries);
    }
    @Test void enumCandidatesUseConsumerQueryAndAppliedFiltersWithoutOwnSelection() throws Exception {
        published(settings());var r=f.service.options(7L,"city",1,Map.of("filters",Map.of("city","广州")));
        assertEquals(List.of("上海"),r.get("values"));var c=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries).queryForDashboard(c.capture());assertEquals("city",c.getValue().getDimensions().get(0).getField());assertTrue(c.getValue().getMetrics().isEmpty());assertEquals(101,c.getValue().getLimit());assertEquals(1,c.getValue().getFilters().size());
        verify(f.queries,never()).query(any());verify(f.queries,never()).datasetFields(any());
    }
    @Test void sourceRevocationStopsLinkTargetsAndCandidateAccess() throws Exception {
        published(settings());doThrow(new BiAccessException(403,"revoked")).when(f.queries).queryForDashboard(any());assertThrows(BiAccessException.class,()->f.service.panelData(7L,"b",1,Map.of("links",Map.of("click","上海"))));assertThrows(BiAccessException.class,()->f.service.options(7L,"city",1,null));
    }
    @Test void manifestContainsOnlyOpaqueControlsAndSourcePanelKeys() throws Exception {
        published(settings());String json=f.json.writeValueAsString(f.service.view(7L));assertFalse(json.contains("sourceField"));assertFalse(json.contains("targets"));assertFalse(json.contains("datasetId"));assertFalse(json.contains("private-filter"));assertTrue(json.contains("defaultValue"));
    }
    @Test void mappingsAreCheckedAndFamilyIsServerDerived() throws Exception {
        var panels=List.of(new DashboardService.PublishedPanel(f.panel("a",0,0),f.chart()),new DashboardService.PublishedPanel(f.panel("b",6,0),f.chart()));
        var fields=Map.of("a",Map.of("city","VARCHAR","id","INT"),"b",Map.of("city","VARCHAR","id","INT"));
        var s=validate(settings(),panels,fields);assertEquals("text",s.controls().get(0).targets().get(0).valueType());
        assertThrows(IllegalArgumentException.class,()->validate(new Settings(List.of(new Control("x","bad","enum",null,List.of(target("a","city","text"),target("b","id","text")))),null),panels,fields));
        assertThrows(IllegalArgumentException.class,()->validate(new Settings(null,List.of(new Link("x","a","id",List.of(target("b","id","number"))))),panels,fields));
        assertThrows(IllegalArgumentException.class,()->validate(new Settings(List.of(new Control("x","bad","text",null,List.of(target("a","missing","text")))),null),panels,fields));
    }
    @Test void boundedDatesNumbersAndNullLinks() throws Exception {
        assertEquals(List.of("2026-01-01","2026-12-31"),value("date","date",List.of("2026-01-01","2026-12-31")));
        assertThrows(IllegalArgumentException.class,()->value("date","date",List.of("2026-02-30","2026-03-01")));assertThrows(IllegalArgumentException.class,()->value("number","number",List.of("NaN",1)));assertThrows(IllegalArgumentException.class,()->value("text","text","x".repeat(257)));
        published(settings());Map<String,Object> links=new HashMap<>();links.put("click",null);f.service.panelData(7L,"b",1,Map.of("links",links));var c=org.mockito.ArgumentCaptor.forClass(QueryModel.class);verify(f.queries,times(2)).queryForDashboard(c.capture());assertEquals("IS_NULL",c.getValue().getFilters().get(2).getOp());
    }
    @Test void candidateOptionsRejectStaleOrUnpublishedControlBeforeQueries() throws Exception {
        published(settings());assertThrows(BiAccessException.class,()->f.service.options(7L,"city",999,null));assertThrows(IllegalArgumentException.class,()->f.service.options(7L,"forged",1,null));assertThrows(IllegalArgumentException.class,()->f.service.options(7L,"amount",1,null));verifyNoInteractions(f.queries);
    }
}
