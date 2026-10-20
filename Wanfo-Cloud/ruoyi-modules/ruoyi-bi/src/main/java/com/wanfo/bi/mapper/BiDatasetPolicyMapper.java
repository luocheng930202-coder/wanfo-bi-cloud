package com.wanfo.bi.mapper;
import java.util.List;
import com.wanfo.bi.security.DatasetDataPolicy.Rule;
import org.apache.ibatis.annotations.*;
public interface BiDatasetPolicyMapper {
    @Select("SELECT id,dataset_id AS datasetId,principal_id AS principalId,field_name AS fieldName,rule_kind AS ruleKind,rule_value AS ruleValue,create_by AS createBy FROM bi_dataset_policy WHERE dataset_id=#{id} ORDER BY id")
    List<Rule> list(Long id);
    @Insert("INSERT INTO bi_dataset_policy(dataset_id,principal_id,field_name,rule_kind,rule_value,create_by) VALUES(#{datasetId},#{principalId},#{fieldName},#{ruleKind},#{ruleValue},#{createBy})")
    void insert(Rule rule);
    @Delete("DELETE FROM bi_dataset_policy WHERE dataset_id=#{datasetId} AND id=#{ruleId}")
    void delete(@Param("datasetId") Long datasetId,@Param("ruleId") Long ruleId);
    @Delete("DELETE FROM bi_dataset_policy WHERE dataset_id=#{id}")
    void deleteWithDataset(Long id);
}
