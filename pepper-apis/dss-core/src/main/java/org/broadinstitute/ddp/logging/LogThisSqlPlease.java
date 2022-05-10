package org.broadinstitute.ddp.logging;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementCustomizer;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizer;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizerFactory;
import org.jdbi.v3.sqlobject.customizer.SqlStatementCustomizingAnnotation;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@SqlStatementCustomizingAnnotation(LogThisSqlPlease.Factory.class)
public @interface LogThisSqlPlease {
    @Slf4j
    class Factory implements SqlStatementCustomizerFactory {
        @Override
        public SqlStatementCustomizer createForMethod(Annotation annotation, Class sqlObjectType, Method method) {
            return q -> q.addCustomizer(new StatementCustomizer() {
                @Override
                public void beforeExecution(PreparedStatement stmt, StatementContext ctx) {
                    log.trace("Here's Your SQL! " + stmt.toString());
                }
            });
        }
    }
}
