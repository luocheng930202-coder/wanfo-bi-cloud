package com.wanfo.bi.mapper;
import java.util.List;
import com.wanfo.bi.domain.BiChartDO;
import org.apache.ibatis.annotations.*;

public interface BiChartMapper {
    String COLS="id,dataset_id AS datasetId,name,type,definition_json AS definitionJson,version,create_by AS createBy,update_by AS updateBy";
    @Select("SELECT "+COLS+" FROM bi_chart_view WHERE del_flag='0' AND definition_json IS NOT NULL ORDER BY id DESC")
    List<BiChartDO> list();
    @Select("SELECT "+COLS+" FROM bi_chart_view WHERE id=#{id} AND del_flag='0'")
    BiChartDO get(Long id);
    @Insert("INSERT INTO bi_chart_view(name,dataset_id,type,definition_json,version,create_by,update_by,create_time,update_time) VALUES(#{name},#{datasetId},#{type},#{definitionJson},0,#{createBy},#{updateBy},NOW(),NOW())")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(BiChartDO row);
    @Update("UPDATE bi_chart_view SET name=#{name},dataset_id=#{datasetId},type=#{type},definition_json=#{definitionJson},version=version+1,update_by=#{updateBy},update_time=NOW() WHERE id=#{id} AND version=#{version} AND del_flag='0'")
    int update(BiChartDO row);
    @Update("UPDATE bi_chart_view SET del_flag='2',update_by=#{operator},update_time=NOW() WHERE id=#{id} AND del_flag='0' AND dashboard_id IS NULL AND NOT EXISTS(SELECT 1 FROM bi_dashboard_binding r JOIN bi_dashboard d ON d.id=r.dashboard_id WHERE r.chart_id=bi_chart_view.id AND d.del_flag='0')")
    int delete(@Param("id") Long id,@Param("operator") String operator);
}
