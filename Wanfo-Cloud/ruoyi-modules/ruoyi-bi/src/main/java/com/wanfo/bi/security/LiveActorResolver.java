package com.wanfo.bi.security;
import java.util.Set;
/** Re-resolves a stored principal through the host; stored role/capability claims are never trusted. */
public interface LiveActorResolver {
 record Snapshot(BiActor actor,Set<String> capabilities){
  public Snapshot {capabilities=Set.copyOf(capabilities);}
  public void require(String permission){if(capabilities.stream().noneMatch(p->"*:*:*".equals(p) || org.springframework.util.PatternMatchUtils.simpleMatch(p,permission)))throw new BiAccessException(403,"身份资格已改变，请重新授权");}
 }
 Snapshot resolve(BiActor reference);
}
