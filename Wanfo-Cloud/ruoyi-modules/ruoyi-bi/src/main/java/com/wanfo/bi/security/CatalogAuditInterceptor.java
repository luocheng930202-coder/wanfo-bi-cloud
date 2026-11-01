package com.wanfo.bi.security;

import com.wanfo.bi.service.CatalogAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/** Audits failed catalog/metadata requests without retaining query strings or request bodies. */
public class CatalogAuditInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(CatalogAuditInterceptor.class);
    private final CatalogAuditService audit;

    public CatalogAuditInterceptor(CatalogAuditService audit) { this.audit = audit; }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception)
    {
        if (exception == null || !request.getRequestURI().startsWith("/bi/")) return;
        try { audit.recordFailure(request.getMethod(), request.getRequestURI(), exception); }
        catch (RuntimeException auditFailure)
        {
            log.error("Catalog request failure audit could not be persisted; trace_id={} exceptionType={}",
                    org.slf4j.MDC.get("trace_id"), exception.getClass().getName());
        }
    }
}
