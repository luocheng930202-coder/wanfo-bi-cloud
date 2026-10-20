package com.wanfo.bi.service;
import com.sun.net.httpserver.HttpServer;
import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;

class RemoteTemplateOriginTest {
    @Test void crossOriginResourcesRequireOperatorAllowlistAndRedirectsAreNotFollowed()throws Exception{
        var catalog=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var assets=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger hits=new AtomicInteger();
        var json=new ObjectMapper();String remote="http://127.0.0.1:"+assets.getAddress().getPort();
        byte[] index=json.writeValueAsBytes(Map.of("templates",List.of(Map.of("id","external","name","external","category","test","resource","url:"+remote+"/layout"))));
        byte[] layout=json.writeValueAsBytes(ScreenTemplateServiceTest.layout());
        catalog.createContext("/index",exchange->{exchange.sendResponseHeaders(200,index.length);try(var out=exchange.getResponseBody()){out.write(index);}});
        assets.createContext("/layout",exchange->{hits.incrementAndGet();exchange.sendResponseHeaders(200,layout.length);try(var out=exchange.getResponseBody()){out.write(layout);}});
        catalog.createContext("/redirect",exchange->{exchange.getResponseHeaders().set("Location",remote+"/layout");exchange.sendResponseHeaders(302,-1);exchange.close();});
        catalog.start();assets.start();try{
            var props=new BiProperties();props.getTemplateMarket().setCatalogUrl("http://127.0.0.1:"+catalog.getAddress().getPort()+"/index");var service=new TemplateMarketService(props,p->{},json);
            assertThrows(BiAccessException.class,()->service.content("external"));assertEquals(0,hits.get());
            props.getTemplateMarket().setAllowedOrigins(List.of(remote));assertNotNull(service.content("external").get("content"));assertEquals(1,hits.get());
            props.getTemplateMarket().setCatalogUrl("http://127.0.0.1:"+catalog.getAddress().getPort()+"/redirect");assertFalse(service.catalog().isEmpty());assertEquals(1,hits.get());
        }finally{catalog.stop(0);assets.stop(0);}
    }
}
