package com.wanfo.bi.query;
import static org.junit.jupiter.api.Assertions.*;
import com.wanfo.bi.config.BiProperties;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
class QueryGovernorTest {
 private QueryGovernor governor(BiProperties p){return new QueryGovernor(p,()->new com.wanfo.bi.security.BiActor("test",1L,"test",null,java.util.Set.of()));}
 @Test void userAndSourceBudgetsReleaseExactlyOnce() throws Exception {
  var p=new BiProperties();p.getQueryLimits().setWaitMillis(0);p.getQueryLimits().setPerUser(1);p.getQueryLimits().setPerSource(1);var g=governor(p);var a=g.admit("a",List.of(1L));assertEquals("QUERY_BUSY",assertThrows(QueryGovernanceException.class,()->g.admit("a",List.of(2L))).category());assertThrows(QueryGovernanceException.class,()->g.admit("b",List.of(1L)));a.close();a.close();try(var b=g.admit("b",List.of(1L))){assertEquals(1,g.activeCount());}assertEquals(0,g.activeCount());
 }
 @Test void multiSourceAdmissionDoesNotPartiallyReserveAndWakesOnRelease() throws Exception {
  var p=new BiProperties();p.getQueryLimits().setPerSource(1);p.getQueryLimits().setWaitMillis(2000);var g=governor(p);var a=g.admit("a",List.of(1L));var executor=Executors.newSingleThreadExecutor();
  try{var queued=executor.submit(()->g.admit("b",List.of(1L,2L)));long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(g.waitingCount()==0 && System.nanoTime()<until)Thread.yield();assertEquals(1,g.waitingCount());var c=g.admit("c",List.of(2L));a.close();assertFalse(queued.isDone());c.close();queued.get(2,TimeUnit.SECONDS).close();assertEquals(0,g.activeCount());assertEquals(0,g.waitingCount());}finally{a.close();executor.shutdownNow();}
 }
 @Test void queueAndGlobalBudgetsAreBounded() throws Exception {
  var p=new BiProperties();p.getQueryLimits().setMaxActive(1);p.getQueryLimits().setMaxWaiting(0);var g=governor(p);try(var a=g.admit("a",List.of(1L))){assertThrows(QueryGovernanceException.class,()->g.admit("b",List.of(2L)));assertEquals(0,g.waitingCount());}
 }
 @Test void queueExpiryDoesNotLeakSlots() throws Exception {
  var p=new BiProperties();p.getQueryLimits().setMaxActive(1);p.getQueryLimits().setWaitMillis(10);var g=governor(p);try(var a=g.admit("a",List.of(1L))){assertThrows(QueryGovernanceException.class,()->g.admit("b",List.of(2L)));assertEquals(1,g.activeCount());assertEquals(0,g.waitingCount());}assertEquals(0,g.activeCount());
 }
}
