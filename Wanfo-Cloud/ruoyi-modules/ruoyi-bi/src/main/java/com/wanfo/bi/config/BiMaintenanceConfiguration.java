package com.wanfo.bi.config;
import com.wanfo.bi.service.AssetStagingService;
import com.wanfo.bi.service.ChartTemplateLeaseService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
@Configuration
@EnableScheduling
public class BiMaintenanceConfiguration {
 private final AssetStagingService staging;
 private final ChartTemplateLeaseService leases;
 public BiMaintenanceConfiguration(AssetStagingService staging,ChartTemplateLeaseService leases){this.staging=staging;this.leases=leases;}
 @Scheduled(cron="0 15 * * * *")
 void hourlyPurge(){staging.purgeExpired();leases.purge();}
}
