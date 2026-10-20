package com.wanfo.bi.controller;
import java.util.*;
import com.wanfo.bi.service.DashboardService;
import com.wanfo.bi.security.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bi/dashboard")
public class DashboardController {
    private final DashboardService service;
    private final PrincipalCatalog principals;
    public DashboardController(DashboardService service,PrincipalCatalog principals){this.service=service;this.principals=principals;}
    @GetMapping @BiPermission("bi:dashboard:list") public List<Map<String,Object>> list(){return service.list();}
    @GetMapping("/{id}/draft") @BiPermission("bi:dashboard:edit") public Map<String,Object> draft(@PathVariable Long id){return service.draft(id);}
    @PostMapping @BiPermission("bi:dashboard:add") public Map<String,Object> create(@RequestBody DashboardService.SaveRequest request){return service.create(request);}
    @PutMapping("/{id}") @BiPermission("bi:dashboard:edit") public Map<String,Object> update(@PathVariable Long id,@RequestBody DashboardService.SaveRequest request){return service.update(id,request);}
    @PostMapping("/preview") @BiPermission("bi:dashboard:add") public Map<String,Object> previewNew(@RequestBody DashboardService.PreviewRequest request)throws Exception{return service.preview(null,request);}
    @PostMapping("/{id}/preview") @BiPermission("bi:dashboard:edit") public Map<String,Object> previewDraft(@PathVariable Long id,@RequestBody DashboardService.PreviewRequest request)throws Exception{return service.preview(id,request);}
    @PostMapping("/preview/options/{key}") @BiPermission("bi:dashboard:add") public Map<String,Object> previewNewOptions(@PathVariable String key,@RequestBody DashboardService.PreviewRequest request)throws Exception{return service.previewOptions(null,key,request);}
    @PostMapping("/{id}/preview/options/{key}") @BiPermission("bi:dashboard:edit") public Map<String,Object> previewDraftOptions(@PathVariable Long id,@PathVariable String key,@RequestBody DashboardService.PreviewRequest request)throws Exception{return service.previewOptions(id,key,request);}
    public record VersionRequest(Integer version) { }
    @PostMapping("/{id}/publish") @BiPermission("bi:dashboard:publish") public Map<String,Object> publish(@PathVariable Long id,@RequestBody VersionRequest request){return service.publish(id,request.version());}
    @PostMapping("/{id}/unpublish") @BiPermission("bi:dashboard:publish") public Map<String,Object> unpublish(@PathVariable Long id,@RequestBody VersionRequest request){return service.unpublish(id,request.version());}
    @DeleteMapping("/{id}") @BiPermission("bi:dashboard:remove") public Map<String,Object> delete(@PathVariable Long id){return service.delete(id);}
    @GetMapping("/{id}/view") @BiPermission("bi:dashboard:consume") public Map<String,Object> view(@PathVariable Long id){return service.view(id);}
    @PostMapping("/{id}/panels/{key}/data") @BiPermission("bi:dashboard:consume") public Map<String,Object> data(@PathVariable Long id,@PathVariable String key,@RequestParam Integer revision,@RequestBody(required=false) Map<String,Object> input)throws Exception{return service.panelData(id,key,revision,input);}
    @PostMapping("/{id}/controls/{key}/options") @BiPermission("bi:dashboard:consume") public Map<String,Object> options(@PathVariable Long id,@PathVariable String key,@RequestParam Integer revision,@RequestBody(required=false) Map<String,Object> input)throws Exception{return service.options(id,key,revision,input);}
    @GetMapping("/principals") @BiPermission("bi:dashboard:grant") public List<Map<String,Object>> principals(@RequestParam(defaultValue="")String query){return principals.search(query);}
    @GetMapping("/{id}/grants") @BiPermission("bi:dashboard:grant") public List<Map<String,Object>> grants(@PathVariable Long id){return service.grants(id);}
    public record GrantRequest(Long principalId,String action) { }
    @PostMapping("/{id}/grants") @BiPermission("bi:dashboard:grant") public Map<String,Object> grant(@PathVariable Long id,@RequestBody GrantRequest request){service.grant(id,request.principalId(),request.action(),true);return Map.of("granted",true);}
    @DeleteMapping("/{id}/grants/{principalId}/{action}") @BiPermission("bi:dashboard:grant") public Map<String,Object> revoke(@PathVariable Long id,@PathVariable Long principalId,@PathVariable String action){service.grant(id,principalId,action,false);return Map.of("revoked",true);}
}
