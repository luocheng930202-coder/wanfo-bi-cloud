package com.wanfo.bi.query;

import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.*;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Instance-local admission, deadlines and cancellation. Identity always comes from the host. */
@Component
public class QueryGovernor {
 private static final ThreadLocal<Run> CURRENT=new ThreadLocal<>();
 private final BiProperties properties;private final CurrentActorProvider actors;
 private final Object lock=new Object();private final Map<String,Integer> users=new HashMap<>();private final Map<Long,Integer> sources=new HashMap<>();
 private final Map<String,Run> runs=new HashMap<>();private final Set<String> pending=new HashSet<>();private final LinkedHashMap<String,Long> cancelled=new LinkedHashMap<>();
 private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"bi-query-deadline");t.setDaemon(true);return t;});
 private final ThreadPoolExecutor cancellers=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"bi-query-cancel");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final Deque<Run> recent=new ArrayDeque<>();
 private final String instanceId=UUID.randomUUID().toString();
 private RedisQueryCoordinator coordinator;
 private final ScheduledThreadPoolExecutor coordinationPulse=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"bi-query-coordination");t.setDaemon(true);return t;});
 @org.springframework.beans.factory.annotation.Autowired(required=false)
 public void configureCoordinator(RedisQueryCoordinator value){coordinator=value;coordinationPulse.setRemoveOnCancelPolicy(true);coordinationPulse.scheduleWithFixedDelay(this::pulse,500,500,TimeUnit.MILLISECONDS);}
 private void pulse(){List<Run> all;synchronized(lock){all=List.copyOf(runs.values());}for(var run:all){if(run.closed.get())continue;try{int status=coordinator.pulse(run.id,run.distributedToken,run.view());if(status!=1)run.stop("QUERY_CANCELLED");}catch(Exception unavailable){for(var activeRun:all)activeRun.stop("QUERY_CANCELLED");return;}}}
 private int active,waiting;private volatile boolean shuttingDown;
 public QueryGovernor(BiProperties properties,CurrentActorProvider actors){this.properties=properties;this.actors=actors;deadlines.setRemoveOnCancelPolicy(true);}
 public Run enter(Collection<Long> ids) throws QueryGovernanceException {
  if(CURRENT.get()!=null)throw new IllegalStateException("Nested query scope");
  var actor=actors.currentActor();var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();String ticket=null;
  if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)ticket=servlet.getRequest().getHeader("X-BI-Query-Key");
  var run=admit(actor.source()+":"+actor.userId(),ids,validKey(ticket));CURRENT.set(run);
  if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet && servlet.getResponse()!=null)servlet.getResponse().setHeader("X-BI-Query-Id",run.id());
  return run;
 }
 public static Run current(){return CURRENT.get();}
 static String validKey(String key){if(key==null)return null;if(!key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException("查询取消标识格式不正确");return key.toLowerCase(java.util.Locale.ROOT);}
 public void cancel(String ticket){var actor=actors.currentActor();cancelFor(actor.source()+":"+actor.userId(),Objects.requireNonNull(validKey(ticket)));}
 void cancelFor(String owner,String ticket){
  Run run;String key=owner+"|"+ticket;synchronized(lock){purge();cancelled.put(key,System.nanoTime()+TimeUnit.SECONDS.toNanos(60));while(cancelled.size()>1024)cancelled.remove(cancelled.keySet().iterator().next());run=runs.get(key);lock.notifyAll();}
  if(run!=null)run.stop("QUERY_CANCELLED");
  if(coordinator!=null)coordinator.cancel(RedisQueryCoordinator.token(key));
 }
 private void purge(){long now=System.nanoTime();cancelled.entrySet().removeIf(e->e.getValue()<=now);}
 Run admit(String owner,Collection<Long> ids) throws QueryGovernanceException {return admit(owner,ids,null);}
 Run admit(String owner,Collection<Long> ids,String ticket) throws QueryGovernanceException {
  var keys=ids.stream().filter(Objects::nonNull).distinct().sorted().toList();var limits=properties.getQueryLimits();long end=System.nanoTime()+limits.getWaitMillis()*1_000_000L;boolean queued=false,admitted=false;String key=ticket==null?null:owner+"|"+ticket;
  String id=UUID.randomUUID().toString(),distributedToken=RedisQueryCoordinator.token(key==null?owner+"|"+id:key);
  synchronized(lock){if(shuttingDown)throw busy();purge();if(key!=null && (runs.containsKey(key) || !pending.add(key)))throw busy();try{
   while(true){
    if(shuttingDown)throw busy();
    if(key!=null && cancelled.containsKey(key))throw new QueryGovernanceException("QUERY_CANCELLED",id,null);
    boolean full=active>=limits.getMaxActive() || users.getOrDefault(owner,0)>=limits.getPerUser() || keys.stream().anyMatch(source->sources.getOrDefault(source,0)>=limits.getPerSource());
    if(coordinator!=null){int result=coordinator.admit(id,distributedToken,owner,keys,sourceKind(),instanceId);if(result==-2)throw new QueryGovernanceException("QUERY_CANCELLED",id,null);if(result<0)throw busy();if(result==1 && full){coordinator.release(id,distributedToken,null);}else full=result!=1;}
    if(!full)break;
    if(!queued){if(waiting>=limits.getMaxWaiting())throw busy();waiting++;queued=true;}
    long left=end-System.nanoTime();if(left<=0)throw busy();try{lock.wait(Math.max(1,Math.min(coordinator==null?Long.MAX_VALUE:100,left/1_000_000)));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new QueryGovernanceException("QUERY_CANCELLED",id,e);}
   }
   active++;users.merge(owner,1,Integer::sum);for(var source:keys)sources.merge(source,1,Integer::sum);var run=new Run(owner,keys,key,id,distributedToken);runs.put(run.key,run);admitted=true;try{run.timer=deadlines.schedule(()->run.stop("QUERY_TIMEOUT"),properties.getQueryTimeoutSeconds(),TimeUnit.SECONDS);}catch(RejectedExecutionException failure){run.close();throw busy();}return run;
  }finally{if(queued)waiting--;if(key!=null)pending.remove(key);if(!admitted && coordinator!=null)try{coordinator.release(id,distributedToken,null);}catch(Exception ignored){/* Shared lease bounds cleanup after a coordination outage. */}}}
 }
 private QueryGovernanceException busy(){return new QueryGovernanceException("QUERY_BUSY",UUID.randomUUID().toString(),null);}
 public Map<String,Object> monitor(){if(coordinator!=null)return coordinator.monitor(instanceId);synchronized(lock){var limits=properties.getQueryLimits();return Map.of("scope","instance","instanceId",instanceId,"active",active,"waiting",waiting,"limits",Map.of("maxActive",limits.getMaxActive(),"perUser",limits.getPerUser(),"perSource",limits.getPerSource(),"maxWaiting",limits.getMaxWaiting(),"slowMillis",limits.getSlowMillis()),"running",runs.values().stream().map(Run::view).toList(),"recent",recent.stream().map(Run::view).toList());}}
 public boolean cancelRequest(String requestId){requestId=validKey(requestId);if(requestId==null)throw new IllegalArgumentException("缺少排查编号");if(coordinator!=null)return coordinator.cancelRequest(requestId);final String selected=requestId;Run found;synchronized(lock){found=runs.values().stream().filter(r->r.id.equals(selected)).findFirst().orElse(null);}if(found==null)return false;found.stop("QUERY_CANCELLED");return true;}
 public final class Run implements AutoCloseable {
  private final String owner,id,key,distributedToken;private final List<Long> keys;private final AtomicBoolean closed=new AtomicBoolean();private final long started=System.nanoTime(),budget=TimeUnit.SECONDS.toNanos(properties.getQueryTimeoutSeconds());
  private final String sourceKind=sourceKind();private final Set<Statement> statements=ConcurrentHashMap.newKeySet();private final long startedAt=System.currentTimeMillis();private volatile boolean failed;private volatile long finished;private volatile String reason;private ScheduledFuture<?> timer;
  private Run(String owner,List<Long> keys,String key,String id,String distributedToken){this.owner=owner;this.id=id;this.distributedToken=distributedToken;this.keys=keys;this.key=key==null?owner+"|"+id:key;}
  public String id(){return id;}
  private Map<String,Object> view(){String status=!closed.get()?(reason==null?"running":"cancelling"):reason!=null?reason:failed?"failed":"completed";return Map.of("requestId",id,"instanceId",instanceId,"source",sourceKind,"startedAt",startedAt,"durationMs",elapsedMillis(),"datasourceCount",keys.size(),"status",status,"slow",elapsedMillis()>=properties.getQueryLimits().getSlowMillis());}
  public long elapsedMillis(){return Math.max(0,((finished==0?System.nanoTime():finished)-started)/1_000_000);}
  public Map<String,Object> diagnostics(){return Map.of("requestId",id,"slow",elapsedMillis()>=properties.getQueryLimits().getSlowMillis(),"durationMs",elapsedMillis());}
  public void check() throws QueryGovernanceException {if(closed.get())throw new QueryGovernanceException("QUERY_CANCELLED",id,null);if(reason==null && System.nanoTime()-started>=budget)stop("QUERY_TIMEOUT");if(reason!=null)throw new QueryGovernanceException(reason,id,null);}
  public void watch(Statement statement) throws java.sql.SQLException {check();statement.setQueryTimeout((int)Math.max(1,(budget-(System.nanoTime()-started)+999_999_999L)/1_000_000_000L));statements.add(statement);if(reason!=null){cancelStatement(statement);check();}}
  public void unwatch(Statement statement){statements.remove(statement);}
  void stop(String why){synchronized(this){if(closed.get() || reason!=null)return;reason=why;}for(var statement:statements)cancelStatement(statement);org.slf4j.LoggerFactory.getLogger(QueryGovernor.class).info("BI query cancellation requestId={} reason={}",id,why);}
  private void cancelStatement(Statement statement){try{cancellers.execute(()->{if(!closed.get() && statements.contains(statement))try{statement.cancel();}catch(Exception ignored){org.slf4j.LoggerFactory.getLogger(QueryGovernor.class).debug("BI driver cancellation did not complete requestId={}",id);}});}catch(RejectedExecutionException ignored){org.slf4j.LoggerFactory.getLogger(QueryGovernor.class).warn("BI cancellation queue full requestId={}",id);}}
  public QueryGovernanceException failure(Exception cause){failed=true;if(closed.get() && coordinator!=null)try{coordinator.outcome(id,view());}catch(Exception ignored){}return new QueryGovernanceException(reason,id,cause);}
  @Override public void close(){if(!closed.compareAndSet(false,true))return;finished=System.nanoTime();if(timer!=null)timer.cancel(false);statements.clear();if(CURRENT.get()==this)CURRENT.remove();try{
   if(elapsedMillis()>=properties.getQueryLimits().getSlowMillis())org.slf4j.LoggerFactory.getLogger(QueryGovernor.class).warn("BI slow query requestId={} source={} elapsedMs={} datasourceCount={}",id,sourceKind,elapsedMillis(),keys.size());
  }finally{synchronized(lock){runs.remove(key,this);recent.addFirst(this);while(recent.size()>200)recent.removeLast();active--;decrement(users,owner);for(var source:keys)decrement(sources,source);lock.notifyAll();}if(coordinator!=null)try{coordinator.release(id,distributedToken,view());}catch(Exception ignored){/* Admission stays fail-closed; existing lease expires. */}}}
 }
 private static String sourceKind(){var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet){String path=servlet.getRequest().getRequestURI();if(path.startsWith("/bi/dashboard/"))return "dashboard";if(path.startsWith("/bi/chart/"))return "chart";}return "background";}
 private static <T> void decrement(Map<T,Integer> counts,T key){counts.computeIfPresent(key,(k,n)->n<=1?null:n-1);}
 int activeCount(){synchronized(lock){return active;}} int waitingCount(){synchronized(lock){return waiting;}}
 @jakarta.annotation.PreDestroy public void shutdown(){List<Run> all;synchronized(lock){shuttingDown=true;lock.notifyAll();all=List.copyOf(runs.values());}for(var run:all)run.stop("QUERY_CANCELLED");deadlines.shutdownNow();coordinationPulse.shutdownNow();cancellers.shutdown();}
}
