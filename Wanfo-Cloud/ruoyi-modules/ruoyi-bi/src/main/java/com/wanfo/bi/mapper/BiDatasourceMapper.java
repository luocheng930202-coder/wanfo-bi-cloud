package com.wanfo.bi.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDatasourceDO;

/**
 * 数据源 数据层。
 *
 * @author zhengliang
 */
public interface BiDatasourceMapper
{
    /** 查询全部未删除的数据源 */
    List<BiDatasourceDO> selectAll();

    /** 查询状态正常的数据源 */
    List<BiDatasourceDO> selectEnabled();

    /** 按主键查询 */
    BiDatasourceDO selectById(Long id);

    /** 按 Calcite schema 别名查询 */
    BiDatasourceDO selectBySchemaName(String schemaName);

    /** 统计 schema 别名占用 */
    int countBySchemaName(String schemaName);

    /** 新增 */
    int insert(BiDatasourceDO ds);

    /** 按主键全量更新（用于编辑） */
    int update(BiDatasourceDO ds);

    /** 仅更新状态（启用/停用） */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("updateBy") String updateBy);

    /** 逻辑删除 */
    int deleteById(@Param("id") Long id, @Param("updateBy") String updateBy);
}

