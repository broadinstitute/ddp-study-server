package org.broadinstitute.ddp.studybuilder.task.lms;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class LmsParentalConsentVersion2 implements CustomTask {
    private static final String DATA_FILE = "patches/parental-consent-v2.conf";
    private static final String CMI_LMS = "cmi-lms";

    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String VAR_TRANSLATION_NEW = "newValue";
    private static final String VAR_NAME = "varName";

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private ActivityDao activityDao;
    private SqlHelper sqlHelper;
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

        if (!studyCfg.getString("study.guid").equals(CMI_LMS)) {
            throw new DDPException("This task is only for the " + CMI_LMS + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCode = dataCfg.getString("activityCode");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityVersionDto activityVer2 = getVersion2(handle, studyDto, metaConsent, activityCode);
        activityUpdate(metaConsent, activityVer2);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, versionTag, meta);
    }

    private void activityUpdate(RevisionMetadata meta, ActivityVersionDto version2) {
        updateTemplateVariables(meta, version2, dataCfg);
    }

    private void updateTemplateVariables(RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionAdultVariableTranslation(config.getString(VAR_NAME),
                    config.getString(VAR_TRANSLATION_NEW), meta, version2);
        }
    }


    private void revisionAdultVariableTranslation(String varName, String newTemplateText,
                                                  RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and updating template variable: {}", varName);
        Long tmplVarId = sqlHelper.findVariableIdByNameAndActivityId(varName, version2.getActivityId());

        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
        log.info("revisioned and updated template variable: {}", tmplVarId);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variable_name") String variableName);

        @SqlQuery("select tv.template_variable_id from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
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
                + "                        where fafs.form_activity_id = :activityId)")
        Long findVariableIdByNameAndActivityId(@Bind("variableName") String variableName, @Bind("activityId") Long activityId);

    }

}

