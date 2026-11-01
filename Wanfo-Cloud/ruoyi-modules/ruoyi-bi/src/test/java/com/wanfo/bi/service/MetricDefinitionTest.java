package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricDefinitionTest {
    BiMetricMapper mapper = mock(BiMetricMapper.class);
    BiDatasetTableMapper datasets = mock(BiDatasetTableMapper.class);
    ChartQueryService queries = mock(ChartQueryService.class);
    BiResourceAuthorizer resources = mock(BiResourceAuthorizer.class);
    CapabilityProvider caps = mock(CapabilityProvider.class);
    MetricPublishRequestMapper publishRequests = mock(MetricPublishRequestMapper.class);
    GlossaryTermMapper glossary = mock(GlossaryTermMapper.class);
    CatalogAuditService audit = mock(CatalogAuditService.class);
    JsonMapper json = JsonMapper.builder().build();
    MetricDefinitionService service = new MetricDefinitionService(mapper, datasets, queries, resources, caps,
            () -> new BiActor("test", 1L, "alice", null, Set.of()), json, publishRequests, glossary, audit);

    MetricDefinitionTest() {
        var ds = new BiDatasetTableDO();
        ds.setType("table");
        when(datasets.selectById(9L)).thenReturn(ds);
        when(publishRequests.countPending(any())).thenReturn(0);
        when(glossary.listByMetric(any())).thenReturn(List.of());
    }

    MetricDefinitionService.Definition atomic() {
        return new MetricDefinitionService.Definition(2, "成交额", "gmv_total", "atomic", 9L,
                new MetricDefinitionService.MeasureSpec("amount", "SUM"),
                List.of(), List.of("city"), null, null, null, null,
                "成交金额合计", "订单日", "元", "Asia/Shanghai", "alice");
    }

    BiMetricDO stored(MetricDefinitionService.Definition definition) {
        var row = new BiMetricDO();
        row.setId(7L);
        row.setDatasetId(9L);
        row.setCode(definition.code());
        row.setName(definition.name());
        row.setKind(definition.kind());
        row.setVersion(2);
        row.setPublishStatus("draft");
        row.setUsageCount(0);
        row.setDefinitionJson(json.writeValueAsString(definition));
        when(mapper.get(7L)).thenReturn(row);
        when(mapper.findByCode(definition.code())).thenReturn(row);
        return row;
    }

    @Test
    void creationValidatesAndRegistersResource() {
        doAnswer(c -> { ((BiMetricDO) c.getArgument(0)).setId(7L); return 1; }).when(mapper).insert(any());
        var r = service.create(new MetricDefinitionService.SaveRequest(null, atomic()));
        assertEquals(7L, r.get("id"));
        verify(resources).register("metric", 7L);
        verify(queries).compileOnly(any());
        var capture = org.mockito.ArgumentCaptor.forClass(BiMetricDO.class);
        verify(mapper).insert(capture.capture());
        assertEquals("gmv_total", capture.getValue().getCode());
        assertEquals("atomic", capture.getValue().getKind());
        assertEquals("alice", capture.getValue().getCreateBy());
    }

    @Test
    void duplicateOrInvalidCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new MetricDefinitionService.SaveRequest(null,
                new MetricDefinitionService.Definition(2, "成交额", "1bad", "atomic", 9L,
                        new MetricDefinitionService.MeasureSpec("amount", "SUM"),
                        List.of(), List.of(), null, null, null, null,
                        null, null, null, null, null))));
        when(mapper.findByCode("gmv_total")).thenReturn(new BiMetricDO());
        assertThrows(IllegalArgumentException.class, () -> service.create(new MetricDefinitionService.SaveRequest(null, atomic())));
        verify(mapper, never()).insert(any());
    }

    @Test
    void sqlDatasetRejected() {
        var sql = new BiDatasetTableDO();
        sql.setType("sql");
        when(datasets.selectById(8L)).thenReturn(sql);
        var bad = new MetricDefinitionService.Definition(2, "成交额", "gmv_sql", "atomic", 8L,
                new MetricDefinitionService.MeasureSpec("amount", "SUM"),
                List.of(), List.of(), null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(new MetricDefinitionService.SaveRequest(null, bad)));
        verify(mapper, never()).insert(any());
    }

    @Test
    void atomicRejectsRawAggregation() {
        var raw = new MetricDefinitionService.Definition(2, "成交额", "gmv_raw", "atomic", 9L,
                new MetricDefinitionService.MeasureSpec("amount", "NONE"),
                List.of(), List.of(), null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(new MetricDefinitionService.SaveRequest(null, raw)));
        verify(mapper, never()).insert(any());
    }

    @Test
    void publishedMetricCannotBeUpdatedUntilUnpublished() {
        var row = stored(atomic());
        row.setPublishStatus("published");
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, new MetricDefinitionService.SaveRequest(2, atomic())));
        assertTrue(e.getMessage().contains("取消发布"));
        verify(mapper, never()).update(any());
    }

    @Test
    void referencedMetricCannotUnpublishOrDelete() {
        var row = stored(atomic());
        row.setPublishStatus("published");
        row.setUsageCount(1);
        assertThrows(IllegalArgumentException.class, () -> service.unpublish(7L, 2));
        verify(mapper, never()).unpublish(any(), anyInt(), any());
        row.setPublishStatus("draft");
        assertThrows(IllegalArgumentException.class, () -> service.delete(7L));
        verify(mapper, never()).delete(any(), any());
    }

    @Test
    void unpublishedConsumeByCodeRejected() throws Exception {
        stored(atomic());
        assertThrows(IllegalArgumentException.class, () -> service.dataByCode("gmv_total"));
        verify(queries, never()).query(any());
    }

    @Test
    void consumeByCodeUsesFrozenPublishedModel() throws Exception {
        var draft = atomic();
        var published = new MetricDefinitionService.Definition(2, "成交额", "gmv_total", "atomic", 9L,
                new MetricDefinitionService.MeasureSpec("amount", "SUM"),
                List.of(), List.of("city"), null, null, null, null,
                "成交金额合计", "订单日", "元", "Asia/Shanghai", "alice");
        var row = stored(draft);
        row.setPublishStatus("published");
        row.setPublishedJson(json.writeValueAsString(published));
        when(queries.query(any())).thenReturn(new LinkedHashMap<>(Map.of("rows", List.of(), "executedSql", "SELECT 1", "params", List.of())));
        var dim = new QueryModel.Dimension("city", "city");
        var data = service.dataByCode("gmv_total", new MetricDefinitionService.MetricQueryRequest(List.of(dim), List.of(), 100, null));
        assertFalse(data.containsKey("executedSql"));
        assertFalse(data.containsKey("params"));
        var capture = org.mockito.ArgumentCaptor.forClass(QueryModel.class);
        verify(queries).query(capture.capture());
        assertEquals(9L, capture.getValue().getDatasetId());
        assertEquals(1, capture.getValue().getDimensions().size());
        assertEquals("city", capture.getValue().getDimensions().get(0).getField());
    }

    @Test
    void runtimeDimensionOutsideAllowlistRejected() throws Exception {
        var row = stored(atomic());
        row.setPublishStatus("published");
        row.setPublishedJson(row.getDefinitionJson());
        var dim = new QueryModel.Dimension("region", "region");
        assertThrows(IllegalArgumentException.class, () -> service.dataByCode("gmv_total",
                new MetricDefinitionService.MetricQueryRequest(List.of(dim), List.of(), null, null)));
        verify(queries, never()).query(any());
    }

    @Test
    void metricGrantDoesNotImplyDatasetQueryAccess() throws Exception {
        stored(atomic());
        doThrow(new BiAccessException(403, "denied")).when(resources).require("dataset", 9L, "query");
        assertThrows(BiAccessException.class, () -> service.detail(7L));
        verify(queries, never()).query(any());
    }

    @Test
    void staleUpdateFailsWithoutOverwriting() {
        stored(atomic());
        when(mapper.update(any())).thenReturn(0);
        var e = assertThrows(BiAccessException.class, () -> service.update(7L, new MetricDefinitionService.SaveRequest(2, atomic())));
        assertEquals(409, e.getCode());
    }

    @Test
    void codeCannotChangeAfterCreate() {
        stored(atomic());
        var renamed = new MetricDefinitionService.Definition(2, "成交额", "other_code", "atomic", 9L,
                new MetricDefinitionService.MeasureSpec("amount", "SUM"),
                List.of(), List.of("city"), null, null, null, null,
                "成交金额合计", "订单日", "元", "Asia/Shanghai", "alice");
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, new MetricDefinitionService.SaveRequest(2, renamed)));
        verify(mapper, never()).update(any());
    }

    @Test
    void listRequiresMetricAndDatasetPermissions() {
        var row = stored(atomic());
        when(mapper.list()).thenReturn(List.of(row));
        when(resources.allowed("metric", 7L, "view")).thenReturn(true);
        assertTrue(service.list().isEmpty());
        when(caps.has("bi:dataset:query")).thenReturn(true);
        when(resources.allowed("dataset", 9L, "view")).thenReturn(true);
        when(resources.allowed("dataset", 9L, "query")).thenReturn(true);
        assertEquals(1, service.list().size());
    }

    @Test
    void bindRequiresPublishedMetric() {
        stored(atomic());
        assertThrows(IllegalArgumentException.class,
                () -> service.bind(7L, new MetricDefinitionService.UsageRequest("screen", "board-1", null)));
        verify(mapper, never()).bind(any());
    }

    @Test
    void publishWritesHistoryFromCurrentDraft() {
        var draft = stored(atomic());
        when(mapper.publish(7L, 2, "alice")).thenReturn(1);
        var fresh = new BiMetricDO();
        fresh.setId(7L);
        fresh.setCode(draft.getCode());
        fresh.setName(draft.getName());
        fresh.setKind(draft.getKind());
        fresh.setDatasetId(9L);
        fresh.setPublishStatus("published");
        fresh.setPublishedVersion(3);
        fresh.setPublishedJson(draft.getDefinitionJson());
        when(mapper.get(7L)).thenReturn(draft, fresh);
        var r = service.publish(7L, 2);
        assertEquals(3, r.get("version"));
        var history = org.mockito.ArgumentCaptor.forClass(BiMetricHistoryDO.class);
        verify(mapper).insertHistory(history.capture());
        assertEquals("gmv_total", history.getValue().getCode());
        assertEquals(3, history.getValue().getPublishedVersion());
    }
}
