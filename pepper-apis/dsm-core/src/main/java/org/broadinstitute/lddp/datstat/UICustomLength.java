package org.broadinstitute.lddp.datstat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
/**
 * Annotation that keep track of the max length for a question if it shouldn't be 255.
 */
public @interface UICustomLength
{
    int value();
}
