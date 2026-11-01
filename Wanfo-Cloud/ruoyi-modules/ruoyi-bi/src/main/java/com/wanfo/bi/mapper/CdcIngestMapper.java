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
public interface CdcIngestMapper {
    @Select("SELECT id,job_code AS jobCode,name,source_datasource_id AS sourceDatasourceId,staging_table AS stagingTable,target_datasource_id AS targetDatasourceId,target_layer AS targetLayer,target_table AS targetTable,watermark_column AS watermarkColumn,status,interval_minutes AS intervalMinutes,last_watermark AS lastWatermark,last_run_at AS lastRunAt,last_error AS lastError,create_by AS createBy,create_time AS createTime FROM dp_cdc_ingest_job ORDER BY id DESC LIMIT 200")
    List<Map<String, Object>> list();

    @Select("SELECT id,job_code AS jobCode,name,source_datasource_id AS sourceDatasourceId,staging_table AS stagingTable,target_datasource_id AS targetDatasourceId,target_layer AS targetLayer,target_table AS targetTable,watermark_column AS watermarkColumn,status,interval_minutes AS intervalMinutes,last_watermark AS lastWatermark,last_run_at AS lastRunAt,last_error AS lastError FROM dp_cdc_ingest_job WHERE id=#{id}")
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id,job_code AS jobCode,source_datasource_id AS sourceDatasourceId,staging_table AS stagingTable,target_datasource_id AS targetDatasourceId,target_layer AS targetLayer,target_table AS targetTable,watermark_column AS watermarkColumn,interval_minutes AS intervalMinutes,last_watermark AS lastWatermark,last_run_at AS lastRunAt FROM dp_cdc_ingest_job WHERE status='ACTIVE' AND (last_run_at IS NULL OR last_run_at < DATE_SUB(NOW(3), INTERVAL interval_minutes MINUTE)) ORDER BY id LIMIT 20")
    List<Map<String, Object>> due();

    @Insert("""
            INSERT INTO dp_cdc_ingest_job(job_code,name,source_datasource_id,staging_table,target_datasource_id,target_layer,target_table,watermark_column,status,interval_minutes,create_by)
            VALUES(#{jobCode},#{name},#{sourceDatasourceId},#{stagingTable},#{targetDatasourceId},#{targetLayer},#{targetTable},#{watermarkColumn},'ACTIVE',#{intervalMinutes},#{createBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("UPDATE dp_cdc_ingest_job SET last_watermark=#{watermark},last_run_at=NOW(3),last_error=NULL,update_by=#{actor} WHERE id=#{id}")
    int markSuccess(@Param("id") Long id, @Param("watermark") String watermark, @Param("actor") String actor);

    @Update("UPDATE dp_cdc_ingest_job SET last_run_at=NOW(3),last_error=#{error},update_by=#{actor} WHERE id=#{id}")
    int markFailure(@Param("id") Long id, @Param("error") String error, @Param("actor") String actor);

    @Update("UPDATE dp_cdc_ingest_job SET status=#{status},update_by=#{actor} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("actor") String actor);
}
