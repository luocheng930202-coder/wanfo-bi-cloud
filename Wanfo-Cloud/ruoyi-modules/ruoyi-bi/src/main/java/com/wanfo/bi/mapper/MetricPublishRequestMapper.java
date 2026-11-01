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
public interface MetricPublishRequestMapper {
    @Insert("""
            INSERT INTO dp_metric_publish_request(metric_id,status,request_note,requested_by,metric_version)
            VALUES(#{metricId},'PENDING',#{requestNote},#{requestedBy},#{metricVersion})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Select("SELECT id,metric_id AS metricId,status,request_note AS requestNote,decision_note AS decisionNote,requested_by AS requestedBy,requested_at AS requestedAt,reviewed_by AS reviewedBy,reviewed_at AS reviewedAt,metric_version AS metricVersion FROM dp_metric_publish_request WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM dp_metric_publish_request WHERE metric_id=#{metricId} AND status='PENDING'")
    int countPending(@Param("metricId") Long metricId);

    @Select("SELECT id,metric_id AS metricId,status,request_note AS requestNote,requested_by AS requestedBy,requested_at AS requestedAt,metric_version AS metricVersion FROM dp_metric_publish_request WHERE status='PENDING' ORDER BY id DESC LIMIT 200")
    List<Map<String, Object>> listPending();

    @Select("SELECT id,metric_id AS metricId,status,request_note AS requestNote,decision_note AS decisionNote,requested_by AS requestedBy,requested_at AS requestedAt,reviewed_by AS reviewedBy,reviewed_at AS reviewedAt,metric_version AS metricVersion FROM dp_metric_publish_request WHERE metric_id=#{metricId} ORDER BY id DESC LIMIT 50")
    List<Map<String, Object>> listByMetric(@Param("metricId") Long metricId);

    @Update("UPDATE dp_metric_publish_request SET status=#{status},decision_note=#{decisionNote},reviewed_by=#{reviewedBy},reviewed_at=NOW(3) WHERE id=#{id} AND status='PENDING'")
    int decide(@Param("id") Long id, @Param("status") String status, @Param("decisionNote") String decisionNote,
            @Param("reviewedBy") String reviewedBy);
}
