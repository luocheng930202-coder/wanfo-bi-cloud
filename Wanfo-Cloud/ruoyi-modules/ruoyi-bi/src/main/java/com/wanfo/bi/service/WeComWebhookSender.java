package com.wanfo.bi.service;

import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sends bounded markdown notices only to an administrator-configured HTTPS robot URL. */
final class WeComWebhookSender {
    private final URI endpoint;
    private final ObjectMapper json;
    WeComWebhookSender(String endpoint,String allowedHosts,ObjectMapper json){
        this.json=json;
        if(endpoint==null||endpoint.isBlank()){this.endpoint=null;return;}
        URI uri;
        try{uri=URI.create(endpoint.trim());}catch(Exception e){throw new IllegalArgumentException("企业微信机器人地址无效");}
        Set<String> allowed=Arrays.stream(allowedHosts==null?new String[0]:allowedHosts.split(",")).map(String::trim).filter(s->!s.isBlank()).map(s->s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||!allowed.contains(uri.getHost().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("企业微信机器人必须使用管理员允许的 HTTPS 域名");
        this.endpoint=uri;
    }
    boolean configured(){return endpoint!=null;}
    void send(String markdown)throws Exception{
        if(endpoint==null)throw new IllegalStateException("企业微信通道未配置");
        HttpURLConnection connection=(HttpURLConnection)endpoint.toURL().openConnection();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(8000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] body=json.writeValueAsBytes(Map.of("msgtype","markdown","markdown",Map.of("content",markdown.substring(0,Math.min(markdown.length(),1800)))));
            connection.setFixedLengthStreamingMode(body.length);try(var out=connection.getOutputStream()){out.write(body);}int status=connection.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("企业微信返回 HTTP "+status);
        }finally{connection.disconnect();}
    }
}
