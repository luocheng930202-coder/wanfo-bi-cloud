package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiApiKeyAccess;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.service.DataApiService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bi")
public class DataApiController
{
    private final DataApiService service;
    private final BiResourceAuthorizer resources;
    public DataApiController(DataApiService service, BiResourceAuthorizer resources){this.service=service;this.resources=resources;}

    @GetMapping("/data-apis") @BiPermission("bi:metric:list")
    public List<Map<String,Object>> list(){return service.list();}

    @GetMapping("/data-apis/portal") @BiPermission("bi:metric:list")
    public Map<String,Object> portal(){return service.portal();}

    @GetMapping("/data-apis/callers") @BiPermission("bi:metric:publish")
    public List<Map<String,Object>> listCallers(@RequestParam(required=false,defaultValue="false") boolean activeOnly){
        return activeOnly ? service.listActiveCallers() : service.listCallers();
    }
    @PostMapping("/data-apis/callers") @BiPermission("bi:metric:publish")
    public Map<String,Object> createCaller(@RequestBody DataApiService.CallerSaveRequest request){return service.createCaller(request);}
    @PutMapping("/data-apis/callers/{callerId}") @BiPermission("bi:metric:publish")
    public Map<String,Object> updateCaller(@PathVariable Long callerId,@RequestBody DataApiService.CallerSaveRequest request){
        return service.updateCaller(callerId,request);
    }

    @GetMapping("/data-apis/{id}") @BiPermission("bi:metric:list")
    public Map<String,Object> get(@PathVariable Long id){return service.get(id);}
    @PostMapping("/data-apis") @BiPermission("bi:metric:publish")
    public Map<String,Object> create(@RequestBody DataApiService.SaveRequest request){return service.create(request);}
    @PutMapping("/data-apis/{id}") @BiPermission("bi:metric:publish")
    public Map<String,Object> update(@PathVariable Long id,@RequestBody DataApiService.SaveRequest request){return service.update(id,request);}
    @PostMapping("/data-apis/{id}/contract-check") @BiPermission("bi:metric:publish")
    public Map<String,Object> contractCheck(@PathVariable Long id){return service.contractCheck(id);}

    @PostMapping("/data-apis/{id}/contract-replay") @BiPermission("bi:metric:publish")
    public Map<String,Object> contractReplay(@PathVariable Long id){return service.contractReplay(id);}

    @PostMapping("/data-apis/{id}/contract-sample-replay") @BiPermission("bi:metric:publish")
    public Map<String,Object> contractSampleReplay(@PathVariable Long id){return service.contractSampleReplay(id);}

    @PostMapping("/data-apis/{id}/refresh-contract-sample") @BiPermission("bi:metric:publish")
    public Map<String,Object> refreshContractSample(@PathVariable Long id){return service.refreshContractSample(id);}

    @PostMapping("/data-apis/{id}/auto-deprecate-siblings") @BiPermission("bi:metric:publish")
    public Map<String,Object> autoDeprecateSiblings(@PathVariable Long id,
            @RequestBody(required=false) DataApiService.AutoDeprecateRequest request){
        return service.autoDeprecateSiblings(id, request);
    }

    @PostMapping("/data-apis/{id}/canary") @BiPermission("bi:metric:publish")
    public Map<String,Object> setCanary(@PathVariable Long id,
            @RequestBody(required=false) DataApiService.CanaryRequest request){
        return service.setCanary(id, request);
    }

    @PostMapping("/data-apis/{id}/publish") @BiPermission("bi:metric:publish")
    public Map<String,Object> publish(@PathVariable Long id){return service.publish(id);}

    @PostMapping("/data-apis/{id}/next-version") @BiPermission("bi:metric:publish")
    public Map<String,Object> nextVersion(@PathVariable Long id,
            @RequestBody(required=false) DataApiService.NextVersionRequest request){
        return service.nextVersion(id, request);
    }

    @PostMapping("/data-apis/{id}/keys/rotate") @BiPermission("bi:metric:publish")
    public Map<String,Object> rotate(@PathVariable Long id,
            @RequestParam(required=false) Integer expiresInDays,
            @RequestBody(required=false) DataApiService.RotateRequest body){
        if(body!=null) return service.rotate(id, body);
        return service.rotate(id, expiresInDays);
    }
    @PostMapping("/data-apis/{id}/keys/{keyId}/revoke") @BiPermission("bi:metric:publish")
    public Map<String,Object> revokeKey(@PathVariable Long id,@PathVariable Long keyId){return service.revokeKey(id,keyId);}
    @PostMapping("/data-apis/{id}/keys/{keyId}/caller") @BiPermission("bi:metric:publish")
    public Map<String,Object> assignCaller(@PathVariable Long id,@PathVariable Long keyId,
            @RequestBody(required=false) DataApiService.AssignCallerRequest request){
        return service.assignKeyCaller(id,keyId,request);
    }
    @PostMapping("/data-apis/{id}/disable") @BiPermission("bi:metric:publish")
    public Map<String,Object> disable(@PathVariable Long id){return service.disable(id);}

    @PostMapping("/data-apis/{id}/deprecate") @BiPermission("bi:metric:publish")
    public Map<String,Object> deprecate(@PathVariable Long id, @RequestBody(required = false) DataApiService.DeprecateRequest request) {
        return service.deprecate(id, request);
    }

    @PostMapping("/data-apis/{id}/undeprecate") @BiPermission("bi:metric:publish")
    public Map<String,Object> undeprecate(@PathVariable Long id) {
        return service.clearDeprecation(id);
    }

    @GetMapping("/data-apis/{id}/openapi") @BiPermission("bi:metric:list")
    public Map<String,Object> openApi(@PathVariable Long id){return service.openApiDoc(id);}

    @GetMapping("/data-apis/{id}/grants") @BiPermission("bi:metric:grant")
    public List<Map<String,Object>> grants(@PathVariable Long id){
        service.requireGrantAccess(id);
        return resources.grants("data_api", id);
    }

    public record GrantRequest(Long principalId, String action) { }

    @PostMapping("/data-apis/{id}/grants") @BiPermission("bi:metric:grant")
    public Map<String,Object> grant(@PathVariable Long id, @RequestBody GrantRequest request){
        service.requireGrantAccess(id);
        resources.change("data_api", id, request.principalId(), request.action(), true);
        return Map.of("granted", true);
    }

    @DeleteMapping("/data-apis/{id}/grants/{principalId}/{action}") @BiPermission("bi:metric:grant")
    public Map<String,Object> revoke(@PathVariable Long id, @PathVariable Long principalId, @PathVariable String action){
        service.requireGrantAccess(id);
        resources.change("data_api", id, principalId, action, false);
        return Map.of("revoked", true);
    }

    @PostMapping("/open-data/{code}") @BiApiKeyAccess
    public Map<String,Object> query(@PathVariable String code,@RequestHeader(name="X-Wanfo-Api-Key",required=false) String key,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int pageSize)
    {return service.execute(code,key,page,pageSize);}
}
