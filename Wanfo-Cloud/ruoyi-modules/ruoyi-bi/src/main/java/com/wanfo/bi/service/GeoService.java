package com.wanfo.bi.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Serves bundled administrative region GeoJSON for map charts. */
@Service
public class GeoService
{
    private static final Set<String> SCOPES = Set.of("china-provinces");

    private final ObjectMapper objectMapper;

    public GeoService(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, String>> listScopes()
    {
        return List.of(Map.of("id", "china-provinces", "name", "中国省级行政区"));
    }

    public Object load(String scope) throws Exception
    {
        if (scope == null || !SCOPES.contains(scope))
        {
            throw new IllegalArgumentException("不支持的地图范围: " + scope);
        }
        String path = "/bi/geo/" + scope + ".json";
        try (InputStream in = GeoService.class.getResourceAsStream(path))
        {
            if (in == null)
            {
                throw new IllegalArgumentException("地图数据不存在");
            }
            return objectMapper.readValue(in, Object.class);
        }
    }
}
