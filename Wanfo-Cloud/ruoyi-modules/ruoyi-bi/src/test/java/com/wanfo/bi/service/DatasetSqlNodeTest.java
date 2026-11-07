package com.wanfo.bi.service;

import java.sql.*;
import java.util.*;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.query.CalciteQueryEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DatasetSqlNodeTest {
    @Test void onlyOneReadOnlyQueryIsAccepted() throws Exception {
        assertTrue(DatasetSqlNodeService.normalize("WITH a AS (SELECT 1 AS n) SELECT n FROM a ORDER BY n;").contains("SELECT"));
        for(String sql:List.of("DELETE FROM orders","UPDATE orders SET amount=0","CREATE TABLE bad(id INT)","SELECT 1; DELETE FROM orders"))
            assertThrows(Exception.class,()->DatasetSqlNodeService.normalize(sql),sql);
    }
    @Test void emptyResultsStillResolveTypesAndFieldNamesFromMetadata() throws Exception {
        var engine=mock(CalciteQueryEngine.class);var catalog=mock(CatalogService.class);var source=new BiDatasource();source.setId(1L);source.setSchemaName("demo");
        when(engine.open(eq(List.of(source)))).thenAnswer(i->DriverManager.getConnection("jdbc:h2:mem:node_metadata"));
        var service=new DatasetSqlNodeService(engine,catalog);
        var result=service.inspect(source,"SELECT CAST(n AS DECIMAL(12,2)) AS \"AMOUNT\" FROM (VALUES (1)) AS v(n) WHERE n=0",null,null,null,100);
        assertEquals(List.of("AMOUNT"),result.get("columns"));assertEquals(List.of(),result.get("rows"));
        var fields=(List<?>)result.get("fieldMetadata");assertEquals("metric",((Map<?,?>)fields.get(0)).get("suggestAs"));
        verify(catalog).requireModel("demo");verify(engine).open(eq(List.of(source)));
    }
    @Test void sqlNodeExecutesAgainstOnlyItsSelectedCalciteConnector() throws Exception {
        String url="jdbc:h2:mem:sql_node_actual;DB_CLOSE_DELAY=-1";
        try(Connection connection=DriverManager.getConnection(url);Statement statement=connection.createStatement()){
            statement.execute("CREATE TABLE IF NOT EXISTS orders(id INT, amount DECIMAL(10,2))");statement.execute("DELETE FROM orders");statement.execute("INSERT INTO orders VALUES (1,12.50)");
        }
        BiDatasource source=new BiDatasource();source.setId(1L);source.setType("h2");source.setSchemaName("demo");source.setJdbcUrl(url);source.setDriverClass("org.h2.Driver");source.setUsername("");source.setPassword("");
        var engine=new CalciteQueryEngine();
        try{
            var service=new DatasetSqlNodeService(engine,mock(CatalogService.class));
            var result=service.inspect(source,"SELECT id AS id, amount AS amount FROM orders",null,null,null,100);
            assertEquals(1,((List<?>)result.get("rows")).size());assertEquals(List.of("id","amount"),result.get("columns"));
            var model=new com.wanfo.bi.domain.dataset.DatasetJoinModel();
            model.setDerivedFields(List.of(
                new com.wanfo.bi.domain.dataset.DatasetDerivedField("calc","double_amount","双倍金额","0","[amount] * 2",null,null,null),
                new com.wanfo.bi.domain.dataset.DatasetDerivedField("group","amount_band","金额分类","1",null,"double_amount",List.of(new com.wanfo.bi.domain.dataset.DatasetDerivedField.Group("低值",List.of("25"))),"其他")
            ));
            var derived=service.inspect(source,"SELECT id AS id, amount AS amount FROM orders",null,null,model,100);
            var first=(Map<?,?>)((List<?>)derived.get("rows")).get(0);assertEquals("低值",first.get("amount_band"));assertTrue(((List<?>)derived.get("columns")).contains("double_amount"));

            model.setSqlParameters(List.of(new com.wanfo.bi.domain.dataset.SqlParameterDefinition("P","ID","NUMBER",false,false,List.of("1"),"ALL")));
            String template="SELECT id AS id, amount AS amount FROM orders WHERE $DE_PARAM{id=$[P]}";
            var filtered=service.inspect(source,template,null,null,model,100,Map.of("P",List.of("2")),true);
            assertTrue(((List<?>)filtered.get("rows")).isEmpty());assertEquals(template,filtered.get("normalizedSql"));
            var textModel=new com.wanfo.bi.domain.dataset.DatasetJoinModel();textModel.setSqlParameters(List.of(new com.wanfo.bi.domain.dataset.SqlParameterDefinition("P","Name","TEXT",false,false,List.of("alice-extra"),"ALL")));
            var substringResult=service.inspect(source,"SELECT * FROM (SELECT 'alice' AS name UNION ALL SELECT 'bob' AS name) t WHERE $DE_PARAM{name=substring('$[P]',1,5)}",null,null,textModel,100,Map.of(),true);
            assertEquals(1,((List<?>)substringResult.get("rows")).size());
            var defaultResult=service.inspect(source,template,null,null,model,100,Map.of(),false);
            assertEquals(1,((List<?>)defaultResult.get("rows")).size());
            assertThrows(Exception.class,()->service.inspect(source,"SELECT * FROM other.orders",null,null,null,100));
        }finally{engine.invalidate("demo");}
    }
    @Test void sqlNodePhysicalJoinAndSavedPreviewHonorDatasetPolicies() throws Exception {
        String url="jdbc:h2:mem:sql_node_join;DB_CLOSE_DELAY=-1";
        try(Connection connection=DriverManager.getConnection(url);Statement statement=connection.createStatement()){
            statement.execute("CREATE TABLE orders(school_id INT,amount DECIMAL(10,2))");statement.execute("CREATE TABLE schools(id INT,name VARCHAR(30))");
            statement.execute("INSERT INTO orders VALUES (1,12.50),(2,30.00)");statement.execute("INSERT INTO schools VALUES (1,'School A'),(2,'School B')");
        }
        var source=new BiDatasource();source.setId(1L);source.setStatus("0");source.setType("h2");source.setSchemaName("demo");source.setSourceSchema("PUBLIC");source.setJdbcUrl(url);source.setDriverClass("org.h2.Driver");source.setUsername("");source.setPassword("");
        var provider=mock(com.wanfo.bi.datasource.DataSourceProvider.class);when(provider.getBySchemaName("demo")).thenReturn(source);when(provider.getById(1L)).thenReturn(source);
        var resources=mock(com.wanfo.bi.security.BiResourceAuthorizer.class);var caps=mock(com.wanfo.bi.security.CapabilityProvider.class);
        var engine=new CalciteQueryEngine();var catalog=new CatalogService(engine,provider,resources,caps,mock(ClassificationService.class),mock(CatalogAuditService.class));
        try{
            var model=new com.wanfo.bi.domain.dataset.DatasetJoinModel();var join=new com.wanfo.bi.domain.dataset.DatasetJoinModel.JoinStep();join.setJoinType("LEFT");join.setRightTable("SCHOOLS");join.setLeftKey("school_id");join.setRightKey("ID");join.setRightFields(List.of("NAME"));model.setJoins(List.of(join));
            var nodes=new DatasetSqlNodeService(engine,catalog);String sql="SELECT school_id AS school_id, amount AS amount FROM orders";
            var result=nodes.inspect(source,sql,"PUBLIC",List.of("school_id","amount"),model,100);assertEquals(2,((List<?>)result.get("rows")).size());assertTrue(((List<?>)result.get("columns")).contains("r_NAME"));
            model.setSourceSql(DatasetSqlNodeService.normalize(sql));
            String base=DatasetSqlNodeService.project(model.getSourceSql(),List.of("school_id","amount"));
            base=catalog.savedJoinStep("demo","PUBLIC",base,"SCHOOLS","LEFT","ID",List.of("NAME"),"school_id","r_",1).sql();
            var row=new com.wanfo.bi.domain.BiDatasetTableDO();row.setId(9L);row.setType("table");row.setDatasourceId(1L);row.setSourceCatalog("PUBLIC");row.setUnionSql(base);row.setModelJson(new tools.jackson.databind.ObjectMapper().writeValueAsString(model));
            var datasets=mock(com.wanfo.bi.mapper.BiDatasetTableMapper.class);when(datasets.selectById(9L)).thenReturn(row);
            var fields=mock(com.wanfo.bi.mapper.BiDatasetFieldMapper.class);var definitions=new ArrayList<com.wanfo.bi.domain.BiDatasetFieldDO>();
            for(String name:List.of("school_id","amount","r_NAME")){var field=new com.wanfo.bi.domain.BiDatasetFieldDO();field.setOriginName(name);field.setOriginType(name.equals("r_NAME")?"VARCHAR":"DECIMAL");definitions.add(field);}when(fields.selectByDatasetId(9L)).thenReturn(definitions);
            var policies=mock(com.wanfo.bi.security.DatasetDataPolicy.class);
            when(policies.resolve(9L)).thenReturn(new com.wanfo.bi.security.DatasetDataPolicy.Scope(List.of(new com.wanfo.bi.security.DatasetDataPolicy.Rule(1L,9L,1L,"school_id","EQ","1","tester")),Set.of("r_NAME"),Set.of()));
            var service=new DatasetService(mock(com.wanfo.bi.mapper.BiDatasetGroupMapper.class),datasets,fields,provider,engine,catalog,resources,caps,policies);
            var saved=service.preview(9L,100);assertEquals(1,((List<?>)saved.get("rows")).size());assertEquals(List.of("school_id","amount"),saved.get("columns"));
        }finally{engine.invalidate("demo");}
    }
    @Test void duplicateOutputNamesAndOutOfRangePreviewsAreRejected() throws Exception {
        var engine=mock(CalciteQueryEngine.class);var source=new BiDatasource();source.setSchemaName("demo");
        when(engine.open(anyList())).thenAnswer(i->DriverManager.getConnection("jdbc:h2:mem:node_duplicates"));
        var service=new DatasetSqlNodeService(engine,mock(CatalogService.class));
        assertThrows(Exception.class,()->service.inspect(source,"SELECT 1 AS x,2 AS x",null,null,null,10));
        assertThrows(IllegalArgumentException.class,()->service.inspect(source,"SELECT 1",null,null,null,101));
    }
}
