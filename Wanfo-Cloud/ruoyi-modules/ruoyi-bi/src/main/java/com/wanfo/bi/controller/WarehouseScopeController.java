package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.mapper.WarehouseScopeMapper;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.WarehouseScopeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bi/warehouse/scopes")
public class WarehouseScopeController
{
    public record BindingRequest(String modelVersion,String tenantField,String organizationField) { }
    public record MappingRequest(Long principalId,String tenantId,String warehouseOrganizationId) { }
    private final WarehouseScopeService scopes;
    private final CatalogAuditService audit;
    public WarehouseScopeController(WarehouseScopeService scopes,CatalogAuditService audit){this.scopes=scopes;this.audit=audit;}
    @GetMapping("/datasets/{datasetId}") @BiPermission("bi:resource:admin")
    public WarehouseScopeMapper.Binding getBinding(@PathVariable Long datasetId){return scopes.getBinding(datasetId);}
    @PutMapping("/datasets/{datasetId}") @BiPermission("bi:resource:admin")
    public WarehouseScopeMapper.Binding saveBinding(@PathVariable Long datasetId,@RequestBody BindingRequest body){var result=scopes.saveBinding(datasetId,body.modelVersion(),body.tenantField(),body.organizationField());audit.record("WAREHOUSE_SCOPE_BIND", "DATASET", datasetId, null, Map.of("modelVersion",result.modelVersion(),"tenantField",result.tenantField(),"organizationField",result.organizationField()));return result;}
    @DeleteMapping("/datasets/{datasetId}") @BiPermission("bi:resource:admin")
    public Map<String,Object> deleteBinding(@PathVariable Long datasetId){scopes.deleteBinding(datasetId);audit.record("WAREHOUSE_SCOPE_UNBIND", "DATASET", datasetId, null, Map.of());return Map.of("removed",true);}
    @GetMapping("/{modelVersion}/organizations") @BiPermission("bi:resource:admin")
    public List<WarehouseScopeMapper.OrganizationMapping> mappings(@PathVariable String modelVersion){return scopes.listMappings(modelVersion);}
    @PostMapping("/{modelVersion}/organizations") @BiPermission("bi:resource:admin")
    public List<WarehouseScopeMapper.OrganizationMapping> addMapping(@PathVariable String modelVersion,@RequestBody MappingRequest body){var result=scopes.addMapping(modelVersion,body.principalId(),body.tenantId(),body.warehouseOrganizationId());audit.record("WAREHOUSE_ORG_SCOPE_MAP", "WAREHOUSE_MODEL", null, null, Map.of("modelVersion",modelVersion,"principalId",body.principalId(),"tenantId",body.tenantId(),"warehouseOrganizationId",body.warehouseOrganizationId()));return result;}
    @DeleteMapping("/{modelVersion}/organizations/{id}") @BiPermission("bi:resource:admin")
    public Map<String,Object> deleteMapping(@PathVariable String modelVersion,@PathVariable Long id){scopes.deleteMapping(modelVersion,id);audit.record("WAREHOUSE_ORG_SCOPE_UNMAP", "WAREHOUSE_MODEL", id, null, Map.of("modelVersion",modelVersion));return Map.of("removed",true);}
}
