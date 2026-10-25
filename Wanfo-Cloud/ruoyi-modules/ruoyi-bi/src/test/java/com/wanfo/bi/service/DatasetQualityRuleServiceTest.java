package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetQualityRuleServiceTest {
    @Test void rulesAreScopedToDatasetFieldsAndReportSampleViolations(){
        var rules=mock(BiDatasetQualityRuleMapper.class);var fields=mock(BiDatasetFieldMapper.class);var datasets=mock(BiDatasetTableMapper.class);var resources=mock(BiResourceAuthorizer.class);var caps=mock(CapabilityProvider.class);var dataset=new BiDatasetTableDO();dataset.setGroupId(0L);when(datasets.selectById(9L)).thenReturn(dataset);
        var field=new BiDatasetFieldDO();field.setOriginName("email");when(fields.selectByDatasetId(9L)).thenReturn(List.of(field));when(rules.get(9L)).thenReturn("[{\"field\":\"email\",\"required\":true,\"unique\":true,\"maxNullRate\":0.1}]");
        var service=new DatasetQualityRuleService(rules,fields,datasets,resources,caps,new ObjectMapper());var report=new LinkedHashMap<String,Object>();report.put("sampledRows",10);report.put("columns",List.of(Map.of("field","email","nullCount",2,"duplicateValueCount",1,"nullRate",0.2)));
        var evaluated=service.evaluate(9L,report);assertEquals(3,evaluated.get("ruleFailureCount"));assertEquals("failed",evaluated.get("qualityStatus"));
        assertThrows(IllegalArgumentException.class,()->service.save(9L,List.of(new DatasetQualityRuleService.Rule("unknown",true,false,null)),"alice"));verify(rules,never()).save(anyLong(),anyString(),anyString());
    }
}
