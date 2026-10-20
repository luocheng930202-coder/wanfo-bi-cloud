package com.wanfo.bi.query;
import java.util.*;
import java.util.regex.*;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlShuttle;
/** Scalar expressions only. Both bracketed and bare references are resolved against the same field whitelist. */
public final class CalcFormulaCompiler {
 private static final Pattern REF=Pattern.compile("'(?:''|[^'])*'|\\[([A-Za-z_][A-Za-z0-9_]*)\\]");
 private static final Set<String> OPS=Set.of("+","-","*","/","%","MOD","ABS","ROUND","CEIL","CEILING","FLOOR","COALESCE","NULLIF","POWER","SQRT","UPPER","LOWER","CONCAT","LENGTH","CHAR_LENGTH","SUBSTRING","TRIM","CASE","=","<>",">","<",">=","<=","AND","OR","NOT","IS NULL","IS NOT NULL");
 private CalcFormulaCompiler(){}
 public static String compile(String formula,Set<String> allowed){return compile(formula,allowed,"\"");}
 public static String compile(String formula,Set<String> allowed,String quote){return parse(formula,allowed,new LinkedHashSet<>()).toSqlString(new SqlDialect(SqlDialect.EMPTY_CONTEXT.withIdentifierQuoteString(quote))).getSql();}
 public static Set<String> references(String formula,Set<String> allowed){Set<String> result=new LinkedHashSet<>();parse(formula,allowed,result);return Set.copyOf(result);}
 private static SqlNode parse(String formula,Set<String> allowed,Set<String> references){
  if(formula==null || formula.isBlank() || formula.length()>500 || formula.contains(";") || formula.contains("--") || formula.contains("/*"))throw new IllegalArgumentException("计算表达式无效");
  Map<String,String> names=new HashMap<>();for(String field:allowed)names.put(field.toLowerCase(Locale.ROOT),field);
  Matcher m=REF.matcher(formula);StringBuffer input=new StringBuffer();while(m.find())m.appendReplacement(input,Matcher.quoteReplacement(m.group(1)==null?m.group():FilterTreeCompiler.quote(m.group(1))));m.appendTail(input);
  try{return SqlParser.create(input.toString()).parseExpression().accept(new SqlShuttle(){
   @Override public SqlNode visit(SqlIdentifier id){String actual=id.isSimple()?names.get(id.getSimple().toLowerCase(Locale.ROOT)):null;if(actual==null)throw new IllegalArgumentException("字段不在数据集中或不可引用");references.add(actual);return new SqlIdentifier(actual,SqlParserPos.ZERO);}
   @Override public SqlNode visit(SqlCall call){if(!OPS.contains(call.getOperator().getName().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("不支持的计算函数或语句");return super.visit(call);}
  });}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("计算表达式语法无效");}
 }
}
