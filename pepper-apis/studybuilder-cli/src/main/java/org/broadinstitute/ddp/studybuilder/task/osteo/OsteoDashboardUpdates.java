package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;


import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class OsteoDashboardUpdates implements CustomTask {


    private static final String ADULT_CONSENT = "patches/adult-consent-dashboard-update.conf";
    private static final String ADULT_MEDICAL_RELEASE = "patches/adult-mr-dashboard-update.conf";
    private static final String ADULT_ABOUT_YOU = "patches/adult-about-you-dashboard-update.conf";
    private static final String ADULT_ABOUT_YOU_ACTIVITY = "patches/adult-about-you-dashboard-update.conf";

    private static final String CHILD_CONSENT = "patches/child-consent-dashboard-update.conf";
    private static final String CHILD_MEDICAL_RELEASE = "patches/child-mr-dashboard-update.conf";
    private static final String CHILD_ABOUT_YOU = "patches/child-about-you-dashboard-update.conf";


    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private Config varsCfg;
    private Path cfgPath;
    private Config cfg;

    private Config adultConsentCfg;
    private Config adultMedicalReleaseCfg;
    private Config adultAboutYouCfg;
    private Config adultAboutYouActivityCfg;

    private Config childConsentCfg;
    private Config childMedicalReleaseCfg;
    private Config childAboutYouCfg;

    private ActivityI18nDao activityI18nDao;

    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.cfgPath = cfgPath;

        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        initAdult();
        initChild();

        cfg = studyCfg;
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {

        this.activityI18nDao = handle.attach(ActivityI18nDao.class);

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid("CMI-OSTEO");
        var studyId = studyDto.getId();

        updateDashboard(handle, adultConsentCfg, studyId);
        updateDashboard(handle, adultMedicalReleaseCfg, studyId);
        updateDashboard(handle, adultAboutYouCfg, studyId);
        updateDashboard(handle, adultAboutYouActivityCfg, studyId);

        updateDashboard(handle, childConsentCfg, studyId);
        updateDashboard(handle, childMedicalReleaseCfg, studyId);
        updateDashboard(handle, childAboutYouCfg, studyId);

        updateAboutYouNameDashboard(handle, adultAboutYouCfg, studyId);
    }

    private void initChild() {
        File childConsentFile = cfgPath.getParent().resolve(CHILD_CONSENT).toFile();
        if (!childConsentFile.exists()) {
            throw new DDPException("Data file is missing: " + childConsentFile);
        }
        childConsentCfg = ConfigFactory.parseFile(childConsentFile);

        File childMedicalReleaseFile = cfgPath.getParent().resolve(CHILD_MEDICAL_RELEASE).toFile();
        if (!childMedicalReleaseFile.exists()) {
            throw new DDPException("Data file is missing: " + childMedicalReleaseFile);
        }
        childMedicalReleaseCfg = ConfigFactory.parseFile(childMedicalReleaseFile);

        File childAboutYouFile = cfgPath.getParent().resolve(CHILD_ABOUT_YOU).toFile();
        if (!childAboutYouFile.exists()) {
            throw new DDPException("Data file is missing: " + childAboutYouFile);
        }
        childAboutYouCfg = ConfigFactory.parseFile(childAboutYouFile);
    }

    private void initAdult() {
        File adultConsentFile = cfgPath.getParent().resolve(ADULT_CONSENT).toFile();
        if (!adultConsentFile.exists()) {
            throw new DDPException("Data file is missing: " + adultConsentFile);
        }
        adultConsentCfg = ConfigFactory.parseFile(adultConsentFile);


        File adultMedicalReleaseFile = cfgPath.getParent().resolve(ADULT_MEDICAL_RELEASE).toFile();
        if (!adultMedicalReleaseFile.exists()) {
            throw new DDPException("Data file is missing: " + adultMedicalReleaseFile);
        }
        adultMedicalReleaseCfg = ConfigFactory.parseFile(adultMedicalReleaseFile);

        File adultAboutYouFile = cfgPath.getParent().resolve(ADULT_ABOUT_YOU).toFile();
        if (!adultAboutYouFile.exists()) {
            throw new DDPException("Data file is missing: " + adultAboutYouFile);
        }
        adultAboutYouCfg = ConfigFactory.parseFile(adultAboutYouFile);

        File adultAboutYouActivityFile = cfgPath.getParent().resolve(ADULT_ABOUT_YOU_ACTIVITY).toFile();
        if (!adultAboutYouActivityFile.exists()) {
            throw new DDPException("Data file is missing: " + adultAboutYouActivityFile);
        }
        adultAboutYouActivityCfg = ConfigFactory.parseFile(adultAboutYouActivityFile);

    }

    private void updateDashboard(Handle handle, Config config, long studyId) {

        List<? extends Config> configList = config.getConfigList("newTranslatedSummaries");
        List<? extends Config> studyActivityCode = config.getConfigList("activityCode");
        for (Config conf1 : studyActivityCode) {
            String activityCode = conf1.getString("code");
            int studyActivityId = handle.attach(SqlHelper.class).findStudyActivityId(activityCode, studyId);
            for (Config conf : configList) {
                String statusCode = conf.getString("statusCode");
                int typeId = handle.attach(SqlHelper.class).findTypeId(statusCode);
                String newValue = conf.getString("text");
                handle.attach(SqlHelper.class).updateTranslationText(newValue, studyActivityId, typeId);
            }
        }
    }

    private void updateAboutYouNameDashboard(Handle handle, Config config, long studyId) {
        String studyActivityCode = config.getConfigList("activityCode").get(0).getString("code");
        int studyActivityId = handle.attach(SqlHelper.class).findStudyActivityId(studyActivityCode, studyId);
        updateActivityDetails(handle, studyActivityId, studyActivityCode);
    }

    private void updateActivityDetails(Handle handle, long activityId, String activityCode) {

        long ts = this.timestamp.toEpochMilli();
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid("CMI-OSTEO");
        String reasonConsentAssent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, "V2");
        RevisionMetadata meta = new RevisionMetadata(ts, adminUser.getId(), reasonConsentAssent);

        ActivityI18nDetail i18nDetail = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, Instant.now().toEpochMilli())
                .iterator().next();

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(i18nDetail.getRevisionId(), meta);

        var newI18nDetail = new ActivityI18nDetail(
                i18nDetail.getId(),
                i18nDetail.getActivityId(),
                i18nDetail.getLangCodeId(),
                i18nDetail.getIsoLangCode(),
                adultAboutYouCfg.getString("name"),
                i18nDetail.getSecondName(),
                i18nDetail.getTitle(),
                i18nDetail.getSubtitle(),
                i18nDetail.getDescription(),
                newRevId);
        activityI18nDao.insertDetails(List.of(newI18nDetail));
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update i18n_study_activity_summary_trans set translation_text = :translationText "
                + "where study_activity_id = :studyActivityId and activity_instance_status_type_id = :typeId")
        int updateTranslationText(@Bind("translationText") String translationText, @Bind("studyActivityId") int studyActivityId,
                                  @Bind("typeId") int typeId);

        @SqlQuery("select activity_instance_status_type_id from activity_instance_status_type "
                + " where activity_instance_status_type_code = :typeCode")
        int findTypeId(@Bind("typeCode") String typeCode);

        @SqlQuery("select study_activity_id from study_activity where study_activity_code = :studyActivityCode and study_id = :studyId")
        int findStudyActivityId(@Bind("studyActivityCode") String studyActivityCode, @Bind("studyId") long studyId);
    }

}
