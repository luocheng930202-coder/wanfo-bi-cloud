package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SchemaChangeMapper
{
    @Select("SELECT source_id FROM dp_asset WHERE id=#{assetId} AND source_kind='BI_RESOURCE' AND asset_type='DATASET'")
    Long datasetIdForAsset(@Param("assetId") Long assetId);

    @Select("SELECT source_id FROM dp_asset WHERE id=#{assetId}")
    Long assetSourceId(@Param("assetId") Long assetId);

    @Select("SELECT id,asset_id AS assetId,previous_version_id AS previousVersionId,current_version_id AS currentVersionId,scan_run_id AS scanRunId,change_type AS changeType,details_json AS detailsJson,actor,observed_at AS observedAt FROM dp_schema_change_event WHERE asset_id=#{assetId} ORDER BY observed_at DESC,id DESC LIMIT 100")
    List<Map<String,Object>> events(@Param("assetId") Long assetId);

    @Select("SELECT id,asset_id AS assetId,previous_version_id AS previousVersionId,current_version_id AS currentVersionId,scan_run_id AS scanRunId,change_type AS changeType,details_json AS detailsJson,actor,observed_at AS observedAt FROM dp_schema_change_event WHERE id=#{id}")
    Map<String,Object> event(@Param("id") Long id);

    @Select("<script>SELECT rs.dataset_id AS datasetId,rs.id AS ruleSetId,rs.version_no AS ruleSetVersion,rs.name AS ruleSetName,rs.status AS ruleSetStatus,r.id AS ruleId,r.rule_code AS ruleCode,r.rule_type AS ruleType,r.field_name AS fieldName,r.severity,r.enabled FROM dp_quality_rule_set rs JOIN dp_quality_rule r ON r.rule_set_id=rs.id WHERE rs.dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY rs.dataset_id,rs.version_no DESC,r.id</script>")
    List<Map<String,Object>> qualityRules(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT f.id AS fieldId,f.dataset_id AS datasetId,f.origin_name AS fieldName,f.show_name AS displayName,f.origin_type AS fieldType,f.dim_flag AS dimFlag,f.ext_flag AS extFlag,f.del_flag AS delFlag FROM bi_dataset_field f WHERE f.dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY f.dataset_id,f.id</script>")
    List<Map<String,Object>> datasetFields(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT id,dataset_id AS datasetId,rule_set_id AS ruleSetId,name,status,schedule_type AS scheduleType FROM dp_quality_task WHERE dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY dataset_id,id</script>")
    List<Map<String,Object>> qualityTasks(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT p.id AS pipelineId,p.pipeline_code AS pipelineCode,p.name AS pipelineName,p.status AS pipelineStatus,r.id AS revisionId,r.revision_no AS revision,n.id AS nodeId,n.node_key AS nodeKey,n.name AS nodeName,CAST(JSON_UNQUOTE(JSON_EXTRACT(n.config_json,'$.datasetId')) AS UNSIGNED) AS datasetId FROM dp_pipeline p JOIN dp_pipeline_revision r ON r.id=p.current_revision_id JOIN dp_pipeline_node n ON n.revision_id=r.id WHERE n.node_type='SOURCE_DATASET' AND CAST(JSON_UNQUOTE(JSON_EXTRACT(n.config_json,'$.datasetId')) AS UNSIGNED) IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY p.id,n.id</script>")
    List<Map<String,Object>> pipelines(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT d.id AS datasetId,d.name AS datasetName,d.type AS datasetType,d.datasource_id AS datasourceId,d.source_table AS sourceTable,CASE WHEN d.model_json IS NOT NULL AND d.model_json!='' THEN 'JOIN_OR_MODEL' WHEN d.type='sql' THEN 'SQL' WHEN d.union_sql IS NOT NULL AND d.union_sql!='' THEN 'UNION_SQL' ELSE 'TABLE' END AS modelingKind FROM bi_dataset_table d WHERE d.del_flag='0' AND d.datasource_id=#{datasourceId} AND (d.type IN ('sql','union') OR (d.model_json IS NOT NULL AND d.model_json!='') OR (d.union_sql IS NOT NULL AND d.union_sql!='')) ORDER BY d.id LIMIT 200</script>")
    List<Map<String,Object>> joinSqlDatasets(@Param("datasourceId") Long datasourceId);

    @Select("SELECT id AS datasetId,name AS datasetName,type AS datasetType,source_table AS sourceTable FROM bi_dataset_table WHERE del_flag='0' AND datasource_id=#{datasourceId} AND LOWER(source_table)=LOWER(#{tableName}) ORDER BY id LIMIT 200")
    List<Map<String,Object>> physicalDatasets(@Param("datasourceId") Long datasourceId, @Param("tableName") String tableName);

    @Select("<script>SELECT id,code,name,dataset_id AS datasetId,publish_status AS publishStatus,version FROM bi_metric WHERE del_flag='0' AND dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id DESC LIMIT 500</script>")
    List<Map<String,Object>> metrics(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT id,name,dataset_id AS datasetId FROM bi_chart_view WHERE del_flag='0' AND definition_json IS NOT NULL AND dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id DESC LIMIT 500</script>")
    List<Map<String,Object>> charts(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT DISTINCT d.id AS dashboardId,d.name AS dashboardName,c.id AS chartId,c.dataset_id AS datasetId,CASE WHEN d.published_json IS NOT NULL THEN 1 ELSE 0 END AS published FROM bi_dashboard d JOIN bi_dashboard_binding b ON b.dashboard_id=d.id JOIN bi_chart_view c ON c.id=b.chart_id WHERE d.del_flag='0' AND c.del_flag='0' AND c.dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY d.id DESC LIMIT 500</script>")
    List<Map<String,Object>> dashboards(@Param("datasetIds") List<Long> datasetIds);

    @Select("<script>SELECT a.id,a.api_code AS apiCode,a.name,a.status,a.api_version AS apiVersion,a.metric_id AS metricId,a.sunset_at AS sunsetAt,m.dataset_id AS datasetId,m.code AS metricCode FROM dp_data_api a JOIN bi_metric m ON m.id=a.metric_id WHERE m.del_flag='0' AND m.dataset_id IN <foreach collection='datasetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY a.id DESC LIMIT 500</script>")
    List<Map<String,Object>> dataApis(@Param("datasetIds") List<Long> datasetIds);

    @Select("SELECT id,source_table AS sourceTable,target_database AS targetDatabase,target_table AS targetTable,status,promote_status AS promoteStatus,create_time AS createTime FROM dp_warehouse_load_run WHERE source_datasource_id=#{datasourceId} AND LOWER(source_table)=LOWER(#{tableName}) ORDER BY id DESC LIMIT 50")
    List<Map<String,Object>> warehouseLoads(@Param("datasourceId") Long datasourceId, @Param("tableName") String tableName);

    @Select("SELECT id,job_code AS jobCode,name,status,staging_table AS stagingTable,target_table AS targetTable,last_run_at AS lastRunAt,last_error AS lastError FROM dp_cdc_ingest_job WHERE source_datasource_id=#{datasourceId} AND (LOWER(staging_table)=LOWER(#{tableName}) OR LOWER(target_table)=LOWER(#{tableName})) ORDER BY id DESC LIMIT 50")
    List<Map<String,Object>> cdcJobs(@Param("datasourceId") Long datasourceId, @Param("tableName") String tableName);

    @Select("SELECT id,asset_type AS assetType,source_kind AS sourceKind,source_id AS sourceId,name,qualified_name AS qualifiedName,status FROM dp_asset WHERE id=#{id}")
    Map<String,Object> assetRow(@Param("id") Long id);
}
