package com.wanfo.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/** Protects detailed metric endpoints on the reactive gateway. */
@Configuration(proxyBeanMethods = false)
public class ActuatorMetricsBasicAuthWebFilter
{
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    public WebFilter actuatorMetricsAuthFilter(
            @Value("${monitor.actuator.username:}") String username,
            @Value("${monitor.actuator.password:}") String password)
    {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (!isMetricsPath(path))
            {
                return chain.filter(exchange);
            }

            if (username.isBlank() || password.isBlank())
            {
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return exchange.getResponse().setComplete();
            }

            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Basic ") || !isAuthorized(authorization.substring(6), username, password))
            {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Wanfo metrics\", charset=\"UTF-8\"");
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    private static boolean isMetricsPath(String path)
    {
        return "/actuator/prometheus".equals(path)
                || "/actuator/metrics".equals(path)
                || path.startsWith("/actuator/metrics/");
    }

    private static boolean isAuthorized(String encoded, String username, String password)
    {
        try
        {
            String supplied = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return MessageDigest.isEqual((username + ":" + password).getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }
    }
}
