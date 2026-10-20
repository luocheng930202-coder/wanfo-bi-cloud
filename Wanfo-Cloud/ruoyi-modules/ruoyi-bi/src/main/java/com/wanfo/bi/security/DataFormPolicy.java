package com.wanfo.bi.security;
import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.query.FilterTreeCompiler;
/** Fixed row rules constrain new rows and historical record visibility. */
public final class DataFormPolicy {
 private DataFormPolicy(){}
 public static void requireWrite(Map<String,Object> values,Map<String,String> types,DatasetDataPolicy.Scope scope){
  if(values.keySet().stream().anyMatch(k->scope.denied().contains(k) || scope.masked().contains(k)))throw new BiAccessException(403,"包含不可写入的受限字段");
  if(!matches(values,types,scope))throw new BiAccessException(403,"填报值不符合当前数据范围，请检查受限字段");
 }
 public static boolean matches(Map<String,Object> row,Map<String,String> types,DatasetDataPolicy.Scope scope){
  for(var rule:scope.rows()){
   String field=rule.fieldName(),op=rule.ruleKind();if(!types.containsKey(field))throw new BiAccessException(403,"数据范围字段已失效");Object actual=row.get(field);
   if("IS_NULL".equals(op)){if(actual!=null)return false;continue;}if("IS_NOT_NULL".equals(op)){if(actual==null)return false;continue;}if(actual==null)return false;
   int cmp;try{cmp=FilterTreeCompiler.numeric(types.get(field))?new BigDecimal(actual.toString()).compareTo(new BigDecimal(rule.ruleValue())):actual.toString().compareTo(rule.ruleValue());}catch(Exception ex){throw new BiAccessException(403,"数据范围规则值无效");}
   boolean allowed=switch(op){case "EQ"->cmp==0;case "NE"->cmp!=0;case "GT"->cmp>0;case "GE"->cmp>=0;case "LT"->cmp<0;case "LE"->cmp<=0;default->throw new BiAccessException(403,"数据范围规则无效");};if(!allowed)return false;
  }return true;
 }
 public static Map<String,Object> visible(Map<String,Object> row,Map<String,String> types,DatasetDataPolicy.Scope scope){Map<String,Object> result=new LinkedHashMap<>();row.forEach((k,v)->{if(types.containsKey(k) && !scope.denied().contains(k))result.put(k,scope.masked().contains(k)?"***":v);});return result;}
}
