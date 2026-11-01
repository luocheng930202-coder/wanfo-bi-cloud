package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogAssetServiceTest
{
    BiMetadataScanMapper mapper = mock(BiMetadataScanMapper.class);
    DataSourceProvider sources = mock(DataSourceProvider.class);
    BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    CapabilityProvider capabilities = mock(CapabilityProvider.class);
    CurrentActorProvider actors = mock(CurrentActorProvider.class);
    CatalogAssetService service = new CatalogAssetService(mapper, sources, resources, capabilities, new ObjectMapper(), actors);

    @Test void searchPassesOnlyAuthorizedDatasourceIdsIntoCountAndPageQueries()
    {
        BiDatasource visible = source(1L);
        BiDatasource hidden = source(2L);
        when(sources.listEnabled()).thenReturn(List.of(visible, hidden));
        when(resources.allowed("datasource", 1L, "view")).thenReturn(true);
        when(resources.allowed("datasource", 2L, "view")).thenReturn(false);
        when(mapper.searchAssets(List.of(1L), "TABLE", null, "orders", 0, 20)).thenReturn(List.of(Map.of("id", 9L)));
        when(mapper.countAssets(List.of(1L), "TABLE", null, "orders")).thenReturn(1L);

        Map<String, Object> result = service.search(null, "table", null, "orders", 1, 20);

        assertEquals(1L, result.get("total"));
        assertEquals(List.of(Map.of("id", 9L)), result.get("items"));
        verify(mapper).searchAssets(List.of(1L), "TABLE", null, "orders", 0, 20);
        verify(mapper).countAssets(List.of(1L), "TABLE", null, "orders");
    }

    @Test void unauthorizedDatasourceReturnsForbiddenWithoutQueryingCatalog()
    {
        when(sources.listEnabled()).thenReturn(List.of(source(2L)));
        when(resources.allowed("datasource", 2L, "view")).thenReturn(false);

        assertEquals(403, assertThrows(BiAccessException.class,
                () -> service.search(2L, null, null, null, 1, 20)).getCode());
        verifyNoInteractions(mapper);
    }

    @Test void emptyAuthorizedScopeDoesNotQueryOrLeakCatalogCounts()
    {
        when(sources.listEnabled()).thenReturn(List.of(source(2L)));
        when(resources.allowed("datasource", 2L, "view")).thenReturn(false);

        Map<String, Object> result = service.search(null, null, null, null, 1, 20);

        assertEquals(0L, result.get("total"));
        assertEquals(List.of(), result.get("items"));
        verifyNoInteractions(mapper);
    }

    @Test void invalidPagingIsRejectedBeforeDatasourceLookup()
    {
        assertThrows(IllegalArgumentException.class, () -> service.search(null, null, null, null, 0, 20));
        verifyNoInteractions(sources, mapper);
    }

    private static BiDatasource source(Long id)
    {
        BiDatasource source = new BiDatasource();
        source.setId(id); source.setStatus("0"); source.setSchemaName("source_" + id);
        return source;
    }
}
