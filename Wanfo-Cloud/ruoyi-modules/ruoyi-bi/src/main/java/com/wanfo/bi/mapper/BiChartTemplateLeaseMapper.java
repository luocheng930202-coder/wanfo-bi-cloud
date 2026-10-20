package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiChartTemplateLeaseMapper {
 @Insert("INSERT INTO bi_chart_template_lease(chart_id,owner_principal,create_by,expire_time) VALUES(#{chartId},#{owner},#{actor},DATE_ADD(NOW(),INTERVAL 7 DAY))")
 int insert(@Param("chartId") Long chartId,@Param("owner") Long owner,@Param("actor") String actor);
 @Select("SELECT chart_id FROM bi_chart_template_lease WHERE owner_principal=#{owner} AND chart_id=#{chartId} AND expire_time>NOW()")
 Long find(@Param("owner") Long owner,@Param("chartId") Long chartId);
 @Delete("DELETE FROM bi_chart_template_lease WHERE owner_principal=#{owner} AND chart_id=#{chartId}")
 int delete(@Param("owner") Long owner,@Param("chartId") Long chartId);
 @Select("SELECT chart_id FROM bi_chart_template_lease WHERE expire_time<=NOW() LIMIT 200")
 List<Long> expired();
 @Delete("<script>DELETE FROM bi_chart_template_lease WHERE owner_principal=#{owner} AND chart_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
 int deleteMany(@Param("owner") Long owner,@Param("ids") List<Long> ids);
 @Delete("DELETE FROM bi_chart_template_lease WHERE chart_id=#{chartId}")
 int deleteByChartId(Long chartId);
}
