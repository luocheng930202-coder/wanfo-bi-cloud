package com.wanfo.bi.security;

/** Stable mapping for verified host identities; not an authentication API. */
public interface PrincipalDirectory
{
    Long resolve(String source, String type, String externalId, String displayName);
}
