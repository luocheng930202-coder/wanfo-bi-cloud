package com.wanfo.bi.controller;
import com.wanfo.bi.service.ResourceImpactService;
import com.wanfo.bi.security.BiPermission;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/bi/resources")
public class BiResourceImpactController {
    private final ResourceImpactService service;
    public BiResourceImpactController(ResourceImpactService service){this.service=service;}
    @GetMapping("/{type}/{id}/impact")
    @BiPermission(anyOf={"bi:datasource:list","bi:dataset:list","bi:chart:list","bi:dashboard:list"})
    public Map<String,Object> impact(@PathVariable String type,@PathVariable Long id){return service.graph(type,id);}
}
