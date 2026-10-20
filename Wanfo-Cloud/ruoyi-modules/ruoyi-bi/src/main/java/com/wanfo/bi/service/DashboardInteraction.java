package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;

/** Bounded, published bindings. Runtime clients supply values, never fields or query models. */
public final class DashboardInteraction {
    private DashboardInteraction() {}
    public record Target(String panel,String field,String valueType) { public Target(String panel,String field){this(panel,field,null);} }
    public record Control(String key,String label,String type,Object defaultValue,List<Target> targets) {}
    public record Link(String key,String sourcePanel,String sourceField,List<Target> targets) {}
    public record Settings(List<Control> controls,List<Link> links) {
        public Settings { controls=controls==null?List.of():List.copyOf(controls);links=links==null?List.of():List.copyOf(links); }
    }
    public static Settings empty(){return new Settings(null,null);}
    public static String family(String type) {
        String t=String.valueOf(type).toUpperCase(Locale.ROOT);
        if(t.matches(".*(INT|DECIMAL|NUMERIC|FLOAT|DOUBLE|REAL).*"))return "number";
        if(t.equals("DATE"))return "date";
        if(t.matches(".*(CHAR|TEXT|STRING).*"))return "text";
        throw new IllegalArgumentException("筛选暂不支持该字段类型");
    }
    static void key(String k){if(k==null || !k.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("交互标识无效");}
    public static Settings validate(Settings s,List<DashboardService.PublishedPanel> panels,Map<String,Map<String,String>> fields) {
        if(s.controls().size()>12 || s.links().size()>12)throw new IllegalArgumentException("最多 12 个筛选器和 12 条联动");
        Set<String> keys=new HashSet<>();
        for(var c:s.controls()) {
            if(c==null)throw new IllegalArgumentException("筛选器不能为空");
            key(c.key());if(!keys.add(c.key()) || c.label()==null || c.label().isBlank() || c.label().length()>80 || !Set.of("text","enum","number","date").contains(String.valueOf(c.type())))throw new IllegalArgumentException("筛选器标识、标题或类型无效");
            String f=targets(c.targets(),fields);
            if(!c.type().equals("enum") && !c.type().equals(f))throw new IllegalArgumentException("筛选器与绑定字段类型不匹配");
            value(c.type(),f,c.defaultValue());
        }
        for(var l:s.links()) {
            if(l==null)throw new IllegalArgumentException("联动不能为空");
            key(l.key());if(!keys.add(l.key()))throw new IllegalArgumentException("交互标识重复");
            var source=panels.stream().filter(p->p.layout().key().equals(l.sourcePanel())).findFirst().orElseThrow(()->new IllegalArgumentException("联动来源组件不存在"));
            if(source.chart()==null || (source.layout().componentType()!=null && !source.layout().componentType().equals("chart")) || source.chart().query().getDimensions().isEmpty() || !Objects.equals(source.chart().query().getDimensions().get(0).getField(),l.sourceField()))throw new IllegalArgumentException("联动来源须为图表第一维度");
            if(ChartDrill.enabled(source.chart()))throw new IllegalArgumentException("已启用下钻的图表不能作为联动来源");
            String f=field(fields,new Target(l.sourcePanel(),l.sourceField()));
            if(!f.equals(targets(l.targets(),fields)) || l.targets().stream().anyMatch(t->t.panel().equals(l.sourcePanel())))throw new IllegalArgumentException("联动目标类型不匹配或指向自身");
        }
        return new Settings(s.controls().stream().map(c->new Control(c.key(),c.label(),c.type(),value(c.type(),field(fields,c.targets().get(0)),c.defaultValue()),c.targets().stream().map(t->new Target(t.panel(),t.field(),field(fields,t))).toList())).toList(),
                s.links().stream().map(l->new Link(l.key(),l.sourcePanel(),l.sourceField(),l.targets().stream().map(t->new Target(t.panel(),t.field(),field(fields,t))).toList())).toList());
    }
    private static String field(Map<String,Map<String,String>> fields,Target t) {
        if(t==null || !fields.containsKey(t.panel()) || !fields.get(t.panel()).containsKey(t.field()))throw new IllegalArgumentException("绑定字段不存在或无查询权限");
        return family(fields.get(t.panel()).get(t.field()));
    }
    private static String targets(List<Target> targets,Map<String,Map<String,String>> fields) {
        if(targets==null || targets.isEmpty() || targets.size()>20)throw new IllegalArgumentException("请配置 1～20 个目标组件");
        Set<String> seen=new HashSet<>();String type=null;
        for(var t:targets){String f=field(fields,t);if(!seen.add(t.panel()))throw new IllegalArgumentException("同一控件不能重复绑定目标组件");if(type!=null && !type.equals(f))throw new IllegalArgumentException("目标字段类型须一致");type=f;}
        return type;
    }
    private static Object scalar(String family,Object raw) {
        if(raw==null)return null;
        if(raw instanceof Map || raw instanceof List || raw instanceof Boolean || String.valueOf(raw).length()>256)throw new IllegalArgumentException("筛选值类型或长度无效");
        try {
            if(family.equals("number")){var n=new BigDecimal(String.valueOf(raw));if(n.precision()>30 || Math.abs(n.scale())>12)throw new IllegalArgumentException();return n;}
            if(!(raw instanceof String))throw new IllegalArgumentException();
            if(family.equals("date"))return LocalDate.parse((String)raw).toString();
            return raw;
        }catch(RuntimeException ex){throw new IllegalArgumentException("筛选值与绑定字段类型不匹配");}
    }
    public static Object linkValue(String family,Object raw){return scalar(family,raw);}
    public static Object value(String type,String family,Object raw) {
        if(raw==null || "".equals(raw))return null;
        if(type.equals("number") || type.equals("date")) {
            if(!(raw instanceof List<?> a) || a.size()!=2 || a.get(0)==null || a.get(1)==null || "".equals(a.get(0)) || "".equals(a.get(1)))throw new IllegalArgumentException("范围筛选须填写起止值");
            Object from=scalar(family,a.get(0)),to=scalar(family,a.get(1));
            int order=family.equals("number")?((BigDecimal)from).compareTo((BigDecimal)to):from.toString().compareTo(to.toString());
            if(order>0)throw new IllegalArgumentException("筛选起值不能大于止值");
            return List.of(from,to);
        }
        return scalar(family,raw);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> inputs(Map<String,Object> body,String section,Set<String> allowed) {
        if(body==null)return Map.of();
        if(!Set.of("filters","links","drills").containsAll(body.keySet()))throw new IllegalArgumentException("只允许提交筛选器、联动或下钻值");
        Object raw=body.get(section);if(raw==null)return Map.of();
        if(!(raw instanceof Map<?,?> map) || map.size()>12 || map.keySet().stream().anyMatch(k->!(k instanceof String) || !allowed.contains(k)))throw new IllegalArgumentException("存在未发布的筛选器或联动标识");
        return (Map<String,Object>)map;
    }
    public static void append(QueryModel q,String field,String type,Object value) {
        if(value!=null)q.addFilter(field,(type.equals("number") || type.equals("date"))?"BETWEEN":"EQ",value);
    }

    public static void validateDrillMap(Map<String,Object> body,Set<String> drillablePanels) {
        if(body==null)return;
        Object raw=body.get("drills");
        if(raw==null)return;
        if(!(raw instanceof Map<?,?> map))throw new IllegalArgumentException("下钻参数格式无效");
        if(map.size()>12)throw new IllegalArgumentException("下钻状态过多");
        for(var entry:map.entrySet()) {
            if(!(entry.getKey() instanceof String panel) || !drillablePanels.contains(panel))throw new IllegalArgumentException("存在未发布的下钻组件");
            if(!(entry.getValue() instanceof List<?>))throw new IllegalArgumentException("下钻路径格式无效");
        }
    }
}
