package com.wanfo.bi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class BiSecurityInterceptor implements HandlerInterceptor
{
    private final CurrentActorProvider actors;
    private final CapabilityProvider capabilities;

    public BiSecurityInterceptor(CurrentActorProvider actors, CapabilityProvider capabilities)
    {
        this.actors = actors;
        this.capabilities = capabilities;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (handler instanceof HandlerMethod method)
        {
            if (method.getMethodAnnotation(BiApiKeyAccess.class) != null) return true;
            // Publisher-scope links and the boundary files they draw may arrive without a session.
            // Viewer scope is rejected in the share service. Other endpoints still require a verified session.
            if (method.getMethodAnnotation(BiShareAccess.class) != null
                    || com.wanfo.common.core.utils.ShareAnonymousPaths.allows(request.getRequestURI()))
            {
                return true;
            }
            BiPermission permission = method.getMethodAnnotation(BiPermission.class);
            if (permission != null)
            {
                String[] anyOf = permission.anyOf();
                if (anyOf.length > 0 && !java.util.Arrays.stream(anyOf).anyMatch(capabilities::has))
                {
                    capabilities.require(anyOf[0]);
                }
                else if (!permission.value().isEmpty())
                {
                    capabilities.require(permission.value());
                }
            }
            else { capabilities.require("bi:resource:admin"); }
            actors.currentActor();
        }
        return true;
    }
}
