package com.wanfo.bi.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end lifecycle on mocked persistence: atomic → publish → derived → composite,
 * with runtime dimensions and scalar composite evaluation.
 */
class MetricStandardLifecycleTest {
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

    Map<Long, BiMetricDO> store = new LinkedHashMap<>();
    Map<String, BiMetricDO> byCode = new LinkedHashMap<>();
    AtomicLong seq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        var ds = new BiDatasetTableDO();
        ds.setType("table");
        when(datasets.selectById(9L)).thenReturn(ds);
        when(publishRequests.countPending(any())).thenReturn(0);
        when(glossary.listByMetric(any())).thenReturn(List.of());
        when(caps.has(anyString())).thenReturn(true);
        doNothing().when(caps).require(anyString());
        doNothing().when(resources).require(anyString(), anyLong(), anyString());
        doNothing().when(resources).register(anyString(), anyLong());
        when(resources.allowed(anyString(), anyLong(), anyString())).thenReturn(true);
        when(queries.compileOnly(any())).thenReturn(null);

        doAnswer(inv -> {
            BiMetricDO row = inv.getArgument(0);
            long id = seq.incrementAndGet();
            row.setId(id);
            row.setVersion(0);
            row.setPublishStatus("draft");
            row.setUsageCount(0);
            store.put(id, clone(row));
            byCode.put(row.getCode(), store.get(id));
            return 1;
        }).when(mapper).insert(any());

