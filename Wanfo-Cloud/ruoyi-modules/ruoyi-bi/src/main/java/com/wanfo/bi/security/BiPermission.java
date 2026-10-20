package com.wanfo.bi.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BiPermission
{
    String value() default "";

    /** When non-empty, any one capability satisfies the check (value is ignored). */
    String[] anyOf() default {};
}
