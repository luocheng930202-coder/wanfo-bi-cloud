package com.wanfo.bi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.wanfo.bi.domain.BiDashboardShareDO;
import com.wanfo.bi.mapper.BiDashboardShareMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardShareService
{
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HASH_PREFIX="{bcrypt-sha256}";
    private static final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder PASSWORDS=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);

    private final BiDashboardShareMapper shareMapper;
    private final DashboardService dashboards;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final com.wanfo.bi.security.LiveActorResolver live;
    @org.springframework.beans.factory.annotation.Value("${bi.share.audit-retention-days:90}")
    private int auditRetentionDays=90;

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${bi.share.maintenance-ms:600000}")
    public void maintainAccessLogs(){shareMapper.purgePasswordAttempts();shareMapper.purgeAccessLogs(Math.max(1,Math.min(3650,auditRetentionDays)));}

    public List<Map<String,Object>> accessLogs(Long dashboardId){
        var identity=live.resolve(actors.currentActor());identity.require("bi:dashboard:publish");Object[] result=new Object[1];
        ActorScope.run(identity.actor(),identity.capabilities(),()->{requireManage(dashboardId);result[0]=shareMapper.accessLogs(dashboardId);});
        return (List<Map<String,Object>>)result[0];
    }

    public DashboardShareService(BiDashboardShareMapper shareMapper, DashboardService dashboards,
                                 BiResourceAuthorizer resources, CapabilityProvider caps,
                                 CurrentActorProvider actors, ObjectMapper json, com.wanfo.bi.security.LiveActorResolver live)
    {
        this.shareMapper = shareMapper;
        this.dashboards = dashboards;
        this.resources = resources;
        this.caps = caps;
        this.actors = actors;
        this.json = json;this.live=live;
    }

    public List<Map<String, Object>> list(Long dashboardId)
    {
        requireManage(dashboardId);
        return shareMapper.listByDashboard(dashboardId).stream().map(this::publicRow).toList();
    }

    @Transactional
    public Map<String, Object> create(Long dashboardId, String dataScope, Integer expireDays, String password)
    {
        requireManage(dashboardId);
        dashboards.shareView(dashboardId);
        String scope = normalizeScope(dataScope);
        BiDashboardShareDO row = new BiDashboardShareDO();
        row.setDashboardId(dashboardId);
        row.setToken(newToken());
        row.setDataScope(scope);
        row.setPasswordHash(hashPassword(password));
        if (expireDays != null && expireDays > 0)
        {
            row.setExpireTime(java.util.Date.from(Instant.now().plus(expireDays, ChronoUnit.DAYS)));
        }
        if ("publisher".equals(scope))
        {
            var identity=live.resolve(actors.currentActor());
            identity.require("bi:dashboard:publish");identity.require("bi:dashboard:consume");
            ActorScope.run(identity.actor(),identity.capabilities(),()->requireManage(dashboardId));
            row.setPublisherActorJson(json.writeValueAsString(identity.actor()));
        }
        row.setCreateBy(actors.currentActor().username());
        shareMapper.insert(row);
        Map<String, Object> out = publicRow(row);
        out.put("token", row.getToken());
        return out;
    }

    @Transactional
    public Map<String, Object> revoke(Long dashboardId, Long shareId)
    {
        requireManage(dashboardId);
        if (shareMapper.disable(shareId, dashboardId) != 1)
        {
            throw new IllegalArgumentException("分享链接不存在或已停用");
        }
        return Map.of("revoked", true);
    }

    public Map<String, Object> view(String token, String password) throws Exception
    {
        var share = requireActive(token, password);
        Map<String, Object> view = withDataScope(share, () -> dashboards.shareView(share.getDashboardId()));
        view.put("dataScope", share.getDataScope());
        audit(share,"VIEW");
        return view;
    }

    public Map<String, Object> panelData(String token, String key, Integer revision, Map<String, Object> input, String password) throws Exception
    {
        var share = requireActive(token, password);
        var result=withDataScope(share, () -> dashboards.sharePanelData(share.getDashboardId(), key, revision, input));audit(share,"DATA");return result;
    }

    public Map<String, Object> options(String token, String control, Integer revision, Map<String, Object> input, String password) throws Exception
    {
        var share = requireActive(token, password);
        var result=withDataScope(share, () -> dashboards.shareOptions(share.getDashboardId(), control, revision, input));audit(share,"OPTIONS");return result;
    }

    private Map<String, Object> publicRow(BiDashboardShareDO row)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("dashboardId", row.getDashboardId());
        m.put("token", row.getToken());
        m.put("dataScope", row.getDataScope());
        m.put("hasPassword", row.getPasswordHash() != null && !row.getPasswordHash().isBlank());
        m.put("expireTime", row.getExpireTime());
        m.put("createBy", row.getCreateBy());
        m.put("createTime", row.getCreateTime());
        return m;
    }

    private void requireManage(Long dashboardId)
    {
        caps.require("bi:dashboard:publish");
        resources.require("dashboard", dashboardId, "publish");
    }

    private BiDashboardShareDO requireActive(String token, String password)
    {
        if (token == null || token.isBlank())
        {
            throw new IllegalArgumentException("分享链接无效");
        }
        BiDashboardShareDO row = shareMapper.selectByToken(token.trim());
        if (row == null)
        {
            throw new BiAccessException(403, "分享链接无效或已停用");
        }
        if (row.getExpireTime() != null && row.getExpireTime().toInstant().isBefore(Instant.now()))
        {
            throw new BiAccessException(403, "分享链接已过期");
        }
        try{live.resolve(actors.currentActor());}catch(BiAccessException ex){audit(row,"ACCESS_DENIED");throw ex;}
        if (row.getPasswordHash() != null && !row.getPasswordHash().isBlank())
        {
            if(password==null||password.isBlank())throw new BiAccessException(401,"需要访问密码");
            Long viewer=actors.currentActor().userId();
            if(shareMapper.passwordAttempts(row.getId(),viewer)>=5){audit(row,"THROTTLED");throw new BiAccessException(429,"密码尝试过多，请10分钟后重试");}
            if (!matchesPassword(password,row.getPasswordHash()))
            {
                shareMapper.passwordAttempt(row.getId(),viewer);
                audit(row,"PASSWORD_REJECTED");
                throw new BiAccessException(401, "需要访问密码");
            }
            if(!row.getPasswordHash().startsWith(HASH_PREFIX))shareMapper.upgradePassword(row.getId(),row.getPasswordHash(),hashPassword(password));
            shareMapper.clearPasswordAttempts(row.getId(),viewer);
        }
        return row;
    }

    private String normalizeScope(String dataScope)
    {
        String scope = dataScope == null ? "viewer" : dataScope.trim().toLowerCase();
        if (!Set.of("viewer", "publisher").contains(scope))
        {
            throw new IllegalArgumentException("数据范围须为 viewer 或 publisher");
        }
        return scope;
    }

    private static String newToken()
    {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashPassword(String password)
    {
        if (password == null || password.isBlank())
        {
            return null;
        }
        if(password.length()>200)throw new IllegalArgumentException("访问密码不能超过200字符");
        return HASH_PREFIX+PASSWORDS.encode(com.wanfo.bi.security.SecurityDigest.hash(password.trim()));
    }

    static boolean matchesPassword(String password,String stored) {
        if(password==null||password.isBlank()||password.length()>200||stored==null)return false;
        String digest=com.wanfo.bi.security.SecurityDigest.hash(password.trim());
        if(stored.startsWith(HASH_PREFIX))return PASSWORDS.matches(digest,stored.substring(HASH_PREFIX.length()));
        return stored.matches("[0-9a-f]{64}")&&MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII));
    }
    private void audit(BiDashboardShareDO share,String event){shareMapper.auditAccess(share.getId(),actors.currentActor().userId(),event);}

    private <T> T withDataScope(BiDashboardShareDO share, ThrowingSupplier<T> task) throws Exception
    {
        try{return withDataScopeCurrent(share,task);}catch(com.wanfo.bi.security.BiAccessException ex){audit(share,"ACCESS_DENIED");throw ex;}
    }

    private <T> T withDataScopeCurrent(BiDashboardShareDO share, ThrowingSupplier<T> task) throws Exception
    {
        boolean publisher="publisher".equals(share.getDataScope());
        BiActor reference=actors.currentActor();
        if(publisher){
            if(share.getPublisherActorJson()==null||share.getPublisherActorJson().isBlank())throw new BiAccessException(403,"分享配置缺少创建者身份");
            reference=json.readValue(share.getPublisherActorJson(),BiActor.class);
        }
        var identity=live.resolve(reference);
        identity.require("bi:dashboard:consume");
        if(publisher)identity.require("bi:dashboard:publish");
        Object[] box=new Object[1];Exception[] error=new Exception[1];
        ActorScope.run(identity.actor(),identity.capabilities(),()->{
            try{if(publisher)resources.require("dashboard",share.getDashboardId(),"publish");box[0]=task.get();}
            catch(Exception ex){error[0]=ex;}
        });
        if(error[0]!=null)throw error[0];
        return (T)box[0];
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T>
    {
        T get() throws Exception;
    }
}
