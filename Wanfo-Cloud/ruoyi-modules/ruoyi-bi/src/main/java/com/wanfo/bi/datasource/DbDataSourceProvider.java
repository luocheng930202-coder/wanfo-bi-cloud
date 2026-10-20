package com.wanfo.bi.datasource;

import java.util.ArrayList;
import java.util.List;

import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.mapper.BiDatasourceMapper;
import org.springframework.stereotype.Component;

/**
 * 基于 MyBatis 的 DataSourceProvider（正式实现）。
 *
 * <p>数据源以 ~R~bi_datasource~R~ 表为准。密码从库中取出后解密，
 * 返回的副本仅供连接池使用，不应外泄到接口响应。
 *
 * @author zhengliang
 */
@Component
public class DbDataSourceProvider implements DataSourceProvider
{
    private final BiDatasourceMapper mapper;
    private final CredentialCipher cipher;

    public DbDataSourceProvider(BiDatasourceMapper mapper, CredentialCipher cipher)
    {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    @Override
    public List<BiDatasource> listEnabled()
    {
        return decryptAll(mapper.selectEnabled());
    }

    @Override
    public BiDatasource getBySchemaName(String schemaName)
    {
        BiDatasourceDO row = mapper.selectBySchemaName(schemaName);
        return row == null ? null : toDomain(row);
    }

    @Override
    public BiDatasource getById(Long id)
    {
        BiDatasourceDO row = mapper.selectById(id);
        return row == null ? null : toDomain(row);
    }

    /** 全部未删除的数据源（管理页面用，不做 status 过滤） */
    public List<BiDatasourceDO> listAll()
    {
        return mapper.selectAll();
    }

    public BiDatasourceDO getRow(Long id)
    {
        return mapper.selectById(id);
    }

    public int insert(BiDatasourceDO row)
    {
        return mapper.insert(row);
    }

    public int update(BiDatasourceDO row)
    {
        return mapper.update(row);
    }

    public int updateStatus(Long id, String status, String operator)
    {
        return mapper.updateStatus(id, status, operator);
    }

    public int delete(Long id, String operator)
    {
        return mapper.deleteById(id, operator);
    }

    public int countBySchemaName(String schemaName)
    {
        return mapper.countBySchemaName(schemaName);
    }

    public boolean isEmpty()
    {
        return mapper.selectAll().isEmpty();
    }

    private List<BiDatasource> decryptAll(List<BiDatasourceDO> rows)
    {
        List<BiDatasource> list = new ArrayList<>(rows.size());
        for (BiDatasourceDO row : rows)
        {
            list.add(toDomain(row));
        }
        return list;
    }

    private BiDatasource toDomain(BiDatasourceDO row)
    {
        BiDatasource ds = new BiDatasource();
        ds.setId(row.getId());
        ds.setName(row.getName());
        ds.setType(row.getType());
        ds.setSchemaName(row.getSchemaName());
        ds.setJdbcUrl(row.getJdbcUrl());
        ds.setDriverClass(row.getDriverClass());
        ds.setUsername(row.getUsername());
        // 解密失败时保留原值，由连接池报错暴露问题
        try
        {
            ds.setPassword(cipher.decrypt(row.getPassword()));
        }
        catch (Exception e)
        {
            ds.setPassword(row.getPassword());
        }
        ds.setSshHost(row.getSshHost());
        ds.setSshPort(row.getSshPort());
        ds.setSshUser(row.getSshUser());
        ds.setStatus(row.getStatus());
        ds.setRemark(row.getRemark());
        return ds;
    }
}

