package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiAssetMapper {
 @Select("SELECT id,name,byte_size AS byteSize,create_by AS createBy FROM bi_image_asset WHERE del_flag='0' ORDER BY id DESC")
 List<Map<String,Object>> list();
 @Select("SELECT id,name,data_url AS image,byte_size AS byteSize FROM bi_image_asset WHERE id=#{id} AND del_flag='0'")
 Map<String,Object> get(Long id);
 @Insert("INSERT INTO bi_image_asset(name,data_url,byte_size,create_by) VALUES(#{name},#{image},#{byteSize},#{actor})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> row);
 @Update("UPDATE bi_image_asset SET del_flag='2',data_url='' WHERE id=#{id} AND del_flag='0'") int delete(Long id);
 @Update("UPDATE bi_image_asset SET name=#{name} WHERE id=#{id} AND BINARY name=BINARY #{expectedName} AND del_flag='0'") int rename(@Param("id")Long id,@Param("name")String name,@Param("expectedName")String expectedName);
}
