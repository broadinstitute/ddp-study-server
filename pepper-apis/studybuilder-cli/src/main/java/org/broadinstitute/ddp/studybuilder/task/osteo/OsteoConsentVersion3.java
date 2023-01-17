package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class OsteoConsentVersion3 implements CustomTask {
    private static final String DATA_FILE = "patches/consent-version-3.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private static final String DATA_FILE_OSTEO_SELF_CONSENT = "patches/self-consent.conf";
    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String ADULT_TRANSLATION_NEW = "newValue";
    private static final String ADULT_TRANSLATION_KEY = "varName";

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
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;

        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        File selfConsentFile = cfgPath.getParent().resolve(DATA_FILE_OSTEO_SELF_CONSENT).toFile();
        if (!selfConsentFile.exists()) {
            throw new DDPException("Data file is missing: " + selfConsentFile);
        }
        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

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

        ActivityVersionDto version3ForConsent = getVersion3(handle, studyDto, metaConsent, activityCodeConsent);
        runAdultConsentUpdate(handle, metaConsent, version3ForConsent);
    }

    private ActivityVersionDto getVersion3(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, "v3", meta);
    }

    private void runAdultConsentUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto version3) {

        updateAdultVariables(handle, meta, version3, dataCfg);
    }

    private void updateAdultVariables(Handle handle, RevisionMetadata meta,
                                      ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionAdultVariableTranslation(config.getString(ADULT_TRANSLATION_KEY),
                    config.getString(ADULT_TRANSLATION_NEW), handle, meta, version2);
        }
    }


    private void revisionAdultVariableTranslation(String varName, String newTemplateText, Handle handle,
                                                  RevisionMetadata meta, ActivityVersionDto version3) {
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(varName);
        for (Long tmplVarId : templateVariableIdByVariableNames) {
            //Long tmplVarId = templateVariableIdByVariableNames.get(0);
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            Translation currTranslation = transList.get(0);

            long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
            long[] revIds = {newFullNameSubRevId};
            jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
            jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version3.getRevId(), tmplVarId);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variable_name") String variableName);

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
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);

        @SqlUpdate("update i18n_activity_detail set name = :name, title = :title where study_activity_id = :studyActivityId")
        int _updateActivityNameAndTitle(@Bind("studyActivityId") long studyActivityId,
                                        @Bind("name") String name,
                                        @Bind("title") String title);

    }
}

