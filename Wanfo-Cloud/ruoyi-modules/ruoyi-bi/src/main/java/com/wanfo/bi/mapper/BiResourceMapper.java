package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface BiResourceMapper
{
    @Select("<script>SELECT DISTINCT r.resource_id FROM bi_resource r LEFT JOIN bi_resource_grant g ON g.resource_type=r.resource_type AND g.resource_id=r.resource_id AND g.action=#{action} AND g.principal_id IN <foreach collection='principals' item='principal' open='(' separator=',' close=')'>#{principal}</foreach> AND (g.expires_at IS NULL OR g.expires_at&gt;NOW(3)) WHERE r.resource_type=#{type} AND (r.owner_id IN <foreach collection='principals' item='principal' open='(' separator=',' close=')'>#{principal}</foreach> OR g.principal_id IS NOT NULL) ORDER BY r.resource_id</script>")
    List<Long> visibleResourceIds(@Param("type") String type, @Param("action") String action,
                                  @Param("principals") List<Long> principals);

    @Select("SELECT resource_id FROM bi_resource WHERE resource_type=#{type} ORDER BY resource_id")
    List<Long> allResourceIds(@Param("type") String type);

    @Select("SELECT owner_id FROM bi_resource WHERE resource_type=#{type} AND resource_id=#{id}")
    Long owner(@Param("type") String type, @Param("id") Long id);

    @Select("SELECT principal_id FROM bi_resource_grant WHERE resource_type=#{type} AND resource_id=#{id} AND action=#{action} AND (expires_at IS NULL OR expires_at>NOW(3))")
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
            + "ON DUPLICATE KEY UPDATE action=action,expires_at=NULL")
    void grant(@Param("type") String type, @Param("id") Long id,
               @Param("principal") Long principal, @Param("action") String action);

    @Insert("INSERT INTO bi_resource_grant(resource_type,resource_id,principal_id,action,expires_at) VALUES(#{type},#{id},#{principal},#{action},#{expiresAt}) "
            + "ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)")
    void grantUntil(@Param("type") String type, @Param("id") Long id, @Param("principal") Long principal,
            @Param("action") String action, @Param("expiresAt") java.time.LocalDateTime expiresAt);

    @Delete("DELETE FROM bi_resource_grant WHERE resource_type=#{type} AND resource_id=#{id} AND principal_id=#{principal} AND action=#{action}")
    void revoke(@Param("type") String type, @Param("id") Long id,
                @Param("principal") Long principal, @Param("action") String action);

    @Select("SELECT resource_type AS resourceType,resource_id AS resourceId,principal_id AS principalId,action,expires_at AS expiresAt FROM bi_resource_grant WHERE expires_at IS NOT NULL AND expires_at<=NOW(3) ORDER BY expires_at LIMIT 500")
    List<Map<String, Object>> listExpiredGrants();

    @Delete("DELETE FROM bi_resource_grant WHERE expires_at IS NOT NULL AND expires_at<=NOW(3)")
    int deleteExpiredGrants();
}
