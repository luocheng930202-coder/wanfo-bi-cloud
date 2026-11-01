package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QualityAlertMapper {

    @Select("""
            SELECT dedupe_key AS dedupeKey,dataset_id AS datasetId,rule_id AS ruleId,severity,
                   last_sent_at AS lastSentAt,last_issue_id AS lastIssueId,escalation_level AS escalationLevel
            FROM dp_quality_alert_dedupe WHERE dedupe_key=#{key}
            """)
    Map<String, Object> findDedupe(@Param("key") String key);

    @Insert("""
            INSERT INTO dp_quality_alert_dedupe(dedupe_key,dataset_id,rule_id,severity,last_sent_at,last_issue_id,escalation_level)
            VALUES(#{dedupeKey},#{datasetId},#{ruleId},#{severity},CURRENT_TIMESTAMP(3),#{lastIssueId},#{escalationLevel})
            ON DUPLICATE KEY UPDATE last_sent_at=CURRENT_TIMESTAMP(3),last_issue_id=VALUES(last_issue_id),
                escalation_level=VALUES(escalation_level)
            """)
    int upsertDedupe(Map<String, Object> row);

    @Insert("""
            INSERT INTO dp_quality_alert_log(issue_id,dataset_id,channel,recipient_user_id,escalation_level,title,body)
            VALUES(#{issueId},#{datasetId},#{channel},#{recipientUserId},#{escalationLevel},#{title},#{body})
            """)
    int insertLog(Map<String, Object> row);

    @Select("""
            SELECT i.id,i.rule_id AS ruleId,i.severity,i.summary,i.claimed_by AS claimedBy,i.sla_due_at AS slaDueAt,
                   r.dataset_id AS datasetId,q.rule_code AS ruleCode
            FROM dp_quality_issue i
            JOIN dp_quality_run r ON r.id=i.run_id
            JOIN dp_quality_rule q ON q.id=i.rule_id
            WHERE i.issue_status='CLAIMED' AND i.sla_due_at IS NOT NULL AND i.sla_due_at<CURRENT_TIMESTAMP(3)
            ORDER BY i.sla_due_at ASC LIMIT 200
            """)
    List<Map<String, Object>> listOverdueClaimed();

    @Select("""
            SELECT i.id,i.rule_id AS ruleId,i.issue_status AS issueStatus,r.dataset_id AS datasetId
            FROM dp_quality_issue i
            JOIN dp_quality_run r ON r.id=i.run_id
            WHERE r.dataset_id=#{datasetId} AND i.rule_id=#{ruleId} AND i.issue_status='FIXED_PENDING'
            ORDER BY i.id DESC LIMIT 50
            """)
    List<Map<String, Object>> listFixedPending(@Param("datasetId") Long datasetId, @Param("ruleId") Long ruleId);
}
