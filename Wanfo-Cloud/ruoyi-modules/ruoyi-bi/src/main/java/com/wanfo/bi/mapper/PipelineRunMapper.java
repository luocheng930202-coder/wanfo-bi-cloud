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
public interface PipelineRunMapper {
    @Select("SELECT id FROM dp_pipeline_run WHERE status IN ('PENDING','RUNNING') AND TIMESTAMPDIFF(SECOND,create_time,CURRENT_TIMESTAMP(3)) > timeout_seconds + 60 ORDER BY id LIMIT 100")
    List<Long> expiredActiveRuns();

    @Update("UPDATE dp_pipeline_node_run n JOIN dp_pipeline_run r ON r.id=n.run_id SET n.status=CASE WHEN r.status='FAILED' THEN 'SKIPPED' ELSE r.status END,n.error_summary='所属流程已结束，由巡检收束未完成节点',n.finished_at=CURRENT_TIMESTAMP(3) WHERE n.status IN ('PENDING','RUNNING') AND r.status IN ('FAILED','CANCELLED','TIMED_OUT')")
    int closeAbandonedNodes();
    @Select("SELECT r.id,r.pipeline_id AS pipelineId,p.name AS taskName,r.status,r.run_mode AS runMode,r.error_summary AS message,r.create_time AS createTime,r.started_at AS startedAt,r.finished_at AS finishedAt,r.duration_ms AS durationMs FROM dp_pipeline_run r JOIN dp_pipeline p ON p.id=r.pipeline_id ORDER BY r.id DESC LIMIT 100")
    List<Map<String,Object>> recentRuns();
    @Select("SELECT id,pipeline_id AS pipelineId,revision_id AS revisionId,revision_hash AS revisionHash,run_mode AS runMode,request_key AS requestKey,status,trigger_by AS triggerBy,trace_id AS traceId,timeout_seconds AS timeoutSeconds,summary_json AS summary,error_summary AS errorSummary,create_time AS createTime,started_at AS startedAt,finished_at AS finishedAt,duration_ms AS durationMs FROM dp_pipeline_run WHERE pipeline_id=#{pipelineId} AND request_key=#{requestKey}")
    Map<String,Object> byRequestKey(@Param("pipelineId") Long pipelineId,@Param("requestKey") String requestKey);

