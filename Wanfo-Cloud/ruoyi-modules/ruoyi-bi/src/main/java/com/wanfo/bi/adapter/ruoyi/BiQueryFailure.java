package com.wanfo.bi.adapter.ruoyi;

import com.wanfo.bi.security.BiAccessException;
import com.wanfo.common.core.web.domain.AjaxResult;
import java.sql.*;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Safe query error contract. Never return exception messages, SQL or connection details. */
final class BiQueryFailure {
    static AjaxResult response(Exception exception) {
        String type="QUERY_FAILED";int code=500;
        if(exception instanceof BiAccessException access){code=access.getCode();type=switch(code){case 401->"SESSION_EXPIRED";case 403->"ACCESS_DENIED";case 404->"RESOURCE_UNAVAILABLE";case 409->"VERSION_CHANGED";default->"QUERY_FAILED";};}
        else {
            Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Throwable cause=exception;cause!=null && seen.add(cause);cause=cause.getCause()){
                if(cause instanceof com.wanfo.bi.query.QueryGovernanceException control && control.category()!=null){type=control.category();code=type.equals("QUERY_BUSY")?429:type.equals("QUERY_CANCELLED")?499:504;break;}
                if(cause instanceof SQLTimeoutException || cause instanceof java.util.concurrent.TimeoutException){type="QUERY_TIMEOUT";code=504;break;}
                if(cause instanceof SQLTransientConnectionException || cause instanceof SQLNonTransientConnectionException || cause instanceof SQLException sql && sql.getSQLState()!=null && sql.getSQLState().startsWith("08")){type="DATASOURCE_UNAVAILABLE";code=503;break;}
                if(cause instanceof SQLSyntaxErrorException){type="QUERY_INVALID";code=400;break;}
            }
            if(type.equals("QUERY_FAILED") && (exception instanceof IllegalArgumentException || exception instanceof org.springframework.http.converter.HttpMessageNotReadableException || exception instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException || exception instanceof org.springframework.web.bind.MissingServletRequestParameterException)){type="QUERY_INVALID";code=400;}
        }
        String id=UUID.randomUUID().toString();
        Set<Throwable> ids=Collections.newSetFromMap(new IdentityHashMap<>());for(Throwable cause=exception;cause!=null && ids.add(cause);cause=cause.getCause())if(cause instanceof com.wanfo.bi.query.QueryGovernanceException control){id=control.requestId();break;}
        LoggerFactory.getLogger(BiQueryFailure.class).warn("BI query failed requestId={} category={} exceptionType={}",id,type,exception.getClass().getName());
        AjaxResult result=AjaxResult.error(code,"查询未完成，请根据错误类型处理并提供排查编号");
        result.put("errorType",type);result.put("requestId",id);return result;
    }
    static boolean queryPath(String path){return path!=null && (path.matches("/bi/chart/(data|map-preview|saved/[^/]+/data)") || path.matches("/bi/dashboard/[^/]+/(panels/[^/]+/data|controls/[^/]+/options)"));}
}
