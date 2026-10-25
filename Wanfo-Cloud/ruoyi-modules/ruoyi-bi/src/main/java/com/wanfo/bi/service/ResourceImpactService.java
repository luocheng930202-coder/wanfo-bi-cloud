package com.wanfo.bi.service;
import java.util.*;
import com.wanfo.bi.mapper.BiResourceGraphMapper;
import com.wanfo.bi.security.*;
import org.springframework.stereotype.Service;

/** Only metadata visible to the current caller enters the graph, including draft edges. */
@Service
public class ResourceImpactService {
    private final BiResourceGraphMapper mapper;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    public ResourceImpactService(BiResourceGraphMapper mapper,BiResourceAuthorizer resources,CapabilityProvider caps){this.mapper=mapper;this.resources=resources;this.caps=caps;}
    private String key(String type,Long id){return type+":"+id;}
    public Map<String,Object> graph(String type,Long id){
        if(!Set.of("datasource","dataset","chart","dashboard").contains(type) || id==null || id<1)throw new IllegalArgumentException("资源类型或编号无效");
        caps.require("bi:"+type+":list");resources.require(type,id,"view");
        var rows=mapper.nodes();var rawEdges=mapper.edges();
        Map<String,Map<String,Object>> visible=new LinkedHashMap<>();Set<Long> draftManagers=new HashSet<>();
        for(var n:rows){
            if(!caps.has("bi:"+n.type()+":list") || !resources.allowed(n.type(),n.id(),"view"))continue;
            boolean manager="dashboard".equals(n.type()) && ((caps.has("bi:dashboard:edit") && resources.allowed("dashboard",n.id(),"edit")) || (caps.has("bi:dashboard:publish") && resources.allowed("dashboard",n.id(),"publish")));
            if("dashboard".equals(n.type()) && !manager && !n.published())continue;
            if(manager)draftManagers.add(n.id());
            visible.put(key(n.type(),n.id()),Map.of("key",key(n.type(),n.id()),"type",n.type(),"id",n.id(),"name",!"dashboard".equals(n.type()) || manager?n.name():Objects.toString(n.publishedName(),"已发布看板")));
        }
        String root=key(type,id);if(!visible.containsKey(root))throw new BiAccessException(403,"资源不可查看");
        var edges=rawEdges.stream().filter(e->visible.containsKey(key(e.sourceType(),e.sourceId())) && visible.containsKey(key(e.targetType(),e.targetId())))
                .filter(e->!"draft".equals(e.kind()) || draftManagers.contains(e.targetId())).toList();
        Set<String> connected=new LinkedHashSet<>();connected.add(root);
        for(int depth=0;depth<4;depth++){Set<String> next=new HashSet<>(connected);for(var e:edges)if(connected.contains(key(e.sourceType(),e.sourceId())) || connected.contains(key(e.targetType(),e.targetId()))){next.add(key(e.sourceType(),e.sourceId()));next.add(key(e.targetType(),e.targetId()));}if(next.equals(connected))break;connected.addAll(next);}
        Set<String> downstream=new HashSet<>();downstream.add(root);for(int depth=0;depth<4;depth++)for(var e:edges)if(downstream.contains(key(e.sourceType(),e.sourceId())))downstream.add(key(e.targetType(),e.targetId()));downstream.remove(root);
        boolean truncated=rows.size()>2000 || rawEdges.size()>6000 || connected.size()>200;
        var nodes=visible.values().stream().filter(n->connected.contains(n.get("key"))).sorted(Comparator.comparing(n->!root.equals(n.get("key")))).limit(200).toList();var selected=nodes.stream().map(n->(String)n.get("key")).collect(java.util.stream.Collectors.toSet());
        var links=edges.stream().filter(e->selected.contains(key(e.sourceType(),e.sourceId())) && selected.contains(key(e.targetType(),e.targetId())))
                .map(e->Map.of("source",key(e.sourceType(),e.sourceId()),"target",key(e.targetType(),e.targetId()),"kind",e.kind())).distinct().toList();
        return Map.of("root",root,"nodes",nodes,"edges",links,"downstream",downstream.stream().filter(selected::contains).sorted().toList(),"truncated",truncated,
            "notice","仅展示当前账号可查看的绑定关系；SQL 跨源依赖及无权查看的资源不在此图中。已发布看板使用冻结配置，源图表编辑不会自动替换已发布快照；删除仍以服务端校验为准。");
    }
}
