package com.wanfo.bi.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BiExportConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService biExportExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "bi-export");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(name = "biMetadataExecutor", destroyMethod = "shutdown")
    public ExecutorService biMetadataExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), runnable -> {
                    Thread thread = new Thread(runnable, "bi-metadata-scan");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "biWarehouseExecutor", destroyMethod = "shutdown")
    public ExecutorService biWarehouseExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "bi-warehouse-load");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "biPipelineExecutor", destroyMethod = "shutdown")
    public ExecutorService biPipelineExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "bi-pipeline-run");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "biPipelineTimer", destroyMethod = "shutdown")
    public ScheduledExecutorService biPipelineTimer() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bi-pipeline-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(name = "biQualityRetryTimer", destroyMethod = "shutdown")
    public ScheduledExecutorService biQualityRetryTimer() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bi-quality-retry");
            thread.setDaemon(true);
            return thread;
        });
    }
}
