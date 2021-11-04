package org.broadinstitute.dsm.db.structure;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
/**
 * Defines how to convert the column into a date during date filtering
 */
public @interface DbDateConversion  {
    SqlDateConverter value();
}
