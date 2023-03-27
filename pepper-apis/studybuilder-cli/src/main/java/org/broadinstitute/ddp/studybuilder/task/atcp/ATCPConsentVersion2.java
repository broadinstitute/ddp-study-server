package org.broadinstitute.ddp.studybuilder.task.atcp;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTemplateVariable;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CircadiaConsentV2;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class ATCPConsentVersion2 implements CustomTask {

    private static final String OLD_TEMPLATE_KEY = "old_search";
    private static final String BLOCK_KEY = "blockNew";
    private static final String BLOCK_UPDATES = "block-updates";

    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String ATCP_STUDY = "atcp";
    private static final String VARIABLES_ADD = "variables-add";
    private static final String VARIABLES_UPD = "variables-update";
    private static final String VARIABLES_DEL = "variables-delete";
    private static final String NEW_TEMPL_VAR_LOOKUP = "consent_participation_detail_p4";
    private static final Gson gson = GsonUtil.standardGson();

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private ActivityDao activityDao;
    private SqlHelper sqlHelper;
    private SectionBlockDao sectionBlockDao;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        //dataCfg = ConfigFactory.parseFile(file);
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(ATCP_STUDY)) {
            throw new DDPException("This task is only for the " + ATCP_STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

/*
    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCodeConsent = "CONSENT";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        log.info("Changing version of {} to {} with timestamp={}", activityCodeConsent, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsent, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        //ActivityVersionDto version2ForConsent = activityDao.
                //getVersion2(handle, studyDto, metaConsent, activityCodeConsent);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "CONSENT");
        ActivityVersionDto version2ForConsent = new  ActivityVersionDto(3008, activityId, versionTag, 4394, metaConsent.getTimestamp(), null);

        runConsentUpdate(handle, metaConsent, version2ForConsent);

    }

*/

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCodeConsent = "CONSENT";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        log.info("Changing version of {} to {} with timestamp={}", activityCodeConsent, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsent, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCodeConsent);
        runConsentUpdate(handle, metaConsent, version2ForConsent);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, "v2", meta);
    }

    private void runConsentUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        updateTemplates(handle, meta, version2);
        terminateTemplateVariables(meta, version2, dataCfg);
        //addTemplateVariables(handle, meta, version2, dataCfg);
        updateTemplateVariables(meta, version2, dataCfg);
    }

    private void updateTemplates(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList(BLOCK_UPDATES);
        for (Config config : configList) {
            revisionContentBlockTemplate(handle, meta, version2, config);
        }
    }

    private void addTemplateVariables(Handle handle, RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        log.info("ADD variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_ADD);
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(NEW_TEMPL_VAR_LOOKUP);
        //get first ID
        Long templateId = sqlHelper.findTemplateIdByVariableId(templateVariableIdByVariableNames.get(0));
        //todo revision the template.. update template text
        TemplateDao templateDao = handle.attach(TemplateDao.class);
        Template template = templateDao.loadTemplateByIdAndTimestamp(templateId, meta.getTimestamp());
        long revId = jdbiRevision.copyAndTerminate(template.getRevisionId().get(), meta);
        templateDao.disableTemplate(templateId,meta);

        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            addVariable(handle, templateId, version2.getRevId(), templateVariable);
        }
    }

    private void updateTemplateVariables(RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        log.info("UPDATE variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_UPD);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            revisionVariableTranslation(templateVariable, meta, version2);
        }
    }

    private void terminateTemplateVariables(RevisionMetadata meta,
                                      ActivityVersionDto version2, Config dataCfg) {
        log.info("Deleting variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_DEL);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            terminateTemplateVariable(templateVariable, meta, version2);
        }
    }

    private void revisionVariableTranslation(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and updating template variable: {}", templateVariable.getName());
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(templateVariable.getName());
        //todo if 0/variable not found.. insert new one 3/24
        log.info("Tmpl variableId count: {} ", templateVariableIdByVariableNames.size());
        for (Long tmplVarId : templateVariableIdByVariableNames) {
        //Long tmplVarId = templateVariableIdByVariableNames.get(0);
            //todo.. use just first variableId and ignore rest !! to handle scenarios where study was reloaded and invalidated
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            //all translations has same revision.. hence using first
            long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
            long[] revIds = new long[transList.size()];
            Arrays.fill(revIds, revId);
            List<Long> substitutionIds = new ArrayList<>();
            log.info("subs/translations count: {} for varId : {} ", transList.size(), tmplVarId);
            for (var currTranslation : transList) {
                String language = currTranslation.getLanguageCode();
                String currentText = currTranslation.getText();

                //todo .. iterate and update all languages
                String newTxt = templateVariable.getTranslation(language).get().getText();
                log.info("terminated: language: {} revId: {} new Text: {} currText: {}", language, currTranslation.getRevisionId(),
                        newTxt, currentText);

                substitutionIds.add(currTranslation.getId().get());
                //jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
                jdbiVarSubst.insert(currTranslation.getLanguageCode(), templateVariable.getTranslation(language).get().getText(),
                        version2.getRevId(), tmplVarId);
            }
            int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
            if (ids.length != revIds.length) {
                throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
            }
            log.info("updated ids: {}", ids);
            log.info("revisioned and updated template variable: {}", tmplVarId);
        }
    }

    private void terminateTemplateVariable(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and terminating template variable: {}", templateVariable.getName());
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(templateVariable.getName());
        log.info("Tmpl variableId count: {} ", templateVariableIdByVariableNames.size());
        for (Long tmplVarId : templateVariableIdByVariableNames) {
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            //all translations has same revision.. hence using first
            long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
            long[] revIds = new long[transList.size()];
            Arrays.fill(revIds, revId);
            List<Long> substitutionIds = new ArrayList<>();
            log.info("subs/translations count: {} for varId : {} ", transList.size(), tmplVarId);
            for (var currTranslation : transList) {
                substitutionIds.add(currTranslation.getId().get());
            }
            int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
            if (ids.length != revIds.length) {
                throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
            }
            log.info("terminated ids: {}", ids);
            log.info("revisioned and terminated template variable: {}", tmplVarId);
        }
    }

    private long addVariable(Handle handle, long templateId, long revisionId, TemplateVariable variable) {
        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = variable.getName();

        if (I18nTemplateConstants.DDP.equals(variableName) || I18nTemplateConstants.LAST_UPDATED.equals(variableName)) {
            throw new DaoException("Variable name '" + variableName + "' is not allowed");
        }

        long variableId = jdbiTemplateVariable.insertVariable(templateId, variableName);
        log.info("inserted variable {} .. id: {} ", variableName, variableId);

        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            long varId = jdbiVariableSubstitution.insert(language, translation.getText(), revisionId, variableId);
            log.info("inserted variable {} language {} .. id: {} : inserted substitution",
                    variableName, language, varId);
        }
        log.info("inserted template variable for all translations");
        return variableId;
    }

    private void revisionContentBlockTemplate(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config conf) {
        Config config = conf.getConfig(BLOCK_KEY);
        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);
        Template newBodyTemplate = contentBlockDef.getBodyTemplate();
        Template newTitleTemplate = contentBlockDef.getTitleTemplate();

        String oldBlockTemplateText = conf.getString(OLD_TEMPLATE_KEY);

        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);

        String templateSearchParam = String.format("%s%s%s", "%", oldBlockTemplateText, "%");
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), templateSearchParam);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId()));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
        if (contentBlock.getTitleTemplateId() != null) {
            templateDao.disableTemplate(contentBlock.getTitleTemplateId(), meta);
        }
        Long newBodyTemplateId = templateDao.insertTemplate(newBodyTemplate, versionDto.getRevId());

        Long newTitleTemplateId = null;
        if (newTitleTemplate != null) {
            newTitleTemplateId = templateDao.insertTemplate(newTitleTemplate, versionDto.getRevId());
        }
        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newBodyTemplateId,
                newTitleTemplateId, versionDto.getRevId());

        log.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, contentBlock.getBlockId(), newBodyTemplateId, contentBlockDef.getBodyTemplate().getTemplateText());
    }

    private interface SqlHelper extends SqlObject {

        //todo filter by studyId so that we query only latest study variables
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variableName "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variableName") String variableName);

        @SqlQuery("select template_id from template_variable where template_variable_id = :variableId "
                + "order by template_id desc")
        Long findTemplateIdByVariableId(@Bind("variableId") long variableId);

        /**
         * Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */
        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text like :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText2(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);


        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text like :text")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);
    }

}

