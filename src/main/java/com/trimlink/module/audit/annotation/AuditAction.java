package com.trimlink.module.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    
    /**
     * The name of the action (e.g., "CREATE_SHOP", "DELETE_APPOINTMENT").
     */
    String action();

    /**
     * The type of resource being affected (e.g., "SHOP", "USER").
     */
    String resource() default "";
}
