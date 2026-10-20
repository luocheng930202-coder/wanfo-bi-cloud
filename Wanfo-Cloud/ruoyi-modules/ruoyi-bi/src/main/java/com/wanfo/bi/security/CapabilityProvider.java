package com.wanfo.bi.security;

public interface CapabilityProvider
{
    void require(String capability);

    default boolean has(String capability)
    {
        try { require(capability); return true; }
        catch (BiAccessException exception)
        {
            if (exception.getCode() != 403) { throw exception; }
            return false;
        }
    }
}
