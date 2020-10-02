package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiVariableSubstitution extends SqlObject {

    @SqlUpdate("insert into i18n_template_substitution"
            + " (language_code_id,substitution_value,revision_id,template_variable_id) values(?,?,?,?)")
    @GetGeneratedKeys()
    long insert(long languageCodeId, String translatedText, long revisionId, long templateVariableId);
    
    @SqlUpdate("INSERT INTO "
            + "     i18n_template_substitution(language_code_id,substitution_value,revision_id,template_variable_id)"
            + " SELECT lc.language_code_id, :translatedText, :revisionId, :templateVariableId "
            + "   FROM language_code AS lc "
            + "  WHERE lc.iso_language_code = :languageCode")
    @GetGeneratedKeys()
    long insert(@Bind("languageCode") String languageCode,
                @Bind("translatedText") String translatedText,
                @Bind("revisionId") long revisionId,
                @Bind("templateVariableId") long templateVariableId);

    @SqlUpdate("DELETE FROM i18n_template_substitution "
                + "WHERE i18n_template_substitution_id = :substitutionId ")
    boolean delete(@Bind("substitutionId") long substitutionId);

    @SqlUpdate("UPDATE i18n_template_substitution "
                + "SET language_code_id = :languageCodeId, "
                + "substitution_value = :text, "
                + "revision_id = :revisionId "
                + "WHERE i18n_template_substitution_id = :substitutionId")
    boolean update(@Bind("substitutionId") long substitutionId, 
                    @Bind("revisionId") long revisionId,
                    @Bind("languageCodeId") long languageCodeId,
                    @Bind("text") String text);

    @SqlUpdate("UPDATE i18n_template_substitution AS i18n "
                + "INNER JOIN language_code AS lc ON lc.iso_language_code = :languageCode "
                + "SET i18n.language_code_id = lc.language_code_id, "
                + "substitution_value = :text, "
                + "revision_id = :revisionId "
                + "WHERE i18n_template_substitution_id = :substitutionId")
    boolean update(@Bind("substitutionId") long substitutionId, 
                    @Bind("revisionId") long revisionId,
                    @Bind("languageCode") String languageCode,
                    @Bind("text") String text);

    @SqlQuery("SELECT sub.i18n_template_substitution_id AS id, "
                + "     lc.iso_language_code, "
                + "     sub.substitution_value AS text, "
                + "     sub.revision_id AS revision_id "
                + "FROM i18n_template_substitution AS sub "
                + "JOIN language_code AS lc ON lc.language_code_id = sub.language_code_id "
                + "WHERE sub.template_variable_id = :templateVariableId")
    @RegisterConstructorMapper(Translation.class)
    List<Translation> fetchSubstitutionsForTemplateVariable(@Bind("templateVariableId") long templateVariableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryActiveRevisionIdsByTemplateId")
    @RegisterRowMapper(I18nSubIdRevIdMapper.class)
    List<I18nSubIdRevId> getActiveRevisionIdsByTemplateId(@Bind("templateId") long templateId);
  
    @SqlBatch("update i18n_template_substitution set revision_id = :revisionId"
            + " where i18n_template_substitution_id = :subId")
    int[] bulkUpdateRevisionIdsBySubIds(@Bind("subId") List<Long> subIds,
                                        @Bind("revisionId") long[] revisionIds);

    class I18nSubIdRevIdMapper implements RowMapper<I18nSubIdRevId> {
        @Override
        public I18nSubIdRevId map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new I18nSubIdRevId(
                    rs.getLong("i18n_template_substitution_id"),
                    rs.getLong("revision_id"));
        }
    }

    class I18nSubIdRevId {

        private long id;
        private long revisionId;

        I18nSubIdRevId(long id, long revisionId) {
            this.id = id;
            this.revisionId = revisionId;
        }

        public long getId() {
            return id;
        }

        public long getRevisionId() {
            return revisionId;
        }
    }
}
