package com.wanfo.bi.adapter.ruoyi;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Query-only resolver; other BI operations retain their original exception handlers. */
@Component
@ConditionalOnProperty(name="bi.security.mode",havingValue="ruoyi",matchIfMissing=true)
public class BiQueryExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final ObjectMapper mapper;
    public BiQueryExceptionResolver(){this.mapper=new ObjectMapper();}
    @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
    @Override public ModelAndView resolveException(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception){
        if(!BiQueryFailure.queryPath(request.getRequestURI()) || response.isCommitted())return null;
        try{response.setStatus(200);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");mapper.writeValue(response.getWriter(),BiQueryFailure.response(exception));return new ModelAndView();}
        catch(java.io.IOException ignored){return null;}
    }
}
