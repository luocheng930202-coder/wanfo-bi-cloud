package com.wanfo.bi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanfo.bi.datasource.CredentialCipher;
import com.wanfo.bi.datasource.snapshot.ApiSnapshotClient;
import com.wanfo.bi.datasource.snapshot.ApiSnapshotClient.OAuth;
import com.wanfo.bi.security.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** One-time OAuth state and grant exchange, bound to the authenticated BI user and stored in Redis. */
@Service
public class ApiOAuthFlowService {
    public record Begin(String authorizationUrl,String state){}
    public record BeginRequest(String authorizationUrl,String tokenUrl,String clientId,String redirectUri,String scope,String tokenField){}
    public record ExchangeRequest(String state,String code,String clientSecret){}
    private static final Duration STATE_TTL=Duration.ofMinutes(10),GRANT_TTL=Duration.ofMinutes(10);
    private static final DefaultRedisScript<String> GETDEL=new DefaultRedisScript<>("local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]) end; return v",String.class);
    private final StringRedisTemplate redis;private final ApiSnapshotClient client;private final CredentialCipher cipher;private final ObjectMapper json;private final CurrentActorProvider actors;private final CapabilityProvider capabilities;private final Set<String> redirectUris;private final SecureRandom random=new SecureRandom();
    public ApiOAuthFlowService(StringRedisTemplate redis,ApiSnapshotClient client,CredentialCipher cipher,ObjectMapper json,CurrentActorProvider actors,CapabilityProvider capabilities,@Value("${bi.external-source.oauth-redirect-uris:}") String redirects){this.redis=redis;this.client=client;this.cipher=cipher;this.json=json;this.actors=actors;this.capabilities=capabilities;this.redirectUris=Arrays.stream(redirects.split(",")).map(String::trim).filter(v->!v.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    public Begin begin(BeginRequest request)throws Exception{
        requireDatasourcePermission();
        if(request==null)throw new IllegalArgumentException("OAuth 配置缺失");
        validateUrl(request.authorizationUrl(),"授权地址");validateUrl(request.tokenUrl(),"令牌地址");
        if(request.clientId()==null||request.clientId().isBlank()||request.clientId().length()>500)throw new IllegalArgumentException("OAuth Client ID 无效");
        URI authorization=URI.create(request.authorizationUrl());if(authorization.getRawQuery()!=null)for(String parameter:authorization.getRawQuery().split("&")){String key=parameter.split("=",2)[0];if(Set.of("response_type","client_id","redirect_uri","state","code_challenge","code_challenge_method","scope").contains(key))throw new IllegalArgumentException("授权地址不能预先包含 OAuth 流程参数");}
        if(request.scope()!=null&&request.scope().length()>1000||request.tokenField()!=null&&request.tokenField().length()>100)throw new IllegalArgumentException("OAuth Scope 或令牌字段过长");
        URI redirect;try{redirect=URI.create(request.redirectUri()==null?"":request.redirectUri());}catch(Exception e){throw new IllegalArgumentException("OAuth 回调地址无效");}if(!redirectUris.contains(redirect.toString())||redirect.getHost()==null||redirect.getUserInfo()!=null||redirect.getFragment()!=null||redirect.getQuery()!=null||!("https".equalsIgnoreCase(redirect.getScheme())||"http".equalsIgnoreCase(redirect.getScheme())&&Set.of("localhost","127.0.0.1").contains(redirect.getHost())))throw new IllegalArgumentException("回调地址须为受信任 HTTPS 地址并加入 bi.external-source.oauth-redirect-uris 白名单");
        String state=random(32),verifier=random(48),challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        Map<String,Object> record=new LinkedHashMap<>();record.put("userId",actors.currentActor().userId());record.put("verifier",verifier);record.put("tokenUrl",request.tokenUrl());record.put("clientId",request.clientId());record.put("redirectUri",redirect.toString());record.put("scope",request.scope()==null?"":request.scope());record.put("tokenField",request.tokenField()==null||request.tokenField().isBlank()?"access_token":request.tokenField());
        redis.opsForValue().set(stateKey(state),json.writeValueAsString(record),STATE_TTL);
        String url=request.authorizationUrl()+(request.authorizationUrl().contains("?")?"&":"?")+"response_type=code&client_id="+enc(request.clientId())+"&redirect_uri="+enc(redirect.toString())+"&state="+enc(state)+"&code_challenge="+enc(challenge)+"&code_challenge_method=S256"+(request.scope()==null||request.scope().isBlank()?"":"&scope="+enc(request.scope()));
        return new Begin(url,state);
    }
    public Map<String,String> exchange(ExchangeRequest request)throws Exception{
        requireDatasourcePermission();
        if(request==null||request.state()==null||!request.state().matches("[A-Za-z0-9_-]{40,100}")||request.code()==null||request.code().isBlank()||request.code().length()>4096||request.clientSecret()==null||request.clientSecret().isBlank()||request.clientSecret().length()>2048)throw new IllegalArgumentException("授权码或客户端密钥无效");
        String raw=redis.execute(GETDEL,List.of(stateKey(request.state())));if(raw==null)throw new IllegalArgumentException("OAuth 授权状态已失效或已使用，请重新授权");
        @SuppressWarnings("unchecked") Map<String,Object> state=json.readValue(raw,Map.class);if(!Objects.equals(String.valueOf(state.get("userId")),String.valueOf(actors.currentActor().userId())))throw new BiAccessException(403,"OAuth 授权状态不属于当前用户");
        OAuth pending=new OAuth(String.valueOf(state.get("tokenUrl")),String.valueOf(state.get("clientId")),request.clientSecret(),String.valueOf(state.get("scope")),String.valueOf(state.get("tokenField")),"authorization_code",null,String.valueOf(state.get("redirectUri")),null);
        OAuth linked=client.exchangeAuthorizationCode(pending,request.code(),String.valueOf(state.get("verifier")),System.nanoTime()+20_000_000_000L);
        if(linked.refreshToken()==null||linked.refreshToken().isBlank())throw new IllegalArgumentException("令牌响应没有 refresh_token；请确认应用开启离线访问权限");
        String grant=random(32);Map<String,Object> grantRecord=Map.of("userId",actors.currentActor().userId(),"oauthCipher",cipher.encrypt(json.writeValueAsString(linked)));redis.opsForValue().set(grantKey(grant),json.writeValueAsString(grantRecord),GRANT_TTL);
        return Map.of("grantId",grant,"status","authorized");
    }
    public OAuth consumeGrant(Long userId,String grantId)throws Exception{
        if(grantId==null||!grantId.matches("[A-Za-z0-9_-]{32,100}"))throw new IllegalArgumentException("OAuth 授权凭证无效");String raw=redis.execute(GETDEL,List.of(grantKey(grantId)));if(raw==null)throw new IllegalArgumentException("OAuth 授权凭证已过期或已使用，请重新授权");@SuppressWarnings("unchecked") Map<String,Object> grant=json.readValue(raw,Map.class);if(!Objects.equals(String.valueOf(grant.get("userId")),String.valueOf(userId)))throw new BiAccessException(403,"OAuth 授权凭证不属于当前用户");return json.readValue(cipher.decrypt(String.valueOf(grant.get("oauthCipher"))),OAuth.class);
    }
    private void validateUrl(String value,String label){try{if(value==null||value.length()>2000)throw new IllegalArgumentException();client.requireAllowedOrigin(URI.create(value));}catch(Exception e){throw new IllegalArgumentException(label+"必须是服务端白名单内的 HTTP(S) 地址");}}
    private void requireDatasourcePermission(){if(!capabilities.has("bi:datasource:add")&&!capabilities.has("bi:datasource:edit"))throw new BiAccessException(403,"缺少数据源管理权限");}
    private String random(int bytes){byte[] value=new byte[bytes];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static String enc(String value){return java.net.URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String stateKey(String state){return "wanfo:bi:oauth:state:"+state;}
    private static String grantKey(String grant){return "wanfo:bi:oauth:grant:"+grant;}
}
