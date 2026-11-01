package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SchemaMigrationMapper
{
    @Select("SELECT e.id,e.asset_id AS assetId,e.previous_version_id AS previousVersionId,e.current_version_id AS currentVersionId,e.change_type AS changeType,e.details_json AS detailsJson FROM dp_schema_change_event e WHERE e.id=#{id}")
    Map<String,Object> event(@Param("id") Long id);

    @Select("SELECT metadata_json AS metadataJson FROM dp_asset_version WHERE id=#{id}")
    Map<String,Object> version(@Param("id") Long id);

    @Select("SELECT id,name,datasource_id AS datasourceId,source_table AS sourceTable,source_catalog AS sourceCatalog,type,model_json AS modelJson,union_sql AS unionSql,del_flag AS delFlag FROM bi_dataset_table WHERE id=#{id}")
    Map<String,Object> dataset(@Param("id") Long id);

    @Select("SELECT id,asset_type AS assetType,source_kind AS sourceKind,source_id AS sourceId,name,qualified_name AS qualifiedName,current_version_id AS currentVersionId FROM dp_asset WHERE id=#{id}")
    Map<String,Object> asset(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(plan_no),0)+1 FROM dp_schema_migration_plan WHERE schema_change_event_id=#{eventId}")
    int nextPlanNo(@Param("eventId") Long eventId);

    @Insert("INSERT INTO dp_schema_migration_plan(schema_change_event_id,plan_no,status,definition_json,blockers_json,before_snapshot_json,create_by) VALUES(#{eventId},#{planNo},#{status},#{definition},#{blockers},#{beforeSnapshot},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertPlan(Map<String,Object> row);

    @Insert("INSERT INTO dp_schema_migration_step(plan_id,step_order,dataset_id,dataset_field_id,operation,source_field,target_field,target_type,before_json,after_json) VALUES(#{planId},#{order},#{datasetId},#{fieldId},#{operation},#{sourceField},#{targetField},#{targetType},#{before},#{after})")
    int insertStep(@Param("planId") Long planId,@Param("order") int order,@Param("datasetId") Long datasetId,
            @Param("fieldId") Long fieldId,@Param("operation") String operation,@Param("sourceField") String sourceField,
            @Param("targetField") String targetField,@Param("targetType") String targetType,
            @Param("before") String before,@Param("after") String after);

    @Select("SELECT id,schema_change_event_id AS eventId,plan_no AS planNo,status,definition_json AS definitionJson,blockers_json AS blockersJson,before_snapshot_json AS beforeSnapshotJson,execution_error AS executionError,create_by AS createBy,create_time AS createTime,approved_by AS approvedBy,approved_at AS approvedAt,blocking_override_ticket AS blockingOverrideTicket,blocking_override_by AS blockingOverrideBy,blocking_override_at AS blockingOverrideAt,executed_by AS executedBy,executed_at AS executedAt,rolled_back_by AS rolledBackBy,rolled_back_at AS rolledBackAt FROM dp_schema_migration_plan WHERE id=#{id}")
    Map<String,Object> plan(@Param("id") Long id);

    @Select("SELECT id,schema_change_event_id AS eventId,plan_no AS planNo,status,execution_error AS executionError,create_by AS createBy,create_time AS createTime,approved_by AS approvedBy,approved_at AS approvedAt,executed_by AS executedBy,executed_at AS executedAt,rolled_back_by AS rolledBackBy,rolled_back_at AS rolledBackAt FROM dp_schema_migration_plan WHERE schema_change_event_id=#{eventId} ORDER BY plan_no DESC LIMIT 100")
    List<Map<String,Object>> plans(@Param("eventId") Long eventId);

    @Select("SELECT id,schema_change_event_id AS eventId,plan_no AS planNo,status,definition_json AS definitionJson,blockers_json AS blockersJson,before_snapshot_json AS beforeSnapshotJson,execution_error AS executionError,create_by AS createBy,create_time AS createTime,approved_by AS approvedBy,approved_at AS approvedAt,blocking_override_ticket AS blockingOverrideTicket,blocking_override_by AS blockingOverrideBy,blocking_override_at AS blockingOverrideAt,executed_by AS executedBy,executed_at AS executedAt,rolled_back_by AS rolledBackBy,rolled_back_at AS rolledBackAt FROM dp_schema_migration_plan WHERE id=#{id} FOR UPDATE")
    Map<String,Object> lockPlan(@Param("id") Long id);

    @Update("""
            UPDATE dp_schema_migration_plan SET blocking_override_ticket=#{ticket},blocking_override_by=#{actor},blocking_override_at=NOW(3)
            WHERE id=#{id}
            """)
    int recordBlockingOverride(@Param("id") Long id, @Param("ticket") String ticket, @Param("actor") String actor);

    @Select("SELECT id,plan_id AS planId,step_order AS stepOrder,dataset_id AS datasetId,dataset_field_id AS datasetFieldId,operation,source_field AS sourceField,target_field AS targetField,target_type AS targetType,before_json AS beforeJson,after_json AS afterJson,status FROM dp_schema_migration_step WHERE plan_id=#{planId} ORDER BY step_order")
    List<Map<String,Object>> steps(@Param("planId") Long planId);

    @Update("UPDATE dp_schema_migration_plan SET status=#{status},approved_by=#{actor},approved_at=NOW(3),execution_error=NULL WHERE id=#{id} AND status='DRAFT'")
    int approve(@Param("id") Long id,@Param("actor") String actor,@Param("status") String status);

    @Update("UPDATE dp_schema_migration_plan SET status=#{status},executed_by=#{actor},executed_at=NOW(3),execution_error=NULL WHERE id=#{id} AND status='APPROVED'")
    int markExecuted(@Param("id") Long id,@Param("actor") String actor,@Param("status") String status);

    @Update("UPDATE dp_schema_migration_plan SET status='FAILED',execution_error=#{error} WHERE id=#{id} AND status IN ('APPROVED','EXECUTING')")
    int markFailed(@Param("id") Long id,@Param("error") String error);

    @Update("UPDATE dp_schema_migration_plan SET status='ROLLED_BACK',rolled_back_by=#{actor},rolled_back_at=NOW(3) WHERE id=#{id} AND status='EXECUTED'")
    int markRolledBack(@Param("id") Long id,@Param("actor") String actor);

    @Update("UPDATE dp_schema_migration_step SET status=#{status} WHERE id=#{id}")
    int updateStepStatus(@Param("id") Long id,@Param("status") String status);

    @Select("SELECT COALESCE(MAX(revision_no),0)+1 FROM dp_schema_migration_revision WHERE plan_id=#{planId}")
    int nextRevision(@Param("planId") Long planId);

    @Insert("INSERT INTO dp_schema_migration_revision(plan_id,revision_no,plan_snapshot_json,change_kind,changed_by) VALUES(#{planId},#{revision},#{snapshot},#{kind},#{actor})")
    int insertRevision(@Param("planId") Long planId,@Param("revision") int revision,@Param("snapshot") String snapshot,
            @Param("kind") String kind,@Param("actor") String actor);

    @Select("SELECT id,revision_no AS revisionNo,plan_snapshot_json AS snapshotJson,change_kind AS changeKind,changed_by AS changedBy,changed_at AS changedAt FROM dp_schema_migration_revision WHERE plan_id=#{planId} ORDER BY revision_no DESC LIMIT 100")
    List<Map<String,Object>> revisions(@Param("planId") Long planId);

    @Select("SELECT COALESCE((SELECT COUNT(*) FROM bi_chart_view WHERE dataset_id=#{id} AND del_flag='0'),0) AS charts,COALESCE((SELECT COUNT(*) FROM bi_dashboard_binding WHERE dataset_id=#{id}),0) AS dashboards,COALESCE((SELECT COUNT(*) FROM bi_dataset_quality_rule WHERE dataset_id=#{id}),0) AS legacyQuality,COALESCE((SELECT COUNT(*) FROM dp_quality_rule_set WHERE dataset_id=#{id}),0) AS qualitySets,COALESCE((SELECT COUNT(*) FROM dp_quality_task WHERE dataset_id=#{id}),0) AS qualityTasks,COALESCE((SELECT COUNT(*) FROM bi_dataset_policy WHERE dataset_id=#{id}),0) AS policies,COALESCE((SELECT COUNT(*) FROM bi_data_form WHERE dataset_id=#{id} AND del_flag='0'),0) AS forms,COALESCE((SELECT COUNT(DISTINCT p.id) FROM dp_pipeline p JOIN dp_pipeline_node n ON n.revision_id=p.current_revision_id WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(n.config_json,'$.datasetId')) AS UNSIGNED)=#{id}),0) AS pipelines")
    Map<String,Object> dependencyCounts(@Param("id") Long datasetId);
}
