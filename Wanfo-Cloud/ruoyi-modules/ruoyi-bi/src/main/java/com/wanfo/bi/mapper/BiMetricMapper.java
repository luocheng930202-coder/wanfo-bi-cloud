package com.wanfo.bi.mapper;

import java.util.List;
import com.wanfo.bi.domain.BiMetricDO;
import com.wanfo.bi.domain.BiMetricHistoryDO;
import com.wanfo.bi.domain.BiMetricUsageDO;
import org.apache.ibatis.annotations.*;

public interface BiMetricMapper {
    String COLS = "id,code,name,kind,dataset_id AS datasetId,definition_json AS definitionJson,published_json AS publishedJson,"
            + "version,published_version AS publishedVersion,publish_status AS publishStatus,create_by AS createBy,update_by AS updateBy,"
            + "(SELECT COUNT(*) FROM bi_metric_usage u WHERE u.metric_id=bi_metric.id) AS usageCount";

    @Select("SELECT " + COLS + " FROM bi_metric WHERE del_flag='0' ORDER BY id DESC")
    List<BiMetricDO> list();

    @Select("SELECT " + COLS + " FROM bi_metric WHERE id=#{id} AND del_flag='0'")
    BiMetricDO get(Long id);

    @Select("SELECT " + COLS + " FROM bi_metric WHERE code=#{code} AND del_flag='0'")
    BiMetricDO findByCode(String code);

    @Insert("INSERT INTO bi_metric(code,name,kind,dataset_id,definition_json,version,publish_status,create_by,update_by,create_time,update_time) "
            + "VALUES(#{code},#{name},#{kind},#{datasetId},#{definitionJson},0,'draft',#{createBy},#{updateBy},NOW(),NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BiMetricDO row);

    @Update("UPDATE bi_metric SET name=#{name},kind=#{kind},dataset_id=#{datasetId},definition_json=#{definitionJson},"
            + "version=version+1,update_by=#{updateBy},update_time=NOW() "
            + "WHERE id=#{id} AND version=#{version} AND del_flag='0' AND publish_status='draft'")
    int update(BiMetricDO row);

    @Update("UPDATE bi_metric SET published_json=definition_json,published_version=version+1,version=version+1,"
            + "publish_status='published',update_by=#{operator},update_time=NOW() "
            + "WHERE id=#{id} AND version=#{version} AND del_flag='0' AND publish_status='draft'")
    int publish(@Param("id") Long id, @Param("version") int version, @Param("operator") String operator);

    @Update("UPDATE bi_metric SET published_json=NULL,publish_status='draft',version=version+1,update_by=#{operator},update_time=NOW() "
            + "WHERE id=#{id} AND version=#{version} AND del_flag='0' AND publish_status='published' "
            + "AND NOT EXISTS(SELECT 1 FROM bi_metric_usage u WHERE u.metric_id=bi_metric.id)")
    int unpublish(@Param("id") Long id, @Param("version") int version, @Param("operator") String operator);

    @Update("UPDATE bi_metric SET del_flag='2',code=CONCAT('~',id,'~',LEFT(code,40)),update_by=#{operator},update_time=NOW() "
            + "WHERE id=#{id} AND del_flag='0' AND publish_status='draft' "
            + "AND NOT EXISTS(SELECT 1 FROM bi_metric_usage u WHERE u.metric_id=bi_metric.id)")
    int delete(@Param("id") Long id, @Param("operator") String operator);

    @Select("SELECT COUNT(*) FROM bi_metric_usage WHERE metric_id=#{metricId}")
    int usageCount(Long metricId);

    @Select("SELECT id,metric_id AS metricId,ref_type AS refType,ref_id AS refId,remark,create_by AS createBy,create_time AS createTime "
            + "FROM bi_metric_usage WHERE metric_id=#{metricId} ORDER BY id DESC")
    List<BiMetricUsageDO> usages(Long metricId);

    @Insert("INSERT INTO bi_metric_usage(metric_id,ref_type,ref_id,remark,create_by,create_time) "
            + "VALUES(#{metricId},#{refType},#{refId},#{remark},#{createBy},NOW()) "
            + "ON DUPLICATE KEY UPDATE remark=VALUES(remark)")
    int bind(BiMetricUsageDO row);

    @Delete("DELETE FROM bi_metric_usage WHERE metric_id=#{metricId} AND ref_type=#{refType} AND ref_id=#{refId}")
    int unbind(@Param("metricId") Long metricId, @Param("refType") String refType, @Param("refId") String refId);

    @Insert("INSERT INTO bi_metric_history(metric_id,published_version,code,name,kind,dataset_id,definition_json,publish_by,publish_time) "
            + "VALUES(#{metricId},#{publishedVersion},#{code},#{name},#{kind},#{datasetId},#{definitionJson},#{publishBy},NOW())")
    int insertHistory(BiMetricHistoryDO row);

    @Select("SELECT id,metric_id AS metricId,published_version AS publishedVersion,code,name,kind,dataset_id AS datasetId,"
            + "definition_json AS definitionJson,publish_by AS publishBy,publish_time AS publishTime "
            + "FROM bi_metric_history WHERE metric_id=#{metricId} ORDER BY published_version DESC")
    List<BiMetricHistoryDO> history(Long metricId);
}
