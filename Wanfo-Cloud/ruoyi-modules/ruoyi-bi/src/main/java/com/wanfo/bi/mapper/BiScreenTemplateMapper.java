package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiScreenTemplateMapper {
 @Select("SELECT id,name,category,description,content_json AS contentJson,create_by AS createBy FROM bi_screen_template WHERE del_flag='0' ORDER BY id DESC")
 List<Map<String,Object>> list();
 @Select("SELECT id,name,category,description,content_json AS contentJson,create_by AS createBy FROM bi_screen_template WHERE id=#{id} AND del_flag='0'")
 Map<String,Object> get(Long id);
 @Insert("INSERT INTO bi_screen_template(name,category,description,content_json,create_by,update_by) VALUES(#{name},#{category},#{description},#{contentJson},#{actor},#{actor})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> row);
 @Update("UPDATE bi_screen_template SET name=#{name},category=#{category},description=#{description},content_json=#{contentJson},update_by=#{actor},update_time=NOW() WHERE id=#{id} AND del_flag='0'")
 int update(Map<String,Object> row);
 @Update("UPDATE bi_screen_template SET del_flag='2',content_json='{}' WHERE id=#{id} AND del_flag='0'") int delete(Long id);
}
