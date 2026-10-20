package com.wanfo.bi.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDatasetFieldDO;

/**
 * 数据集字段 数据层。
 *
 * @author zhengliang
 */
public interface BiDatasetFieldMapper
{
    @org.apache.ibatis.annotations.Update("UPDATE bi_dataset_field SET origin_type=#{type},update_time=NOW() WHERE id=#{id} AND ext_flag='1' AND origin_type='CALC' AND ext_formula=#{formula} AND del_flag='0'")
    int repairCalculatedType(@Param("id") Long id,@Param("formula") String formula,@Param("type") String type);
    List<BiDatasetFieldDO> selectByDatasetId(Long datasetId);

    BiDatasetFieldDO selectById(Long id);

    int insert(BiDatasetFieldDO row);

    /** 批量插入（创建数据集时一次性写入全部字段） */
    int insertBatch(@Param("list") List<BiDatasetFieldDO> list);

    int update(BiDatasetFieldDO row);

    int deleteByDatasetId(@Param("datasetId") Long datasetId);

    int deleteById(@Param("id") Long id);
}

