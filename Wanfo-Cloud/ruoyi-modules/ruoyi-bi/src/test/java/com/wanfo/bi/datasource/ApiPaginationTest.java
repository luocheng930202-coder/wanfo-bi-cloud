package com.wanfo.bi.datasource;
import com.wanfo.bi.datasource.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ApiPaginationTest {
    @Test void postJsonOAuthAndNestedObjectExpansionAreSupportedWithinAllowedOrigin()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/token",exchange->{exchange.getRequestBody().readAllBytes();byte[] bytes="{\"access_token\":\"test-token\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/data",exchange->{assertEquals("POST",exchange.getRequestMethod());String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertTrue(body.contains("\"tenant\":7"));assertEquals("Bearer test-token",exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes="{\"rows\":[{\"user\":{\"id\":7,\"name\":\"A\"},\"active\":true}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{String origin="http://127.0.0.1:"+server.getAddress().getPort();var client=new ApiSnapshotClient(origin);var config=new ApiSnapshotClient.Config(origin+"/data","rows",Map.of(),null,"POST",Map.of("tenant",7),true,new ApiSnapshotClient.OAuth(origin+"/token","client","secret","","access_token"));var data=ApiSnapshotReader.read(client,config,new ObjectMapper());assertEquals(List.of("user.id","user.name","active"),data.columns().stream().map(SnapshotData.Column::name).toList());assertEquals(List.of("7","A","true"),data.rows().get(0));}finally{server.stop(0);}
    }
    @Test void pageNumberPaginationCollectsAllRowsAndRejectsPartialRepeatedOrChangingResults()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/pages",exchange->{String query=exchange.getRequestURI().getRawQuery();int page=Integer.parseInt(Arrays.stream(query.split("&")).filter(s->s.startsWith("page=")).findFirst().orElseThrow().substring(5));String body=page==1?"{\"total\":3,\"rows\":[{\"amount\":1},{\"amount\":2}]}":"{\"total\":3,\"rows\":[{\"amount\":3}]}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/repeated",exchange->{byte[] bytes="{\"total\":4,\"rows\":[{\"amount\":1},{\"amount\":2}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            String origin="http://127.0.0.1:"+server.getAddress().getPort();var client=new ApiSnapshotClient(origin);var json=new ObjectMapper();var pagination=new ApiSnapshotClient.Pagination("page","size",2,1,5,"total");
            var data=ApiSnapshotReader.read(client,new ApiSnapshotClient.Config(origin+"/pages","rows",Map.of(),pagination),json);assertEquals(3,data.rows().size());assertEquals("3",data.rows().get(2).get(0));
            assertThrows(IllegalArgumentException.class,()->ApiSnapshotReader.read(client,new ApiSnapshotClient.Config(origin+"/pages","rows",Map.of(),new ApiSnapshotClient.Pagination("page","size",2,1,1,"total")),json));
            assertThrows(IllegalArgumentException.class,()->ApiSnapshotReader.read(client,new ApiSnapshotClient.Config(origin+"/repeated","rows",Map.of(),pagination),json));
            assertThrows(IllegalArgumentException.class,()->ApiSnapshotReader.read(client,new ApiSnapshotClient.Config(origin+"/pages?page=1","rows",Map.of(),pagination),json));
        }finally{server.stop(0);}
    }
}
