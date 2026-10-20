package com.wanfo.bi.security;

import java.util.Set;

/** Host-neutral identity. IDs refer to bi_principal, not host tables. */
public record BiActor(String source, Long userId, String username, Long organizationId, Set<Long> roleIds)
{
    public BiActor
    {
        roleIds = Set.copyOf(roleIds);
    }
}
