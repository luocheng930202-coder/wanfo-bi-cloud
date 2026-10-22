package com.wanfo.bi.query;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wanfo.bi.config.BiProperties;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
class QueryCancellationTest {
 private static final String KEY=java.util.UUID.randomUUID().toString();
 @Test void cancellationIsOwnerScopedAndDoesNotReleaseRunningCapacity() throws Exception {
  var p=new BiProperties();var g=new QueryGovernor(p,null);var statement=mock(java.sql.Statement.class);var signalled=new CountDownLatch(1);doAnswer(i->{signalled.countDown();return null;}).when(statement).cancel();
  try(var run=g.admit("a",List.of(1L),KEY)){run.watch(statement);g.cancelFor("b",KEY);run.check();verify(statement,never()).cancel();g.cancelFor("a",KEY);assertTrue(signalled.await(2,TimeUnit.SECONDS));assertEquals("QUERY_CANCELLED",assertThrows(QueryGovernanceException.class,run::check).category());assertEquals(1,g.activeCount());}finally{g.shutdown();}assertEquals(0,g.activeCount());
 }
 @Test void cancellationBeforeArrivalPreventsExecutionAndDuplicateKeysAreRejected() throws Exception {
  var g=new QueryGovernor(new BiProperties(),null);try{g.cancelFor("a",KEY);assertEquals("QUERY_CANCELLED",assertThrows(QueryGovernanceException.class,()->g.admit("a",List.of(1L),KEY)).category());assertEquals(0,g.activeCount());try(var b=g.admit("b",List.of(1L),KEY)){assertEquals("QUERY_BUSY",assertThrows(QueryGovernanceException.class,()->g.admit("b",List.of(1L),KEY)).category());}}finally{g.shutdown();}
 }
 @Test void queuedCancellationWakesWaiterWithoutTakingAnySourcePermit() throws Exception {
  var p=new BiProperties();p.getQueryLimits().setPerUser(1);p.getQueryLimits().setWaitMillis(2000);var g=new QueryGovernor(p,null);var worker=Executors.newSingleThreadExecutor();
  try(var active=g.admit("a",List.of(1L))){var result=worker.submit(()->{try(var ignored=g.admit("a",List.of(2L),KEY)){return "unexpected";}catch(QueryGovernanceException e){return e.category();}});long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(g.waitingCount()==0 && System.nanoTime()<end)Thread.yield();assertEquals(1,g.waitingCount());g.cancelFor("a",KEY);assertEquals("QUERY_CANCELLED",result.get(2,TimeUnit.SECONDS));assertEquals(1,g.activeCount());assertEquals(0,g.waitingCount());}finally{worker.shutdownNow();g.shutdown();}
 }
 @Test void deadlineSignalsDriverAndRejectsLateResults() throws Exception {
  var p=new BiProperties();p.setQueryTimeoutSeconds(1);var g=new QueryGovernor(p,null);var statement=mock(java.sql.Statement.class);var signalled=new CountDownLatch(1);doAnswer(i->{signalled.countDown();return null;}).when(statement).cancel();try(var run=g.admit("a",List.of(1L))){run.watch(statement);verify(statement).setQueryTimeout(1);assertTrue(signalled.await(3,TimeUnit.SECONDS));assertEquals("QUERY_TIMEOUT",assertThrows(QueryGovernanceException.class,run::check).category());assertEquals("QUERY_TIMEOUT",run.failure(new java.sql.SQLException("driver detail")).category());assertEquals(1,g.activeCount());}finally{g.shutdown();}
 }
 @Test void publicCancellationUsesVerifiedIdentityAndNeverClientOwner() throws Exception {
  var actor=new java.util.concurrent.atomic.AtomicReference<>(new com.wanfo.bi.security.BiActor("host",2L,"other",null,Set.of()));var g=new QueryGovernor(new BiProperties(),actor::get);try(var run=g.admit("host:1",List.of(1L),KEY)){g.cancel(KEY);run.check();actor.set(new com.wanfo.bi.security.BiActor("host",1L,"owner",null,Set.of()));g.cancel(KEY);assertThrows(QueryGovernanceException.class,run::check);assertThrows(IllegalArgumentException.class,()->g.cancel("invalid"));}finally{g.shutdown();}
 }
}
