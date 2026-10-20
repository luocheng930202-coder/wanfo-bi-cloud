package com.wanfo.bi.init;

import java.util.List;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.DbDataSourceProvider;
import com.wanfo.bi.service.BiDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据源首次启动引导。
 *
 * <p>bi_datasource 表为空时，把 ~R~bi.datasources~R~ 配置里的演示数据源导入表，
 * 并用 CredentialCipher 正确加密密码。这样开发环境不需要手工录数据源，
 * 生产环境则通过界面或接口维护。
 *
 * @author zhengliang
 */
@Component
@Order(100)
public class BiDataSourceInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(BiDataSourceInitializer.class);

    private final DbDataSourceProvider provider;
    private final CredentialCipher cipher;
    private final BiProperties properties;

    public BiDataSourceInitializer(DbDataSourceProvider provider,
                                   CredentialCipher cipher,
                                   BiProperties properties)
    {
        this.provider = provider;
        this.cipher = cipher;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        if (!provider.isEmpty())
        {
            log.info("bi_datasource 已有 {} 条记录，跳过引导导入", provider.listAll().size());
            return;
        }

        List<BiProperties.Ds> configured = properties.getDatasources();
        if (configured == null || configured.isEmpty())
        {
            log.info("bi.datasources 未配置，首次启动不导入任何数据源（可在界面上手工新增）");
            return;
        }

        int n = 0;
        for (BiProperties.Ds c : configured)
        {
            try
            {
                BiDatasourceDO row = new BiDatasourceDO();
                row.setName(c.getName());
                row.setType(c.getType());
                row.setSchemaName(c.getSchemaName());
                row.setJdbcUrl(c.getUrl());
                row.setDriverClass(BiDataSourceService.resolveDriver(c.getType(), c.getDriver()));
                row.setUsername(c.getUsername());
                row.setPassword(cipher.encrypt(c.getPassword()));
                row.setPoolMaxTotal(8);
                row.setPoolMinIdle(1);
                row.setQueryTimeout(60);
                row.setStatus("0");
                row.setRemark("引导导入自配置项 bi.datasources");
                row.setCreateBy("system");
                row.setUpdateBy("system");
                row.setDelFlag("0");
                provider.insert(row);
                n++;
            }
            catch (Exception e)
            {
                log.error("引导导入数据源失败 " + c.getName(), e);
            }
        }
        log.info("bi_datasource 引导导入完成，共 {} 条", n);
    }
}

