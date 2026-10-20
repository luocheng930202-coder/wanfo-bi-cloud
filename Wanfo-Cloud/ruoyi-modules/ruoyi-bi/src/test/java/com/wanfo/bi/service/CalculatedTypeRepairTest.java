package com.wanfo.bi.service;
import java.util.*;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.mapper.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class CalculatedTypeRepairTest {
    @Test void repairsOnlyLegacyDefinitionsAndLeavesInvalidFieldsForReview(){
        var datasets=mock(BiDatasetTableMapper.class);var fields=mock(BiDatasetFieldMapper.class);var ds=new BiDatasetTableDO();ds.setId(1L);when(datasets.selectAll(null)).thenReturn(List.of(ds));
        var physical=new BiDatasetFieldDO();physical.setOriginName("amount");physical.setOriginType("DECIMAL");physical.setExtFlag("0");
        var calc=new BiDatasetFieldDO();calc.setId(2L);calc.setExtFlag("1");calc.setOriginType("CALC");calc.setExtFormula("amount*2");
        var bad=new BiDatasetFieldDO();bad.setId(3L);bad.setExtFlag("1");bad.setOriginType("CALC");bad.setExtFormula("missing*2");
        when(fields.selectByDatasetId(1L)).thenReturn(List.of(physical,calc,bad));new CalculatedTypeRepair(datasets,fields).run(null);
        verify(fields).repairCalculatedType(eq(2L),eq("amount*2"),argThat(t->t.startsWith("DECIMAL")));verify(fields,never()).repairCalculatedType(eq(3L),anyString(),anyString());
    }
}
