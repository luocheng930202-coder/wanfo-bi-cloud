package com.wanfo.bi.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WarehouseScopeMapper
{
    record Binding(Long datasetId, String modelVersion, String tenantField, String organizationField) { }
    record OrganizationMapping(Long id, String modelVersion, Long principalId, String tenantId, String warehouseOrganizationId) { }

    @Select("SELECT dataset_id AS datasetId,model_version AS modelVersion,tenant_field AS tenantField,organization_field AS organizationField FROM dp_dataset_scope_binding WHERE dataset_id=#{datasetId}")
    Binding binding(Long datasetId);

    @Select("SELECT principal_type FROM bi_principal WHERE id=#{id} AND source='ruoyi' AND principal_type='organization'")
    String organizationPrincipalType(Long id);

    @Select("SELECT tenant_id AS tenantId,warehouse_org_id AS warehouseOrganizationId FROM dp_warehouse_org_scope_mapping WHERE model_version=#{modelVersion} AND principal_id=#{principalId}")
    List<OrganizationMapping> mappingsForPrincipal(@Param("modelVersion") String modelVersion,@Param("principalId") Long principalId);

    @Insert("INSERT INTO dp_dataset_scope_binding(dataset_id,model_version,tenant_field,organization_field,update_by) VALUES(#{datasetId},#{modelVersion},#{tenantField},#{organizationField},#{updateBy}) ON DUPLICATE KEY UPDATE model_version=VALUES(model_version),tenant_field=VALUES(tenant_field),organization_field=VALUES(organization_field),update_by=VALUES(update_by),update_time=CURRENT_TIMESTAMP(3)")
    int saveBinding(@Param("datasetId") Long datasetId,@Param("modelVersion") String modelVersion,@Param("tenantField") String tenantField,@Param("organizationField") String organizationField,@Param("updateBy") String updateBy);

    @Delete("DELETE FROM dp_dataset_scope_binding WHERE dataset_id=#{datasetId}")
    int deleteBinding(Long datasetId);

    @Select("SELECT id,model_version AS modelVersion,principal_id AS principalId,tenant_id AS tenantId,warehouse_org_id AS warehouseOrganizationId FROM dp_warehouse_org_scope_mapping WHERE model_version=#{modelVersion} ORDER BY tenant_id,principal_id,warehouse_org_id")
    List<OrganizationMapping> mappings(String modelVersion);

    @Select("<script>SELECT id,model_version AS modelVersion,principal_id AS principalId,tenant_id AS tenantId,warehouse_org_id AS warehouseOrganizationId FROM dp_warehouse_org_scope_mapping WHERE model_version=#{modelVersion} AND principal_id IN <foreach collection='principalIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<OrganizationMapping> mappingsForPrincipals(@Param("modelVersion") String modelVersion,@Param("principalIds") List<Long> principalIds);

    @Insert("INSERT INTO dp_warehouse_org_scope_mapping(model_version,principal_id,tenant_id,warehouse_org_id,create_by) VALUES(#{mapping.modelVersion},#{mapping.principalId},#{mapping.tenantId},#{mapping.warehouseOrganizationId},#{createBy})")
    int addMapping(@Param("mapping") OrganizationMapping mapping,@Param("createBy") String createBy);

    @Delete("DELETE FROM dp_warehouse_org_scope_mapping WHERE model_version=#{modelVersion} AND id=#{id}")
    int deleteMapping(@Param("modelVersion") String modelVersion,@Param("id") Long id);
}
