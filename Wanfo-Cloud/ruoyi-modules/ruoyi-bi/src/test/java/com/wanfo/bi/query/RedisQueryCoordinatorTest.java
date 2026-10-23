package com.wanfo.bi.query;
import com.wanfo.bi.config.BiProperties;
import com.wanfo.bi.security.BiAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Two independent governors sharing a real Redis state; enable via WANFO_TEST_REDIS_PORT. */
@EnabledIfEnvironmentVariable(named="WANFO_TEST_REDIS_PORT",matches=".+")
class RedisQueryCoordinatorTest {
 @Test void globalUserAndSourceQuotasAreSharedAndRemoteStopKeepsLeaseUntilClose()throws Exception{
  try(var f=new Fixture(2,1,1,0)){var a=f.first.admit("owner-a",List.of(1L));try{
   assertThrows(QueryGovernanceException.class,()->f.second.admit("owner-a",List.of(2L)));
   assertThrows(QueryGovernanceException.class,()->f.second.admit("owner-b",List.of(1L)));
   try(var b=f.second.admit("owner-b",List.of(2L))){assertEquals(2,((Number)f.first.monitor().get("active")).intValue());assertThrows(QueryGovernanceException.class,()->f.first.admit("owner-c",List.of(3L)));assertTrue(f.second.cancelRequest(a.id()));awaitCancelled(a);assertEquals(2,((Number)f.second.monitor().get("active")).intValue());}
  }finally{a.close();}assertEquals(0,((Number)f.first.monitor().get("active")).intValue());assertFalse(f.first.monitor().toString().contains("owner-a"));}
 }
 @Test void cancelBeforeAdmissionWorksOnOtherNodeAndIsOwnerIsolated()throws Exception{try(var f=new Fixture(4,2,4,0)){String ticket=UUID.randomUUID().toString();f.second.cancelFor("owner-a",ticket);assertThrows(QueryGovernanceException.class,()->f.first.admit("owner-a",List.of(1L),ticket));try(var r=f.first.admit("owner-b",List.of(1L),ticket)){r.check();}assertEquals(0,((Number)f.first.monitor().get("active")).intValue());}}
 @Test void duplicateTicketsAcrossNodesCannotRunTwice()throws Exception{try(var f=new Fixture(4,4,4,0)){String ticket=UUID.randomUUID().toString();try(var r=f.first.admit("owner",List.of(1L),ticket)){assertThrows(QueryGovernanceException.class,()->f.second.admit("owner",List.of(1L),ticket));assertEquals(1,((Number)f.first.monitor().get("active")).intValue());}}}
 @Test void sharedQueueIsBoundedAndRemoteCancellationRemovesWaiter()throws Exception{
  try(var f=new Fixture(1,1,1,1)){f.props.getQueryLimits().setWaitMillis(5000);String ticket=UUID.randomUUID().toString();var executor=Executors.newSingleThreadExecutor();try(var occupied=f.first.admit("active",List.of(1L))){var pending=executor.submit(()->{try(var r=f.second.admit("waiting",List.of(1L),ticket)){return false;}catch(QueryGovernanceException expected){return true;}});long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(((Number)f.first.monitor().get("waiting")).intValue()==0 && System.nanoTime()<deadline)Thread.sleep(20);assertEquals(1,((Number)f.first.monitor().get("waiting")).intValue());assertThrows(QueryGovernanceException.class,()->f.first.admit("overflow",List.of(1L)));f.first.cancelFor("waiting",ticket);assertTrue(pending.get(3,TimeUnit.SECONDS));assertEquals(0,((Number)f.first.monitor().get("waiting")).intValue());}finally{executor.shutdownNow();}}
 }
 @Test void expiredCrashedNodeLeaseIsReclaimedAndHistoryIsBounded()throws Exception{
  try(var f=new Fixture(1,1,1,0)){String id=UUID.randomUUID().toString(),token=RedisQueryCoordinator.token("crashed");assertEquals(1,f.coordinator.admit(id,token,"crashed",List.of(1L),"chart","dead-node"));var json=new com.fasterxml.jackson.databind.ObjectMapper();var state=json.readValue(f.redis.opsForValue().get(f.key),Map.class);var active=(Map<String,Map<String,Object>>)state.get("active");active.get(id).put("untilAt",0);f.redis.opsForValue().set(f.key,json.writeValueAsString(state));try(var r=f.second.admit("new-owner",List.of(1L))){assertTrue(f.second.monitor().toString().contains("lease_expired"));}assertEquals(0,((Number)f.second.monitor().get("active")).intValue());for(int i=0;i<205;i++)try(var r=f.first.admit("bounded",List.of(1L))){}assertEquals(200,((List<?>)f.second.monitor().get("recent")).size());}
 }
 @Test void unavailableRedisNeverFallsBackToLocalAdmission(){var config=new RedisStandaloneConfiguration("127.0.0.1",1);var client=LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).shutdownTimeout(Duration.ZERO).build();var factory=new LettuceConnectionFactory(config,client);factory.afterPropertiesSet();var p=new BiProperties();var governor=new QueryGovernor(p,()->null);try{governor.configureCoordinator(new RedisQueryCoordinator(factory,p));assertThrows(BiAccessException.class,()->governor.admit("owner",List.of(1L)));assertEquals(0,governor.activeCount());}finally{governor.shutdown();factory.destroy();}}
 private void awaitCancelled(QueryGovernor.Run run)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<deadline){try{run.check();Thread.sleep(20);}catch(QueryGovernanceException cancelled){return;}}fail("Cross-node cancellation was not delivered");}
 private static class Fixture implements AutoCloseable{
  final BiProperties props=new BiProperties();final LettuceConnectionFactory factory;final StringRedisTemplate redis;final RedisQueryCoordinator coordinator;final QueryGovernor first,second;final String key;
  Fixture(int active,int user,int source,int waiting){props.getQueryLimits().setNamespace("test"+UUID.randomUUID().toString().replace("-",""));props.getQueryLimits().setMaxActive(active);props.getQueryLimits().setPerUser(user);props.getQueryLimits().setPerSource(source);props.getQueryLimits().setMaxWaiting(waiting);props.getQueryLimits().setWaitMillis(0);
   var config=new RedisStandaloneConfiguration(System.getenv().getOrDefault("WANFO_TEST_REDIS_HOST","127.0.0.1"),Integer.parseInt(System.getenv("WANFO_TEST_REDIS_PORT")));String password=System.getenv("WANFO_TEST_REDIS_PASSWORD");if(password!=null && !password.isBlank())config.setPassword(password);
   var client=LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).shutdownTimeout(Duration.ZERO).build();factory=new LettuceConnectionFactory(config,client);factory.afterPropertiesSet();redis=new StringRedisTemplate(factory);key="wanfo:{"+props.getQueryLimits().getNamespace()+"}:query-state";coordinator=new RedisQueryCoordinator(factory,props);first=new QueryGovernor(props,()->null);second=new QueryGovernor(props,()->null);first.configureCoordinator(coordinator);second.configureCoordinator(new RedisQueryCoordinator(factory,props));
  }
  public void close(){first.shutdown();second.shutdown();redis.delete(key);factory.destroy();}
 }
}
