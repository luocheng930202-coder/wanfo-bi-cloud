package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDatasetTableDO;

/**
 * 数据集 数据层。
 *
 * @author zhengliang
 */
public interface BiDatasetTableMapper
{
    /** 全部未删除数据集（可选按目录过滤） */
    List<BiDatasetTableDO> selectAll(@Param("groupId") Long groupId);

    BiDatasetTableDO selectById(Long id);

    /** 数据集名称在同目录内是否重复 */
    int countByName(@Param("groupId") Long groupId, @Param("name") String name);

    int insert(BiDatasetTableDO row);

    int update(BiDatasetTableDO row);

    int updateUnionAndModel(@Param("id") Long id, @Param("unionSql") String unionSql,
                            @Param("modelJson") String modelJson, @Param("updateBy") String updateBy);

    int deleteById(@Param("id") Long id, @Param("updateBy") String updateBy);

    /** 统计目录下的数据集数（删除目录前校验） */
    int countByGroupId(Long groupId);

    /** 数据集被图表引用的次数（删除前校验，S6 完成后启用） */
    List<Map<String, Object>> countChartRefs(Long datasetId);
}

