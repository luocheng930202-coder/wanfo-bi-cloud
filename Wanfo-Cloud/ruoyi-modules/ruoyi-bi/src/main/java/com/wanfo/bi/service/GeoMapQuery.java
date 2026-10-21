package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.ChartDefinitionService.Definition;
import tools.jackson.databind.ObjectMapper;

/** Re-aggregate at the selected geography using the normal secured query compiler. */
public final class GeoMapQuery {
    private GeoMapQuery() { }
    private static final ObjectMapper JSON=new ObjectMapper();
    public static boolean isMap(Definition d){return d!=null && "map".equals(d.type());}
    public static GeoCatalog.Region root(Definition d){return GeoCatalog.region(d.advanced()==null?null:d.advanced().getMapScope());}
    public static String mode(Definition d){return d.advanced()==null || d.advanced().getMapValueMode()==null?"name":d.advanced().getMapValueMode();}
    private static String binding(Definition d,String level){var f=d.advanced()==null?null:d.advanced().getMapFields();if(f==null)return null;return switch(level){case "province"->f.province();case "city"->f.city();case "district"->f.county();default->null;};}
    public static String field(Definition d,GeoCatalog.Region scope){String value=binding(d,GeoCatalog.displayLevel(scope));if(value!=null && !value.isBlank())return value;if(scope.code().equals(root(d).code()) && "100000".equals(scope.code()))return d.query().getDimensions().get(0).getField();return null;}
    public static List<String> fields(Definition d){var values=new LinkedHashSet<String>();for(var level:List.of("province","city","district")){var f=binding(d,level);if(f!=null&&!f.isBlank())values.add(f);}if(d.query()!=null && !d.query().getDimensions().isEmpty())values.add(d.query().getDimensions().get(0).getField());return List.copyOf(values);}
    public static boolean canOpen(Definition d,GeoCatalog.Region child){return !child.children().isEmpty() && field(d,child)!=null;}
    public static boolean enabled(Definition d){return GeoCatalog.children(root(d)).stream().anyMatch(r->canOpen(d,r));}
    public static void validate(Definition d,Map<String,String> types){
        if(!isMap(d))return;
        if(!Set.of("name","code").contains(mode(d)))throw new IllegalArgumentException("地图地区值须使用名称或行政区编码");
        var root=root(d);String display=field(d,root);
        if(display==null)throw new IllegalArgumentException("请配置当前地图层级对应的地区字段");
        if(!Objects.equals(display,d.query().getDimensions().get(0).getField()))throw new IllegalArgumentException("地图维度须与当前层级绑定字段一致");
        var seen=new HashSet<String>();var bindings=d.advanced()==null?null:d.advanced().getMapFields();
        if(bindings!=null)for(var value:Arrays.asList(bindings.province(),bindings.city(),bindings.county()))if(value!=null && !value.isBlank() && !seen.add(value))throw new IllegalArgumentException("省市县层级须分别绑定不同字段");
        for(var f:fields(d)){
            if(f.length()>120 || !types.containsKey(f))throw new IllegalArgumentException("地图绑定字段不存在或没有查询权限");
            String family=DashboardInteraction.family(types.get(f));if("date".equals(family) || "name".equals(mode(d)) && !"text".equals(family))throw new IllegalArgumentException("地区名称须使用文本字段；行政区编码可使用文本或数值字段");
        }
        if("name".equals(mode(d))){
            var parents=new ArrayList<>(GeoCatalog.ancestors(root));parents.add(root);
            for(var p:parents)if(!"country".equals(p.level()) && !p.level().equals(GeoCatalog.displayLevel(root)) && (binding(d,p.level())==null || binding(d,p.level()).isBlank()))throw new IllegalArgumentException("按名称匹配区域地图时，请同时绑定上级省市字段以避免同名地区误匹配");
        }
        if(d.advanced()!=null && d.advanced().getDrillFields()!=null && !d.advanced().getDrillFields().isEmpty())throw new IllegalArgumentException("地图下钻请使用省市县字段绑定");
    }
    public static List<ChartDrill.Segment> path(Definition d,Map<String,Object> input,String key,Map<String,String> types){
        if(input==null || input.get("drills")==null)return List.of();
        if(!(input.get("drills") instanceof Map<?,?> drills) || drills.size()>12)throw new IllegalArgumentException("地图下钻参数无效");
        Object raw=drills.get(key);if(raw==null)return List.of();
        if(!(raw instanceof List<?> list) || list.size()>2)throw new IllegalArgumentException("地图下钻路径无效");
        var current=root(d);var result=new ArrayList<ChartDrill.Segment>();
        for(var item:list){
            if(!(item instanceof Map<?,?> segment))throw new IllegalArgumentException("地图下钻路径项无效");
            String field=field(d,current);
            if(field==null || !field.equals(segment.get("field")) || !types.containsKey(field))throw new IllegalArgumentException("地图下钻字段与当前层级不一致或没有权限");
            var value=DashboardInteraction.linkValue(DashboardInteraction.family(types.get(field)),segment.get("value"));
            var child=GeoCatalog.child(current,value,mode(d));
            if(!canOpen(d,child))throw new IllegalArgumentException("当前地区无下级地图或未配置下级字段");
            result.add(new ChartDrill.Segment(field,value));current=child;
        }
        return List.copyOf(result);
    }
    public static GeoCatalog.Region current(Definition d,List<ChartDrill.Segment> path){var r=root(d);for(var s:path)r=GeoCatalog.child(r,s.value(),mode(d));return r;}
    private static Object codeValue(String code,String field,Map<String,String> types){String family=DashboardInteraction.family(types.get(field));return "number".equals(family)?Long.valueOf(code):code;}
    private static List<Object> values(Definition d,List<GeoCatalog.Region> regions,String field,Map<String,String> types){var out=new LinkedHashSet<Object>();for(var r:regions){if("code".equals(mode(d)))out.add(codeValue(r.code(),field,types));else out.addAll(r.aliases());}return List.copyOf(out);}
    public static void apply(Definition d,QueryModel q,List<ChartDrill.Segment> path,Map<String,String> types){
        var current=current(d,path);String display=field(d,current);
        if(display==null || !types.containsKey(display))throw new IllegalArgumentException("当前地图层级字段未配置或没有查询权限");
        var selected=root(d);var parents=new ArrayList<>(GeoCatalog.ancestors(selected));parents.add(selected);
        for(var p:parents){var field=binding(d,p.level());if(field!=null && !field.equals(display) && !"country".equals(p.level()))q.addFilter(field,"IN",values(d,List.of(p),field,types));}
        for(var s:path)q.addFilter(s.field(),"EQ",s.value());
        var regions=GeoCatalog.children(current);if(regions.isEmpty())regions=List.of(current);
        if(!"100000".equals(current.code()))q.addFilter(display,"IN",values(d,regions,display,types));
        q.setDimensions(new ArrayList<>(List.of(new QueryModel.Dimension(display,display))));
        var metricKeys=new HashSet<String>();for(var m:q.getMetrics())metricKeys.add(m.getAlias()!=null&&!m.getAlias().isBlank()?m.getAlias():m.getAgg()==null || "NONE".equalsIgnoreCase(m.getAgg())?m.getField():m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField());
        if(q.getOrderBys()!=null)q.setOrderBys(q.getOrderBys().stream().filter(o->metricKeys.contains(o.getField()) || display.equals(o.getField())).toList());
    }
    public static Definition presentation(Definition d,List<ChartDrill.Segment> path){
        var q=JSON.readValue(JSON.writeValueAsString(d.query()),QueryModel.class);
        var a=d.advanced()==null?new com.wanfo.bi.domain.chart.ChartAdvancedOptions():JSON.readValue(JSON.writeValueAsString(d.advanced()),com.wanfo.bi.domain.chart.ChartAdvancedOptions.class);
        var r=current(d,path);String field=field(d,r);q.setDimensions(List.of(new QueryModel.Dimension(field,field)));a.setMapScope(GeoCatalog.scope(r));
        return new Definition(1,d.name(),d.type(),q,d.style(),a);
    }
    public static Map<String,Object> context(Definition d,List<ChartDrill.Segment> path){
        var scope=current(d,path);var crumbs=new ArrayList<Map<String,Object>>();var node=root(d);
        for(var seg:path){node=GeoCatalog.child(node,seg.value(),mode(d));crumbs.add(Map.of("name",node.name(),"code",node.code()));}
        var targets=GeoCatalog.children(scope).stream().filter(r->canOpen(d,r)).map(GeoCatalog.Region::code).toList();
        return Map.of("scope",GeoCatalog.scope(scope),"rootScope",GeoCatalog.scope(root(d)),"rootName",root(d).name(),"field",field(d,scope),"canDrillDown",!targets.isEmpty(),"drillableRegions",targets,"breadcrumbs",crumbs);
    }
}
