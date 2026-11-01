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
public interface QualityTaskMapper {
    @Select("SELECT r.id,r.task_id AS taskId,r.retry_of_run_id AS retryOfRunId,r.attempt_no AS attemptNo,r.dataset_id AS datasetId,t.name AS taskName,d.name AS resourceName,r.status,r.scan_scope AS scanScope,r.rows_scanned AS rowsScanned,r.rules_total AS rulesTotal,r.rules_failed AS rulesFailed,r.issue_count AS issueCount,r.failure_summary AS message,r.create_time AS createTime,r.started_at AS startedAt,r.completed_at AS finishedAt FROM dp_quality_run r JOIN dp_quality_task t ON t.id=r.task_id JOIN bi_dataset_table d ON d.id=r.dataset_id WHERE d.del_flag='0' ORDER BY r.id DESC LIMIT 100")
    List<Map<String,Object>> recentRuns();
    @Insert("INSERT INTO dp_quality_task(dataset_id,rule_set_id,name,status,schedule_type,schedule_json,scan_scope,timeout_seconds,retry_limit,create_by) VALUES(#{datasetId},#{ruleSetId},#{name},'ENABLED',#{scheduleType},#{scheduleJson},#{scanScope},#{timeoutSeconds},#{retryLimit},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertTask(Map<String,Object> row);

    @Select("SELECT t.id,t.dataset_id AS datasetId,t.rule_set_id AS ruleSetId,t.name,t.status,t.schedule_type AS scheduleType,CASE WHEN t.schedule_type='INTERVAL' THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(t.schedule_json,'$.intervalMinutes')) AS UNSIGNED) ELSE NULL END AS scheduleIntervalMinutes,t.scan_scope AS scanScope,t.timeout_seconds AS timeoutSeconds,t.retry_limit AS retryLimit,t.create_time AS createTime,t.update_time AS updateTime,s.version_no AS ruleVersion,s.status AS ruleSetStatus FROM dp_quality_task t JOIN dp_quality_rule_set s ON s.id=t.rule_set_id WHERE t.dataset_id=#{datasetId} ORDER BY t.id DESC")
    List<Map<String,Object>> listTasks(@Param("datasetId") Long datasetId);

    @Select("SELECT t.id,t.dataset_id AS datasetId,t.rule_set_id AS ruleSetId,t.name,t.status,t.schedule_type AS scheduleType,CASE WHEN t.schedule_type='INTERVAL' THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(t.schedule_json,'$.intervalMinutes')) AS UNSIGNED) ELSE NULL END AS scheduleIntervalMinutes,t.scan_scope AS scanScope,t.timeout_seconds AS timeoutSeconds,t.retry_limit AS retryLimit,s.version_no AS ruleVersion,s.status AS ruleSetStatus FROM dp_quality_task t JOIN dp_quality_rule_set s ON s.id=t.rule_set_id WHERE t.id=#{id}")
    Map<String,Object> selectTask(@Param("id") Long id);

    @Select("SELECT id,dataset_id AS datasetId,rule_set_id AS ruleSetId,schedule_json AS scheduleJson FROM dp_quality_task WHERE status='ENABLED' AND schedule_type='INTERVAL' AND CAST(JSON_UNQUOTE(JSON_EXTRACT(schedule_json,'$.nextRunEpoch')) AS UNSIGNED) <= #{nowEpoch} ORDER BY id LIMIT 100")
    List<Map<String,Object>> dueScheduledTasks(@Param("nowEpoch") long nowEpoch);

    @Update("UPDATE dp_quality_task SET schedule_json=JSON_SET(schedule_json,'$.nextRunEpoch',#{nextEpoch}),update_by='scheduler' WHERE id=#{id} AND status='ENABLED' AND schedule_type='INTERVAL' AND CAST(JSON_UNQUOTE(JSON_EXTRACT(schedule_json,'$.nextRunEpoch')) AS UNSIGNED)=#{expectedEpoch}")
    int claimScheduledTask(@Param("id") Long id,@Param("expectedEpoch") long expectedEpoch,@Param("nextEpoch") long nextEpoch);

    @Select("SELECT COUNT(*) FROM dp_quality_run WHERE task_id=#{taskId} AND status IN ('QUEUED','RUNNING')")
    int countActiveRuns(@Param("taskId") Long taskId);

    @Select("SELECT COUNT(*) FROM dp_quality_run WHERE status IN ('QUEUED','RUNNING')")
    int activeGlobalCount();

    @Select("SELECT id,task_id AS taskId,retry_of_run_id AS retryOfRunId,attempt_no AS attemptNo,dataset_id AS datasetId,rule_set_id AS ruleSetId,rule_set_version AS ruleSetVersion,scan_scope AS scanScope,trace_id AS traceId,create_by AS createBy,status,failure_code AS failureCode FROM dp_quality_run WHERE id=#{id}")
    Map<String,Object> selectRun(@Param("id") Long id);

    @Update("UPDATE dp_quality_run SET status='CANCELLED',completed_at=CURRENT_TIMESTAMP(3),failure_code='USER_CANCELLED',failure_summary='质量任务已由用户取消' WHERE id=#{id} AND task_id=#{taskId} AND status IN ('QUEUED','RUNNING')")
    int cancelRun(@Param("taskId") Long taskId,@Param("id") Long id);

    @Update("UPDATE dp_quality_task SET status=#{status},update_by=#{actor} WHERE id=#{id} AND status IN ('ENABLED','PAUSED')")
    int updateStatus(@Param("id") Long id,@Param("status") String status,@Param("actor") String actor);

    @Insert("INSERT INTO dp_quality_run(task_id,dataset_id,rule_set_id,rule_set_version,trigger_type,scan_scope,status,idempotency_key,trace_id,create_by) VALUES(#{taskId},#{datasetId},#{ruleSetId},#{ruleSetVersion},#{triggerType},#{scanScope},'QUEUED',#{idempotencyKey},#{traceId},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertRun(Map<String,Object> row);

    @Insert("INSERT INTO dp_quality_run(task_id,retry_of_run_id,attempt_no,dataset_id,rule_set_id,rule_set_version,trigger_type,scan_scope,status,idempotency_key,trace_id,create_by) VALUES(#{taskId},#{retryOfRunId},#{attemptNo},#{datasetId},#{ruleSetId},#{ruleSetVersion},'RETRY',#{scanScope},'QUEUED',NULL,#{traceId},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertRetryRun(Map<String,Object> row);

    @Update("UPDATE dp_quality_run SET status='RUNNING',started_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='QUEUED'")
    int startRun(@Param("id") Long id);

    @Update("UPDATE dp_quality_run SET status='FAILED',completed_at=CURRENT_TIMESTAMP(3),failure_code='QUEUE_REJECTED',failure_summary='质量任务队列繁忙，请稍后重试' WHERE id=#{id} AND status='QUEUED'")
    int failQueued(@Param("id") Long id);

    @Insert("INSERT INTO dp_quality_result(run_id,rule_id,status,rows_scanned,passed_count,failed_count,metric_value,expected_value,summary,duration_ms) VALUES(#{runId},#{ruleId},#{status},#{rows},#{passed},#{failed},#{metric},#{expected},#{summary},#{duration})")
    int insertResult(Map<String,Object> row);

    @Insert("INSERT INTO dp_quality_issue(run_id,rule_id,issue_key,severity,summary) VALUES(#{runId},#{ruleId},#{issueKey},#{severity},#{summary}) ON DUPLICATE KEY UPDATE summary=VALUES(summary)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertIssue(Map<String,Object> row);

    @Update("UPDATE dp_quality_run SET status=#{status},rows_scanned=#{rows},rules_total=#{total},rules_passed=#{passed},rules_failed=#{failed},issue_count=#{issues},completed_at=CURRENT_TIMESTAMP(3),failure_code=#{failureCode},failure_summary=#{failureSummary} WHERE id=#{id} AND status='RUNNING'")
    int finishRun(Map<String,Object> row);

    @Select("SELECT id,task_id AS taskId,retry_of_run_id AS retryOfRunId,attempt_no AS attemptNo,dataset_id AS datasetId,rule_set_id AS ruleSetId,rule_set_version AS ruleSetVersion,trigger_type AS triggerType,scan_scope AS scanScope,status,trace_id AS traceId,rows_scanned AS rowsScanned,rules_total AS rulesTotal,rules_passed AS rulesPassed,rules_failed AS rulesFailed,issue_count AS issueCount,started_at AS startedAt,completed_at AS completedAt,failure_code AS failureCode,failure_summary AS failureSummary,create_by AS createBy,create_time AS createTime FROM dp_quality_run WHERE task_id=#{taskId} ORDER BY id DESC LIMIT 100")
    List<Map<String,Object>> listRuns(@Param("taskId") Long taskId);

    @Select("SELECT id,rule_id AS ruleId,status,rows_scanned AS rowsScanned,passed_count AS passedCount,failed_count AS failedCount,metric_value AS metricValue,expected_value AS expectedValue,summary,duration_ms AS durationMs,create_time AS createTime FROM dp_quality_result WHERE run_id=#{runId} ORDER BY id")
    List<Map<String,Object>> listResults(@Param("runId") Long runId);

    @Select("SELECT i.id,i.run_id AS runId,i.rule_id AS ruleId,i.issue_key AS issueKey,i.severity,i.issue_status AS issueStatus,i.summary,i.claimed_by AS claimedBy,i.claimed_at AS claimedAt,i.sla_due_at AS slaDueAt,i.resolved_by AS resolvedBy,i.resolved_at AS resolvedAt,i.resolution,i.verify_run_id AS verifyRunId,i.fixed_at AS fixedAt,i.create_time AS createTime,r.dataset_id AS datasetId,r.rule_set_version AS ruleSetVersion,q.rule_code AS ruleCode,(i.issue_status='CLAIMED' AND i.sla_due_at IS NOT NULL AND i.sla_due_at<CURRENT_TIMESTAMP(3)) AS slaBreached FROM dp_quality_issue i JOIN dp_quality_run r ON r.id=i.run_id JOIN dp_quality_rule q ON q.id=i.rule_id WHERE r.dataset_id=#{datasetId} ORDER BY i.create_time DESC LIMIT 500")
    List<Map<String,Object>> listIssues(@Param("datasetId") Long datasetId);

    @Select("SELECT i.id,i.issue_status AS issueStatus,i.claimed_by AS claimedBy,r.dataset_id AS datasetId FROM dp_quality_issue i JOIN dp_quality_run r ON r.id=i.run_id WHERE i.id=#{issueId}")
    Map<String,Object> selectIssue(@Param("issueId") Long issueId);

    @Update("UPDATE dp_quality_issue SET issue_status='CLAIMED',claimed_by=#{actor},claimed_at=CURRENT_TIMESTAMP(3),sla_due_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL #{slaHours} HOUR) WHERE id=#{issueId} AND issue_status='OPEN'")
    int claimIssue(@Param("issueId") Long issueId,@Param("actor") String actor,@Param("slaHours") int slaHours);

    @Select("SELECT COUNT(*) FROM dp_quality_issue i JOIN dp_quality_run r ON r.id=i.run_id WHERE r.dataset_id=#{datasetId} AND i.issue_status='CLAIMED' AND i.sla_due_at IS NOT NULL AND i.sla_due_at<CURRENT_TIMESTAMP(3)")
    long overdueIssueCount(@Param("datasetId") Long datasetId);

    @Update("UPDATE dp_quality_issue SET issue_status='RESOLVED',resolved_by=#{actor},resolved_at=CURRENT_TIMESTAMP(3),resolution=#{note} WHERE id=#{issueId} AND issue_status='CLAIMED' AND claimed_by=#{actor}")
    int resolveIssue(@Param("issueId") Long issueId,@Param("actor") String actor,@Param("note") String note);

    /** Claimant marks fixed; remains open until a later PASS result for the same rule. */
    @Update("UPDATE dp_quality_issue SET issue_status='FIXED_PENDING',resolution=#{note},fixed_at=CURRENT_TIMESTAMP(3) WHERE id=#{issueId} AND issue_status='CLAIMED' AND claimed_by=#{actor}")
    int markFixedPending(@Param("issueId") Long issueId,@Param("actor") String actor,@Param("note") String note);

    @Update("UPDATE dp_quality_issue SET issue_status='RESOLVED',resolved_by=#{actor},resolved_at=CURRENT_TIMESTAMP(3),verify_run_id=#{verifyRunId} WHERE id=#{issueId} AND issue_status='FIXED_PENDING'")
    int closeAfterVerify(@Param("issueId") Long issueId,@Param("actor") String actor,@Param("verifyRunId") long verifyRunId);

    @Select("SELECT status FROM dp_quality_result WHERE run_id=#{runId} AND rule_id=#{ruleId} ORDER BY id DESC LIMIT 1")
    String latestResultStatus(@Param("runId") long runId,@Param("ruleId") long ruleId);

    @Insert("INSERT INTO dp_quality_issue_history(issue_id,actor,action,note) VALUES(#{issueId},#{actor},#{action},#{note})")
    int insertIssueHistory(@Param("issueId") Long issueId,@Param("actor") String actor,@Param("action") String action,@Param("note") String note);

    @Select("SELECT id,actor,action,note,create_time AS createTime FROM dp_quality_issue_history WHERE issue_id=#{issueId} ORDER BY id")
    List<Map<String,Object>> listIssueHistory(@Param("issueId") Long issueId);

    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS scanDay,COUNT(*) AS runCount,SUM(status='SUCCEEDED') AS passedRuns,SUM(status='FAILED') AS failedRuns,SUM(issue_count) AS issueCount,SUM(rows_scanned) AS rowsScanned FROM dp_quality_run WHERE dataset_id=#{datasetId} AND create_time>=CURRENT_TIMESTAMP(3)-INTERVAL #{days} DAY GROUP BY DATE_FORMAT(create_time,'%Y-%m-%d') ORDER BY scanDay")
    List<Map<String,Object>> qualityTrend(@Param("datasetId") Long datasetId,@Param("days") int days);

    @Select("SELECT id,status,scan_scope AS scanScope,rows_scanned AS rowsScanned,rules_total AS rulesTotal,rules_passed AS rulesPassed,rules_failed AS rulesFailed,issue_count AS issueCount,started_at AS startedAt,completed_at AS completedAt FROM dp_quality_run WHERE dataset_id=#{datasetId} AND scan_scope='FULL' AND rules_total>0 ORDER BY create_time DESC LIMIT 1")
    Map<String,Object> latestFullRun(@Param("datasetId") Long datasetId);

    @Update("UPDATE dp_quality_run SET status='FAILED',completed_at=CURRENT_TIMESTAMP(3),failure_code='SERVICE_RESTART',failure_summary='服务重启后，未完成的质量运行已标记中断；请重新运行' WHERE status IN ('QUEUED','RUNNING') AND create_time < CURRENT_TIMESTAMP(3)-INTERVAL 5 MINUTE")
    int recoverInterruptedRuns();
}
