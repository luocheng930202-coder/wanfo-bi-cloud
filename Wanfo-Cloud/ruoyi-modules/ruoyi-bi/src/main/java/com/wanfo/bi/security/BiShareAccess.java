package com.wanfo.bi.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks endpoints that only require a verified login plus a valid share token in the path. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BiShareAccess
{
}
