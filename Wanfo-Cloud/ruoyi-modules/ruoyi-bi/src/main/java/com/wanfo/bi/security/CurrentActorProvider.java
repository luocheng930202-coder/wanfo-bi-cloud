package com.wanfo.bi.security;

public interface CurrentActorProvider
{
    /** Returns a verified identity or rejects the request. Never trusts client identity headers. */
    BiActor currentActor();
}
