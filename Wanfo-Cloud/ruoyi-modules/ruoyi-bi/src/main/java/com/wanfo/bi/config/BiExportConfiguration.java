package com.wanfo.bi.config;

import java.util.concurrent.ExecutorService;
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
}
