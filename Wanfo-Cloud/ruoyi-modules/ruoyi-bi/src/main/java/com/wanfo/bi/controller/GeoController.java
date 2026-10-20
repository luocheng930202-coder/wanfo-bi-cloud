package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 行政区地图")
@RestController
@RequestMapping("/bi/geo")
public class GeoController
{
    private final GeoService geo;

    public GeoController(GeoService geo)
    {
        this.geo = geo;
    }

    @Operation(summary = "可用地图范围")
    @GetMapping("/scopes")
    @BiPermission("bi:chart:list")
    public List<Map<String, String>> scopes()
    {
        return geo.listScopes();
    }

    @Operation(summary = "GeoJSON 边界数据")
    @GetMapping("/{scope}")
    @BiPermission("bi:chart:list")
    public Object geo(@PathVariable String scope) throws Exception
    {
        return geo.load(scope);
    }
}
