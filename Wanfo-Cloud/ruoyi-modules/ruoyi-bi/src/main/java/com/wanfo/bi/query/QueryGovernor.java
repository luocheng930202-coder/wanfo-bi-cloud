package com.wanfo.bi.query;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Per-process bounded admission; all source permits are acquired atomically. */
@Component
public class QueryGovernor {
 private static final ThreadLocal<Run> CURRENT=new ThreadLocal<>();
 private final BiProperties properties;private final CurrentActorProvider actors;
 private final Object lock=new Object();private final Map<String,Integer> users=new HashMap<>();private final Map<Long,Integer> sources=new HashMap<>();
 private int active,waiting;
 public QueryGovernor(BiProperties properties,CurrentActorProvider actors){this.properties=properties;this.actors=actors;}
 public Run enter(Collection<Long> ids) throws QueryGovernanceException {
  if(CURRENT.get()!=null)throw new IllegalStateException("Nested query scope");
  var actor=actors.currentActor();var run=admit(actor.source()+":"+actor.userId(),ids);CURRENT.set(run);
  var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet && servlet.getResponse()!=null)servlet.getResponse().setHeader("X-BI-Query-Id",run.id());
  return run;
 }
 public static Run current(){return CURRENT.get();}
 Run admit(String owner,Collection<Long> ids) throws QueryGovernanceException {
  var keys=ids.stream().filter(Objects::nonNull).distinct().sorted().toList();var limits=properties.getQueryLimits();long end=System.nanoTime()+limits.getWaitMillis()*1_000_000L;boolean queued=false;
  synchronized(lock){try{
   while(active>=limits.getMaxActive() || users.getOrDefault(owner,0)>=limits.getPerUser() || keys.stream().anyMatch(id->sources.getOrDefault(id,0)>=limits.getPerSource())){
    if(!queued){if(waiting>=limits.getMaxWaiting())throw busy();waiting++;queued=true;}
    long left=end-System.nanoTime();if(left<=0)throw busy();try{lock.wait(Math.max(1,left/1_000_000));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new QueryGovernanceException("QUERY_CANCELLED",UUID.randomUUID().toString(),e);}
   }
   active++;users.merge(owner,1,Integer::sum);for(var id:keys)sources.merge(id,1,Integer::sum);return new Run(owner,keys);
  }finally{if(queued)waiting--;}}
 }
 private QueryGovernanceException busy(){return new QueryGovernanceException("QUERY_BUSY",UUID.randomUUID().toString(),null);}
 public final class Run implements AutoCloseable {
  private final String owner,id=UUID.randomUUID().toString();private final List<Long> keys;private final AtomicBoolean closed=new AtomicBoolean();private final long started=System.nanoTime();
  private final String sourceKind=sourceKind();
  private Run(String owner,List<Long> keys){this.owner=owner;this.keys=keys;}
  public long elapsedMillis(){return Math.max(0,(System.nanoTime()-started)/1_000_000);}
  public java.util.Map<String,Object> diagnostics(){return java.util.Map.of("requestId",id,"slow",elapsedMillis()>=properties.getQueryLimits().getSlowMillis(),"durationMs",elapsedMillis());}
  public String id(){return id;}
  public QueryGovernanceException failure(Exception cause){return new QueryGovernanceException(null,id,cause);}
  @Override public void close(){if(!closed.compareAndSet(false,true))return;if(CURRENT.get()==this)CURRENT.remove();
   if(elapsedMillis()>=properties.getQueryLimits().getSlowMillis())org.slf4j.LoggerFactory.getLogger(QueryGovernor.class).warn("BI slow query requestId={} source={} elapsedMs={} datasourceCount={}",id,sourceKind,elapsedMillis(),keys.size());
   synchronized(lock){active--;decrement(users,owner);for(var key:keys)decrement(sources,key);lock.notifyAll();}}
 }
 private static String sourceKind(){var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet){String path=servlet.getRequest().getRequestURI();if(path.startsWith("/bi/dashboard/"))return "dashboard";if(path.startsWith("/bi/chart/"))return "chart";}return "background";}
 private static <T> void decrement(Map<T,Integer> counts,T key){counts.computeIfPresent(key,(k,n)->n<=1?null:n-1);}
 int activeCount(){synchronized(lock){return active;}}
 int waitingCount(){synchronized(lock){return waiting;}}
}