        when(mapper.get(anyLong())).thenAnswer(inv -> {
            BiMetricDO row = store.get(inv.getArgument(0, Long.class));
            return row == null ? null : clone(row);
        });
        when(mapper.findByCode(anyString())).thenAnswer(inv -> {
            BiMetricDO row = byCode.get(inv.getArgument(0, String.class));
            return row == null ? null : clone(row);
        });
        when(mapper.list()).thenAnswer(inv -> store.values().stream().map(this::clone).toList());
        when(mapper.update(any())).thenAnswer(inv -> {
            BiMetricDO incoming = inv.getArgument(0);
            BiMetricDO current = store.get(incoming.getId());
            if (current == null || !Objects.equals(current.getVersion(), incoming.getVersion())) return 0;
            incoming.setPublishStatus(current.getPublishStatus());
            incoming.setPublishedJson(current.getPublishedJson());
            incoming.setPublishedVersion(current.getPublishedVersion());
            incoming.setUsageCount(current.getUsageCount());
            incoming.setVersion(current.getVersion() + 1);
            store.put(incoming.getId(), clone(incoming));
            byCode.put(incoming.getCode(), store.get(incoming.getId()));
            return 1;
        });
        when(mapper.publish(anyLong(), anyInt(), anyString())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Integer expected = inv.getArgument(1);
            BiMetricDO current = store.get(id);
            if (current == null || !Objects.equals(current.getVersion(), expected)) return 0;
            current.setPublishStatus("published");
            current.setPublishedJson(current.getDefinitionJson());
            current.setPublishedVersion((current.getPublishedVersion() == null ? 0 : current.getPublishedVersion()) + 1);
            current.setVersion(expected + 1);
            byCode.put(current.getCode(), current);
            return 1;
        });
        when(mapper.insertHistory(any())).thenReturn(1);
    }

    private BiMetricDO clone(BiMetricDO src) {
        var row = new BiMetricDO();
        row.setId(src.getId());
        row.setCode(src.getCode());
        row.setName(src.getName());
        row.setKind(src.getKind());
        row.setDatasetId(src.getDatasetId());
        row.setDefinitionJson(src.getDefinitionJson());
        row.setPublishedJson(src.getPublishedJson());
        row.setPublishStatus(src.getPublishStatus());
        row.setVersion(src.getVersion());
        row.setPublishedVersion(src.getPublishedVersion());
        row.setUsageCount(src.getUsageCount());
        row.setCreateBy(src.getCreateBy());
        row.setUpdateBy(src.getUpdateBy());
        return row;
    }

    private MetricDefinitionService.Definition atomic(String code, String field, String agg, List<String> dims) {
        return new MetricDefinitionService.Definition(2, code, code, "atomic", 9L,
                new MetricDefinitionService.MeasureSpec(field, agg),
                List.of(), dims, null, null, null, null,
                "业务定义-" + code, "订单日", "元", "Asia/Shanghai", "alice");
    }

    @Test
    void atomicDerivedCompositeLifecycleWithRuntimeDimensions() throws Exception {
        // 1) Create & publish GMV atomic
        var gmvCreate = service.create(new MetricDefinitionService.SaveRequest(null,
                atomic("gmv_total", "amount", "SUM", List.of("city", "channel"))));
        Long gmvId = ((Number) gmvCreate.get("id")).longValue();
        service.publish(gmvId, 0);

        // 2) Create & publish order count atomic
        var cntCreate = service.create(new MetricDefinitionService.SaveRequest(null,
                atomic("order_cnt", "id", "COUNT", List.of("city", "channel"))));
        Long cntId = ((Number) cntCreate.get("id")).longValue();
        service.publish(cntId, 0);

        assertEquals("published", store.get(gmvId).getPublishStatus());
        assertEquals("published", store.get(cntId).getPublishStatus());

        // 3) Query atomic with runtime city dimension
        doAnswer(inv -> {
            QueryModel q = inv.getArgument(0);
            Map<String, Object> row = new LinkedHashMap<>();
            if (q.getDimensions() != null && !q.getDimensions().isEmpty()) row.put("city", "上海");
            for (var m : q.getMetrics()) {
                String alias = m.getAlias() == null ? m.getField() : m.getAlias();
                if ("gmv".equals(alias) || "gmv_total".equals(alias) || "amount".equals(m.getField())) row.put(alias, 1000);
                else if ("orders".equals(alias) || "order_cnt".equals(alias) || "id".equals(m.getField())) row.put(alias, 10);
                else row.put(alias, 1);
            }
            return new LinkedHashMap<>(Map.of("columns", List.copyOf(row.keySet()), "rows", List.of(row), "rowCount", 1));
        }).when(queries).query(any());

        var dim = new QueryModel.Dimension("city", "city");
        var gmvData = service.dataByCode("gmv_total",
                new MetricDefinitionService.MetricQueryRequest(List.of(dim), List.of(), 100, null));
        assertEquals(1, ((List<?>) gmvData.get("rows")).size());
        verify(queries, atLeastOnce()).query(argThat(q ->
                q.getDimensions().size() == 1 && "city".equals(q.getDimensions().get(0).getField())));

        // Reject illegal dimension
        assertThrows(IllegalArgumentException.class, () -> service.dataByCode("gmv_total",
                new MetricDefinitionService.MetricQueryRequest(
                        List.of(new QueryModel.Dimension("region", "region")), List.of(), null, null)));

        // 4) Derived AOV = gmv/orders
        var derived = new MetricDefinitionService.Definition(2, "客单价", "aov", "derived", 9L, null,
                List.of(), List.of("city"),
                List.of(new MetricDefinitionService.ComponentRef("gmv_total", "gmv"),
                        new MetricDefinitionService.ComponentRef("order_cnt", "orders")),
                "gmv/orders", null, null,
                "成交额/订单数", "订单日", "元", "Asia/Shanghai", "alice");
        var aovCreate = service.create(new MetricDefinitionService.SaveRequest(null, derived));
        Long aovId = ((Number) aovCreate.get("id")).longValue();
        service.publish(aovId, 0);

        var aovData = service.dataByCode("aov",
                new MetricDefinitionService.MetricQueryRequest(List.of(dim), List.of(), 100, null));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aovRows = (List<Map<String, Object>>) aovData.get("rows");
        assertEquals(1, aovRows.size());
        assertEquals(100.0, ((Number) aovRows.get(0).get("value")).doubleValue(), 0.0001);
        assertEquals("上海", aovRows.get(0).get("city"));
        assertTrue(aovData.containsKey("derived"));

        // 5) Composite margin = (gmv_total - order_cnt) / gmv_total  using published atomics as scalar components
        doAnswer(inv -> {
            QueryModel q = inv.getArgument(0);
            String alias = q.getMetrics().get(0).getAlias();
            double value = "order_cnt".equals(alias) || "id".equals(q.getMetrics().get(0).getField()) ? 10d : 1000d;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(alias, value);
            return new LinkedHashMap<>(Map.of("columns", List.of(alias), "rows", List.of(row), "rowCount", 1));
        }).when(queries).query(any());

        var composite = new MetricDefinitionService.Definition(2, "简化毛利率近似", "margin_proxy", "composite", 9L,
                null, List.of(), List.of(), null, null,
                List.of("gmv_total", "order_cnt"), "(gmv_total - order_cnt) / gmv_total",
                "演示复合标量", "全局", "比率", "Asia/Shanghai", "alice");
        var cCreate = service.create(new MetricDefinitionService.SaveRequest(null, composite));
        Long cId = ((Number) cCreate.get("id")).longValue();
        service.publish(cId, 0);

        var cData = service.dataByCode("margin_proxy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cRows = (List<Map<String, Object>>) cData.get("rows");
        assertEquals(1, cRows.size());
        assertEquals(0.99, ((Number) cRows.get(0).get("value")).doubleValue(), 0.0001);
        assertTrue(cData.containsKey("composite"));

        // Composite rejects runtime dimensions
        assertThrows(IllegalArgumentException.class, () -> service.dataByCode("margin_proxy",
                new MetricDefinitionService.MetricQueryRequest(List.of(dim), List.of(), null, null)));

        // Consumer context exposes allowlist
        var ctx = service.consumerContext(gmvId);
        assertEquals("published", ctx.get("publishStatus"));
        assertEquals(List.of("city", "channel"), ctx.get("allowedDimensions"));
    }
}
