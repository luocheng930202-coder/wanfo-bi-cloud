package com.wanfo.bi.adapter.ruoyi;

import com.wanfo.bi.security.BiAccessException;
import com.wanfo.common.core.web.domain.AjaxResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** RuoYi's client expects {code,msg,data}; core controllers keep host-neutral return values. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "bi.security.mode", havingValue = "ruoyi", matchIfMissing = true)
public class RuoYiBiWebAdvice implements ResponseBodyAdvice<Object>
{
    @ExceptionHandler(BiAccessException.class)
    public AjaxResult denied(BiAccessException exception)
    {
        return AjaxResult.error(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalidInput(IllegalArgumentException exception)
    {
        return AjaxResult.error(400, exception.getMessage());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public AjaxResult malformedJson(org.springframework.http.converter.HttpMessageNotReadableException exception)
    {
        return AjaxResult.error(400, "请求 JSON 格式或字段类型不正确");
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public AjaxResult invalidParameter(Exception exception)
    {
        return AjaxResult.error(400, "请求参数缺失或类型不正确");
    }

    @Override
    public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter)
    {
        Class<?> body = parameter.getParameterType();
        if (byte[].class.equals(body) || org.springframework.http.ResponseEntity.class.equals(body)) return false;
        return "com.wanfo.bi.controller".equals(parameter.getContainingClass().getPackageName());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response)
    {
        return body instanceof AjaxResult ? body : AjaxResult.success(body);
    }
}
