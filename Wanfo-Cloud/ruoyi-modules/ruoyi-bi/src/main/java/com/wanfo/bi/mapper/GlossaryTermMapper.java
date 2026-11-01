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
public interface GlossaryTermMapper {

    @Select("""
            SELECT id,term_code AS termCode,name,category,definition_text AS definitionText,owner_name AS ownerName,
                   certification_status AS certificationStatus,metric_id AS metricId,create_by AS createBy,
                   create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_glossary_term ORDER BY id DESC LIMIT 500
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT id,term_code AS termCode,name,category,definition_text AS definitionText,owner_name AS ownerName,
                   certification_status AS certificationStatus,metric_id AS metricId,create_by AS createBy,
                   create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_glossary_term WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Select("""
            SELECT id,term_code AS termCode,name,category,definition_text AS definitionText,owner_name AS ownerName,
                   certification_status AS certificationStatus,metric_id AS metricId
            FROM dp_glossary_term WHERE metric_id=#{metricId} ORDER BY id DESC LIMIT 50
            """)
    List<Map<String, Object>> listByMetric(@Param("metricId") Long metricId);

    @Select("SELECT id FROM bi_metric WHERE id=#{id}")
    Long metricExists(@Param("id") Long id);

    @Insert("""
            INSERT INTO dp_glossary_term(term_code,name,category,definition_text,owner_name,certification_status,metric_id,create_by)
            VALUES(#{termCode},#{name},#{category},#{definitionText},#{ownerName},#{certificationStatus},#{metricId},#{createBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_glossary_term SET name=#{name},category=#{category},definition_text=#{definitionText},
                owner_name=#{ownerName},certification_status=#{certificationStatus},metric_id=#{metricId},update_by=#{updateBy}
            WHERE id=#{id}
            """)
    int update(Map<String, Object> row);
}
