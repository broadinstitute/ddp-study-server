package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiFormActivitySetting;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

@Slf4j
public class BrugadaConsentV2 implements CustomTask {
    private static final String DATA_FILE = "patches/consent-v2.conf";
    private static final String STUDY = "brugada";
    private static final String INTRO = "introduction";

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
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        String activityCode = dataCfg.getString("activityCode");
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
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
        Config intro = dataCfg.getConfig(INTRO);
        revisionConsentSetting(handle, meta, version2, intro);
    }

    private void revisionConsentSetting(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config conf) {
        FormSectionDef newIntro = gson.fromJson(ConfigUtil.toJson(conf), FormSectionDef.class);

        JdbiFormActivitySetting jdbiFormSetting = handle.attach(JdbiFormActivitySetting.class);
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);

        FormActivitySettingDto settings = jdbiFormSetting.findActiveSettingDtoByActivityId(versionDto.getActivityId())
                .orElseThrow(() -> new DDPException("Could not find latest form settings for activity id=" + versionDto.getActivityId()));

        long newRevId = jdbiRevision.copyAndTerminate(settings.getRevisionId(), meta);
        int numRows = jdbiFormSetting.updateRevisionIdById(settings.getId(), newRevId);
        if (numRows != 1) {
            throw new DDPException(String.format(
                    "Cannot update revision for activityId=%d, formActivitySettingId=%d",
                    versionDto.getActivityId(), settings.getId()));
        }

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        long introductionSectionId = sectionBlockDao.insertSection(versionDto.getActivityId(), newIntro, versionDto.getRevId());

        long newSettingId = jdbiFormSetting.insert(versionDto.getActivityId(), settings.getListStyleHint(),
                introductionSectionId, settings.getClosingSectionId(), versionDto.getRevId(),
                settings.getReadonlyHintTemplateId(), settings.getLastUpdated(), settings.getLastUpdatedTextTemplateId(),
                settings.shouldSnapshotSubstitutionsOnSubmit(), settings.shouldSnapshotAddressOnSubmit());

        log.info("Created new form activity setting with id={}", newSettingId);
    }

}
