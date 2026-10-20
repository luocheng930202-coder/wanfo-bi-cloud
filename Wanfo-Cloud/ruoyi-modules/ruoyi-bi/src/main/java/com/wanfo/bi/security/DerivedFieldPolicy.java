package com.wanfo.bi.security;
import java.util.*;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.query.CalcFormulaCompiler;
/** A derived value must never reveal a denied or masked dependency. */
public final class DerivedFieldPolicy {
 private DerivedFieldPolicy(){}
 public static DatasetDataPolicy.Scope expand(List<BiDatasetFieldDO> fields,DatasetDataPolicy.Scope scope){
  Set<String> names=new HashSet<>();for(var f:fields)names.add(f.getOriginName());
  Map<String,Set<String>> dependencies=new LinkedHashMap<>();
  try{for(var f:fields)if("1".equals(f.getExtFlag()))dependencies.put(f.getOriginName(),CalcFormulaCompiler.references(f.getExtFormula(),names));}
  catch(IllegalArgumentException ex){throw new BiAccessException(403,"计算字段定义已失效，请联系管理员");}
  Set<String> denied=new HashSet<>(scope.denied()),blocked=new HashSet<>(denied);blocked.addAll(scope.masked());boolean changed;
  do{changed=false;for(var entry:dependencies.entrySet())if(!Collections.disjoint(entry.getValue(),blocked) && blocked.add(entry.getKey())){denied.add(entry.getKey());changed=true;}}while(changed);
  Set<String> masked=new HashSet<>(scope.masked());masked.removeAll(denied);
  return new DatasetDataPolicy.Scope(scope.rows(),Set.copyOf(denied),Set.copyOf(masked));
 }
}
