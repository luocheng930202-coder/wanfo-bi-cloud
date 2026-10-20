package com.wanfo.bi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BiPrincipalMapper
{
    @Update("UPDATE bi_principal SET display_name=#{name} WHERE id=#{id}")
    void updateDisplayName(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id FROM bi_principal WHERE source = #{source} AND principal_type = #{type} AND external_id = #{externalId}")
    Long find(@Param("source") String source, @Param("type") String type, @Param("externalId") String externalId);

    // Unique key resolves concurrent first access. Existing principal IDs are never replaced.
    @Insert("INSERT INTO bi_principal(source, principal_type, external_id, display_name, create_time) "
            + "VALUES(#{source}, #{type}, #{externalId}, #{displayName}, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE id = id")
    void insert(@Param("source") String source, @Param("type") String type,
                @Param("externalId") String externalId, @Param("displayName") String displayName);
}
