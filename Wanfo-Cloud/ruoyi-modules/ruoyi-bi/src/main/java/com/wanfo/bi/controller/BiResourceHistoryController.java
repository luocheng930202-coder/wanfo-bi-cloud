package com.wanfo.bi.controller;
import com.wanfo.bi.service.ResourceHistoryService;
import com.wanfo.bi.security.BiPermission;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/bi/resources/{type}/{id}/history")
public class BiResourceHistoryController {
    private final ResourceHistoryService service;
    public BiResourceHistoryController(ResourceHistoryService service){this.service=service;}
    @GetMapping @BiPermission(anyOf={"bi:chart:edit","bi:dashboard:edit"}) public Object list(@PathVariable String type,@PathVariable Long id){return service.list(type,id);}
    @GetMapping("/{version}") @BiPermission(anyOf={"bi:chart:edit","bi:dashboard:edit"}) public Object detail(@PathVariable String type,@PathVariable Long id,@PathVariable int version){return service.detail(type,id,version);}
    public record RestoreRequest(int currentVersion){}
    @PostMapping("/{version}/restore") @BiPermission(anyOf={"bi:chart:edit","bi:dashboard:edit"}) public Map<String,Object> restore(@PathVariable String type,@PathVariable Long id,@PathVariable int version,@RequestBody RestoreRequest request){return service.restore(type,id,version,request.currentVersion());}
}
