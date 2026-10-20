package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.chart.ChartTypes;

/** Server-side v1 file protocol, aligned with biTemplateFiles.ts. No executable content or bindings. */
final class LayoutTemplateValidator {
    private LayoutTemplateValidator() { }
    static Map<String,Object> validate(Map<String,Object> value) {
        if(value==null)throw invalid("模板不能为空");
        complexity(value,0,new int[]{0});
        boolean screen="datacenter-screen-template".equals(value.get("format"));
        if(!screen&&!"datacenter-dashboard-template".equals(value.get("format")))throw invalid("模板格式不受支持");
        object(value,screen?"format version name panels scene appearance":"format version name panels appearance");
        number(value.get("version"),1,1);text(value.get("name"),200,false);
        int width=12,height=120;
        if(screen){var scene=object(value.get("scene"),"width height fit refreshSeconds");width=number(scene.get("width"),320,8192);height=number(scene.get("height"),240,8192);choice(scene.get("fit"),Set.of("contain","width","none"));number(scene.get("refreshSeconds"),0,0);}
        appearance(value.get("appearance"));
        if(!(value.get("panels") instanceof List<?> panels)||panels.isEmpty()||panels.size()>20)throw invalid("模板须包含1至20个组件");
        List<Map<String,Object>> entries=new ArrayList<>();Map<String,Map<String,Object>> keys=new LinkedHashMap<>();int images=0;
        for(Object item:panels){
            var p=object(item,screen?"key title componentType x y w h style content locked hidden groupKey parentKey chartType":"key title componentType x y w h style content locked chartType");
            String key=key(p.get("key"));if(keys.putIfAbsent(key,p)!=null)throw invalid("组件标识重复");entries.add(p);
            text(p.get("title"),200,true);String type=choice(p.get("componentType"),screen?Set.of("chart","kpi","text","image","border","line","container"):Set.of("chart","kpi","text","image"));
            int x=number(p.get("x"),0,screen?width:10),y=number(p.get("y"),0,screen?height:118),w=number(p.get("w"),screen?20:2,width),h=number(p.get("h"),screen?20:2,screen?height:12);
            if(x+w>width||y+h>height)throw invalid("组件超出布局边界");
            bool(p.get("locked"));if(screen)bool(p.get("hidden"));style(p.get("style"));
            if(p.get("groupKey")!=null)key(p.get("groupKey"));if(p.get("parentKey")!=null)key(p.get("parentKey"));
            if(Set.of("chart","kpi").contains(type)){
                choice(p.get("chartType"),ChartTypes.ALL);if("kpi".equals(type)&&!"table".equals(p.get("chartType")))throw invalid("指标卡须使用单值表格槽");
                if(p.get("content")!=null)throw invalid("数据槽不能携带静态内容");
            }else{
                if(p.get("chartType")!=null)throw invalid("静态组件不能携带图表类型");
                switch(type){
                    case "text"->text(object(p.get("content"),"text").get("text"),2000,true);
                    case "image"->{if(++images>5)throw invalid("模板最多打包5张图片");String image=text(object(p.get("content"),"image").get("image"),350000,false);DashboardContent.normalize("image",new DashboardContent.Content(null,image));}
                    case "container"->container(p);
                    default->{var d=object(object(p.get("content"),"decoration").get("decoration"),"color stroke pattern radius direction");color(d.getOrDefault("color","#38BDF8"),false);number(d.getOrDefault("stroke",2),1,12);number(d.getOrDefault("radius",8),0,80);choice(d.getOrDefault("pattern","solid"),Set.of("solid","dashed","dotted"));choice(d.getOrDefault("direction","horizontal"),Set.of("horizontal","vertical"));}
                }
            }
        }
        if(screen)relations(entries,keys);else for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++)if(overlap(entries.get(i),entries.get(j)))throw invalid("栅格组件不能重叠");
        return value;
    }
    private static void relations(List<Map<String,Object>> entries,Map<String,Map<String,Object>> keys){
        Map<String,List<Map<String,Object>>> groups=new HashMap<>();
        for(int i=0;i<entries.size();i++){
            var p=entries.get(i);boolean container="container".equals(p.get("componentType"));Object parent=p.get("parentKey"),group=p.get("groupKey");
            if((container||parent!=null)&&group!=null)throw invalid("容器及成员须先解组");
            if(container&&parent!=null)throw invalid("不支持嵌套容器");
            if(group!=null)groups.computeIfAbsent(group.toString(),k->new ArrayList<>()).add(p);
            if(parent==null)continue;
            var owner=keys.get(parent);int pi=owner==null?-1:entries.indexOf(owner);
            if(owner==null||owner==p||!"container".equals(owner.get("componentType"))||owner.get("parentKey")!=null||pi>=i)throw invalid("容器父级或层序无效");
            for(int n=pi+1;n<i;n++)if(!Objects.equals(entries.get(n).get("parentKey"),parent))throw invalid("容器成员须连续排列");
            int pad=container(owner);
            if(n(p,"x")<n(owner,"x")+pad||n(p,"y")<n(owner,"y")+pad||n(p,"x")+n(p,"w")>n(owner,"x")+n(owner,"w")-pad||n(p,"y")+n(p,"h")>n(owner,"y")+n(owner,"h")-pad)throw invalid("成员超出容器内边距");
            if(Boolean.TRUE.equals(owner.get("hidden"))&&!Boolean.TRUE.equals(p.get("hidden"))||Boolean.TRUE.equals(owner.get("locked"))&&!Boolean.TRUE.equals(p.get("locked")))throw invalid("成员隐藏、锁定须与容器一致");
        }
        for(var group:groups.values())if(group.size()<2||group.stream().anyMatch(p->!Objects.equals(p.get("hidden"),group.get(0).get("hidden"))))throw invalid("组合须至少两个成员且整体显示或隐藏");
    }
    private static int container(Map<String,Object> p){
        var c=object(object(p.get("content"),"container").get("container"),"background borderColor stroke radius padding");
        color(c.getOrDefault("background","#16243A"),true);color(c.getOrDefault("borderColor","#38BDF8"),false);number(c.getOrDefault("stroke",1),0,12);number(c.getOrDefault("radius",8),0,80);int pad=number(c.getOrDefault("padding",12),0,40);
        if(n(p,"w")<2*pad+20||n(p,"h")<2*pad+20)throw invalid("容器尺寸不足");return pad;
    }
    private static void appearance(Object value){var a=object(value,"theme background cardBackground textColor borderColor palette titleSize radius decimals grouping");choice(a.get("theme"),Set.of("light","dark"));for(String k:List.of("background","cardBackground","textColor","borderColor"))color(a.get(k),false);choice(a.get("palette"),Set.of("business","ocean","warm"));number(a.get("titleSize"),12,24);number(a.get("radius"),0,24);number(a.get("decimals"),0,6);bool(a.get("grouping"));}
    private static void style(Object value){var s=object(value,"showTitle titleAlign numberFormat");bool(s.get("showTitle"));choice(s.get("titleAlign"),Set.of("left","center","right"));if(s.get("numberFormat")!=null){var f=object(s.get("numberFormat"),"mode scale decimals grouping fixed prefix suffix nullText");choice(f.get("mode"),Set.of("number","percent"));choice(f.get("scale"),Set.of("none","thousand","wan","million"));if("percent".equals(f.get("mode"))&&!"none".equals(f.get("scale")))throw invalid("百分比不能同时缩放");number(f.get("decimals"),0,6);bool(f.get("grouping"));bool(f.get("fixed"));for(String k:List.of("prefix","suffix","nullText"))if(text(f.get(k),20,true).matches("(?s).*[\\x00-\\x1f\\x7f-\\x9f].*"))throw invalid("格式文本含控制字符");}}
    private static boolean overlap(Map<String,Object> a,Map<String,Object> b){return n(a,"x")<n(b,"x")+n(b,"w")&&n(a,"x")+n(a,"w")>n(b,"x")&&n(a,"y")<n(b,"y")+n(b,"h")&&n(a,"y")+n(a,"h")>n(b,"y");}
    private static int n(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value,String fields){if(!(value instanceof Map<?,?> map)||!new HashSet<>(Arrays.asList(fields.split(" "))).containsAll(map.keySet()))throw invalid("模板结构或字段不受支持");return (Map<String,Object>)map;}
    private static String text(Object value,int max,boolean empty){if(!(value instanceof String s)||s.length()>max||!empty&&s.isBlank())throw invalid("模板文本无效");return s;}
    private static String key(Object value){String k=text(value,64,false);if(!k.matches("[A-Za-z0-9_-]+"))throw invalid("组件标识无效");return k;}
    private static int number(Object value,int min,int max){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw invalid("模板数值超出范围");return n.intValue();}
    private static void bool(Object value){if(!(value instanceof Boolean))throw invalid("模板布尔值无效");}
    private static String choice(Object value,Set<String> choices){if(!(value instanceof String s)||!choices.contains(s))throw invalid("模板选项不受支持");return s;}
    private static void color(Object value,boolean transparent){if(!(value instanceof String s)||!(transparent&&"transparent".equals(s)||s.matches("#[0-9a-fA-F]{6}")))throw invalid("模板颜色无效");}
    private static void complexity(Object value,int depth,int[] nodes){if(depth>12||++nodes[0]>4000)throw invalid("模板结构过于复杂");if(value instanceof Map<?,?> map)for(var e:map.entrySet()){if(Set.of("__proto__","prototype","constructor").contains(e.getKey()))throw invalid("模板字段不安全");complexity(e.getValue(),depth+1,nodes);}else if(value instanceof List<?> list)for(var item:list)complexity(item,depth+1,nodes);}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
