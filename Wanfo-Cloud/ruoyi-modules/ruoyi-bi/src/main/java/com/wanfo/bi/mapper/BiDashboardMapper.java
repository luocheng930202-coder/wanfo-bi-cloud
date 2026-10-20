package com.wanfo.bi.mapper;
import java.util.List;
import com.wanfo.bi.domain.BiDashboardDO;
import org.apache.ibatis.annotations.*;
public interface BiDashboardMapper {
    String COLS="id,name,definition_json AS definitionJson,published_json AS publishedJson,version,published_version AS publishedVersion,create_by AS createBy,update_by AS updateBy";
    @Select("SELECT "+COLS+" FROM bi_dashboard WHERE del_flag='0' AND definition_json IS NOT NULL ORDER BY id DESC") List<BiDashboardDO> list();
    @Select("SELECT "+COLS+" FROM bi_dashboard WHERE id=#{id} AND del_flag='0'") BiDashboardDO get(Long id);
    @Insert("INSERT INTO bi_dashboard(name,definition_json,version,create_by,update_by,create_time,update_time) VALUES(#{name},#{definitionJson},0,#{createBy},#{updateBy},NOW(),NOW())")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(BiDashboardDO row);
    @Update("UPDATE bi_dashboard SET name=#{name},definition_json=#{definitionJson},version=version+1,update_by=#{updateBy},update_time=NOW() WHERE id=#{id} AND version=#{version} AND del_flag='0'") int update(BiDashboardDO row);
    @Update("UPDATE bi_dashboard SET published_json=#{snapshot},published_version=#{next},version=#{next},update_by=#{operator},update_time=NOW() WHERE id=#{id} AND version=#{version} AND del_flag='0'")
    int publish(@Param("id") Long id,@Param("version") int version,@Param("next") int next,@Param("snapshot") String snapshot,@Param("operator") String operator);
    @Update("UPDATE bi_dashboard SET published_json=NULL,published_version=NULL,version=version+1,update_by=#{operator},update_time=NOW() WHERE id=#{id} AND version=#{version} AND del_flag='0'")
    int unpublish(@Param("id") Long id,@Param("version") int version,@Param("operator") String operator);
    @Update("UPDATE bi_dashboard SET del_flag='2',update_by=#{operator},update_time=NOW() WHERE id=#{id} AND del_flag='0'") int delete(@Param("id") Long id,@Param("operator") String operator);
    @Delete("DELETE FROM bi_dashboard_binding WHERE dashboard_id=#{id} AND binding_kind=#{kind}") void clearBindings(@Param("id") Long id,@Param("kind") String kind);
    @Insert("INSERT INTO bi_dashboard_binding(dashboard_id,chart_id,dataset_id,binding_kind) VALUES(#{id},#{chart},#{dataset},#{kind}) ON DUPLICATE KEY UPDATE dataset_id=VALUES(dataset_id)")
    void bind(@Param("id") Long id,@Param("chart") Long chart,@Param("dataset") Long dataset,@Param("kind") String kind);
}
