package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.TemplateMarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bi/template/market")
public class TemplateMarketController
{
    private final TemplateMarketService market;

    public TemplateMarketController(TemplateMarketService market)
    {
        this.market = market;
    }

    @GetMapping
    @BiPermission("bi:template:list")
    public List<Map<String, Object>> catalog()
    {
        return market.catalog();
    }

    @GetMapping("/{entryId}")
    @BiPermission("bi:template:list")
    public Map<String, Object> content(@PathVariable String entryId)
    {
        return market.content(entryId);
    }
}
