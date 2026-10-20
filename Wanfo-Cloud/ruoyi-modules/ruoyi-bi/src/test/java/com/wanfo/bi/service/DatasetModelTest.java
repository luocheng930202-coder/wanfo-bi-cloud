package com.wanfo.bi.service;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetModelTest
{
    private final BiDatasetGroupMapper groups=mock(BiDatasetGroupMapper.class);
    private final BiDatasetTableMapper datasets=mock(BiDatasetTableMapper.class);
    private final BiDatasetFieldMapper fields=mock(BiDatasetFieldMapper.class);
    private final DataSourceProvider sources=mock(DataSourceProvider.class);
    private final CalciteQueryEngine engine=mock(CalciteQueryEngine.class);
    private final CatalogService catalog=mock(CatalogService.class);
    private final BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);
    private final CapabilityProvider capabilities=mock(CapabilityProvider.class);
    private final DatasetDataPolicy policies=mock(DatasetDataPolicy.class);
    { when(policies.resolve(anyLong())).thenReturn(DatasetDataPolicy.Scope.unrestricted()); }
    private final DatasetService service=new DatasetService(groups,datasets,fields,sources,engine,catalog,resources,capabilities,policies);

    private BiDatasetTableDO table()
    {
        BiDatasetTableDO row=new BiDatasetTableDO(); row.setName("Demo"); row.setType("table");
        row.setDatasourceId(7L); row.setSourceCatalog("poc_a"); row.setSourceTable("orders"); return row;
    }
    private BiDatasource source()
    {
        BiDatasource source=new BiDatasource(); source.setId(7L); source.setSchemaName("demo"); source.setStatus("0");
        when(sources.getById(7L)).thenReturn(source); return source;
    }

    @Test void previewChecksDatasetQueryBeforeReadingItsDefinition() throws Exception
    {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",9L,"query");
        assertThrows(BiAccessException.class,()->service.preview(9L,100));
        verifyNoInteractions(datasets,catalog,engine,sources);
    }

    @Test void consumerUsesSavedDefinitionWithoutRawSourceModellingAccess() throws Exception
    {
        source(); BiDatasetTableDO row=table(); row.setId(9L); when(datasets.selectById(9L)).thenReturn(row);
        BiDatasetFieldDO field=new BiDatasetFieldDO(); field.setOriginName("city"); when(fields.selectByDatasetId(9L)).thenReturn(List.of(field));
        when(catalog.savedPreview("demo","poc_a","orders",List.of("city"),10,DatasetDataPolicy.Scope.unrestricted())).thenReturn(new java.util.LinkedHashMap<>(Map.of("ok",true)));
        assertEquals(true,service.preview(9L,10).get("ok"));
        verify(resources).require("dataset",9L,"view"); verify(resources).require("dataset",9L,"query");
        verify(resources,never()).require("datasource",7L,"model"); verifyNoInteractions(engine);
    }

    @Test void creationUsesValidatedCatalogAndRegistersOwner() throws Exception
    {
        source(); BiDatasetTableDO form=table();
        when(catalog.plan("demo","poc_a","orders",List.of("city"))).thenReturn(new CatalogService.TablePlan("poc_a","orders",List.of("city"),List.of(Map.of("name","city","type","VARCHAR","suggestAs","dimension")),"unused"));
        doAnswer(call->{ ((BiDatasetTableDO)call.getArgument(0)).setId(9L); return 1; }).when(datasets).insert(any());
        service.createTableDataset(form,List.of(new DatasetService.FieldSelection("city",null,null)),"alice");
        assertEquals("poc_a",form.getSourceCatalog()); assertEquals("alice",form.getCreateBy());
        verify(resources).register("dataset",9L); verify(fields).insertBatch(anyList());
    }

    @Test void missingSourceModelGrantCannotPersistDataset() throws Exception
    {
        source();
        when(catalog.plan(anyString(),anyString(),anyString(),anyList())).thenThrow(new BiAccessException(403,"denied"));
        assertThrows(BiAccessException.class,()->service.createTableDataset(table(),List.of(new DatasetService.FieldSelection("city",null,null)),"alice"));
        verify(datasets,never()).insert(any()); verify(fields,never()).insertBatch(anyList());
    }

    @Test void groupCreationChecksParentWriteAccess()
    {
        doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset-group",4L,"edit");
        assertThrows(BiAccessException.class,()->service.createGroup("Child",4L,"alice"));
        verifyNoInteractions(groups);
    }

    @Test void hiddenDatasetsAreFilteredBeforeFieldLookup()
    {
        BiDatasetTableDO row=table(); row.setId(9L); when(datasets.selectAll(null)).thenReturn(List.of(row));
        assertTrue(service.list(null).isEmpty()); verifyNoInteractions(fields);
    }
}
