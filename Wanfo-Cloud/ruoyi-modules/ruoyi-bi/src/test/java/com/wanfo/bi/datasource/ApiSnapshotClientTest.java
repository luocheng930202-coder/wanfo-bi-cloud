package com.wanfo.bi.datasource;
import com.wanfo.bi.datasource.snapshot.ApiSnapshotClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiSnapshotClientTest {
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
