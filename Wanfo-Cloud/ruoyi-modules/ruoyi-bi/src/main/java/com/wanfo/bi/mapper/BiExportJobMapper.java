package com.wanfo.bi.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BiExportJobMapper {
 @Select("SELECT id,name,source_type AS sourceType,source_id AS sourceId,panel_key AS panelKey,format,status,message,row_count AS rowCount,file_name AS fileName,expire_time AS expireTime,create_time AS createTime FROM bi_export_job WHERE owner_principal=#{owner} AND expire_time>NOW() ORDER BY id DESC LIMIT 50")
 List<Map<String,Object>> list(@Param("owner") Long owner);
 @Select("SELECT id,name,source_type AS sourceType,source_id AS sourceId,panel_key AS panelKey,format,status,request_json AS requestJson,message,row_count AS rowCount,file_name AS fileName,content_type AS contentType,owner_principal AS ownerPrincipal,expire_time AS expireTime,create_by AS createBy FROM bi_export_job WHERE id=#{id}")
 Map<String,Object> get(@Param("id") Long id);
 @Select("SELECT id,name,request_json AS requestJson,status,file_name AS fileName,content_type AS contentType,content,owner_principal AS ownerPrincipal,expire_time AS expireTime FROM bi_export_job WHERE id=#{id}")
 Map<String,Object> getFile(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM bi_export_job WHERE owner_principal=#{owner} AND status IN ('queued','running')")
 int active(@Param("owner") Long owner);
 @Insert("INSERT INTO bi_export_job(name,source_type,source_id,panel_key,format,status,request_json,owner_principal,create_by,expire_time) VALUES(#{name},#{sourceType},#{sourceId},#{panelKey},#{format},'queued',#{requestJson},#{owner},#{actor},DATE_ADD(NOW(),INTERVAL 7 DAY))")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> row);
 @Update("UPDATE bi_export_job SET status='running' WHERE id=#{id} AND status='queued'") int claim(@Param("id") Long id);
 @Update("UPDATE bi_export_job SET status=#{status},message=#{message},row_count=#{rowCount},file_name=#{fileName},content_type=#{contentType},content=#{content,jdbcType=BLOB},request_json=COALESCE(#{validationJson,jdbcType=LONGVARCHAR},request_json),finish_time=NOW() WHERE id=#{id} AND status='running'")
 int finish(Map<String,Object> row);
 @Update("UPDATE bi_export_job SET status='cancelled',message='已取消',finish_time=NOW() WHERE id=#{id} AND owner_principal=#{owner} AND status IN ('queued','running')")
 int cancel(@Param("id") Long id, @Param("owner") Long owner);
 @Update("UPDATE bi_export_job SET status='failed',message='服务重启，请重新提交',finish_time=NOW() WHERE status IN ('queued','running')") int recover();
 @Update("UPDATE bi_export_job SET content=NULL WHERE expire_time<=NOW() AND content IS NOT NULL") int purge();
}
