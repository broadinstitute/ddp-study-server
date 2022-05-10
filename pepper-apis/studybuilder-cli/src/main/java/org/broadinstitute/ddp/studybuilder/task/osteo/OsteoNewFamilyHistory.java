package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoNewFamilyHistory implements CustomTask {

    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/family-history.conf";

    private Config cfg;
    private Config dataCfg;

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

        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        var helper = handle.attach(SqlHelper.class);
        long activityOldId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "FAMILY_HISTORY");

        for (var conf : dataCfg.getConfigList("eventChanges")) {
            long activityNewId = ActivityBuilder.findActivityId(handle, studyDto.getId(), conf.getString("activityCode"));
            long eventActionId = helper.findEventActionIdByActivityIdAndLabel(activityOldId, conf.getString("label"));
            helper.updateActivityInstanceCreationAction(activityNewId, eventActionId);
            log.info("Successfully updated event action id {} with label {} study to {}",
                    eventActionId, conf.getString("label"), conf.getString("activityCode"));
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select aica.activity_instance_creation_action_id from event_configuration ec"
                + "   join event_action ea on ec.event_action_id = ea.event_action_id"
                + "   join activity_instance_creation_action aica on ea.event_action_id = aica.activity_instance_creation_action_id"
                + " where aica.study_activity_id = :activityId and ec.label = :label")
        int findEventActionIdByActivityIdAndLabel(@Bind("activityId") long activityId, @Bind("label") String label);

        @SqlUpdate("update activity_instance_creation_action "
                + "set study_activity_id = :newActivityId "
                + "where activity_instance_creation_action_id = :event_action_id")
        void updateActivityInstanceCreationAction(@Bind("newActivityId") long newActivityId, @Bind("event_action_id") long eventActionId);
    }
}
