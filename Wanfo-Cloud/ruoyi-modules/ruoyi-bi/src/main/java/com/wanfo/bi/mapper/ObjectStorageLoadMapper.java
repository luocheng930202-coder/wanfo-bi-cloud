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
public interface ObjectStorageLoadMapper {
    @Insert("""
            INSERT INTO dp_object_storage_load_run(datasource_id,target_datasource_id,bucket,object_prefix,target_database,target_table,status,request_snapshot_json,trace_id,trigger_by)
            VALUES(#{datasourceId},#{targetDatasourceId},#{bucket},#{objectPrefix},#{targetDatabase},#{targetTable},'PENDING',#{requestSnapshotJson},#{traceId},#{triggerBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("UPDATE dp_object_storage_load_run SET status='RUNNING',started_at=NOW(3) WHERE id=#{id} AND status='PENDING'")
    int start(@Param("id") Long id);

    @Update("UPDATE dp_object_storage_load_run SET status='SUCCEEDED',object_count=#{objects},row_count=#{rows},finished_at=NOW(3),error_summary=NULL WHERE id=#{id} AND status='RUNNING'")
    int succeed(@Param("id") Long id, @Param("objects") int objects, @Param("rows") long rows);

    @Update("UPDATE dp_object_storage_load_run SET status='FAILED',error_summary=#{error},finished_at=NOW(3) WHERE id=#{id} AND status IN ('PENDING','RUNNING')")
    int fail(@Param("id") Long id, @Param("error") String error);

    @Select("SELECT id,datasource_id AS datasourceId,target_datasource_id AS targetDatasourceId,bucket,object_prefix AS objectPrefix,target_database AS targetDatabase,target_table AS targetTable,status,object_count AS objectCount,row_count AS rowCount,error_summary AS errorSummary,trace_id AS traceId,trigger_by AS triggerBy,started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime FROM dp_object_storage_load_run WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id,datasource_id AS datasourceId,target_datasource_id AS targetDatasourceId,bucket,object_prefix AS objectPrefix,target_database AS targetDatabase,target_table AS targetTable,status,object_count AS objectCount,row_count AS rowCount,error_summary AS errorSummary,trigger_by AS triggerBy,create_time AS createTime FROM dp_object_storage_load_run ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> recent();
}
