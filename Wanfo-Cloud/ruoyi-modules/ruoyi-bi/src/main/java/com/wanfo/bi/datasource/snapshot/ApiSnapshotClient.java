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
    public record Config(String url,String dataPath,Map<String,String> headers,Pagination pagination){
        public Config(String url,String path,Map<String,String> headers){this(url,path,headers,null);}
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
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        int wait=(int)Math.max(1,Math.min(3000,(deadline-System.nanoTime())/1_000_000));
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(wait);connection.setReadTimeout(wait);connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept","application/json");
        if(config.headers()!=null){
            if(config.headers().size()>20)throw new IllegalArgumentException("请求头不能超过 20 项");
            for(var header:config.headers().entrySet()){
                String key=header.getKey(),value=header.getValue();
                if(!key.matches("[A-Za-z0-9-]{1,80}") || Set.of("host","connection","content-length","transfer-encoding","proxy-authorization").contains(key.toLowerCase(Locale.ROOT))
                        || value==null || value.length()>4000 || value.indexOf('\r')>=0 || value.indexOf('\n')>=0)throw new IllegalArgumentException("API 请求头无效");
                connection.setRequestProperty(key,value);
            }
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
}
