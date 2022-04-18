package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.GsonUtil;
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

    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private Config varsCfg;
    private Path cfgPath;
    private Config cfg;
    private Gson gson;

    private Config adultConsentCfg;
    private Config adultMedicalReleaseCfg;
    private Config adultAboutYouCfg;
    private Config adultAboutYouActivityCfg;

    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.cfgPath = cfgPath;

        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }


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

        cfg = studyCfg;
        timestamp = Instant.now();
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {

        updateAdultDashboard(handle, adultConsentCfg);
        updateAdultDashboard(handle, adultMedicalReleaseCfg);
        updateAdultDashboard(handle, adultAboutYouCfg);
    }

    private void updateAdultDashboard(Handle handle, Config config) {
        List<? extends Config> configList = config.getConfigList("newTranslatedSummaries");
        String studyActivityCode = config.getString("activityCode");
        int studyActivityId = handle.attach(SqlHelper.class).findStudyActivityId(studyActivityCode);
        for (Config conf : configList) {
            String statusCode = conf.getString("statusCode");
            int typeId = handle.attach(SqlHelper.class).findTypeId(statusCode);
            String newValue = conf.getString("text");
            handle.attach(SqlHelper.class).updateTranslationText(newValue, studyActivityId, typeId);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update i18n_study_activity_summary_trans set translation_text = :translationText "
                + "where study_activity_id = :studyActivityId and activity_instance_status_type_id = :typeId")
        int updateTranslationText(@Bind("translationText") String translationText, @Bind("studyActivityId") int studyActivityId,
                                  @Bind("typeId") int typeId);

        @SqlQuery("select activity_instance_status_type_id from activity_instance_status_type "
                + " where activity_instance_status_type_code = :typeCode")
        int findTypeId(@Bind("typeCode") String typeCode);

        @SqlQuery("select study_activity_id from study_activity where study_activity_code = :studyActivityCode")
        int findStudyActivityId(@Bind("studyActivityCode") String studyActivityCode);
    }

}
