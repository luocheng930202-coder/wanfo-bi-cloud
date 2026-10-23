package com.wanfo.bi.query;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.BiAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Optional shared quotas, expiring leases, cancellation tombstones and bounded diagnostic history. */
@Component
@ConditionalOnProperty(prefix="bi.query-limits",name="coordination",havingValue="redis")
public class RedisQueryCoordinator {
 private final StringRedisTemplate redis;private final BiProperties properties;private final String key;private final ObjectMapper json=new ObjectMapper();
 private final DefaultRedisScript<String> script=new DefaultRedisScript<>();
 public RedisQueryCoordinator(RedisConnectionFactory factory,BiProperties properties){
  if(!(factory instanceof LettuceConnectionFactory lettuce) || lettuce.getClientConfiguration().getCommandTimeout().compareTo(Duration.ofSeconds(2))>0)throw new IllegalStateException("Redis query coordination requires a Lettuce command timeout at most 2 seconds");
  this.redis=new StringRedisTemplate(factory);this.properties=properties;this.key="wanfo:{"+properties.getQueryLimits().getNamespace()+"}:query-state";script.setLocation(new ClassPathResource("query/coordination.lua"));script.setResultType(String.class);
 }
 public static String token(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> limits(){var l=properties.getQueryLimits();return Map.of("maxActive",l.getMaxActive(),"perUser",l.getPerUser(),"perSource",l.getPerSource(),"maxWaiting",l.getMaxWaiting(),"slowMillis",l.getSlowMillis());}
 private String config(){var l=properties.getQueryLimits();return l.getMaxActive()+":"+l.getPerUser()+":"+l.getPerSource()+":"+l.getMaxWaiting();}
 @SuppressWarnings("unchecked") private Map<String,Object> execute(String action,Map<String,Object> payload){try{return json.readValue(redis.execute(script,List.of(key),action,json.writeValueAsString(payload)),Map.class);}catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("BI shared query coordination unavailable");throw new BiAccessException(503,"查询协调服务暂不可用，请稍后重试");}}
 private Map<String,Object> payload(String id,String token,Map<String,Object> event){var p=new LinkedHashMap<String,Object>();p.put("id",id);p.put("token",token);p.put("leaseMs",180000);if(event!=null)p.put("event",event);return p;}
 public int admit(String id,String token,String owner,List<Long> sources,String source,String instanceId){var p=payload(id,token,Map.of("requestId",id,"instanceId",instanceId,"source",source,"startedAt",System.currentTimeMillis(),"durationMs",0,"datasourceCount",sources.size(),"status","running","slow",false));p.put("owner",token(owner));p.put("sources",sources.stream().map(String::valueOf).toList());p.put("limits",limits());p.put("config",config());p.put("waitMs",properties.getQueryLimits().getWaitMillis());return ((Number)execute("admit",p).get("code")).intValue();}
 public int pulse(String id,String token,Map<String,Object> event){return ((Number)execute("pulse",payload(id,token,event)).get("code")).intValue();}
 public void release(String id,String token,Map<String,Object> event){execute("release",payload(id,token,event));}
 public void outcome(String id,Map<String,Object> event){execute("outcome",Map.of("id",id,"event",event));}
 public void cancel(String token){execute("cancel",Map.of("token",token));}
 public boolean cancelRequest(String id){return ((Number)execute("adminCancel",Map.of("id",id)).get("code")).intValue()==1;}
 public Map<String,Object> monitor(String instanceId){var value=execute("monitor",Map.of("limits",limits()));value.put("instanceId",instanceId);for(String field:List.of("running","recent"))if(!(value.get(field) instanceof List<?>))value.put(field,List.of());return value;}
}