    @Insert("INSERT INTO dp_pipeline_run(pipeline_id,active_pipeline_id,revision_id,revision_hash,run_mode,request_key,status,trigger_by,trace_id,timeout_seconds,request_snapshot_json) VALUES(#{pipelineId},#{pipelineId},#{revisionId},#{hash},#{mode},#{key},'PENDING',#{actor},#{traceId},#{timeout},CAST(#{snapshot} AS JSON))")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Map<String,Object> row);

    @Select("SELECT request_snapshot_json AS requestSnapshot FROM dp_pipeline_run WHERE id=#{runId}")
    String requestSnapshot(@Param("runId") Long runId);

    @Select("SELECT id,pipeline_id AS pipelineId,revision_id AS revisionId,revision_hash AS revisionHash,run_mode AS runMode,request_key AS requestKey,status,trigger_by AS triggerBy,trace_id AS traceId,timeout_seconds AS timeoutSeconds,summary_json AS summary,error_summary AS errorSummary,create_time AS createTime,started_at AS startedAt,finished_at AS finishedAt,duration_ms AS durationMs FROM dp_pipeline_run WHERE pipeline_id=#{pipelineId} ORDER BY id DESC LIMIT #{limit}")
    List<Map<String,Object>> list(@Param("pipelineId") Long pipelineId,@Param("limit") int limit);

    @Select("SELECT id,pipeline_id AS pipelineId,revision_id AS revisionId,revision_hash AS revisionHash,run_mode AS runMode,request_key AS requestKey,status,trigger_by AS triggerBy,trace_id AS traceId,timeout_seconds AS timeoutSeconds,summary_json AS summary,error_summary AS errorSummary,create_time AS createTime,started_at AS startedAt,finished_at AS finishedAt,duration_ms AS durationMs FROM dp_pipeline_run WHERE id=#{runId}")
    Map<String,Object> run(@Param("runId") Long runId);

    @Select("SELECT r.pipeline_id AS pipelineId FROM dp_pipeline_run r WHERE r.id=#{runId}") Long pipelineId(@Param("runId") Long runId);
    @Select("SELECT status FROM dp_pipeline_run WHERE id=#{runId}") String status(@Param("runId") Long runId);
    @Select("SELECT count(*) FROM dp_pipeline_run WHERE pipeline_id=#{pipelineId} AND status IN ('PENDING','RUNNING')") int activeCount(@Param("pipelineId") Long pipelineId);
    @Select("SELECT count(*) FROM dp_pipeline_run WHERE status IN ('PENDING','RUNNING')") int activeGlobalCount();

    @Update("UPDATE dp_pipeline_run SET status='RUNNING',started_at=CURRENT_TIMESTAMP(3) WHERE id=#{runId} AND status='PENDING'") int start(@Param("runId") Long runId);
    @Update("UPDATE dp_pipeline_run SET status='SUCCEEDED',active_pipeline_id=NULL,summary_json=CAST(#{summary} AS JSON),finished_at=CURRENT_TIMESTAMP(3),duration_ms=TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000 WHERE id=#{runId} AND status='RUNNING'") int succeed(@Param("runId") Long runId,@Param("summary") String summary);
    @Update("UPDATE dp_pipeline_run SET status='FAILED',active_pipeline_id=NULL,error_summary=#{summary},finished_at=CURRENT_TIMESTAMP(3),duration_ms=IFNULL(TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000,0) WHERE id=#{runId} AND status IN ('PENDING','RUNNING')") int fail(@Param("runId") Long runId,@Param("summary") String summary);
    @Update("UPDATE dp_pipeline_run SET status='CANCELLED',active_pipeline_id=NULL,error_summary='由操作者取消',finished_at=CURRENT_TIMESTAMP(3),duration_ms=IFNULL(TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000,0) WHERE id=#{runId} AND status IN ('PENDING','RUNNING')") int cancel(@Param("runId") Long runId);
    @Update("UPDATE dp_pipeline_run SET status='TIMED_OUT',active_pipeline_id=NULL,error_summary='执行超过配置的超时时间',finished_at=CURRENT_TIMESTAMP(3),duration_ms=IFNULL(TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000,0) WHERE id=#{runId} AND status IN ('PENDING','RUNNING')") int timeout(@Param("runId") Long runId);

    @Insert("INSERT INTO dp_pipeline_node_run(run_id,node_key,node_type,status) VALUES(#{runId},#{nodeKey},#{nodeType},'PENDING')") int insertNode(@Param("runId") Long runId,@Param("nodeKey") String nodeKey,@Param("nodeType") String nodeType);
    @Select("SELECT id,run_id AS runId,node_key AS nodeKey,node_type AS nodeType,status,attempt,input_row_count AS inputRowCount,output_row_count AS outputRowCount,summary_json AS summary,error_summary AS errorSummary,started_at AS startedAt,finished_at AS finishedAt,duration_ms AS durationMs FROM dp_pipeline_node_run WHERE run_id=#{runId} ORDER BY id")
    List<Map<String,Object>> nodes(@Param("runId") Long runId);
    @Update("UPDATE dp_pipeline_node_run SET status='RUNNING',started_at=CURRENT_TIMESTAMP(3) WHERE run_id=#{runId} AND node_key=#{nodeKey} AND status='PENDING'") int startNode(@Param("runId") Long runId,@Param("nodeKey") String nodeKey);
    @Update("UPDATE dp_pipeline_node_run SET status='SUCCEEDED',input_row_count=#{inputRows},output_row_count=#{outputRows},summary_json=CAST(#{summary} AS JSON),finished_at=CURRENT_TIMESTAMP(3),duration_ms=TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000 WHERE run_id=#{runId} AND node_key=#{nodeKey} AND status='RUNNING'") int succeedNode(@Param("runId") Long runId,@Param("nodeKey") String nodeKey,@Param("inputRows") long inputRows,@Param("outputRows") long outputRows,@Param("summary") String summary);
    @Update("UPDATE dp_pipeline_node_run SET status='FAILED',error_summary=#{summary},finished_at=CURRENT_TIMESTAMP(3),duration_ms=IFNULL(TIMESTAMPDIFF(MICROSECOND,started_at,CURRENT_TIMESTAMP(3))/1000,0) WHERE run_id=#{runId} AND node_key=#{nodeKey} AND status IN ('PENDING','RUNNING')") int failNode(@Param("runId") Long runId,@Param("nodeKey") String nodeKey,@Param("summary") String summary);
    @Update("UPDATE dp_pipeline_node_run SET status=#{status},error_summary=#{summary},finished_at=CURRENT_TIMESTAMP(3) WHERE run_id=#{runId} AND status IN ('PENDING','RUNNING')") int closePendingNodes(@Param("runId") Long runId,@Param("status") String status,@Param("summary") String summary);
}
