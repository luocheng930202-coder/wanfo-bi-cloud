package com.wanfo.bi.adapter.ruoyi;

import static org.junit.jupiter.api.Assertions.*;

import com.wanfo.bi.controller.WorkbenchController;
import com.wanfo.common.core.web.domain.AjaxResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

class RuoYiBiWebAdviceTest
{
    private final RuoYiBiWebAdvice advice = new RuoYiBiWebAdvice();

    @Test
    void wrapsWorkbenchResponses() throws Exception
    {
        MethodParameter parameter = new MethodParameter(
                WorkbenchController.class.getDeclaredMethod("summary"), -1);
        assertTrue(advice.supports(parameter, null));
        Object wrapped = advice.beforeBodyWrite(java.util.Map.of("dashboard", 2), parameter, null, null, null, null);
        assertInstanceOf(AjaxResult.class, wrapped);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) ((AjaxResult) wrapped).get("data");
        assertEquals(2, data.get("dashboard"));
    }
}
