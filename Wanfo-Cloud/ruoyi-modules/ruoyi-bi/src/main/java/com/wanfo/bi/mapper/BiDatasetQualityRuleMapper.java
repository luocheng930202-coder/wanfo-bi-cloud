package com.wanfo.bi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface BiDatasetQualityRuleMapper {
    @Select("SELECT rules_json FROM bi_dataset_quality_rule WHERE dataset_id=#{id}") String get(@Param("id") Long id);
    @Insert("INSERT INTO bi_dataset_quality_rule(dataset_id,rules_json,update_by) VALUES(#{id},#{json},#{user}) ON DUPLICATE KEY UPDATE rules_json=VALUES(rules_json),update_by=VALUES(update_by),update_time=CURRENT_TIMESTAMP")
    int save(@Param("id") Long id,@Param("json") String json,@Param("user") String user);
}
