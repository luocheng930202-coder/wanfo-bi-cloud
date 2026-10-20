package com.wanfo.bi.security;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorScopeTest {
 @Test void bindClearsAfterRunAndMatchesCapturedCapabilities(){
  BiActor actor=new BiActor("test",1L,"alice",null,Set.of());
  AtomicReference<BiActor> seen=new AtomicReference<>();
  ActorScope.run(actor,Set.of("bi:export:add","bi:chart:*"),()->{
   seen.set(ActorScope.actor());
   assertTrue(ActorScope.allows("bi:export:add"));
   assertTrue(ActorScope.allows("bi:chart:query"));
   assertFalse(ActorScope.allows("bi:dashboard:consume"));
  });
  assertEquals(actor,seen.get());
  assertNull(ActorScope.actor());
  assertFalse(ActorScope.active());
 }
}
