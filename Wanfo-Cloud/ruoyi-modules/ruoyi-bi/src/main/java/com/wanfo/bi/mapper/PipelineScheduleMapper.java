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
public interface PipelineScheduleMapper {
    @Select("SELECT id,pipeline_id AS pipelineId,revision_id AS revisionId,name,enabled,schedule_json AS scheduleJson,target_datasource_id AS targetDatasourceId,max_rows AS maxRows,timeout_seconds AS timeoutSeconds,owner_user_id AS ownerUserId,actor_json AS actorJson,failure_count AS failureCount,last_status AS lastStatus,last_message AS lastMessage,last_run_id AS lastRunId,last_run_at AS lastRunAt,next_run_utc AS nextRunUtc,create_by AS createBy,create_time AS createTime,update_time AS updateTime FROM dp_pipeline_schedule WHERE pipeline_id=#{pipelineId}")
    Map<String, Object> byPipeline(@Param("pipelineId") Long pipelineId);

    @Select("SELECT id,pipeline_id AS pipelineId,revision_id AS revisionId,name,enabled,schedule_json AS scheduleJson,target_datasource_id AS targetDatasourceId,max_rows AS maxRows,timeout_seconds AS timeoutSeconds,owner_user_id AS ownerUserId,actor_json AS actorJson,failure_count AS failureCount,last_status AS lastStatus,last_message AS lastMessage,last_run_id AS lastRunId,last_run_at AS lastRunAt,next_run_utc AS nextRunUtc,create_by AS createBy,create_time AS createTime,update_time AS updateTime FROM dp_pipeline_schedule WHERE id=#{id}")
    Map<String, Object> get(@Param("id") Long id);

    @Select("SELECT id FROM dp_pipeline_schedule WHERE enabled=1 AND next_run_utc<=UTC_TIMESTAMP(3) ORDER BY next_run_utc,id LIMIT 50")
    List<Long> dueIds();

    @Update("UPDATE dp_pipeline_schedule SET next_run_utc=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=#{id} AND enabled=1 AND next_run_utc<=UTC_TIMESTAMP(3)")
    int claim(@Param("id") Long id);

    @Insert("INSERT INTO dp_pipeline_schedule(pipeline_id,revision_id,name,enabled,schedule_json,target_datasource_id,max_rows,timeout_seconds,owner_user_id,actor_json,next_run_utc,create_by) VALUES(#{pipelineId},#{revisionId},#{name},#{enabled},CAST(#{scheduleJson} AS JSON),#{targetDatasourceId},#{maxRows},#{timeoutSeconds},#{ownerUserId},CAST(#{actorJson} AS JSON),#{nextRunUtc},#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("UPDATE dp_pipeline_schedule SET revision_id=#{revisionId},name=#{name},enabled=#{enabled},schedule_json=CAST(#{scheduleJson} AS JSON),target_datasource_id=#{targetDatasourceId},max_rows=#{maxRows},timeout_seconds=#{timeoutSeconds},actor_json=CAST(#{actorJson} AS JSON),next_run_utc=#{nextRunUtc},failure_count=CASE WHEN #{enabled}=1 THEN 0 ELSE failure_count END WHERE id=#{id} AND pipeline_id=#{pipelineId}")
    int update(Map<String, Object> row);

    @Update("UPDATE dp_pipeline_schedule SET last_status=#{status},last_message=#{message},last_run_id=#{runId},last_run_at=UTC_TIMESTAMP(3),failure_count=#{failureCount},enabled=#{enabled},next_run_utc=#{nextRunUtc} WHERE id=#{id}")
    int complete(Map<String, Object> row);

    @Update("UPDATE dp_pipeline_schedule SET enabled=0 WHERE id=#{id}")
    int disable(@Param("id") Long id);
}
