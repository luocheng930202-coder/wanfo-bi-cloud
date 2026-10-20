package com.wanfo.bi.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接池生命周期钩子。
 *
 * <p>数据源配置变更（地址/账号/密码/驱动）后必须销毁旧池，
 * 否则新配置不会生效 —— 这是连接池类组件最常见的坑。
 *
 * @author zhengliang
 */
@Component
public class CalcitePoolManager
{
    private static final Logger log = LoggerFactory.getLogger(CalcitePoolManager.class);

    private final CalciteQueryEngine engine;

    public CalcitePoolManager(CalciteQueryEngine engine)
    {
        this.engine = engine;
    }

    /**
     * 销毁指定 schema 的连接池，下次 open() 时按新配置重建。
     */
    public void invalidate(String schemaName)
    {
        log.info("连接池失效标记: {}", schemaName);
        engine.invalidate(schemaName);
    }

    /** 应用停止时关闭全部连接池 */
    @PreDestroy
    public void shutdownAll()
    {
        engine.shutdown();
    }
}

