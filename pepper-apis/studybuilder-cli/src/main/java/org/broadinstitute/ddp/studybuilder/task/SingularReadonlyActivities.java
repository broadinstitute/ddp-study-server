package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class SingularReadonlyActivities implements CustomTask {
    private static final String DATA_FILE  = "patches/singular-readonly-activities.conf";
    private static final String STUDY_GUID  = "singular";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    public SingularReadonlyActivities() {
        super();
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        updateActivities(handle, studyDto, user.getUserId());
    }

    private void updateActivities(Handle handle, StudyDto studyDto, long userId) {
        if (!dataCfg.hasPath("activities")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        log.info("Updating activities configuration...");
        List<String> activities = dataCfg.getStringList("activities");
        activities.forEach(act -> updateActivity(handle, act, studyDto));
        log.info("Events configuration has been removed in study {}", cfg.getString("study.guid"));
    }

    private void updateActivity(Handle handle, String activityCode, StudyDto studyDto) {
        var helper = handle.attach(SqlHelper.class);
        long studyActivityId = helper.findActivityIdByStudyIdAndCode(studyDto.getId(), activityCode);
        List<Long> finishedActivities = helper.getDoneActivities(studyActivityId);
        helper.updateActivityInstances(finishedActivities);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update activity_instance set is_readonly=true where study_activity_id in (:studyActivityId)")
        void updateActivityInstances(@Bind("studyActivityId") List<Long> studyActivityId);

        default long findActivityIdByStudyIdAndCode(long studyId, String activityCode) {
            return getHandle().attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyId, activityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity id for " + activityCode));
        }

        @SqlQuery("select ai.activity_instance_id from activity_instance ai join activity_instance_status ais on "
                + "ai.activity_instance_id = ais.activity_instance_id where study_activity_id=:studyActivityId and "
                + "ais.activity_instance_status_type_id=(select activity_instance_status_type_id from activity_instance_status_type "
                + "where activity_instance_status_type_code='COMPLETE')")
        List<Long> getDoneActivities(@Bind("studyActivityId") long studyActivityId);
    }

}
