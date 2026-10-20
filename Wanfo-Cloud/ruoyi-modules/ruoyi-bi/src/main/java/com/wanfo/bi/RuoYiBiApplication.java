package com.wanfo.bi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import com.wanfo.common.security.annotation.EnableCustomConfig;
import com.wanfo.common.security.annotation.EnableRyFeignClients;

/**
 * BI 报表平台（自研）模块
 *
 * <p>包结构对应后续的模块拆分：
 * <ul>
 *   <li>{@code com.wanfo.bi.datasource} — 数据源定义与连接池管理</li>
 *   <li>{@code com.wanfo.bi.query}     — 查询引擎（Calcite 联邦查询 / SQL 编译）</li>
 *   <li>{@code com.wanfo.bi.controller} — REST 接口</li>
 * </ul>
 *
 * @author zhengliang
 */
@EnableCustomConfig
@EnableRyFeignClients
@MapperScan("com.wanfo.bi.mapper")
@SpringBootApplication
public class RuoYiBiApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiBiApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  BI 报表平台模块启动成功 (RuoYi-Cloud-BI)");
        System.out.println("==================================================");
    }
}

