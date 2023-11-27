package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoActivityDashboardOrdering implements CustomTask {

    private static final String DATA_FILE = "patches/osteo-activity-ordering.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }


    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        updateOrdering(handle, studyDto);
    }

    private void updateOrdering(Handle handle, StudyDto studyDto) {
        List<? extends Config> activities = dataCfg.getConfigList("activities");
        for (Config activity : activities) {
            String activityCode = activity.getString("activityCode");
            int displayOder = activity.getInt("displayOrder");
            handle.attach(SqlHelper.class).updateActivityDisplayOrder(displayOder, activityCode, studyDto.getId());
            log.info("Activity displayOrder {}:{} has been updated in study {}", activityCode, displayOder, STUDY_GUID);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update study_activity set display_order = :displayOrder "
                + "where study_activity_code = :activityCode and study_id= :studyId")
        int updateActivityDisplayOrder(@Bind("displayOrder") int displayOrder,
                                       @Bind("activityCode") String activityCode,
                                       @Bind("studyId") long studyId);
    }
}
