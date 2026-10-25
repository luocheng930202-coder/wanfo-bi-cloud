package com.wanfo.bi.datasource;
import com.wanfo.bi.datasource.snapshot.ApiSnapshotClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiSnapshotClientTest {
    @Test void authorizationCodeExchangeUsesPkceAndRefreshRotatesRefreshToken()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var grantSeen=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/token",exchange->{String form=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);grantSeen.set(form);String body=form.contains("grant_type=authorization_code")?"{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\"}":"{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\"}";byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/data",exchange->{assertEquals("Bearer access-2",exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes="[{\"id\":1}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{String origin="http://127.0.0.1:"+server.getAddress().getPort();var client=new ApiSnapshotClient(origin);var pending=new ApiSnapshotClient.OAuth(origin+"/token","client","secret","read","access_token","authorization_code",null,origin+"/callback",null);var linked=client.exchangeAuthorizationCode(pending,"sample-code","sample-verifier",System.nanoTime()+10_000_000_000L);assertTrue(grantSeen.get().contains("grant_type=authorization_code"));assertTrue(grantSeen.get().contains("code_verifier=sample-verifier"));assertEquals("refresh-1",linked.refreshToken());var prepared=client.refreshOAuth(new ApiSnapshotClient.OAuth(origin+"/token","client","secret","read","access_token","refresh_token",linked.refreshToken(),origin+"/callback",null),System.nanoTime()+10_000_000_000L);assertEquals("access-2",prepared.accessToken());assertEquals("refresh-2",prepared.refreshToken());assertEquals("[{\"id\":1}]",new String(client.fetch(new ApiSnapshotClient.Config(origin+"/data","",Map.of(),null,"GET",null,false,prepared)),java.nio.charset.StandardCharsets.UTF_8));}finally{server.stop(0);}
    }
    @Test void onlyExplicitOriginIsAccessibleAndRedirectsAreNotFollowed()throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var redirects=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/data",exchange->{
            if(!"Bearer test".equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}
            byte[] body="[{\"a\":1}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/destination");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/destination",exchange->{redirects.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.start();
        try {
            String origin="http://127.0.0.1:"+server.getAddress().getPort();
            var config=new ApiSnapshotClient.Config(origin+"/data","",Map.of("Authorization","Bearer test"));
            assertThrows(IllegalArgumentException.class,()->new ApiSnapshotClient("").fetch(config));
            var client=new ApiSnapshotClient(origin);
            assertEquals("[{\"a\":1}]",new String(client.fetch(config),java.nio.charset.StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class,()->client.fetch(new ApiSnapshotClient.Config(origin+"/redirect","",Map.of())));
            assertEquals(0,redirects.get());
            assertThrows(IllegalArgumentException.class,()->client.fetch(new ApiSnapshotClient.Config(origin+"/data","",Map.of("Host","other"))));
            assertThrows(IllegalArgumentException.class,()->client.fetch(new ApiSnapshotClient.Config(origin+"/data","",Map.of("X-Test","bad\r\nHeader"))));
            assertThrows(IllegalArgumentException.class,()->client.fetch(new ApiSnapshotClient.Config("file:///etc/passwd","",Map.of())));
        } finally {server.stop(0);}
    }
}
