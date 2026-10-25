package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BiReportSubscriptionMapper {
    @Select("SELECT id,name,source_type AS sourceType,source_id AS sourceId,panel_key AS panelKey,format,interval_minutes AS intervalMinutes,channels_json AS channels,schedule_json AS schedule,template_title AS templateTitle,template_body AS templateBody,enabled,COALESCE(next_run_utc,next_run) AS nextRun,last_run AS lastRun,last_status AS lastStatus,last_message AS lastMessage,JSON_LENGTH(recipients_json) AS recipientCount,create_time AS createTime FROM bi_report_subscription WHERE owner_principal=#{owner} ORDER BY id DESC")
    List<Map<String,Object>> list(@Param("owner") Long owner);

    @Select("SELECT * FROM bi_report_subscription WHERE id=#{id} AND owner_principal=#{owner}")
    Map<String,Object> owned(@Param("id") Long id,@Param("owner") Long owner);

    @Insert("INSERT INTO bi_report_subscription(owner_principal,actor_json,source_type,source_id,panel_key,format,name,recipients_json,channels_json,interval_minutes,schedule_json,next_run_utc,template_title,template_body,enabled,next_run,create_by) VALUES(#{owner},#{actorJson},#{sourceType},#{sourceId},#{panelKey},#{format},#{name},#{recipientsJson},#{channelsJson},#{intervalMinutes},#{scheduleJson},#{nextRunUtc},#{templateTitle},#{templateBody},1,DATE_ADD(NOW(),INTERVAL #{intervalMinutes} MINUTE),#{actorName})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Map<String,Object> row);

    @Update("UPDATE bi_report_subscription SET recipients_json=#{recipientsJson},channels_json=#{channelsJson},interval_minutes=#{intervalMinutes},schedule_json=#{scheduleJson},next_run_utc=#{nextRunUtc},template_title=#{templateTitle},template_body=#{templateBody},enabled=#{enabled},next_run=DATE_ADD(NOW(),INTERVAL #{intervalMinutes} MINUTE),failure_count=0,last_status=IF(#{enabled}=1,'waiting','paused') WHERE id=#{id} AND owner_principal=#{owner}")
    int update(Map<String,Object> row);

    @Delete("DELETE FROM bi_report_subscription WHERE id=#{id} AND owner_principal=#{owner}")
    int delete(@Param("id") Long id,@Param("owner") Long owner);

    @Select("SELECT id FROM bi_report_subscription WHERE enabled=1 AND ((next_run_utc IS NOT NULL AND next_run_utc<=UTC_TIMESTAMP()) OR (next_run_utc IS NULL AND next_run<=NOW())) ORDER BY COALESCE(next_run_utc,next_run),id LIMIT 10")
    List<Long> dueIds();

    @Update("UPDATE bi_report_subscription SET next_run=IF(next_run_utc IS NULL,DATE_ADD(NOW(),INTERVAL 5 MINUTE),next_run),next_run_utc=IF(next_run_utc IS NULL,NULL,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 MINUTE)),run_started=NOW(),last_status='running' WHERE id=#{id} AND enabled=1 AND ((next_run_utc IS NOT NULL AND next_run_utc<=UTC_TIMESTAMP()) OR (next_run_utc IS NULL AND next_run<=NOW()))")
    int claim(@Param("id") Long id);

    @Select("SELECT * FROM bi_report_subscription WHERE id=#{id}")
    Map<String,Object> get(@Param("id") Long id);

    @Update("UPDATE bi_report_subscription SET last_run=NOW(),last_status=#{status},last_message=#{message},failure_count=#{failureCount},enabled=IF(enabled=0,0,#{enabled}),next_run=COALESCE(#{nextRun},next_run),next_run_utc=#{nextRunUtc} WHERE id=#{id}")
    int complete(Map<String,Object> row);

    @Insert("INSERT INTO bi_report_subscription_audit(subscription_id,subscription_name,source_type,source_id,owner_principal,started_at,finished_at,status,attempt,row_count,recipient_count,message) VALUES(#{subscriptionId},#{subscriptionName},#{sourceType},#{sourceId},#{owner},#{startedAt},NOW(),#{status},#{attempt},#{rowCount},#{recipientCount},#{message})")
    int audit(Map<String,Object> row);

    @Select("SELECT id,started_at AS startedAt,finished_at AS finishedAt,status,attempt,row_count AS rowCount,recipient_count AS recipientCount,message FROM bi_report_subscription_audit WHERE subscription_id=#{id} AND owner_principal=#{owner} ORDER BY id DESC LIMIT 50")
    List<Map<String,Object>> history(@Param("id") Long id,@Param("owner") Long owner);

    @Select("SELECT n.id,n.title,n.body,n.created_at AS createdAt,n.read_at AS readAt,s.source_type AS sourceType,s.source_id AS sourceId FROM bi_report_notification n LEFT JOIN bi_report_subscription s ON s.id=n.subscription_id WHERE n.user_id=#{userId} ORDER BY n.created_at DESC,n.id DESC LIMIT 100")
    List<Map<String,Object>> notifications(@Param("userId") Long userId);

    @Update("UPDATE bi_report_notification SET read_at=COALESCE(read_at,NOW()) WHERE id=#{id} AND user_id=#{userId}")
    int markNotificationRead(@Param("id") Long id,@Param("userId") Long userId);

    @Insert("INSERT INTO bi_report_notification(user_id,subscription_id,title,body) VALUES(#{userId},#{subscriptionId},#{title},#{body})")
    int addNotification(Map<String,Object> row);

    @Select("SELECT a.id,a.subscription_id AS subscriptionId,a.owner_principal AS ownerId,COALESCE(NULLIF(a.source_type,''),s.source_type) AS sourceType,COALESCE(a.source_id,s.source_id) AS sourceId,COALESCE(NULLIF(a.subscription_name,''),s.name) AS subscriptionName,a.started_at AS startedAt,a.finished_at AS finishedAt,a.status,a.attempt,a.row_count AS rowCount,a.recipient_count AS recipientCount,a.message FROM bi_report_subscription_audit a LEFT JOIN bi_report_subscription s ON s.id=a.subscription_id ORDER BY a.id DESC LIMIT 200")
    List<Map<String,Object>> globalAudit();
}
