package com.wanfo.common.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects metric endpoints while leaving liveness and readiness checks public. */
public class ActuatorMetricsBasicAuthFilter extends OncePerRequestFilter
{
    private final String username;
    private final String password;

    public ActuatorMetricsBasicAuthFilter(String username, String password)
    {
        this.username = username;
        this.password = password;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getServletPath();
        return !("/actuator/prometheus".equals(path)
                || "/actuator/metrics".equals(path)
                || path.startsWith("/actuator/metrics/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (username == null || username.isBlank() || password == null || password.isBlank())
        {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Metrics credentials are not configured");
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ") || !isAuthorized(authorization.substring(6)))
        {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Wanfo metrics\", charset=\"UTF-8\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAuthorized(String encoded)
    {
        try
        {
            String supplied = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            byte[] expectedBytes = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
            byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expectedBytes, suppliedBytes);
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }
    }
}
