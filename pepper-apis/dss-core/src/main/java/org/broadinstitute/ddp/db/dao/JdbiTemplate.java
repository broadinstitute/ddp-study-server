package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.TemplateTable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTemplate extends SqlObject {

    String INSERT_TEMPLATE_SQL = "INSERT INTO template(template_code, template_type_id, template_text, revision_id) "
            + "SELECT :templateCode, type.template_type_id, :templateText, :revisionId "
            + "FROM template_type AS type "
            + "WHERE type.template_type_code = :templateType";

    default String generateUniqueCode() {
        return DBUtils.uniqueStandardGuid(getHandle(), TemplateTable.TABLE_NAME, TemplateTable.CODE);
    }

    @SqlUpdate(INSERT_TEMPLATE_SQL)
    @GetGeneratedKeys()
    long insert(@Bind("templateCode") String templateCode,
                @Bind("templateType") TemplateType templateType,
                @Bind("templateText") String templateText,
                @Bind("revisionId") long revisionId);

    @SqlBatch(INSERT_TEMPLATE_SQL)
    @GetGeneratedKeys("template_id")
    long[] insert(@Bind("templateCode") List<String> templateCode,
                @Bind("templateType") Iterator<TemplateType> templateType,
                @Bind("templateText") Iterator<String> templateText,
                @Bind("revisionId") long revisionId);

    @SqlUpdate("UPDATE template "
                + "INNER JOIN template_type AS type ON type.template_type_code = :templateType "
                + "SET template_code = :templateCode, "
                + "template.template_type_id = type.template_type_id, "
                + "template_text = :templateText, "
                + "revision_id = :revisionId "
                + "WHERE template_id = :templateId")
    boolean update(@Bind("templateId") long templateId,
                    @Bind("templateCode") String templateCode,
                    @Bind("templateType") TemplateType templateType,
                    @Bind("templateText") String templateText,
                    @Bind("revisionId") long revisionId);

    @SqlQuery("select template_text from template where template_id = ?")
    Optional<String> getTextById(@Bind("templateId") long templateId);

    @SqlQuery("select rev.revision_id from template as tmpl"
            + " join revision as rev on rev.revision_id = tmpl.revision_id"
            + " where tmpl.template_id = :templateId and rev.end_date is null")
    Optional<Long> getRevisionIdIfActive(@Bind("templateId") long templateId);

    @SqlUpdate("update template set revision_id = :revisionId where template_id = :templateId")
    int updateRevisionIdById(@Bind("templateId") long templateId,
                                @Bind("revisionId") long revisionId);

    @SqlQuery("SELECT tmpl.*, "
            + "       tmpl.revision_id as template_revision_id, "
            + "       type.template_type_code as template_type "
            + "FROM template AS tmpl "
            + "JOIN template_type AS type ON type.template_type_id = tmpl.template_type_id "
            + "WHERE tmpl.template_id = :templateId ")
    @RegisterConstructorMapper(Template.class)
    Optional<Template> fetch(@Bind("templateId") long templateId);
}
