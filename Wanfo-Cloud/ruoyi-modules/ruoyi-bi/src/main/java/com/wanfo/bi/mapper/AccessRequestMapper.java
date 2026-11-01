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
public interface AccessRequestMapper {
    @Insert("""
            INSERT INTO dp_access_request(status,purpose,resource_type,resource_id,asset_urn,actions_json,valid_days,
                requested_by,requester_user_id)
            VALUES('PENDING',#{purpose},#{resourceType},#{resourceId},#{assetUrn},#{actionsJson},#{validDays},
                #{requestedBy},#{requesterUserId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Select("SELECT id,status,purpose,resource_type AS resourceType,resource_id AS resourceId,asset_urn AS assetUrn,actions_json AS actionsJson,valid_days AS validDays,expires_at AS expiresAt,decision_note AS decisionNote,requested_by AS requestedBy,requester_user_id AS requesterUserId,requested_at AS requestedAt,reviewed_by AS reviewedBy,reviewed_at AS reviewedAt FROM dp_access_request WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id,status,purpose,resource_type AS resourceType,resource_id AS resourceId,asset_urn AS assetUrn,actions_json AS actionsJson,valid_days AS validDays,expires_at AS expiresAt,requested_by AS requestedBy,requester_user_id AS requesterUserId,requested_at AS requestedAt FROM dp_access_request WHERE status='PENDING' ORDER BY id DESC LIMIT 200")
    List<Map<String, Object>> listPending();

    @Select("SELECT id,status,purpose,resource_type AS resourceType,resource_id AS resourceId,asset_urn AS assetUrn,actions_json AS actionsJson,valid_days AS validDays,expires_at AS expiresAt,requested_by AS requestedBy,requested_at AS requestedAt,reviewed_by AS reviewedBy FROM dp_access_request WHERE requester_user_id=#{userId} ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> listMine(@Param("userId") Long userId);

    @Update("UPDATE dp_access_request SET status=#{status},decision_note=#{decisionNote},reviewed_by=#{reviewedBy},reviewed_at=NOW(3),expires_at=#{expiresAt} WHERE id=#{id} AND status='PENDING'")
    int decide(Map<String, Object> row);

    @Update("UPDATE dp_access_request SET status='EXPIRED' WHERE status='APPROVED' AND expires_at IS NOT NULL AND expires_at<=NOW(3)")
    int expireApproved();
}
