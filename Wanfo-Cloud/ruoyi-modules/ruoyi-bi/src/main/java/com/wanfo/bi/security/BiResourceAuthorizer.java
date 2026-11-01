package com.wanfo.bi.security;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.wanfo.bi.mapper.BiResourceMapper;
import org.springframework.stereotype.Service;

/** Explicit allow grants: function permissions AND resource grants; owners manage their resources. */
@Service
public class BiResourceAuthorizer
{
    private static final Map<String, Set<String>> ACTIONS = Map.ofEntries(
            Map.entry("datasource", Set.of("view", "edit", "test", "remove", "grant", "model")),
            Map.entry("dataset", Set.of("view", "query", "consume", "write", "remove", "grant")),
            Map.entry("chart", Set.of("view", "edit", "query", "remove", "grant")),
            Map.entry("metric", Set.of("view", "edit", "query", "publish", "remove", "grant")),
            Map.entry("dashboard", Set.of("view", "edit", "publish", "consume", "remove", "grant")),
            Map.entry("pipeline", Set.of("view", "edit", "publish", "rollback", "run", "remove", "grant")),
            Map.entry("asset", Set.of("view", "edit", "remove", "grant")),
            Map.entry("template", Set.of("view", "edit", "remove", "grant", "use")),
            Map.entry("dataset-group", Set.of("view", "edit", "remove")),
            Map.entry("data_api", Set.of("view", "manage", "grant", "remove")),
            Map.entry("metric_set", Set.of("view", "edit", "remove", "grant")));
    private final BiResourceMapper mapper;
    private final CurrentActorProvider actors;
    private final CapabilityProvider capabilities;

    public BiResourceAuthorizer(BiResourceMapper mapper, CurrentActorProvider actors, CapabilityProvider capabilities)
    {
        this.mapper = mapper;
        this.actors = actors;
        this.capabilities = capabilities;
    }

    public boolean allowed(String type, Long id, String action)
    {
        if (id == null || !ACTIONS.getOrDefault(type, Set.of()).contains(action)) { return false; }
        BiActor actor = actors.currentActor();
        if (capabilities.has("bi:resource:admin")) { return true; }
        if (actor.userId().equals(mapper.owner(type, id))) { return true; }
        Set<Long> principals = new HashSet<>(actor.roleIds());
        principals.add(actor.userId());
        if (actor.organizationId() != null) { principals.add(actor.organizationId()); }
        return mapper.granted(type, id, action).stream().anyMatch(principals::contains);
    }

    /** Returns the visible IDs for one resource type in a single grant query. */
    public List<Long> visibleIds(String type, String action)
    {
        if (!ACTIONS.getOrDefault(type, Set.of()).contains(action)) return List.of();
        if (capabilities.has("bi:resource:admin")) return mapper.allResourceIds(type);
        BiActor actor = actors.currentActor();
        Set<Long> principals = new HashSet<>(actor.roleIds());
        principals.add(actor.userId());
        if (actor.organizationId() != null) principals.add(actor.organizationId());
        return mapper.visibleResourceIds(type, action, List.copyOf(principals));
    }

    public void require(String type, Long id, String action)
    {
        if (!allowed(type, id, action)) { throw new BiAccessException(403, "没有该资源的操作权限"); }
    }

    public void register(String type, Long id)
    {
        mapper.register(type, id, actors.currentActor().userId());
    }

    public void requireAdministrator()
    {
        capabilities.require("bi:resource:admin");
    }

    public List<Map<String, Object>> grants(String type, Long id)
    {
        require(type, id, "grant");
        return mapper.list(type, id);
    }

    public void change(String type, Long id, Long principal, String action, boolean grant)
    {
        require(type, id, "grant");
        if (principal == null || !ACTIONS.getOrDefault(type, Set.of()).contains(action) || mapper.principalExists(principal) != 1)
        {
            throw new IllegalArgumentException("无效的授权对象或操作");
        }
        if (grant) { mapper.grant(type, id, principal, action); }
        else { mapper.revoke(type, id, principal, action); }
    }

    /** Approve access-request style grants with optional expiry; caller must already be authorized. */
    public void grantUntil(String type, Long id, Long principal, String action, java.time.LocalDateTime expiresAt)
    {
        require(type, id, "grant");
        if (principal == null || !ACTIONS.getOrDefault(type, Set.of()).contains(action) || mapper.principalExists(principal) != 1)
            throw new IllegalArgumentException("无效的授权对象或操作");
        if (expiresAt == null) mapper.grant(type, id, principal, action);
        else mapper.grantUntil(type, id, principal, action, expiresAt);
    }
}
