package com.wanfo.bi.config;

import com.wanfo.bi.security.BiSecurityInterceptor;
import com.wanfo.bi.security.CatalogAuditInterceptor;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.service.CatalogAuditService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BiSecurityConfiguration implements WebMvcConfigurer
{
    private final CurrentActorProvider actors;
    private final CapabilityProvider capabilities;
    private final CatalogAuditService audit;

    public BiSecurityConfiguration(CurrentActorProvider actors, CapabilityProvider capabilities, CatalogAuditService audit)
    {
        this.actors = actors;
        this.capabilities = capabilities;
        this.audit = audit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        // Reject expired sessions before RuoYi's HeaderInterceptor (-10) can refresh them.
        registry.addInterceptor(new CatalogAuditInterceptor(audit))
                .addPathPatterns("/bi/catalog/**", "/bi/metadata-scans/**").order(-30);
        registry.addInterceptor(new BiSecurityInterceptor(actors, capabilities))
                .addPathPatterns("/bi/**").order(-20);
    }
}
