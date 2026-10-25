package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BiReportSubscriptionMapper {
    @Select("SELECT id,name,source_type AS sourceType,source_id AS sourceId,panel_key AS panelKey,format,interval_minutes AS intervalMinutes,enabled,next_run AS nextRun,last_run AS lastRun,last_status AS lastStatus,last_message AS lastMessage,JSON_LENGTH(recipients_json) AS recipientCount,create_time AS createTime FROM bi_report_subscription WHERE owner_principal=#{owner} ORDER BY id DESC")
    List<Map<String,Object>> list(@Param("owner") Long owner);

    @Select("SELECT * FROM bi_report_subscription WHERE id=#{id} AND owner_principal=#{owner}")
    Map<String,Object> owned(@Param("id") Long id,@Param("owner") Long owner);

    @Insert("INSERT INTO bi_report_subscription(owner_principal,actor_json,source_type,source_id,panel_key,format,name,recipients_json,interval_minutes,enabled,next_run,create_by) VALUES(#{owner},#{actorJson},#{sourceType},#{sourceId},#{panelKey},#{format},#{name},#{recipientsJson},#{intervalMinutes},1,DATE_ADD(NOW(),INTERVAL #{intervalMinutes} MINUTE),#{actorName})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Map<String,Object> row);

    @Update("UPDATE bi_report_subscription SET recipients_json=#{recipientsJson},interval_minutes=#{intervalMinutes},enabled=#{enabled},next_run=DATE_ADD(NOW(),INTERVAL #{intervalMinutes} MINUTE),failure_count=0,last_status=IF(#{enabled}=1,'waiting','paused') WHERE id=#{id} AND owner_principal=#{owner}")
    int update(Map<String,Object> row);

    @Delete("DELETE FROM bi_report_subscription WHERE id=#{id} AND owner_principal=#{owner}")
    int delete(@Param("id") Long id,@Param("owner") Long owner);

    @Select("SELECT id FROM bi_report_subscription WHERE enabled=1 AND next_run<=NOW() ORDER BY next_run,id LIMIT 10")
    List<Long> dueIds();

    @Update("UPDATE bi_report_subscription SET next_run=DATE_ADD(NOW(),INTERVAL 5 MINUTE),run_started=NOW(),last_status='running' WHERE id=#{id} AND enabled=1 AND next_run<=NOW()")
    int claim(@Param("id") Long id);

    @Select("SELECT * FROM bi_report_subscription WHERE id=#{id}")
    Map<String,Object> get(@Param("id") Long id);

    @Update("UPDATE bi_report_subscription SET last_run=NOW(),last_status=#{status},last_message=#{message},failure_count=#{failureCount},enabled=IF(enabled=0,0,#{enabled}),next_run=#{nextRun} WHERE id=#{id}")
    int complete(Map<String,Object> row);

    @Insert("INSERT INTO bi_report_subscription_audit(subscription_id,owner_principal,started_at,finished_at,status,attempt,row_count,recipient_count,message) VALUES(#{subscriptionId},#{owner},#{startedAt},NOW(),#{status},#{attempt},#{rowCount},#{recipientCount},#{message})")
    int audit(Map<String,Object> row);

    @Select("SELECT id,started_at AS startedAt,finished_at AS finishedAt,status,attempt,row_count AS rowCount,recipient_count AS recipientCount,message FROM bi_report_subscription_audit WHERE subscription_id=#{id} AND owner_principal=#{owner} ORDER BY id DESC LIMIT 50")
    List<Map<String,Object>> history(@Param("id") Long id,@Param("owner") Long owner);
}
