package com.wanfo.bi.service;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.ChartDefinitionService.Definition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldDisplayLabelsTest
{
    @Test void catalogShowNamePrefersComment()
    {
        assertEquals("金额", FieldDisplayLabels.catalogShowName(Map.of("name", "amount", "comment", "金额")));
        assertEquals("qty", FieldDisplayLabels.catalogShowName(Map.of("name", "qty", "comment", "  ")));
    }

    @Test void chartColumnLabelsUseShowName()
    {
        BiDatasetFieldDO f = new BiDatasetFieldDO();
        f.setOriginName("amount");
        f.setShowName("销售金额");
        QueryModel q = new QueryModel();
        q.addDimension("province", null);
        q.addMetric("amount", "SUM", null);
        Definition chart = new Definition(1, "t", "bar", q);
        Map<String, String> labels = FieldDisplayLabels.forChartResult(chart, List.of(f), List.of("province", "sum_amount"));
        assertEquals("province", labels.get("province"));
        assertEquals("销售金额 · 求和", labels.get("sum_amount"));
    }
}
