package com.wanfo.bi.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks the narrowly scoped endpoint whose authentication is a verified DP API key. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BiApiKeyAccess { }
