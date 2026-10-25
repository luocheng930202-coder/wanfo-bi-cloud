package com.wanfo.common.security.config;

import com.wanfo.common.security.filter.ActuatorMetricsBasicAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/** Registers Basic authentication for Prometheus and detailed metrics endpoints. */
@Configuration(proxyBeanMethods = false)
public class ActuatorMetricsSecurityConfiguration
{
    @Bean
    public FilterRegistrationBean<ActuatorMetricsBasicAuthFilter> actuatorMetricsBasicAuthFilter(Environment environment)
    {
        ActuatorMetricsBasicAuthFilter filter = new ActuatorMetricsBasicAuthFilter(
                environment.getProperty("monitor.actuator.username"),
                environment.getProperty("monitor.actuator.password"));
        FilterRegistrationBean<ActuatorMetricsBasicAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/actuator/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("actuatorMetricsBasicAuthFilter");
        return registration;
    }
}
