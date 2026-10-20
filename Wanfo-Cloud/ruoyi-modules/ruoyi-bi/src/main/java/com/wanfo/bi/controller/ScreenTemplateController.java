package com.wanfo.bi.controller;
import java.util.*;
import com.wanfo.bi.service.ScreenTemplateService;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.PrincipalCatalog;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/bi/template")
public class ScreenTemplateController {
 private final ScreenTemplateService service;private final PrincipalCatalog principals;
 public ScreenTemplateController(ScreenTemplateService service,PrincipalCatalog principals){this.service=service;this.principals=principals;}
 @GetMapping @BiPermission("bi:template:list") public List<Map<String,Object>> list(){return service.list();}
 @GetMapping("/summaries") @BiPermission("bi:template:list") public List<Map<String,Object>> summaries(){return service.listSummaries();}
 @GetMapping("/{id}") @BiPermission("bi:template:list") public Map<String,Object> detail(@PathVariable Long id){return service.detail(id);}
 @PostMapping @BiPermission("bi:template:add") public Map<String,Object> create(@RequestBody ScreenTemplateService.SaveRequest request){return service.create(request);}
 @PutMapping("/{id}") @BiPermission("bi:template:edit") public Map<String,Object> update(@PathVariable Long id,@RequestBody ScreenTemplateService.SaveRequest request){return service.update(id,request);}
 @DeleteMapping("/{id}") @BiPermission("bi:template:remove") public Map<String,Object> delete(@PathVariable Long id){return service.delete(id);}
 @GetMapping("/principals") @BiPermission("bi:template:grant") public List<Map<String,Object>> principals(@RequestParam(defaultValue="")String query){if(query.length()>200)throw new IllegalArgumentException("搜索内容过长");return principals.search(query);}
 @GetMapping("/{id}/grants") @BiPermission("bi:template:grant") public List<Map<String,Object>> grants(@PathVariable Long id){return service.grants(id);}
 public record Grant(Long principalId,String action) { }
 @PostMapping("/{id}/grants") @BiPermission("bi:template:grant") public Map<String,Object> grant(@PathVariable Long id,@RequestBody Grant request){service.grant(id,request.principalId(),request.action(),true);return Map.of("granted",true);}
 @DeleteMapping("/{id}/grants/{principal}/{action}") @BiPermission("bi:template:grant") public Map<String,Object> revoke(@PathVariable Long id,@PathVariable Long principal,@PathVariable String action){service.grant(id,principal,action,false);return Map.of("revoked",true);}
}
