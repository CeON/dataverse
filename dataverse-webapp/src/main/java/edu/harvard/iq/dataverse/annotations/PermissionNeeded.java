package edu.harvard.iq.dataverse.annotations;

import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to provide data for @Restricted annotation. It must be placed both on METHOD and affected
 * PARAMETERS.
 * <br>
 * The usage is following: we place the annotation on method (possibly multiple times) to configure needed permissions.
 * If there are more that one annotation on this level, attribute <b>on</b> should be used to differentiate between
 * target parameters – and in this case corresponding parameters should have the same <b>value</b> on PARAMETER level
 * annotation. If there is no <b>on</b> attribute filled it will be used for every parameter without filled
 * <b>value</b>.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PermissionNeeded.Container.class)
public @interface PermissionNeeded {
    String value() default "";
    String on() default "";
    Permission[] needs() default { };
    Permission[] needsOnOwner() default { };
    boolean allRequired() default false;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Container {
        PermissionNeeded[] value();
    }
}
