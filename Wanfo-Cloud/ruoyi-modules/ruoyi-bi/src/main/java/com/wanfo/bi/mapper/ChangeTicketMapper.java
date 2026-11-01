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
public interface ChangeTicketMapper {

    @Select("""
            SELECT id,asset_id AS assetId,title,status,severity,source_type AS sourceType,
                   CAST(summary_json AS CHAR) AS summaryJson,create_by AS createBy,create_time AS createTime,
                   approved_by AS approvedBy,approved_at AS approvedAt
            FROM dp_change_ticket
            WHERE (#{assetId} IS NULL OR asset_id=#{assetId})
            ORDER BY id DESC LIMIT 200
            """)
    List<Map<String, Object>> list(@Param("assetId") Long assetId);

    @Select("""
            SELECT id,asset_id AS assetId,title,status,severity,source_type AS sourceType,
                   CAST(summary_json AS CHAR) AS summaryJson,create_by AS createBy,create_time AS createTime,
                   approved_by AS approvedBy,approved_at AS approvedAt
            FROM dp_change_ticket WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Select("""
            SELECT id,asset_id AS assetId,title,status,severity,source_type AS sourceType,
                   CAST(summary_json AS CHAR) AS summaryJson,create_by AS createBy,create_time AS createTime,
                   approved_by AS approvedBy,approved_at AS approvedAt
            FROM dp_change_ticket WHERE id=#{id} AND asset_id=#{assetId} AND status='APPROVED'
            """)
    Map<String, Object> approvedForAsset(@Param("id") Long id, @Param("assetId") Long assetId);

    @Insert("""
            INSERT INTO dp_change_ticket(asset_id,title,status,severity,source_type,summary_json,create_by)
            VALUES(#{assetId},#{title},#{status},#{severity},#{sourceType},CAST(#{summaryJson} AS JSON),#{createBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_change_ticket SET status='APPROVED',approved_by=#{actor},approved_at=NOW(3)
            WHERE id=#{id} AND status='OPEN'
            """)
    int approve(@Param("id") Long id, @Param("actor") String actor);

    @Update("""
            UPDATE dp_change_ticket SET status='CLOSED',approved_by=COALESCE(approved_by,#{actor}),approved_at=COALESCE(approved_at,NOW(3))
            WHERE id=#{id} AND status IN ('OPEN','APPROVED')
            """)
    int close(@Param("id") Long id, @Param("actor") String actor);
}
