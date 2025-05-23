package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

//NOTE: As of now this class supports only revisioning an activity with ONLY existing variable names versioned
//Only Template variables of block content template, question prompt template and block group title templates are supported
//If new variables need to be added/deleted, new blocks, sections versioned.. all that is NOT supported

@Slf4j
public class RevisionStudyActivityVariablesSupport implements CustomTask {
    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_NEW = "newValue";
    private static final String TRANSLATION_KEY = "varName";
    private String dataFile;
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
    private String studyGuid;
    private String activityCode;
    private String activityName;
    private String activityTitle;

    private ActivityI18nDao activityI18nDao;

    public RevisionStudyActivityVariablesSupport(String studyGuid, String activityCode, String dataFilePath) {
        this.studyGuid = studyGuid;
        this.activityCode = activityCode;
        this.dataFile = dataFilePath;
    }

    public RevisionStudyActivityVariablesSupport(String studyGuid, String activityCode, String dataFilePath,
            String activityName, String activityTitle) {
        this.studyGuid = studyGuid;
        this.activityCode = activityCode;
        this.dataFile = dataFilePath;
        this.activityName = activityName;
        this.activityTitle = activityTitle;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only meant for the " + studyGuid + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);
        this.activityI18nDao = handle.attach(ActivityI18nDao.class);

        ActivityVersionDto newActivityVer = getNewVersion(handle, studyDto, metaConsent, activityCode);
        //revision activity Title
        if (StringUtils.isNotBlank(this.activityTitle)) {
            log.info("Revisioning activity Title: {} ", this.activityTitle);
            revisionActivityTitle(newActivityVer.getActivityId(), this.activityCode,
                    this.activityName, this.activityTitle, newActivityVer.getRevId());
        }
        runActivityUpdate(handle, metaConsent, newActivityVer);
    }

    private ActivityVersionDto getNewVersion(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, versionTag, meta);
    }

    private void runActivityUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto newVersion) {
        log.info("Version : {} Rev start..{}..Rev Tag: {}..Rev End:{} ", newVersion.getRevId(), newVersion.getRevStart(),
                newVersion.getVersionTag(), newVersion.getRevEnd());
        //todo
        //add new variables, delete variables
        //block/section revision support
        updateActivityVariables(handle, meta, newVersion, dataCfg);
    }

    private void updateActivityVariables(Handle handle, RevisionMetadata meta,
                                         ActivityVersionDto newVersion, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionAdultVariableTranslation(config.getString(TRANSLATION_KEY),
                    config.getString(TRANSLATION_NEW), meta, newVersion);
        }
    }


    private void revisionAdultVariableTranslation(String varName, String newTemplateText,
                                                  RevisionMetadata meta, ActivityVersionDto version3) {
        log.info("Revisioning and updating template variable: {} .. \n New Text: {} ", varName, newTemplateText);
        Long tmplVarId = sqlHelper.findBlockTemplateVariableIdByNameAndActivityId(varName, version3.getActivityId());

        if (tmplVarId == null) {
            log.warn("NO Template Variable found with name: {}. Checking in Block Group Header Template Variable", varName);
            tmplVarId = sqlHelper.findBlockGroupTemplateVariableIdByNameAndActivityId(varName, version3.getActivityId());
        }

        if (tmplVarId == null) {
            log.warn("NO Template Variable found with name: {}. Checking in Question prompt Template Variable", varName);
            tmplVarId = sqlHelper.findQuestionPromptTemplateVariableIdByNameAndActivityId(varName, version3.getActivityId());
            if (tmplVarId == null) {
                throw new DDPException("Template variable NOT found with name : " + varName + " and study activityId: "
                        + version3.getActivityId() + ". Checked Body template vars and question prompt template vars");
            }
        }
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        log.info("Translations count for var: {} .. list size: {}", tmplVarId, transList.size());
        Translation currTranslation = transList.get(transList.size() - 1);
        log.info("Current Translation : {} : Rev Id: {}", currTranslation.getText(), currTranslation.getRevisionId());

        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version3.getRevId(), tmplVarId);
        log.info("Revisioned and updated template variable: {}", tmplVarId);

    }

    private void revisionActivityTitle(long activityId, String activityCode, String name, String title, long revisionId) {
        ActivityI18nDetail i18nDetail = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, Instant.now().toEpochMilli())
                .iterator().next();
        var newI18nDetail = new ActivityI18nDetail(
                i18nDetail.getId(),
                i18nDetail.getActivityId(),
                i18nDetail.getLangCodeId(),
                i18nDetail.getIsoLangCode(),
                name,
                i18nDetail.getSecondName(),
                title,
                i18nDetail.getSubtitle(),
                i18nDetail.getDescription(),
                revisionId);
        activityI18nDao.insertDetails(List.of(newI18nDetail));
        log.info("Revisioned translatedTitle & Name for activity {}", activityCode);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variable_name") String variableName);

        @SqlQuery("select tv.template_variable_id from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id "
                + "  join template_variable tv on tv.template_id = tmpl.template_id "
                + " where tv.variable_name = :variableName"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId) order by tv.template_variable_id desc")
        Long findBlockTemplateVariableIdByNameAndActivityId(@Bind("variableName") String variableName, @Bind("activityId") Long activityId);


        @SqlQuery("select tv.template_variable_id from block_group_header as bgh"
                + "  join template as tmpl on tmpl.template_id = bgh.title_template_id "
                + "  join template_variable tv on tv.template_id = tmpl.template_id "
                + " where tv.variable_name = :variableName"
                + "   and bgh.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId) order by tv.template_variable_id desc")
        Long findBlockGroupTemplateVariableIdByNameAndActivityId(
                @Bind("variableName") String variableName, @Bind("activityId") Long activityId);

        @SqlQuery("select tv.template_variable_id from question as q"
                + "  join template as tmpl on tmpl.template_id = q.question_prompt_template_id"
                + "  join template_variable tv on tv.template_id = tmpl.template_id "
                + " where tv.variable_name = :variableName"
                + "   and q.study_activity_id = :activityId order by tv.template_variable_id desc")
        Long findQuestionPromptTemplateVariableIdByNameAndActivityId(
                @Bind("variableName") String variableName, @Bind("activityId") Long activityId);
    }

}
