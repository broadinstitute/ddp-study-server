package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

@Slf4j
public class OsteoConsentAssentV3 implements CustomTask {
    // pepper-449
    private static final String DATA_FILE = "patches/consent-assent-v3.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_KEY = "varName";
    private static final String TRANSLATION_NEW = "newValue";
    private static final String LANGUAGE_CODE = "language";

    private Config dataCfg;
    private SqlHelper sqlHelper;
    private String activityCode;
    private String studyGuid;
    private String versionTag;
    private Config studyCfg;
    private Config varsCfg;
    private Instant timestamp;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Patch conf file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file);

        this.studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(OSTEO_STUDY)) {
            throw new DDPException("This task is only for " + OSTEO_STUDY + " study!");
        }

        this.varsCfg = varsCfg;
        this.studyCfg = studyCfg;
        this.activityCode = dataCfg.getString("activityCode");

        this.timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        String activityCode = dataCfg.getString("activityCode");

        this.versionTag = dataCfg.getString("versionTag");
        long ts = this.timestamp.toEpochMilli();
        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);

        sqlHelper = handle.attach(SqlHelper.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityVersionDto version3 = getVersion3(handle, studyDto, metaConsent, activityCode);
        updateActivityVariables(handle, metaConsent, version3);
    }

    private ActivityVersionDto getVersion3(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);
    }

    private void updateActivityVariables(Handle handle, RevisionMetadata meta, ActivityVersionDto version3) {
        String languageCode = dataCfg.getString(LANGUAGE_CODE);
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionVariableTranslation(config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW),
                    languageCode, handle, meta, version3);
        }
    }

    private void revisionVariableTranslation(String varName, String newTemplateText, String isoLanguageCode, Handle handle,
            RevisionMetadata meta, ActivityVersionDto version3) {
        log.info("Update template {}", varName);
        long tmplVarId = sqlHelper.findTemplateVariableIdByVariableName(varName);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        List<Long> revisionIdList = transList.stream().map(Translation::getRevisionId).map(Optional::get).collect(Collectors.toList());
        log.info("revision id {}", revisionIdList);
        long newSubRevId = jdbiRevision.copyAndTerminate(revisionIdList.get(0), meta.getTimestamp(), meta.getUserId(), meta.getReason());
        long[] retiringId = {newSubRevId};
        transList.forEach(transListEntry -> {
            jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(transListEntry.getId().get()), retiringId);
            if (isoLanguageCode.equalsIgnoreCase(transListEntry.getLanguageCode())) {
                jdbiVarSubst.insert(transListEntry.getLanguageCode(), newTemplateText, version3.getRevId(), tmplVarId);
            } else {
                jdbiVarSubst.insert(transListEntry.getLanguageCode(), transListEntry.getText(), version3.getRevId(), tmplVarId);
            }
        });
    }


    private interface SqlHelper extends SqlObject {

        @SqlQuery("SELECT template_variable_id FROM template_variable WHERE variable_name = :variable_name "
                + "ORDER BY template_variable_id DESC")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);
    }
}
