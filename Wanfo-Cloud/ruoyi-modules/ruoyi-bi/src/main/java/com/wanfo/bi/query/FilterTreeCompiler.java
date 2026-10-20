package com.wanfo.bi.query;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import com.wanfo.bi.domain.query.QueryModel;

/** Bounded structural filter AST. Identifiers come from the visible schema; every value is bound. */
public final class FilterTreeCompiler {
    private FilterTreeCompiler() { }
    public record Compiled(String sql,List<Object> params) { }
    public static Compiled compile(List<QueryModel.Filter> legacy,QueryModel.FilterNode tree,Map<String,String> types) {
        if(legacy==null) throw invalid("filters 不能为 null");
        State state=new State(types);List<String> terms=new ArrayList<>();
        if(legacy.size()>100) throw invalid("过滤条件不能超过 100 个节点");
        for(var leaf:legacy) {state.node(1);terms.add(state.leaf(leaf));}
        if(tree!=null) terms.add(state.tree(tree,1));
        return new Compiled(String.join(" AND ",terms),List.copyOf(state.params));
    }
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
    public static boolean numeric(String type) {return type!=null && type.toLowerCase(Locale.ROOT).matches(".*(int|decimal|numeric|float|double|real|number).*");}
    static String quote(String name) {return "\""+name.replace("\"","\"\"")+"\"";}
    private static class State {
        final Map<String,String> types;final List<Object> params=new ArrayList<>();int nodes;
        State(Map<String,String> types) {this.types=types;}
        void node(int depth) {if(depth>8 || ++nodes>100) throw invalid("过滤树最多 8 层、100 个节点");}
        String tree(QueryModel.FilterNode n,int depth) {
            node(depth);if(n==null) throw invalid("过滤节点不能为 null");
            if(n.getLogic()!=null || n.getChildren()!=null) {
                String logic=n.getLogic()==null?"":n.getLogic().toUpperCase(Locale.ROOT);
                if(!Set.of("AND","OR").contains(logic) || n.getChildren()==null || n.getChildren().isEmpty()
                        || n.getChildren().size()>100 || n.getField()!=null || n.getOp()!=null || n.getValue()!=null)
                    throw invalid("过滤组须指定 AND/OR 和非空 children，不能混入字段条件");
                List<String> parts=new ArrayList<>();for(var child:n.getChildren()) parts.add(tree(child,depth+1));
                return "("+String.join(" "+logic+" ",parts)+")";
            }
            return leaf(n);
        }
        String leaf(QueryModel.Filter f) {
            if(f==null || f.getField()==null || !types.containsKey(f.getField())) throw invalid("过滤字段不存在或无权访问");
            String op=f.getOp()==null?"EQ":f.getOp().toUpperCase(Locale.ROOT);
            if(!QueryModel.OP_WHITELIST.contains(op)) throw invalid("不支持的过滤操作符");
            String col=quote(f.getField());Object value=f.getValue();
            if(op.equals("IS_NULL") || op.equals("IS_NOT_NULL")) {
                if(value!=null) throw invalid("空值判断不接受固定值");
                return col+(op.equals("IS_NULL")?" IS NULL":" IS NOT NULL");
            }
            if(op.equals("IN") || op.equals("NOT_IN") || op.equals("BETWEEN")) {
                if(!(value instanceof List<?> values) || values.isEmpty() || values.size()>100
                        || (op.equals("BETWEEN") && values.size()!=2)) throw invalid("IN/NOT_IN 须为 1～100 项数组，BETWEEN 须为两项数组");
                for(Object v:values) bind(f.getField(),v,false);
                if(op.equals("BETWEEN")) return col+" BETWEEN ? AND ?";
                return col+" "+op.replace('_',' ')+" ("+values.stream().map(v->"?").collect(Collectors.joining(", "))+")";
            }
            boolean like=op.equals("LIKE") || op.equals("NOT_LIKE");
            bind(f.getField(),value,like);
            String symbol=switch(op) {case "EQ"->"=";case "NE"->"<>";case "GT"->">";case "GTE"->">=";case "LT"->"<";case "LTE"->"<=";default->op.replace('_',' ');};
            return col+" "+symbol+" ?";
        }
        void bind(String field,Object value,boolean like) {
            if(value==null || (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean))) throw invalid("过滤值须为非空标量；空值请使用 IS_NULL");
            if(value.toString().length()>1000 || params.size()>=1000) throw invalid("过滤值过长或参数超过 1000 项");
            String type=types.get(field);
            boolean text=type==null || type.isEmpty() || type.toLowerCase(Locale.ROOT).matches(".*(char|text|string|enum|set).*");
            if(like && (!(value instanceof String) || !text)) throw invalid("LIKE 仅支持文本字段和文本值");
            if(numeric(types.get(field))) {
                try {value=new BigDecimal(value.toString());}catch(NumberFormatException ex){throw invalid("数值字段的过滤值须为有效数字");}
            } else if(value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f)) throw invalid("过滤值须为有限数字");
            params.add(value);
        }
    }
}
