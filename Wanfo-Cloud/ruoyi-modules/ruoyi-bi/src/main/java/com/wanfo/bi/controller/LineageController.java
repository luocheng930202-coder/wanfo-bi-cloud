package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.LineageCollectionService;
import com.wanfo.bi.service.LineageGraphService;
import com.wanfo.bi.service.LineageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bi/catalog/lineage")
public class LineageController
{
    public record InvalidateRequest(String status) { }
    public record RecomputeRequest(String sourceMethod,java.math.BigDecimal confidence,Map<String,Object> evidence) { }
    private final LineageService lineage;
    private final LineageCollectionService collection;
    private final LineageGraphService graph;
    private final CatalogAuditService audit;
    public LineageController(LineageService lineage,LineageCollectionService collection,LineageGraphService graph,CatalogAuditService audit){this.lineage=lineage;this.collection=collection;this.graph=graph;this.audit=audit;}

    @PostMapping("/rebuild") @BiPermission("bi:resource:admin")
    public Map<String,Object> rebuild(){Map<String,Object> result=collection.rebuild();audit.record("LINEAGE_REBUILD","CATALOG",null,null,result);return result;}

    @GetMapping("/graph/{assetId}") @BiPermission("bi:datasource:model")
    public Map<String,Object> graph(@PathVariable Long assetId,@RequestParam(defaultValue="both") String direction,
            @RequestParam(defaultValue="3") int depth,@RequestParam(defaultValue="false") boolean includeStale){
        Map<String,Object> result=graph.graph(assetId,direction,depth,includeStale);
        audit.record("LINEAGE_GRAPH_VIEW","ASSET",assetId,null,Map.of("direction",result.get("direction"),"depth",result.get("depth"),"nodeCount",((List<?>)result.get("nodes")).size(),"edgeCount",((List<?>)result.get("edges")).size(),"includeStale",includeStale,"truncated",result.get("truncated")));
        return result;
    }

    @GetMapping("/edges") @BiPermission("bi:resource:admin")
    public List<Map<String,Object>> edges(@RequestParam Long assetId,@RequestParam(defaultValue="false") boolean activeOnly){var result=lineage.list(assetId,activeOnly);audit.record("LINEAGE_EDGE_LIST","ASSET",assetId,null,Map.of("activeOnly",activeOnly,"returned",result.size()));return result;}

    @PostMapping("/edges") @BiPermission("bi:resource:admin")
    public Map<String,Object> save(@RequestBody Map<String,Object> input){Map<String,Object> result=lineage.save(input);audit.record("LINEAGE_EDGE_SAVE","LINEAGE_EDGE",number(result.get("id")),null,Map.of("lineageLevel",result.get("lineageLevel"),"sourceMethod",result.get("sourceMethod"),"confidence",result.get("confidence")));return result;}

    @PostMapping("/edges/{id}/invalidate") @BiPermission("bi:resource:admin")
    public Map<String,Object> invalidate(@PathVariable Long id,@RequestBody InvalidateRequest body){Map<String,Object> result=lineage.invalidate(id,body.status());audit.record("LINEAGE_EDGE_INVALIDATE","LINEAGE_EDGE",id,null,Map.of("status",body.status()));return result;}

    @PostMapping("/edges/{id}/recompute") @BiPermission("bi:resource:admin")
    public Map<String,Object> recompute(@PathVariable Long id,@RequestBody RecomputeRequest body){Map<String,Object> input=new java.util.LinkedHashMap<>();input.put("sourceMethod",body.sourceMethod());input.put("confidence",body.confidence());input.put("evidence",body.evidence());Map<String,Object> result=lineage.recompute(id,input);audit.record("LINEAGE_EDGE_RECOMPUTE","LINEAGE_EDGE",id,null,Map.of("sourceMethod",result.get("sourceMethod"),"confidence",result.get("confidence")));return result;}

    @GetMapping("/edges/{id}/revisions") @BiPermission("bi:resource:admin")
    public List<Map<String,Object>> revisions(@PathVariable Long id){var result=lineage.revisions(id);audit.record("LINEAGE_EDGE_REVISIONS","LINEAGE_EDGE",id,null,Map.of("returned",result.size()));return result;}

    private Long number(Object value){return value instanceof Number n?n.longValue():value==null?null:Long.valueOf(value.toString());}
}
