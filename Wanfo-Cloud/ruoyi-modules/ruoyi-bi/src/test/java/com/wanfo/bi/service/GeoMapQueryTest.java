package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.chart.ChartAdvancedOptions;
import com.wanfo.bi.domain.query.QueryModel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class GeoMapQueryTest {
    final Map<String,String> types=Map.of("province","VARCHAR","city","VARCHAR","county","VARCHAR","amount","DECIMAL");
    ChartDefinitionService.Definition definition(){var q=new QueryModel();q.setDatasetId(1L);q.addDimension("province","region");q.addMetric("amount","SUM","sales");var a=new ChartAdvancedOptions();a.setMapScope("china-provinces");a.setMapFields(new ChartAdvancedOptions.MapFields("province","city","county"));return new ChartDefinitionService.Definition(1,"地图","map",q,null,a);}
    Map<String,Object> input(Object... values){var list=new ArrayList<Map<String,Object>>();for(int i=0;i<values.length;i+=2)list.add(Map.of("field",values[i],"value",values[i+1]));return Map.of("drills",Map.of("chart",list));}
    @Test void completeCatalogHasConsistentParentsAndServesCountyOutline() throws Exception {
        assertTrue(GeoCatalog.catalog().regions().size()>3000);
        for(var r:GeoCatalog.catalog().regions())for(var c:GeoCatalog.children(r))assertEquals(r.code(),c.parent());
        assertEquals("city",GeoCatalog.displayLevel(GeoCatalog.region("330000")));
        assertEquals("district",GeoCatalog.displayLevel(GeoCatalog.region("330100")));
        var geometry=(Map<?,?>)GeoCatalog.geometry("330106");var features=(List<?>)geometry.get("features");assertEquals(1,features.size());
        assertThrows(IllegalArgumentException.class,()->GeoCatalog.region("../application"));
        assertThrows(IllegalArgumentException.class,()->GeoCatalog.region("999999"));
    }
    @Test void drillReaggregatesByChildFieldAndKeepsExistingPredicates() {
        var d=definition();d.query().addFilter("amount","GT",0);
        GeoMapQuery.validate(d,types);
        var path=GeoMapQuery.path(d,input("province","浙江省","city","杭州市"),"chart",types);
        var json=new ObjectMapper();var q=json.readValue(json.writeValueAsString(d.query()),QueryModel.class);
        GeoMapQuery.apply(d,q,path,types);
        assertEquals("county",q.getDimensions().get(0).getField());
        assertEquals("province",d.query().getDimensions().get(0).getField());
        assertTrue(q.getFilters().stream().anyMatch(f->"amount".equals(f.getField()) && "GT".equals(f.getOp())));
        assertTrue(q.getFilters().stream().anyMatch(f->"province".equals(f.getField()) && "浙江省".equals(f.getValue())));
        assertTrue(q.getFilters().stream().anyMatch(f->"city".equals(f.getField()) && "杭州市".equals(f.getValue())));
        assertEquals("330100",GeoMapQuery.context(d,path).get("scope"));
        assertEquals("330100",GeoMapQuery.presentation(d,path).advanced().getMapScope());
        assertEquals("china-provinces",d.advanced().getMapScope());
    }
    @Test void invalidAncestryFieldsAndExcessDepthCannotSelectArbitraryData() {
        var d=definition();
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.path(d,input("province","广东省","city","杭州市"),"chart",types));
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.path(d,input("secret","浙江省"),"chart",types));
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.path(d,input("province","浙江省","city","杭州市","county","西湖区"),"chart",types));
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.validate(d,Map.of("province","VARCHAR")));
    }
    @Test void municipalitiesSkipTheCityFieldAndMissingCountyBindingStopsDrill() {
        var d=definition();var path=GeoMapQuery.path(d,input("province","北京市"),"chart",types);
        var q=new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(d.query()),QueryModel.class);GeoMapQuery.apply(d,q,path,types);
        assertEquals("county",q.getDimensions().get(0).getField());
        d.advanced().setMapFields(new ChartAdvancedOptions.MapFields("province","city",null));
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.path(d,input("province","北京市"),"chart",types));
        assertDoesNotThrow(()->GeoMapQuery.path(d,input("province","浙江省"),"chart",types));
    }
    @Test void namedRegionalMapsRequireParentBindingsAndCodesAreUnambiguous() {
        var d=definition();d.advanced().setMapScope("330100");d.query().setDimensions(List.of(new QueryModel.Dimension("county","county")));
        d.advanced().setMapFields(new ChartAdvancedOptions.MapFields(null,null,"county"));
        assertThrows(IllegalArgumentException.class,()->GeoMapQuery.validate(d,types));
        d.advanced().setMapValueMode("code");assertDoesNotThrow(()->GeoMapQuery.validate(d,types));
        GeoMapQuery.apply(d,d.query(),List.of(),types);
        assertTrue(d.query().getFilters().stream().anyMatch(f->"county".equals(f.getField()) && "IN".equals(f.getOp())));
    }
    @Test void legacyProvinceMapRemainsValidButDoesNotAdvertiseUnboundDrill() {
        var d=definition();d.advanced().setMapFields(null);
        assertDoesNotThrow(()->GeoMapQuery.validate(d,types));assertFalse(GeoMapQuery.enabled(d));
        assertTrue(ChartDrill.fields(d).isEmpty());
    }
}
