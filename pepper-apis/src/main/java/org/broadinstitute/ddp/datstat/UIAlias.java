package org.broadinstitute.ddp.datstat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
/**
 * Annotation that helps us mark the name
 * by which the UI knows a particular datstat field
 */
public @interface UIAlias {
    String value();
}