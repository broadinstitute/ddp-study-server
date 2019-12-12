package org.broadinstitute.ddp.logging;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;

import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementCustomizer;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizer;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizerFactory;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@SqlStatementCustomizingAnnotation(LogThisSqlPlease.Factory.class)
public @interface LogThisSqlPlease {
    class Factory implements SqlStatementCustomizerFactory {
        Logger logger = LoggerFactory.getLogger(LogThisSqlPlease.class);

        @Override
        public SqlStatementCustomizer createForMethod(Annotation annotation, Class sqlObjectType, Method method) {
            return q -> q.addCustomizer(new StatementCustomizer() {
                @Override
                public void beforeExecution(PreparedStatement stmt, StatementContext ctx) {
                    logger.trace("Here's Your SQL! " + stmt.toString());
                }
            });
        }
    }
}
