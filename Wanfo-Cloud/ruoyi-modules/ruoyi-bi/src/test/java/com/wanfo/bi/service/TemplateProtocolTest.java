package com.wanfo.bi.service;

import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateProtocolTest {
    Map<String,Object> root(){return ScreenTemplateServiceTest.layout();}
    @SuppressWarnings("unchecked") Map<String,Object> panel(Map<String,Object> root,int index){return (Map<String,Object>)((List<?>)root.get("panels")).get(index);}
    @Test void staticTextCanContainBindingWordsWithoutFalsePositive(){var r=root();panel(r,0).put("content",Map.of("text","password chartId datasetId 是文档文字"));assertDoesNotThrow(()->ScreenTemplateService.validate(r));}
    @Test void duplicateOutOfBoundsUnknownAndWrongTypesRejected(){
        for(String defect:List.of("duplicate","bounds","unknown","type","binding","fraction")){
            var r=root();var p=panel(r,1);
            switch(defect){case "duplicate"->p.put("key",panel(r,0).get("key"));case "bounds"->p.put("x",8192);case "unknown"->p.put("callback","alert(1)");case "type"->p.put("componentType","script");case "binding"->p.put("chartId",1);case "fraction"->p.put("w",20.2);}
            assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(r),defect);
        }
    }
    @Test void missingParentsAndSingletonGroupsRejected(){for(String relation:List.of("parentKey","groupKey")){var r=root();panel(r,1).put(relation,"missing");assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(r));}}
    @Test void gridOverlapRejected(){var r=ScreenTemplateServiceTest.builtin("market-dashboard-retail");var a=panel(r,0);var b=panel(r,1);b.put("x",a.get("x"));b.put("y",a.get("y"));assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(r));}
    @Test void boundedReadStopsAfterLimitPlusOne()throws Exception{
        int[] read={0};var infinite=new InputStream(){@Override public int read(){read[0]++;return 1;}};
        assertThrows(com.wanfo.bi.security.BiAccessException.class,()->TemplateMarketService.readBounded(infinite,100));assertEquals(101,read[0]);
        assertArrayEquals(new byte[100],TemplateMarketService.readBounded(new ByteArrayInputStream(new byte[100]),100));
    }
}
