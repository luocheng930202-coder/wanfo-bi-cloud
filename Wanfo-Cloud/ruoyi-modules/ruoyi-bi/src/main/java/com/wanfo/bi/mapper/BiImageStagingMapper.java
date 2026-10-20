package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiImageStagingMapper {
 @Select("SELECT COUNT(*) FROM bi_image_staging WHERE owner_principal=#{owner} AND expire_time>NOW()")
 int countActive(Long owner);
 @Select("SELECT id,name,data_url AS image,byte_size AS byteSize,expire_time AS expireTime FROM bi_image_staging WHERE id=#{id} AND owner_principal=#{owner} AND expire_time>NOW()")
 Map<String,Object> get(@Param("id") Long id,@Param("owner") Long owner);
 @Insert("INSERT INTO bi_image_staging(name,data_url,byte_size,owner_principal,create_by,expire_time) VALUES(#{name},#{image},#{byteSize},#{owner},#{actor},DATE_ADD(NOW(),INTERVAL 1 DAY))")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> row);
 @Delete("<script>DELETE FROM bi_image_staging WHERE owner_principal=#{owner} AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
 int release(@Param("owner") Long owner,@Param("ids") List<Long> ids);
 @Delete("DELETE FROM bi_image_staging WHERE expire_time<=NOW()")
 int purge();
}
