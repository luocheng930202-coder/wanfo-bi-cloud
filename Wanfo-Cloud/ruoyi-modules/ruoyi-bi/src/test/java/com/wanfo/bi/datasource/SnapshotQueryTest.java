package com.wanfo.bi.datasource;
import com.wanfo.bi.datasource.snapshot.*;
import com.wanfo.bi.mapper.BiSourceSnapshotMapper;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.service.CatalogService;
import com.wanfo.bi.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotQueryTest {
    @Test void snapshotFlowsThroughModellingCalciteAndFilteredAggregation()throws Exception {
        var data=SnapshotParser.csv("region,amount,date\n北京,10.25,2026-10-01\n上海,20.5,2026-10-02\n北京,2.25,2026-10-03".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var json=new ObjectMapper();var mapper=mock(BiSourceSnapshotMapper.class);
        when(mapper.get(7L)).thenReturn(new BiSourceSnapshotMapper.Row(7L,"csv",null,json.writeValueAsString(data.columns()),json.writeValueAsString(data.rows()),3,1,"sales.csv","now"));
        var registry=new SnapshotRegistry(mapper);var engine=new CalciteQueryEngine();engine.configureSnapshots(registry);
        var ds=new BiDatasource();ds.setId(7L);ds.setSchemaName("sales");ds.setType("csv");ds.setStatus("0");
        var provider=mock(DataSourceProvider.class);when(provider.getBySchemaName("sales")).thenReturn(ds);
        var service=new CatalogService(engine,provider,mock(BiResourceAuthorizer.class),mock(CapabilityProvider.class));service.configureSnapshots(registry);
        assertEquals(List.of("snapshot"),service.listCatalogs("sales"));assertEquals("data",service.listTables("sales","snapshot").get(0).get("name"));
        assertEquals(3,service.listColumns("sales","snapshot","data").size());
        assertThrows(IllegalArgumentException.class,()->service.plan("sales","snapshot","secret",List.of("amount")));
        assertEquals(2,service.preview("sales","snapshot","data",List.of("region","amount"),2).get("rowCount"));
        try(Connection c=engine.open(List.of(ds));PreparedStatement st=c.prepareStatement("SELECT SUM(\"amount\") FROM \"sales\".\"data\" WHERE \"region\"=? AND \"date\">=DATE '2026-10-01'")){
            st.setString(1,"北京");try(ResultSet rs=st.executeQuery()){assertTrue(rs.next());assertEquals(0,new java.math.BigDecimal("12.50").compareTo(rs.getBigDecimal(1)));}
        }
        assertEquals(Map.of("sales",true),engine.probeAll(List.of(ds)));
    }
}
