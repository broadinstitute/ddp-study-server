package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
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
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@Slf4j
public class AtcpAssentV2 implements CustomTask {
    private static final String DATA_FILE = "patches/assent-v2.conf";
    private static final String STUDY = "atcp";
    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_KEY = "variableName";
    private static final String TRANSLATION_NEW = "newValue";
    private static final String LANGUAGE_CODE = "language";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
        this.cfgPath = cfgPath;
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        String activityCode = dataCfg.getString("activityCode");
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp.toEpochMilli());
        revisionConsent(handle, adminUser.getId(), studyDto, activityCode, versionTag, timestamp.toEpochMilli());
    }

    private void revisionConsent(Handle handle, long adminUserId, StudyDto studyDto,
                                 String activityCode, String versionTag, long timestamp) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp, adminUserId, reason);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);

        updateVariables(handle, meta, version2);
    }

    private void updateVariables(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionVariableTranslation(config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW),
                    config.getString(LANGUAGE_CODE), handle, meta, version2);
        }
    }

    private void revisionVariableTranslation(String varName, String newTemplateText, String isoLanguageCode, Handle handle,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        long tmplVarId = handle.attach(SqlHelper.class).findTemplateVariableIdByVariableName(varName);
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        List<Long> revisionIdList = transList.stream().map(Translation::getRevisionId).map(Optional::get).collect(Collectors.toList());
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newSubRevId = jdbiRevision.copyAndTerminate(revisionIdList.get(0), meta.getTimestamp(), meta.getUserId(), meta.getReason());
        long[] retiringId = {newSubRevId};
        transList.forEach(transListEntry -> {
            jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(transListEntry.getId().get()), retiringId);
            if (isoLanguageCode.equalsIgnoreCase(transListEntry.getLanguageCode())) {
                jdbiVarSubst.insert(transListEntry.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
            } else {
                jdbiVarSubst.insert(transListEntry.getLanguageCode(), transListEntry.getText(), version2.getRevId(), tmplVarId);
            }
        });
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);
    }
}
