package com.wanfo.bi.query;
import com.wanfo.bi.domain.query.QueryModel;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TopNQueryTest {
 private final Map<String,String> types=Map.of("category","VARCHAR","channel","VARCHAR","amount","INTEGER","buyer","VARCHAR");
 private final String base="SELECT * FROM (VALUES ('A','X',100,'same'),('B','X',10,'same'),('B','X',20,'same'),('C','Y',90,'same'),('D','X',1,'other')) AS t(\"category\",\"channel\",\"amount\",\"buyer\")";
 private QueryModel model(){var q=new QueryModel();q.setDatasetId(1L);q.addDimension("category","c");q.addMetric("amount","SUM","sales");q.addMetric("amount","AVG","mean");q.addMetric("buyer","COUNT_DISTINCT","buyers");q.setLimit(100);q.setTopN(new QueryModel.TopN(1,"sales",true));return q;}
 private TopNQuery.Result execute(QueryModel q,String source,List<Object> security)throws Exception{var compiled=new ChartQueryCompiler().compile(q,source,types);var params=new ArrayList<>(security);params.addAll(compiled.params);try(var c=new CalciteQueryEngine().open(List.of())){var r=new CalciteQueryEngine().queryWithParams(c,compiled.sql,params,10001);return TopNQuery.decode(q,r.getColumns(),r.getRows());}}
 @Test void otherReaggregatesFactsForAverageAndDistinct()throws Exception{var r=execute(model(),base,List.of());assertEquals(2,r.rows().size());var other=r.rows().get(((java.util.List<Integer>)r.metadata().get("otherRowIndexes")).get(0));assertEquals(121d/4,Double.parseDouble(other.get("mean").toString()),0.000001);assertEquals("2",other.get("buyers").toString());assertFalse(r.columns().contains(TopNQuery.OTHER));assertEquals(1,((List<?>)r.metadata().get("otherRowIndexes")).size());}
 @Test void rowPolicyAndUserFilterApplyBeforeRankingAndOnlyBindOnce()throws Exception{var q=model();q.addFilter("channel","EQ","X");var r=execute(q,"SELECT * FROM ("+base+") AS secured WHERE \"category\" <> ?",List.of("A"));assertTrue(r.rows().stream().anyMatch(x->"B".equals(x.get("c"))));}
 @Test void categorySeriesAndNullCategoryPreserveFullTotals()throws Exception{var q=model();q.addDimension("channel","s");var r=execute(q,base,List.of());assertEquals(3,r.rows().size());assertEquals(221d,r.rows().stream().mapToDouble(x->Double.parseDouble(x.get("sales").toString())).sum());}
 @Test void withoutOtherRetainsOnlyRankedCategories()throws Exception{var q=model();q.setTopN(new QueryModel.TopN(1,"sales",false));var r=execute(q,base,List.of());assertEquals(1,r.rows().size());assertEquals("A",r.rows().get(0).get("c"));}
 @Test void incompatibleModesAndOversizedCompleteResultsFail(){var q=model();q.setTimeComparison("year");assertThrows(IllegalArgumentException.class,()->new ChartQueryCompiler().compile(q,base,types));q.setTimeComparison(null);q.setLimit(1);assertThrows(IllegalArgumentException.class,()->execute(q,base,List.of()));q.setTopN(new QueryModel.TopN(101,"sales",true));assertThrows(IllegalArgumentException.class,()->new ChartQueryCompiler().compile(q,base,types));}

 @Test void jdbcAdapterExecutesRankingJoinWithoutCorrelatedWindowConventionFailure()throws Exception{
  String url="jdbc:h2:mem:topn"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
  try(var direct=java.sql.DriverManager.getConnection(url,"sa","");var statement=direct.createStatement()){
   statement.execute("CREATE TABLE facts(category VARCHAR(100),channel VARCHAR(100),amount INTEGER,buyer VARCHAR(100))");
   statement.execute("INSERT INTO facts VALUES ('A','X',100,'same'),('B','X',10,'same'),('B','X',20,'same'),('C','Y',90,'same'),('D','X',1,'other')");
   var ds=new com.wanfo.bi.datasource.BiDatasource();ds.setId(1L);ds.setSchemaName("fixture");ds.setJdbcUrl(url);ds.setDriverClass("org.h2.Driver");ds.setUsername("sa");ds.setPassword("");var engine=new CalciteQueryEngine();
   try(var connection=engine.open(List.of(ds))){
    var q=model();q.addDimension("channel","s");var plan=new ChartQueryCompiler().compile(q,"SELECT * FROM \"fixture\".\"facts\"",types);var data=engine.queryWithParams(connection,plan.sql,plan.params,10001);var r=TopNQuery.decode(q,data.getColumns(),data.getRows());assertEquals(3,r.rows().size());assertEquals(221d,r.rows().stream().mapToDouble(row->Double.parseDouble(row.get("sales").toString())).sum());
    q.addFilter("channel","EQ","X");plan=new ChartQueryCompiler().compile(q,"SELECT * FROM \"fixture\".\"facts\"",types);data=engine.queryWithParams(connection,plan.sql,plan.params,10001);r=TopNQuery.decode(q,data.getColumns(),data.getRows());assertEquals(131d,r.rows().stream().mapToDouble(row->Double.parseDouble(row.get("sales").toString())).sum());
    try(var insert=direct.prepareStatement("INSERT INTO facts VALUES (?,'X',?, 'shared')")){insert.setNull(1,java.sql.Types.VARCHAR);insert.setInt(2,200);insert.executeUpdate();insert.setString(1,"\u5176\u4ed6");insert.setInt(2,300);insert.executeUpdate();}
    q.setTopN(new QueryModel.TopN(2,"sales",true));plan=new ChartQueryCompiler().compile(q,"SELECT * FROM \"fixture\".\"facts\"",types);data=engine.queryWithParams(connection,plan.sql,plan.params,10001);r=TopNQuery.decode(q,data.getColumns(),data.getRows());assertEquals(3,r.rows().size());assertTrue(r.rows().stream().anyMatch(row->row.get("c")==null));assertEquals(631d,r.rows().stream().mapToDouble(row->Double.parseDouble(row.get("sales").toString())).sum());var otherRow=((List<Integer>)r.metadata().get("otherRowIndexes")).get(0);assertNotEquals("\u5176\u4ed6",r.rows().get(otherRow).get("c"));

   }finally{engine.shutdown();}
  }
 }
}
