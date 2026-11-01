package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LineageMapper
{
    @Select("SELECT id,asset_type AS assetType,status FROM dp_asset WHERE id=#{id}")
    Map<String,Object> asset(Long id);

    @Select("SELECT asset_id FROM dp_asset_version WHERE id=#{versionId}")
    Long versionOwner(Long versionId);

    @Select("SELECT current_version_id FROM dp_asset WHERE id=#{id}")
    Long currentVersion(Long id);

    @Select("SELECT id,edge_key AS edgeKey,source_asset_id AS sourceAssetId,target_asset_id AS targetAssetId,lineage_level AS lineageLevel,edge_kind AS edgeKind,source_method AS sourceMethod,confidence,source_version_id AS sourceVersionId,target_version_id AS targetVersionId,evidence_json AS evidenceJson,status,revision_no AS revisionNo,observed_at AS observedAt,create_by AS createBy,update_by AS updateBy,update_time AS updateTime FROM dp_lineage_edge WHERE id=#{id}")
    Map<String,Object> edge(Long id);

    @Select("SELECT id FROM dp_lineage_edge WHERE edge_key=#{edgeKey}")
    Long edgeIdByKey(String edgeKey);

    @Select("SELECT id,edge_key AS edgeKey,status FROM dp_lineage_edge WHERE source_method IN ('CATALOG','RESOURCE_BINDING') AND status='ACTIVE'")
    List<Map<String,Object>> generatedEdges();

    @Select("SELECT a.id,a.asset_type AS assetType,a.source_kind AS sourceKind,a.source_id AS sourceId,a.parent_id AS parentId,p.source_id AS parentSourceId,a.name,a.qualified_name AS qualifiedName,a.status,a.current_version_id AS currentVersionId,v.metadata_json AS metadataJson FROM dp_asset a LEFT JOIN dp_asset p ON p.id=a.parent_id LEFT JOIN dp_asset_version v ON v.id=a.current_version_id WHERE a.status IN ('ACTIVE','STALE') ORDER BY a.id LIMIT 10001")
    List<Map<String,Object>> lineageAssets();

    @Select("SELECT dashboard_id AS dashboardId,chart_id AS chartId,dataset_id AS datasetId,binding_kind AS bindingKind FROM bi_dashboard_binding b JOIN bi_dashboard d ON d.id=b.dashboard_id AND d.del_flag='0' WHERE d.del_flag='0' LIMIT 10001")
    List<Map<String,Object>> dashboardBindings();

    @Select("""
            SELECT CAST(SUBSTRING_INDEX(r.source_table,':',-1) AS UNSIGNED) AS datasetId,
                   m.id AS metricId,
                   r.target_datasource_id AS targetDatasourceId,
                   r.target_database AS targetDatabase,
                   r.target_table AS targetTable
            FROM dp_warehouse_load_run r
            LEFT JOIN bi_metric m ON m.dataset_id=CAST(SUBSTRING_INDEX(r.source_table,':',-1) AS UNSIGNED) AND m.del_flag='0'
            WHERE r.status='SUCCEEDED' AND r.load_mode IN ('DATASET','MODEL') AND r.source_table LIKE 'dataset:%'
            ORDER BY r.id DESC LIMIT 2000
            """)
    List<Map<String,Object>> warehousePublishBindings();

    @Select("<script>SELECT a.id AS id,a.asset_type AS assetType,a.source_kind AS sourceKind,a.source_id AS sourceId,a.parent_id AS parentId,p.source_id AS parentSourceId,a.name,a.qualified_name AS qualifiedName,a.status,a.current_version_id AS currentVersionId FROM dp_asset a LEFT JOIN dp_asset p ON p.id=a.parent_id WHERE a.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String,Object>> assets(@Param("ids") List<Long> ids);

    @Select("<script>SELECT e.id,e.edge_key AS edgeKey,e.source_asset_id AS sourceAssetId,e.target_asset_id AS targetAssetId,e.lineage_level AS lineageLevel,e.edge_kind AS edgeKind,e.source_method AS sourceMethod,e.confidence,e.source_version_id AS sourceVersionId,e.target_version_id AS targetVersionId,e.status,CASE WHEN e.status='ACTIVE' AND ((e.source_version_id IS NOT NULL AND e.source_version_id&lt;&gt;s.current_version_id) OR (e.target_version_id IS NOT NULL AND e.target_version_id&lt;&gt;t.current_version_id)) THEN 'STALE' ELSE e.status END AS effectiveStatus FROM dp_lineage_edge e JOIN dp_asset s ON s.id=e.source_asset_id JOIN dp_asset t ON t.id=e.target_asset_id WHERE e.status&lt;&gt;'REVOKED' AND (e.source_asset_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> OR e.target_asset_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>) ORDER BY e.id LIMIT #{limit}</script>")
    List<Map<String,Object>> touchingEdges(@Param("ids") List<Long> ids,@Param("limit") int limit);

    @Select("<script>SELECT id,edge_key AS edgeKey,source_asset_id AS sourceAssetId,target_asset_id AS targetAssetId,lineage_level AS lineageLevel,edge_kind AS edgeKind,source_method AS sourceMethod,confidence,source_version_id AS sourceVersionId,target_version_id AS targetVersionId,evidence_json AS evidenceJson,status,revision_no AS revisionNo,observed_at AS observedAt,create_by AS createBy,update_by AS updateBy,update_time AS updateTime FROM dp_lineage_edge WHERE (source_asset_id=#{assetId} OR target_asset_id=#{assetId}) <if test='activeOnly'>AND status='ACTIVE'</if> ORDER BY id DESC LIMIT 500</script>")
    List<Map<String,Object>> edges(@Param("assetId") Long assetId,@Param("activeOnly") boolean activeOnly);

    @Select("SELECT id,edge_id AS edgeId,revision_no AS revisionNo,edge_snapshot_json AS snapshot,change_kind AS changeKind,changed_by AS changedBy,changed_at AS changedAt FROM dp_lineage_edge_revision WHERE edge_id=#{edgeId} ORDER BY revision_no DESC LIMIT 100")
    List<Map<String,Object>> revisions(Long edgeId);

    @Insert("INSERT INTO dp_lineage_edge(edge_key,source_asset_id,target_asset_id,lineage_level,edge_kind,source_method,confidence,source_version_id,target_version_id,evidence_json,status,revision_no,observed_at,create_by,update_by) VALUES(#{edgeKey},#{sourceAssetId},#{targetAssetId},#{lineageLevel},#{edgeKind},#{sourceMethod},#{confidence},#{sourceVersionId},#{targetVersionId},#{evidenceJson},'ACTIVE',1,CURRENT_TIMESTAMP(3),#{actor},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertEdge(Map<String,Object> row);

    @Update("UPDATE dp_lineage_edge SET lineage_level=#{lineageLevel},edge_kind=#{edgeKind},source_method=#{sourceMethod},confidence=#{confidence},source_version_id=#{sourceVersionId},target_version_id=#{targetVersionId},evidence_json=#{evidenceJson},status='ACTIVE',revision_no=revision_no+1,observed_at=CURRENT_TIMESTAMP(3),update_by=#{actor} WHERE id=#{id}")
    int updateEdge(Map<String,Object> row);

    @Update("UPDATE dp_lineage_edge SET status=#{status},revision_no=revision_no+1,observed_at=CURRENT_TIMESTAMP(3),update_by=#{actor} WHERE id=#{id} AND status<>'REVOKED'")
    int updateStatus(@Param("id") Long id,@Param("status") String status,@Param("actor") String actor);

    @Insert("INSERT INTO dp_lineage_edge_revision(edge_id,revision_no,edge_snapshot_json,change_kind,changed_by) VALUES(#{edgeId},#{revisionNo},#{snapshot},#{changeKind},#{actor})")
    int insertRevision(@Param("edgeId") Long edgeId,@Param("revisionNo") Integer revisionNo,@Param("snapshot") String snapshot,@Param("changeKind") String changeKind,@Param("actor") String actor);
}
