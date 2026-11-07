package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BiMetadataScanMapper
{
    @Select("SELECT r.id,r.scan_id AS scanId,r.datasource_id AS datasourceId,s.name AS taskName,d.name AS resourceName,r.status,r.progress_total AS progressTotal,r.progress_completed AS progressCompleted,r.success_count AS successCount,r.failure_count AS failureCount,r.error_summary AS message,r.create_time AS createTime,r.started_at AS startedAt,r.finished_at AS finishedAt FROM dp_metadata_scan_run r JOIN dp_metadata_scan s ON s.id=r.scan_id JOIN bi_datasource d ON d.id=r.datasource_id AND d.del_flag='0' ORDER BY r.id DESC LIMIT 100")
    List<Map<String,Object>> recentRuns();
    @Insert("INSERT INTO dp_catalog_audit(actor_id,actor_name,action,subject_type,subject_id,source_id,outcome,trace_id,summary_json) VALUES(#{actorId},#{actorName},#{action},#{subjectType},#{subjectId},#{sourceId},#{outcome},#{traceId},#{summary})")
    int insertCatalogAudit(Map<String, Object> audit);

    String AUDIT_UNION="SELECT CONCAT('catalog:',id) AS id,actor_id AS actorId,actor_name AS actorName,action,subject_type AS subjectType,CAST(subject_id AS CHAR) AS subjectId,source_id AS sourceId,outcome,trace_id AS traceId,summary_json AS summaryJson,created_at AS createdAt FROM dp_catalog_audit UNION ALL SELECT CONCAT('archive:',id) AS id,actor_id AS actorId,actor_name AS actorName,action,subject_type AS subjectType,CAST(subject_id AS CHAR) AS subjectId,source_id AS sourceId,outcome,trace_id AS traceId,summary_json AS summaryJson,created_at AS createdAt FROM dp_audit_archive WHERE source_table='dp_catalog_audit' UNION ALL SELECT CONCAT('subscription:',a.id) AS id,a.owner_principal AS actorId,CAST(a.owner_principal AS CHAR) AS actorName,'REPORT_SUBSCRIPTION_DELIVERY' AS action,'REPORT_SUBSCRIPTION' AS subjectType,CAST(a.subscription_id AS CHAR) AS subjectId,a.source_id AS sourceId,CASE WHEN UPPER(a.status) IN ('SUCCESS','SUCCEEDED','SENT') THEN 'SUCCESS' ELSE 'FAILED' END AS outcome,NULL AS traceId,JSON_OBJECT('attempt',a.attempt,'rowCount',a.row_count,'recipientCount',a.recipient_count,'message',a.message) AS summaryJson,a.finished_at AS createdAt FROM bi_report_subscription_audit a UNION ALL SELECT CONCAT('archive-subscription:',id) AS id,actor_id AS actorId,actor_name AS actorName,action,subject_type AS subjectType,CAST(subject_id AS CHAR) AS subjectId,source_id AS sourceId,outcome,trace_id AS traceId,summary_json AS summaryJson,created_at AS createdAt FROM dp_audit_archive WHERE source_table='bi_report_subscription_audit' UNION ALL SELECT CONCAT('api-call:',c.id) AS id,c.api_key_id AS actorId,COALESCE(CONCAT('API Key ',k.key_prefix),'API Key (deleted)') AS actorName,'DATA_API_CALL' AS action,'DATA_API' AS subjectType,CAST(c.api_id AS CHAR) AS subjectId,m.metric_id AS sourceId,c.status AS outcome,NULL AS traceId,JSON_OBJECT('durationMs',c.duration_ms,'rowCount',c.row_count,'errorCode',c.error_code) AS summaryJson,c.called_at AS createdAt FROM dp_data_api_call c LEFT JOIN dp_data_api_key k ON k.id=c.api_key_id LEFT JOIN dp_data_api m ON m.id=c.api_id UNION ALL SELECT CONCAT('archive-api-call:',id) AS id,actor_id AS actorId,actor_name AS actorName,action,subject_type AS subjectType,CAST(subject_id AS CHAR) AS subjectId,source_id AS sourceId,outcome,trace_id AS traceId,summary_json AS summaryJson,created_at AS createdAt FROM dp_audit_archive WHERE source_table='dp_data_api_call'";

    @Select("<script>SELECT * FROM ("+AUDIT_UNION+") audit_rows WHERE 1=1 <if test='actor != null and actor.length() > 0'>AND actorName LIKE CONCAT('%',#{actor},'%')</if> <if test='action != null and action.length() > 0'><choose><when test='action.endsWith(\"_\")'>AND action LIKE CONCAT(#{action},'%')</when><otherwise>AND action=#{action}</otherwise></choose></if> <if test='outcome != null and outcome.length() > 0'>AND outcome=#{outcome}</if> <if test='fromAt != null'>AND createdAt &gt;= #{fromAt}</if> <if test='toAt != null'>AND createdAt &lt; #{toAt}</if> ORDER BY createdAt DESC,id DESC LIMIT #{offset},#{pageSize}</script>")
    List<Map<String,Object>> catalogAuditPage(@Param("offset") int offset,@Param("pageSize") int pageSize,@Param("actor") String actor,@Param("action") String action,@Param("outcome") String outcome,@Param("fromAt") LocalDateTime fromAt,@Param("toAt") LocalDateTime toAt);

    @Select("<script>SELECT COUNT(*) FROM ("+AUDIT_UNION+") audit_rows WHERE 1=1 <if test='actor != null and actor.length() > 0'>AND actorName LIKE CONCAT('%',#{actor},'%')</if> <if test='action != null and action.length() > 0'><choose><when test='action.endsWith(\"_\")'>AND action LIKE CONCAT(#{action},'%')</when><otherwise>AND action=#{action}</otherwise></choose></if> <if test='outcome != null and outcome.length() > 0'>AND outcome=#{outcome}</if> <if test='fromAt != null'>AND createdAt &gt;= #{fromAt}</if> <if test='toAt != null'>AND createdAt &lt; #{toAt}</if></script>")
    long countCatalogAudit(@Param("actor") String actor,@Param("action") String action,@Param("outcome") String outcome,@Param("fromAt") LocalDateTime fromAt,@Param("toAt") LocalDateTime toAt);

    @Select("SELECT live_retention_days AS liveRetentionDays,updated_by AS updatedBy,updated_at AS updatedAt FROM dp_audit_retention_policy WHERE policy_key='global'")
    Map<String,Object> auditRetentionPolicy();

    @Select("SELECT live_retention_days AS liveRetentionDays,updated_by AS updatedBy,updated_at AS updatedAt FROM dp_audit_retention_policy WHERE policy_key=#{policyKey}")
    Map<String,Object> auditRetentionPolicyByKey(@Param("policyKey") String policyKey);

    @Update("UPDATE dp_audit_retention_policy SET live_retention_days=#{days},updated_by=#{actor} WHERE policy_key='global'")
    int updateAuditRetention(@Param("days") int days,@Param("actor") String actor);

    @Insert("INSERT INTO dp_audit_retention_policy(policy_key,live_retention_days,updated_by) VALUES(#{policyKey},#{days},#{actor}) ON DUPLICATE KEY UPDATE live_retention_days=#{days},updated_by=#{actor}")
    int upsertAuditRetention(@Param("policyKey") String policyKey,@Param("days") int days,@Param("actor") String actor);

    @Insert("INSERT IGNORE INTO dp_audit_archive(source_table,source_record_id,actor_id,actor_name,action,subject_type,subject_id,source_id,outcome,trace_id,summary_json,created_at) SELECT 'dp_catalog_audit',id,actor_id,actor_name,action,subject_type,subject_id,source_id,outcome,trace_id,summary_json,created_at FROM dp_catalog_audit WHERE created_at < #{cutoff} ORDER BY id LIMIT #{batchSize}")
    int archiveOldCatalogAudit(@Param("cutoff") LocalDateTime cutoff,@Param("batchSize") int batchSize);

    @Insert("INSERT IGNORE INTO dp_audit_archive(source_table,source_record_id,actor_id,actor_name,action,subject_type,subject_id,source_id,outcome,trace_id,summary_json,created_at) SELECT 'bi_report_subscription_audit',id,owner_principal,CAST(owner_principal AS CHAR),'REPORT_SUBSCRIPTION_DELIVERY','REPORT_SUBSCRIPTION',subscription_id,source_id,CASE WHEN UPPER(status) IN ('SUCCESS','SUCCEEDED','SENT') THEN 'SUCCESS' ELSE 'FAILED' END,NULL,JSON_OBJECT('attempt',attempt,'rowCount',row_count,'recipientCount',recipient_count,'message',message),finished_at FROM bi_report_subscription_audit WHERE finished_at < #{cutoff} ORDER BY id LIMIT #{batchSize}")
    int archiveOldSubscriptionAudit(@Param("cutoff") LocalDateTime cutoff,@Param("batchSize") int batchSize);

    @Insert("INSERT IGNORE INTO dp_audit_archive(source_table,source_record_id,actor_id,actor_name,action,subject_type,subject_id,source_id,outcome,trace_id,summary_json,created_at) SELECT 'dp_data_api_call',c.id,c.api_key_id,COALESCE(CONCAT('API Key ',k.key_prefix),'API Key (deleted)'),'DATA_API_CALL','DATA_API',c.api_id,m.metric_id,c.status,NULL,JSON_OBJECT('durationMs',c.duration_ms,'rowCount',c.row_count,'errorCode',c.error_code),c.called_at FROM dp_data_api_call c LEFT JOIN dp_data_api_key k ON k.id=c.api_key_id LEFT JOIN dp_data_api m ON m.id=c.api_id WHERE c.called_at < #{cutoff} ORDER BY c.id LIMIT #{batchSize}")
    int archiveOldDataApiAudit(@Param("cutoff") LocalDateTime cutoff,@Param("batchSize") int batchSize);

    @Delete("DELETE a FROM dp_catalog_audit a JOIN (SELECT source_record_id FROM dp_audit_archive WHERE source_table='dp_catalog_audit' ORDER BY source_record_id LIMIT #{batchSize}) archived ON archived.source_record_id=a.id")
    int purgeArchivedCatalogAudit(@Param("batchSize") int batchSize);

    @Delete("DELETE a FROM bi_report_subscription_audit a JOIN (SELECT source_record_id FROM dp_audit_archive WHERE source_table='bi_report_subscription_audit' ORDER BY source_record_id LIMIT #{batchSize}) archived ON archived.source_record_id=a.id")
    int purgeArchivedSubscriptionAudit(@Param("batchSize") int batchSize);

    @Delete("DELETE c FROM dp_data_api_call c JOIN (SELECT source_record_id FROM dp_audit_archive WHERE source_table='dp_data_api_call' ORDER BY source_record_id LIMIT #{batchSize}) archived ON archived.source_record_id=c.id")
    int purgeArchivedDataApiAudit(@Param("batchSize") int batchSize);

    @Select("SELECT COUNT(*) FROM dp_audit_archive WHERE source_table='dp_catalog_audit'")
    long countArchivedCatalogAudit();

    @Select("SELECT d.id,d.name,d.type,d.schema_name AS schemaName,d.status,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='datasource' AND r.resource_id=d.id) AS ownerPrincipalId FROM bi_datasource d WHERE d.del_flag='0' ORDER BY d.id")
    List<Map<String, Object>> backfillDataSources();

    @Select("SELECT d.id,d.id AS datasourceId,d.name,d.type,d.schema_name AS schemaName,d.status,s.kind AS snapshotKind,s.row_count AS rowCount,s.version AS snapshotVersion,CAST(s.refreshed_at AS CHAR) AS refreshedAt,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='datasource' AND r.resource_id=d.id) AS ownerPrincipalId FROM bi_datasource d LEFT JOIN bi_source_snapshot s ON s.datasource_id=d.id WHERE d.del_flag='0' AND d.type='api' ORDER BY d.id")
    List<Map<String, Object>> backfillApiSources();

    @Select("SELECT d.id,d.name,d.type,d.datasource_id AS datasourceId,d.source_table AS sourceTable,d.remark,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='dataset' AND r.resource_id=d.id) AS ownerPrincipalId FROM bi_dataset_table d WHERE d.del_flag='0' ORDER BY d.id")
    List<Map<String, Object>> backfillDatasets();

    @Select("SELECT f.id,f.dataset_id AS datasetId,f.origin_name AS name,f.origin_type AS type,f.show_name AS showName,f.dim_flag AS dimFlag,f.ext_flag AS extFlag,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='dataset' AND r.resource_id=d.id) AS ownerPrincipalId FROM bi_dataset_field f JOIN bi_dataset_table d ON d.id=f.dataset_id AND d.del_flag='0' WHERE f.del_flag='0' ORDER BY f.dataset_id,f.id")
    List<Map<String, Object>> backfillDatasetFields();

    @Select("SELECT c.id,c.name,c.type,c.dataset_id AS datasetId,c.dashboard_id AS dashboardId,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='chart' AND r.resource_id=c.id) AS ownerPrincipalId FROM bi_chart_view c WHERE c.del_flag='0' AND c.definition_json IS NOT NULL ORDER BY c.id")
    List<Map<String, Object>> backfillCharts();

    @Select("SELECT d.id,d.name,d.type,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='dashboard' AND r.resource_id=d.id) AS ownerPrincipalId FROM bi_dashboard d WHERE d.del_flag='0' AND d.definition_json IS NOT NULL ORDER BY d.id")
    List<Map<String, Object>> backfillDashboards();

    @Select("SELECT m.id,m.code,m.name,m.kind,m.dataset_id AS datasetId,m.publish_status AS publishStatus,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='metric' AND r.resource_id=m.id) AS ownerPrincipalId FROM bi_metric m WHERE m.del_flag='0' ORDER BY m.id")
    List<Map<String, Object>> backfillMetrics();

    @Select("SELECT a.id,a.name,a.metric_id AS metricId,a.status AS publishStatus,a.api_code AS code,(SELECT r.owner_id FROM bi_resource r WHERE r.resource_type='data_api' AND r.resource_id=a.id) AS ownerPrincipalId FROM dp_data_api a WHERE a.status IN ('ACTIVE','DEPRECATED') ORDER BY a.id")
    List<Map<String, Object>> backfillDataApis();

    @Insert("INSERT INTO dp_asset(asset_type,source_kind,source_id,urn,urn_hash,parent_id,name,qualified_name,description,owner_principal_id,status,last_seen_at,create_by,update_by) VALUES(#{type},'BI_RESOURCE',#{sourceId},#{urn},#{urnHash},#{parentId},#{name},#{qualifiedName},#{description},#{ownerPrincipalId},'ACTIVE',NOW(),#{actor},#{actor}) ON DUPLICATE KEY UPDATE source_id=VALUES(source_id),parent_id=VALUES(parent_id),name=VALUES(name),qualified_name=VALUES(qualified_name),description=VALUES(description),owner_principal_id=VALUES(owner_principal_id),status='ACTIVE',last_seen_at=NOW(),update_by=VALUES(update_by)")
    int upsertBackfillAsset(Map<String, Object> asset);

    @Insert("INSERT INTO dp_metadata_scan(datasource_id,name,scope_json,status,create_by,update_by) VALUES(#{sourceId},#{name},#{scope},#{status},#{actor},#{actor})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDefinition(Map<String, Object> command);

    @Select("SELECT id FROM bi_principal WHERE source='ruoyi' AND principal_type='user' AND external_id='1' LIMIT 1")
    Long platformAdminPrincipalId();

    @Select("SELECT id,datasource_id AS datasourceId,name,scope_json AS scopeJson,status,create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime FROM dp_metadata_scan WHERE id=#{id}")
    Map<String, Object> definition(@Param("id") Long id);

    @Select("SELECT id,datasource_id AS datasourceId FROM dp_metadata_scan WHERE status='0' ORDER BY id LIMIT 200")
    List<Map<String, Object>> enabledScans();

    @Select("SELECT MAX(finished_at) FROM dp_metadata_scan_run WHERE scan_id=#{scanId} AND status IN ('SUCCEEDED','SUCCESS')")
    LocalDateTime lastSucceededAt(@Param("scanId") Long scanId);

    @Select("SELECT id,datasource_id AS datasourceId,name,scope_json AS scopeJson,status,create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime FROM dp_metadata_scan WHERE datasource_id=#{sourceId} ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> definitions(@Param("sourceId") Long sourceId);

    @Update("UPDATE dp_metadata_scan SET name=#{name},scope_json=#{scope},status=#{status},update_by=#{actor} WHERE id=#{id}")
    int updateDefinition(@Param("id") Long id, @Param("name") String name,
                         @Param("scope") String scope, @Param("status") String status,
                         @Param("actor") String actor);

    @Delete("DELETE FROM dp_metadata_scan WHERE id=#{id}")
    int deleteDefinition(@Param("id") Long id);

    @Select("SELECT id FROM bi_datasource WHERE id=#{sourceId} AND del_flag='0' FOR UPDATE")
    Long lockDatasource(@Param("sourceId") Long sourceId);

    @Select("SELECT COUNT(*) FROM dp_metadata_scan_run WHERE datasource_id=#{sourceId} AND status IN ('PENDING','RUNNING')")
    int countActiveRuns(@Param("sourceId") Long sourceId);

    @Select("SELECT COUNT(*) FROM dp_metadata_scan_run WHERE status IN ('PENDING','RUNNING')")
    int activeGlobalCount();

    @Insert("INSERT INTO dp_metadata_scan_run(scan_id,datasource_id,trigger_type,trigger_by,status,trace_id) VALUES(#{scanId},#{sourceId},'MANUAL',#{actor},'PENDING',#{traceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(Map<String, Object> command);

    @Select("SELECT id,scan_id AS scanId,datasource_id AS datasourceId,trigger_type AS triggerType,trigger_by AS triggerBy,status,progress_total AS progressTotal,progress_completed AS progressCompleted,success_count AS successCount,failure_count AS failureCount,error_summary AS errorSummary,result_summary_json AS resultSummaryJson,trace_id AS traceId,started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime FROM dp_metadata_scan_run WHERE id=#{id}")
    Map<String, Object> run(@Param("id") Long id);

    @Select("SELECT id,scan_id AS scanId,datasource_id AS datasourceId,trigger_type AS triggerType,trigger_by AS triggerBy,status,progress_total AS progressTotal,progress_completed AS progressCompleted,success_count AS successCount,failure_count AS failureCount,error_summary AS errorSummary,result_summary_json AS resultSummaryJson,trace_id AS traceId,started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime FROM dp_metadata_scan_run WHERE datasource_id=#{sourceId} ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> runs(@Param("sourceId") Long sourceId);

    @Update("UPDATE dp_metadata_scan_run SET status='RUNNING',started_at=NOW() WHERE id=#{id} AND status='PENDING'")
    int startRun(@Param("id") Long id);

    @Select("SELECT NOW()")
    java.time.LocalDateTime databaseNow();

    @Update("UPDATE dp_metadata_scan_run SET progress_total=#{total},progress_completed=#{completed},success_count=#{success},failure_count=#{failure},result_summary_json=#{summary},status=#{status},finished_at=NOW(),error_summary=#{error} WHERE id=#{id}")
    int finishRun(@Param("id") Long id, @Param("total") int total, @Param("completed") int completed,
                  @Param("success") int success, @Param("failure") int failure,
                  @Param("summary") String summary, @Param("status") String status,
                  @Param("error") String error);

    @Update("UPDATE dp_metadata_scan_run SET progress_total=#{total},progress_completed=#{completed},success_count=#{success},failure_count=#{failure} WHERE id=#{id} AND status='RUNNING'")
    int progress(@Param("id") Long id, @Param("total") int total, @Param("completed") int completed,
                 @Param("success") int success, @Param("failure") int failure);

    @Update("UPDATE dp_metadata_scan_run SET status='FAILED',error_summary=#{error},finished_at=NOW() WHERE id=#{id} AND status IN ('PENDING','RUNNING')")
    int failRun(@Param("id") Long id, @Param("error") String error);

    @Insert("INSERT INTO dp_asset(asset_type,source_kind,source_id,urn,urn_hash,parent_id,name,qualified_name,description,status,last_seen_at,create_by,update_by) VALUES(#{type},'CONNECTOR',#{sourceId},#{urn},#{urnHash},#{parentId},#{name},#{qualifiedName},#{description},'ACTIVE',NOW(),#{actor},#{actor}) ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),qualified_name=VALUES(qualified_name),description=VALUES(description),status='ACTIVE',last_seen_at=NOW(),update_by=VALUES(update_by)")
    int upsertAsset(Map<String, Object> asset);

    @Select("SELECT id,current_version_id AS currentVersionId,status FROM dp_asset WHERE urn_hash=#{urnHash}")
    Map<String, Object> assetByHash(@Param("urnHash") String urnHash);

    @Select("SELECT a.id,a.asset_type AS assetType,a.source_kind AS sourceKind,a.source_id AS sourceId,a.parent_id AS parentId,p.source_id AS parentSourceId,a.name,a.qualified_name AS qualifiedName,a.description,a.status,a.last_seen_at AS lastSeenAt,a.current_version_id AS currentVersionId,a.urn FROM dp_asset a LEFT JOIN dp_asset p ON p.id=a.parent_id WHERE a.id=#{id}")
    Map<String, Object> assetById(@Param("id") Long id);

    @Select("<script>SELECT a.id,a.asset_type AS assetType,a.source_kind AS sourceKind,a.source_id AS sourceId,a.parent_id AS parentId,p.source_id AS parentSourceId,a.name,a.qualified_name AS qualifiedName,a.description,a.status,a.last_seen_at AS lastSeenAt,a.current_version_id AS currentVersionId,a.urn FROM dp_asset a LEFT JOIN dp_asset p ON p.id=a.parent_id WHERE a.source_kind='BI_RESOURCE' AND a.asset_type=#{assetType} AND <choose><when test='assetType == \"DATASET_FIELD\"'>p.source_id IN <foreach collection='resourceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></when><otherwise>a.source_id IN <foreach collection='resourceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></otherwise></choose><if test='query != null and query.length() > 0'> AND (a.name LIKE CONCAT('%',#{query},'%') OR a.qualified_name LIKE CONCAT('%',#{query},'%'))</if> ORDER BY a.name,a.id LIMIT #{offset},#{pageSize}</script>")
    List<Map<String, Object>> searchVisibleBiAssets(@Param("assetType") String assetType,
            @Param("resourceIds") List<Long> resourceIds, @Param("query") String query,
            @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM dp_asset a LEFT JOIN dp_asset p ON p.id=a.parent_id WHERE a.source_kind='BI_RESOURCE' AND a.asset_type=#{assetType} AND <choose><when test='assetType == \"DATASET_FIELD\"'>p.source_id IN <foreach collection='resourceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></when><otherwise>a.source_id IN <foreach collection='resourceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></otherwise></choose><if test='query != null and query.length() > 0'> AND (a.name LIKE CONCAT('%',#{query},'%') OR a.qualified_name LIKE CONCAT('%',#{query},'%'))</if></script>")
    long countVisibleBiAssets(@Param("assetType") String assetType, @Param("resourceIds") List<Long> resourceIds,
            @Param("query") String query);

    @Select("SELECT schema_hash AS schemaHash,version_no AS versionNo,metadata_json AS metadataJson FROM dp_asset_version WHERE id=#{versionId}")
    Map<String, Object> assetVersion(@Param("versionId") Long versionId);

    @Insert("INSERT INTO dp_asset_version(asset_id,version_no,schema_hash,metadata_json,change_summary_json,scan_run_id,observed_at,create_by) VALUES(#{assetId},#{versionNo},#{schemaHash},#{metadata},#{changeSummary},#{runId},NOW(),#{actor})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAssetVersion(Map<String, Object> version);

    @Update("UPDATE dp_asset SET current_version_id=#{versionId},status='ACTIVE',last_seen_at=NOW(),update_by=#{actor} WHERE id=#{assetId}")
    int setCurrentAssetVersion(@Param("assetId") Long assetId, @Param("versionId") Long versionId, @Param("actor") String actor);

    @Insert("INSERT INTO dp_schema_change_event(asset_id,previous_version_id,current_version_id,scan_run_id,change_type,details_json,actor) VALUES(#{assetId},#{previousVersionId},#{currentVersionId},#{runId},#{changeType},#{details},#{actor})")
    int insertSchemaChangeEvent(@Param("assetId") Long assetId, @Param("previousVersionId") Long previousVersionId,
            @Param("currentVersionId") Long currentVersionId, @Param("runId") Long runId, @Param("changeType") String changeType,
            @Param("details") String details, @Param("actor") String actor);

    @Update("UPDATE dp_asset SET status='STALE',update_by=#{actor} WHERE source_kind='CONNECTOR' AND source_id=#{sourceId} AND status='ACTIVE' AND last_seen_at < #{startedAt}")
    int markMissingAssetsStale(@Param("sourceId") Long sourceId, @Param("startedAt") java.time.LocalDateTime startedAt,
                               @Param("actor") String actor);

    @Select("<script>SELECT id,asset_type AS assetType,source_id AS sourceId,parent_id AS parentId,name,qualified_name AS qualifiedName,description,status,last_seen_at AS lastSeenAt,current_version_id AS currentVersionId,urn FROM dp_asset WHERE source_kind='CONNECTOR' AND source_id IN <foreach collection='sourceIds' item='sourceId' open='(' separator=',' close=')'>#{sourceId}</foreach><if test='type != null and type.length() > 0'> AND asset_type=#{type}</if><if test='parentId != null'> AND parent_id=#{parentId}</if><if test='query != null and query.length() > 0'> AND (name LIKE CONCAT('%',#{query},'%') OR qualified_name LIKE CONCAT('%',#{query},'%'))</if> ORDER BY asset_type,name,id LIMIT #{offset},#{pageSize}</script>")
    List<Map<String, Object>> searchAssets(@Param("sourceIds") List<Long> sourceIds, @Param("type") String type,
            @Param("parentId") Long parentId, @Param("query") String query,
            @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM dp_asset WHERE source_kind='CONNECTOR' AND source_id IN <foreach collection='sourceIds' item='sourceId' open='(' separator=',' close=')'>#{sourceId}</foreach><if test='type != null and type.length() > 0'> AND asset_type=#{type}</if><if test='parentId != null'> AND parent_id=#{parentId}</if><if test='query != null and query.length() > 0'> AND (name LIKE CONCAT('%',#{query},'%') OR qualified_name LIKE CONCAT('%',#{query},'%'))</if></script>")
    long countAssets(@Param("sourceIds") List<Long> sourceIds, @Param("type") String type,
            @Param("parentId") Long parentId, @Param("query") String query);

    @Select("<script>SELECT id,asset_type AS assetType,source_id AS sourceId,parent_id AS parentId,name,qualified_name AS qualifiedName,description,status,last_seen_at AS lastSeenAt,current_version_id AS currentVersionId,urn FROM dp_asset WHERE id=#{id} AND source_kind='CONNECTOR' AND source_id IN <foreach collection='sourceIds' item='sourceId' open='(' separator=',' close=')'>#{sourceId}</foreach></script>")
    Map<String, Object> visibleAsset(@Param("id") Long id, @Param("sourceIds") List<Long> sourceIds);

    @Select("SELECT id,version_no AS versionNo,schema_hash AS schemaHash,metadata_json AS metadataJson,change_summary_json AS changeSummaryJson,scan_run_id AS scanRunId,observed_at AS observedAt FROM dp_asset_version WHERE asset_id=#{assetId} ORDER BY version_no DESC LIMIT 100")
    List<Map<String, Object>> assetVersions(@Param("assetId") Long assetId);
}
