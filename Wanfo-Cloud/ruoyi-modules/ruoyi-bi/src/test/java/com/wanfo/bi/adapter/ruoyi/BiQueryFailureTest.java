package com.wanfo.bi.adapter.ruoyi;

import static org.junit.jupiter.api.Assertions.*;
import com.wanfo.bi.security.BiAccessException;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

class BiQueryFailureTest {
    @Test void typedCausesAreClassifiedWithoutReturningSecrets(){
        for(var entry:java.util.Map.of(new SQLTimeoutException("secret SQL"),"QUERY_TIMEOUT",new SQLTransientConnectionException("password=secret"),"DATASOURCE_UNAVAILABLE",new SQLSyntaxErrorException("private_column"),"QUERY_INVALID").entrySet()){
            var r=BiQueryFailure.response(new RuntimeException("jdbc:private",entry.getKey()));assertEquals(entry.getValue(),r.get("errorType"));assertFalse(r.toString().contains("secret"));assertFalse(r.toString().contains("private"));assertDoesNotThrow(()->java.util.UUID.fromString(r.get("requestId").toString()));
        }
    }
    @Test void accessAndVersionsRemainDistinctAndGenericErrorsStayGeneric(){
        assertEquals("DATASOURCE_UNAVAILABLE",BiQueryFailure.response(new SQLException("private", "08001")).get("errorType"));
        assertEquals("ACCESS_DENIED",BiQueryFailure.response(new BiAccessException(403,"private" )).get("errorType"));
        assertEquals("VERSION_CHANGED",BiQueryFailure.response(new BiAccessException(409,"private")).get("errorType"));
        assertEquals("QUERY_INVALID",BiQueryFailure.response(new IllegalArgumentException("private")).get("errorType"));
        assertEquals("QUERY_FAILED",BiQueryFailure.response(new RuntimeException("connection timeout SQL text is not a classification")).get("errorType"));
    }
    @Test void resolverHandlesOnlyQueryRoutes() throws Exception {
        var resolver=new BiQueryExceptionResolver();
        var request=new MockHttpServletRequest();var response=new org.springframework.mock.web.MockHttpServletResponse();
        request.setRequestURI("/bi/chart/data");assertNotNull(resolver.resolveException(request,response,null,new IllegalArgumentException("secret SQL")));
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("QUERY_INVALID"));assertFalse(response.getContentAsString().contains("secret"));
        request.setRequestURI("/bi/chart/saved");response=new org.springframework.mock.web.MockHttpServletResponse();assertNull(resolver.resolveException(request,response,null,new RuntimeException("original")));assertEquals("",response.getContentAsString());
    }
    @Test void controlledQueriesRetainTheirCorrelationAndSafeCategory(){
        String id=java.util.UUID.randomUUID().toString();for(String kind:java.util.List.of("QUERY_TIMEOUT","QUERY_CANCELLED","QUERY_BUSY")){var response=BiQueryFailure.response(new com.wanfo.bi.query.QueryGovernanceException(kind,id,new java.sql.SQLException("private SQL")));assertEquals(kind,response.get("errorType"));assertEquals(id,response.get("requestId"));assertFalse(response.toString().contains("private"));}
    }
    @Test void queryPathsAreBoundedAndIdsAreUnique(){
        assertTrue(BiQueryFailure.queryPath("/bi/dashboard/1/panels/map/data"));assertTrue(BiQueryFailure.queryPath("/bi/chart/saved/1/data"));assertFalse(BiQueryFailure.queryPath("/bi/chart/saved"));assertFalse(BiQueryFailure.queryPath(null));assertNotEquals(BiQueryFailure.response(new RuntimeException()).get("requestId"),BiQueryFailure.response(new RuntimeException()).get("requestId"));
    }
}
