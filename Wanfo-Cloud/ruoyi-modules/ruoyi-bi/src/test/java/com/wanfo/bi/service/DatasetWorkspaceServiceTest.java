package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.dataset.*;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.query.*;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetWorkspaceServiceTest {
    final BiDatasetTableMapper datasets=mock(BiDatasetTableMapper.class);
    final BiDatasetFieldMapper fields=mock(BiDatasetFieldMapper.class);
    final DatasetWorkspaceMapper writes=mock(DatasetWorkspaceMapper.class);
    final SchemaMigrationMapper dependencies=mock(SchemaMigrationMapper.class);
    final DataSourceProvider sources=mock(DataSourceProvider.class);
    final CatalogService catalog=mock(CatalogService.class);
    final DatasetSqlNodeService nodes=mock(DatasetSqlNodeService.class);
    final BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    final CapabilityProvider caps=mock(CapabilityProvider.class);
    final UserQueryCache cache=mock(UserQueryCache.class);
    final BiDatasetTableDO stored=new BiDatasetTableDO();
    final DatasetWorkspaceService service=new DatasetWorkspaceService(datasets,fields,writes,dependencies,sources,catalog,nodes,resources,caps,cache);
    DatasetWorkspaceServiceTest() throws Exception {
        stored.setId(9L);stored.setName("Original");stored.setType("table");stored.setGroupId(0L);stored.setDatasourceId(1L);stored.setSourceCatalog("PUBLIC");stored.setSourceTable("orders");stored.setUnionSql("SELECT amount,city FROM demo.orders");
        when(datasets.selectById(9L)).thenReturn(stored);when(fields.selectByDatasetId(9L)).thenReturn(List.of(field(11L,"amount","DECIMAL"),field(12L,"city","VARCHAR")));
        var source=new BiDatasource();source.setId(1L);source.setSchemaName("demo");source.setStatus("0");when(sources.getById(1L)).thenReturn(source);
        when(catalog.plan(eq("demo"),eq("PUBLIC"),eq("orders"),anyList())).thenAnswer(inv->new CatalogService.TablePlan("PUBLIC","orders",inv.getArgument(3),List.of(Map.of("name","amount","type","DECIMAL"),Map.of("name","city","type","VARCHAR")),"SELECT amount,city FROM demo.orders"));
        when(writes.lock(9L)).thenReturn(9L);when(writes.updateDefinition(any())).thenReturn(1);when(writes.updateFieldDefinition(any())).thenReturn(1);
        when(writes.charts(9L)).thenReturn(List.of());when(writes.dashboards(9L)).thenReturn(List.of());when(dependencies.dependencyCounts(9L)).thenReturn(Map.of());
    }
    BiDatasetFieldDO field(Long id,String name,String type){var f=new BiDatasetFieldDO();f.setId(id);f.setDatasetId(9L);f.setOriginName(name);f.setOriginType(type);f.setShowName(name);f.setExtFlag("0");f.setDimFlag("amount".equals(name)?"0":"1");f.setOrderNum(id.intValue());return f;}
    DatasetWorkspaceService.Draft draft(String... selected){
        var row=new BiDatasetTableDO();row.setName("Updated");row.setGroupId(0L);row.setDatasourceId(1L);row.setSourceCatalog("PUBLIC");row.setSourceTable("orders");
        return new DatasetWorkspaceService.Draft(row,new DatasetJoinModel(),Arrays.stream(selected).map(name->new DatasetService.FieldSelection(name,"Label "+name,null)).toList(),Map.of(),String.valueOf(service.load(9L).get("revision")),null);
    }
    DatasetWorkspaceService.Draft confirmed(DatasetWorkspaceService.Draft d,DatasetWorkspaceService.Impact impact){return new DatasetWorkspaceService.Draft(d.dataset(),d.model(),d.fields(),d.parameterValues(),d.revision(),impact.token());}
    void chart(String config){var chart=new BiChartDO();chart.setId(77L);chart.setName("Sales");chart.setDatasetId(9L);chart.setDefinitionJson(config);when(writes.charts(9L)).thenReturn(List.of(chart));when(resources.allowed("chart",77L,"view")).thenReturn(true);}

    @Test void compatibleUpdatePreservesDatasetAndFieldIdsWithoutCreatingReplacement() throws Exception {
        var d=draft("amount","city");var impact=service.check(9L,d);assertTrue(impact.blockers().isEmpty());
        assertEquals(9L,service.save(9L,confirmed(d,impact),"tester").get("id"));
        verify(datasets,never()).insert(any());verify(fields,never()).insert(any());verify(fields,never()).deleteByDatasetId(any());
        verify(writes).updateFieldDefinition(argThat(f->f.getId()==11L && f.getShowName().equals("Label amount")));
        verify(writes).updateFieldDefinition(argThat(f->f.getId()==12L));verify(cache).clear(9L);
    }
    @Test void chartReferencedFieldDeletionIsBlockedBeforeAnyMutation() throws Exception {
        chart("{\"query\":{\"metrics\":[{\"field\":\"amount\"}]}}");var d=draft("city");var impact=service.check(9L,d);
        assertTrue(impact.blockers().stream().anyMatch(b->b.contains("77") && b.contains("amount")));
        assertThrows(IllegalArgumentException.class,()->service.save(9L,confirmed(d,impact),"tester"));verify(writes,never()).updateDefinition(any());verify(fields,never()).deleteById(any());
    }
    @Test void unusedFieldCanBeRemovedWhileKeepingChartReferences() throws Exception {
        chart("{\"dimensions\":[{\"field\":\"city\"}]}");var d=draft("city");var impact=service.check(9L,d);assertTrue(impact.blockers().isEmpty());
        service.save(9L,confirmed(d,impact),"tester");verify(fields).deleteById(11L);verify(writes).updateFieldDefinition(argThat(f->f.getId()==12L));
    }
    @Test void referencedTypeChangeIsBlocked() throws Exception {
        chart("{\"field\":\"amount\"}");when(catalog.plan(eq("demo"),anyString(),anyString(),anyList())).thenReturn(new CatalogService.TablePlan("PUBLIC","orders",List.of("amount"),List.of(Map.of("name","amount","type","VARCHAR")),"SELECT amount FROM demo.orders"));
        assertFalse(service.check(9L,draft("amount")).blockers().isEmpty());
    }
    @Test void frozenDashboardAndPolicyDependenciesAreConservativelyProtected() throws Exception {
        when(writes.dashboards(9L)).thenReturn(List.of(Map.of("id",99L,"name","Published")));when(dependencies.dependencyCounts(9L)).thenReturn(Map.of("policies",1));
        var impact=service.check(9L,draft("city"));assertTrue(impact.blockers().stream().anyMatch(b->b.contains("99")));assertTrue(impact.blockers().stream().anyMatch(b->b.contains("policies")));
        assertEquals("无权查看名称的引用资源",impact.references().get(0).get("name"));
    }
    @Test void staleEditorCannotOverwriteChangedDefinition() {
        var d=draft("amount","city");stored.setRemark("another editor changed this");assertThrows(IllegalArgumentException.class,()->service.check(9L,d));verifyNoInteractions(catalog);
    }
    @Test void changedDownstreamDefinitionsInvalidateConfirmationToken() throws Exception {
        var d=draft("amount","city");var impact=service.check(9L,d);chart("{\"field\":\"city\"}");
        assertThrows(IllegalArgumentException.class,()->service.save(9L,confirmed(d,impact),"tester"));verify(writes,never()).updateDefinition(any());
    }
    @Test void editCapabilityIsIndependentFromQueryAndCreation() {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",9L,"edit");assertThrows(BiAccessException.class,()->service.load(9L));verifyNoInteractions(fields);
    }
    @Test void legacyCalculatedFieldsRestoreRatherThanDisappear() {
        var calc=field(13L,"doubled","DECIMAL");calc.setExtFlag("1");calc.setExtFormula("[amount]*2");when(fields.selectByDatasetId(9L)).thenReturn(List.of(field(11L,"amount","DECIMAL"),calc));
        var loaded=service.load(9L);var model=(DatasetJoinModel)loaded.get("model");assertEquals("[amount]*2",model.getDerivedFields().get(0).formula());assertEquals(1,((List<?>)loaded.get("baseFields")).size());
    }
    @Test void mixedLegacyAdditionsAndEditedLabelsSurviveReload() {
        stored.setModelJson("{\"derivedFields\":[{\"kind\":\"calc\",\"originName\":\"doubled\",\"showName\":\"old label\",\"formula\":\"[amount]*2\"}]}");
        var doubled=field(13L,"doubled","DECIMAL");doubled.setExtFlag("1");doubled.setShowName("Updated label");doubled.setExtFormula("[amount]*2");
        var later=field(14L,"later","DECIMAL");later.setExtFlag("1");later.setExtFormula("[doubled]+1");
        when(fields.selectByDatasetId(9L)).thenReturn(List.of(field(11L,"amount","DECIMAL"),doubled,later));
        var loaded=(DatasetJoinModel)service.load(9L).get("model");assertEquals(2,loaded.getDerivedFields().size());assertEquals("Updated label",loaded.getDerivedFields().get(0).showName());assertEquals("[doubled]+1",loaded.getDerivedFields().get(1).formula());
    }
    @Test void explicitAcknowledgementIsRequiredEvenWhenNoFieldsBreak() throws Exception {
        var d=draft("amount","city");assertThrows(IllegalArgumentException.class,()->service.save(9L,d,"tester"));verify(writes,never()).updateDefinition(any());
    }
}
