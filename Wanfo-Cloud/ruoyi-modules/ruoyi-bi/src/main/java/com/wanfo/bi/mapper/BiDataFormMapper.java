package com.wanfo.bi.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDataFormDO;

public interface BiDataFormMapper
{
    List<BiDataFormDO> selectAll();

    BiDataFormDO selectById(Long id);

    int insert(BiDataFormDO row);

    int update(BiDataFormDO row);

    int deleteById(@Param("id") Long id, @Param("updateBy") String updateBy);
}
