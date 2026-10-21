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
        return GeoCatalog.geometry(scope);
    }
    public GeoCatalog.Index regions(){return GeoCatalog.catalog();}
}
