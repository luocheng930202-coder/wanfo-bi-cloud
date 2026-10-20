package com.wanfo.system.api.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import com.wanfo.common.core.utils.poi.ExcelImageHandler;
import com.wanfo.system.api.RemoteFileService;
import com.wanfo.system.api.handler.RemoteExcelImageHandler;

/**
 * Excel图片导入处理器
 *
 * @author ruoyi
 */
@AutoConfiguration
@ConditionalOnBean(RemoteFileService.class)
public class RemoteFileAutoConfiguration
{
    @Bean
    public ExcelImageHandler excelImageHandler(RemoteFileService remoteFileService)
    {
        return new RemoteExcelImageHandler(remoteFileService);
    }
}
