package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.CapabilityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TemplateMarketServiceTest
{
    BiProperties properties = new BiProperties();
    CapabilityProvider caps = mock(CapabilityProvider.class);
    TemplateMarketService service = new TemplateMarketService(properties, caps, new ObjectMapper());

    @BeforeEach
    void allowList()
    {
        doNothing().when(caps).require("bi:template:list");
    }

    @Test
    void builtinCatalogContainsValidatedKinds()
    {
        var catalog = service.catalog();
        assertTrue(catalog.size() >= 2);
        assertTrue(catalog.stream().anyMatch(e -> "dashboard".equals(e.get("kind"))));
        assertTrue(catalog.stream().anyMatch(e -> "screen".equals(e.get("kind"))));
        assertTrue(catalog.stream().allMatch(e -> "builtin".equals(e.get("provider"))));
    }

    @Test
    void contentReturnsLayoutWithoutBindings()
    {
        var body = service.content("market-dashboard-retail");
        assertEquals("market-dashboard-retail", body.get("id"));
        assertTrue(body.get("content") instanceof java.util.Map<?, ?> content);
        assertEquals("datacenter-dashboard-template", String.valueOf(((java.util.Map<?, ?>) body.get("content")).get("format")));
        assertFalse(String.valueOf(body.get("content")).contains("chartId"));
    }

    @Test
    void unknownEntryRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> service.content("missing-entry"));
    }
}
