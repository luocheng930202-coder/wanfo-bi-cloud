package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface BiResourceMapper
{
    @Select("SELECT owner_id FROM bi_resource WHERE resource_type=#{type} AND resource_id=#{id}")
    Long owner(@Param("type") String type, @Param("id") Long id);

    @Select("SELECT principal_id FROM bi_resource_grant WHERE resource_type=#{type} AND resource_id=#{id} AND action=#{action}")
    List<Long> granted(@Param("type") String type, @Param("id") Long id, @Param("action") String action);

    @Insert("INSERT INTO bi_resource(resource_type,resource_id,owner_id) VALUES(#{type},#{id},#{owner})")
    void register(@Param("type") String type, @Param("id") Long id, @Param("owner") Long owner);

    @Select("SELECT g.principal_id AS principalId,p.display_name AS displayName,p.principal_type AS principalType,g.action "
            + "FROM bi_resource_grant g JOIN bi_principal p ON p.id=g.principal_id "
            + "WHERE g.resource_type=#{type} AND g.resource_id=#{id} ORDER BY p.id,g.action")
    List<Map<String, Object>> list(@Param("type") String type, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM bi_principal WHERE id=#{id}")
    int principalExists(Long id);

    @Insert("INSERT INTO bi_resource_grant(resource_type,resource_id,principal_id,action) VALUES(#{type},#{id},#{principal},#{action}) "
            + "ON DUPLICATE KEY UPDATE action=action")
    void grant(@Param("type") String type, @Param("id") Long id,
               @Param("principal") Long principal, @Param("action") String action);

    @Delete("DELETE FROM bi_resource_grant WHERE resource_type=#{type} AND resource_id=#{id} AND principal_id=#{principal} AND action=#{action}")
    void revoke(@Param("type") String type, @Param("id") Long id,
                @Param("principal") Long principal, @Param("action") String action);
}
