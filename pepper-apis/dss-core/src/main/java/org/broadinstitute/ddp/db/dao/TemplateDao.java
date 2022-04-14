package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;
import static org.broadinstitute.ddp.constants.SqlConstants.TemplateTable;
import static org.broadinstitute.ddp.constants.SqlConstants.TemplateVariableTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DBUtils;
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
     * Template is created only is the {@link Template} object is not null.
     *
     * @param template   the template definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     * @return Long ID of an inserted template, or null - if passed parameter `template` is null
     */
    default Long insertTemplateIfNotNull(Template template, long revisionId) {
        Long templateId = null;
        if (template != null) {
            templateId = insertTemplate(template, revisionId);
        }
        return templateId;
    }

    /**
     * Batch insert templates.
     * Template is created only is the {@link Template} object is not null.
     *
     * @param templates  the template definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     * @return array that with either the db template id or a null if the there was a null in template list
     */
    default Long[] insertTemplates(List<Template> templates, long revisionId) {
        // it is helpful to accept some nulls in the template list, but lets filter those out
        List<Template> nonNullTemplates = templates.stream().filter(Objects::nonNull).collect(toList());
        if (nonNullTemplates.stream().anyMatch(t -> t.getTemplateId() != null)) {
            throw new IllegalStateException("One of the templates had a Template id");
        }
        for (Template template : nonNullTemplates) {
            for (TemplateVariable variable : template.getVariables()) {
                String variableName = variable.getName();

                if (I18nTemplateConstants.DDP.equals(variableName) || I18nTemplateConstants.LAST_UPDATED.equals(variableName)) {
                    throw new DaoException("Variable name '" + variableName + "' is not allowed");
                }
            }
        }
        JdbiTemplate jdbiTemplate = getJdbiTemplate();
        JdbiTemplateVariable jdbiTemplateVariable = getJdbiTemplateVariable();
        JdbiVariableSubstitution jdbiVariableSubstitution = getJdbiVariableSubstitution();

        // Note we are just generating the guids without checking the database. For our purpose the very rare collision
        // is a tolerable problem
        List<String> templateCodes = nonNullTemplates.stream().map(t -> DBUtils.generateUncheckedStandardGuid()).collect(toList());
        Iterator<TemplateType> templateTypes = nonNullTemplates.stream().map(Template::getTemplateType).iterator();
        Iterator<String> templateText = nonNullTemplates.stream().map(Template::getTemplateText).iterator();

        long[] templateIds = jdbiTemplate.insert(templateCodes, templateTypes, templateText, revisionId);

        for (int i = 0; i < templateIds.length; i++) {
            nonNullTemplates.get(i).setTemplateId(templateIds[i]);
            nonNullTemplates.get(i).setTemplateCode(templateCodes.get(i));
        }
        class IdToTemplateVariable {
            Long templateId;
            TemplateVariable variable;

            IdToTemplateVariable(Long id, TemplateVariable variable) {
                this.templateId = id;
                this.variable = variable;
            }
        }

        List<IdToTemplateVariable> templateIdToTemplateVariable = nonNullTemplates.stream()
                .flatMap(t -> t.getVariables().stream().map(v -> new IdToTemplateVariable(t.getTemplateId(), v)))
                .collect(toList());

        long[] variableIds = jdbiTemplateVariable.insertVariables(templateIdToTemplateVariable.stream().map(p -> p.templateId).iterator(),
                templateIdToTemplateVariable.stream().map(p -> p.variable.getName()).iterator());

        for (int i = 0; i < variableIds.length; i++) {
            templateIdToTemplateVariable.get(i).variable.setId(variableIds[i]);
        }

        List<Long> langCodes = templateIdToTemplateVariable.stream()
                .flatMap(v -> v.variable.getTranslations().stream())
                .map(tr -> LanguageStore.get(tr.getLanguageCode()).getId())
                .collect(toList());

        Iterator<String> transTexts = templateIdToTemplateVariable.stream()
                .flatMap(v -> v.variable.getTranslations().stream())
                .map(Translation::getText)
                .iterator();

        List<Long> transVarIds = templateIdToTemplateVariable.stream()
                .flatMap(v -> v.variable.getTranslations().stream().map(tr -> v.variable.getId().orElse(null)))
                .collect(toList());

        jdbiVariableSubstitution.insert(langCodes, transTexts, revisionId, transVarIds);

        Long[] templateIdsToReturn = new Long[templates.size()];
        int j = 0;
        for (int i = 0; i < templates.size(); i++) {
            templateIdsToReturn[i] = templates.get(i) == null ? null : templateIds[j++];
        }
        return templateIdsToReturn;
    }

    /**
     * Create new template by inserting all its related data. If template code is not provided, it will be generated.
     *
     * @param template   the template definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default long insertTemplate(Template template, long revisionId) {
        return this.insertTemplates(List.of(template), revisionId)[0];
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

    default Template loadTemplateByIdAndTimestamp(long templateId, long timestamp) {
        try (var stream = loadTemplatesByIdsAndTimestamp(Set.of(templateId), timestamp)) {
            return stream.findFirst().orElse(null);
        }
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryTemplatesByIdsAndTimestamp")
    @RegisterConstructorMapper(Template.class)
    @RegisterConstructorMapper(TemplateVariable.class)
    @RegisterConstructorMapper(Translation.class)
    @UseRowReducer(TemplateReducer.class)
    Stream<Template> loadTemplatesByIdsAndTimestamp(
            @BindList(value = "templateIds", onEmpty = EmptyHandling.NULL) Iterable<Long> templateIds,
            @Bind("timestamp") long timestamp);

    default Map<Long, Template> collectTemplatesByIdsAndTimestamp(Iterable<Long> templateIds, long timestamp) {
        try (var stream = loadTemplatesByIdsAndTimestamp(templateIds, timestamp)) {
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

    default Map<Long, Map<String, String>> findAllTranslatedVariablesByIds(Set<Long> templateIds, long langCodeId,
                                                                           Long defaultLangCodeId, long timestamp) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(TemplateDao.class, "queryAllTranslatedVariablesByTemplateIdsAndLangId")
                .render();
        return getHandle().createQuery(query)
                .bindList("templateIds", new ArrayList<>(templateIds))
                .bind("langCodeId", langCodeId)
                .bind("defaultLangCodeId", defaultLangCodeId)
                .bind("timestamp", timestamp)
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
