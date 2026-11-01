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
public interface DpQualityRuleVersionMapper {
    @Select("SELECT dataset_id AS datasetId,rules_json AS rulesJson,update_by AS updateBy,update_time AS updateTime FROM bi_dataset_quality_rule ORDER BY dataset_id")
    List<Map<String,Object>> legacyRows();

    @Select("SELECT COUNT(*) FROM dp_quality_rule_set WHERE dataset_id=#{datasetId} AND remark='Imported from bi_dataset_quality_rule V25'")
    int legacyImported(@Param("datasetId") Long datasetId);

    @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM dp_quality_rule_set WHERE dataset_id=#{datasetId}")
    int nextVersion(@Param("datasetId") Long datasetId);

    @Insert("INSERT INTO dp_quality_rule_set(dataset_id,version_no,name,status,create_by,remark) VALUES(#{datasetId},#{version},#{name},'DRAFT',#{actor},#{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSet(Map<String,Object> row);

    @Insert("INSERT INTO dp_quality_rule(rule_set_id,rule_code,rule_type,field_name,severity,enabled,config_json) VALUES(#{setId},#{code},#{type},#{field},#{severity},#{enabled},CAST(#{config} AS JSON))")
    int insertRule(@Param("setId") Long setId,@Param("code") String code,@Param("type") String type,
            @Param("field") String field,@Param("severity") String severity,@Param("enabled") boolean enabled,
            @Param("config") String config);

    @Select("SELECT id,dataset_id AS datasetId,version_no AS version,name,status,definition_hash AS definitionHash,create_by AS createBy,create_time AS createTime,publish_by AS publishBy,publish_time AS publishTime,remark FROM dp_quality_rule_set WHERE dataset_id=#{datasetId} ORDER BY version_no DESC")
    List<Map<String,Object>> listSets(@Param("datasetId") Long datasetId);

    @Select("SELECT id,dataset_id AS datasetId,version_no AS version,name,status,definition_hash AS definitionHash,create_by AS createBy,create_time AS createTime,publish_by AS publishBy,publish_time AS publishTime,remark FROM dp_quality_rule_set WHERE id=#{id} AND dataset_id=#{datasetId}")
    Map<String,Object> selectSet(@Param("datasetId") Long datasetId,@Param("id") Long id);

    @Select("SELECT id,rule_set_id AS ruleSetId,rule_code AS code,rule_type AS type,field_name AS field,severity,enabled,config_json AS config FROM dp_quality_rule WHERE rule_set_id=#{setId} ORDER BY id")
    List<Map<String,Object>> listRules(@Param("setId") Long setId);

    @Update("UPDATE dp_quality_rule_set SET status='PUBLISHED',definition_hash=#{hash},publish_by=#{actor},publish_time=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND dataset_id=#{datasetId} AND status='DRAFT'")
    int publish(@Param("datasetId") Long datasetId,@Param("id") Long id,@Param("hash") String hash,@Param("actor") String actor);
}
