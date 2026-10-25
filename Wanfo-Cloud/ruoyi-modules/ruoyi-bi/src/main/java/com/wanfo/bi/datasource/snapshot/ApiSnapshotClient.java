package com.wanfo.bi.datasource.snapshot;

import java.net.*;
import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicit origin allowlist; never follows redirects or forwards credentials to another origin. */
@Component
public class ApiSnapshotClient {
    private final Set<String> origins;
    public record Pagination(String pageParam,String sizeParam,int pageSize,int startPage,int maxPages,String totalPath){}
    public record OAuth(String tokenUrl,String clientId,String clientSecret,String scope,String tokenField){}
    public record Config(String url,String dataPath,Map<String,String> headers,Pagination pagination,String method,Map<String,Object> body,boolean flatten,OAuth oauth){
        public Config(String url,String path,Map<String,String> headers){this(url,path,headers,null,"GET",null,false,null);}
        public Config(String url,String path,Map<String,String> headers,Pagination pagination){this(url,path,headers,pagination,"GET",null,false,null);}
    }
    public ApiSnapshotClient(@Value("${bi.external-source.allowed-origins:}") String origins){
        this.origins=new HashSet<>();for(String origin:origins.split(","))if(!origin.isBlank())this.origins.add(origin(URI.create(origin.trim())));
    }
    private static String origin(URI uri){
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || !Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException("API 地址须为 HTTP(S) 且不能包含账号或片段");
        return uri.getScheme()+"://"+uri.getHost().toLowerCase(Locale.ROOT)+":"+(uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort());
    }
    public byte[] fetch(Config config)throws IOException {return fetch(config,System.nanoTime()+20_000_000_000L);}
    public byte[] fetch(Config config,long deadline)throws IOException {
        if(System.nanoTime()>=deadline)throw new IllegalArgumentException("API 请求超过时间限制");
        URI uri=URI.create(config.url());
        if(!origins.contains(origin(uri)))throw new IllegalArgumentException("API 域名未获允许，请配置 bi.external-source.allowed-origins");
        Map<String,String> requestHeaders=new LinkedHashMap<>(config.headers()==null?Map.of():config.headers());
        if(config.oauth()!=null)requestHeaders.put("Authorization","Bearer "+oauthToken(config.oauth(),deadline));
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        int wait=(int)Math.max(1,Math.min(3000,(deadline-System.nanoTime())/1_000_000));
        String method=config.method()==null?"GET":config.method().toUpperCase(Locale.ROOT);
        if(!Set.of("GET","POST").contains(method))throw new IllegalArgumentException("API 仅支持 GET 或 POST");
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(wait);connection.setReadTimeout(wait);connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        {
            if(requestHeaders.size()>20)throw new IllegalArgumentException("请求头不能超过 20 项");
            for(var header:requestHeaders.entrySet()){
                String key=header.getKey(),value=header.getValue();
                if(!key.matches("[A-Za-z0-9-]{1,80}") || Set.of("host","connection","content-length","transfer-encoding","proxy-authorization").contains(key.toLowerCase(Locale.ROOT))
                        || value==null || value.length()>4000 || value.indexOf('\r')>=0 || value.indexOf('\n')>=0)throw new IllegalArgumentException("API 请求头无效");
                connection.setRequestProperty(key,value);
            }
        }
        if("POST".equals(method)){
            byte[] body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(config.body()==null?Map.of():config.body());
            if(body.length>64*1024)throw new IllegalArgumentException("POST 参数不能超过 64 KB");
            connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            try(OutputStream output=connection.getOutputStream()){output.write(body);}
        }
        try {
            int status=connection.getResponseCode();
            if(status<200 || status>=300)throw new IllegalArgumentException("API 返回 HTTP "+status+"，不跟随重定向");
            if(connection.getContentLengthLong()>SnapshotParser.MAX_BYTES)throw new IllegalArgumentException("API 响应超过 5 MB");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){if(System.nanoTime()>deadline || output.size()+n>SnapshotParser.MAX_BYTES)throw new IllegalArgumentException("API 超过时间或容量限制");output.write(buffer,0,n);}
                return output.toByteArray();
            }
        } finally {connection.disconnect();}
    }
    public String oauthToken(OAuth oauth,long deadline)throws IOException {
        URI token=URI.create(oauth.tokenUrl());if(!origins.contains(origin(token)))throw new IllegalArgumentException("OAuth 地址域名未获允许");
        if(oauth.clientId()==null||oauth.clientId().isBlank()||oauth.clientSecret()==null||oauth.clientSecret().isBlank())throw new IllegalArgumentException("OAuth 客户端配置无效");
        HttpURLConnection c=(HttpURLConnection)token.toURL().openConnection();int wait=(int)Math.max(1,Math.min(3000,(deadline-System.nanoTime())/1_000_000));c.setInstanceFollowRedirects(false);c.setConnectTimeout(wait);c.setReadTimeout(wait);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        String form="grant_type=client_credentials&client_id="+URLEncoder.encode(oauth.clientId(),java.nio.charset.StandardCharsets.UTF_8)+"&client_secret="+URLEncoder.encode(oauth.clientSecret(),java.nio.charset.StandardCharsets.UTF_8)+(oauth.scope()==null||oauth.scope().isBlank()?"":"&scope="+URLEncoder.encode(oauth.scope(),java.nio.charset.StandardCharsets.UTF_8));
        try{c.getOutputStream().write(form.getBytes(java.nio.charset.StandardCharsets.UTF_8));if(c.getResponseCode()!=200)throw new IllegalArgumentException("OAuth 令牌接口返回错误");byte[] bytes=c.getInputStream().readNBytes(SnapshotParser.MAX_BYTES+1);if(bytes.length>SnapshotParser.MAX_BYTES)throw new IllegalArgumentException("OAuth 响应过大");var root=SnapshotParser.tree(bytes,new com.fasterxml.jackson.databind.ObjectMapper());String value=root.path(oauth.tokenField()==null||oauth.tokenField().isBlank()?"access_token":oauth.tokenField()).asText();if(value.isBlank()||value.length()>4000)throw new IllegalArgumentException("OAuth 响应缺少有效令牌");return value;}finally{c.disconnect();}
    }
}
