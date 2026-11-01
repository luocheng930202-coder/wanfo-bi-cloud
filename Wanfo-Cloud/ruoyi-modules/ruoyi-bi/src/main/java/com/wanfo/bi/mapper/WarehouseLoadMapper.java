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
public interface WarehouseLoadMapper
{
    @Select("SELECT r.id,r.source_datasource_id AS sourceDatasourceId,r.target_datasource_id AS targetDatasourceId,s.name AS sourceName,t.name AS targetName,r.source_table AS sourceTable,r.target_table AS targetTable,r.load_mode AS loadMode,r.publish_mode AS publishMode,r.request_snapshot_json AS requestSnapshotJson,r.status,r.promote_status AS promoteStatus,r.row_count AS rowCount,r.error_summary AS message,r.create_time AS createTime,r.started_at AS startedAt,r.finished_at AS finishedAt FROM dp_warehouse_load_run r LEFT JOIN bi_datasource s ON s.id=r.source_datasource_id LEFT JOIN bi_datasource t ON t.id=r.target_datasource_id ORDER BY r.id DESC LIMIT 100")
    List<Map<String,Object>> recentRuns();
    @Insert("INSERT INTO dp_warehouse_load_run(source_datasource_id,target_datasource_id,source_table,target_database,target_table,load_mode,publish_mode,request_snapshot_json,status,trace_id,trigger_by) VALUES(#{sourceId},#{targetId},#{sourceTable},#{targetDatabase},#{targetTable},#{loadMode},#{publishMode},#{requestSnapshotJson},'PENDING',#{traceId},#{actor})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(Map<String, Object> run);

    @Select("SELECT id,source_datasource_id AS sourceDatasourceId,target_datasource_id AS targetDatasourceId,source_table AS sourceTable,target_database AS targetDatabase,target_table AS targetTable,load_mode AS loadMode,publish_mode AS publishMode,request_snapshot_json AS requestSnapshotJson,status,promote_status AS promoteStatus,row_count AS rowCount,target_replaced AS targetReplaced,error_summary AS errorSummary,trace_id AS traceId,trigger_by AS triggerBy,started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime FROM dp_warehouse_load_run WHERE id=#{id}")
    Map<String, Object> run(@Param("id") Long id);

    @Select("SELECT id,source_datasource_id AS sourceDatasourceId,target_datasource_id AS targetDatasourceId,source_table AS sourceTable,target_database AS targetDatabase,target_table AS targetTable,load_mode AS loadMode,publish_mode AS publishMode,status,promote_status AS promoteStatus,row_count AS rowCount,target_replaced AS targetReplaced,error_summary AS errorSummary,trace_id AS traceId,trigger_by AS triggerBy,started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime FROM dp_warehouse_load_run WHERE target_datasource_id=#{targetId} ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> runs(@Param("targetId") Long targetId);

    @Update("UPDATE dp_warehouse_load_run SET status='RUNNING',started_at=NOW() WHERE id=#{id} AND status='PENDING'")
    int start(@Param("id") Long id);

    @Update("UPDATE dp_warehouse_load_run SET promote_status=#{phase} WHERE id=#{id} AND status='RUNNING'")
    int markPromote(@Param("id") Long id, @Param("phase") String phase);

    @Update("UPDATE dp_warehouse_load_run SET status='SUCCEEDED',promote_status='SUCCEEDED',row_count=#{rows},target_replaced=#{replaced},finished_at=NOW(),error_summary=NULL WHERE id=#{id} AND status='RUNNING'")
    int succeed(@Param("id") Long id, @Param("rows") long rows, @Param("replaced") boolean replaced);

    @Update("UPDATE dp_warehouse_load_run SET status='FAILED',promote_status='FAILED',error_summary=#{error},finished_at=NOW() WHERE id=#{id} AND status IN ('PENDING','RUNNING')")
    int fail(@Param("id") Long id, @Param("error") String error);

    @Select("SELECT id,target_datasource_id AS targetDatasourceId,target_database AS targetDatabase,target_table AS targetTable FROM dp_warehouse_load_run WHERE status IN ('PENDING','RUNNING') AND COALESCE(started_at,create_time)<DATE_SUB(NOW(),INTERVAL 5 MINUTE) ORDER BY id LIMIT 500")
    List<Map<String, Object>> staleCandidates();

    @Select("SELECT watermark_column AS watermarkColumn,watermark_jdbc_type AS watermarkJdbcType,key_columns AS keyColumns,watermark_value AS watermarkValue FROM dp_warehouse_load_checkpoint WHERE source_datasource_id=#{sourceId} AND source_table=#{sourceTable} AND target_database=#{targetDatabase} AND target_table=#{targetTable}")
    Map<String, Object> checkpoint(@Param("sourceId") Long sourceId, @Param("sourceTable") String sourceTable,
            @Param("targetDatabase") String targetDatabase, @Param("targetTable") String targetTable);

    @Insert("INSERT INTO dp_warehouse_load_checkpoint(source_datasource_id,source_table,target_database,target_table,watermark_column,watermark_jdbc_type,key_columns,watermark_value,last_successful_run_id) VALUES(#{sourceId},#{sourceTable},#{targetDatabase},#{targetTable},#{watermarkColumn},#{watermarkJdbcType},#{keyColumns},#{watermarkValue},#{runId}) ON DUPLICATE KEY UPDATE watermark_jdbc_type=VALUES(watermark_jdbc_type),watermark_value=VALUES(watermark_value),last_successful_run_id=VALUES(last_successful_run_id),updated_at=NOW()")
    int saveCheckpoint(@Param("sourceId") Long sourceId, @Param("sourceTable") String sourceTable,
            @Param("targetDatabase") String targetDatabase, @Param("targetTable") String targetTable,
            @Param("watermarkColumn") String watermarkColumn, @Param("watermarkJdbcType") int watermarkJdbcType,
            @Param("keyColumns") String keyColumns, @Param("watermarkValue") String watermarkValue,
            @Param("runId") long runId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM dp_warehouse_load_checkpoint WHERE source_datasource_id=#{sourceId} AND source_table=#{sourceTable} AND target_database=#{targetDatabase} AND target_table=#{targetTable}")
    int deleteCheckpoint(@Param("sourceId") Long sourceId, @Param("sourceTable") String sourceTable,
            @Param("targetDatabase") String targetDatabase, @Param("targetTable") String targetTable);

    @Insert("INSERT INTO dp_warehouse_reconciliation(run_id,target_datasource_id,model_version,source_table,target_table,check_scope,source_row_count,target_row_count,source_sales_amount,target_sales_amount,source_net_quantity,target_net_quantity,source_max_updated_at,freshness_age_seconds,status,summary) VALUES(#{runId},#{targetId},#{modelVersion},#{sourceTable},#{targetTable},'FULL',#{sourceRows},#{targetRows},#{sourceAmount},#{targetAmount},#{sourceQuantity},#{targetQuantity},#{sourceMaxUpdatedAt},#{freshnessAgeSeconds},#{status},#{summary})")
    int saveReconciliation(Map<String, Object> result);

    @Select("SELECT run_id AS runId,target_datasource_id AS targetDatasourceId,model_version AS modelVersion,source_table AS sourceTable,target_table AS targetTable,check_scope AS checkScope,source_row_count AS sourceRowCount,target_row_count AS targetRowCount,source_sales_amount AS sourceSalesAmount,target_sales_amount AS targetSalesAmount,source_net_quantity AS sourceNetQuantity,target_net_quantity AS targetNetQuantity,source_max_updated_at AS sourceMaxUpdatedAt,observed_at AS observedAt,freshness_age_seconds AS freshnessAgeSeconds,status,summary FROM dp_warehouse_reconciliation WHERE target_datasource_id=#{targetId} AND model_version='retail-v1' ORDER BY observed_at DESC LIMIT 1")
    Map<String, Object> latestReconciliation(@Param("targetId") Long targetId);

    @Select("SELECT run_id AS runId,target_datasource_id AS targetDatasourceId,model_version AS modelVersion,source_table AS sourceTable,target_table AS targetTable,check_scope AS checkScope,source_row_count AS sourceRowCount,target_row_count AS targetRowCount,source_sales_amount AS sourceSalesAmount,target_sales_amount AS targetSalesAmount,source_net_quantity AS sourceNetQuantity,target_net_quantity AS targetNetQuantity,source_max_updated_at AS sourceMaxUpdatedAt,observed_at AS observedAt,freshness_age_seconds AS freshnessAgeSeconds,status,summary FROM dp_warehouse_reconciliation WHERE target_datasource_id=#{targetId} AND model_version=#{modelVersion} ORDER BY observed_at DESC LIMIT 1")
    Map<String, Object> latestReconciliationForModel(@Param("targetId") Long targetId, @Param("modelVersion") String modelVersion);

    @Select("SELECT COUNT(*) FROM dp_warehouse_load_run WHERE status IN ('PENDING','RUNNING')")
    long activeRunCount();

    @Select("SELECT COUNT(*) FROM dp_warehouse_load_run WHERE status IN ('PENDING','RUNNING') AND COALESCE(trace_id,'') LIKE 'cdc-%'")
    long activeCdcRunCount();

    @Select("SELECT COUNT(*) FROM dp_warehouse_load_run WHERE status IN ('PENDING','RUNNING') AND COALESCE(started_at,create_time)<DATE_SUB(NOW(),INTERVAL 5 MINUTE)")
    long staleRunCount();

    @Update("UPDATE dp_warehouse_reconciliation SET status='PASS',observed_at=NOW(3),summary=#{summary} WHERE run_id=#{runId} AND status='PREPARED'")
    int markReconciliationPublished(@Param("runId") long runId, @Param("summary") String summary);

    @Update("UPDATE dp_warehouse_load_run SET status='INTERRUPTED',error_summary=#{error},finished_at=NOW() WHERE id=#{id} AND status IN ('PENDING','RUNNING')")
    int interrupt(@Param("id") Long id, @Param("error") String error);
}
