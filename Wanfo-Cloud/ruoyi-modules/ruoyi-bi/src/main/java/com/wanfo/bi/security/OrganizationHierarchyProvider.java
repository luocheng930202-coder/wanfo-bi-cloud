package com.wanfo.bi.security;

import java.util.List;

/** Resolves a verified BI organization principal to its active host-organization subtree. */
public interface OrganizationHierarchyProvider
{
    List<Long> activeSubtree(Long organizationPrincipalId);
}
