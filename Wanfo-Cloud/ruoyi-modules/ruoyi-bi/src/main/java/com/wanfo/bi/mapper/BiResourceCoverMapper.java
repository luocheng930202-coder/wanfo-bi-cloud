package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BiResourceCoverMapper {
    @Select("""
            <script>
            SELECT resource_id AS resourceId, version FROM bi_resource_cover
            WHERE resource_kind=#{kind} AND resource_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> versions(@Param("kind") String kind, @Param("ids") List<Long> ids);

    @Select("SELECT version, media_type AS mediaType, data_url AS dataUrl FROM bi_resource_cover WHERE resource_kind=#{kind} AND resource_id=#{id}")
    Map<String, Object> find(@Param("kind") String kind, @Param("id") Long id);

    @Insert("""
            INSERT INTO bi_resource_cover(resource_kind,resource_id,version,media_type,data_url,update_time)
            VALUES(#{kind},#{id},#{version},#{media},#{data},NOW())
            ON DUPLICATE KEY UPDATE version=VALUES(version), media_type=VALUES(media_type), data_url=VALUES(data_url), update_time=NOW()
            """)
    int save(@Param("kind") String kind, @Param("id") Long id, @Param("version") int version,
             @Param("media") String media, @Param("data") String data);
}
