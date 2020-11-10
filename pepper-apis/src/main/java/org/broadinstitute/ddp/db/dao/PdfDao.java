package org.broadinstitute.ddp.db.dao;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.pdf.MailingAddressTemplateDto;
import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;
import org.broadinstitute.ddp.db.dto.pdf.PhysicianInstitutionTemplateDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CompositeAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfTemplate;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface PdfDao extends SqlObject {

    @CreateSqlObject
    PdfSql getPdfSql();


    default long insertConfigVersionWithDataSources(PdfVersion version) {
        long versionId = getPdfSql().insertConfigVersion(version);
        version.setId(versionId);
        for (PdfDataSource source : version.getDataSources()) {
            insertDataSource(versionId, source);
        }
        return versionId;
    }

    default long insertDataSource(long versionId, PdfDataSource source) {
        PdfSql pdfSql = getPdfSql();
        long sourceId = pdfSql.insertBaseDataSource(versionId, source.getType());
        source.setId(sourceId);
        if (source.getType() == PdfDataSourceType.ACTIVITY) {
            PdfActivityDataSource activitySource = (PdfActivityDataSource) source;
            DBUtils.checkInsert(1, pdfSql.insertActivityDataSource(
                    sourceId, activitySource.getActivityId(), activitySource.getVersionId()));
        }
        return sourceId;
    }

    default long insertTemplate(PdfTemplate template) {
        PdfSql pdfSql = getPdfSql();
        long templateId = pdfSql.insertBaseTemplate(template.getRawBytes(), template.getType());
        template.setId(templateId);
        switch (template.getType()) {
            case MAILING_ADDRESS:
                DBUtils.checkInsert(1, pdfSql.insertMailingAddressTemplate((MailingAddressTemplate) template));
                break;
            case PHYSICIAN_INSTITUTION:
                DBUtils.checkInsert(1, pdfSql.insertPhysicianInstitutionTemplate((PhysicianInstitutionTemplate) template));
                break;
            case CUSTOM:
                insertCustomTemplate((CustomTemplate) template);
                break;
            default:
                throw new DaoException("unhandled pdf template type " + template.getType());
        }
        return templateId;
    }

    private void insertCustomTemplate(CustomTemplate template) {
        PdfSql pdfSql = getPdfSql();
        DBUtils.checkInsert(1, pdfSql.insertBaseCustomTemplate(template.getId()));
        for (PdfSubstitution substitution : template.getSubstitutions()) {
            substitution.setTemplateId(template.getId());
            long subId = insertSubstitution(substitution);
            pdfSql.assignSubstitutionToCustomTemplate(template.getId(), subId);
        }
    }

    default long insertSubstitution(PdfSubstitution substitution) {
        PdfSql pdfSql = getPdfSql();
        long subId = pdfSql.insertBaseSubstitution(substitution.getPlaceholder(), substitution.getType());
        substitution.setId(subId);
        switch (substitution.getType()) {
            case PROFILE:
                String profileField = ((ProfileSubstitution) substitution).getFieldName();
                if (!ProfileSubstitution.SUPPORTED_PROFILE_FIELDS.contains(profileField)) {
                    throw new DaoException("Unsupported pdf substitution profile field name '" + profileField + "'");
                }
                DBUtils.checkInsert(1, pdfSql.insertProfileSubstitution(subId, profileField));
                break;
            case ACTIVITY_DATE:
                DBUtils.checkInsert(1, pdfSql.insertActivityDateSubstitution(
                        subId, ((ActivityDateSubstitution) substitution).getActivityId()));
                break;
            case ANSWER:
                insertAnswerSubstitution((AnswerSubstitution) substitution);
                break;
            default:
                throw new DaoException("unhandled pdf substitution type " + substitution.getType());
        }
        return subId;
    }

    private void insertAnswerSubstitution(AnswerSubstitution substitution) {
        PdfSql pdfSql = getPdfSql();
        DBUtils.checkInsert(1, pdfSql.insertBaseAnswerSubstitution(
                substitution.getId(), substitution.getActivityId(),
                substitution.getQuestionStableId(), substitution.getParentQuestionStableId()));

        if (substitution.getQuestionType() == QuestionType.BOOLEAN) {
            BooleanAnswerSubstitution boolSubstitution = (BooleanAnswerSubstitution) substitution;
            DBUtils.checkInsert(1, pdfSql.insertBooleanAnswerSubstitution(substitution.getId(), boolSubstitution.checkIfFalse()));
        }
    }

    /**
     * Create new pdf document configuration, with a single starting version.
     *
     * @param config the pdf configuration
     * @return id of newly created pdf configuration
     */
    default long insertNewConfig(PdfConfiguration config, List<PdfTemplate> templates) {
        PdfSql pdfSql = getPdfSql();
        long configId = pdfSql.insertConfigInfo(config.getInfo());
        config.getInfo().setId(configId);

        config.getVersion().setConfigId(configId);
        long versionId = insertConfigVersionWithDataSources(config.getVersion());

        for (int i = 0, size = templates.size(); i < size; i++) {
            int order = i + 1;
            long templateId = insertTemplate(templates.get(i));
            pdfSql.assignTemplateToVersion(versionId, templateId, order);
            config.addTemplateId(templateId);
        }

        return configId;
    }

    /**
     * Add a new version to an existing pdf document configuration.
     *
     * @param config the pdf configuration describing the new version
     * @return id of new pdf version
     * @throws DaoException if associated pdf document configuration does not already exist
     */
    default long insertNewConfigVersion(PdfConfiguration config, List<PdfTemplate> templates) {
        PdfConfigInfo info = findConfigInfoByStudyIdAndName(config.getStudyId(), config.getConfigName())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find pdf document configuration for creating new pdf version using studyId=%d configurationName=%s",
                        config.getStudyId(), config.getConfigName())));
        config.getInfo().setId(info.getId());

        config.getVersion().setConfigId(info.getId());
        long versionId = insertConfigVersionWithDataSources(config.getVersion());

        PdfSql pdfSql = getPdfSql();

        for (int i = 0, size = templates.size(); i < size; i++) {
            int order = i + 1;
            long templateId = insertTemplate(templates.get(i));
            pdfSql.assignTemplateToVersion(versionId, templateId, order);
        }

        return config.getVersion().getId();
    }


    default void deleteConfigVersionAndDataSources(PdfVersion version) {
        for (PdfDataSource source : version.getDataSources()) {
            deleteDataSource(source);
        }
        DBUtils.checkDelete(1, getPdfSql().deleteConfigVersionById(version.getId()));
    }

    default void deleteDataSource(PdfDataSource source) {
        PdfSql pdfSql = getPdfSql();
        if (source.getType() == PdfDataSourceType.ACTIVITY) {
            DBUtils.checkDelete(1, pdfSql.deleteActivityDataSourceBySourceId(source.getId()));
        }
        DBUtils.checkDelete(1, pdfSql.deleteBaseDataSourceById(source.getId()));
    }

    default void deleteTemplate(PdfTemplate template) {
        PdfSql pdfSql = getPdfSql();
        switch (template.getType()) {
            case MAILING_ADDRESS:
                DBUtils.checkDelete(1, pdfSql.deleteMailingAddressTemplateByTemplateId(template.getId()));
                break;
            case PHYSICIAN_INSTITUTION:
                DBUtils.checkDelete(1, pdfSql.deletePhysicianInstitutionTemplateByTemplateId(template.getId()));
                break;
            case CUSTOM:
                deleteCustomTemplate((CustomTemplate) template);
                break;
            default:
                throw new DaoException("unhandled pdf template type " + template.getType());
        }
        DBUtils.checkDelete(1, pdfSql.deleteBaseTemplateById(template.getId()));
    }

    private void deleteCustomTemplate(CustomTemplate template) {
        PdfSql pdfSql = getPdfSql();
        List<PdfSubstitution> substitutions = template.getSubstitutions();
        DBUtils.checkDelete(substitutions.size(), pdfSql.unassignSubstitutionsFromCustomTemplate(template.getId()));
        for (PdfSubstitution substitution : substitutions) {
            deleteSubstitution(substitution);
        }
        DBUtils.checkDelete(1, pdfSql.deleteBaseCustomTemplateByTemplateId(template.getId()));
    }

    default void deleteSubstitution(PdfSubstitution substitution) {
        PdfSql pdfSql = getPdfSql();
        switch (substitution.getType()) {
            case PROFILE:
                DBUtils.checkDelete(1, pdfSql.deleteProfileSubstitutionBySubId(substitution.getId()));
                break;
            case ACTIVITY_DATE:
                DBUtils.checkDelete(1, pdfSql.deleteActivityDateSubstitutionBySubId(substitution.getId()));
                break;
            case ANSWER:
                deleteAnswerSubstitution((AnswerSubstitution) substitution);
                break;
            default:
                throw new DaoException("unhandled pdf substitution type " + substitution.getType());
        }
        DBUtils.checkDelete(1, pdfSql.deleteBaseSubstitutionById(substitution.getId()));
    }

    private void deleteAnswerSubstitution(AnswerSubstitution substitution) {
        PdfSql pdfSql = getPdfSql();
        if (substitution.getQuestionType() == QuestionType.BOOLEAN) {
            DBUtils.checkDelete(1, pdfSql.deleteBooleanAnswerSubstitutionBySubId(substitution.getId()));
        }
        DBUtils.checkDelete(1, pdfSql.deleteBaseAnswerSubstitutionBySubId(substitution.getId()));
    }

    /**
     * Remove a single version of a pdf document configuration. If this is the last version, will also delete the associated pdf document
     * configuration as well.
     *
     * @param config the pdf document version to delete
     * @return true if associated pdf document was also deleted
     */
    default boolean deleteSpecificConfigVersion(PdfConfiguration config) {
        PdfVersion version = config.getVersion();
        List<Long> templateIds = config.getTemplateIds();

        PdfSql pdfSql = getPdfSql();
        DBUtils.checkDelete(templateIds.size(), pdfSql.unassignTemplatesFromVersion(version.getId()));

        for (Long templateId : templateIds) {
            PdfTemplate template = findFullTemplateByTemplateId(templateId).orElseThrow(() -> new DDPException("Could not find template "
                    + "with id: " + templateId));
            deleteTemplate(template);
        }

        deleteConfigVersionAndDataSources(version);

        int numVersions = findNumVersionsByConfigId(config.getId());
        if (numVersions == 0) {
            DBUtils.checkDelete(1, pdfSql.deleteConfigInfoById(config.getId()));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete all versions of a pdf document configuration, and delete the configuration itself as well.
     *
     * @param configId id of the pdf document configuration
     * @return number of versions deleted
     */
    default int deleteAllConfigVersions(long configId) {
        List<PdfVersion> versions = findOrderedConfigVersionsByConfigId(configId);
        if (versions.isEmpty()) {
            throw new DaoException("Pdf configuration with id=" + configId + " does not have any versions");
        }

        for (PdfVersion version : versions) {
            PdfConfiguration config = findFullConfig(version);
            deleteSpecificConfigVersion(config);
            //TODO query one by one instead of all at once
            findFullTemplatesByVersionId(version.getId()).forEach(template -> deleteTemplate(template));
        }



        return versions.size();
    }


    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryBaseTemplatesByVersionId")
    @RegisterConstructorMapper(PdfTemplateDto.class)
    @RegisterConstructorMapper(value = MailingAddressTemplateDto.class, prefix = "m")
    @RegisterConstructorMapper(value = PhysicianInstitutionTemplateDto.class, prefix = "p")
    @UseRowReducer(BaseTemplatesReducer.class)
    List<PdfTemplate> findBaseTemplatesByVersionId(@Bind("versionId") long versionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findBaseTemplatesByTemplateId")
    @RegisterConstructorMapper(PdfTemplateDto.class)
    @RegisterConstructorMapper(value = MailingAddressTemplateDto.class, prefix = "m")
    @RegisterConstructorMapper(value = PhysicianInstitutionTemplateDto.class, prefix = "p")
    @UseRowReducer(BaseTemplatesReducer.class)
    Optional<PdfTemplate> findBaseTemplateByTemplateId(@Bind("templateId") long templateId);

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQuerySubstitutionsByCustomTemplateIds")
    @RegisterConstructorMapper(value = ProfileSubstitution.class, prefix = "p")
    @RegisterConstructorMapper(value = ActivityDateSubstitution.class, prefix = "d")
    @RegisterConstructorMapper(value = AnswerSubstitution.class, prefix = "a")
    @RegisterConstructorMapper(value = BooleanAnswerSubstitution.class, prefix = "a")
    @UseRowReducer(SubstitutionsReducer.class)
    List<PdfSubstitution> findSubstitutionsByCustomTemplateIds(
            @BindList(value = "customTemplateIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> customTemplateIds);



    @UseStringTemplateSqlLocator
    @SqlQuery("findTemplateIdsByVersionId")
    List<Long> findTemplateIdsByVersionId(@Bind("versionId") long versionId);

    default Optional<PdfTemplate> findFullTemplateByTemplateId(long templateId) {
        Optional<PdfTemplate> optionalTemplate = findBaseTemplateByTemplateId(templateId);
        optionalTemplate.ifPresent(template -> buildFullTemplates(Collections.singletonList(template)));
        return optionalTemplate;
    }

    default List<PdfTemplate> findFullTemplatesByVersionId(long versionId) {
        List<PdfTemplate> templates = findBaseTemplatesByVersionId(versionId);

        buildFullTemplates(templates);

        return templates;
    }

    private void buildFullTemplates(List<PdfTemplate> templates) {
        Map<Long, CustomTemplate> customTemplates = new HashMap<>();
        for (PdfTemplate template : templates) {
            if (template.getType() == PdfTemplateType.CUSTOM) {
                customTemplates.put(template.getId(), (CustomTemplate) template);
            }
        }

        if (!customTemplates.isEmpty()) {
            findSubstitutionsByCustomTemplateIds(customTemplates.keySet())
                    .forEach(sub -> customTemplates.get(sub.getTemplateId()).addSubstitution(sub));

            //build CompositeAnswerSubstitution's
            for (CustomTemplate template: customTemplates.values()) {
                Map<String, CompositeAnswerSubstitution> compositeSubs = new HashMap<>();
                Iterator<PdfSubstitution> subsItr = template.getSubstitutions().iterator();
                while (subsItr.hasNext()) {
                    PdfSubstitution sub = subsItr.next();
                    if (!(sub instanceof AnswerSubstitution)) {
                        continue;
                    }
                    AnswerSubstitution answerSub = (AnswerSubstitution) sub;
                    String parentStableCode = answerSub.getParentQuestionStableId();
                    if (StringUtils.isNotBlank(parentStableCode)) {
                        if (!compositeSubs.containsKey(parentStableCode)) {
                            compositeSubs.put(parentStableCode,
                                    new CompositeAnswerSubstitution(null, answerSub.getActivityId(), parentStableCode));
                        }
                        compositeSubs.get(parentStableCode).addChildAnswerSubstitutions(answerSub);
                        subsItr.remove();
                    }
                }

                if (!compositeSubs.isEmpty()) {
                    compositeSubs.forEach((key, compSub) -> template.addSubstitution(compSub));
                }
            }
        }
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDocumentConfigInfoById")
    @RegisterConstructorMapper(PdfConfigInfo.class)
    Optional<PdfConfigInfo> findConfigInfo(@Bind("id") long configId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDocumentConfigInfoByStudyIdAndName")
    @RegisterConstructorMapper(PdfConfigInfo.class)
    Optional<PdfConfigInfo> findConfigInfoByStudyIdAndName(@Bind("studyId") long studyId, @Bind("name") String configName);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDocumentConfigInfoByStudyGuidAndName")
    @RegisterConstructorMapper(PdfConfigInfo.class)
    Optional<PdfConfigInfo> findConfigInfoByStudyGuidAndName(@Bind("studyGuid") String studyGuid, @Bind("name") String configName);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDocumentConfigInfoByStudyGuid")
    @RegisterConstructorMapper(PdfConfigInfo.class)
    List<PdfConfigInfo> findConfigInfoByStudyGuid(@Bind("studyGuid") String studyGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryVersionWithDataSourcesById")
    @RegisterConstructorMapper(value = PdfVersion.class, prefix = "v")
    @RegisterConstructorMapper(value = PdfDataSource.class, prefix = "s")
    @RegisterConstructorMapper(value = PdfActivityDataSource.class, prefix = "s")
    @UseRowReducer(ConfigVersionWithDataSourcesReducer.class)
    Optional<PdfVersion> findConfigVersion(@Bind("versionId") long versionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryVersionWithDataSourcesByConfigIdAndVersionTag")
    @RegisterConstructorMapper(value = PdfVersion.class, prefix = "v")
    @RegisterConstructorMapper(value = PdfDataSource.class, prefix = "s")
    @RegisterConstructorMapper(value = PdfActivityDataSource.class, prefix = "s")
    @UseRowReducer(ConfigVersionWithDataSourcesReducer.class)
    Optional<PdfVersion> findConfigVersionByConfigIdAndVersionTag(@Bind("configId") long configId, @Bind("versionTag") String versionTag);

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryVersionsWithDataSourcesByConfigIdInDescOrder")
    @RegisterConstructorMapper(value = PdfVersion.class, prefix = "v")
    @RegisterConstructorMapper(value = PdfDataSource.class, prefix = "s")
    @RegisterConstructorMapper(value = PdfActivityDataSource.class, prefix = "s")
    @UseRowReducer(ConfigVersionWithDataSourcesReducer.class)
    List<PdfVersion> findOrderedConfigVersionsByConfigId(@Bind("configId") long configId);

    @SqlQuery("select count(*) from pdf_document_version where pdf_document_configuration_id = :configId")
    int findNumVersionsByConfigId(@Bind("configId") long configId);

    default PdfConfiguration findFullConfig(long versionId) {
        PdfVersion version = findConfigVersion(versionId)
                .orElseThrow(() -> new DaoException("Could not find pdf version using versionId=" + versionId));
        return findFullConfig(version);
    }

    default PdfConfiguration findFullConfig(PdfVersion version) {
        PdfConfigInfo info = findConfigInfo(version.getConfigId())
                .orElseThrow(() -> new DaoException("Could not find pdf config info using id=" + version.getConfigId()));
        return findFullConfig(info, version);
    }

    default PdfConfiguration findFullConfig(PdfConfigInfo info, PdfVersion version) {
        List<Long> templateIds = findTemplateIdsByVersionId(version.getId());
        return new PdfConfiguration(info, version, templateIds);
    }

    class ConfigVersionWithDataSourcesReducer implements LinkedHashMapRowReducer<Long, PdfVersion> {
        @Override
        public void accumulate(Map<Long, PdfVersion> container, RowView view) {
            long versionId = view.getColumn("v_pdf_document_version_id", Long.class);
            PdfVersion version = container.computeIfAbsent(versionId, id -> view.getRow(PdfVersion.class));
            String dataSourceType = view.getColumn("s_pdf_data_source_type", String.class);
            if (dataSourceType != null) {
                PdfDataSource source;
                PdfDataSourceType type = PdfDataSourceType.valueOf(dataSourceType);
                if (type == PdfDataSourceType.ACTIVITY) {
                    source = view.getRow(PdfActivityDataSource.class);
                } else {
                    source = view.getRow(PdfDataSource.class);
                }
                version.addDataSource(source);
            }
        }
    }

    class BaseTemplatesReducer implements LinkedHashMapRowReducer<Long, PdfTemplate> {
        @Override
        public void accumulate(Map<Long, PdfTemplate> container, RowView view) {
            PdfTemplateDto dto = view.getRow(PdfTemplateDto.class);
            PdfTemplate template;
            switch (dto.getType()) {
                case MAILING_ADDRESS:
                    template = new MailingAddressTemplate(dto, view.getRow(MailingAddressTemplateDto.class));
                    break;
                case PHYSICIAN_INSTITUTION:
                    template = new PhysicianInstitutionTemplate(dto, view.getRow(PhysicianInstitutionTemplateDto.class));
                    break;
                case CUSTOM:
                    template = new CustomTemplate(dto);
                    break;
                default:
                    throw new DaoException("unhandled pdf template type " + dto.getType());
            }
            container.put(template.getId(), template);
        }
    }

    class SubstitutionsReducer implements LinkedHashMapRowReducer<Long, PdfSubstitution> {
        @Override
        public void accumulate(Map<Long, PdfSubstitution> container, RowView view) {
            SubstitutionType type = SubstitutionType.valueOf(view.getColumn("substitution_type", String.class));
            PdfSubstitution sub;
            switch (type) {
                case PROFILE:
                    sub = view.getRow(ProfileSubstitution.class);
                    break;
                case ACTIVITY_DATE:
                    sub = view.getRow(ActivityDateSubstitution.class);
                    break;
                case ANSWER:
                    QuestionType qtype = QuestionType.valueOf(view.getColumn("a_question_type", String.class));
                    if (qtype == QuestionType.BOOLEAN) {
                        sub = view.getRow(BooleanAnswerSubstitution.class);
                    } else {
                        sub = view.getRow(AnswerSubstitution.class);
                    }
                    break;
                default:
                    throw new DaoException("unhandled pdf substitution type " + type);
            }
            container.put(sub.getId(), sub);
        }
    }
}
