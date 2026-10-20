package com.wanfo.bi.service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.CapabilityProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TemplateMarketService
{
    private static final String BUILTIN_INDEX = "bi/template-market/index.json";
    private static final int MAX_CATALOG_BYTES = 512 * 1024;
    private static final int MAX_TEMPLATE_BYTES = 2 * 1024 * 1024;

    private final BiProperties properties;
    private final CapabilityProvider caps;
    private final ObjectMapper json;

    public TemplateMarketService(BiProperties properties, CapabilityProvider caps, ObjectMapper json)
    {
        this.properties = properties;
        this.caps = caps;
        this.json = json;
    }

    public List<Map<String, Object>> catalog()
    {
        caps.require("bi:template:list");
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> entry : loadIndex(null))
            merged.put(String.valueOf(entry.get("id")), entry);
        String remote = properties.getTemplateMarket().getCatalogUrl();
        if (remote != null && !remote.isBlank())
        {
            try {
                for (Map<String, Object> entry : loadIndex(remote))
                    merged.put(String.valueOf(entry.get("id")), entry);
            } catch(RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Remote template catalog unavailable; serving built-in templates");
            }
        }
        return new ArrayList<>(merged.values());
    }

    public Map<String, Object> content(String entryId)
    {
        caps.require("bi:template:list");
        if (entryId == null || entryId.isBlank() || entryId.length() > 120)
            throw new IllegalArgumentException("模板标识无效");
        Map<String, Object> entry = findEntry(entryId);
        if (entry == null)
            throw new IllegalArgumentException("市场模板不存在");
        String resource = String.valueOf(entry.get("resource"));
        Map<String, Object> layout = loadLayout(resource, properties.getTemplateMarket().getCatalogUrl());
        layout = ScreenTemplateService.validate(layout);
        Map<String, Object> result = new LinkedHashMap<>(entry);
        result.remove("resource");
        result.put("content", layout);
        result.put("kind", kind(layout));
        return result;
    }

    private Map<String, Object> findEntry(String entryId)
    {
        for (Map<String, Object> entry : catalog())
            if (entryId.equals(String.valueOf(entry.get("id"))))
                return entry;
        return null;
    }

    private List<Map<String, Object>> loadIndex(String catalogUrl)
    {
        JsonNode root = catalogUrl == null ? readClasspathJson(BUILTIN_INDEX) : readRemoteJson(catalogUrl, MAX_CATALOG_BYTES);
        if (!root.has("templates") || !root.get("templates").isArray())
            throw new IllegalArgumentException("模板市场目录格式无效");
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : root.get("templates"))
        {
            if (!node.isObject())
                continue;
            String id = text(node, "id", 120);
            String name = text(node, "name", 200);
            String category = text(node, "category", 64);
            String description = optionalText(node, "description", 500);
            String kind = optionalKind(node);
            String provider = catalogUrl == null ? "builtin" : "remote";
            String resource = text(node, "resource", 500);
            if (!resource.startsWith("classpath:") && !resource.startsWith("url:"))
                throw new IllegalArgumentException("市场模板资源路径不受支持");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", name);
            item.put("category", category);
            item.put("description", description);
            item.put("kind", kind);
            item.put("provider", provider);
            item.put("resource", resource);
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> loadLayout(String resource, String catalogBase)
    {
        if (resource.startsWith("classpath:"))
        {
            String path = resource.substring("classpath:".length());
            if (path.contains("..") || !path.startsWith("bi/template-market/"))
                throw new IllegalArgumentException("资源路径无效");
            JsonNode node = readClasspathJson(path);
            return json.convertValue(node, Map.class);
        }
        String url = resource.substring("url:".length());
        if (!url.startsWith("http://") && !url.startsWith("https://"))
        {
            if (catalogBase == null || catalogBase.isBlank())
                throw new IllegalArgumentException("相对市场资源需要配置目录 URL");
            url = resolveAgainst(catalogBase, url);
        }
        JsonNode node = readRemoteJson(url, MAX_TEMPLATE_BYTES);
        return json.convertValue(node, Map.class);
    }

    private static String resolveAgainst(String base, String relative)
    {
        URI baseUri = URI.create(base);
        return baseUri.resolve(relative).toString();
    }

    private JsonNode readClasspathJson(String path)
    {
        try (InputStream in = new ClassPathResource(path).getInputStream())
        {
            byte[] bytes = readBounded(in, MAX_TEMPLATE_BYTES);
            return json.readTree(bytes);
        }
        catch (java.io.IOException e)
        {
            throw new IllegalArgumentException("无法读取内置模板市场资源");
        }
    }

    private JsonNode readRemoteJson(String url, int maxBytes)
    {
        requireAllowedOrigin(url);
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeout = properties.getTemplateMarket().getFetchTimeoutSeconds() * 1000;
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code != 200)
                throw new BiAccessException(502, "模板市场拉取失败");
            if(conn.getContentLengthLong()>maxBytes)throw new BiAccessException(502,"市场响应过大");
            try (InputStream in = conn.getInputStream())
            {
                byte[] bytes = readBounded(in,maxBytes);
                return json.readTree(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        catch (BiAccessException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw new BiAccessException(502, "模板市场不可用");
        }
        catch (Exception e)
        {
            throw new BiAccessException(502, "模板市场不可用");
        }
        finally { if(conn!=null)conn.disconnect(); }
    }

    static byte[] readBounded(InputStream input,int limit) throws java.io.IOException {
        byte[] bytes=input.readNBytes(limit+1);
        if(bytes.length>limit)throw new BiAccessException(502,"市场响应过大");
        return bytes;
    }

    private void requireAllowedOrigin(String url) {
        String actual=origin(url),catalog=origin(properties.getTemplateMarket().getCatalogUrl());
        if(!actual.equals(catalog)&&properties.getTemplateMarket().getAllowedOrigins().stream().noneMatch(v->origin(v).equals(actual)))
            throw new BiAccessException(502,"模板资源来源未获配置授权");
    }
    private static String origin(String value) {
        try {
            URI uri=URI.create(value);
            if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null)throw new IllegalArgumentException();
            int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
            return uri.getScheme()+"://"+uri.getHost().toLowerCase(Locale.ROOT)+":"+port;
        }catch(RuntimeException ex){throw new BiAccessException(502,"模板市场地址无效");}
    }

    private static String text(JsonNode node, String field, int max)
    {
        JsonNode v = node.get(field);
        if (v == null || !v.isString() || v.asString().isBlank() || v.asString().length() > max)
            throw new IllegalArgumentException("市场目录字段无效");
        return v.asString().trim();
    }

    private static String optionalText(JsonNode node, String field, int max)
    {
        JsonNode v = node.get(field);
        if (v == null || v.isNull())
            return "";
        if (!v.isString() || v.asString().length() > max)
            throw new IllegalArgumentException("市场目录字段无效");
        return v.asString().trim();
    }

    private static String optionalKind(JsonNode node)
    {
        JsonNode v = node.get("kind");
        if (v == null || v.isNull())
            return "screen";
        String kind = v.asString();
        if (!Set.of("screen", "dashboard").contains(kind))
            throw new IllegalArgumentException("市场模板类型无效");
        return kind;
    }

    private static String kind(Map<String, Object> layout)
    {
        return "datacenter-dashboard-template".equals(String.valueOf(layout.get("format"))) ? "dashboard" : "screen";
    }
}
