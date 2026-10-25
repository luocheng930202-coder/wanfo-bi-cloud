package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiSourceRefreshMapper {
    record Status(int intervalMinutes,String nextRun,String lastStatus){}
    @Insert("INSERT INTO bi_source_refresh(datasource_id,interval_minutes,next_run,updated_by) VALUES(#{id},#{minutes},IF(#{minutes}=0,NULL,DATE_ADD(NOW(),INTERVAL #{minutes} MINUTE)),#{actor}) ON DUPLICATE KEY UPDATE interval_minutes=VALUES(interval_minutes),next_run=VALUES(next_run),updated_by=VALUES(updated_by),lease_token=NULL,lease_until=NULL")
    int configure(@Param("id") Long id,@Param("minutes") int minutes,@Param("actor") String actor);
    @Select("SELECT r.datasource_id FROM bi_source_refresh r JOIN bi_datasource d ON d.id=r.datasource_id WHERE d.del_flag='0' AND d.status='0' AND d.type='api' AND r.interval_minutes>0 AND r.next_run<=NOW() AND (r.lease_until IS NULL OR r.lease_until<NOW()) ORDER BY r.next_run LIMIT 10") List<Long> due();
    @Update("UPDATE bi_source_refresh SET lease_token=#{token},lease_until=DATE_ADD(NOW(),INTERVAL 120 SECOND),next_run=DATE_ADD(NOW(),INTERVAL interval_minutes MINUTE) WHERE datasource_id=#{id} AND interval_minutes>0 AND next_run<=NOW() AND (lease_until IS NULL OR lease_until<NOW())")
    int claim(@Param("id") Long id,@Param("token") String token);
    @Select("SELECT interval_minutes AS intervalMinutes,CAST(next_run AS CHAR) AS nextRun,last_status AS lastStatus FROM bi_source_refresh WHERE datasource_id=#{id}") Status status(Long id);
    @Update("UPDATE bi_source_refresh SET last_status=#{status},lease_until=NULL,lease_token=NULL WHERE datasource_id=#{id} AND lease_token=#{token}")
    int finish(@Param("id") Long id,@Param("token") String token,@Param("status") String status);
    @Select("SELECT COUNT(*) FROM bi_source_refresh WHERE datasource_id=#{id} AND lease_token=#{token} AND lease_until>=NOW() AND interval_minutes>0")
    int owns(@Param("id") Long id,@Param("token") String token);
}
