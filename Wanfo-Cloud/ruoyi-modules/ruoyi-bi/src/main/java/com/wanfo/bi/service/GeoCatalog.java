package com.wanfo.bi.service;

import java.io.InputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.ObjectMapper;

/** Versioned, bundled boundaries only. Neither scope IDs nor feature names are URLs or paths. */
public final class GeoCatalog {
    public record Region(String code,String name,String level,String parent,List<String> children,List<String> aliases,String boundarySource) { }
    public record Index(String version,String source,List<Region> regions,List<String> unavailableChildren) { }
    private GeoCatalog() { }
    private static final class Holder {
        static final ObjectMapper JSON=new ObjectMapper();
        static final Index INDEX=readIndex();
        static final Map<String,Region> REGIONS=index();
        static Index readIndex(){try(var in=GeoCatalog.class.getResourceAsStream("/bi/geo/regions-index.json")){if(in==null)throw new IllegalStateException("地图目录未安装");return JSON.readValue(in,Index.class);}catch(Exception e){throw new IllegalStateException("地图目录无法读取",e);}}
        static Map<String,Region> index(){var m=new LinkedHashMap<String,Region>();for(var r:INDEX.regions())m.put(r.code(),r);return Collections.unmodifiableMap(m);}
    }
    public static Index catalog(){return Holder.INDEX;}
    public static Region region(String scope){String code=scope==null || "china-provinces".equals(scope)?"100000":scope;if(!code.matches("[0-9]{6}") || !Holder.REGIONS.containsKey(code))throw new IllegalArgumentException("不支持的地图范围");return Holder.REGIONS.get(code);}
    public static String scope(Region region){return "100000".equals(region.code())?"china-provinces":region.code();}
    public static List<Region> children(Region region){return region.children().stream().map(GeoCatalog::region).toList();}
    public static String displayLevel(Region r){var children=children(r);if(children.isEmpty())return r.level();if(children.stream().anyMatch(c->"province".equals(c.level())))return "province";if(children.stream().anyMatch(c->"city".equals(c.level())))return "city";return "district";}
    public static List<Region> ancestors(Region r){var path=new ArrayList<Region>();while(r.parent()!=null){r=region(r.parent());if(!"country".equals(r.level()))path.add(r);}Collections.reverse(path);return path;}
    public static Region child(Region parent,Object value,String mode){String text=String.valueOf(value);var candidates=children(parent).stream().filter(r->"code".equals(mode)?r.code().equals(text):r.aliases().contains(text)).toList();if(candidates.size()!=1)throw new IllegalArgumentException("下钻地区不属于当前地图或名称不唯一，请使用行政区编码");return candidates.get(0);}
    @SuppressWarnings("unchecked")
    public static Object geometry(String scope) throws Exception {
        var r=region(scope);String source=r.boundarySource();String path="100000".equals(source)?"/bi/geo/china-provinces.json":"/bi/geo/regions/"+source+".json.gz";
        try(InputStream raw=GeoCatalog.class.getResourceAsStream(path)){
            if(raw==null)throw new IllegalArgumentException("当前地图边界未安装");
            try(InputStream in=path.endsWith(".gz")?new GZIPInputStream(raw):raw){
                var data=Holder.JSON.readValue(in,Map.class);
                if(source.equals(r.code()))return data;
                var features=(List<Map<String,Object>>)data.get("features");
                var match=features.stream().filter(f->r.code().equals(String.valueOf(((Map<?,?>)f.get("properties")).get("adcode")))).toList();
                if(match.isEmpty())throw new IllegalArgumentException("当前地图边界不存在");
                return Map.of("type","FeatureCollection","features",match);
            }
        }
    }
}
