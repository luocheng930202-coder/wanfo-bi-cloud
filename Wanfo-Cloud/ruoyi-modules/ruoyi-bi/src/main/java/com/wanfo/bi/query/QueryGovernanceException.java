package com.wanfo.bi.query;
/** Safe control outcome and correlation across service/HTTP boundaries. */
public class QueryGovernanceException extends java.sql.SQLException {
 private final String category,requestId;
 public QueryGovernanceException(String category,String requestId,Throwable cause){super("BI query did not complete",cause);this.category=category;this.requestId=requestId;}
 public String category(){return category;} public String requestId(){return requestId;}
}
