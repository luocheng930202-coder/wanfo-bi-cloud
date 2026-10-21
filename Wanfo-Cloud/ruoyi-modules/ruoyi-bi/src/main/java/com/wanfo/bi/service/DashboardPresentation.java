package com.wanfo.bi.service;

import java.util.*;

/** Host-independent, bounded display values. Never accepts CSS, URLs or scripts. */
public final class DashboardPresentation {
    private DashboardPresentation() { }
    public record Appearance(String theme, String background, String cardBackground, String textColor,
            String borderColor, String palette, Integer titleSize, Integer radius,
            Integer decimals, Boolean grouping) { }
    public record NumberFormat(String mode,String scale,Integer decimals,Boolean grouping,Boolean fixed,String prefix,String suffix,String nullText) { }
    public record Threshold(String op,String value,String color) { }
    public record MetricStyle(String key,NumberFormat format,List<Threshold> rules) { }
    public record PanelStyle(Boolean showTitle, String titleAlign,NumberFormat numberFormat,List<MetricStyle> metrics) {
        public PanelStyle(Boolean showTitle,String titleAlign,NumberFormat format){this(showTitle,titleAlign,format,List.of());}
        public PanelStyle(Boolean showTitle,String titleAlign){this(showTitle,titleAlign,null);}
    }
    private static String literal(String value,String fallback){if(value==null)return fallback;if(value.length()>20 || value.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("格式文本最多 20 字符且不能含控制字符");return value;}
    public static NumberFormat normalize(NumberFormat f){if(f==null)return null;String mode=choice(f.mode(),"number",Set.of("number","percent"));String scale=choice(f.scale(),"none",Set.of("none","thousand","wan","million","yi"));if(mode.equals("percent") && !scale.equals("none"))throw new IllegalArgumentException("百分比不能同时使用单位缩放");return new NumberFormat(mode,scale,bounded(f.decimals(),2,0,6),f.grouping()==null?true:f.grouping(),f.fixed()==null?true:f.fixed(),literal(f.prefix(),""),literal(f.suffix(),""),literal(f.nullText(),"—"));}

    public static List<MetricStyle> normalizeMetrics(List<MetricStyle> values){
        if(values==null)return List.of();if(values.size()>20)throw new IllegalArgumentException("最多 20 个度量样式");
        var result=new ArrayList<MetricStyle>();var keys=new HashSet<String>();
        for(var m:values){if(m==null || m.key()==null || m.key().isBlank() || m.key().length()>128 || m.key().codePoints().anyMatch(Character::isISOControl) || !keys.add(m.key()))throw new IllegalArgumentException("度量样式键无效或重复");
            var rules=m.rules()==null?List.<Threshold>of():m.rules();if(rules.size()>8)throw new IllegalArgumentException("每个度量最多 8 条阈值规则");
            var normalized=new ArrayList<Threshold>();for(var r:rules){if(r==null || r.op()==null || !Set.of("GT","GTE","LT","LTE","EQ","NE").contains(r.op()) || r.value()==null || r.value().length()>100 || !r.value().matches("[+-]?[0-9]+(\\.[0-9]+)?"))throw new IllegalArgumentException("阈值须为有效比较与十进制数值");normalized.add(new Threshold(r.op(),r.value(),color(r.color(),"#F56C6C")));}
            result.add(new MetricStyle(m.key(),normalize(m.format()),List.copyOf(normalized)));
        }return List.copyOf(result);
    }
    public static void validateMetricKeys(PanelStyle style,Set<String> outputs){for(var m:style.metrics())if(!outputs.contains(m.key()))throw new IllegalArgumentException("度量样式引用已失效，请更新或移除："+m.key());}
    public static Appearance defaults(String theme) {
        boolean dark="dark".equals(theme);
        return new Appearance(dark?"dark":"light",dark?"#111827":"#F2F5F9",
                dark?"#1F2937":"#FFFFFF",dark?"#F3F4F6":"#303133",
                dark?"#4B5563":"#DCE3EC","business",14,6,2,true);
    }
    private static String choice(String value,String fallback,Set<String> allowed) {
        if(value==null)return fallback;
        if(!allowed.contains(value))throw new IllegalArgumentException("不支持的看板样式选项");
        return value;
    }
    private static String color(String value,String fallback) {
        if(value==null)return fallback;
        if(!value.matches("#[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("看板颜色须为六位十六进制色值");
        return value.toUpperCase(java.util.Locale.ROOT);
    }
    private static Integer bounded(Integer value,int fallback,int min,int max) {
        if(value==null)return fallback;
        if(value<min || value>max)throw new IllegalArgumentException("看板样式数值超出允许范围");
        return value;
    }
    public static Appearance normalize(Appearance value) {
        if(value==null)return defaults("light");
        String theme=choice(value.theme(),"light",Set.of("light","dark"));var d=defaults(theme);
        return new Appearance(theme,color(value.background(),d.background()),color(value.cardBackground(),d.cardBackground()),
                color(value.textColor(),d.textColor()),color(value.borderColor(),d.borderColor()),
                choice(value.palette(),d.palette(),Set.of("business","ocean","warm")),
                bounded(value.titleSize(),14,12,24),bounded(value.radius(),6,0,24),
                bounded(value.decimals(),2,0,6),value.grouping()==null?true:value.grouping());
    }
    public static PanelStyle normalize(PanelStyle value) {
        return value==null?new PanelStyle(true,"left"):new PanelStyle(value.showTitle()==null?true:value.showTitle(),
                choice(value.titleAlign(),"left",Set.of("left","center","right")),normalize(value.numberFormat()),normalizeMetrics(value.metrics()));
    }
    public record Scene(Integer width,Integer height,String fit,Integer refreshSeconds) {
        public Scene(Integer width,Integer height,String fit){this(width,height,fit,0);}
    }
    private static int refreshSeconds(Integer value){int seconds=value==null?0:value;if(seconds!=0 && (seconds<10 || seconds>3600))throw new IllegalArgumentException("刷新间隔须为0或10至3600秒");return seconds;}
    public static Scene scene(String kind,Scene value){if(!"screen".equals(kind)){if(value!=null)throw new IllegalArgumentException("栅格看板不能包含自由画布尺寸");return null;}if(value==null)throw new IllegalArgumentException("大屏须设置设计尺寸");return new Scene(bounded(value.width(),1920,320,8192),bounded(value.height(),1080,240,8192),choice(value.fit(),"contain",Set.of("contain","width","none")),refreshSeconds(value.refreshSeconds()));}
    public static void carrier(String kind,String layout) {
        if("screen".equals(kind)){if(!"absolute".equals(layout))throw new IllegalArgumentException("大屏须使用 absolute 布局");return;}
        if(kind!=null && !kind.equals("dashboard"))throw new IllegalArgumentException("不支持的文档载体");
        if(layout!=null && !layout.equals("grid"))throw new IllegalArgumentException("栅格看板须使用 grid 布局");
    }
}
