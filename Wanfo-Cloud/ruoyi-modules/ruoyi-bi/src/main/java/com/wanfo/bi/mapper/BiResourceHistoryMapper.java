package com.wanfo.bi.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
public interface BiResourceHistoryMapper {
    record Row(String resourceType,Long resourceId,int version,String operation,String definitionJson,String actor,String createdAt){}
    @Insert("INSERT IGNORE INTO bi_resource_history(resource_type,resource_id,version,operation,definition_json,actor) VALUES(#{resourceType},#{resourceId},#{version},#{operation},#{definitionJson},#{actor})") int insert(Row row);
    @Select("SELECT resource_type AS resourceType,resource_id AS resourceId,version,operation,NULL AS definitionJson,actor,CAST(CAST(UNIX_TIMESTAMP(created_at) AS DECIMAL(20,0))*1000 AS CHAR(32)) AS createdAt FROM bi_resource_history WHERE resource_type=#{type} AND resource_id=#{id} ORDER BY version DESC LIMIT 50")
    List<Row> list(@Param("type") String type,@Param("id") Long id);
    @Select("SELECT resource_type AS resourceType,resource_id AS resourceId,version,operation,definition_json AS definitionJson,actor,CAST(CAST(UNIX_TIMESTAMP(created_at) AS DECIMAL(20,0))*1000 AS CHAR(32)) AS createdAt FROM bi_resource_history WHERE resource_type=#{type} AND resource_id=#{id} AND version=#{version}")
    Row get(@Param("type") String type,@Param("id") Long id,@Param("version") int version);
    @Delete("DELETE FROM bi_resource_history WHERE resource_type=#{type} AND resource_id=#{id} AND version < #{version}-49")
    int prune(@Param("type") String type,@Param("id") Long id,@Param("version") int version);
}
