package com.wanfo.bi.query;

import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.regex.*;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.dataset.*;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;

/** Independent DE_PARAM-compatible compiler. Values never become SQL source text. */
public final class SqlTemplateParameters {
    private SqlTemplateParameters(){}
    private static final Pattern VARIABLE=Pattern.compile("\\$\\[([A-Za-z_][A-Za-z0-9_]{0,63})]");
    private static final String PREFIX="__WANFO_SQL_BLOCK_";
    public record Bound(String sql,List<Object> params){}
    private record Block(String marker,String body){}
    private record Scan(String skeleton,List<Block> blocks){}

    public static Bound forDataset(BiDatasetTableDO row,Map<String,List<String>> values,boolean edit) throws Exception {
        var model=row.getModelJson()==null || row.getModelJson().isBlank()?null:new tools.jackson.databind.ObjectMapper().readValue(row.getModelJson(),DatasetJoinModel.class);
        return compile(row.getUnionSql(),model==null?null:model.getSqlParameters(),values,edit);
    }
    public static Bound compile(String template,List<SqlParameterDefinition> definitions,Map<String,List<String>> values,boolean edit) {
        try{return compileInternal(template,definitions,values,edit);}catch(IllegalArgumentException error){throw error;}catch(Exception error){throw new IllegalArgumentException("SQL 参数模板语法无效",error);}
    }
    private static Bound compileInternal(String template,List<SqlParameterDefinition> definitions,Map<String,List<String>> values,boolean edit) throws Exception {
        if(template==null || template.length()>200000)throw new IllegalArgumentException("SQL 模板为空或过长");
        Scan scan=scan(template);
        if(scan.blocks().isEmpty())return new Bound(template,List.of());
        var skeleton=parse(scan.skeleton());validateLocation(skeleton,false,new HashSet<>(scan.blocks().stream().map(Block::marker).toList()));
        Map<String,SqlParameterDefinition> byName=new LinkedHashMap<>();
        if(definitions!=null)for(var definition:definitions){
            if(definition==null || definition.name()==null || !definition.name().matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || byName.putIfAbsent(definition.name(),definition)!=null)throw new IllegalArgumentException("SQL 参数名称无效或重复");
            if(definition.type()==null || definition.defaultScope()==null || !Set.of("TEXT","NUMBER","DATE","DATETIME").contains(definition.type()) || !Set.of("EDIT","ALL").contains(definition.defaultScope()))throw new IllegalArgumentException("SQL 参数类型或默认值作用范围无效");
        }
        Set<String> referenced=new LinkedHashSet<>();for(var block:scan.blocks()){var matcher=VARIABLE.matcher(block.body());while(matcher.find())referenced.add(matcher.group(1));}
        if(referenced.size()>50 || !byName.keySet().containsAll(referenced))throw new IllegalArgumentException("请解析并配置全部 SQL 参数（最多 50 个）");
        Map<String,List<String>> overrides=values==null?Map.of():values;
        if(!referenced.containsAll(overrides.keySet()))throw new IllegalArgumentException("包含未定义的 SQL 参数值");
        Map<String,List<Object>> resolved=new HashMap<>();
        for(String name:referenced){
            var definition=byName.get(name);
            List<String> raw=overrides.containsKey(name)?overrides.get(name):(edit || "ALL".equals(definition.defaultScope()))?definition.defaultValues():List.of();
            raw=raw==null?List.of():raw.stream().filter(v->v!=null && !v.isEmpty()).toList();
            if(raw.size()>100 || !definition.multiple() && raw.size()>1)throw new IllegalArgumentException("参数 "+name+" 的值数量无效");
            if(raw.isEmpty() && definition.required())throw new IllegalArgumentException("请填写必填 SQL 参数："+name);
            List<Object> typed=new ArrayList<>();for(String value:raw)typed.add(typedValue(definition,value));resolved.put(name,typed);
        }
        Map<String,List<Object>> shapeValues=new HashMap<>();resolved.forEach((name,list)->shapeValues.put(name,list.isEmpty()?List.of("0"):list));
        List<Object> shapeBindings=new ArrayList<>();Set<Integer> shapeMulti=new HashSet<>();String shape=scan.skeleton();
        for(var block:scan.blocks())shape=shape.replace(block.marker()+"()","("+render(block.body(),byName,shapeValues,shapeBindings,shapeMulti)+")");
        var shapeNode=parse(shape);validateLocation(shapeNode,false,Set.of());validateMulti(shapeNode,false,shapeMulti);
        List<Object> params=new ArrayList<>();Set<Integer> multiIndices=new HashSet<>();String sql=scan.skeleton();
        for(var block:scan.blocks()){
            var variables=VARIABLE.matcher(block.body());Set<String> names=new LinkedHashSet<>();while(variables.find())names.add(variables.group(1));
            if(names.isEmpty())throw new IllegalArgumentException("每个 $DE_PARAM 条件必须引用至少一个 $[参数名]");
            String replacement=names.stream().anyMatch(n->resolved.get(n).isEmpty())?"TRUE":render(block.body(),byName,resolved,params,multiIndices);
            sql=sql.replace(block.marker()+"()","("+replacement+")");
        }
        var node=parse(sql);validateLocation(node,false,Set.of());validateMulti(node,false,multiIndices);
        int[] count={0};node.accept(new org.apache.calcite.sql.util.SqlShuttle(){@Override public SqlNode visit(SqlDynamicParam parameter){count[0]++;return parameter;}});
        if(count[0]!=params.size())throw new IllegalArgumentException("SQL 中存在未配置的占位符");
        return new Bound(node.toSqlString(CalciteSqlDialect.DEFAULT).getSql(),List.copyOf(params));
    }
    private static SqlNode parse(String sql) throws Exception {
        var node=SqlParser.create(sql.trim().replaceFirst(";\\s*$",""),SqlParser.config().withLex(Lex.MYSQL_ANSI)).parseQuery();
        if(!node.isA(SqlKind.QUERY))throw new IllegalArgumentException("只允许单条只读查询");return node;
    }
    private static void validateLocation(SqlNode node,boolean where,Set<String> markers){
        if(node==null)return;
        if(node instanceof SqlDynamicParam && !where)throw new IllegalArgumentException("SQL 变量只能在 WHERE 条件中使用");
        if(node instanceof SqlCall call){
            if(markers.contains(call.getOperator().getName())){if(!where)throw new IllegalArgumentException("SQL 变量只能在 WHERE 条件中使用");return;}
            if(call instanceof SqlSelect select){for(SqlNode child:select.getOperandList())validateLocation(child,child!=null && child==select.getWhere(),markers);}
            else for(SqlNode child:call.getOperandList())validateLocation(child,where,markers);
        }else if(node instanceof SqlNodeList list)for(SqlNode child:list)validateLocation(child,where,markers);
    }
    private static void validateMulti(SqlNode node,boolean inList,Set<Integer> indices){
        if(node==null)return;
        if(node instanceof SqlDynamicParam value){if(indices.contains(value.getIndex()) && !inList)throw new IllegalArgumentException("多值参数只能用于 IN / NOT IN 的值列表");}
        else if(node instanceof SqlCall call){for(int i=0;i<call.getOperandList().size();i++)validateMulti(call.getOperandList().get(i),((call.getKind()==SqlKind.IN || call.getKind()==SqlKind.NOT_IN) && i==1 && call.getOperandList().get(i) instanceof SqlNodeList) || (inList && call.getKind()==SqlKind.CAST && i==0),indices);}
        else if(node instanceof SqlNodeList list)for(SqlNode child:list)validateMulti(child,inList,indices);
    }
    private static Object typedValue(SqlParameterDefinition definition,String value){
        if(value.length()>4000)throw new IllegalArgumentException("SQL 参数值过长："+definition.name());
        try{return switch(definition.type()){
            case "TEXT"->value;
            case "NUMBER"->{var number=new BigDecimal(value);if(number.precision()>100 || Math.abs((long)number.scale())>100)throw new IllegalArgumentException();yield number;}
            case "DATE"->java.sql.Date.valueOf(LocalDate.parse(value));
            case "DATETIME"->java.sql.Timestamp.valueOf(LocalDateTime.parse(value.replace(' ','T')));
            default->throw new IllegalArgumentException();
        };}catch(Exception error){throw new IllegalArgumentException("SQL 参数类型不匹配："+definition.name());}
    }
    private static String render(String body,Map<String,SqlParameterDefinition> definitions,Map<String,List<Object>> values,List<Object> bindings,Set<Integer> multi){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<body.length();){
            char c=body.charAt(i);
            if(c=='\''){
                int end=quotedEnd(body,i,c);String literal=body.substring(i+1,end-1).replace("''","'");var matcher=VARIABLE.matcher(literal);
                if(matcher.find()){
                    StringBuilder value=new StringBuilder();int last=0;
                    do{String name=matcher.group(1);if(definitions.get(name).multiple())throw new IllegalArgumentException("带引号的 SQL 字符串只支持单值参数");value.append(literal,last,matcher.start()).append(values.get(name).get(0));last=matcher.end();}while(matcher.find());
                    value.append(literal.substring(last));addBinding(bindings,value.toString());out.append("CAST(? AS VARCHAR(4000))");
                }else out.append(body,i,end);
                i=end;
            }else if(body.startsWith("$[",i)){
                var matcher=VARIABLE.matcher(body);matcher.region(i,body.length());if(!matcher.lookingAt())throw new IllegalArgumentException("SQL 参数名称无效");
                String name=matcher.group(1);var list=values.get(name);for(int n=0;n<list.size();n++){if(n>0)out.append(',');if(definitions.get(name).multiple())multi.add(bindings.size());addBinding(bindings,list.get(n));out.append(placeholder(definitions.get(name),list.get(n)));}i=matcher.end();
            }else if(c=='"' || c=='`'){
                int end=quotedEnd(body,i,c);String identifier=body.substring(i+1,end-1);if(identifier.contains("$["))throw new IllegalArgumentException("参数不能作为字段或表名");out.append(c=='`'?"\""+identifier.replace("``","`").replace("\"","\"\"")+"\"":body.substring(i,end));i=end;
            }else if(body.startsWith("--",i) || body.startsWith("/*",i))throw new IllegalArgumentException("参数条件内不支持注释");
            else {out.append(c);i++;}
        }
        return out.toString();
    }
    private static String placeholder(SqlParameterDefinition definition,Object value){
        String type=switch(definition.type()){
            case "TEXT"->"VARCHAR(4000)";
            case "DATE"->"DATE";
            case "DATETIME"->"TIMESTAMP";
            case "NUMBER"->{var number=value instanceof BigDecimal decimal?decimal:BigDecimal.ZERO;int scale=Math.max(0,number.scale());int precision=Math.max(Math.max(1,scale),number.precision()+Math.max(0,-number.scale()));yield "DECIMAL("+precision+","+scale+")";}
            default->throw new IllegalArgumentException("SQL 参数类型无效");
        };
        return "CAST(? AS "+type+")";
    }
    private static void addBinding(List<Object> bindings,Object value){if(bindings.size()>=2000)throw new IllegalArgumentException("SQL 参数展开后最多 2000 个绑定值");bindings.add(value);}
    private static Scan scan(String sql){
        if(sql.contains(PREFIX))throw new IllegalArgumentException("SQL 使用了保留的内部标识");
        StringBuilder skeleton=new StringBuilder();List<Block> blocks=new ArrayList<>();
        for(int i=0;i<sql.length();){
            if(sql.startsWith("--",i)){int end=sql.indexOf('\n',i);if(end<0)end=sql.length();skeleton.append(sql,i,end);i=end;}
            else if(sql.startsWith("/*",i)){int end=sql.indexOf("*/",i+2);if(end<0)throw new IllegalArgumentException("SQL 注释未闭合");skeleton.append(sql,i,end+2);i=end+2;}
            else if(sql.startsWith("$DE_PARAM{",i)){
                int start=i+10,end=start;for(;end<sql.length();end++){
                    char c=sql.charAt(end);if(c=='\'' || c=='"' || c=='`'){end=quotedEnd(sql,end,c)-1;continue;}
                    if(c=='{')throw new IllegalArgumentException("SQL 参数条件不能嵌套");if(c=='}')break;
                }
                if(end>=sql.length() || blocks.size()>=50)throw new IllegalArgumentException("SQL 参数条件未闭合或数量超过 50");
                String body=sql.substring(start,end);var matcher=VARIABLE.matcher(body);String scrubbed=matcher.replaceAll("");if(scrubbed.contains("$["))throw new IllegalArgumentException("SQL 参数名称不能为空或包含非法字符");
                String marker=PREFIX+blocks.size();blocks.add(new Block(marker,body));skeleton.append(marker).append("()");i=end+1;
            }else if(sql.charAt(i)=='\'' || sql.charAt(i)=='"' || sql.charAt(i)=='`'){
                char c=sql.charAt(i);int end=quotedEnd(sql,i,c);String token=sql.substring(i,end);if(token.contains("$["))throw new IllegalArgumentException("SQL 参数必须置于 WHERE 内的 $DE_PARAM{...} 条件中");skeleton.append(c=='`'?"\""+token.substring(1,token.length()-1).replace("``","`").replace("\"","\"\"")+"\"":token);i=end;
            }else if(sql.startsWith("$[",i) || sql.startsWith("$DE_PARAM",i))throw new IllegalArgumentException("SQL 参数必须使用 $DE_PARAM{...} 包裹");
            else{skeleton.append(sql.charAt(i++));}
        }
        return new Scan(skeleton.toString(),blocks);
    }
    private static int quotedEnd(String text,int start,char quote){
        for(int i=start+1;i<text.length();i++)if(text.charAt(i)==quote){if(i+1<text.length() && text.charAt(i+1)==quote)i++;else return i+1;}
        throw new IllegalArgumentException("SQL 字符串或标识符未闭合");
    }
}
