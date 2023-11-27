package org.broadinstitute.ddp.db.dao;

import java.util.Iterator;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTemplateVariable extends SqlObject {
    String INSERT_TEMPLATE_SQL = "insert into template_variable(template_id,variable_name) values (:template_id, :variable_name)";

    @SqlUpdate(INSERT_TEMPLATE_SQL)
    @GetGeneratedKeys
    long insertVariable(@Bind("template_id") long templateId,
                        @Bind("variable_name") String variableName);

    @SqlBatch(INSERT_TEMPLATE_SQL)
    @GetGeneratedKeys("template_variable_id")
    long[] insertVariables(@Bind("template_id")Iterator<Long> templateIds,
                         @Bind("variable_name") Iterator<String> variableNames);
    
    @SqlUpdate("DELETE FROM template_variable WHERE template_variable_id = :id")
    boolean delete(@Bind("id") long templateVariableId);

    @SqlUpdate("UPDATE template_variable "
                + "SET template_id = :template_id, variable_name = :name "
                + "WHERE template_variable_id = :id")
    boolean update(@Bind("id") long templateVariableId,
                    @Bind("template_id") long templateId,
                    @Bind("name") String variableName);
}
