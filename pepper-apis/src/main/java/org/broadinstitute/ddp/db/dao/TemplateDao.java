package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.TemplateTable;
import static org.broadinstitute.ddp.constants.SqlConstants.TemplateVariableTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TemplateDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(TemplateDao.class);

    @CreateSqlObject
    JdbiTemplate getJdbiTemplate();

    @CreateSqlObject
    JdbiTemplateVariable getJdbiTemplateVariable();

    @CreateSqlObject
    JdbiVariableSubstitution getJdbiVariableSubstitution();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();


    /**
     * Create new template by inserting all its related data. If template code is not provided, it will be generated.
     *
     * @param template   the template definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default long insertTemplate(Template template, long revisionId) {
        if (template.getTemplateId() != null) {
            throw new IllegalStateException("Template id already set to " + template.getTemplateId());
        }

        JdbiTemplate jdbiTemplate = getJdbiTemplate();
        JdbiTemplateVariable jdbiTemplateVariable = getJdbiTemplateVariable();
        JdbiVariableSubstitution jdbiVariableSubstitution = getJdbiVariableSubstitution();

        TemplateType templateType = template.getTemplateType();
        String templateText = template.getTemplateText();
        String templateCode = template.getTemplateCode();

        if (templateCode == null) {
            templateCode = jdbiTemplate.generateUniqueCode();
            template.setTemplateCode(templateCode);
        }

        long templateId = jdbiTemplate.insert(templateCode,
                                                templateType,
                                                templateText,
                                                revisionId);
        template.setTemplateId(templateId);

        for (TemplateVariable variable : template.getVariables()) {
            String variableName = variable.getName();
            Collection<Translation> translations = variable.getTranslations();

            if (I18nTemplateConstants.DDP.equals(variableName) || I18nTemplateConstants.LAST_UPDATED.equals(variableName)) {
                throw new DaoException("Variable name '" + variableName + "' is not allowed");
            }

            // insert into template variable
            long templateVariableId = jdbiTemplateVariable.insertVariable(templateId, variableName);

            for (Translation translation : translations) {
                String languageCode = translation.getLanguageCode();
                String translatedText = translation.getText();
                jdbiVariableSubstitution.insert(
                        LanguageStore.get(languageCode).getId(),
                        translatedText, revisionId, templateVariableId);
            }
        }
        LOG.debug("inserted template {} for {}", templateId, templateCode);

        return templateId;
    }

    /**
     * End the currently active template by terminating all its related data, such as active variable substitutions.
     *
     * @param templateId the id of active template
     * @param meta       the revision metadata to use for terminating data
     */
    default void disableTemplate(long templateId, RevisionMetadata meta) {
        JdbiRevision jdbiRev = getJdbiRevision();
        JdbiTemplate jdbiTmpl = getJdbiTemplate();
        JdbiVariableSubstitution jdbiSub = getJdbiVariableSubstitution();

        long tmplRevId = jdbiTmpl.getRevisionIdIfActive(templateId)
                .orElseThrow(() -> new NoSuchElementException("Cannot find active template with id " + templateId));
        List<JdbiVariableSubstitution.I18nSubIdRevId> subs = jdbiSub.getActiveRevisionIdsByTemplateId(templateId);

        List<Long> subIds = new ArrayList<>();
        List<Long> oldRevIds = new ArrayList<>();
        for (JdbiVariableSubstitution.I18nSubIdRevId sub : subs) {
            subIds.add(sub.getId());
            oldRevIds.add(sub.getRevisionId());
        }

        oldRevIds.add(0, tmplRevId);
        long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, meta);
        if (newRevIds.length != oldRevIds.size()) {
            throw new DaoException("Not all revisions for template and template substitutions were terminated");
        }

        int numRows = jdbiTmpl.updateRevisionIdById(templateId, newRevIds[0]);
        if (numRows != 1) {
            throw new DaoException("Cannot update revision for template " + templateId);
        }

        long[] newSubRevIds = Arrays.copyOfRange(newRevIds, 1, newRevIds.length);
        int[] numUpdated = jdbiSub.bulkUpdateRevisionIdsBySubIds(subIds, newSubRevIds);
        if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
            throw new DaoException("Not all template substitution revisions were updated");
        }

        Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
        for (long revId : maybeOrphanedIds) {
            if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                LOG.info("Deleted orphaned revision {} by template id {}", revId, templateId);
            }
        }
    }


    @SqlQuery("select t.template_text, count(tv.template_variable_id) as variable_count "
            + "from template as t left join template_variable as tv on tv.template_id = t.template_id "
            + "where t.template_id = :templateId group by t.template_id")
    @RegisterRowMapper(TextAndVarCountMapper.class)
    Optional<TextAndVarCount> findTextAndVarCountById(@Bind("templateId") long templateId);

    @SqlQuery("select t.template_id, t.template_text, count(tv.template_variable_id) as variable_count "
            + "from template as t left join template_variable as tv on tv.template_id = t.template_id "
            + "where t.template_id in (<templateIds>) group by t.template_id")
    @KeyColumn("template_id")
    @RegisterRowMapper(TextAndVarCountMapper.class)
    Map<Long, TextAndVarCount> findAllTextAndVarCountsByIds(@BindList("templateIds") Set<Long> templateIds);

    default Template loadTemplateById(long templateId) {
        try (var stream = loadTemplatesByIds(Set.of(templateId))) {
            return stream.findFirst().orElse(null);
        }
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryTemplatesByIds")
    @RegisterConstructorMapper(Template.class)
    @RegisterConstructorMapper(TemplateVariable.class)
    @RegisterConstructorMapper(Translation.class)
    @UseRowReducer(TemplateReducer.class)
    Stream<Template> loadTemplatesByIds(
            @BindList(value = "templateIds", onEmpty = EmptyHandling.NULL) Iterable<Long> templateIds);

    default Map<Long, Template> collectTemplatesByIds(Iterable<Long> templateIds) {
        try (var stream = loadTemplatesByIds(templateIds)) {
            return stream.collect(Collectors.toMap(Template::getTemplateId, Function.identity()));
        }
    }

    class TemplateReducer implements LinkedHashMapRowReducer<Long, Template> {
        @Override
        public void accumulate(Map<Long, Template> container, RowView view) {
            long id = view.getColumn("template_id", Long.class);
            Template template = container.computeIfAbsent(id, key -> view.getRow(Template.class));

            String variableName = view.getColumn("variable_name", String.class);
            if (StringUtils.isNotBlank(variableName)) {
                var variable = template.getVariable(variableName).orElse(null);
                if (variable == null) {
                    variable = view.getRow(TemplateVariable.class);
                    template.addVariable(variable);
                }

                Long subId = view.getColumn("substitution_id", Long.class);
                if (subId != null) {
                    variable.addTranslation(view.getRow(Translation.class));
                }
            }
        }
    }

    class TextAndVarCountMapper implements RowMapper<TextAndVarCount> {
        @Override
        public TextAndVarCount map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new TextAndVarCount(rs.getString(TemplateTable.TEXT), rs.getInt(TemplateVariableTable.VARIABLE_COUNT));
        }
    }

    class TextAndVarCount {
        private String text;
        private int varCount;

        TextAndVarCount(String text, int varCount) {
            this.text = text;
            this.varCount = varCount;
        }

        public String getText() {
            return text;
        }

        public int getVarCount() {
            return varCount;
        }
    }

    default Map<Long, Map<String, String>> findAllTranslatedVariablesByIds(Set<Long> templateIds, long langCodeId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(TemplateDao.class, "queryAllTranslatedVariablesByTemplateIdsAndLangId")
                .render();
        return getHandle().createQuery(query)
                .bindList("templateIds", new ArrayList<>(templateIds))
                .bind("langCodeId", langCodeId)
                .reduceRows(new HashMap<>(), (accumulator, row) -> {
                    long key = row.getColumn(TemplateTable.ID, Long.class);
                    Map<String, String> vars = accumulator.computeIfAbsent(key, k -> new HashMap<>());
                    String name = row.getColumn(TemplateVariableTable.NAME, String.class);
                    String value = row.getColumn(TemplateVariableTable.SUBSTITUTION_VALUE, String.class);
                    vars.put(name, value);
                    return accumulator;
                });
    }
}
