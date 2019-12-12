package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTemplateVariable extends SqlObject {

    @SqlUpdate("insert into template_variable(template_id,variable_name) values (:template_id, :variable_name)")
    @GetGeneratedKeys
    long insertVariable(@Bind("template_id") long templateId,
                        @Bind("variable_name") String variableName);
    
    @SqlUpdate("DELETE FROM template_variable WHERE template_variable_id = :id")
    boolean delete(@Bind("id") long templateVariableId);

    @SqlUpdate("UPDATE template_variable "
                + "SET template_id = :template_id, variable_name = :name "
                + "WHERE template_variable_id = :id")
    boolean update(@Bind("id") long templateVariableId,
                    @Bind("template_id") long templateId,
                    @Bind("name") String variableName);
}
