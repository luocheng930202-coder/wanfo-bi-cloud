package com.wanfo.bi.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DependentFieldMigrationMapper {
    @Insert("INSERT INTO dp_dependent_field_migration(datasource_id,status,mapping_json,before_snapshot_json,create_by) VALUES(#{datasourceId},'APPLIED',#{mappingJson},#{beforeSnapshotJson},#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Select("SELECT id,datasource_id AS datasourceId,status,mapping_json AS mappingJson,before_snapshot_json AS beforeSnapshotJson,create_by AS createBy,create_time AS createTime FROM dp_dependent_field_migration WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id,datasource_id AS datasourceId,status,create_by AS createBy,create_time AS createTime FROM dp_dependent_field_migration WHERE datasource_id=#{datasourceId} ORDER BY id DESC LIMIT 20")
    java.util.List<Map<String, Object>> listByDatasource(@Param("datasourceId") Long datasourceId);

    @Update("UPDATE dp_dependent_field_migration SET status='ROLLED_BACK',rolled_back_by=#{actor},rolled_back_at=NOW(3) WHERE id=#{id} AND status='APPLIED'")
    int markRolledBack(@Param("id") Long id, @Param("actor") String actor);
}
