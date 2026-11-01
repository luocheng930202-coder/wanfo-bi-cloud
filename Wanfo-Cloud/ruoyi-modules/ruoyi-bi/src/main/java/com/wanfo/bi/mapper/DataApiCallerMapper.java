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
public interface DataApiCallerMapper {
    String COLS = "id,org_code AS orgCode,org_name AS orgName,contact_name AS contactName,contact_email AS contactEmail,"
            + "notes,status,calls_per_minute AS callsPerMinute,daily_quota AS dailyQuota,canary_weight AS canaryWeight,"
            + "create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM dp_data_api_caller ORDER BY id DESC")
    List<Map<String, Object>> list();

    @Select("SELECT " + COLS + " FROM dp_data_api_caller WHERE status='ACTIVE' ORDER BY org_name,id")
    List<Map<String, Object>> listActive();

    @Select("SELECT " + COLS + " FROM dp_data_api_caller WHERE id=#{id}")
    Map<String, Object> get(@Param("id") Long id);

    @Select("SELECT " + COLS + " FROM dp_data_api_caller WHERE org_code=#{code}")
    Map<String, Object> byCode(@Param("code") String code);

    @Insert("""
            INSERT INTO dp_data_api_caller(org_code,org_name,contact_name,contact_email,notes,status,
                calls_per_minute,daily_quota,canary_weight,create_by,update_by)
            VALUES(#{orgCode},#{orgName},#{contactName},#{contactEmail},#{notes},'ACTIVE',
                #{callsPerMinute},#{dailyQuota},#{canaryWeight},#{user},#{user})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_data_api_caller SET org_name=#{orgName},contact_name=#{contactName},contact_email=#{contactEmail},
                notes=#{notes},status=#{status},calls_per_minute=#{callsPerMinute},daily_quota=#{dailyQuota},
                canary_weight=#{canaryWeight},update_by=#{user},update_time=NOW(3)
            WHERE id=#{id}
            """)
    int update(Map<String, Object> row);

    @Select("""
            SELECT c.api_id AS apiId, ca.id AS callerId, ca.org_code AS orgCode, ca.org_name AS orgName,
                   COUNT(*) AS callCount30d, COUNT(DISTINCT c.api_key_id) AS keyCount30d, MAX(c.called_at) AS lastCalledAt
            FROM dp_data_api_call c
            INNER JOIN dp_data_api_key k ON k.id=c.api_key_id
            INNER JOIN dp_data_api_caller ca ON ca.id=k.caller_id
            WHERE c.called_at >= DATE_SUB(NOW(3), INTERVAL 30 DAY)
            GROUP BY c.api_id, ca.id, ca.org_code, ca.org_name
            ORDER BY c.api_id, callCount30d DESC
            """)
    List<Map<String, Object>> callerOrgStats30d();
}
