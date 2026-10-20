package com.wanfo.bi.datasource;

import java.util.List;

/**
 * 数据源仓储。
 *
 * <p>当前为过渡实现：内存存储，由 {@link ConfigDataSourceProvider} 从 bi.datasources 配置装配。
 * S3-T02 完成后替换为 MyBatis 实现（bi_datasource 表已就绪），
 * 接口签名保持不变，业务层无需改动。
 *
 * @author zhengliang
 */
public interface DataSourceProvider
{
    /** 查询全部启用的数据源 */
    List<BiDatasource> listEnabled();

    /** 按 schema 别名查询 */
    BiDatasource getBySchemaName(String schemaName);

    /** 按主键查询 */
    BiDatasource getById(Long id);
}

