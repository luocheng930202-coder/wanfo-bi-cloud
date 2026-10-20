package com.wanfo.bi.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardMetricStyleTest {
 DashboardPresentation.MetricStyle m(String key,String op,String value,String color){return new DashboardPresentation.MetricStyle(key,null,List.of(new DashboardPresentation.Threshold(op,value,color)));}
 @Test void oldStyleHasEmptyMetrics(){assertTrue(DashboardPresentation.normalize(new DashboardPresentation.PanelStyle(true,"left")).metrics().isEmpty());}
 @Test void rulesNormalizedAndKeyValidated(){var values=DashboardPresentation.normalizeMetrics(List.of(m("sales","GTE","10000","#abcdef")));assertEquals("#ABCDEF",values.get(0).rules().get(0).color());DashboardPresentation.validateMetricKeys(new DashboardPresentation.PanelStyle(true,"left",null,values),Set.of("sales"));}
 @Test void unicodeAliasesSupported(){assertEquals("销售额",DashboardPresentation.normalizeMetrics(List.of(m("销售额","GT","1","#123456"))).get(0).key());}
 @Test void staleOutputDenied(){var style=new DashboardPresentation.PanelStyle(true,"left",null,List.of(m("old","GT","1","#123456")));assertThrows(IllegalArgumentException.class,()->DashboardPresentation.validateMetricKeys(style,Set.of("new")));}
 @Test void duplicateKeysDenied(){assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalizeMetrics(List.of(m("a","GT","1","#123456"),m("a","GT","1","#123456"))));}
 @Test void invalidRulesDenied(){for(var m:List.of(m("a","SQL","1","#123456"),m("a","GT","NaN","#123456"),m("a","GT","1","url(bad)")))assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalizeMetrics(List.of(m)));}
 @Test void ruleCountBounded(){var rule=new DashboardPresentation.Threshold("GT","1","#123456");assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalizeMetrics(List.of(new DashboardPresentation.MetricStyle("a",null,Collections.nCopies(9,rule)))));}
}
