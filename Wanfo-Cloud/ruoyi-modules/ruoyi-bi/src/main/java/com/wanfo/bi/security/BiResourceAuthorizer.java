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
    private static final Map<String, Set<String>> ACTIONS = Map.of(
            "datasource", Set.of("view", "edit", "test", "remove", "grant", "model"),
            "dataset", Set.of("view", "query", "consume", "write", "remove", "grant"),
            "chart", Set.of("view", "edit", "query", "remove", "grant"),
            "metric", Set.of("view", "edit", "query", "publish", "remove", "grant"),
            "dashboard", Set.of("view", "edit", "publish", "consume", "remove", "grant"),
            "asset", Set.of("view", "edit", "remove", "grant"),
            "template", Set.of("view", "edit", "remove", "grant", "use"),
            "dataset-group", Set.of("view", "edit", "remove"));
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
}
