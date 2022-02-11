package org.broadinstitute.dsm.db.structure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)

/**
 * Annotation that helps us mark the table name
 * of the variable in the db
 */
public @interface TableName {
    String name();
    String alias();
    String primaryKey();
    String columnPrefix();
}
