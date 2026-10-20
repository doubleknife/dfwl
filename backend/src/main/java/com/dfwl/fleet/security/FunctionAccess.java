package com.dfwl.fleet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a protected API to registered page or button functions. anyOf uses OR semantics. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FunctionAccess {
    String[] anyOf() default {};
    /** For authenticated personal resources whose service enforces owner/Purpose, not role grants. */
    boolean self() default false;
    /** POST solely to parse an uploaded file without writing business facts; requires PAGE functions. */
    boolean readOnlyPost() default false;
}
