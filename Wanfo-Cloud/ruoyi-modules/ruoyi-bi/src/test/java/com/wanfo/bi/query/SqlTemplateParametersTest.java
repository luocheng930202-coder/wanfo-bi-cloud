package com.wanfo.bi.query;
import java.util.*;
import java.sql.*;
import com.wanfo.bi.domain.dataset.SqlParameterDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SqlTemplateParametersTest {
 private SqlParameterDefinition parameter(String name,String type,boolean multiple,boolean required,List<String> defaults,String scope){return new SqlParameterDefinition(name,name,type,multiple,required,defaults,scope);}
 private SqlParameterDefinition text(String name,boolean multiple,List<String> defaults){return parameter(name,"TEXT",multiple,false,defaults,"ALL");}
 @Test void quotedSubstringAndUnquotedInUsePreparedBindings() throws Exception {
  String sql="select * from (values ('alice',1),('bob',2)) as t(name,id) where $DE_PARAM{ name = substring('$[PARAM1]',1,5)} and $DE_PARAM{ name in ($[PARAM2]) }";
  var definitions=List.of(text("PARAM1",false,List.of("alice-extra")),text("PARAM2",true,List.of("alice","bob")));
  var bound=SqlTemplateParameters.compile(sql,definitions,Map.of(),true);
  assertEquals(List.of("alice-extra","alice","bob"),bound.params());assertFalse(bound.sql().contains("alice-extra"));
  try(Connection connection=DriverManager.getConnection("jdbc:h2:mem:parameters");PreparedStatement statement=connection.prepareStatement(bound.sql())){
   for(int i=0;i<bound.params().size();i++)statement.setObject(i+1,bound.params().get(i));try(ResultSet result=statement.executeQuery()){assertTrue(result.next());assertEquals("alice",result.getString(1));assertFalse(result.next());}
  }
 }
 @Test void valuesWithQuotesAndSqlTextCannotBecomeExecutableSql() throws Exception {
  String value="alice') OR 1=1 --";var bound=SqlTemplateParameters.compile("SELECT * FROM (VALUES ('alice')) AS t(name) WHERE $DE_PARAM{name=$[P]}",List.of(text("P",false,List.of())),Map.of("P",List.of(value)),false);
  assertEquals(List.of(value),bound.params());assertFalse(bound.sql().contains(value));
  try(Connection connection=DriverManager.getConnection("jdbc:h2:mem:injection");PreparedStatement statement=connection.prepareStatement(bound.sql())){statement.setString(1,value);try(ResultSet result=statement.executeQuery()){assertFalse(result.next());}}
 }
 @Test void parametersAreForbiddenOutsideEachSelectsWhereEvenWhenOptionalIsEmpty(){
  var definitions=List.of(text("P",false,List.of()));
  for(String sql:List.of("SELECT $DE_PARAM{1=$[P]} FROM t","SELECT * FROM t HAVING $DE_PARAM{1=$[P]}","SELECT * FROM t ORDER BY $DE_PARAM{1=$[P]}","SELECT * FROM a JOIN b ON $DE_PARAM{a.id=$[P]}","SELECT * FROM t WHERE $DE_PARAM{EXISTS(SELECT $[P] FROM other)}","SELECT * FROM t WHERE name='$[P]'"))
   assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile(sql,definitions,Map.of(),false),sql);
 }
 @Test void optionalMissingValueRemovesWholeBlockWithoutLeavingUnusedBindings(){
  var result=SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{id=$[A] AND name='$[B]'}",List.of(text("A",false,List.of("1")),text("B",false,List.of())),Map.of(),false);
  assertTrue(result.sql().contains("TRUE"));assertTrue(result.params().isEmpty());
 }
 @Test void requiredParametersAndDefaultScopesAreEnforced(){
  var definition=parameter("P","NUMBER",false,false,List.of("5"),"EDIT");String sql="SELECT * FROM t WHERE $DE_PARAM{id=$[P]}";
  assertEquals(1,SqlTemplateParameters.compile(sql,List.of(definition),Map.of(),true).params().size());assertTrue(SqlTemplateParameters.compile(sql,List.of(definition),Map.of(),false).params().isEmpty());
  var required=parameter("P","TEXT",false,true,List.of(),"ALL");assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile(sql,List.of(required),Map.of(),true));
 }
 @Test void multiValuesOnlyBelongInInLists(){
  var parameter=text("P",true,List.of("a","b"));
  assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{name=substring($[P],1,5)}",List.of(parameter),Map.of(),true));
  assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{name='$[P]'}",List.of(parameter),Map.of(),true));
  assertEquals(2,SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{name NOT IN ($[P])}",List.of(parameter),Map.of(),true).params().size());
 }
 @Test void quotedPatternsAndRepeatedParametersKeepTextAndBindingOrder(){
  var result=SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{name LIKE '%$[P]%'} AND $DE_PARAM{name='$[P]'}",List.of(text("P",false,List.of("O'Reilly"))),Map.of(),true);
  assertEquals(List.of("%O'Reilly%","O'Reilly"),result.params());
 }
 @Test void commentsQuotedBracesAndNestedWhereAreHandled(){
  var result=SqlTemplateParameters.compile("SELECT * FROM t WHERE EXISTS(SELECT 1 FROM x WHERE $DE_PARAM{name='a}b' AND id=$[P]}) -- $DE_PARAM{ignored}",List.of(parameter("P","NUMBER",false,false,List.of("1"),"ALL")),Map.of(),true);
  assertEquals(1,result.params().size());assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{id=$[]}",List.of(),Map.of(),true));
 }
 @Test void typedDatesAndInvalidNumbersAreChecked(){
  var date=parameter("P","DATE",false,false,List.of("2026-11-05"),"ALL");assertInstanceOf(java.sql.Date.class,SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{event_date=$[P]}",List.of(date),Map.of(),true).params().get(0));
  assertThrows(IllegalArgumentException.class,()->SqlTemplateParameters.compile("SELECT * FROM t WHERE $DE_PARAM{id=$[P]}",List.of(parameter("P","NUMBER",false,false,List.of("not-number"),"ALL")),Map.of(),true));
 }
}
