package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class OsteoActivityDetailsUpdate implements CustomTask {

    private static final String OSTEO_STUDY = "CMI-OSTEO";
    private static final String DATA_PATH = "patches/update-activity-details.conf";

    private static final String CREATED = "CREATED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETE = "COMPLETE";

    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        File dataFile = cfgPath.getParent().resolve(DATA_PATH).toFile();
        if (!dataFile.exists()) {
            throw new DDPException("Data file is missing: " + dataFile);
        }
        dataCfg = ConfigFactory.parseFile(dataFile);
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(OSTEO_STUDY);
        var studyId = studyDto.getId();

        updateDashboard(handle, dataCfg, studyId);
    }

    private void updateDashboard(Handle handle, Config config, long studyId) {

        List<? extends Config> translatedSummaries = config.getConfigList("newTranslatedSummaries");
        for (Config summary : translatedSummaries) {
            String activityCode = summary.getString("activityCode");
            int activityId = handle.attach(SqlHelper.class).findStudyActivityId(activityCode, studyId);

            if (summary.hasPath(CREATED)) {
                updateSummaryByActivityIdAndTypeId(handle, CREATED, summary, activityId);
            }

            if (summary.hasPath(IN_PROGRESS)) {
                updateSummaryByActivityIdAndTypeId(handle, IN_PROGRESS, summary, activityId);
            }

            if (summary.hasPath(COMPLETE)) {
                updateSummaryByActivityIdAndTypeId(handle, COMPLETE, summary, activityId);
            }
        }
    }

    private void updateSummaryByActivityIdAndTypeId(Handle handle, String type, Config summary, int studyActivityId) {
        int typeId = handle.attach(SqlHelper.class).findTypeId(type);
        String newValue = summary.getString(type);
        handle.attach(SqlHelper.class).updateTranslationText(newValue, studyActivityId, typeId);
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
