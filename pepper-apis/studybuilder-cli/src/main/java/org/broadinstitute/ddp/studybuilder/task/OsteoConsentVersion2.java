package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;


public class OsteoConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoConsentVersion2.class);
    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
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

        LOG.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();
        updateVariables(handle, adminUser, studyDto, activityCode, ts);
    }

    private void updateVariables(Handle handle, User adminUser, StudyDto studyDto, String activityCode, long ts) {
        List<? extends Config> configList = dataCfg.getConfigList("translation-updates");
        System.out.println(configList);
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);

        RevisionMetadata meta = new RevisionMetadata(ts, adminUser.getId(), reason);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, "v2", meta);
        for (Config config : configList) {
            revisionVariableTranslation(config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW), handle, meta, version2);
        }
    }

    private static final String TRANSLATION_KEY = "varName";
    private static final String TRANSLATION_NEW = "newValue";

    private void revisionVariableTranslation(String varName, String newTemplateText, Handle handle,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        long tmplVarId = handle.attach(SqlHelper.class).findTemplateVariableIdByVariableName(varName);
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newVarSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newVarSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
    }


    private interface SqlHelper extends SqlObject {
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);
    }
}

