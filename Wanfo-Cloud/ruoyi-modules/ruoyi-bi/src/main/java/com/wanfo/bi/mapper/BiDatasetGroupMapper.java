package com.wanfo.bi.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDatasetGroupDO;

/**
 * 数据集目录 数据层。
 *
 * @author zhengliang
 */
public interface BiDatasetGroupMapper
{
    /** 全部未删除目录 */
    List<BiDatasetGroupDO> selectAll();

    BiDatasetGroupDO selectById(Long id);

    int countByName(@Param("parentId") Long parentId, @Param("name") String name);

    int insert(BiDatasetGroupDO row);

    int update(BiDatasetGroupDO row);

    int deleteById(@Param("id") Long id, @Param("updateBy") String updateBy);

    /** 判断是否存在子目录（删除前校验） */
    int countChildren(Long parentId);
}

