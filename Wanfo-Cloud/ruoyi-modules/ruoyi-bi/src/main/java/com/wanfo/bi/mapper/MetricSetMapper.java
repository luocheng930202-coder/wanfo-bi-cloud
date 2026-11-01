package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MetricSetMapper {
    @Select("SELECT id,set_code AS setCode,name,theme,description,status,create_by AS createBy,create_time AS createTime FROM dp_metric_set ORDER BY id DESC LIMIT 500")
    List<Map<String, Object>> list();

    @Select("SELECT id,set_code AS setCode,name,theme,description,status,create_by AS createBy,create_time AS createTime FROM dp_metric_set WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id FROM dp_metric_set WHERE set_code=#{code}")
    Long findIdByCode(@Param("code") String code);

    @Insert("INSERT INTO dp_metric_set(set_code,name,theme,description,status,create_by) VALUES(#{setCode},#{name},#{theme},#{description},#{status},#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("UPDATE dp_metric_set SET name=#{name},theme=#{theme},description=#{description},status=#{status},update_by=#{updateBy} WHERE id=#{id}")
    int update(Map<String, Object> row);

    @Delete("DELETE FROM dp_metric_set WHERE id=#{id}")
    int delete(@Param("id") Long id);

    @Select("SELECT m.set_id AS setId,m.metric_id AS metricId,m.sort_no AS sortNo,b.code AS metricCode,b.name AS metricName,b.publish_status AS publishStatus FROM dp_metric_set_member m LEFT JOIN bi_metric b ON b.id=m.metric_id WHERE m.set_id=#{setId} ORDER BY m.sort_no,m.metric_id")
    List<Map<String, Object>> members(@Param("setId") Long setId);

    @Delete("DELETE FROM dp_metric_set_member WHERE set_id=#{setId}")
    int clearMembers(@Param("setId") Long setId);

    @Insert("INSERT INTO dp_metric_set_member(set_id,metric_id,sort_no) VALUES(#{setId},#{metricId},#{sortNo})")
    int addMember(@Param("setId") Long setId, @Param("metricId") Long metricId, @Param("sortNo") int sortNo);
}
