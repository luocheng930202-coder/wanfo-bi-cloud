package com.wanfo.bi.service;

import java.util.*;
import java.util.regex.Pattern;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.WarehouseScopeMapper;
import com.wanfo.bi.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit bridge between host organization principals and warehouse row keys. */
@Service
public class WarehouseScopeService
{
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private final WarehouseScopeMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final BiDatasetFieldMapper fields;
    private final OrganizationHierarchyProvider hierarchy;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;

    public WarehouseScopeService(WarehouseScopeMapper mapper, BiDatasetTableMapper datasets, BiDatasetFieldMapper fields,
            OrganizationHierarchyProvider hierarchy, CapabilityProvider capabilities, CurrentActorProvider actors) {
        this.mapper=mapper;this.datasets=datasets;this.fields=fields;this.hierarchy=hierarchy;this.capabilities=capabilities;this.actors=actors;
    }

    public DatasetDataPolicy.Scope apply(Long datasetId, BiActor actor, DatasetDataPolicy.Scope scope) {
        WarehouseScopeMapper.Binding binding=mapper.binding(datasetId);
        if(binding==null)return scope;
        requireBoundFields(datasetId,binding.tenantField(),binding.organizationField());
        List<Long> subtree=actor.organizationId()==null?List.of():hierarchy.activeSubtree(actor.organizationId());
        if(subtree.isEmpty())return withEmptyOrganizationScope(scope,binding.organizationField());
        List<WarehouseScopeMapper.OrganizationMapping> rows=mapper.mappingsForPrincipals(binding.modelVersion(),subtree);
        if(rows.isEmpty())return withEmptyOrganizationScope(scope,binding.organizationField());
        Set<String> tenants=new LinkedHashSet<>(),orgs=new LinkedHashSet<>();
        for(var row:rows){if(row.tenantId()!=null&&!row.tenantId().isBlank())tenants.add(row.tenantId());if(row.warehouseOrganizationId()!=null&&!row.warehouseOrganizationId().isBlank())orgs.add(row.warehouseOrganizationId());}
        if(tenants.size()!=1||orgs.isEmpty())return withEmptyOrganizationScope(scope,binding.organizationField());
        Map<String,List<String>> values=new LinkedHashMap<>(scope.allowedValues());
        values.put(binding.tenantField(),intersect(values.get(binding.tenantField()),List.of(tenants.iterator().next())));
        values.put(binding.organizationField(),intersect(values.get(binding.organizationField()),new ArrayList<>(orgs)));
        return new DatasetDataPolicy.Scope(scope.rows(),scope.denied(),scope.masked(),values);
    }

    private static DatasetDataPolicy.Scope withEmptyOrganizationScope(DatasetDataPolicy.Scope scope,String field){
        Map<String,List<String>> values=new LinkedHashMap<>(scope.allowedValues());values.put(field,List.of());
        return new DatasetDataPolicy.Scope(scope.rows(),scope.denied(),scope.masked(),values);
    }
    private static List<String> intersect(List<String> first,List<String> second){
        if(first==null)return List.copyOf(second);Set<String> allowed=new HashSet<>(second);
        return first.stream().filter(allowed::contains).distinct().toList();
    }

    public WarehouseScopeMapper.Binding getBinding(Long datasetId){requireAdmin();requireTableDataset(datasetId);return mapper.binding(datasetId);}
    @Transactional
    public WarehouseScopeMapper.Binding saveBinding(Long datasetId,String modelVersion,String tenantField,String organizationField){
        requireAdmin();requireTableDataset(datasetId);
        if(modelVersion==null||!MODEL.matcher(modelVersion.trim()).matches()||!validIdentifier(tenantField)||!validIdentifier(organizationField)||tenantField.equals(organizationField))
            throw new IllegalArgumentException("请填写有效模型版本、租户字段和组织字段");
        requireBoundFields(datasetId,tenantField,organizationField);
        mapper.saveBinding(datasetId,modelVersion.trim(),tenantField,organizationField,actors.currentActor().username());
        return mapper.binding(datasetId);
    }
    @Transactional public void deleteBinding(Long datasetId){requireAdmin();requireTableDataset(datasetId);mapper.deleteBinding(datasetId);}
    public List<WarehouseScopeMapper.OrganizationMapping> listMappings(String modelVersion){requireAdmin();validateModel(modelVersion);return mapper.mappings(modelVersion);}
    @Transactional
    public List<WarehouseScopeMapper.OrganizationMapping> addMapping(String modelVersion,Long principalId,String tenantId,String warehouseOrganizationId){
        requireAdmin();validateModel(modelVersion);
        if(principalId==null||!"organization".equals(mapper.organizationPrincipalType(principalId))||tenantId==null||tenantId.isBlank()||tenantId.length()>128||warehouseOrganizationId==null||warehouseOrganizationId.isBlank()||warehouseOrganizationId.length()>128)
            throw new IllegalArgumentException("请选择若依组织主体并填写租户 ID、数仓组织 ID");
        if(!mapper.mappingsForPrincipal(modelVersion,principalId).isEmpty())throw new IllegalArgumentException("该模型版本下此若依组织已配置映射");
        mapper.addMapping(new WarehouseScopeMapper.OrganizationMapping(null,modelVersion,principalId,tenantId.trim(),warehouseOrganizationId.trim()),actors.currentActor().username());
        return mapper.mappings(modelVersion);
    }
    @Transactional public void deleteMapping(String modelVersion,Long id){requireAdmin();validateModel(modelVersion);mapper.deleteMapping(modelVersion,id);}

    private void requireBoundFields(Long datasetId,String tenantField,String orgField){
        Set<String> names=new HashSet<>();for(BiDatasetFieldDO field:fields.selectByDatasetId(datasetId))names.add(field.getOriginName());
        if(!names.contains(tenantField)||!names.contains(orgField))throw new BiAccessException(403,"数仓权限字段不存在或已被数据集移除，请先修复权限绑定");
    }
    private void requireTableDataset(Long id){BiDatasetTableDO row=datasets.selectById(id);if(row==null||!"table".equals(row.getType()))throw new IllegalArgumentException("仅支持物理表数据集绑定数仓组织范围");}
    private void requireAdmin(){capabilities.require("bi:resource:admin");}
    private static boolean validIdentifier(String value){return value!=null&&IDENTIFIER.matcher(value).matches();}
    private static void validateModel(String value){if(value==null||!MODEL.matcher(value).matches())throw new IllegalArgumentException("模型版本格式无效");}
}
